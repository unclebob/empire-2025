(ns empire.computer.fighter
  "Computer fighter module - VMS Empire style fighter movement.
   Leg-based coverage, navigation, state machine, process-fighter entry point."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.movement :as computer-movement]
            [empire.config.core :as config]
            [empire.computer.fighter-decisions :as decisions]
            [empire.computer.fighter-process-decisions :as process-decisions]
            [empire.computer.fighter-flight-plan :as flight-plan]
            [empire.computer.fighter-movement :as fm]
            [empire.computer.fighter-exploration :as fe]
            [empire.computer.threat-response :as threat-response]
            [empire.game-mechanics.movement.visibility :as visibility]))

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
      (when (core/move-unit-to pos explore-pos)
        (computer-movement/update-cell-visibility! pos :computer)
        (computer-movement/update-cell-visibility! explore-pos :computer)
        (when (fm/consume-fighter-fuel explore-pos)
          {:pos explore-pos :hops 1}))
      (when-let [hop (fm/hop-over-friendly pos target)]
        (when-let [{:keys [pos hops]} (fm/execute-hop pos hop)]
          (when (fm/consume-fighter-fuel pos)
            {:pos pos :hops hops}))))))

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

(defn- adjacent-to-city-site? [site pos]
  (and site
       (= :city (:type (computer-cell-at site)))
       (<= (fm/distance-to pos site) 1)))

(defn- adjacent-to-site? [site pos]
  (and site (<= (fm/distance-to pos site) 1)))

(defn- desperate-patrol [pos]
  (when-let [{:keys [pos hops]} (fm/do-patrol pos)]
    (when (fm/consume-fighter-fuel pos)
      {:pos pos :hops hops})))

(defn- handle-low-fuel
  "Handle low-fuel: return to nearest refueling site or patrol desperately.
   Returns :landed, {:pos p :hops n}, or nil."
  [pos]
  (let [site (fm/find-nearest-refueling-site pos)]
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

(defn- handle-exploration-or-drone
  "Dispatch to drone-step or explore-step based on flight-mode."
  [pos unit]
  (if (= :drone (:flight-mode unit))
    (fe/drone-step pos unit)
    (fe/explore-step pos unit)))

(defn- move-fighter-toward-objective
  "Non-combat movement priorities: explore/drone > arrival > low fuel > navigate > patrol. CC=5."
  [pos unit]
  (let [fuel (:fuel unit config/fighter-fuel)
        target (:flight-target-site unit)
        action (decisions/objective-action
                {:exploring? (exploring? unit)
                 :drone? (= :drone (:flight-mode unit))
                 :at-flight-target? (and target (at-flight-target? pos target))
                 :low-fuel? (fm/should-return-to-refuel? pos fuel)
                 :has-target? (boolean target)})]
    (case action
      :explore (handle-exploration-or-drone pos unit)
      :arrive (handle-arrival pos unit)
      :low-fuel (handle-low-fuel pos)
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
  (threat-response/process-fighter-threat pos unit))

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
;; {:version 1, :tested-at "2026-03-16T09:12:56.213164-05:00", :module-hash "-938659709", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 13, :hash "1623406765"} {:id "defn-/ensure-flight-target", :kind "defn-", :line 17, :end-line 19, :hash "1637502047"} {:id "defn-/at-flight-target?", :kind "defn-", :line 21, :end-line 23, :hash "1502384376"} {:id "defn-/assign-exploration-flight", :kind "defn-", :line 25, :end-line 27, :hash "1061413541"} {:id "defn-/handle-arrival", :kind "defn-", :line 29, :end-line 31, :hash "905536671"} {:id "defn-/select-best-navigation-target", :kind "defn-", :line 33, :end-line 41, :hash "-722523704"} {:id "defn-/navigate-toward-target", :kind "defn-", :line 43, :end-line 61, :hash "639683001"} {:id "defn-/refuel-at-site", :kind "defn-", :line 63, :end-line 69, :hash "1041119285"} {:id "defn-/move-and-consume-toward", :kind "defn-", :line 71, :end-line 77, :hash "-1274531809"} {:id "defn-/adjacent-to-city-site?", :kind "defn-", :line 79, :end-line 82, :hash "49227538"} {:id "defn-/adjacent-to-site?", :kind "defn-", :line 84, :end-line 85, :hash "-82053016"} {:id "defn-/desperate-patrol", :kind "defn-", :line 87, :end-line 90, :hash "-471602302"} {:id "defn-/handle-low-fuel", :kind "defn-", :line 92, :end-line 101, :hash "-786913424"} {:id "defn-/handle-patrol", :kind "defn-", :line 103, :end-line 108, :hash "632345729"} {:id "defn-/exploring?", :kind "defn-", :line 110, :end-line 114, :hash "-2145013931"} {:id "defn-/handle-exploration-or-drone", :kind "defn-", :line 116, :end-line 121, :hash "683756139"} {:id "defn-/move-fighter-toward-objective", :kind "defn-", :line 123, :end-line 139, :hash "-1800952099"} {:id "defn-/move-fighter-once", :kind "defn-", :line 141, :end-line 150, :hash "403808442"} {:id "defn-/fighter-at?", :kind "defn-", :line 152, :end-line 155, :hash "-1409954566"} {:id "defn-/burn-stuck-fuel", :kind "defn-", :line 157, :end-line 161, :hash "-826225124"} {:id "defn-/step-fighter", :kind "defn-", :line 163, :end-line 171, :hash "1649110636"} {:id "defn-/computer-fighter?", :kind "defn-", :line 173, :end-line 177, :hash "-388940705"} {:id "defn-/process-threat-fighter?", :kind "defn-", :line 179, :end-line 181, :hash "376643004"} {:id "defn-/run-fighter-steps!", :kind "defn-", :line 183, :end-line 191, :hash "-496949623"} {:id "defn/process-fighter", :kind "defn", :line 193, :end-line 202, :hash "-1975436729"}]}
;; clj-mutate-manifest-end
