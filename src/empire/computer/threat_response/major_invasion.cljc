(ns empire.computer.threat-response.major-invasion
  "Major invasion planning and assignment helpers."
  (:require [empire.computer.threat-response.major-invasion-assignment :as assignment]
            [empire.computer.threat-response.kamikazee :as kamikazee]
            [empire.computer.threat-response.invasion-state :as invasion-state]
            [empire.computer.threat-response.probe :as probe]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.movement :as computer-movement]
            [empire.computer.shared.world-query :as world-query]
            [empire.state.api :as sa]))

(def ^:private major-invasion-unload-radius 2)
(def ^:private max-invasion-coastal-candidates 24)
(def ^:private preferred-invasion-landing-distance 8)
(def ^:private invasion-load-timeout-rounds 5)
(def ^:private invasion-route-retry-rounds 5)

(defn- target-land-candidates-within-radius*
  [state target]
  (let [candidates (filter #(<= (grid/chebyshev-distance % target) major-invasion-unload-radius)
                           (:target-land-set state))]
    (if (seq candidates)
      candidates
      [target])))

(defn- coastal-land?
  [computer-map land-pos]
  (some (fn [n]
          (= :sea (get-in computer-map (conj n :type))))
        (world-query/get-neighbors land-pos)))

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

(defn- unloadable-target-land?
  [computer-map connected-land pos]
  (let [cell (get-in computer-map pos)]
    (and (contains? connected-land pos)
         cell
         (nil? (:contents cell))
         (or (and (= :land (:type cell))
                  (nil? (:country-id cell)))
             (and (= :city (:type cell))
                  (#{:free :player} (:city-status cell)))))))

(defn- immediate-unload-slot-count
  [computer-map connected-land sea-pos]
  (count (filter #(unloadable-target-land? computer-map connected-land %)
                 (world-query/get-neighbors sea-pos))))

(defn- land-has-reachable-sea-neighbor?
  [computer-map reachable-sea land-pos]
  (some (fn [n]
          (let [cell (get-in computer-map n)]
            (and cell
                 (= :sea (:type cell))
                 (contains? reachable-sea n))))
        (world-query/get-neighbors land-pos)))

(defn- sea-reachable-detection-points
  [state computer-map computer-sea-unit-types]
  (let [reachable-sea (invasion-state/reachable-sea-set computer-map computer-sea-unit-types)]
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
      (apply min-key #(grid/distance pos %) sea-points)

      :else nil)))

(defn best-invasion-target-and-path
  [ctx pos target]
  (let [state ((:load-major-invasion-state ctx))
        computer-map ((:read-runtime-state ctx) :computer-map)
        candidates (let [all-candidates (connected-coastal-candidates computer-map state target)
                         nearby-candidates (filter #(<= (grid/chebyshev-distance % target)
                                                        preferred-invasion-landing-distance)
                                                   all-candidates)
                         candidates-base (if (seq nearby-candidates) nearby-candidates all-candidates)]
                     (->> candidates-base
                          (sort-by (fn [candidate]
                                     [(grid/chebyshev-distance candidate target)
                                      candidate]))
                          (take max-invasion-coastal-candidates)))
        scored (keep (fn [candidate]
                       (when-let [path (computer-movement/bfs-to-land-ho-target pos candidate computer-map)]
                         (let [path' (vec path)
                               connected-land (connected-target-land computer-map state candidate)
                               sea-pos (if (seq path') (last path') pos)
                               unload-slots (immediate-unload-slot-count computer-map connected-land sea-pos)]
                           {:target candidate
                            :path path'
                            :score [(- unload-slots)
                                    (grid/chebyshev-distance candidate target)
                                    (count path')
                                    candidate]})))
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

(defn- current-invading-mission?
  [unit target-revision]
  (and (= :invading (:transport-mission unit))
       (= target-revision (:invasion-plan-revision unit))
       (:invasion-target unit)))

(defn- stamped-transport-target?
  [unit target]
  (and (:major-invasion unit)
       (= target (:major-invasion-target unit))))

(defn- should-stamp-invasion-target?
  [current-transport target target-revision]
  (let [clear-skip? (and (contains? current-transport :major-invasion-skip-revision)
                         (not= target-revision (:major-invasion-skip-revision current-transport)))]
    (and (:type current-transport)
         (or clear-skip?
             (not (stamped-transport-target? current-transport target))))))

(defn- stamp-transport-major-invasion-target!
  [ctx pos target target-revision]
  (let [current-transport (get-in ((:current-world ctx)) (conj pos :contents))
        clear-skip? (and (contains? current-transport :major-invasion-skip-revision)
                         (not= target-revision (:major-invasion-skip-revision current-transport)))]
    (when (should-stamp-invasion-target? current-transport target target-revision)
      ((:update-game-map! ctx) update-in (conj pos :contents)
       #(cond-> (assoc % :major-invasion true :major-invasion-target target)
          clear-skip?
          (dissoc :major-invasion-skip-revision)))
      (when-let [sync-ai-unit! (:sync-ai-unit! ctx)]
        (sync-ai-unit! pos)))))

(defn- should-plan-invasion-route?
  [pos unit army-count mission opted-out? target-revision]
  (and (not opted-out?)
       (not (zero? army-count))
       (not= mission :unloading)
       (not= mission :load-for-invasion)
       (not (current-invading-mission? unit target-revision))
       (not (valid-invasion-plan? pos unit target-revision))))

(defn- reuse-current-invasion-target-and-path
  [ctx pos unit target-revision]
  (let [current-target (:invasion-target unit)
        computer-map ((:read-runtime-state ctx) :computer-map)]
    (when (and current-target
               (= target-revision (:invasion-plan-revision unit)))
      (when-let [path (computer-movement/bfs-to-land-ho-target pos current-target computer-map)]
        {:target current-target
         :path (vec path)}))))

(defn- choose-invasion-target-and-path
  [ctx pos transport target target-revision]
  (or (reuse-current-invasion-target-and-path ctx pos transport target-revision)
      (if-let [best-fn (:best-invasion-target-and-path-fn ctx)]
        (best-fn pos target)
        (best-invasion-target-and-path ctx pos target))
      {:target target :path nil}))

(defn- current-round
  [ctx]
  (or (when-let [read-runtime-state (:read-runtime-state ctx)]
        (read-runtime-state :round-number))
      0))

(defn- invasion-route-retry-deferred?
  [ctx unit]
  (let [retry-round (:invasion-route-retry-after-round unit)]
    (and (number? retry-round)
         (< (current-round ctx) retry-round))))

(defn- adjacent-unloadable-target-land?
  [ctx pos]
  (let [state ((:load-major-invasion-state ctx))
        computer-map ((:read-runtime-state ctx) :computer-map)
        target-land (:target-land-set state)]
    (boolean
     (some #(unloadable-target-land? computer-map target-land %)
           (world-query/get-neighbors pos)))))

(defn- stranded-unloading-major-invasion?
  [ctx pos unit army-count mission opted-out? target-revision]
  (and (not opted-out?)
       (= :unloading mission)
       (:major-invasion unit)
       (pos? army-count)
       (:invasion-target unit)
       (= target-revision (:invasion-plan-revision unit))
       (empty? (:sail-path unit))
       (empty? (:invasion-path unit))
       (not (invasion-route-retry-deferred? ctx unit))
       (not (adjacent-unloadable-target-land? ctx pos))))

(defn- route-fields
  [pos target target-revision path]
  (cond-> {:transport-mission (if (empty? path) :unloading :invading)
           :invasion-target target
           :invasion-plan-revision target-revision
           :invasion-path-origin pos}
    (seq path)
    (assoc :invasion-path path)))

(defn- log-invading-transition!
  [pos current-mission target path target-revision transport]
  (when (and (seq path)
             (not= current-mission :invading))
    (probe/log-event! :transport-entered-invading
                      {:pos pos
                       :from-mission current-mission
                       :target target
                       :path path
                       :target-revision target-revision
                       :transport transport})))

(defn- apply-invasion-route!
  [ctx pos target target-revision path]
  ((:update-game-map! ctx) update-in (conj pos :contents)
   #(-> %
        (merge (route-fields pos target target-revision path))
        (dissoc :load-target-cell
                :load-manifest
                :load-plan-failure
                :hold-sail-to-load-since-round
                :loading-since-round
                :sail-path
                :invasion-route-retry-after-round)))
  (when-let [sync-ai-unit! (:sync-ai-unit! ctx)]
    (sync-ai-unit! pos)))

(defn- update-transport-invasion-route!
  [ctx pos target target-revision]
  (let [current-transport (get-in ((:current-world ctx)) (conj pos :contents))
        current-mission (:transport-mission current-transport)
        {actual-target :target path :path}
        (choose-invasion-target-and-path ctx pos current-transport target target-revision)]
    (when (and (:type current-transport) (some? path))
      (apply-invasion-route! ctx pos actual-target target-revision path)
      (log-invading-transition! pos current-mission actual-target path target-revision current-transport))))

(defn- defer-invasion-route-retry!
  [ctx pos]
  ((:update-game-map! ctx) assoc-in
   (conj pos :contents :invasion-route-retry-after-round)
   (+ (current-round ctx) invasion-route-retry-rounds))
  (when-let [sync-ai-unit! (:sync-ai-unit! ctx)]
    (sync-ai-unit! pos)))

(defn- recover-stranded-invasion-route!
  [ctx pos target-revision]
  (let [current-transport (get-in ((:current-world ctx)) (conj pos :contents))
        current-mission (:transport-mission current-transport)
        route (reuse-current-invasion-target-and-path ctx pos current-transport target-revision)]
    (if-let [{:keys [target path]} route]
      (if (seq path)
        (do
          (apply-invasion-route! ctx pos target target-revision path)
          (log-invading-transition! pos current-mission target path target-revision current-transport))
        (defer-invasion-route-retry! ctx pos))
      (defer-invasion-route-retry! ctx pos))))

(defn- clear-stale-invasion-routing!
  [ctx pos]
  (when (:type (get-in ((:current-world ctx)) (conj pos :contents)))
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
           (assoc transport' :transport-mission (if (zero? (:army-count transport' 0))
                                                  :sail-to-load
                                                  :sail-to-unload))
           transport')))))
  (when-let [sync-ai-unit! (:sync-ai-unit! ctx)]
    (sync-ai-unit! pos)))

(defn- maybe-mark-find-armies-for-invasion!
  [ctx pos army-count target-revision]
  (when (zero? army-count)
    (let [current-transport (get-in ((:current-world ctx)) (conj pos :contents))]
      (when (and (:type current-transport)
                 (not (or (= :load-for-invasion (:transport-mission current-transport))
                          (= target-revision (:major-invasion-skip-revision current-transport))
                          (= :find-armies-for-invasion (:transport-mission current-transport)))))
        ((:update-game-map! ctx) assoc-in (conj pos :contents :transport-mission) :find-armies-for-invasion)
        (when-let [sync-ai-unit! (:sync-ai-unit! ctx)]
          (sync-ai-unit! pos))))))

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
        (cond
          (stranded-unloading-major-invasion? ctx pos unit army-count mission opted-out? target-revision)
          (recover-stranded-invasion-route! ctx pos target-revision)

          (should-plan-invasion-route? pos unit army-count mission opted-out? target-revision)
          (update-transport-invasion-route! ctx pos target target-revision)))
      (clear-stale-invasion-routing! ctx pos))
    (maybe-mark-find-armies-for-invasion! ctx pos army-count target-revision)))

(defn apply-major-invasion-assignment!
  [ctx pos unit]
  (assignment/apply-major-invasion-assignment!
   (assoc ctx
          :nearest-major-ship-target-fn #(nearest-major-ship-target ctx %)
          :prepare-transport-major-invasion!-fn #(prepare-transport-major-invasion! ctx %1 %2))
   pos
   unit))

(defn- finding-armies-for-invasion?
  [unit round-now]
  (and unit
       (= :transport (:type unit))
       (= :find-armies-for-invasion (:transport-mission unit))
       (:major-invasion-target unit)
       (number? round-now)))

(defn- timeout-find-armies-mission!
  [ctx pos]
  ((:update-game-map! ctx) update-in (conj pos :contents)
   #(-> %
        (assoc :major-invasion-skip-revision (current-target-land-revision ctx))
        (assoc :transport-mission (if (zero? (:army-count % 0))
                                    :sail-to-load
                                    :sail-to-unload))
        (dissoc :major-invasion-target
                :major-invasion-find-armies-round
                :invasion-target
                :invasion-path
                :invasion-path-origin
                :invasion-plan-revision))))

(defn trim-stale-find-armies-missions!
  [ctx]
  (let [state ((:load-major-invasion-state ctx))
        round-now ((:read-runtime-state ctx) :round-number)]
    (doseq [pos (:known-transports state)
            :let [unit (get-in ((:current-world ctx)) (conj pos :contents))]
            :when (finding-armies-for-invasion? unit round-now)]
      (let [start-round (or (:major-invasion-find-armies-round unit) round-now)]
        (if (>= (- round-now start-round) invasion-load-timeout-rounds)
          (timeout-find-armies-mission! ctx pos)
          ((:update-game-map! ctx) assoc-in (conj pos :contents :major-invasion-find-armies-round) start-round))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-08T09:06:15.15335-05:00", :module-hash "1135315789", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 10, :hash "-506482337"} {:id "def/major-invasion-unload-radius", :kind "def", :line 12, :end-line 12, :hash "-1532793606"} {:id "def/max-invasion-coastal-candidates", :kind "def", :line 13, :end-line 13, :hash "-329358744"} {:id "def/preferred-invasion-landing-distance", :kind "def", :line 14, :end-line 14, :hash "-1028129861"} {:id "def/invasion-load-timeout-rounds", :kind "def", :line 15, :end-line 15, :hash "1973653881"} {:id "def/invasion-route-retry-rounds", :kind "def", :line 16, :end-line 16, :hash "1384724681"} {:id "defn-/target-land-candidates-within-radius*", :kind "defn-", :line 18, :end-line 24, :hash "529658090"} {:id "defn-/coastal-land?", :kind "defn-", :line 26, :end-line 30, :hash "-2120109713"} {:id "defn-/connected-target-land", :kind "defn-", :line 32, :end-line 35, :hash "-2128426892"} {:id "defn/connected-coastal-candidates", :kind "defn", :line 37, :end-line 43, :hash "1763371992"} {:id "defn-/unloadable-target-land?", :kind "defn-", :line 45, :end-line 54, :hash "-742785503"} {:id "defn-/immediate-unload-slot-count", :kind "defn-", :line 56, :end-line 59, :hash "-724557110"} {:id "defn-/land-has-reachable-sea-neighbor?", :kind "defn-", :line 61, :end-line 68, :hash "1471892783"} {:id "defn-/sea-reachable-detection-points", :kind "defn-", :line 70, :end-line 78, :hash "606914790"} {:id "defn/recompute-sea-reachable-detection-points!", :kind "defn", :line 80, :end-line 85, :hash "-1397968623"} {:id "defn/nearest-major-sea-target", :kind "defn", :line 87, :end-line 98, :hash "760479851"} {:id "defn/best-invasion-target-and-path", :kind "defn", :line 100, :end-line 129, :hash "-670272583"} {:id "defn-/current-target-land-revision", :kind "defn-", :line 131, :end-line 133, :hash "1736132922"} {:id "defn/major-invasion-target-revision", :kind "defn", :line 135, :end-line 137, :hash "1485306651"} {:id "defn-/valid-invasion-plan?", :kind "defn-", :line 139, :end-line 144, :hash "40176473"} {:id "defn-/current-invading-mission?", :kind "defn-", :line 146, :end-line 150, :hash "-1187161722"} {:id "defn-/stamped-transport-target?", :kind "defn-", :line 152, :end-line 155, :hash "1551770107"} {:id "defn-/stamp-transport-major-invasion-target!", :kind "defn-", :line 157, :end-line 170, :hash "-1474292926"} {:id "defn-/should-plan-invasion-route?", :kind "defn-", :line 172, :end-line 179, :hash "431983465"} {:id "defn-/reuse-current-invasion-target-and-path", :kind "defn-", :line 181, :end-line 189, :hash "-657994075"} {:id "defn-/choose-invasion-target-and-path", :kind "defn-", :line 191, :end-line 197, :hash "-428795633"} {:id "defn-/current-round", :kind "defn-", :line 199, :end-line 203, :hash "653495144"} {:id "defn-/invasion-route-retry-deferred?", :kind "defn-", :line 205, :end-line 209, :hash "-1655770037"} {:id "defn-/adjacent-unloadable-target-land?", :kind "defn-", :line 211, :end-line 218, :hash "1648936259"} {:id "defn-/stranded-unloading-major-invasion?", :kind "defn-", :line 220, :end-line 231, :hash "66078566"} {:id "defn-/route-fields", :kind "defn-", :line 233, :end-line 240, :hash "972898936"} {:id "defn-/log-invading-transition!", :kind "defn-", :line 242, :end-line 252, :hash "1689257188"} {:id "defn-/apply-invasion-route!", :kind "defn-", :line 254, :end-line 267, :hash "-1245238467"} {:id "defn-/update-transport-invasion-route!", :kind "defn-", :line 269, :end-line 277, :hash "-1544408001"} {:id "defn-/defer-invasion-route-retry!", :kind "defn-", :line 279, :end-line 285, :hash "-829470118"} {:id "defn-/recover-stranded-invasion-route!", :kind "defn-", :line 287, :end-line 298, :hash "177420461"} {:id "defn-/clear-stale-invasion-routing!", :kind "defn-", :line 300, :end-line 318, :hash "1449647587"} {:id "defn-/maybe-mark-find-armies-for-invasion!", :kind "defn-", :line 320, :end-line 330, :hash "499601852"} {:id "defn-/nearest-major-ship-target", :kind "defn-", :line 332, :end-line 337, :hash "-472358978"} {:id "defn/prepare-transport-major-invasion!", :kind "defn", :line 339, :end-line 357, :hash "-301003230"} {:id "defn/apply-major-invasion-assignment!", :kind "defn", :line 359, :end-line 366, :hash "1442867914"} {:id "defn/trim-stale-find-armies-missions!", :kind "defn", :line 368, :end-line 394, :hash "977085127"}]}
;; clj-mutate-manifest-end
