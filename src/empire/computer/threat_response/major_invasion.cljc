(ns empire.computer.threat-response.major-invasion
  "Major invasion planning and assignment helpers."
  (:require [empire.computer.army.coastal :as army-coastal]
            [empire.computer.core :as core]
            [empire.computer.threat-response.kamikazee :as kamikazee]
            [empire.computer.threat-response.invasion-state :as invasion-state]
            [empire.computer.movement :as computer-movement]
            [empire.state.api :as sa]))

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
                       (when-let [path (computer-movement/bfs-to-land-ho-target pos candidate computer-map)]
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
      (let [state (or (when-let [load-major-invasion-state (:load-major-invasion-state ctx)]
                        (load-major-invasion-state))
                      {})
            world (or (when-let [current-world (:current-world ctx)]
                        (current-world))
                      (sa/current-world))
            targets (kamikazee/ordered-army-target-positions state
                                                             (kamikazee/current-round ctx)
                                                             world)
            plan (kamikazee/plan-route state
                                       world
                                       pos
                                       (:fuel unit 32))]
        ((:update-game-map! ctx) update-in (conj pos :contents)
         #(-> %
              (assoc :major-invasion true
                     :kamikazee true
                     :major-invasion-target (when-let [nearest-major-target (:nearest-major-target ctx)]
                                              (nearest-major-target pos))
                     :kamikazee-targets targets
                     :kamikazee-route (:route plan)
                     :kamikazee-terminal-site (:terminal-site plan)
                     :kamikazee-stage (if (seq (:route plan)) :route :hunt))
              (dissoc :threat-mission :threat-center :threat-radius :threat-rounds-left))))

      ((:major-invasion-ship-types ctx) t)
      (if (= :carrier t)
        (if (kamikazee/carrier-support-target ctx pos)
          ((:update-game-map! ctx) update-in (conj pos :contents)
           assoc :major-invasion true
           :mode :sentry
           :major-invasion-target pos)
          ((:update-game-map! ctx) update-in (conj pos :contents)
           assoc :major-invasion true
           :major-invasion-target (nearest-major-ship-target ctx pos)))
        ((:update-game-map! ctx) update-in (conj pos :contents)
         assoc :major-invasion true
         :major-invasion-target (nearest-major-ship-target ctx pos)))

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-14T10:08:42.851404-05:00", :module-hash "-1001541697", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "1510895066"} {:id "def/major-invasion-unload-radius", :kind "def", :line 10, :end-line 10, :hash "-1532793606"} {:id "def/max-invasion-coastal-candidates", :kind "def", :line 11, :end-line 11, :hash "-329358744"} {:id "def/preferred-invasion-landing-distance", :kind "def", :line 12, :end-line 12, :hash "-1028129861"} {:id "def/invasion-load-timeout-rounds", :kind "def", :line 13, :end-line 13, :hash "1973653881"} {:id "defn-/target-land-candidates-within-radius*", :kind "defn-", :line 15, :end-line 21, :hash "-827635081"} {:id "defn-/coastal-land?", :kind "defn-", :line 23, :end-line 27, :hash "-1736933564"} {:id "defn-/connected-target-land", :kind "defn-", :line 29, :end-line 32, :hash "-2128426892"} {:id "defn/connected-coastal-candidates", :kind "defn", :line 34, :end-line 40, :hash "1763371992"} {:id "defn-/flood-sea-reachable", :kind "defn-", :line 42, :end-line 57, :hash "-1962794039"} {:id "defn-/reachable-sea-set", :kind "defn-", :line 59, :end-line 71, :hash "-222811944"} {:id "defn-/land-has-reachable-sea-neighbor?", :kind "defn-", :line 73, :end-line 80, :hash "-26578749"} {:id "defn-/sea-reachable-detection-points", :kind "defn-", :line 82, :end-line 90, :hash "620253926"} {:id "defn/recompute-sea-reachable-detection-points!", :kind "defn", :line 92, :end-line 97, :hash "-1397968623"} {:id "defn/nearest-major-sea-target", :kind "defn", :line 99, :end-line 110, :hash "1073025441"} {:id "defn-/best-invasion-target-and-path", :kind "defn-", :line 112, :end-line 136, :hash "1427558764"} {:id "defn-/current-target-land-revision", :kind "defn-", :line 138, :end-line 140, :hash "1736132922"} {:id "defn/major-invasion-target-revision", :kind "defn", :line 142, :end-line 144, :hash "1485306651"} {:id "defn-/valid-invasion-plan?", :kind "defn-", :line 146, :end-line 151, :hash "40176473"} {:id "defn-/stamp-transport-major-invasion-target!", :kind "defn-", :line 153, :end-line 158, :hash "934394738"} {:id "defn-/should-plan-invasion-route?", :kind "defn-", :line 160, :end-line 166, :hash "738456503"} {:id "defn-/update-transport-invasion-route!", :kind "defn-", :line 168, :end-line 187, :hash "763781411"} {:id "defn-/clear-stale-invasion-routing!", :kind "defn-", :line 189, :end-line 202, :hash "-1068287357"} {:id "defn-/maybe-mark-find-armies-for-invasion!", :kind "defn-", :line 204, :end-line 212, :hash "-690300027"} {:id "defn-/nearest-major-ship-target", :kind "defn-", :line 214, :end-line 219, :hash "-472358978"} {:id "defn/prepare-transport-major-invasion!", :kind "defn", :line 221, :end-line 235, :hash "708545281"} {:id "defn-/assign-army-invasion-embark!", :kind "defn-", :line 237, :end-line 245, :hash "1480260381"} {:id "defn/apply-major-invasion-assignment!", :kind "defn", :line 247, :end-line 297, :hash "204897230"} {:id "defn/trim-stale-find-armies-missions!", :kind "defn", :line 299, :end-line 323, :hash "-498020734"}]}
;; clj-mutate-manifest-end
