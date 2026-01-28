(ns empire.fsm.board-transport
  "Board-Transport FSM - army moves to beach and boards a transport.
   Phase 0: Move toward assigned beach cell adjacent to transport-landing.
   Phase 1: When on beach adjacent to transport, attempt to board.
   Terminal: Successfully boarded, stuck, or transport gone."
  (:require [empire.atoms :as atoms]
            [empire.movement.pathfinding :as pathfinding]
            [empire.fsm.explorer-utils :as utils]
            [empire.ui.coordinates :as coords]))

;; --- Helper Functions ---

(defn- adjacent?
  "Returns true if pos1 and pos2 are orthogonally or diagonally adjacent."
  [pos1 pos2]
  (let [[r1 c1] pos1
        [r2 c2] pos2
        dr (Math/abs (- r1 r2))
        dc (Math/abs (- c1 c2))]
    (and (<= dr 1) (<= dc 1) (not (and (= dr 0) (= dc 0))))))

(defn- transport-at-landing?
  "Returns true if a computer transport is at the transport-landing position."
  [transport-landing]
  (let [cell (get-in @atoms/game-map transport-landing)
        contents (:contents cell)]
    (and contents
         (= :transport (:type contents))
         (= :computer (:owner contents)))))

(defn- transport-army-count
  "Returns the number of armies on the transport at given position."
  [transport-pos]
  (let [cell (get-in @atoms/game-map transport-pos)]
    (get cell :army-count 0)))

(defn- find-sidestep-move
  "Find a move that makes progress toward beach cell while avoiding direct path.
   Prefers non-backtrack moves. Returns [row col] or nil."
  [pos beach-cell recent-moves]
  (let [valid-moves (utils/get-valid-moves pos @atoms/game-map)
        non-backtrack (remove (set recent-moves) valid-moves)
        moves-to-try (if (seq non-backtrack) non-backtrack valid-moves)]
    (when (seq moves-to-try)
      (let [scored (map (fn [m] [m (coords/manhattan-distance m beach-cell)]) moves-to-try)
            min-dist (apply min (map second scored))
            best-moves (filter #(= min-dist (second %)) scored)]
        (first (rand-nth best-moves))))))

;; --- Guards ---

(defn- stuck?
  "Guard: Returns true if there are no valid moves available."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        valid-moves (utils/get-valid-moves pos @atoms/game-map)]
    (empty? valid-moves)))

(defn- transport-gone?
  "Guard: Returns true if transport is no longer at expected transport-landing."
  [ctx]
  (let [transport-landing (get-in ctx [:entity :fsm-data :transport-landing])]
    (not (transport-at-landing? transport-landing))))

(defn- adjacent-to-transport?
  "Guard: Returns true if army is on a beach cell adjacent to transport-landing."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        transport-landing (get-in ctx [:entity :fsm-data :transport-landing])]
    (adjacent? pos transport-landing)))

(defn- transport-has-room?
  "Guard: Returns true if transport has space for another army (max 6)."
  [ctx]
  (let [transport-landing (get-in ctx [:entity :fsm-data :transport-landing])
        army-count (transport-army-count transport-landing)]
    (< army-count 6)))

(defn- transport-full?
  "Guard: Returns true if transport is full (6 armies)."
  [ctx]
  (let [transport-landing (get-in ctx [:entity :fsm-data :transport-landing])
        army-count (transport-army-count transport-landing)]
    (>= army-count 6)))

(defn- can-move-toward?
  "Guard: Returns true if pathfinding can find next step toward beach cell."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        beach-cell (get-in ctx [:entity :fsm-data :assigned-beach-cell])
        next-pos (when beach-cell (pathfinding/next-step-toward pos beach-cell))]
    (boolean next-pos)))

(defn- needs-sidestep?
  "Guard: Returns true if direct path is blocked but sidestep move exists."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        beach-cell (get-in ctx [:entity :fsm-data :assigned-beach-cell])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])]
    (boolean (find-sidestep-move pos beach-cell recent-moves))))

(defn- always [_ctx] true)

;; --- Actions ---

(defn- terminal-stuck-action
  "Action: Called when stuck with no valid moves. Notifies Lieutenant."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [(utils/make-mission-ended-event unit-id :stuck)]}))

(defn- terminal-abort-action
  "Action: Called when transport is gone. Notifies Lieutenant."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [(utils/make-mission-ended-event unit-id :aborted)]}))

(defn- board-action
  "Action: Execute boarding - army moves onto transport. Notifies Lieutenant."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])
        transport-id (get-in ctx [:entity :fsm-data :transport-id])
        transport-landing (get-in ctx [:entity :fsm-data :transport-landing])]
    {:board-transport transport-landing
     :events [{:type :army-boarded
               :priority :normal
               :data {:transport-id transport-id :army-id unit-id}}
              (utils/make-mission-ended-event unit-id :boarded)]}))

(defn- prepare-board-action
  "Action: Prepare for boarding, verify transport still present."
  [_ctx]
  nil)

(defn- wait-for-space-action
  "Action: Wait for transport to have room (another army disembarks or transport is replaced)."
  [_ctx]
  nil)

(defn- move-toward-action
  "Action: Move toward assigned beach cell using pathfinding."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        beach-cell (get-in ctx [:entity :fsm-data :assigned-beach-cell])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        next-pos (pathfinding/next-step-toward pos beach-cell)]
    (when next-pos
      {:move-to next-pos
       :recent-moves (utils/update-recent-moves recent-moves next-pos)})))

(defn- sidestep-action
  "Action: Sidestep around obstacle while moving toward beach cell."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        beach-cell (get-in ctx [:entity :fsm-data :assigned-beach-cell])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        sidestep-pos (find-sidestep-move pos beach-cell recent-moves)]
    (when sidestep-pos
      {:move-to sidestep-pos
       :recent-moves (utils/update-recent-moves recent-moves sidestep-pos)})))

;; --- FSM Definition ---

(def board-transport-fsm
  "FSM transitions for board-transport mission.
   Format: [current-state guard-fn new-state action-fn]

   States:
   - :moving-to-beach       - Pathfinding toward assigned beach cell
   - :boarding              - On beach cell adjacent to transport, boarding
   - [:terminal :boarded]   - Successfully on transport
   - [:terminal :stuck]     - No valid moves available
   - [:terminal :aborted]   - Transport gone or no longer available"
  [;; Moving to beach transitions (order matters - first matching wins)
   [:moving-to-beach  stuck?                 [:terminal :stuck]    terminal-stuck-action]
   [:moving-to-beach  transport-gone?        [:terminal :aborted]  terminal-abort-action]
   [:moving-to-beach  adjacent-to-transport? :boarding             prepare-board-action]
   [:moving-to-beach  can-move-toward?       :moving-to-beach      move-toward-action]
   [:moving-to-beach  needs-sidestep?        :moving-to-beach      sidestep-action]

   ;; Boarding transitions
   [:boarding  transport-gone?        [:terminal :aborted]  terminal-abort-action]
   [:boarding  transport-has-room?    [:terminal :boarded]  board-action]
   [:boarding  transport-full?        :boarding             wait-for-space-action]
   [:boarding  always                 :boarding             wait-for-space-action]])

;; --- Create Mission ---

(defn create-board-transport-data
  "Create FSM data for board-transport mission.
   pos - current army position
   transport-id - specific transport to board
   transport-landing - sea cell where transport docks
   assigned-beach-cell - specific beach cell to reach (adjacent to transport-landing)
   unit-id - for Lieutenant tracking"
  [pos transport-id transport-landing assigned-beach-cell unit-id]
  (let [at-beach-adjacent? (and (= pos assigned-beach-cell)
                                (adjacent? pos transport-landing))]
    {:fsm board-transport-fsm
     :fsm-state (if at-beach-adjacent? :boarding :moving-to-beach)
     :fsm-data {:mission-type :board-transport
                :position pos
                :transport-id transport-id
                :transport-landing transport-landing
                :assigned-beach-cell assigned-beach-cell
                :unit-id unit-id
                :recent-moves [pos]}}))
