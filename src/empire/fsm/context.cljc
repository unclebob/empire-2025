(ns empire.fsm.context
  "Build context maps for FSM predicates and actions.
   Context provides access to entity state and game world."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]))

(defn build-context
  "Build a context map for FSM guard and action functions.
   Includes entity state and relevant game world data."
  [entity]
  {:entity entity
   :fsm-data (:fsm-data entity)
   :event-queue (:event-queue entity)
   :game-map @atoms/game-map
   :computer-map @atoms/computer-map
   :round-number @atoms/round-number})

(defn has-event?
  "Returns true if entity's queue contains an event of the given type."
  [context event-type]
  (boolean (some #(= event-type (:type %)) (:event-queue context))))

(defn get-cell
  "Get cell from game-map at given [row col] coordinates."
  [context coords]
  (get-in (:game-map context) coords))

(defn get-computer-cell
  "Get cell from computer's fog-of-war map at given [row col] coordinates."
  [context coords]
  (get-in (:computer-map context) coords))

(defn cell-explored?
  "Returns true if the computer has explored the cell at given coordinates."
  [context coords]
  (let [cell (get-computer-cell context coords)]
    (and cell (not= :unexplored (:type cell)))))

(defn- valid-explore-cell?
  "Returns true if a cell is valid for army exploration (land, no city, no unit)."
  [cell]
  (and cell
       (= :land (:type cell))
       (nil? (:contents cell))))

(defn get-valid-army-moves
  "Returns list of valid adjacent positions for army exploration from position."
  [context pos]
  (map-utils/get-matching-neighbors pos (:game-map context) map-utils/neighbor-offsets
                                    valid-explore-cell?))
