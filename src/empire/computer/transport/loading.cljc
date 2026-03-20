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

(defn- loadable-army-at?
  "Returns true if neighbor n has a loadable computer army."
  [n game-map unloaded-countries unload-eid]
  (let [cell (get-in game-map n)
        unit (:contents cell)
        invasion-pickup? (= :move-to-coast-for-invasion (:mode unit))]
    (and unit
         (= :army (:type unit))
         (= :computer (:owner unit))
         (or invasion-pickup?
             (not (and (seq unloaded-countries)
                       (:country-id unit)
                       (tc/recently-unloaded-country?
                         unloaded-countries (:country-id unit)))))
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
        unloaded-countries (:unloaded-countries transport)
        unload-eid (:unload-event-id transport)
        check-neighbors (fn [p]
                          (some #(loadable-army-at? % game-map unloaded-countries unload-eid)
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

(defn load-adjacent-armies
  "Loads computer armies from adjacent land cells. Returns number loaded.
   Skips armies from recently unloaded countries."
  [pos]
  (let [computer-map (sa/read-state :computer-map)
        transport (get-in computer-map (conj pos :contents))
        army-count (:army-count transport 0)
        capacity (- 6 army-count)
        unloaded-countries (:unloaded-countries transport)
        manifest-ids (when (contains? transport :load-manifest)
                       (set (:load-manifest transport)))
        neighbors (world-query/get-neighbors pos)
        armies (filter (fn [n]
                         (let [cell (get-in computer-map n)
                               unit (:contents cell)
                               invasion-pickup? (= :move-to-coast-for-invasion (:mode unit))]
                           (and unit
                                (= :army (:type unit))
                                (= :computer (:owner unit))
                                (or (nil? manifest-ids)
                                    (contains? manifest-ids (:computer-unit-id unit)))
                                (or invasion-pickup?
                                    (not (and (seq unloaded-countries)
                                              (:country-id unit)
                                              (tc/recently-unloaded-country?
                                                unloaded-countries (:country-id unit))))))))
                       neighbors)
        to-load (min capacity (count armies))]
    (let [loaded-positions (vec (take to-load armies))]
      (doseq [army-pos loaded-positions]
        (debug/log-computer-event! :transport-load-army pos {:from army-pos})
        (sa/update-world! update-in army-pos dissoc :contents)
        (update-cell-visibility! army-pos :computer))
        (when (pos? to-load)
          (sa/update-world! update-in (conj pos :contents :army-count) (fnil + 0) to-load)
          (when manifest-ids
            (let [loaded-ids (->> loaded-positions
                                  (keep #(get-in computer-map (conj % :contents :computer-unit-id)))
                                  set)
                  updated-manifest (vec (remove loaded-ids (:load-manifest transport)))]
              (sa/update-world! update-in (conj pos :contents :load-manifest)
                              (constantly updated-manifest))
              (reservations/update-army-ids!
               (:transport-id transport)
               updated-manifest)))
        (visibility/sync-ai-unit-to-computer-map! pos))
      ;; Wake nearby sentries to advance the transport queue
      (doseq [army-pos loaded-positions]
        (action-resolution/wake-nearby-sentries army-pos 3))
      to-load)))

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
  (contains? transport :load-manifest))

(defn manifest-empty?
  [transport]
  (empty? (:load-manifest transport)))

(defn loading-stale?
  [transport]
  (when-let [started (:loading-since-round transport)]
    (> (- (or (sa/read-state :round-number) 0) started) max-loading-rounds)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:58:57.394349-05:00", :module-hash "859187559", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "788791339"} {:id "defn-/update-cell-visibility!", :kind "defn-", :line 10, :end-line 12, :hash "-1102586575"} {:id "defn-/loadable-army-at?", :kind "defn-", :line 14, :end-line 30, :hash "-584039442"} {:id "defn-/passable-coastal-sea-neighbor?", :kind "defn-", :line 32, :end-line 40, :hash "1453757411"} {:id "defn-/coastal-sea-neighbors", :kind "defn-", :line 42, :end-line 45, :hash "279146420"} {:id "defn/has-nearby-loadable-armies?", :kind "defn", :line 47, :end-line 69, :hash "-37818847"} {:id "defn/load-adjacent-armies", :kind "defn", :line 71, :end-line 105, :hash "-176084131"} {:id "defn/coastal-crawl-move", :kind "defn", :line 107, :end-line 130, :hash "1512796160"} {:id "defn/should-start-sailing?", :kind "defn", :line 132, :end-line 136, :hash "1199262319"} {:id "defn/clear-pickup-continent-if-arrived", :kind "defn", :line 138, :end-line 146, :hash "1344478972"} {:id "def/max-loading-rounds", :kind "def", :line 148, :end-line 148, :hash "-1272023921"} {:id "defn/loading-stale?", :kind "defn", :line 150, :end-line 153, :hash "545146802"}]}
;; clj-mutate-manifest-end
