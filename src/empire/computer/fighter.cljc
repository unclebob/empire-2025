;; mutation-tested: 2026-02-27
(ns empire.computer.fighter
  "Computer fighter module - VMS Empire style fighter movement.
   Leg-based coverage, navigation, state machine, process-fighter entry point."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.core :as core]
            [empire.computer.ship :as ship]
            [empire.movement.visibility :as visibility]
            [empire.config :as config]
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

(defn- current-refueling-site
  "Returns the refueling site position the fighter at pos is at, or nil.
   A fighter is 'at' a city if on it, or 'at' a carrier if adjacent to it."
  [pos]
  (let [cell (get-in (current-world) pos)]
    (cond
      (and (= :city (:type cell)) (= :computer (:city-status cell)))
      pos

      :else
      (first (filter (fn [n]
                       (let [ncell (get-in (current-world) n)]
                         (and (= :carrier (get-in ncell [:contents :type]))
                              (= :computer (get-in ncell [:contents :owner]))
                              (= :holding (get-in ncell [:contents :carrier-mode])))))
                     (core/get-neighbors pos))))))

(defn- choose-leg
  "Choose the best leg from current-site. Returns target site position or nil.
   Prefers unflown legs (absent from records), then oldest (lowest :last-flown)."
  [current-site]
  (let [sites (ship/find-refueling-sites)
        reachable (filter #(and (not= % current-site)
                                (<= (fm/distance-to current-site %) config/fighter-fuel))
                          sites)
        leg-records (or (read-runtime-state :fighter-leg-records) {})
        scored (map (fn [target]
                      (let [leg-key #{current-site target}
                            record (get leg-records leg-key)
                            last-flown (:last-flown record -1)]
                        [target last-flown]))
                    reachable)]
    (when (seq scored)
      (first (first (sort-by second scored))))))

(defn- assign-regular-leg
  "Assign a regular leg flight: choose-leg, set target, origin, and :flight-mode :regular."
  [pos site-pos]
  (when-let [target (choose-leg site-pos)]
    (update-game-map! update-in (conj pos :contents)
                      assoc :flight-target-site target
                      :flight-origin-site site-pos
                      :flight-mode :regular)))

(def ^:private sortie-half-steps 16)

(defn- assign-exploration-flight
  "Assign exploration sortie or drone. Roll 1/20 for drone.
   Pick heading, set exploration fields, project endpoint."
  [pos site-pos]
  (let [heading (fe/best-exploration-heading pos sortie-half-steps)
        drone? (< (rand) 0.05)
        mode (if drone? :drone :explore)
        [dr dc] heading
        endpoint [(+ (first pos) (* sortie-half-steps dr))
                  (+ (second pos) (* sortie-half-steps dc))]]
    (update-game-map! update-in (conj pos :contents)
                      assoc :flight-mode mode
                      :explore-origin site-pos
                      :explore-heading heading
                      :explore-steps-remaining sortie-half-steps
                      :flight-target-site endpoint
                      :flight-origin-site site-pos)))

(defn- ensure-flight-target
  "If fighter at pos is at a refueling site with no flight-mode or target,
   refuel and assign either a regular leg or exploration flight."
  [pos]
  (let [unit (get-in (current-world) (conj pos :contents))]
    (when (and unit (nil? (:flight-mode unit)) (nil? (:flight-target-site unit)))
      (when-let [site-pos (current-refueling-site pos)]
        (update-game-map! assoc-in (conj pos :contents :fuel) config/fighter-fuel)
        (if (>= (rand) 0.5)
          (assign-regular-leg pos site-pos)
          (assign-exploration-flight pos site-pos))))))

(defn- at-flight-target?
  "True if pos has reached the flight target. City: at position. Carrier: adjacent."
  [pos target]
  (let [target-cell (get-in (current-world) target)]
    (or (= pos target)
        (and (= :carrier (get-in target-cell [:contents :type]))
             (<= (fm/distance-to pos target) 1)))))

(defn- handle-arrival
  "Process arrival at target refueling site. Record leg, refuel, pick new leg.
   Returns current pos (no movement this step)."
  [pos unit]
  (let [target (:flight-target-site unit)
        origin (:flight-origin-site unit)]
    ;; Record completed leg (skip degenerate self-loops from returning sorties)
    (when (and origin (not= origin target))
      (write-runtime-state! :fighter-leg-records
                            (assoc (or (read-runtime-state :fighter-leg-records) {})
                                   #{origin target}
                                   {:last-flown (or (read-runtime-state :round-number) 0)})))
    ;; Refuel
    (update-game-map! assoc-in (conj pos :contents :fuel) config/fighter-fuel)
    ;; Clear exploration fields and pick new leg from target site
    (let [new-target (choose-leg target)]
      (update-game-map! update-in (conj pos :contents)
                        #(-> %
                             (dissoc :explore-origin :explore-heading :explore-steps-remaining :flight-mode)
                             (assoc :flight-target-site new-target :flight-origin-site target))))
    {:pos pos :hops 1}))

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

(defn process-fighter
  "Processes a computer fighter using VMS Empire style logic.
   Moves up to fighter-speed (8) cells per round, consuming fuel each step.
   Priority each step: Attack > Arrive at target > Return if low fuel > Navigate/Patrol
   Returns nil."
  [pos unit]
  (when (and unit (= :computer (:owner unit)) (= :fighter (:type unit)))
    (if (when-let [process-threat (requiring-resolve 'empire.computer.threat-response/process-fighter-threat)]
          (process-threat pos unit))
      nil
      (do
        (ensure-flight-target pos)
        (loop [current-pos pos
               steps-remaining fm/fighter-speed]
          (when (pos? steps-remaining)
            (when-let [{:keys [pos steps-used]} (step-fighter current-pos)]
              (recur pos (- steps-remaining steps-used))))))))
  nil)
