;; mutation-tested: no
(ns empire.computer.threat-response
  "Threat-response coordinator for enemy detections.
   Handles fighter/ship local responses and global major invasion mobilization."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.army.coastal :as army-coastal]
            [empire.computer.core :as core]
            [empire.computer.fighter-movement :as fighter-movement]
            [empire.computer.threat-response.invasion-state :as invasion-state]
            [empire.computer.threat-response.processing :as processing]
            [empire.computer.ship-core :as ship-core]
            [empire.config :as config]
            [empire.domain.ai.threat-policy :as threat-policy]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]))

(def ^:private threat-radius threat-policy/threat-radius)
(def ^:private major-invasion-unload-radius 2)

(def ^:private major-invasion-ship-types
  #{:patrol-boat :destroyer :submarine :carrier :battleship})

(def ^:private computer-sea-unit-types
  (conj major-invasion-ship-types :transport))
(def ^:private max-invasion-coastal-candidates 24)
(def ^:private preferred-invasion-landing-distance 8)
(def ^:private invasion-load-timeout-rounds 5)

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(declare recompute-sea-reachable-detection-points!)
(declare nearest-major-sea-target)

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
                     (:detection-points state))
        current-target-land (:target-land-set state)
        changed? (not= current-target-land target-land)
        next-revision (if changed?
                        (inc (or (:target-land-revision state) 0))
                        (or (:target-land-revision state) 0))]
    (update-major-invasion-state! assoc
                                  :target-land-set target-land
                                  :target-land-revision next-revision)))

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
  (let [state (load-major-invasion-state)
        nearby-existing? (some #(<= (core/chebyshev-distance pos %) 2)
                               (:detection-points state))
        should-add? (or (not (:active? state))
                        (not nearby-existing?))]
    (when should-add?
      (update-major-invasion-state!
       invasion-state/activate-state
       pos
       (read-runtime-state :round-number))
      (recompute-major-invasion-target-land!)
      (recompute-sea-reachable-detection-points!))))

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
    nil)
  nil)

(defn- dec-threat-rounds
  [unit]
  (threat-policy/dec-threat-rounds unit))

(defn- nearest-major-target
  [pos]
  (invasion-state/nearest-target (load-major-invasion-state) pos))

(defn- nearest-major-ship-target
  [pos]
  (or (nearest-major-sea-target pos)
      (nearest-major-target pos)))

(defn- target-land-candidates-within-radius*
  [state target]
  (let [candidates (filter #(<= (core/chebyshev-distance % target) major-invasion-unload-radius)
                           (:target-land-set state))]
    (if (seq candidates)
      candidates
      [target])))

(defn- coastal-land?
  [computer-map land-pos]
  (some (fn [n]
          (= :sea (get-in computer-map (conj n :type))))
        (core/get-neighbors land-pos)))

(defn- connected-target-land
  [computer-map state target]
  (or (invasion-state/flood-fill-land computer-map target)
      (set (target-land-candidates-within-radius* state target))))

(defn- connected-coastal-candidates
  [computer-map state target]
  (let [connected-land (connected-target-land computer-map state target)
        coastal (filter #(coastal-land? computer-map %) connected-land)]
    (if (seq coastal)
      coastal
      (target-land-candidates-within-radius* state target))))

(defn- flood-sea-reachable
  [computer-map starts]
  (loop [queue (into clojure.lang.PersistentQueue/EMPTY starts)
         visited (set starts)]
    (if (empty? queue)
      visited
      (let [current (peek queue)
            rest-queue (pop queue)
            sea-neighbors (for [n (core/get-neighbors current)
                                :let [cell (get-in computer-map n)]
                                :when (and cell
                                           (= :sea (:type cell))
                                           (not (contains? visited n)))]
                            n)]
        (recur (into rest-queue sea-neighbors)
               (into visited sea-neighbors))))))

(defn- reachable-sea-set
  [computer-map]
  (let [starts (for [i (range (count computer-map))
                     j (range (count (first computer-map)))
                     :let [unit (get-in computer-map [i j :contents])]
                     :when (and unit
                                (= :computer (:owner unit))
                                (computer-sea-unit-types (:type unit))
                                (= :sea (get-in computer-map [i j :type])))]
                 [i j])]
    (if (seq starts)
      (flood-sea-reachable computer-map starts)
      #{})))

(defn- land-has-reachable-sea-neighbor?
  [computer-map reachable-sea land-pos]
  (some (fn [n]
          (let [cell (get-in computer-map n)]
            (and cell
                 (= :sea (:type cell))
                 (contains? reachable-sea n))))
        (core/get-neighbors land-pos)))

(defn- sea-reachable-detection-points
  [state computer-map]
  (let [reachable-sea (reachable-sea-set computer-map)]
    (if (empty? reachable-sea)
      #{}
      (set (filter (fn [target]
                     (some #(land-has-reachable-sea-neighbor? computer-map reachable-sea %)
                           (connected-coastal-candidates computer-map state target)))
                   (:detection-points state))))))

(defn- recompute-sea-reachable-detection-points!
  []
  (let [state (load-major-invasion-state)
        computer-map (read-runtime-state :computer-map)
        sea-reachable (sea-reachable-detection-points state computer-map)]
    (update-major-invasion-state! assoc :sea-reachable-detection-points sea-reachable)))

(defn- nearest-major-sea-target
  [pos]
  (let [state (load-major-invasion-state)
        sea-points (:sea-reachable-detection-points state ::unset)]
    (cond
      (= ::unset sea-points)
      (invasion-state/nearest-target state pos)

      (seq sea-points)
      (apply min-key #(core/distance pos %) sea-points)

      :else nil)))

(defn- best-invasion-target-and-path
  [pos target]
  (let [state (load-major-invasion-state)
        computer-map (read-runtime-state :computer-map)
        all-candidates (connected-coastal-candidates computer-map state target)
        nearby-candidates (filter #(<= (core/chebyshev-distance % target)
                                       preferred-invasion-landing-distance)
                                  all-candidates)
        candidates-base (if (seq nearby-candidates) nearby-candidates all-candidates)
        candidates (->> candidates-base
                        (sort-by (fn [candidate]
                                   [(core/chebyshev-distance candidate target)
                                    candidate]))
                        (take max-invasion-coastal-candidates))
        scored (keep (fn [candidate]
                       (when-let [path (pathfinding-bfs/bfs-to-land-ho-target pos candidate computer-map)]
                         {:target candidate
                          :path (vec path)
                          :score [(core/chebyshev-distance candidate target)
                                  (count path)
                                  candidate]}))
                     candidates)]
    (when (seq scored)
      (let [{:keys [target path]} (first (sort-by :score scored))]
        {:target target :path path}))))

(defn- current-target-land-revision
  []
  (or (:target-land-revision (load-major-invasion-state)) 0))

(defn major-invasion-target-revision
  []
  (current-target-land-revision))

(defn- valid-invasion-plan?
  [pos unit target-revision]
  (and (= :invading (:transport-mission unit))
       (seq (:invasion-path unit))
       (= pos (:invasion-path-origin unit))
       (= target-revision (:invasion-plan-revision unit))))

(defn- prepare-transport-major-invasion!
  [pos unit]
  (let [army-count (:army-count unit 0)
        target (nearest-major-ship-target pos)
        mission (:transport-mission unit)
        target-revision (current-target-land-revision)
        skip-revision (:major-invasion-skip-revision unit)
        opted-out? (= skip-revision target-revision)]
    (if target
      (do
        (update-game-map! update-in (conj pos :contents)
                          #(cond-> (assoc % :major-invasion true :major-invasion-target target)
                             (not= target-revision (:major-invasion-skip-revision %))
                             (dissoc :major-invasion-skip-revision)))
        (when (and (not opted-out?)
                   (not (zero? army-count))
                   (not= mission :unloading)
                   (not= mission :load-for-invasion)
                   (not (valid-invasion-plan? pos unit target-revision)))
          (let [{actual-target :target path :path}
                (or (best-invasion-target-and-path pos target)
                    {:target target :path nil})]
            (when (some? path)
              (if (empty? path)
                (update-game-map! update-in (conj pos :contents)
                                  assoc :transport-mission :unloading
                                  :invasion-target actual-target
                                  :invasion-plan-revision target-revision
                                  :invasion-path-origin pos)
                (update-game-map! update-in (conj pos :contents)
                                  assoc :transport-mission :invading
                                  :invasion-target actual-target
                                  :invasion-path path
                                  :invasion-plan-revision target-revision
                                  :invasion-path-origin pos))))))
      ;; No invasion objective: clear stale invasion routing.
      (update-game-map! update-in (conj pos :contents)
                        (fn [transport]
                          (let [transport' (-> transport
                                               (assoc :major-invasion true
                                                      :major-invasion-target nil)
                                               (dissoc :invasion-target
                                                       :invasion-path
                                                       :invasion-plan-revision
                                                       :invasion-path-origin))]
                            (if (= :invading (:transport-mission transport'))
                              (assoc transport' :transport-mission :sailing)
                              transport')))))
    (cond
      (zero? army-count)
      (update-game-map! update-in (conj pos :contents)
                        (fn [transport]
                          (if (or (= :load-for-invasion (:transport-mission transport))
                                  (= target-revision (:major-invasion-skip-revision transport)))
                            transport
                            (assoc transport :transport-mission :find-armies-for-invasion))))
      :else nil)))

(defn- assign-army-invasion-embark!
  [pos unit]
  (let [country-id (:country-id unit)]
    (when-not (army-coastal/should-sentry-on-coast? pos country-id)
      (let [target (or (:coast-target unit)
                       (army-coastal/find-coast-target-once pos country-id))]
        (update-game-map! update-in (conj pos :contents)
                          #(cond-> (assoc % :mode :move-to-coast-for-invasion)
                             target (assoc :coast-target target)))))))

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
                            :major-invasion-target (nearest-major-ship-target pos))

          (= :transport t)
          (prepare-transport-major-invasion! pos unit)

          (= :army t)
          (assign-army-invasion-embark! pos unit)

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
    (recompute-sea-reachable-detection-points!)
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
   {:current-world current-world
    :nearest-major-target nearest-major-ship-target
    :threat-radius threat-radius}
   pos
   ship-type
   unit))
