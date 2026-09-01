(ns empire.computer.fighter
  "Computer fighter module - VMS Empire style fighter movement.
   Leg-based coverage, navigation, state machine, process-fighter entry point."
  (:require [empire.state.api :as sa]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.movement :as computer-movement]
            [empire.config.core :as config]
            [empire.computer.fighter.decisions :as decisions]
            [empire.computer.fighter.process-decisions :as process-decisions]
            [empire.computer.fighter.flight-plan :as flight-plan]
            [empire.computer.fighter.movement :as fm]
            [empire.computer.fighter.exploration :as fe]
            [empire.computer.threat-response-port :as threat-response-port]
            [empire.game-mechanics.visibility :as visibility]))

(defn- computer-unit-at
  [pos]
  (:contents (get-in (sa/read-state :computer-map) pos)))

(defn- computer-cell-at
  [pos]
  (get-in (sa/read-state :computer-map) pos))

;; --- Leg-based coverage ---

(defn- ensure-flight-target
  [pos]
  (flight-plan/ensure-flight-target! #(sa/read-state :computer-map) sa/update-world! sa/read-state pos)
  (visibility/sync-ai-unit-to-computer-map! pos))

(defn- at-flight-target?
  [pos target]
  (flight-plan/at-flight-target? #(sa/read-state :computer-map) pos target))

(defn- assign-exploration-flight
  [pos site-pos]
  (flight-plan/assign-exploration-flight! sa/update-world! (sa/read-state :computer-map) pos site-pos)
  (visibility/sync-ai-unit-to-computer-map! pos))

(defn- handle-arrival
  [pos unit]
  (let [result (flight-plan/handle-arrival! #(sa/read-state :computer-map) sa/update-world! sa/read-state sa/write-state! pos unit)]
    (visibility/sync-ai-unit-to-computer-map! pos)
    result))

(defn- select-best-navigation-target
  "Score passable unoccupied neighbors by unexplored count, break ties by proximity."
  [passable target]
  (let [candidates (filter #(not (fm/occupied? %)) passable)
        scored (map (fn [n] [n (fe/count-unexplored-neighbors n)]) candidates)
        best-score (when (seq scored) (apply max (map second scored)))]
    (when (and best-score (pos? best-score))
      (let [at-best (filter #(= best-score (second %)) scored)]
        (first (first (sort-by (fn [[n _]] (fm/distance-to n target)) at-best)))))))

(defn- explore-navigation-step
  [pos explore-pos]
  (when (action-resolution/move-unit-to pos explore-pos)
    (computer-movement/update-cell-visibility! pos :computer)
    (computer-movement/update-cell-visibility! explore-pos :computer)
    (when (fm/consume-fighter-fuel explore-pos)
      {:pos explore-pos :hops 1})))

(defn- refuel-at-site
  "Refuel fighter in place, recording origin site for leg tracking."
  [pos site-pos]
  (sa/update-world! assoc-in (conj pos :contents :fuel) config/fighter-fuel)
  (sa/update-world! update-in (conj pos :contents)
                    assoc :flight-origin-site site-pos)
  pos)

(defn- move-and-consume-toward
  "Move one step toward target and consume fuel. Returns {:pos p :hops n} or nil."
  [pos target]
  (when-let [hop (fm/hop-over-friendly pos target)]
    (when-let [{:keys [pos hops]} (fm/execute-hop pos hop)]
      (when (fm/consume-fighter-fuel pos)
        {:pos pos :hops hops}))))

(defn- navigate-toward-target
  "Move one step toward target, preferring unexplored cells when fuel allows.
   Returns {:pos p :hops n} or nil."
  [pos target fuel]
  (let [passable (fm/get-passable-neighbors pos)
        direct-dist (fm/distance-to pos target)
        fuel-margin? (> fuel (+ direct-dist 2))
        explore-pos (when fuel-margin?
                      (select-best-navigation-target passable target))]
    (if explore-pos
      (explore-navigation-step pos explore-pos)
      (move-and-consume-toward pos target))))

(defn- adjacent-to-city-site? [site pos]
  (and site
       (= :city (:type (computer-cell-at site)))
       (<= (fm/distance-to pos site) 1)))

(defn- adjacent-to-site? [site pos]
  (and site (<= (fm/distance-to pos site) 1)))

(defn- refueling-site?
  [site]
  (let [cell (computer-cell-at site)]
    (or (and (= :city (:type cell))
             (= :computer (:city-status cell)))
        (and (= :carrier (get-in cell [:contents :type]))
             (= :computer (get-in cell [:contents :owner]))
             (= :holding (get-in cell [:contents :carrier-mode]))))))

(defn- candidate-refueling-sites
  [pos unit]
  (->> [(:explore-landing-site unit)
        (when (and (= :regular (:flight-mode unit))
                   (refueling-site? (:flight-target-site unit)))
          (:flight-target-site unit))
        (:flight-origin-site unit)
        (fm/find-nearest-refueling-site pos)]
       (remove nil?)
       distinct))

(defn- nearest-recovery-site
  [pos unit]
  (when-let [sites (seq (candidate-refueling-sites pos unit))]
    (apply min-key (partial fm/distance-to pos) sites)))

(defn- should-break-off-to-refuel?
  [pos fuel unit]
  (if-let [site (nearest-recovery-site pos unit)]
    (<= fuel (+ (fm/distance-to pos site) 2))
    false))

(defn- desperate-patrol [pos]
  (when-let [{:keys [pos hops]} (fm/do-patrol pos)]
    (when (fm/consume-fighter-fuel pos)
      {:pos pos :hops hops})))

(defn- handle-low-fuel
  "Handle low-fuel: return to nearest refueling site or patrol desperately.
   Returns :landed, {:pos p :hops n}, or nil."
  [pos unit]
  (let [site (nearest-recovery-site pos unit)]
    (cond
      (adjacent-to-city-site? site pos) (fm/land-at-city pos site)
      (adjacent-to-site? site pos) {:pos (refuel-at-site pos site) :hops 1}
      site (move-and-consume-toward pos site)
      :else (desperate-patrol pos))))

(defn- handle-patrol
  "Execute one patrol step, consuming fuel."
  [pos]
  (when-let [{:keys [pos hops]} (fm/do-patrol pos)]
    (when (fm/consume-fighter-fuel pos)
      {:pos pos :hops hops})))

(defn- exploring?
  "True if unit is on an outbound exploration sortie with steps remaining."
  [unit]
  (and (= :explore (:flight-mode unit))
       (pos? (:explore-steps-remaining unit 0))))

(defn- handle-exploration
  "Process an outbound exploration sortie."
  [pos unit]
  (fe/explore-step pos unit))

(defn- arrived-at-flight-target?
  [pos target]
  (and target (at-flight-target? pos target)))

(defn- move-fighter-toward-objective
  "Non-combat movement priorities: explore > arrival > low fuel > navigate > patrol. CC=5."
  [pos unit]
  (let [fuel (:fuel unit config/fighter-fuel)
        target (:flight-target-site unit)
        action (decisions/objective-action
                {:exploring? (exploring? unit)
                 :at-flight-target? (arrived-at-flight-target? pos target)
                 :low-fuel? (should-break-off-to-refuel? pos fuel unit)
                 :has-target? (boolean target)})]
    (case action
      :explore (handle-exploration pos unit)
      :arrive (handle-arrival pos unit)
      :low-fuel (handle-low-fuel pos unit)
      :navigate (navigate-toward-target pos target fuel)
      (handle-patrol pos))))

(defn- move-fighter-once
  "Execute one step of fighter priority logic. CC=2.
   Returns {:pos p :hops n}, :landed, or nil (died/stuck)."
  [pos unit]
  (let [enemy-pos (fm/find-adjacent-enemy pos)]
    (case (decisions/fighter-step-action enemy-pos :objective)
      :attack (when-let [new-pos (fm/attack-enemy pos enemy-pos)]
                (when (fm/consume-fighter-fuel new-pos)
                  {:pos new-pos :hops 1}))
      (move-fighter-toward-objective pos unit))))

(defn- fighter-at?
  "Returns true if a fighter exists at pos on the game map."
  [pos]
  (= :fighter (get-in (sa/read-state :computer-map) (conj pos :contents :type))))

(defn- burn-stuck-fuel
  "Burns fuel for a stuck fighter at pos. Returns pos if survived, nil if died."
  [pos]
  (when (and (fighter-at? pos) (fm/consume-fighter-fuel pos))
    pos))

(defn- step-fighter
  "Execute one step. Returns {:pos p :steps-used n} or nil (landed/died)."
  [current-pos]
  (when (fighter-at? current-pos)
    (let [unit (computer-unit-at current-pos)
          result (move-fighter-once current-pos unit)
          burned-pos (when (and (not= result :landed) (not (map? result)))
                       (burn-stuck-fuel current-pos))]
      (process-decisions/step-result result burned-pos))))

(defn- computer-fighter?
  [unit]
  (and unit
       (= :computer (:owner unit))
       (= :fighter (:type unit))))

(defn- process-threat-fighter?
  [pos unit]
  (threat-response-port/process-fighter-threat pos unit))

(defn- run-fighter-steps!
  [pos]
  (ensure-flight-target pos)
  (loop [current-pos pos
         steps-remaining fm/fighter-speed]
    (let [step (when (pos? steps-remaining)
                 (step-fighter current-pos))]
      (when (process-decisions/continue-steps? steps-remaining step)
        (recur (:pos step) (- steps-remaining (:steps-used step)))))))

(defn process-fighter
  "Processes a computer fighter using VMS Empire style logic.
   Moves up to fighter-speed (8) cells per round, consuming fuel each step.
   Priority each step: Attack > Arrive at target > Return if low fuel > Navigate/Patrol
   Returns nil."
  [pos unit]
  (when (computer-fighter? unit)
    (when-not (process-threat-fighter? pos unit)
      (run-fighter-steps! pos)))
  nil)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:52:16.132364-05:00", :module-hash "-2099860804", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "295736422"} {:id "defn-/computer-unit-at", :kind "defn-", :line 16, :end-line nil, :hash "-1795563674"} {:id "defn-/computer-cell-at", :kind "defn-", :line 20, :end-line nil, :hash "1073340041"} {:id "defn-/ensure-flight-target", :kind "defn-", :line 26, :end-line nil, :hash "-216376912"} {:id "defn-/at-flight-target?", :kind "defn-", :line 31, :end-line nil, :hash "-595857678"} {:id "defn-/assign-exploration-flight", :kind "defn-", :line 35, :end-line nil, :hash "-59449493"} {:id "defn-/handle-arrival", :kind "defn-", :line 40, :end-line nil, :hash "-832720345"} {:id "defn-/select-best-navigation-target", :kind "defn-", :line 46, :end-line nil, :hash "-554551890"} {:id "defn-/explore-navigation-step", :kind "defn-", :line 56, :end-line nil, :hash "1903417521"} {:id "defn-/refuel-at-site", :kind "defn-", :line 64, :end-line nil, :hash "1041119285"} {:id "defn-/move-and-consume-toward", :kind "defn-", :line 72, :end-line nil, :hash "-1274531809"} {:id "defn-/navigate-toward-target", :kind "defn-", :line 80, :end-line nil, :hash "-1154489868"} {:id "defn-/adjacent-to-city-site?", :kind "defn-", :line 93, :end-line nil, :hash "1899879240"} {:id "defn-/adjacent-to-site?", :kind "defn-", :line 98, :end-line nil, :hash "-82053016"} {:id "defn-/refueling-site?", :kind "defn-", :line 101, :end-line nil, :hash "1911933383"} {:id "defn-/candidate-refueling-sites", :kind "defn-", :line 110, :end-line nil, :hash "-1554620140"} {:id "defn-/nearest-recovery-site", :kind "defn-", :line 121, :end-line nil, :hash "-1501551094"} {:id "defn-/should-break-off-to-refuel?", :kind "defn-", :line 126, :end-line nil, :hash "-1340915880"} {:id "defn-/desperate-patrol", :kind "defn-", :line 132, :end-line nil, :hash "-471602302"} {:id "defn-/handle-low-fuel", :kind "defn-", :line 137, :end-line nil, :hash "-30998865"} {:id "defn-/handle-patrol", :kind "defn-", :line 148, :end-line nil, :hash "632345729"} {:id "defn-/exploring?", :kind "defn-", :line 155, :end-line nil, :hash "-2145013931"} {:id "defn-/handle-exploration", :kind "defn-", :line 161, :end-line nil, :hash "-1202436871"} {:id "defn-/arrived-at-flight-target?", :kind "defn-", :line 166, :end-line nil, :hash "-2017855715"} {:id "defn-/move-fighter-toward-objective", :kind "defn-", :line 170, :end-line nil, :hash "2003600848"} {:id "defn-/move-fighter-once", :kind "defn-", :line 187, :end-line nil, :hash "403808442"} {:id "defn-/fighter-at?", :kind "defn-", :line 198, :end-line nil, :hash "-1137626720"} {:id "defn-/burn-stuck-fuel", :kind "defn-", :line 203, :end-line nil, :hash "-826225124"} {:id "defn-/step-fighter", :kind "defn-", :line 209, :end-line nil, :hash "-213901629"} {:id "defn-/computer-fighter?", :kind "defn-", :line 219, :end-line nil, :hash "-388940705"} {:id "defn-/process-threat-fighter?", :kind "defn-", :line 225, :end-line nil, :hash "-1126765009"} {:id "defn-/run-fighter-steps!", :kind "defn-", :line 229, :end-line nil, :hash "-496949623"} {:id "defn/process-fighter", :kind "defn", :line 239, :end-line nil, :hash "-1975436729"}]}
;; clj-mutate-manifest-end
