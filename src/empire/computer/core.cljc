;; mutation-tested: 2026-03-03
(ns empire.computer.core
  "Shared utilities for computer AI modules."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.ports.movement :as movement-port]
            [empire.application.state :as app-state]
            [empire.computer.core.transport-search :as transport-search]
            [empire.debug :as debug]
            [empire.combat :as combat]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

(defn- movement-services
  []
  (:movement-port @state-ctx))

(defn- country-city-producing-armies?
  [city-pos country-id]
  (if-let [f (:country-city-producing-armies? @state-ctx)]
    (f city-pos country-id)
    false))

(defn- set-city-production!
  [city-pos item]
  (if-let [f (:set-city-production! @state-ctx)]
    (f city-pos item)
    nil))

(defn- update-cell-visibility!
  ([pos owner]
   (movement-port/movement-update-cell-visibility (movement-services) pos owner))
  ([pos owner unit]
   (movement-port/movement-update-cell-visibility-with-unit (movement-services) pos owner unit)))

(def ^:private neighbor-offsets
  [[-1 -1] [-1 0] [-1 1]
   [0 -1]          [0 1]
   [1 -1]  [1 0]  [1 1]])

(defn- neighbors-in-map
  [the-map [r c]]
  (if (and (sequential? the-map) (seq the-map) (sequential? (first the-map)))
    (let [height (count the-map)
          width (count (first the-map))]
      (for [[dr dc] neighbor-offsets
            :let [nr (+ r dr)
                  nc (+ c dc)]
            :when (and (<= 0 nr) (< nr height)
                       (<= 0 nc) (< nc width))]
        [nr nc]))
    []))

(defn- on-same-continent?
  [country-a country-b]
  ((:on-same-continent? @state-ctx) country-a country-b))

(defn- update-runtime-state!
  [k f & args]
  (let [current (read-runtime-state k)
        next-state (apply f current args)]
    (write-runtime-state! k next-state)))

(defmulti get-neighbors (fn [& _] :default))
(defmethod get-neighbors :default
  [pos]
  (neighbors-in-map (current-world) pos))

(defmulti distance (fn [& _] :default))
(defmethod distance :default
  [[x1 y1] [x2 y2]]
  (+ (Math/abs (- x2 x1)) (Math/abs (- y2 y1))))

(defmulti chebyshev-distance (fn [& _] :default))
(defmethod chebyshev-distance :default
  [[r1 c1] [r2 c2]]
  (max (Math/abs (- r2 r1)) (Math/abs (- c2 c1))))

(defmulti attackable-target? (fn [& _] :default))
(defmethod attackable-target? :default
  [cell]
  (or (and (= (:type cell) :city)
           (#{:player :free} (:city-status cell)))
      (and (:contents cell)
           (= (:owner (:contents cell)) :player)
           (not= :satellite (:type (:contents cell))))))

(defmulti find-visible-cities (fn [& _] :default))
(defmethod find-visible-cities :default
  [status-pred]
  (let [comp-map (read-runtime-state :computer-map)]
    (for [i (range (count comp-map))
          j (range (count (first comp-map)))
          :let [cell (get-in comp-map [i j])]
          :when (and (= (:type cell) :city)
                     (status-pred (:city-status cell)))]
      [i j])))

(defmulti move-toward (fn [& _] :default))
(defmethod move-toward :default
  [pos target passable-neighbors]
  (when (seq passable-neighbors)
    (apply min-key #(distance % target) passable-neighbors)))

(defmulti adjacent-to-computer-unexplored? (fn [& _] :default))
(defmethod adjacent-to-computer-unexplored? :default
  [pos]
  (let [comp-map (read-runtime-state :computer-map)]
    (boolean (some #(nil? (get-in comp-map %))
                   (neighbors-in-map comp-map pos)))))

(defmulti stamp-territory (fn [& _] :default))
(defmethod stamp-territory :default
  [pos unit]
  (when (and (= :army (:type unit))
             (= :computer (:owner unit))
             (:country-id unit)
             (#{:land :city} (:type (get-in (current-world) pos))))
    (update-game-map! assoc-in (conj pos :country-id) (:country-id unit))))

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

(defmulti move-unit-to (fn [& _] :default))
(defmethod move-unit-to :default
  [from-pos to-pos]
  (let [from-cell (get-in (current-world) from-pos)
        to-cell (get-in (current-world) to-pos)
        unit (:contents from-cell)]
    (cond
      (:contents to-cell) nil
      (foreign-territory? unit to-cell) nil
      :else
      (do
        (update-game-map! assoc-in from-pos (dissoc from-cell :contents))
        (update-game-map! assoc-in (conj to-pos :contents) unit)
        (stamp-territory to-pos unit)
        (update-cell-visibility! from-pos (:owner unit))
        (update-cell-visibility! to-pos (:owner unit) unit)
        to-pos))))

(defmulti attempt-conquest-computer (fn [& _] :default))
(defmethod attempt-conquest-computer :default
  [army-pos city-pos]
  (let [army-cell (get-in (current-world) army-pos)
        army (:contents army-cell)
        city-cell (get-in (current-world) city-pos)]
    (if (< (rand) 0.5)
      ;; Success - conquer the city, army dies
      (do
        (debug/log-computer-event! :army-conquest-success army-pos {:city city-pos})
        (update-game-map! assoc-in army-pos (dissoc army-cell :contents))
        (update-game-map! assoc-in city-pos (assoc city-cell :city-status :computer))
        (update-runtime-state! :computer-city-positions (fnil conj #{}) city-pos)
        (combat/conquer-city-contents city-pos :computer)
        (stamp-territory city-pos army)
        ;; Player-map updates only when the player loses a city.
        ;; Computer conquest of free cities must not update player-map.
        (when (= :player (:city-status city-cell))
          (update-runtime-state! :player-map assoc-in city-pos (get-in (current-world) city-pos)))
        (let [city-country-id (:country-id (get-in (current-world) city-pos))]
          (when-not (and city-country-id
                         (country-city-producing-armies? city-pos city-country-id))
            (set-city-production! city-pos :army)))
        (update-cell-visibility! army-pos :computer)
        (update-cell-visibility! city-pos :computer)
        nil)
      ;; Failure - army dies
      (do
        (debug/log-computer-event! :army-conquest-fail army-pos {:city city-pos})
        (update-game-map! assoc-in army-pos (dissoc army-cell :contents))
        (update-cell-visibility! army-pos :computer)
        nil))))

(defn- adjacent?
  "Returns true if pos1 and pos2 are adjacent (including diagonally)."
  [pos1 pos2]
  (let [[r1 c1] pos1
        [r2 c2] pos2
        dr (Math/abs (- r2 r1))
        dc (Math/abs (- c2 c1))]
    (and (<= dr 1) (<= dc 1) (not (and (zero? dr) (zero? dc))))))

(defmulti random-away-direction (fn [& _] :default))
(defmethod random-away-direction :default
  [origin target]
  (let [[oc or'] origin
        [tc tr] target
        dc (Integer/signum (- tc oc))
        dr (Integer/signum (- tr or'))]
    [(if (zero? dc) (if (< (rand) 0.5) -1 1) dc)
     (if (zero? dr) (if (< (rand) 0.5) -1 1) dr)]))

(defmulti find-wakeable-sentries (fn [& _] :default))
(defmethod find-wakeable-sentries :default
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
                     (<= (chebyshev-distance pos [c r]) radius))]
      [c r])))

(defmulti wake-nearby-sentries (fn [& _] :default))
(defmethod wake-nearby-sentries :default
  [pos radius]
  (let [candidates (find-wakeable-sentries (current-world) pos radius)]
    (doseq [coord candidates
            :let [direction (random-away-direction pos coord)]]
      (update-game-map! update-in (conj coord :contents)
                        #(-> % (assoc :mode :awake
                                      :interior-explore-direction direction)
                             (dissoc :move-history))))
    (count candidates)))

(defmulti board-transport (fn [& _] :default))
(defmethod board-transport :default
  [army-pos transport-pos]
  (when-not (adjacent? army-pos transport-pos)
    (throw (ex-info "Cannot board transport from non-adjacent cell"
                    {:army-pos army-pos :transport-pos transport-pos})))
  (update-game-map! update-in army-pos dissoc :contents)
  (update-game-map! update-in (conj transport-pos :contents :army-count) (fnil inc 0))
  (wake-nearby-sentries army-pos 3))

(defmulti find-visible-player-units (fn [& _] :default))
(defmethod find-visible-player-units :default
  []
  (let [comp-map (read-runtime-state :computer-map)]
    (for [i (range (count comp-map))
          j (range (count (first comp-map)))
          :let [cell (get-in comp-map [i j])
                contents (:contents cell)]
          :when (and contents (= (:owner contents) :player))]
      [i j])))

;; Army-Transport Coordination (used by army module)
(defmulti find-loading-transport (fn [& _] :default))
(defmethod find-loading-transport :default
  ([] (find-loading-transport nil))
  ([army-unload-event-id]
   (transport-search/find-loading-transport (current-world) army-unload-event-id)))

(defmulti find-adjacent-loading-transport (fn [& _] :default))
(defmethod find-adjacent-loading-transport :default
  ([pos]
   (find-adjacent-loading-transport pos nil))
  ([pos army-unload-event-id]
   (transport-search/find-adjacent-loading-transport (current-world)
                                                     get-neighbors
                                                     pos
                                                     army-unload-event-id)))
