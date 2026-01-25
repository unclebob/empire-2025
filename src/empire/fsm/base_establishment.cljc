(ns empire.fsm.base-establishment
  "Base establishment logic - evaluates beach candidates and transport readiness."
  (:require [empire.atoms :as atoms]))

(defn- get-neighbors
  "Get all 8 neighboring coordinates for a position."
  [[row col]]
  (for [dr [-1 0 1]
        dc [-1 0 1]
        :when (not (and (zero? dr) (zero? dc)))]
    [(+ row dr) (+ col dc)]))

(defn- cell-type-at
  "Get cell type at coordinates from game-map."
  [coords]
  (when-let [cell (get-in @atoms/game-map coords)]
    (:type cell)))

(defn- count-adjacent-land
  "Count land cells adjacent to the given position."
  [coords]
  (count (filter #(= :land (cell-type-at %)) (get-neighbors coords))))

(defn valid-beach-candidate?
  "Returns true if position is a valid beach for transport unloading.
   Must be land, adjacent to sea, with at least 3 adjacent land cells."
  [coords]
  (let [cell-type (cell-type-at coords)]
    (boolean
      (and (= :land cell-type)
           (some #(= :sea (cell-type-at %)) (get-neighbors coords))
           (>= (count-adjacent-land coords) 3)))))

(defn transport-fully-loaded?
  "Returns true if transport has 6 armies aboard."
  [transport]
  (= 6 (:army-count transport 0)))

(defn base-establishment-ready?
  "Returns true if Lieutenant has beach candidates and a fully loaded transport."
  [lieutenant transports]
  (boolean
    (and (seq (:beach-candidates lieutenant))
         (some transport-fully-loaded? transports))))

(defn make-base-established-event
  "Create a base-established event."
  [beach-coords transport-id]
  {:type :base-established
   :priority :high
   :data {:beach-coords beach-coords
          :transport-id transport-id}})
