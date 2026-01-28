(ns empire.fsm.fighter-patrol
  "Fighter Patrol FSM - aerial reconnaissance for early warning.
   Launched from city, flies outbound in random direction, turns back at half fuel,
   returns to base to refuel, then repeats. Reports enemy sightings to Lieutenant."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.fsm.explorer-utils :as utils]))

;; --- Helper Functions ---

(defn- add-coords
  "Add two coordinate pairs."
  [[r1 c1] [r2 c2]]
  [(+ r1 r2) (+ c1 c2)])

(defn- step-toward
  "Return next position one step toward target."
  [from to]
  (let [[fr fc] from
        [tr tc] to
        dr (cond (< fr tr) 1 (> fr tr) -1 :else 0)
        dc (cond (< fc tc) 1 (> fc tc) -1 :else 0)]
    [(+ fr dr) (+ fc dc)]))

(defn- neighbors
  "Get all 8 neighboring positions."
  [[r c]]
  (for [dr [-1 0 1]
        dc [-1 0 1]
        :when (not (and (zero? dr) (zero? dc)))]
    [(+ r dr) (+ c dc)]))

(defn- enemy-at?
  "Returns true if there's a player unit at the given position."
  [ctx pos]
  (let [cell (get-in (:game-map ctx) pos)
        contents (:contents cell)]
    (and contents (= :player (:owner contents)))))

(defn- find-adjacent-enemies
  "Find all enemy positions adjacent to pos."
  [ctx pos]
  (filter (partial enemy-at? ctx) (neighbors pos)))

(defn- find-sidestep-away-from-enemies
  "Find a move that avoids enemies."
  [ctx pos enemies]
  (let [game-map (:game-map ctx)
        valid-neighbors (filter (fn [p]
                                  (let [cell (get-in game-map p)]
                                    (and cell
                                         (not (nil? (:type cell)))
                                         (not (enemy-at? ctx p)))))
                                (neighbors pos))]
    (when (seq valid-neighbors)
      ;; Pick the one furthest from any enemy
      (let [scored (map (fn [p]
                          (let [min-dist (apply min (map (fn [e]
                                                           (let [[pr pc] p
                                                                 [er ec] e]
                                                             (+ (Math/abs (- pr er))
                                                                (Math/abs (- pc ec)))))
                                                         enemies))]
                            [p min-dist]))
                        valid-neighbors)
            max-dist (apply max (map second scored))
            best (filter #(= max-dist (second %)) scored)]
        (first (rand-nth best))))))

;; --- Guards ---

(defn clear-of-city?
  "Guard: Fighter has moved off the city cell."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        base (get-in ctx [:entity :fsm-data :base-city])]
    (not= pos base)))

(defn fuel-at-half?
  "Guard: Fuel is at or below 50% - time to turn back."
  [ctx]
  (let [fuel (get-in ctx [:entity :fsm-data :fuel-remaining])
        max-fuel (get-in ctx [:entity :fsm-data :max-fuel])]
    (<= fuel (/ max-fuel 2))))

(defn enemy-adjacent?
  "Guard: Enemy unit in adjacent cell."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])]
    (boolean (seq (find-adjacent-enemies ctx pos)))))

(defn at-base?
  "Guard: Fighter is at base city."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        base (get-in ctx [:entity :fsm-data :base-city])]
    (= pos base)))

(defn at-map-edge?
  "Guard: Fighter has reached edge of map in patrol direction."
  [ctx]
  (let [[r c] (get-in ctx [:entity :fsm-data :position])
        [dr dc] (get-in ctx [:entity :fsm-data :patrol-direction])
        game-map (:game-map ctx)
        height (count game-map)
        width (count (first game-map))]
    (or (and (neg? dr) (zero? r))
        (and (pos? dr) (= r (dec height)))
        (and (neg? dc) (zero? c))
        (and (pos? dc) (= c (dec width))))))

(defn refueled?
  "Guard: Fuel is back to max."
  [ctx]
  (let [fuel (get-in ctx [:entity :fsm-data :fuel-remaining])
        max-fuel (get-in ctx [:entity :fsm-data :max-fuel])]
    (= fuel max-fuel)))

;; --- Actions ---

(defn pick-direction-action
  "Action: Choose random outbound direction."
  [_ctx]
  (let [directions [[0 1] [0 -1] [1 0] [-1 0]
                    [1 1] [1 -1] [-1 1] [-1 -1]]
        dir (rand-nth directions)]
    {:patrol-direction dir}))

(defn- takeoff-action
  "Action: Move off city cell in patrol direction."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dir (get-in ctx [:entity :fsm-data :patrol-direction])
        new-pos (add-coords pos dir)
        fuel (get-in ctx [:entity :fsm-data :fuel-remaining])]
    {:move-to new-pos
     :fuel-remaining (dec fuel)}))

(defn fly-outbound-action
  "Action: Continue flying in patrol direction."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        dir (get-in ctx [:entity :fsm-data :patrol-direction])
        new-pos (add-coords pos dir)
        fuel (get-in ctx [:entity :fsm-data :fuel-remaining])]
    {:move-to new-pos
     :fuel-remaining (dec fuel)}))

(defn- turn-back-action
  "Action: Reverse direction to head home (state change handles this)."
  [_ctx]
  nil)

(defn fly-toward-base-action
  "Action: Fly toward base city."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        base (get-in ctx [:entity :fsm-data :base-city])
        next-pos (step-toward pos base)
        fuel (get-in ctx [:entity :fsm-data :fuel-remaining])]
    {:move-to next-pos
     :fuel-remaining (dec fuel)}))

(defn- report-and-sidestep-action
  "Action: Report enemy, sidestep to avoid."
  [ctx]
  (let [pos (get-in ctx [:entity :fsm-data :position])
        lt-id (get-in ctx [:entity :fsm-data :lieutenant-id])
        enemies (find-adjacent-enemies ctx pos)
        reported (get-in ctx [:entity :fsm-data :enemies-reported])
        new-enemies (remove reported enemies)
        sidestep-pos (find-sidestep-away-from-enemies ctx pos enemies)
        fuel (get-in ctx [:entity :fsm-data :fuel-remaining])]
    {:move-to (or sidestep-pos pos)
     :fuel-remaining (dec fuel)
     :enemies-reported (into reported new-enemies)
     :events (mapv (fn [e]
                     {:type :enemy-spotted
                      :priority :high
                      :to lt-id
                      :data {:enemy-coords e
                             :spotted-by :fighter}})
                   new-enemies)}))

(defn land-action
  "Action: Land at base city."
  [_ctx]
  {:enemies-reported #{}})

(defn refuel-action
  "Action: Refuel at base."
  [ctx]
  (let [fuel (get-in ctx [:entity :fsm-data :fuel-remaining])
        max-fuel (get-in ctx [:entity :fsm-data :max-fuel])]
    {:fuel-remaining (min max-fuel (+ fuel 2))}))

;; --- FSM Definition ---

(def fighter-patrol-fsm
  "FSM transitions for fighter patrol.
   Format: [current-state guard-fn new-state action-fn]

   States:
   - :launching   - Taking off from city
   - :flying-out  - Flying away from base in chosen direction
   - :returning   - Flying back toward base
   - :landing     - At base, refueling"
  [;; Launching transitions
   [:launching  clear-of-city?  :flying-out  pick-direction-action]
   [:launching  utils/always          :launching   takeoff-action]

   ;; Flying out transitions
   [:flying-out  fuel-at-half?    :returning   turn-back-action]
   [:flying-out  enemy-adjacent?  :flying-out  report-and-sidestep-action]
   [:flying-out  at-map-edge?     :returning   turn-back-action]
   [:flying-out  utils/always           :flying-out  fly-outbound-action]

   ;; Returning transitions
   [:returning  at-base?         :landing    land-action]
   [:returning  enemy-adjacent?  :returning  report-and-sidestep-action]
   [:returning  utils/always           :returning  fly-toward-base-action]

   ;; Landing transitions
   [:landing  refueled?  :launching  (constantly nil)]
   [:landing  utils/always     :landing    refuel-action]])

;; --- Create Patrol ---

(defn create-fighter-patrol
  "Create a fighter for patrol duty.
   fighter-id - unique identifier
   base-city - [r c] origin city for return
   lieutenant-id - commanding officer for reports
   max-fuel - full tank (turns of flight)"
  [fighter-id base-city lieutenant-id max-fuel]
  {:fsm fighter-patrol-fsm
   :fsm-state :launching
   :fsm-data {:fighter-id fighter-id
              :position base-city
              :base-city base-city
              :lieutenant-id lieutenant-id
              :fuel-remaining max-fuel
              :max-fuel max-fuel
              :patrol-direction nil
              :enemies-reported #{}}
   :event-queue []})
