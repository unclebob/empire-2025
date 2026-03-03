;; mutation-tested: 2026-03-02
(ns empire.computer.transport-loading
  "Transport loading — army loading, coastal crawling, staleness detection."
  (:require [empire.adapters.state.runtime :as runtime-state]
            [empire.application.ports :as ports]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.core :as core]
            [empire.computer.transport-core :as tc]
            [empire.computer.transport-targeting :as targeting]
            [empire.debug :as debug]
            [empire.movement.visibility :as visibility]))

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
  (let [store (runtime-state/runtime-state-store)]
    (ports/read-runtime-state store k)))

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
          (core/get-neighbors current)))

(defn has-nearby-loadable-armies?
  "BFS along coastal sea cells up to max-depth hops from pos.
   Returns true if any adjacent land cell at any visited position
  has a loadable computer army."
  [pos transport max-depth]
  (let [game-map (current-world)
        unloaded-countries (:unloaded-countries transport)
        unload-eid (:unload-event-id transport)
        check-neighbors (fn [p]
                          (some #(loadable-army-at? % game-map unloaded-countries unload-eid)
                                (core/get-neighbors p)))]
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
  (let [game-map (current-world)
        transport (get-in game-map (conj pos :contents))
        army-count (:army-count transport 0)
        capacity (- 6 army-count)
        unloaded-countries (:unloaded-countries transport)
        neighbors (core/get-neighbors pos)
        armies (filter (fn [n]
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
                                                unloaded-countries (:country-id unit))))))))
                       neighbors)
        to-load (min capacity (count armies))]
    (let [loaded-positions (vec (take to-load armies))]
      (doseq [army-pos loaded-positions]
        (debug/log-computer-event! :transport-load-army pos {:from army-pos})
        (update-game-map! update-in army-pos dissoc :contents)
        (visibility/update-cell-visibility army-pos :computer))
      (when (pos? to-load)
        (update-game-map! update-in (conj pos :contents :army-count) (fnil + 0) to-load))
      ;; Wake nearby sentries to advance the transport queue
      (doseq [army-pos loaded-positions]
        (core/wake-nearby-sentries army-pos 3))
      to-load)))

(defn coastal-crawl-move
  "Moves transport to adjacent sea cell that is also adjacent to land.
   Avoids recent positions from crawl-history."
  [pos]
  (let [game-map (current-world)
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
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility target :computer)
        (let [new-history (vec (take-last 3 (conj (:crawl-history unit []) pos)))]
          (update-game-map! assoc-in (conj target :contents :crawl-history) new-history))
        ;; Auto-load armies from adjacent land at new position
        (load-adjacent-armies target)
        target))))

(defn should-start-sailing?
  [pos transport army-count]
  (and (>= army-count 4)
       (or (>= army-count 6)
           (not (has-nearby-loadable-armies? pos transport 3)))))

(defn clear-pickup-continent-if-arrived
  [pos]
  (let [transport (get-in (current-world) (conj pos :contents))
        pcp (:pickup-continent-pos transport)]
    (when (and pcp
               (tc/adjacent-to-land? pos)
               (targeting/adjacent-to-pickup-continent? pos pcp))
      (update-game-map! update-in (conj pos :contents)
             dissoc :pickup-continent-pos))))

(def ^:private max-loading-rounds 10)

(defn loading-stale?
  [transport]
  (let [since (:loading-since transport)]
    (and since (> (- (or (read-runtime-state :round-number) 0) since) max-loading-rounds))))
