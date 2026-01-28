(ns empire.fsm.rally-to-squad
  "Rally-to-Squad FSM - army moves to rally point and joins a squad.
   Phase 0: Move toward rally-point using pathfinding.
   Phase 1: If rally-point blocked, sidestep to nearby empty land adjacent to rally-point.
   Terminal: Join squad and emit :unit-arrived event, or get stuck."
  (:require [empire.atoms :as atoms]
            [empire.movement.pathfinding :as pathfinding]
            [empire.fsm.explorer-utils :as utils]
            [empire.ui.coordinates :as coords]))

;; --- Helper Functions ---

(defn- blocked-by-army-or-city?
  "Returns true if the cell at pos contains a friendly army or is a friendly city."
  [pos]
  (let [cell (get-in @atoms/game-map pos)]
    (or
     ;; Friendly city
     (and (= :city (:type cell))
          (= :computer (:city-status cell)))
     ;; Friendly army
     (and (:contents cell)
          (= :army (:type (:contents cell)))
          (= :computer (:owner (:contents cell)))))))

(defn- adjacent?
  "Returns true if pos1 and pos2 are orthogonally or diagonally adjacent."
  [pos1 pos2]
  (let [[r1 c1] pos1
        [r2 c2] pos2
        dr (Math/abs (- r1 r2))
        dc (Math/abs (- c1 c2))]
    (and (<= dr 1) (<= dc 1) (not (and (= dr 0) (= dc 0))))))

(defn- find-sidestep-move
  "Find a move that makes progress toward rally-point while avoiding direct path.
   Prefers non-backtrack moves. Returns [row col] or nil."
  [pos rally-point recent-moves]
  (let [valid-moves (utils/get-valid-moves pos @atoms/game-map)
        non-backtrack (remove (set recent-moves) valid-moves)
        moves-to-try (if (seq non-backtrack) non-backtrack valid-moves)]
    (when (seq moves-to-try)
      ;; Score moves by distance to rally-point, pick closest
      (let [scored (map (fn [m] [m (coords/manhattan-distance m rally-point)]) moves-to-try)
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

(defn- at-rally-point?
  "Guard: Returns true if current position equals rally-point."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally-point (get-in ctx [:entity :fsm-data :rally-point])]
    (= pos rally-point)))

(defn- rally-blocked?
  "Guard: Returns true if rally-point is occupied and army is adjacent to it."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally-point (get-in ctx [:entity :fsm-data :rally-point])]
    (and rally-point
         (adjacent? pos rally-point)
         (blocked-by-army-or-city? rally-point))))

(defn- can-move-toward?
  "Guard: Returns true if pathfinding can find next step toward rally-point."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally-point (get-in ctx [:entity :fsm-data :rally-point])
        next-pos (when rally-point (pathfinding/next-step-toward pos rally-point))]
    (boolean next-pos)))

(defn- needs-sidestep?
  "Guard: Returns true if direct path is blocked but sidestep move exists."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally-point (get-in ctx [:entity :fsm-data :rally-point])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])]
    (boolean (find-sidestep-move pos rally-point recent-moves))))

(defn- on-empty-land-adjacent-to-rally?
  "Guard: Returns true if current position is empty land cell adjacent to rally-point.
   In sidestepping state, we're already on the cell, so just verify it's land and adjacent."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally-point (get-in ctx [:entity :fsm-data :rally-point])
        cell (get-in @atoms/game-map pos)]
    (and (= :land (:type cell))
         (adjacent? pos rally-point))))

(defn- always [_ctx] true)

;; --- Event Creation ---

(defn- make-unit-arrived-event
  "Create a :unit-arrived event for the squad."
  [unit-id squad-id coords]
  {:type :unit-arrived
   :priority :high
   :data {:unit-id unit-id :squad-id squad-id :coords coords}})

;; --- Actions ---

(defn- terminal-action
  "Action: Called when the army is stuck with no valid moves.
   Emits a :mission-ended event to notify the Lieutenant."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [(utils/make-mission-ended-event unit-id :stuck)]}))

(defn- join-squad-action
  "Action: Called when arriving at rally-point or suitable adjacent land.
   Emits :unit-arrived event to squad and :mission-ended event to Lieutenant."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])
        squad-id (get-in ctx [:entity :fsm-data :squad-id])
        pos (get-in ctx [:entity :fsm-data :position])]
    {:events [(make-unit-arrived-event unit-id squad-id pos)
              (utils/make-mission-ended-event unit-id :joined)]}))

(defn- begin-sidestep-action
  "Action: Begin sidestepping to find empty land near blocked rally-point.
   Computes first move toward alternate landing spot."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally-point (get-in ctx [:entity :fsm-data :rally-point])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        sidestep-pos (find-sidestep-move pos rally-point recent-moves)]
    (when sidestep-pos
      {:move-to sidestep-pos
       :recent-moves (utils/update-recent-moves recent-moves sidestep-pos)})))

(defn- move-toward-action
  "Action: Move toward rally-point using pathfinding.
   Returns {:move-to [row col]}"
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally-point (get-in ctx [:entity :fsm-data :rally-point])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        next-pos (pathfinding/next-step-toward pos rally-point)]
    (when next-pos
      {:move-to next-pos
       :recent-moves (utils/update-recent-moves recent-moves next-pos)})))

(defn- sidestep-action
  "Action: Sidestep around obstacle while moving toward rally-point."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally-point (get-in ctx [:entity :fsm-data :rally-point])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        sidestep-pos (find-sidestep-move pos rally-point recent-moves)]
    (when sidestep-pos
      {:move-to sidestep-pos
       :recent-moves (utils/update-recent-moves recent-moves sidestep-pos)})))

(defn- sidestep-to-land-action
  "Action: Continue sidestepping to find empty land adjacent to rally-point."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        rally-point (get-in ctx [:entity :fsm-data :rally-point])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        sidestep-pos (find-sidestep-move pos rally-point recent-moves)]
    (when sidestep-pos
      {:move-to sidestep-pos
       :recent-moves (utils/update-recent-moves recent-moves sidestep-pos)})))

;; --- FSM Definition ---

(def rally-to-squad-fsm
  "FSM transitions for rally-to-squad mission.
   Format: [current-state guard-fn new-state action-fn]

   States:
   - :moving                - Pathfinding toward rally-point
   - :sidestepping-rally    - Rally-point blocked, finding nearby land
   - [:terminal :joined]    - Mission complete, joined squad
   - [:terminal :stuck]     - No valid moves available"
  [;; Moving transitions (order matters - first matching wins)
   [:moving  stuck?              [:terminal :stuck]      terminal-action]
   [:moving  at-rally-point?     [:terminal :joined]     join-squad-action]
   [:moving  rally-blocked?      :sidestepping-rally     begin-sidestep-action]
   [:moving  can-move-toward?    :moving                 move-toward-action]
   [:moving  needs-sidestep?     :moving                 sidestep-action]

   ;; Sidestepping transitions
   [:sidestepping-rally  stuck?                           [:terminal :stuck]    terminal-action]
   [:sidestepping-rally  on-empty-land-adjacent-to-rally? [:terminal :joined]   join-squad-action]
   [:sidestepping-rally  always                           :sidestepping-rally   sidestep-to-land-action]])

;; --- Create Mission ---

(defn create-rally-to-squad-data
  "Create FSM data for a rally-to-squad mission at given position.
   rally-point is where the army should move to join the squad.
   squad-id identifies the squad to join.
   Optional unit-id parameter identifies the unit for mission tracking.
   If already at rally-point, starts in terminal joined state."
  ([pos rally-point squad-id]
   (create-rally-to-squad-data pos rally-point squad-id nil))
  ([pos rally-point squad-id unit-id]
   (let [at-rally? (= pos rally-point)]
     {:fsm rally-to-squad-fsm
      :fsm-state (if at-rally? [:terminal :joined] :moving)
      :fsm-data {:mission-type :rally-to-squad
                 :position pos
                 :rally-point rally-point
                 :squad-id squad-id
                 :unit-id unit-id
                 :recent-moves [pos]}})))
