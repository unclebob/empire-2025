(ns empire.fsm.move-with-squad
  "Move-with-Squad FSM - army moves as part of a squad toward a target.
   Order-driven FSM that cycles between waiting for orders and executing moves.
   Squad coordinates movement; army follows orders while staying cohesive."
  (:require [empire.atoms :as atoms]
            [empire.movement.pathfinding :as pathfinding]
            [empire.fsm.context :as context]
            [empire.fsm.explorer-utils :as utils]))

;; --- Helper Functions ---

(defn- find-sidestep-move
  "Find a move that makes progress toward target while avoiding direct path.
   Prefers non-backtrack moves. Returns [row col] or nil."
  [pos target recent-moves]
  (let [valid-moves (utils/get-valid-moves pos @atoms/game-map)
        non-backtrack (remove (set recent-moves) valid-moves)
        moves-to-try (if (seq non-backtrack) non-backtrack valid-moves)]
    (when (seq moves-to-try)
      (let [scored (map (fn [m]
                          (let [[mr mc] m
                                [tr tc] target
                                dist (+ (Math/abs (- mr tr)) (Math/abs (- mc tc)))]
                            [m dist]))
                        moves-to-try)
            min-dist (apply min (map second scored))
            best-moves (filter #(= min-dist (second %)) scored)]
        (first (rand-nth best-moves))))))

;; --- Guards ---

(defn squad-disbanded?
  "Guard: Squad has been disbanded."
  [ctx]
  (context/has-event? ctx :squad-disbanded))

(defn squad-attacking?
  "Guard: Squad is transitioning to attack mode."
  [ctx]
  (context/has-event? ctx :squad-attacking))

(defn has-move-order?
  "Guard: Move order event in queue."
  [ctx]
  (context/has-event? ctx :move-order))

(defn at-ordered-position?
  "Guard: Current position equals ordered-position."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        ordered (get-in ctx [:entity :fsm-data :ordered-position])]
    (= pos ordered)))

(defn- stuck?
  "Guard: No valid moves available."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        valid-moves (utils/get-valid-moves pos @atoms/game-map)]
    (empty? valid-moves)))

(defn- can-move-toward?
  "Guard: Pathfinding can find next step toward ordered position."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        target (get-in ctx [:entity :fsm-data :ordered-position])
        next-pos (when target (pathfinding/next-step-toward pos target))]
    (boolean next-pos)))

(defn- needs-sidestep?
  "Guard: Direct path blocked but sidestep exists."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        target (get-in ctx [:entity :fsm-data :ordered-position])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])]
    (boolean (and target (find-sidestep-move pos target recent-moves)))))

(defn- move-blocked?
  "Guard: Cannot move toward target (for reporting blocked status)."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        target (get-in ctx [:entity :fsm-data :ordered-position])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        next-step (when target (pathfinding/next-step-toward pos target))
        sidestep (when target (find-sidestep-move pos target recent-moves))]
    (and target (nil? next-step) (nil? sidestep))))

(defn- always [_ctx] true)

;; --- Actions ---

(defn- terminal-action
  "Action: Called when stuck with no valid moves."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [(utils/make-mission-ended-event unit-id :stuck)]}))

(defn terminal-disbanded-action
  "Action: Called when squad is disbanded."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [(utils/make-mission-ended-event unit-id :disbanded)]}))

(defn terminal-attack-action
  "Action: Called when squad transitions to attack."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [(utils/make-mission-ended-event unit-id :attack-mode)]}))

(defn accept-order-action
  "Action: Pop move order from queue, set as current target."
  [ctx]
  (let [event-queue (:event-queue (:entity ctx))
        move-order (first (filter #(= :move-order (:type %)) event-queue))
        target (get-in move-order [:data :target])]
    {:ordered-position target}))

(defn report-position-action
  "Action: Notify squad of arrival at ordered position."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:ordered-position nil
     :events [{:type :unit-position-report
               :priority :normal
               :data {:unit-id unit-id :coords pos}}]}))

(defn report-blocked-action
  "Action: Notify squad that movement is blocked."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:ordered-position nil
     :events [{:type :unit-blocked
               :priority :high
               :data {:unit-id unit-id :coords pos}}]}))

(defn- move-toward-action
  "Action: Move toward ordered position using pathfinding."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        target (get-in ctx [:entity :fsm-data :ordered-position])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        next-pos (pathfinding/next-step-toward pos target)]
    (when next-pos
      {:move-to next-pos
       :recent-moves (utils/update-recent-moves recent-moves next-pos)})))

(defn- sidestep-action
  "Action: Sidestep around obstacle while moving toward target."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        target (get-in ctx [:entity :fsm-data :ordered-position])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        sidestep-pos (find-sidestep-move pos target recent-moves)]
    (when sidestep-pos
      {:move-to sidestep-pos
       :recent-moves (utils/update-recent-moves recent-moves sidestep-pos)})))

;; --- FSM Definition ---

(def move-with-squad-fsm
  "FSM transitions for move-with-squad mission.
   Format: [current-state guard-fn new-state action-fn]

   States:
   - :awaiting-orders           - Waiting for movement order from squad
   - :executing-move            - Moving to ordered position
   - [:terminal :disbanded]     - Squad disbanded
   - [:terminal :attack-mode]   - Squad attacking
   - [:terminal :stuck]         - No valid moves"
  [;; Awaiting-orders transitions
   [:awaiting-orders  squad-disbanded?  [:terminal :disbanded]    terminal-disbanded-action]
   [:awaiting-orders  squad-attacking?  [:terminal :attack-mode]  terminal-attack-action]
   [:awaiting-orders  has-move-order?   :executing-move           accept-order-action]
   [:awaiting-orders  always            :awaiting-orders          (constantly nil)]

   ;; Executing-move transitions
   [:executing-move  stuck?               [:terminal :stuck]   terminal-action]
   [:executing-move  at-ordered-position? :awaiting-orders     report-position-action]
   [:executing-move  can-move-toward?     :executing-move      move-toward-action]
   [:executing-move  needs-sidestep?      :executing-move      sidestep-action]
   [:executing-move  move-blocked?        :awaiting-orders     report-blocked-action]])

;; --- Create Mission ---

(defn create-move-with-squad-data
  "Create FSM data for a move-with-squad mission.
   pos - current army position
   squad-id - parent squad identifier
   unit-id - optional unit identifier for tracking"
  ([pos squad-id]
   (create-move-with-squad-data pos squad-id nil))
  ([pos squad-id unit-id]
   {:fsm move-with-squad-fsm
    :fsm-state :awaiting-orders
    :fsm-data {:mission-type :move-with-squad
               :position pos
               :ordered-position nil
               :squad-id squad-id
               :unit-id unit-id
               :recent-moves [pos]}}))
