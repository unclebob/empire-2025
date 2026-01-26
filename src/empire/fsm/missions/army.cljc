(ns empire.fsm.missions.army
  "Army explorer mission FSMs - coastline and interior exploration."
  (:require [empire.fsm.engine :as engine]
            [empire.fsm.context :as context]
            [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]))

;; --- Helper Functions ---

(defn- cell-type-at
  "Get cell type at coordinates from game-map."
  [coords]
  (when-let [cell (get-in @atoms/game-map coords)]
    (:type cell)))

(defn on-coastline?
  "Returns true if position is land adjacent to sea."
  [coords]
  (boolean
    (and (= :land (cell-type-at coords))
         (seq (map-utils/get-matching-neighbors coords @atoms/game-map map-utils/neighbor-offsets
                                                 #(= :sea (:type %)))))))

(defn find-adjacent-free-city
  "Find a free city adjacent to the given position, if any."
  [_ctx coords]
  (first (map-utils/get-matching-neighbors coords @atoms/game-map map-utils/neighbor-offsets
                                           #(and (= :city (:type %))
                                                 (= :free (:city-status %))))))

(defn find-unexplored-direction
  "Find a direction toward unexplored territory. Returns [dr dc] or nil."
  [ctx coords]
  (let [computer-map (:computer-map ctx)
        unexplored (map-utils/get-matching-neighbors coords computer-map map-utils/neighbor-offsets
                                                     #(= :unexplored (:type %)))]
    (when (seq unexplored)
      (let [[nr nc] (first unexplored)
            [r c] coords]
        [(- nr r) (- nc c)]))))

;; --- Event Constructors ---

(defn make-city-found-event
  "Create a free-city-found event."
  [coords from-id]
  {:type :free-city-found
   :priority :high
   :data {:coords coords}
   :from from-id})

(defn make-coastline-mapped-event
  "Create a coastline-mapped event for beach candidate."
  [coords from-id]
  {:type :coastline-mapped
   :priority :normal
   :data {:coords coords}
   :from from-id})

;; --- Coastline Explorer FSM ---

(defn- coastline-continue?
  "Guard: can continue coastline exploration."
  [_ctx]
  true)  ; Simplified - always continue unless terminal

(def coastline-explorer-fsm
  "FSM for coastline exploration mission."
  [[:exploring coastline-continue? :exploring (constantly nil)]])

(defn create-coastline-explorer
  "Create an army unit with coastline exploration mission."
  [lieutenant-id start-pos]
  {:fsm coastline-explorer-fsm
   :fsm-state :exploring
   :fsm-data {:mission-type :explore-coastline
              :start-pos start-pos
              :current-pos start-pos
              :lieutenant-id lieutenant-id
              :visited #{start-pos}}
   :event-queue []})

;; --- Interior Explorer FSM ---

(defn- interior-continue?
  "Guard: can continue interior exploration."
  [_ctx]
  true)  ; Simplified - always continue unless terminal

(def interior-explorer-fsm
  "FSM for interior exploration mission."
  [[:exploring interior-continue? :exploring (constantly nil)]])

(defn create-interior-explorer
  "Create an army unit with interior exploration mission."
  [lieutenant-id start-pos]
  {:fsm interior-explorer-fsm
   :fsm-state :exploring
   :fsm-data {:mission-type :explore-interior
              :start-pos start-pos
              :current-pos start-pos
              :lieutenant-id lieutenant-id
              :visited #{start-pos}}
   :event-queue []})

;; --- Processing ---

(defn process-explorer
  "Process one step of an explorer's FSM."
  [explorer]
  (let [ctx (context/build-context explorer)]
    (engine/step explorer ctx)))
