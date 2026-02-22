(ns empire.computer.core
  "Shared utilities for computer AI modules."
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.debug :as debug]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.visibility :as visibility]
            [empire.combat :as combat]
            [empire.player.production :as production]))

(defn get-neighbors
  "Returns valid neighbor coordinates for a position."
  [pos]
  (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets
                                    some?))

(defn distance
  "Manhattan distance between two positions."
  [[x1 y1] [x2 y2]]
  (+ (Math/abs (- x2 x1)) (Math/abs (- y2 y1))))

(defn chebyshev-distance
  "Chebyshev distance between two positions."
  [[r1 c1] [r2 c2]]
  (max (Math/abs (- r2 r1)) (Math/abs (- c2 c1))))

(defn attackable-target?
  "Returns true if the cell contains an attackable target for the computer."
  [cell]
  (or (and (= (:type cell) :city)
           (#{:player :free} (:city-status cell)))
      (and (:contents cell)
           (= (:owner (:contents cell)) :player))))

(defn find-visible-cities
  "Finds cities visible on computer-map matching the status predicate."
  [status-pred]
  (for [i (range (count @atoms/computer-map))
        j (range (count (first @atoms/computer-map)))
        :let [cell (get-in @atoms/computer-map [i j])]
        :when (and (= (:type cell) :city)
                   (status-pred (:city-status cell)))]
    [i j]))

(defn move-toward
  "Returns the neighbor that moves closest to target."
  [pos target passable-neighbors]
  (when (seq passable-neighbors)
    (apply min-key #(distance % target) passable-neighbors)))

(defn adjacent-to-computer-unexplored?
  "Returns true if the position has an adjacent unexplored cell on computer-map."
  [pos]
  (map-utils/any-neighbor-matches? pos @atoms/computer-map map-utils/neighbor-offsets
                                   nil?))

(defn stamp-territory
  "Stamps a land cell with the army's country-id as it moves through."
  [pos unit]
  (when (and (= :army (:type unit))
             (= :computer (:owner unit))
             (:country-id unit)
             (= :land (:type (get-in @atoms/game-map pos))))
    (swap! atoms/game-map assoc-in (conj pos :country-id) (:country-id unit))))

(defn- foreign-territory?
  "Returns true if unit is a computer army with a country-id and the target
   land cell has a different country-id. Cities are always passable."
  [unit to-cell]
  (and (= :army (:type unit))
       (= :computer (:owner unit))
       (:country-id unit)
       (= :land (:type to-cell))
       (:country-id to-cell)
       (not= (:country-id unit) (:country-id to-cell))))

(defn move-unit-to
  "Moves a unit from from-pos to to-pos. Returns to-pos if moved, nil if target
   occupied or blocked by sovereignty."
  [from-pos to-pos]
  (let [from-cell (get-in @atoms/game-map from-pos)
        to-cell (get-in @atoms/game-map to-pos)
        unit (:contents from-cell)]
    (cond
      (:contents to-cell) nil
      (foreign-territory? unit to-cell) nil
      :else
      (do
        (swap! atoms/game-map assoc-in from-pos (dissoc from-cell :contents))
        (swap! atoms/game-map assoc-in (conj to-pos :contents) unit)
        (stamp-territory to-pos unit)
        (visibility/update-cell-visibility to-pos (:owner unit) unit)
        to-pos))))

(defn- assign-country-on-conquest
  "Assigns country-id to conquered city based on the conquering army's identity.
   Army with country-id: city gets that country-id.
   Army with unload-event-id: mint new country-id, assign to city and all armies sharing that unload-event-id."
  [city-pos army]
  (cond
    (:country-id army)
    (swap! atoms/game-map assoc-in (conj city-pos :country-id) (:country-id army))

    (:unload-event-id army)
    (let [new-country-id @atoms/next-country-id
          eid (:unload-event-id army)]
      (swap! atoms/next-country-id inc)
      (swap! atoms/game-map assoc-in (conj city-pos :country-id) new-country-id)
      ;; Stamp all armies with the same unload-event-id
      (let [game-map @atoms/game-map]
        (doseq [i (range (count game-map))
                j (range (count (first game-map)))
                :let [cell (get-in game-map [i j])
                      unit (:contents cell)]
                :when (and unit
                           (= :army (:type unit))
                           (= :computer (:owner unit))
                           (= eid (:unload-event-id unit)))]
          (swap! atoms/game-map update-in [(int i) (int j) :contents]
                 #(-> % (assoc :country-id new-country-id) (dissoc :unload-event-id))))))))

(defn attempt-conquest-computer
  "Computer army attempts to conquer a city. Returns new position or nil if army died."
  [army-pos city-pos]
  (let [army-cell (get-in @atoms/game-map army-pos)
        army (:contents army-cell)
        city-cell (get-in @atoms/game-map city-pos)]
    (if (< (rand) 0.5)
      ;; Success - conquer the city, army dies
      (do
        (debug/log-computer-event! :army-conquest-success army-pos {:city city-pos})
        (swap! atoms/game-map assoc-in army-pos (dissoc army-cell :contents))
        (swap! atoms/game-map assoc-in city-pos (assoc city-cell :city-status :computer))
        (combat/conquer-city-contents city-pos :computer)
        (assign-country-on-conquest city-pos army)
        (let [city-country-id (:country-id (get-in @atoms/game-map city-pos))
              country-city-producing-armies? (requiring-resolve 'empire.computer.production/country-city-producing-armies?)]
          (when-not (and city-country-id
                         (country-city-producing-armies? city-pos city-country-id))
            (production/set-city-production city-pos :army)))
        (visibility/update-cell-visibility army-pos :computer)
        (visibility/update-cell-visibility city-pos :computer)
        nil)
      ;; Failure - army dies
      (do
        (debug/log-computer-event! :army-conquest-fail army-pos {:city city-pos})
        (swap! atoms/game-map assoc-in army-pos (dissoc army-cell :contents))
        (visibility/update-cell-visibility army-pos :computer)
        nil))))

(defn- adjacent?
  "Returns true if pos1 and pos2 are adjacent (including diagonally)."
  [pos1 pos2]
  (let [[r1 c1] pos1
        [r2 c2] pos2
        dr (Math/abs (- r2 r1))
        dc (Math/abs (- c2 c1))]
    (and (<= dr 1) (<= dc 1) (not (and (zero? dr) (zero? dc))))))

(defn wake-nearby-sentries
  "Wakes sentry armies within radius Chebyshev distance of pos.
   Each woken army gets interior-explore-direction pointing away from pos.
   Returns count of armies woken."
  [pos radius]
  (let [[pc pr] pos
        game-map @atoms/game-map
        woken (atom 0)]
    (doseq [c (range (max 0 (- pc radius)) (min (count game-map) (+ pc radius 1)))
            r (range (max 0 (- pr radius)) (min (count (first game-map)) (+ pr radius 1)))
            :when (not= [c r] pos)
            :let [cell (get-in game-map [c r])
                  unit (:contents cell)]
            :when (and unit
                       (= :army (:type unit))
                       (= :computer (:owner unit))
                       (= :sentry (:mode unit))
                       (<= (chebyshev-distance pos [c r]) radius))]
      (let [dc (Integer/signum (- c pc))
            dr (Integer/signum (- r pr))
            direction [(if (zero? dc) (if (< (rand) 0.5) -1 1) dc)
                       (if (zero? dr) (if (< (rand) 0.5) -1 1) dr)]]
        (swap! atoms/game-map update-in (conj [c r] :contents)
               #(-> % (assoc :mode :awake
                              :interior-explore-direction direction)
                    (dissoc :move-history)))
        (swap! woken inc)))
    @woken))

(defn board-transport
  "Loads army onto transport. Removes army from pos, increments transport army count.
   Verifies adjacency before loading. Wakes nearby sentries to advance the queue."
  [army-pos transport-pos]
  (when-not (adjacent? army-pos transport-pos)
    (throw (ex-info "Cannot board transport from non-adjacent cell"
                    {:army-pos army-pos :transport-pos transport-pos})))
  (swap! atoms/game-map update-in army-pos dissoc :contents)
  (swap! atoms/game-map update-in (conj transport-pos :contents :army-count) (fnil inc 0))
  (wake-nearby-sentries army-pos 3))

(defn find-visible-player-units
  "Finds player units visible on computer-map."
  []
  (for [i (range (count @atoms/computer-map))
        j (range (count (first @atoms/computer-map)))
        :let [cell (get-in @atoms/computer-map [i j])
              contents (:contents cell)]
        :when (and contents (= (:owner contents) :player))]
    [i j]))

;; Army-Transport Coordination (used by army module)

(defn- transport-compatible?
  "Returns true if the transport doesn't have a matching unload-event-id as the army.
   An army should not board the same transport that unloaded it."
  [transport-unit army-unload-event-id]
  (or (nil? army-unload-event-id)
      (nil? (:unload-event-id transport-unit))
      (not= (:unload-event-id transport-unit) army-unload-event-id)))

(defn find-loading-transport
  "Finds a transport in loading state that has room.
   When army-unload-event-id is provided, excludes transports with matching ID."
  ([]
   (find-loading-transport nil))
  ([army-unload-event-id]
   (first (for [i (range (count @atoms/game-map))
                j (range (count (first @atoms/game-map)))
                :let [cell (get-in @atoms/game-map [i j])
                      unit (:contents cell)]
                :when (and unit
                           (= :computer (:owner unit))
                           (= :transport (:type unit))
                           (= :loading (:transport-mission unit))
                           (< (:army-count unit 0) 6)
                           (transport-compatible? unit army-unload-event-id))]
            [i j]))))

(defn find-adjacent-loading-transport
  "Finds an adjacent loading transport with room.
   When army-unload-event-id is provided, excludes transports with matching ID."
  ([pos]
   (find-adjacent-loading-transport pos nil))
  ([pos army-unload-event-id]
   (first (filter (fn [neighbor]
                    (let [cell (get-in @atoms/game-map neighbor)
                          unit (:contents cell)]
                      (and unit
                           (= :computer (:owner unit))
                           (= :transport (:type unit))
                           (= :loading (:transport-mission unit))
                           (< (:army-count unit 0) 6)
                           (transport-compatible? unit army-unload-event-id))))
                  (get-neighbors pos)))))
