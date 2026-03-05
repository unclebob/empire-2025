;; mutation-tested: no
(ns empire.computer.core.impl
  "Default implementations for computer.core multimethods."
  (:require [empire.application.ports.movement :as movement-port]
            [empire.application.state-access :as sa]
            [empire.combat :as combat]
            [empire.computer.core :as core]
            [empire.computer.core.transport-search :as transport-search]
            [empire.debug :as debug]))

(defn- movement-services
  []
  (:movement-port (sa/state-ctx)))

(defn- country-city-producing-armies?
  [city-pos country-id]
  (if-let [f (:country-city-producing-armies? (sa/state-ctx))]
    (f city-pos country-id)
    false))

(defn- set-city-production!
  [city-pos item]
  (if-let [f (:set-city-production! (sa/state-ctx))]
    (f city-pos item)
    nil))

(defn- update-cell-visibility!
  ([pos owner]
   (movement-port/movement-update-cell-visibility (movement-services) pos owner))
  ([pos owner unit]
   (movement-port/movement-update-cell-visibility-with-unit (movement-services) pos owner unit)))

(defn- on-same-continent?
  [country-a country-b]
  ((:on-same-continent? (sa/state-ctx)) country-a country-b))

(defn- foreign-territory?
  "Returns true if unit is a computer army with a country-id and the target
   land cell has a different country-id. Cities are always passable."
  [unit to-cell]
  (and (= :army (:type unit))
       (= :computer (:owner unit))
       (:country-id unit)
       (= :land (:type to-cell))
       (:country-id to-cell)
       (not (on-same-continent? (:country-id unit) (:country-id to-cell)))))

(defmethod core/get-neighbors :default
  [pos]
  (core/neighbors-in-map (sa/current-world) pos))

(defmethod core/distance :default
  [[x1 y1] [x2 y2]]
  (+ (Math/abs (- x2 x1)) (Math/abs (- y2 y1))))

(defmethod core/chebyshev-distance :default
  [[r1 c1] [r2 c2]]
  (max (Math/abs (- r2 r1)) (Math/abs (- c2 c1))))

(defmethod core/attackable-target? :default
  [cell]
  (or (and (= (:type cell) :city)
           (#{:player :free} (:city-status cell)))
      (and (:contents cell)
           (= (:owner (:contents cell)) :player)
           (not= :satellite (:type (:contents cell))))))

(defmethod core/find-visible-cities :default
  [status-pred]
  (let [comp-map (sa/read-state :computer-map)]
    (for [i (range (count comp-map))
          j (range (count (first comp-map)))
          :let [cell (get-in comp-map [i j])]
          :when (and (= (:type cell) :city)
                     (status-pred (:city-status cell)))]
      [i j])))

(defmethod core/move-toward :default
  [pos target passable-neighbors]
  (when (seq passable-neighbors)
    (apply min-key #(core/distance % target) passable-neighbors)))

(defmethod core/adjacent-to-computer-unexplored? :default
  [pos]
  (let [comp-map (sa/read-state :computer-map)]
    (boolean (some #(nil? (get-in comp-map %))
                   (core/neighbors-in-map comp-map pos)))))

(defmethod core/stamp-territory :default
  [pos unit]
  (when (and (= :army (:type unit))
             (= :computer (:owner unit))
             (:country-id unit)
             (#{:land :city} (:type (get-in (sa/current-world) pos))))
    (sa/update-world! assoc-in (conj pos :country-id) (:country-id unit))))

(defmethod core/move-unit-to :default
  [from-pos to-pos]
  (let [from-cell (get-in (sa/current-world) from-pos)
        to-cell (get-in (sa/current-world) to-pos)
        unit (:contents from-cell)]
    (cond
      (:contents to-cell) nil
      (foreign-territory? unit to-cell) nil
      :else
      (do
        (sa/update-world! assoc-in from-pos (dissoc from-cell :contents))
        (sa/update-world! assoc-in (conj to-pos :contents) unit)
        (core/stamp-territory to-pos unit)
        (update-cell-visibility! from-pos (:owner unit))
        (update-cell-visibility! to-pos (:owner unit) unit)
        to-pos))))

(defmethod core/attempt-conquest-computer :default
  [army-pos city-pos]
  (let [army-cell (get-in (sa/current-world) army-pos)
        army (:contents army-cell)
        city-cell (get-in (sa/current-world) city-pos)]
    (if (< (rand) 0.5)
      ;; Success - conquer the city, army dies
      (do
        (debug/log-computer-event! :army-conquest-success army-pos {:city city-pos})
        (sa/update-world! assoc-in army-pos (dissoc army-cell :contents))
        (sa/update-world! assoc-in city-pos (assoc city-cell :city-status :computer))
        (sa/update-state! :computer-city-positions (fnil conj #{}) city-pos)
        (combat/conquer-city-contents city-pos :computer)
        (core/stamp-territory city-pos army)
        ;; Player-map updates only when the player loses a city.
        ;; Computer conquest of free cities must not update player-map.
        (when (= :player (:city-status city-cell))
          (sa/update-state! :player-map assoc-in city-pos (get-in (sa/current-world) city-pos)))
        (let [city-country-id (:country-id (get-in (sa/current-world) city-pos))]
          (when-not (and city-country-id
                         (country-city-producing-armies? city-pos city-country-id))
            (set-city-production! city-pos :army)))
        (update-cell-visibility! army-pos :computer)
        (update-cell-visibility! city-pos :computer)
        nil)
      ;; Failure - army dies
      (do
        (debug/log-computer-event! :army-conquest-fail army-pos {:city city-pos})
        (sa/update-world! assoc-in army-pos (dissoc army-cell :contents))
        (update-cell-visibility! army-pos :computer)
        nil))))

(defmethod core/random-away-direction :default
  [origin target]
  (let [[oc or'] origin
        [tc tr] target
        dc (Integer/signum (- tc oc))
        dr (Integer/signum (- tr or'))]
    [(if (zero? dc) (if (< (rand) 0.5) -1 1) dc)
     (if (zero? dr) (if (< (rand) 0.5) -1 1) dr)]))

(defmethod core/find-wakeable-sentries :default
  [game-map pos radius]
  (let [[pc pr] pos]
    (for [c (range (max 0 (- pc radius)) (min (count game-map) (+ pc radius 1)))
          r (range (max 0 (- pr radius)) (min (count (first game-map)) (+ pr radius 1)))
          :when (not= [c r] pos)
          :let [cell (get-in game-map [c r])
                unit (:contents cell)]
          :when (and unit
                     (= :army (:type unit))
                     (= :computer (:owner unit))
                     (= :sentry (:mode unit))
                     (<= (core/chebyshev-distance pos [c r]) radius))]
      [c r])))

(defmethod core/wake-nearby-sentries :default
  [pos radius]
  (let [candidates (core/find-wakeable-sentries (sa/current-world) pos radius)]
    (doseq [coord candidates
            :let [direction (core/random-away-direction pos coord)]]
      (sa/update-world! update-in (conj coord :contents)
                        #(-> % (assoc :mode :awake
                                      :interior-explore-direction direction)
                             (dissoc :move-history))))
    (count candidates)))

(defmethod core/board-transport :default
  [army-pos transport-pos]
  (when-not (core/adjacent? army-pos transport-pos)
    (throw (ex-info "Cannot board transport from non-adjacent cell"
                    {:army-pos army-pos :transport-pos transport-pos})))
  (sa/update-world! update-in army-pos dissoc :contents)
  (sa/update-world! update-in (conj transport-pos :contents :army-count) (fnil inc 0))
  (core/wake-nearby-sentries army-pos 3))

(defmethod core/find-visible-player-units :default
  []
  (let [comp-map (sa/read-state :computer-map)]
    (for [i (range (count comp-map))
          j (range (count (first comp-map)))
          :let [cell (get-in comp-map [i j])
                contents (:contents cell)]
          :when (and contents (= (:owner contents) :player))]
      [i j])))

(defmethod core/find-loading-transport :default
  ([] (core/find-loading-transport nil))
  ([army-unload-event-id]
   (transport-search/find-loading-transport (sa/current-world) army-unload-event-id)))

(defmethod core/find-adjacent-loading-transport :default
  ([pos]
   (core/find-adjacent-loading-transport pos nil))
  ([pos army-unload-event-id]
   (transport-search/find-adjacent-loading-transport (sa/current-world)
                                                     core/get-neighbors
                                                     pos
                                                     army-unload-event-id)))
