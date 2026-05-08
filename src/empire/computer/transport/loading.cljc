(ns empire.computer.transport.loading
  "Transport loading — army loading, coastal crawling, staleness detection."
  (:require [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.transport.core :as tc]
            [empire.computer.transport.reservations :as reservations]
            [empire.computer.shared.world-query :as world-query]
            [empire.game-mechanics.debug.logging :as debug]))

(defn- update-cell-visibility!
  [pos owner]
  (visibility/update-cell-visibility pos owner))

(def ^:private max-loading-rounds 10)

(defn- invasion-loading?
  [transport]
  (= :load-for-invasion (:transport-mission transport)))

(defn- loadable-army-at?
  "Returns true if neighbor n has a loadable computer army."
  [n game-map unload-eid]
  (let [cell (get-in game-map n)
        unit (:contents cell)
        invasion-pickup? (= :move-to-coast-for-invasion (:mode unit))]
    (and unit
         (= :army (:type unit))
         (= :computer (:owner unit))
         (or invasion-pickup?
             (not (and unload-eid
                       (= (:unload-event-id unit) unload-eid)))))))

(defn- passable-coastal-sea-neighbor?
  [n game-map visited]
  (and (not (visited n))
       (let [cell (get-in game-map n)]
         (and cell
              (= :sea (:type cell))
              (or (nil? (:contents cell))
                  (= :computer (:owner (:contents cell))))
              (tc/adjacent-to-land? n)))))

(defn- coastal-sea-neighbors
  [current game-map visited]
  (filter #(passable-coastal-sea-neighbor? % game-map visited)
          (world-query/get-neighbors current)))

(defn has-nearby-loadable-armies?
  "BFS along coastal sea cells up to max-depth hops from pos.
   Returns true if any adjacent land cell at any visited position
  has a loadable computer army."
  [pos transport max-depth]
  (let [game-map (sa/read-state :computer-map)
        unload-eid (:unload-event-id transport)
        check-neighbors (fn [p]
                          (some #(loadable-army-at? % game-map unload-eid)
                                (world-query/get-neighbors p)))]
    (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [pos 0])
           visited #{pos}]
      (if (empty? queue)
        false
        (let [[current depth] (peek queue)]
          (cond
            (check-neighbors current) true
            (>= depth max-depth) (recur (pop queue) visited)
            :else
            (let [coastal-neighbors (coastal-sea-neighbors current game-map visited)]
              (recur (reduce #(conj %1 [%2 (inc depth)]) (pop queue) coastal-neighbors)
                     (into visited coastal-neighbors)))))))))

(defn- adjacent-loadable-armies
  [computer-map pos manifest-ids]
  (keep (fn [n]
          (let [unit (:contents (get-in computer-map n))]
            (when (and unit
                       (= :army (:type unit))
                       (= :computer (:owner unit)))
              {:pos n
               :unit unit
               :manifest-match? (and manifest-ids
                                     (contains? manifest-ids (:computer-unit-id unit)))})))
        (world-query/get-neighbors pos)))

(defn- prioritize-loadable-armies
  [transport armies]
  (if (invasion-loading? transport)
    (concat (filter :manifest-match? armies)
            (remove :manifest-match? armies))
    (concat (remove :manifest-match? armies)
            (filter :manifest-match? armies))))

(defn- log-load-army!
  [pos transport army-count army-pos]
  (debug/log-computer-event! :transport-load-army
                             pos
                             {:from army-pos
                              :transport-id (:transport-id transport)
                              :transport-mission (:transport-mission transport)
                              :army-count-before army-count
                              :load-target-cell (:load-target-cell transport)
                              :major-invasion (:major-invasion transport)}))

(defn- remove-loaded-armies!
  [pos transport army-count loaded-positions]
  (doseq [army-pos loaded-positions]
    (log-load-army! pos transport army-count army-pos)
    (sa/update-world! update-in army-pos dissoc :contents)
    (update-cell-visibility! army-pos :computer)))

(defn- apply-updated-load-manifest!
  [pos transport army-count to-load loaded-armies]
  (let [loaded-manifest-ids (->> loaded-armies
                                 (filter :manifest-match?)
                                 (keep (comp :computer-unit-id :unit))
                                 set)
        updated-manifest (vec (remove loaded-manifest-ids (:load-manifest transport)))
        final-army-count (+ army-count to-load)]
    (if (>= final-army-count 6)
      (do
        (sa/update-world! assoc-in (conj pos :contents :load-manifest) nil)
        (reservations/release! (:transport-id transport)))
      (do
        (sa/update-world! assoc-in (conj pos :contents :load-manifest) updated-manifest)
        (reservations/update-army-ids!
         (:transport-id transport)
         updated-manifest)))))

(defn load-adjacent-armies
  "Loads computer armies from adjacent land cells. Returns number loaded.
   Skips armies unloaded in the same event to avoid immediate bounce-back."
  [pos]
  (let [computer-map (sa/read-state :computer-map)
        transport (get-in computer-map (conj pos :contents))
        army-count (:army-count transport 0)
        capacity (- 6 army-count)
        manifest-ids (when (contains? transport :load-manifest)
                       (set (:load-manifest transport)))
        armies (adjacent-loadable-armies computer-map pos manifest-ids)
        prioritized-armies (prioritize-loadable-armies transport armies)
        loaded-armies (vec (take capacity prioritized-armies))
        loaded-positions (mapv :pos loaded-armies)
        to-load (count loaded-positions)]
    (remove-loaded-armies! pos transport army-count loaded-positions)
    (when (pos? to-load)
      (sa/update-world! update-in (conj pos :contents :army-count) (fnil + 0) to-load)
      (when manifest-ids
        (apply-updated-load-manifest! pos transport army-count to-load loaded-armies))
      (visibility/sync-ai-unit-to-computer-map! pos))
    ;; Wake nearby sentries to advance the transport queue
    (doseq [army-pos loaded-positions]
      (action-resolution/wake-nearby-sentries army-pos 3))
    to-load))

(defn coastal-crawl-move
  "Moves transport to adjacent sea cell that is also adjacent to land.
   Avoids recent positions from crawl-history."
  [pos]
  (let [computer-map (sa/read-state :computer-map)
        unit (get-in computer-map (conj pos :contents))
        crawl-history (:crawl-history unit [])
        history (set crawl-history)
        passable (tc/get-passable-sea-neighbors pos)
        empty-passable (filter (fn [n]
                                 (nil? (:contents (get-in computer-map n))))
                               passable)
        coastal-cells (filter tc/adjacent-to-land? empty-passable)
        preferred (remove history coastal-cells)
        cleared-history? (and (empty? preferred)
                              (seq coastal-cells)
                              (seq crawl-history))
        targets (if (seq preferred) preferred coastal-cells)
        history-base (if cleared-history? [] crawl-history)]
    (when (seq targets)
      (when cleared-history?
        (sa/update-world! assoc-in (conj pos :contents :crawl-history) [])
        (visibility/sync-ai-unit-to-computer-map! pos))
      (let [target (rand-nth targets)]
        (action-resolution/move-unit-to pos target)
        (update-cell-visibility! pos :computer)
        (update-cell-visibility! target :computer)
        (let [new-history (vec (take-last 3 (conj history-base pos)))]
          (sa/update-world! assoc-in (conj target :contents :crawl-history) new-history))
        ;; Keep the visible transport state aligned with the authoritative unit so a second crawl
        ;; step in the same round can avoid immediately backtracking.
        (sa/update-state! :computer-map assoc-in (conj target :contents :crawl-history)
                          (vec (take-last 3 (conj history-base pos))))
        ;; Auto-load armies from adjacent land at new position
        (load-adjacent-armies target)
        target))))

(defn should-start-sailing?
  [pos transport army-count]
  (and (>= army-count 4)
       (or (>= army-count 6)
           (not (has-nearby-loadable-armies? pos transport 3)))))

(defn planned-loading?
  [transport]
  (vector? (:load-manifest transport)))

(defn manifest-empty?
  [transport]
  (and (vector? (:load-manifest transport))
       (empty? (:load-manifest transport))))

(defn loading-stale?
  [transport]
  (when-let [started (:loading-since-round transport)]
    (> (- (or (sa/read-state :round-number) 0) started) max-loading-rounds)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-07T18:45:15.490028-05:00", :module-hash "1687092041", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 9, :hash "830210575"} {:id "defn-/update-cell-visibility!", :kind "defn-", :line 11, :end-line 13, :hash "-1102586575"} {:id "def/max-loading-rounds", :kind "def", :line 15, :end-line 15, :hash "-1272023921"} {:id "defn-/invasion-loading?", :kind "defn-", :line 17, :end-line 19, :hash "791733525"} {:id "defn-/loadable-army-at?", :kind "defn-", :line 21, :end-line 32, :hash "1468019729"} {:id "defn-/passable-coastal-sea-neighbor?", :kind "defn-", :line 34, :end-line 42, :hash "1453757411"} {:id "defn-/coastal-sea-neighbors", :kind "defn-", :line 44, :end-line 47, :hash "621004733"} {:id "defn/has-nearby-loadable-armies?", :kind "defn", :line 49, :end-line 70, :hash "-728268526"} {:id "defn-/adjacent-loadable-armies", :kind "defn-", :line 72, :end-line 83, :hash "-2046544844"} {:id "defn-/prioritize-loadable-armies", :kind "defn-", :line 85, :end-line 91, :hash "-2138293232"} {:id "defn-/log-load-army!", :kind "defn-", :line 93, :end-line 102, :hash "-1542683057"} {:id "defn-/remove-loaded-armies!", :kind "defn-", :line 104, :end-line 109, :hash "-1362883897"} {:id "defn-/apply-updated-load-manifest!", :kind "defn-", :line 111, :end-line 127, :hash "-1627745092"} {:id "defn/load-adjacent-armies", :kind "defn", :line 129, :end-line 153, :hash "259249126"} {:id "defn/coastal-crawl-move", :kind "defn", :line 155, :end-line 190, :hash "1225651740"} {:id "defn/should-start-sailing?", :kind "defn", :line 192, :end-line 196, :hash "1199262319"} {:id "defn/planned-loading?", :kind "defn", :line 198, :end-line 200, :hash "-470198343"} {:id "defn/manifest-empty?", :kind "defn", :line 202, :end-line 205, :hash "-294812737"} {:id "defn/loading-stale?", :kind "defn", :line 207, :end-line 210, :hash "-1970271111"}]}
;; clj-mutate-manifest-end
