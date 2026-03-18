(ns empire.computer.transport-unloading
  "Transport unloading — opportunistic and targeted army unloading."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.transport-core :as tc]
            [empire.computer.transport-targeting :as targeting]
            [empire.computer.threat-response :as threat-response]
            [empire.game-mechanics.debug.logging :as debug]
            [empire.computer.movement :as computer-movement]))


(defn pickup-exclude-ids
  "Returns set of country-ids to exclude: transport's own country-id,
   pickup-country-id, and the country-id at pickup-continent-pos."
  [world transport]
  (disj (set [(:country-id transport)
              (:pickup-country-id transport)
              (when-let [pcp (:pickup-continent-pos transport)]
                (:country-id (get-in world pcp)))])
        nil))

(defn pickup-continent-if-needed
  "Returns the pickup continent set for pickup-continent-pos using cached flood-fill.
   Always prefer geography-based exclusion to avoid load/unload loops when
   country-id stamping changes on the same landmass."
  [transport]
  (when-let [pcp (:pickup-continent-pos transport)]
    (land-objectives/flood-fill-continent pcp)))

(defn unloadable-land-cell?
  "Returns true if cell is empty land/city not excluded by country-id or pickup continent."
  [cell neighbor-pos exclude-ids pickup-continent major-invasion?]
  (and cell
       (#{:land :city} (:type cell))
       (nil? (:contents cell))
       (or (not major-invasion?)
           (threat-response/major-invasion-target-land? neighbor-pos))
       (or (empty? exclude-ids)
           (not (contains? exclude-ids (:country-id cell))))
       (or (nil? pickup-continent)
           (not (contains? pickup-continent neighbor-pos)))))

(defn adjacent-empty-land
  "Returns adjacent land/city positions that are empty (no unit).
   Excludes positions on the pickup continent and land belonging to
   any country-id in exclude-ids set."
  [world get-neighbors-fn pos exclude-ids pickup-continent major-invasion?]
  (filter (fn [neighbor]
            (unloadable-land-cell?
              (get-in world neighbor) neighbor exclude-ids pickup-continent major-invasion?))
          (get-neighbors-fn pos)))

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
                    (core/get-neighbors pos))))))

(defn has-nearby-unloadable-land?
  "BFS along coastal sea cells up to max-depth hops from pos.
   Returns true if any visited position has adjacent empty land
   not excluded by country-id or pickup continent."
  [pos transport max-depth]
  (let [game-map (sa/read-state :computer-map)
        exclude-ids (pickup-exclude-ids game-map transport)
        pickup-continent (pickup-continent-if-needed transport)
        major-invasion? (:major-invasion transport)
        has-unloadable-neighbor? (fn [p]
                                   (some (fn [n]
                                           (unloadable-land-cell?
                                             (get-in game-map n) n exclude-ids pickup-continent major-invasion?))
                                         (core/get-neighbors p)))]
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
                          (core/get-neighbors current))]
              (recur (reduce #(conj %1 [%2 (inc depth)]) (pop queue) coastal-neighbors)
                     (into visited coastal-neighbors)))))))))

(defn- transition-to-loading-inline
  "Inline loading transition — avoids circular dep with facade."
  [pos]
  (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))]
    (if (:never-reload? transport)
      (do
        (tc/set-transport-mission pos :sailing)
        (sa/update-world! update-in (conj pos :contents)
                          dissoc :unload-target-city :pickup-continent-pos))
      (do
        (tc/set-transport-mission pos :loading)
        (sa/update-world! update-in (conj pos :contents) dissoc :unload-target-city)
        (let [current-continent (when-let [lp (tc/find-adjacent-land-pos pos)]
                                  (land-objectives/flood-fill-continent lp))
              next-pickup (targeting/find-next-pickup-continent-pos pos current-continent)]
          (sa/update-world! assoc-in
                            (conj pos :contents :pickup-continent-pos) next-pickup))))))

(defn- unload-army-template
  [transport]
  (let [unload-eid (:unload-event-id transport)
        unload-cid (or (:unload-country-id transport) (:country-id transport))]
    (cond-> {:type :army :owner :computer :mode :move-inland :hits 1}
      unload-eid (assoc :unload-event-id unload-eid)
      unload-cid (assoc :country-id unload-cid))))

(defn- place-unloaded-armies!
  [pos targets army unload-eid]
  (doseq [land-pos targets]
    (debug/log-computer-event! :transport-unload-army pos {:to land-pos :eid unload-eid})
    (sa/update-world! assoc-in (conj land-pos :contents) army)
    (core/stamp-territory land-pos army)
    (computer-movement/update-cell-visibility! land-pos :computer)))

(defn- record-unloaded-country!
  [pos targets]
  (let [unloaded-cid (->> targets
                          (keep #(:country-id (get-in (sa/current-world) %)))
                          first)]
    (when unloaded-cid
      (sa/update-world! update-in (conj pos :contents :unloaded-countries)
                        assoc unloaded-cid (or (sa/read-state :round-number) 0))
      (tc/sync-transport-to-computer-map! pos))))

(defn- finish-unload!
  [pos army-count to-unload]
  (sa/update-world! update-in (conj pos :contents :army-count) - to-unload)
  (tc/sync-transport-to-computer-map! pos)
  (when (<= (- army-count to-unload) 0)
    (transition-to-loading-inline pos)))

(defn- adjacent-unloadable-neighbors
  [pos pickup-continent]
  (let [game-map (sa/read-state :computer-map)]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (#{:land :city} (:type cell))
                     (nil? (:contents cell))
                     (or (nil? pickup-continent)
                         (not (contains? pickup-continent neighbor))))))
            (core/get-neighbors pos))))

(defn- adjacent-empty-land-any
  [game-map pos]
  (filter (fn [neighbor]
            (let [cell (get-in game-map neighbor)]
              (and cell
                   (#{:land :city} (:type cell))
                   (nil? (:contents cell)))))
          (core/get-neighbors pos)))

(defn try-opportunistic-unload
  "If transport has armies and there is adjacent unclaimed land,
   unload all possible armies onto targets. Returns true if any unloaded."
  [pos]
  (let [game-map (sa/read-state :computer-map)
        transport (get-in game-map (conj pos :contents))
        army-count (:army-count transport 0)
        exclude-ids (pickup-exclude-ids game-map transport)
        pickup-continent (pickup-continent-if-needed transport)
        major-invasion? (:major-invasion transport)
        targets (when (pos? army-count)
                  (adjacent-empty-land game-map
                                                 core/get-neighbors
                                                 pos
                                                 exclude-ids
                                                 pickup-continent
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
  "Unload armies onto adjacent land, excluding pickup continent. Returns true if any unloaded."
  [pos pickup-continent]
  (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))
        army-count (:army-count transport 0)]
    (when (pos? army-count)
      (let [land-neighbors (adjacent-unloadable-neighbors pos pickup-continent)
            to-unload (min army-count (count land-neighbors))]
        (when (pos? to-unload)
          (let [selected-targets (take to-unload land-neighbors)
                unload-eid (:unload-event-id transport)
                army (unload-army-template transport)]
            (place-unloaded-armies! pos selected-targets army unload-eid)
            (record-unloaded-country! pos selected-targets)
            (finish-unload! pos army-count to-unload))
          true)))))

(defn unloading-crawl-move
  "Moves unloading transport to adjacent coastal sea cell to find empty land.
   Like coastal-crawl-move but without auto-loading armies."
  [pos]
  (let [game-map (sa/current-world)
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
        (core/move-unit-to pos target)
        (computer-movement/update-cell-visibility! pos :computer)
        (computer-movement/update-cell-visibility! target :computer)
        (let [new-history (vec (take-last 3 (conj (:crawl-history unit []) pos)))]
          (sa/update-world! assoc-in (conj target :contents :crawl-history) new-history))
        (tc/sync-transport-to-computer-map! target)
        target))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:59:11.701992-05:00", :module-hash "1542528357", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 10, :hash "571949949"} {:id "defn/pickup-exclude-ids", :kind "defn", :line 13, :end-line 21, :hash "1846454024"} {:id "defn/pickup-continent-if-needed", :kind "defn", :line 23, :end-line 29, :hash "-1134240335"} {:id "defn/unloadable-land-cell?", :kind "defn", :line 31, :end-line 42, :hash "-1386163529"} {:id "defn/adjacent-empty-land", :kind "defn", :line 44, :end-line 52, :hash "27296169"} {:id "defn-/passable-coastal-sea?", :kind "defn-", :line 54, :end-line 63, :hash "1069173453"} {:id "defn/has-nearby-unloadable-land?", :kind "defn", :line 65, :end-line 92, :hash "1193770655"} {:id "defn-/transition-to-loading-inline", :kind "defn-", :line 94, :end-line 110, :hash "-55081164"} {:id "defn-/unload-army-template", :kind "defn-", :line 112, :end-line 118, :hash "-201166129"} {:id "defn-/place-unloaded-armies!", :kind "defn-", :line 120, :end-line 126, :hash "381899336"} {:id "defn-/record-unloaded-country!", :kind "defn-", :line 128, :end-line 135, :hash "-84411371"} {:id "defn-/finish-unload!", :kind "defn-", :line 137, :end-line 141, :hash "270973256"} {:id "defn-/adjacent-unloadable-neighbors", :kind "defn-", :line 143, :end-line 153, :hash "-947538451"} {:id "defn-/adjacent-empty-land-any", :kind "defn-", :line 155, :end-line 162, :hash "-456014395"} {:id "defn/try-opportunistic-unload", :kind "defn", :line 164, :end-line 189, :hash "-1410515644"} {:id "defn/try-opportunistic-unload-any-land", :kind "defn", :line 191, :end-line 207, :hash "817909817"} {:id "defn/unload-armies", :kind "defn", :line 209, :end-line 224, :hash "818516088"} {:id "defn/unloading-crawl-move", :kind "defn", :line 226, :end-line 247, :hash "-1673599752"}]}
;; clj-mutate-manifest-end
