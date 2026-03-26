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
            [empire.computer.shared.movement :as computer-movement]))


(defn pickup-exclude-ids
  "Returns set of country-ids to exclude: transport's own country-id
   and pickup-country-id."
  [world transport]
  (disj (set [(:country-id transport)
              (:pickup-country-id transport)])
        nil))

(defn unloadable-land-cell?
  "Returns true if cell is empty land/city not excluded by country-id."
  [cell neighbor-pos exclude-ids major-invasion?]
  (and cell
       (or (and (= :land (:type cell))
                (nil? (:country-id cell)))
           (and (= :city (:type cell))
                (#{:free :player} (:city-status cell))))
       (nil? (:contents cell))
       (or (not major-invasion?)
           (threat-response/major-invasion-target-land? neighbor-pos))
       (or (empty? exclude-ids)
           (not (contains? exclude-ids (:country-id cell))))))

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

(defn has-nearby-unloadable-land?
  "BFS along coastal sea cells up to max-depth hops from pos.
   Returns true if any visited position has adjacent empty land
   not excluded by country-id or pickup continent."
  [pos transport max-depth]
  (let [game-map (sa/read-state :computer-map)
        exclude-ids (pickup-exclude-ids game-map transport)
        major-invasion? (:major-invasion transport)
        has-unloadable-neighbor? (fn [p]
                                   (some (fn [n]
                                           (unloadable-land-cell?
                                             (get-in game-map n) n exclude-ids major-invasion?))
                                         (world-query/get-neighbors p)))]
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [pos 0])
           visited #{pos}]
      (if (empty? queue)
        false
        (let [[current depth] (peek queue)]
          (cond
            (has-unloadable-neighbor? current) true
            (>= depth max-depth) (recur (pop queue) visited)
            :else
            (let [coastal-neighbors
                  (filter #(passable-coastal-sea? % visited game-map)
                          (world-query/get-neighbors current))]
              (recur (reduce #(conj %1 [%2 (inc depth)]) (pop queue) coastal-neighbors)
                     (into visited coastal-neighbors)))))))))

(defn- transition-to-loading-inline
  "Inline return-to-load transition — avoids circular dep with facade."
  [pos]
  (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))
        transport-id (:transport-id transport)
        _ (reservations/release! transport-id)
        computer-map (sa/read-state :computer-map)
        load-target-cell (load-targeting/choose-load-target-cell
                          pos
                          computer-map
                          {:reserved-coastal-cells (reservations/reserved-coastal-cells transport-id)
                           :excluded-country-ids (disj (conj (set (keys (:unloaded-countries transport)))
                                                             (:pickup-country-id transport))
                                                       nil)
                           :reserved-army-ids (reservations/reserved-army-ids transport-id)})
        sail-path (if load-target-cell
                    (or (load-targeting/path-to-load-target pos computer-map load-target-cell)
                        [])
                    (or (sailing-path/compute-sail-to-load-path pos computer-map)
                        []))
        path-ready? (and load-target-cell
                         (or (seq sail-path)
                             (load-targeting/target-reached? pos load-target-cell)))]
    (let [manifest (vec (army-assignment/assign-returning-transport-staging-at! pos load-target-cell))
          stored-manifest (when (seq manifest) manifest)]
      (tc/set-transport-mission pos :sail-to-load)
      (tc/update-transport-contents! pos #(dissoc % :unload-target-city))
      (tc/assoc-transport-field! pos :load-target-cell load-target-cell)
      (tc/assoc-transport-field! pos :load-manifest nil)
      (tc/assoc-transport-field! pos :load-plan-failure nil)
      (tc/assoc-transport-field! pos :hold-sail-to-load-since-round nil)
      (tc/assoc-transport-field! pos :loading-since-round nil)
      (tc/assoc-transport-field! pos :sail-path (vec sail-path))
      (tc/assoc-transport-field! pos :load-manifest stored-manifest)
      (when (and load-target-cell
                 (seq stored-manifest)
                 path-ready?)
        (reservations/reserve! transport-id
                               load-target-cell
                               stored-manifest))
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
  [pos targets army unload-eid]
  (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))
        landing-metrics (when-let [landing-pos (first targets)]
                          (unload-continent-metrics transport landing-pos))]
    (when-let [landing-pos (first targets)]
      (log-foreign-continent-landing! pos transport landing-pos landing-metrics))
    (doseq [land-pos targets]
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
      (computer-movement/update-cell-visibility! land-pos :computer))))

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
                 (and cell
                      (or (and (= :land (:type cell))
                               (nil? (:country-id cell)))
                          (and (= :city (:type cell))
                               (#{:free :player} (:city-status cell))))
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

(defn try-opportunistic-unload
  "If transport has armies and there is adjacent unclaimed land,
   unload all possible armies onto targets. Returns true if any unloaded."
  [pos]
  (let [game-map (sa/read-state :computer-map)
        transport (get-in game-map (conj pos :contents))
        army-count (:army-count transport 0)
        exclude-ids (pickup-exclude-ids game-map transport)
        major-invasion? (:major-invasion transport)
        targets (when (pos? army-count)
                  (adjacent-empty-land game-map
                                       world-query/get-neighbors
                                       pos
                                       exclude-ids
                                       major-invasion?))
        to-unload (min army-count (count targets))]
    (when (pos? to-unload)
      (let [selected-targets (take to-unload targets)
            unload-eid (:unload-event-id transport)
            army (unload-army-template transport)]
        (place-unloaded-armies! pos selected-targets army unload-eid)
        (record-unloaded-country! pos selected-targets)
        (finish-unload! pos army-count to-unload)
        true))))

(defn try-opportunistic-unload-any-land
  "Lake-locked transport unload: drop armies on any adjacent empty land/city.
   Ignores major-invasion target filtering and pickup exclusions."
  [pos]
  (let [game-map (sa/read-state :computer-map)
        transport (get-in game-map (conj pos :contents))
        army-count (:army-count transport 0)
        targets (when (pos? army-count)
                  (adjacent-empty-land-any game-map pos))
        to-unload (min army-count (count targets))]
    (when (pos? to-unload)
      (let [unload-eid (:unload-event-id transport)
            army (unload-army-template transport)
            selected-targets (take to-unload targets)]
        (place-unloaded-armies! pos selected-targets army unload-eid)
        (finish-unload! pos army-count to-unload)
        true))))

(defn unload-armies
  "Unload armies onto adjacent unclaimed land. Returns true if any unloaded."
  ([pos]
   (unload-armies pos nil))
  ([pos _]
   (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))
         army-count (:army-count transport 0)]
     (when (pos? army-count)
       (let [land-neighbors (adjacent-unloadable-neighbors pos)
             to-unload (min army-count (count land-neighbors))]
         (when (pos? to-unload)
           (let [selected-targets (take to-unload land-neighbors)
                 unload-eid (:unload-event-id transport)
                 army (unload-army-template transport)]
             (place-unloaded-armies! pos selected-targets army unload-eid)
             (record-unloaded-country! pos selected-targets)
             (finish-unload! pos army-count to-unload))
           true))))))

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
;; {:version 1, :tested-at "2026-03-12T11:59:11.701992-05:00", :module-hash "1542528357", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 10, :hash "571949949"} {:id "defn/pickup-exclude-ids", :kind "defn", :line 13, :end-line 21, :hash "1846454024"} {:id "defn/pickup-continent-if-needed", :kind "defn", :line 23, :end-line 29, :hash "-1134240335"} {:id "defn/unloadable-land-cell?", :kind "defn", :line 31, :end-line 42, :hash "-1386163529"} {:id "defn/adjacent-empty-land", :kind "defn", :line 44, :end-line 52, :hash "27296169"} {:id "defn-/passable-coastal-sea?", :kind "defn-", :line 54, :end-line 63, :hash "1069173453"} {:id "defn/has-nearby-unloadable-land?", :kind "defn", :line 65, :end-line 92, :hash "1193770655"} {:id "defn-/transition-to-loading-inline", :kind "defn-", :line 94, :end-line 110, :hash "-55081164"} {:id "defn-/unload-army-template", :kind "defn-", :line 112, :end-line 118, :hash "-201166129"} {:id "defn-/place-unloaded-armies!", :kind "defn-", :line 120, :end-line 126, :hash "381899336"} {:id "defn-/record-unloaded-country!", :kind "defn-", :line 128, :end-line 135, :hash "-84411371"} {:id "defn-/finish-unload!", :kind "defn-", :line 137, :end-line 141, :hash "270973256"} {:id "defn-/adjacent-unloadable-neighbors", :kind "defn-", :line 143, :end-line 153, :hash "-947538451"} {:id "defn-/adjacent-empty-land-any", :kind "defn-", :line 155, :end-line 162, :hash "-456014395"} {:id "defn/try-opportunistic-unload", :kind "defn", :line 164, :end-line 189, :hash "-1410515644"} {:id "defn/try-opportunistic-unload-any-land", :kind "defn", :line 191, :end-line 207, :hash "817909817"} {:id "defn/unload-armies", :kind "defn", :line 209, :end-line 224, :hash "818516088"} {:id "defn/unloading-crawl-move", :kind "defn", :line 226, :end-line 247, :hash "-1673599752"}]}
;; clj-mutate-manifest-end
