(ns empire.fsm.hurry-up-and-wait
  "Hurry-Up-And-Wait FSM - army moves to destination, then enters sentry mode.
   Phase 0: Move toward destination using pathfinding.
   Phase 1: If destination blocked, sidestep to nearby empty land.
   Terminal: Arrive and signal :enter-sentry-mode, or get stuck."
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

(defn- find-sidestep-move
  "Find a move that makes progress toward destination while avoiding direct path.
   Prefers non-backtrack moves. Returns [row col] or nil."
  [pos dest recent-moves]
  (let [valid-moves (utils/get-valid-moves pos @atoms/game-map)
        non-backtrack (remove (set recent-moves) valid-moves)
        moves-to-try (if (seq non-backtrack) non-backtrack valid-moves)]
    (when (seq moves-to-try)
      ;; Score moves by distance to destination, pick closest
      (let [scored (map (fn [m] [m (coords/manhattan-distance m dest)]) moves-to-try)
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

(defn- at-destination?
  "Guard: Returns true if current position equals destination."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dest (get-in ctx [:entity :fsm-data :destination])]
    (or (nil? dest) (= pos dest))))

(defn- destination-blocked?
  "Guard: Returns true if destination is occupied by friendly army or city."
  [ctx]
  (let [dest (get-in ctx [:entity :fsm-data :destination])]
    (and dest (blocked-by-army-or-city? dest))))

(defn- can-move-toward?
  "Guard: Returns true if pathfinding can find next step toward destination."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dest (get-in ctx [:entity :fsm-data :destination])
        next-pos (when dest (pathfinding/next-step-toward pos dest))]
    (boolean next-pos)))

(defn- needs-sidestep?
  "Guard: Returns true if direct path is blocked but sidestep move exists."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dest (get-in ctx [:entity :fsm-data :destination])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])]
    (boolean (find-sidestep-move pos dest recent-moves))))

(defn- on-empty-land?
  "Guard: Returns true if current position is empty land cell.
   In sidestepping state, we're already on the cell, so just verify it's land."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        cell (get-in @atoms/game-map pos)]
    (= :land (:type cell))))

(defn- always [_ctx] true)

;; --- Actions ---

(defn- terminal-action
  "Action: Called when the army is stuck with no valid moves.
   Emits a :mission-ended event to notify the Lieutenant."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [(utils/make-mission-ended-event unit-id :stuck)]}))

(defn- arrive-action
  "Action: Called when arriving at destination or suitable land.
   Sets :enter-sentry-mode flag and emits :mission-ended event."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:enter-sentry-mode true
     :events [(utils/make-mission-ended-event unit-id :arrived)]}))

(defn- begin-sidestep-action
  "Action: Begin sidestepping to find empty land near blocked destination.
   Computes first move toward alternate landing spot."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dest (get-in ctx [:entity :fsm-data :destination])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        sidestep-pos (find-sidestep-move pos dest recent-moves)]
    (when sidestep-pos
      {:move-to sidestep-pos
       :recent-moves (utils/update-recent-moves recent-moves sidestep-pos)})))

(defn- move-toward-action
  "Action: Move toward destination using pathfinding.
   Returns {:move-to [row col]}"
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dest (get-in ctx [:entity :fsm-data :destination])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        next-pos (pathfinding/next-step-toward pos dest)]
    (when next-pos
      {:move-to next-pos
       :recent-moves (utils/update-recent-moves recent-moves next-pos)})))

(defn- sidestep-action
  "Action: Sidestep around obstacle while moving toward destination."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dest (get-in ctx [:entity :fsm-data :destination])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        sidestep-pos (find-sidestep-move pos dest recent-moves)]
    (when sidestep-pos
      {:move-to sidestep-pos
       :recent-moves (utils/update-recent-moves recent-moves sidestep-pos)})))

(defn- sidestep-to-land-action
  "Action: Continue sidestepping to find empty land."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dest (get-in ctx [:entity :fsm-data :destination])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        sidestep-pos (find-sidestep-move pos dest recent-moves)]
    (when sidestep-pos
      {:move-to sidestep-pos
       :recent-moves (utils/update-recent-moves recent-moves sidestep-pos)})))

;; --- FSM Definition ---

(def hurry-up-and-wait-fsm
  "FSM transitions for hurry-up-and-wait mission.
   Format: [current-state guard-fn new-state action-fn]

   States:
   - :moving                    - Pathfinding toward destination
   - :sidestepping-destination  - Destination blocked, finding nearby land
   - [:terminal :arrived]       - Mission complete, enter sentry mode
   - [:terminal :stuck]         - No valid moves available"
  [;; Moving transitions (order matters - first matching wins)
   [:moving  stuck?                [:terminal :stuck]          terminal-action]
   [:moving  at-destination?       [:terminal :arrived]        arrive-action]
   [:moving  destination-blocked?  :sidestepping-destination   begin-sidestep-action]
   [:moving  can-move-toward?      :moving                     move-toward-action]
   [:moving  needs-sidestep?       :moving                     sidestep-action]

   ;; Sidestepping transitions
   [:sidestepping-destination  stuck?          [:terminal :stuck]             terminal-action]
   [:sidestepping-destination  on-empty-land?  [:terminal :arrived]           arrive-action]
   [:sidestepping-destination  always          :sidestepping-destination      sidestep-to-land-action]])

;; --- Create Mission ---

(defn create-hurry-up-and-wait-data
  "Create FSM data for a hurry-up-and-wait mission at given position.
   Optional destination parameter sets where the army should move to.
   Optional unit-id parameter identifies the unit for mission tracking.
   If already at destination or no destination, starts in terminal arrived state."
  ([pos]
   (create-hurry-up-and-wait-data pos nil nil))
  ([pos destination]
   (create-hurry-up-and-wait-data pos destination nil))
  ([pos destination unit-id]
   (let [has-destination? (and destination (not= pos destination))]
     {:fsm hurry-up-and-wait-fsm
      :fsm-state (if has-destination? :moving [:terminal :arrived])
      :fsm-data {:position pos
                 :destination (when has-destination? destination)
                 :recent-moves [pos]
                 :unit-id unit-id}})))
