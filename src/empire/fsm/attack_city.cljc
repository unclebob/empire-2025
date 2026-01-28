(ns empire.fsm.attack-city
  "Attack-City FSM - army mission to capture a target city.
   Used during squad :attacking state. Army moves adjacent to city
   and attempts conquest. Reports success or failure to squad."
  (:require [empire.atoms :as atoms]
            [empire.movement.pathfinding :as pathfinding]
            [empire.fsm.explorer-utils :as utils]))


;; --- Guards ---

(defn- stuck?
  "Guard: Returns true if there are no valid moves available."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (utils/stuck? pos @atoms/game-map)))

(defn adjacent-to-target?
  "Guard: Returns true if current position is adjacent to target city."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        target (get-in ctx [:entity :fsm-data :target-city])]
    (utils/adjacent? pos target)))

(defn city-captured?
  "Guard: Returns true if target city belongs to computer."
  [ctx]
  (let [target (get-in ctx [:entity :fsm-data :target-city])
        cell (get-in (:game-map ctx) target)]
    (= :computer (:city-status cell))))

(def ^{:doc "Alias for city-captured? - city already belongs to us."}
  city-already-ours? city-captured?)

(defn- can-move-toward?
  "Guard: Returns true if pathfinding can find next step toward target."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        target (get-in ctx [:entity :fsm-data :target-city])
        next-pos (when target (pathfinding/next-step-toward pos target))]
    (boolean next-pos)))

(defn- needs-sidestep?
  "Guard: Returns true if direct path is blocked but sidestep move exists."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        target (get-in ctx [:entity :fsm-data :target-city])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])]
    (boolean (utils/find-sidestep-move pos target recent-moves @atoms/game-map))))

;; --- Actions ---

(defn- terminal-action
  "Action: Called when stuck with no valid moves.
   Emits a :mission-ended event to notify the Lieutenant."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [(utils/make-mission-ended-event unit-id :stuck)]}))

(defn report-conquest-action
  "Action: Report successful capture to squad and Lieutenant."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])
        target (get-in ctx [:entity :fsm-data :target-city])]
    {:events [{:type :city-conquered
               :priority :high
               :data {:coords target :unit-id unit-id}}
              (utils/make-mission-ended-event unit-id :conquered)]}))

(defn- report-already-captured-action
  "Action: City already ours - report mission complete."
  [ctx]
  (let [unit-id (get-in ctx [:entity :fsm-data :unit-id])]
    {:events [(utils/make-mission-ended-event unit-id :conquered)]}))

(defn prepare-attack-action
  "Action: Prepare for attack (just state transition, no action needed)."
  [_ctx]
  nil)

(defn attempt-capture-action
  "Action: Trigger movement into city cell to initiate combat/conquest."
  [ctx]
  (let [target (get-in ctx [:entity :fsm-data :target-city])]
    {:move-to target}))

(defn- move-toward-action
  "Action: Move toward target city using pathfinding."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        target (get-in ctx [:entity :fsm-data :target-city])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        next-pos (pathfinding/next-step-toward pos target)]
    (when next-pos
      {:move-to next-pos
       :recent-moves (utils/update-recent-moves recent-moves next-pos)})))

(defn- sidestep-action
  "Action: Sidestep around obstacle while moving toward target."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        target (get-in ctx [:entity :fsm-data :target-city])
        recent-moves (or (get-in ctx [:entity :fsm-data :recent-moves]) [])
        sidestep-pos (utils/find-sidestep-move pos target recent-moves @atoms/game-map)]
    (when sidestep-pos
      {:move-to sidestep-pos
       :recent-moves (utils/update-recent-moves recent-moves sidestep-pos)})))

;; --- FSM Definition ---

(def attack-city-fsm
  "FSM transitions for attack-city mission.
   Format: [current-state guard-fn new-state action-fn]

   States:
   - :approaching             - Moving to get adjacent to target city
   - :attacking               - Adjacent to city, attempting capture
   - [:terminal :conquered]   - City captured successfully
   - [:terminal :stuck]       - No valid moves available"
  [;; Approaching transitions
   [:approaching  stuck?              [:terminal :stuck]      terminal-action]
   [:approaching  city-already-ours?  [:terminal :conquered]  report-already-captured-action]
   [:approaching  adjacent-to-target? :attacking              prepare-attack-action]
   [:approaching  can-move-toward?    :approaching            move-toward-action]
   [:approaching  needs-sidestep?     :approaching            sidestep-action]

   ;; Attacking transitions
   [:attacking  city-captured?       [:terminal :conquered]  report-conquest-action]
   [:attacking  city-already-ours?   [:terminal :conquered]  report-already-captured-action]
   [:attacking  utils/always         :attacking              attempt-capture-action]])

;; --- Create Mission ---

(defn create-attack-city-data
  "Create FSM data for an attack-city mission.
   pos - current army position
   target-city - [row col] of city to capture
   squad-id - parent squad identifier
   unit-id - optional unit identifier for tracking"
  ([pos target-city squad-id]
   (create-attack-city-data pos target-city squad-id nil))
  ([pos target-city squad-id unit-id]
   {:fsm attack-city-fsm
    :fsm-state :approaching
    :fsm-data {:mission-type :attack-city
               :position pos
               :target-city target-city
               :squad-id squad-id
               :unit-id unit-id
               :recent-moves [pos]}}))
