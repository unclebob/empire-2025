;; mutation-tested: 2026-03-02
(ns empire.computer.threat-response.major-invasion
  "Major invasion planning and assignment helpers."
  (:require [empire.computer.army.coastal :as army-coastal]
            [empire.computer.core :as core]
            [empire.computer.threat-response.invasion-state :as invasion-state]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]))

(def ^:private major-invasion-unload-radius 2)
(def ^:private max-invasion-coastal-candidates 24)
(def ^:private preferred-invasion-landing-distance 8)
(def ^:private invasion-load-timeout-rounds 5)

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

(defn connected-coastal-candidates
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
  [computer-map computer-sea-unit-types]
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
  [state computer-map computer-sea-unit-types]
  (let [reachable-sea (reachable-sea-set computer-map computer-sea-unit-types)]
    (if (empty? reachable-sea)
      #{}
      (set (filter (fn [target]
                     (some #(land-has-reachable-sea-neighbor? computer-map reachable-sea %)
                           (connected-coastal-candidates computer-map state target)))
                   (:detection-points state))))))

(defn recompute-sea-reachable-detection-points!
  [ctx]
  (let [state ((:load-major-invasion-state ctx))
        computer-map ((:read-runtime-state ctx) :computer-map)
        sea-reachable (sea-reachable-detection-points state computer-map (:computer-sea-unit-types ctx))]
    ((:update-major-invasion-state! ctx) assoc :sea-reachable-detection-points sea-reachable)))

(defn nearest-major-sea-target
  [ctx pos]
  (let [state ((:load-major-invasion-state ctx))
        sea-points (:sea-reachable-detection-points state ::unset)]
    (cond
      (= ::unset sea-points)
      ((:nearest-major-target ctx) pos)

      (seq sea-points)
      (apply min-key #(core/distance pos %) sea-points)

      :else nil)))

(defn- best-invasion-target-and-path
  [ctx pos target]
  (let [state ((:load-major-invasion-state ctx))
        computer-map ((:read-runtime-state ctx) :computer-map)
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

(defn current-target-land-revision
  [ctx]
  (or (:target-land-revision ((:load-major-invasion-state ctx))) 0))

(defn major-invasion-target-revision
  [ctx]
  (current-target-land-revision ctx))

(defn- valid-invasion-plan?
  [pos unit target-revision]
  (and (= :invading (:transport-mission unit))
       (seq (:invasion-path unit))
       (= pos (:invasion-path-origin unit))
       (= target-revision (:invasion-plan-revision unit))))

(defn- stamp-transport-major-invasion-target!
  [ctx pos target target-revision]
  ((:update-game-map! ctx) update-in (conj pos :contents)
   #(cond-> (assoc % :major-invasion true :major-invasion-target target)
      (not= target-revision (:major-invasion-skip-revision %))
      (dissoc :major-invasion-skip-revision))))

(defn- should-plan-invasion-route?
  [pos unit army-count mission opted-out? target-revision]
  (and (not opted-out?)
       (not (zero? army-count))
       (not= mission :unloading)
       (not= mission :load-for-invasion)
       (not (valid-invasion-plan? pos unit target-revision))))

(defn- update-transport-invasion-route!
  [ctx pos target target-revision]
  (let [{actual-target :target path :path}
        (or (if-let [best-fn (:best-invasion-target-and-path-fn ctx)]
              (best-fn pos target)
              (best-invasion-target-and-path ctx pos target))
            {:target target :path nil})]
    (when (some? path)
      (if (empty? path)
        ((:update-game-map! ctx) update-in (conj pos :contents)
         assoc :transport-mission :unloading
         :invasion-target actual-target
         :invasion-plan-revision target-revision
         :invasion-path-origin pos)
        ((:update-game-map! ctx) update-in (conj pos :contents)
         assoc :transport-mission :invading
         :invasion-target actual-target
         :invasion-path path
         :invasion-plan-revision target-revision
         :invasion-path-origin pos)))))

(defn- clear-stale-invasion-routing!
  [ctx pos]
  ((:update-game-map! ctx) update-in (conj pos :contents)
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

(defn- maybe-mark-find-armies-for-invasion!
  [ctx pos army-count target-revision]
  (when (zero? army-count)
    ((:update-game-map! ctx) update-in (conj pos :contents)
     (fn [transport]
       (if (or (= :load-for-invasion (:transport-mission transport))
               (= target-revision (:major-invasion-skip-revision transport)))
         transport
         (assoc transport :transport-mission :find-armies-for-invasion))))))

(defn- nearest-major-ship-target
  [ctx pos]
  (or (if-let [nearest-sea-fn (:nearest-major-sea-target-fn ctx)]
        (nearest-sea-fn pos)
        (nearest-major-sea-target ctx pos))
      ((:nearest-major-target ctx) pos)))

(defn prepare-transport-major-invasion!
  [ctx pos unit]
  (let [army-count (:army-count unit 0)
        target (nearest-major-ship-target ctx pos)
        mission (:transport-mission unit)
        target-revision (current-target-land-revision ctx)
        skip-revision (:major-invasion-skip-revision unit)
        opted-out? (= skip-revision target-revision)]
    (if target
      (do
        (stamp-transport-major-invasion-target! ctx pos target target-revision)
        (when (should-plan-invasion-route? pos unit army-count mission opted-out? target-revision)
          (update-transport-invasion-route! ctx pos target target-revision)))
      (clear-stale-invasion-routing! ctx pos))
    (maybe-mark-find-armies-for-invasion! ctx pos army-count target-revision)))

(defn- assign-army-invasion-embark!
  [ctx pos unit]
  (let [country-id (:country-id unit)]
    (when-not (army-coastal/should-sentry-on-coast? pos country-id)
      (let [target (or (:coast-target unit)
                       (army-coastal/find-coast-target-once pos country-id))]
        ((:update-game-map! ctx) update-in (conj pos :contents)
         #(cond-> (assoc % :mode :move-to-coast-for-invasion)
            target (assoc :coast-target target)))))))

(defn apply-major-invasion-assignment!
  [ctx pos unit]
  (let [t (:type unit)]
    (cond
      (= :fighter t)
      ((:update-game-map! ctx) update-in (conj pos :contents)
       assoc :major-invasion true
       :major-invasion-target ((:nearest-major-target ctx) pos))

      ((:major-invasion-ship-types ctx) t)
      ((:update-game-map! ctx) update-in (conj pos :contents)
       assoc :major-invasion true
       :major-invasion-target (nearest-major-ship-target ctx pos))

      (= :transport t)
      (prepare-transport-major-invasion! ctx pos unit)

      (= :army t)
      (assign-army-invasion-embark! ctx pos unit)

      :else nil)))

(defn trim-stale-find-armies-missions!
  [ctx]
  (let [state ((:load-major-invasion-state ctx))
        round-now ((:read-runtime-state ctx) :round-number)]
    (doseq [pos (:known-transports state)
            :let [unit (get-in ((:current-world ctx)) (conj pos :contents))]
            :when (and unit
                       (= :transport (:type unit))
                       (= :find-armies-for-invasion (:transport-mission unit))
                       (:major-invasion-target unit)
                       (number? round-now))]
      (let [start-round (or (:major-invasion-find-armies-round unit) round-now)
            timed-out? (>= (- round-now start-round) invasion-load-timeout-rounds)]
        (if timed-out?
          ((:update-game-map! ctx) update-in (conj pos :contents)
           #(-> %
                (assoc :major-invasion-skip-revision (current-target-land-revision ctx))
                (assoc :transport-mission :sailing)
                (dissoc :major-invasion-target
                        :major-invasion-find-armies-round
                        :invasion-target
                        :invasion-path
                        :invasion-path-origin
                        :invasion-plan-revision)))
          ((:update-game-map! ctx) assoc-in (conj pos :contents :major-invasion-find-armies-round) start-round))))))
