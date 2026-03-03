;; mutation-tested: 2026-03-03
(ns empire.computer.fighter
  "Computer fighter module - VMS Empire style fighter movement.
   Leg-based coverage, navigation, state machine, process-fighter entry point."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.core :as core]
            [empire.movement.visibility :as visibility]
            [empire.config :as config]
            [empire.computer.fighter.flight-plan :as flight-plan]
            [empire.computer.fighter-movement :as fm]
            [empire.computer.fighter-exploration :as fe]))

(defonce ^:private state-ctx (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

;; --- Leg-based coverage ---

(defn- ensure-flight-target
  [pos]
  (flight-plan/ensure-flight-target! current-world update-game-map! read-runtime-state pos))

(defn- at-flight-target?
  [pos target]
  (flight-plan/at-flight-target? current-world pos target))

(defn- assign-exploration-flight
  [pos site-pos]
  (flight-plan/assign-exploration-flight! update-game-map! (current-world) pos site-pos))

(defn- handle-arrival
  [pos unit]
  (flight-plan/handle-arrival! current-world update-game-map! read-runtime-state write-runtime-state! pos unit))

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
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility explore-pos :computer)
        (when (fm/consume-fighter-fuel explore-pos)
          {:pos explore-pos :hops 1}))
      (when-let [hop (fm/hop-over-friendly pos target)]
        (when-let [{:keys [pos hops]} (fm/execute-hop pos hop)]
          (when (fm/consume-fighter-fuel pos)
            {:pos pos :hops hops}))))))

(defn- refuel-at-site
  "Refuel fighter in place, recording origin site for leg tracking."
  [pos site-pos]
  (update-game-map! assoc-in (conj pos :contents :fuel) config/fighter-fuel)
  (update-game-map! update-in (conj pos :contents)
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
       (= :city (:type (get-in (current-world) site)))
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
        target (:flight-target-site unit)]
    (cond
      ;; Priority: Exploration sortie or drone in progress
      (or (exploring? unit) (= :drone (:flight-mode unit)))
      (handle-exploration-or-drone pos unit)

      ;; Arrived at target refueling site
      (and target (at-flight-target? pos target))
      (handle-arrival pos unit)

      ;; Low fuel → return to nearest refueling site
      (fm/should-return-to-refuel? pos fuel)
      (handle-low-fuel pos)

      ;; Navigate toward target
      target
      (navigate-toward-target pos target fuel)

      :else
      (handle-patrol pos))))

(defn- move-fighter-once
  "Execute one step of fighter priority logic. CC=2.
   Returns {:pos p :hops n}, :landed, or nil (died/stuck)."
  [pos unit]
  (let [enemy-pos (fm/find-adjacent-enemy pos)]
    (if enemy-pos
      (when-let [new-pos (fm/attack-enemy pos enemy-pos)]
        (when (fm/consume-fighter-fuel new-pos)
          {:pos new-pos :hops 1}))
      (move-fighter-toward-objective pos unit))))

(defn- fighter-at?
  "Returns true if a fighter exists at pos on the game map."
  [pos]
  (= :fighter (get-in (current-world) (conj pos :contents :type))))

(defn- burn-stuck-fuel
  "Burns fuel for a stuck fighter at pos. Returns pos if survived, nil if died."
  [pos]
  (when (and (fighter-at? pos) (fm/consume-fighter-fuel pos))
    pos))

(defn- step-fighter
  "Execute one step. Returns {:pos p :steps-used n} or nil (landed/died)."
  [current-pos]
  (when (fighter-at? current-pos)
    (let [unit (get-in (current-world) (conj current-pos :contents))
          result (move-fighter-once current-pos unit)]
      (cond
        (= result :landed) nil
        (map? result) {:pos (:pos result) :steps-used (:hops result)}
        :else (when-let [p (burn-stuck-fuel current-pos)]
                {:pos p :steps-used 1})))))

(defn- computer-fighter?
  [unit]
  (and unit
       (= :computer (:owner unit))
       (= :fighter (:type unit))))

(defn- process-threat-fighter?
  [pos unit]
  (when-let [process-threat (requiring-resolve 'empire.computer.threat-response/process-fighter-threat)]
    (process-threat pos unit)))

(defn- run-fighter-steps!
  [pos]
  (ensure-flight-target pos)
  (loop [current-pos pos
         steps-remaining fm/fighter-speed]
    (when (pos? steps-remaining)
      (when-let [{:keys [pos steps-used]} (step-fighter current-pos)]
        (recur pos (- steps-remaining steps-used))))))

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
