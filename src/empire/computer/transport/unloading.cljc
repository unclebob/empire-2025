(ns empire.computer.transport.unloading
  "Transport unloading — opportunistic and targeted army unloading."
  (:require [empire.state.api :as sa]
            [empire.computer.army.assignment :as army-assignment]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.transport.core :as tc]
            [empire.computer.transport.load-targeting :as load-targeting]
            [empire.computer.transport.reservations :as reservations]
            [empire.computer.transport.sailing-path :as sailing-path]
            [empire.computer.threat-response-impl :as threat-response]
            [empire.game-mechanics.unit-stamping :as unit-stamping]
            [empire.game-mechanics.debug.logging :as debug]
            [empire.game-mechanics.visibility :as visibility]
            [empire.computer.shared.world-query :as world-query]
            [empire.computer.shared.movement :as computer-movement]
            [empire.computer.shared.grid :as grid]))


(defn pickup-exclude-ids
  "Returns set of country-ids to exclude: transport's own country-id
   and pickup-country-id."
  [world transport]
  (disj (set [(:country-id transport)
              (:pickup-country-id transport)])
        nil))

(defn- empty-unclaimed-land?
  [cell]
  (and (= :land (:type cell))
       (nil? (:country-id cell))))

(defn- capturable-empty-city?
  [cell]
  (and (= :city (:type cell))
       (#{:free :player} (:city-status cell))))

(defn- allowed-unload-country?
  [cell exclude-ids]
  (or (empty? exclude-ids)
      (not (contains? exclude-ids (:country-id cell)))))

(defn- allowed-major-invasion-land?
  [neighbor-pos major-invasion?]
  (or (not major-invasion?)
      (threat-response/major-invasion-target-land? neighbor-pos)))

(defn unloadable-land-cell?
  "Returns true if cell is empty land/city not excluded by country-id."
  [cell neighbor-pos exclude-ids major-invasion?]
  (and cell
       (or (empty-unclaimed-land? cell)
           (capturable-empty-city? cell))
       (nil? (:contents cell))
       (allowed-major-invasion-land? neighbor-pos major-invasion?)
       (allowed-unload-country? cell exclude-ids)))

(defn adjacent-empty-land
  "Returns adjacent land/city positions that are empty (no unit).
   Excludes land belonging to any country-id in exclude-ids set."
  [world get-neighbors-fn pos exclude-ids major-invasion?]
  (->> (get-neighbors-fn pos)
       (filter (fn [neighbor]
                 (unloadable-land-cell?
                   (get-in world neighbor) neighbor exclude-ids major-invasion?)))
       sort))

(defn- passable-coastal-sea?
  "Returns true if pos is an unvisited coastal sea cell passable by a computer transport."
  [pos visited game-map]
  (and (not (visited pos))
       (let [cell (get-in game-map pos)]
         (and cell
              (= :sea (:type cell))
              (or (nil? (:contents cell))
                  (= :computer (:owner (:contents cell))))
              (some (fn [neighbor]
                      (let [neighbor-cell (get-in game-map neighbor)]
                        (and neighbor-cell
                             (#{:land :city} (:type neighbor-cell)))))
                    (world-query/get-neighbors pos))))))

(defn- has-unloadable-neighbor-at?
  [game-map pos exclude-ids major-invasion?]
  (some (fn [n]
          (unloadable-land-cell? (get-in game-map n) n exclude-ids major-invasion?))
        (world-query/get-neighbors pos)))

(defn- nearby-indexed-coastal-candidates
  [game-map coastal-index pos max-depth]
  (filter (fn [c]
            (and (<= (grid/chebyshev-distance pos c) max-depth)
                 (= :sea (:type (get-in game-map c)))))
          (:coastal-sea-cells coastal-index)))

(defn- indexed-nearby-unloadable-land?
  [game-map coastal-index pos max-depth exclude-ids major-invasion?]
  (boolean
   (some #(has-unloadable-neighbor-at? game-map % exclude-ids major-invasion?)
         (cons pos (nearby-indexed-coastal-candidates game-map coastal-index pos max-depth)))))

(defn- bfs-nearby-unloadable-land?
  [game-map pos max-depth exclude-ids major-invasion?]
  (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [pos 0])
         visited #{pos}]
    (if (empty? queue)
      false
      (let [[current depth] (peek queue)]
        (cond
          (has-unloadable-neighbor-at? game-map current exclude-ids major-invasion?) true
          (>= depth max-depth) (recur (pop queue) visited)
          :else
          (let [coastal-neighbors
                (filter #(passable-coastal-sea? % visited game-map)
                        (world-query/get-neighbors current))]
            (recur (reduce #(conj %1 [%2 (inc depth)]) (pop queue) coastal-neighbors)
                   (into visited coastal-neighbors))))))))

(defn has-nearby-unloadable-land?
  "Check if any coastal sea cell within max-depth of pos has adjacent unloadable land.
   Uses coastal index when available, falls back to BFS."
  [pos transport max-depth]
  (let [game-map (sa/read-state :computer-map)
        exclude-ids (pickup-exclude-ids game-map transport)
        major-invasion? (:major-invasion transport)
        coastal-index (sa/read-state :coastal-index)]
    (cond
      (and coastal-index (pos? max-depth))
      (indexed-nearby-unloadable-land? game-map coastal-index pos max-depth exclude-ids major-invasion?)

      (zero? max-depth)
      (boolean (has-unloadable-neighbor-at? game-map pos exclude-ids major-invasion?))

      :else
      (bfs-nearby-unloadable-land? game-map pos max-depth exclude-ids major-invasion?))))

(defn- return-load-target
  [pos computer-map transport]
  (let [transport-id (:transport-id transport)]
    (load-targeting/choose-load-target-cell
     pos
     computer-map
     {:reserved-coastal-cells (reservations/reserved-coastal-cells transport-id)
      :excluded-country-ids (disj #{(:pickup-country-id transport)} nil)
      :reserved-army-ids (reservations/reserved-army-ids transport-id)})))

(defn- return-load-sail-path
  [pos computer-map load-target-cell]
  (if load-target-cell
    (or (load-targeting/path-to-load-target pos computer-map load-target-cell) [])
    (or (sailing-path/compute-sail-to-load-path pos computer-map) [])))

(defn- path-ready-for-load?
  [pos load-target-cell sail-path]
  (and load-target-cell
       (or (seq sail-path)
           (load-targeting/target-reached? pos load-target-cell))))

(defn- reset-return-loading-fields!
  [pos load-target-cell sail-path stored-manifest]
  (tc/set-transport-mission pos :sail-to-load)
  (tc/update-transport-contents! pos #(dissoc % :unload-target-city))
  (doseq [[field value] {:load-target-cell load-target-cell
                         :load-manifest nil
                         :load-plan-failure nil
                         :hold-sail-to-load-since-round nil
                         :loading-since-round nil
                         :sail-path (vec sail-path)}]
    (tc/assoc-transport-field! pos field value))
  (tc/assoc-transport-field! pos :load-manifest stored-manifest))

(defn- reserve-return-load!
  [transport-id load-target-cell stored-manifest path-ready?]
  (when (and load-target-cell
             (seq stored-manifest)
             path-ready?)
    (reservations/reserve! transport-id
                           load-target-cell
                           stored-manifest)))

(defn- transition-to-loading-inline
  "Inline return-to-load transition — avoids circular dep with facade."
  [pos]
  (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))
        transport-id (:transport-id transport)
        _ (reservations/release! transport-id)
        computer-map (sa/read-state :computer-map)
        load-target-cell (return-load-target pos computer-map transport)
        sail-path (return-load-sail-path pos computer-map load-target-cell)
        path-ready? (path-ready-for-load? pos load-target-cell sail-path)]
    (let [manifest (vec (army-assignment/assign-returning-transport-staging-at! pos load-target-cell))
          stored-manifest (when (seq manifest) manifest)]
      (reset-return-loading-fields! pos load-target-cell sail-path stored-manifest)
      (reserve-return-load! transport-id load-target-cell stored-manifest path-ready?)
      (visibility/sync-ai-unit-to-computer-map! pos)
      nil)))

(defn- unload-continent-metrics
  [transport land-pos]
  (let [target-continent (land-objectives/flood-fill-continent land-pos)
        continent-scan (when target-continent
                         (land-objectives/scan-continent target-continent))
        produced-at (:produced-at transport)
        foreign-continent? (and target-continent
                                produced-at
                                (not (contains? target-continent produced-at)))
        had-computer-presence? (and continent-scan
                                    (or (pos? (:computer-cities continent-scan 0))
                                        (pos? (:computer-units continent-scan 0))))]
    {:foreign-continent? (boolean foreign-continent?)
     :first-landing-on-continent? (and foreign-continent?
                                      (not had-computer-presence?))
     :continent-id (land-objectives/continent-id target-continent)
     :continent-size (:size continent-scan 0)
     :continent-computer-cities (:computer-cities continent-scan 0)
     :continent-computer-units (:computer-units continent-scan 0)}))

(defn- log-foreign-continent-landing!
  [pos transport landing-pos landing-metrics]
  (when (:first-landing-on-continent? landing-metrics)
    (debug/log-computer-event! :transport-foreign-continent-landing
                               pos
                               {:to landing-pos
                                :transport-id (:transport-id transport)
                                :transport-mission (:transport-mission transport)
                                :army-count-before (:army-count transport 0)
                                :major-invasion (:major-invasion transport)
                                :invasion-target (:invasion-target transport)
                                :major-invasion-target (:major-invasion-target transport)
                                :foreign-continent? true
                                :first-landing-on-continent? true
                                :continent-id (:continent-id landing-metrics)
                                :continent-size (:continent-size landing-metrics)
                                :continent-computer-cities (:continent-computer-cities landing-metrics)
                                :continent-computer-units (:continent-computer-units landing-metrics)})))

(defn- unload-army-template
  [transport]
  (let [unload-eid (:unload-event-id transport)
        unload-cid (or (:unload-country-id transport) (:country-id transport))]
    (cond-> (unit-stamping/ensure-computer-unit-id
             {:type :army :owner :computer :mode :move-inland :hits 1})
      unload-eid (assoc :unload-event-id unload-eid)
      unload-cid (assoc :country-id unload-cid))))

(defn- place-unloaded-armies!
  [pos targets transport unload-eid]
  (let [landing-metrics (when-let [landing-pos (first targets)]
                          (unload-continent-metrics transport landing-pos))]
    (when-let [landing-pos (first targets)]
      (log-foreign-continent-landing! pos transport landing-pos landing-metrics))
    (doseq [land-pos targets]
      (let [army (unload-army-template transport)]
      (debug/log-computer-event! :transport-unload-army
                                 pos
                                 {:to land-pos
                                  :eid unload-eid
                                  :transport-id (:transport-id transport)
                                  :transport-mission (:transport-mission transport)
                                  :army-count-before (:army-count transport 0)
                                  :major-invasion (:major-invasion transport)
                                  :invasion-target (:invasion-target transport)
                                  :major-invasion-target (:major-invasion-target transport)
                                  :foreign-continent? (:foreign-continent? landing-metrics)
                                  :first-landing-on-continent? (:first-landing-on-continent? landing-metrics)
                                  :continent-id (:continent-id landing-metrics)
                                  :continent-size (:continent-size landing-metrics)
                                  :continent-computer-cities (:continent-computer-cities landing-metrics)
                                  :continent-computer-units (:continent-computer-units landing-metrics)})
        (sa/update-world! assoc-in (conj land-pos :contents) army)
        (action-resolution/stamp-territory land-pos army)
        (computer-movement/update-cell-visibility! land-pos :computer)))))

(defn- record-unloaded-country!
  [pos targets]
  (let [unloaded-cid (->> targets
                          (keep #(:country-id (get-in (sa/read-state :computer-map) %)))
                          first)]
    (when unloaded-cid
      (sa/update-world! update-in (conj pos :contents :unloaded-countries)
                        assoc unloaded-cid (or (sa/read-state :round-number) 0))
      (visibility/sync-ai-unit-to-computer-map! pos))))

(defn- finish-unload!
  [pos army-count to-unload]
  (sa/update-world! update-in (conj pos :contents :army-count) - to-unload)
  (sa/update-world! assoc-in (conj pos :contents :last-unload-round)
                    (or (sa/read-state :round-number) 0))
  (if (pos? (- army-count to-unload))
    (sa/update-world! update-in (conj pos :contents)
                      #(if (:unloading-hold-since-round %)
                         %
                         (assoc % :unloading-hold-since-round
                                  (or (sa/read-state :round-number) 0))))
    (sa/update-world! update-in (conj pos :contents) dissoc :unloading-hold-since-round))
  (visibility/sync-ai-unit-to-computer-map! pos)
  (when (<= (- army-count to-unload) 0)
    (transition-to-loading-inline pos)))

(defn- adjacent-unloadable-neighbors
  [pos]
  (let [game-map (sa/read-state :computer-map)]
    (sort
     (filter (fn [neighbor]
               (let [cell (get-in game-map neighbor)]
                 (and (or (empty-unclaimed-land? cell)
                          (capturable-empty-city? cell))
                      (nil? (:contents cell)))))
             (world-query/get-neighbors pos)))))

(defn- adjacent-empty-land-any
  [game-map pos]
  (sort
   (filter (fn [neighbor]
             (let [cell (get-in game-map neighbor)]
               (and cell
                    (or (= :land (:type cell))
                        (#{:free :player :computer} (:city-status cell)))
                    (nil? (:contents cell)))))
           (world-query/get-neighbors pos))))

(defn- unload-to-targets!
  [pos targets record-country?]
  (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))
        army-count (:army-count transport 0)
        to-unload (min army-count (count targets))]
    (when (pos? to-unload)
      (let [selected-targets (take to-unload targets)
            unload-eid (:unload-event-id transport)]
        (place-unloaded-armies! pos selected-targets transport unload-eid)
        (when record-country?
          (record-unloaded-country! pos selected-targets))
        (finish-unload! pos army-count to-unload)
        true))))

(defn try-opportunistic-unload
  "If transport has armies and there is adjacent empty land,
   unload all possible armies onto targets. Returns true if any unloaded."
  [pos]
  (let [game-map (sa/read-state :computer-map)
        army-count (:army-count (get-in game-map (conj pos :contents)) 0)]
    (when (pos? army-count)
      (unload-to-targets! pos (adjacent-empty-land-any game-map pos) true))))

(defn try-opportunistic-unload-any-land
  "Lake-locked transport unload: drop armies on any adjacent empty land/city.
   Ignores major-invasion target filtering and pickup exclusions."
  [pos]
  (let [game-map (sa/read-state :computer-map)
        army-count (:army-count (get-in game-map (conj pos :contents)) 0)]
    (when (pos? army-count)
      (unload-to-targets! pos (adjacent-empty-land-any game-map pos) false))))

(defn unload-armies
  "Unload armies onto adjacent unclaimed land. Returns true if any unloaded."
  ([pos]
   (unload-armies pos nil))
  ([pos _]
   (unload-to-targets! pos (adjacent-unloadable-neighbors pos) true)))

(defn unloading-crawl-move
  "Moves unloading transport to adjacent coastal sea cell to find empty land.
   Like coastal-crawl-move but without auto-loading armies."
  [pos]
  (let [game-map (sa/read-state :computer-map)
        unit (get-in game-map (conj pos :contents))
        history (set (:crawl-history unit []))
        passable (tc/get-passable-sea-neighbors pos)
        empty-passable (filter (fn [n]
                                 (nil? (:contents (get-in game-map n))))
                               passable)
        coastal-cells (filter tc/adjacent-to-land? empty-passable)
        preferred (remove history coastal-cells)
        targets (if (seq preferred) preferred coastal-cells)]
    (when (seq targets)
      (let [target (rand-nth targets)]
        (action-resolution/move-unit-to pos target)
        (computer-movement/update-cell-visibility! pos :computer)
        (computer-movement/update-cell-visibility! target :computer)
        (let [new-history (vec (take-last 3 (conj (:crawl-history unit []) pos)))]
          (sa/update-world! assoc-in (conj target :contents :crawl-history) new-history))
        (visibility/sync-ai-unit-to-computer-map! target)
        target))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-07T16:49:14.354232-05:00", :module-hash "561938929", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 17, :hash "328641055"} {:id "defn/pickup-exclude-ids", :kind "defn", :line 20, :end-line 26, :hash "-787496537"} {:id "defn/unloadable-land-cell?", :kind "defn", :line 28, :end-line 40, :hash "-1577162921"} {:id "defn/adjacent-empty-land", :kind "defn", :line 42, :end-line 50, :hash "869341940"} {:id "defn-/passable-coastal-sea?", :kind "defn-", :line 52, :end-line 65, :hash "-1663501396"} {:id "defn-/has-unloadable-neighbor-at?", :kind "defn-", :line 67, :end-line 71, :hash "-1791340750"} {:id "defn-/nearby-indexed-coastal-candidates", :kind "defn-", :line 73, :end-line 78, :hash "996811274"} {:id "defn-/indexed-nearby-unloadable-land?", :kind "defn-", :line 80, :end-line 84, :hash "1166261685"} {:id "defn-/bfs-nearby-unloadable-land?", :kind "defn-", :line 86, :end-line 101, :hash "-2134187530"} {:id "defn/has-nearby-unloadable-land?", :kind "defn", :line 103, :end-line 119, :hash "1785986405"} {:id "defn-/return-load-target", :kind "defn-", :line 121, :end-line 129, :hash "1406131320"} {:id "defn-/return-load-sail-path", :kind "defn-", :line 131, :end-line 135, :hash "-823830989"} {:id "defn-/path-ready-for-load?", :kind "defn-", :line 137, :end-line 141, :hash "1332724633"} {:id "defn-/reset-return-loading-fields!", :kind "defn-", :line 143, :end-line 154, :hash "1051601985"} {:id "defn-/reserve-return-load!", :kind "defn-", :line 156, :end-line 163, :hash "1375367898"} {:id "defn-/transition-to-loading-inline", :kind "defn-", :line 165, :end-line 180, :hash "416996834"} {:id "defn-/unload-continent-metrics", :kind "defn-", :line 182, :end-line 200, :hash "-924315629"} {:id "defn-/log-foreign-continent-landing!", :kind "defn-", :line 202, :end-line 219, :hash "506796922"} {:id "defn-/unload-army-template", :kind "defn-", :line 221, :end-line 228, :hash "455949077"} {:id "defn-/place-unloaded-armies!", :kind "defn-", :line 230, :end-line 256, :hash "-1782757694"} {:id "defn-/record-unloaded-country!", :kind "defn-", :line 258, :end-line 266, :hash "-379441948"} {:id "defn-/finish-unload!", :kind "defn-", :line 268, :end-line 282, :hash "-1338790402"} {:id "defn-/adjacent-unloadable-neighbors", :kind "defn-", :line 284, :end-line 296, :hash "-1318494185"} {:id "defn-/adjacent-empty-land-any", :kind "defn-", :line 298, :end-line 307, :hash "-1358600425"} {:id "defn/try-opportunistic-unload", :kind "defn", :line 309, :end-line 325, :hash "-688667391"} {:id "defn/try-opportunistic-unload-any-land", :kind "defn", :line 327, :end-line 342, :hash "-1631433894"} {:id "defn/unload-armies", :kind "defn", :line 344, :end-line 360, :hash "1614465478"} {:id "defn/unloading-crawl-move", :kind "defn", :line 362, :end-line 384, :hash "1311152599"}]}
;; clj-mutate-manifest-end
