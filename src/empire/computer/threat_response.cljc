;; mutation-tested: no
(ns empire.computer.threat-response
  "Threat-response coordinator for enemy detections.
   Handles fighter/ship local responses and global major invasion mobilization."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.core :as core]
            [empire.computer.fighter-movement :as fighter-movement]
            [empire.computer.threat-response.invasion-state :as invasion-state]
            [empire.computer.threat-response.processing :as processing]
            [empire.computer.ship-core :as ship-core]
            [empire.config :as config]
            [empire.domain.ai.threat-policy :as threat-policy]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]))

(def ^:private threat-radius threat-policy/threat-radius)

(def ^:private major-invasion-ship-types
  #{:patrol-boat :destroyer :submarine :carrier :battleship})

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- load-major-invasion-state
  []
  ((:load-major-invasion-state @state-ctx)))

(defn- save-major-invasion-state!
  [state]
  ((:save-major-invasion-state! @state-ctx) state))

(defn- update-major-invasion-state!
  [f & args]
  (let [current (load-major-invasion-state)
        next-state (apply f current args)]
    (save-major-invasion-state! next-state)))

(defn major-invasion-active?
  []
  (:active? (load-major-invasion-state)))

(defn major-invasion-detection-points
  []
  (:detection-points (load-major-invasion-state)))

(defn major-invasion-target-land?
  [pos]
  (contains? (:target-land-set (load-major-invasion-state)) pos))

(defn- recompute-major-invasion-target-land!
  []
  (let [state (load-major-invasion-state)
        target-land (invasion-state/recompute-target-land
                     (current-world)
                     (:detection-points state))]
    (update-major-invasion-state! assoc :target-land-set target-land)))

(defn- find-computer-unit-positions
  [pred]
  (let [game-map (current-world)]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [unit (get-in game-map [i j :contents])]
          :when (and unit
                     (= :computer (:owner unit))
                     (pred unit))]
      [i j])))

(defn- assign-threat-mission!
  [positions mission-kv]
  (doseq [pos positions]
    (update-game-map! update-in (conj pos :contents) merge mission-kv)))

(defn- closest-positions
  [origin positions n]
  (->> positions
       (sort-by #(core/distance % origin))
       (take n)))

(defn- activate-major-invasion!
  [pos]
  (update-major-invasion-state!
   invasion-state/activate-state
   pos
   (read-runtime-state :round-number))
  (recompute-major-invasion-target-land!))

(defn- handle-fighter-detection!
  [pos]
  (let [fighters (find-computer-unit-positions #(= :fighter (:type %)))
        selected (closest-positions pos fighters threat-policy/fighter-response-count)]
    (assign-threat-mission!
     selected (threat-policy/fighter-sweep-mission pos))))

(defn- handle-ship-detection!
  [pos]
  (let [patrols (find-computer-unit-positions #(= :patrol-boat (:type %)))
        battleships (find-computer-unit-positions #(= :battleship (:type %)))
        psel (closest-positions pos patrols threat-policy/ship-response-count)
        bsel (closest-positions pos battleships threat-policy/ship-response-count)
        selected (concat psel bsel)]
    (assign-threat-mission! selected (threat-policy/sea-scout-mission pos))))

(defn handle-detection!
  "Handle a newly-visible cell on computer-map for threat triggers."
  [pos game-cell]
  (case (threat-policy/detection-trigger game-cell)
    :fighter-detected (handle-fighter-detection! pos)
    :ship-detected (handle-ship-detection! pos)
    :major-invasion-trigger (activate-major-invasion! pos)
    nil))

(defn- dec-threat-rounds
  [unit]
  (threat-policy/dec-threat-rounds unit))

(defn- nearest-major-target
  [pos]
  (invasion-state/nearest-target (load-major-invasion-state) pos))

(defn- prepare-transport-major-invasion!
  [pos unit]
  (let [army-count (:army-count unit 0)
        target (nearest-major-target pos)
        mission (:transport-mission unit)]
    (when target
      (update-game-map! update-in (conj pos :contents)
                        assoc :major-invasion true :major-invasion-target target))
    (cond
      (zero? army-count)
      (update-game-map! update-in (conj pos :contents)
                        assoc :transport-mission :loading)

      (and target (not= mission :unloading))
      (when-let [path (pathfinding-bfs/bfs-to-land-ho-target pos target (read-runtime-state :computer-map))]
        (update-game-map! update-in (conj pos :contents)
                          assoc :transport-mission :invading
                          :invasion-target target
                          :invasion-path (vec path))))))

(defn refresh-major-invasion-assignments!
  "Applies major-invasion tags/targets to all mobilized computer units."
  []
  (when (major-invasion-active?)
    (let [units (find-computer-unit-positions (constantly true))
          world (current-world)]
      (doseq [pos units
              :let [unit (get-in world (conj pos :contents))
                    t (:type unit)]]
        (cond
          (= :fighter t)
          (update-game-map! update-in (conj pos :contents)
                            assoc :major-invasion true
                            :major-invasion-target (nearest-major-target pos))

          (major-invasion-ship-types t)
          (update-game-map! update-in (conj pos :contents)
                            assoc :major-invasion true
                            :major-invasion-target (nearest-major-target pos))

          (= :transport t)
          (prepare-transport-major-invasion! pos unit)

          :else nil)))))

(defn on-round-start!
  "Round-start maintenance for threat responses."
  []
  ;; Tick temporary fighter/ship response timers.
  (let [game-map (current-world)]
    (doseq [i (range (count game-map))
            j (range (count (first game-map)))
            :let [unit (get-in game-map [i j :contents])]
            :when (and unit
                       (= :computer (:owner unit))
                       (:threat-mission unit))]
      (update-game-map! update-in [i j :contents] threat-policy/dec-threat-rounds)))
  ;; Recompute invasion theater and refresh global mobilization tags each round.
  (when (major-invasion-active?)
    (recompute-major-invasion-target-land!)
    (refresh-major-invasion-assignments!)))

(defn prepare-transport!
  "Called by transport processing; applies major-invasion directives when active."
  [pos]
  (when (major-invasion-active?)
    (when-let [unit (get-in (current-world) (conj pos :contents))]
      (when (= :transport (:type unit))
        (prepare-transport-major-invasion! pos unit)
        true))))

(defn- fighter-step-threat
  [pos unit]
  (processing/fighter-step-threat
   {:current-world current-world
    :update-game-map! update-game-map!
    :threat-radius threat-radius}
   pos
   unit))

(defn process-fighter-threat
  "Overrides regular fighter logic while fighter-sweep threat mission is active.
   Returns true when handled."
  [pos unit]
  (when (= :fighter-sweep (:threat-mission unit))
    (loop [current pos
           remaining fighter-movement/fighter-speed]
      (when (pos? remaining)
        (when-let [{:keys [pos steps-used]}
                   (fighter-step-threat current (get-in (current-world) (conj current :contents)))]
          (recur pos (- remaining steps-used)))))
    true))

(defn process-ship-threat
  "Overrides regular ship logic for sea-scout and major-invasion missions.
   Returns true when handled."
  [pos ship-type unit]
  (processing/process-ship-threat
   {:nearest-major-target nearest-major-target
    :threat-radius threat-radius}
   pos
   ship-type
   unit))
