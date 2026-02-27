;; mutation-tested: 2026-02-24
(ns empire.computer.transport
  "Computer transport module — simplified 3-state mission flow.
   Loading: coastal crawl, auto-load adjacent armies, sail when loaded
   Sailing: follow BFS path to unexplored coast, opportunistic unload
   Unloading: coast-crawl while dropping armies on empty land"
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.computer.land-objectives :as land-objectives]
            [empire.debug :as debug]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.movement.visibility :as visibility]
            [empire.movement.map-utils :as map-utils]))

(declare load-adjacent-armies)

(defn- get-passable-sea-neighbors
  "Returns passable sea neighbors for a transport."
  [pos]
  (let [game-map @atoms/game-map]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (= :sea (:type cell))
                     (or (nil? (:contents cell))
                         (= :computer (:owner (:contents cell)))))))
            (core/get-neighbors pos))))

(defn- recently-unloaded-country?
  "Returns true if the country-id was unloaded to within the last 10 rounds."
  [unloaded-countries country-id]
  (when-let [unload-round (get unloaded-countries country-id)]
    (< (- @atoms/round-number unload-round) 10)))

(defn- adjacent-to-land?
  "Returns true if position has adjacent land cell."
  [pos]
  (map-utils/adjacent-to-land? pos atoms/game-map))

(defn- find-adjacent-land-pos
  "Returns the first adjacent land or city position, or nil."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [n]
                     (let [cell (get-in game-map n)]
                       (and cell (#{:land :city} (:type cell)))))
                   (core/get-neighbors pos)))))

(defn- adjacent-to-pickup-continent?
  "Returns true if any adjacent land cell shares a country-id with the cell
   at pickup-continent-pos. Cheap O(neighbors) alternative to flood-fill."
  [pos pickup-continent-pos]
  (let [game-map @atoms/game-map
        pcp-country-id (:country-id (get-in game-map pickup-continent-pos))]
    (if pcp-country-id
      (some (fn [n]
              (let [cell (get-in game-map n)]
                (and cell
                     (#{:land :city} (:type cell))
                     (= pcp-country-id (:country-id cell)))))
            (core/get-neighbors pos))
      ;; No country-id at pcp — fall back to distance check
      (<= (core/distance pos pickup-continent-pos) 2))))

(defn- score-target-city
  "Score a target city for a transport. Lower = more attractive.
   Factors: distance, continent attackable cities, computer presence."
  [transport-pos target-city]
  (let [dist (core/distance transport-pos target-city)
        target-continent (land-objectives/flood-fill-continent target-city)
        scan (when target-continent (land-objectives/scan-continent target-continent))
        attackable (+ (:player-cities scan 0) (:free-cities scan 0))
        continent-factor (if (pos? attackable)
                           (/ 100.0 attackable)
                           100.0)
        presence-penalty (if (pos? (:computer-cities scan 0)) 10.0 1.0)]
    (* dist continent-factor presence-penalty)))

(defn find-unload-target
  "Find best enemy city to unload near, excluding pickup continent.
   Prioritizes player cities over free cities.
   Prefers unclaimed targets to spread transports."
  [pickup-continent transport-pos]
  (let [player-cities (core/find-visible-cities #{:player})
        free-cities (core/find-visible-cities #{:free})
        ;; Filter both to off-continent
        player-off (if pickup-continent
                     (remove #(contains? pickup-continent %) player-cities)
                     player-cities)
        free-off (if pickup-continent
                   (remove #(contains? pickup-continent %) free-cities)
                   free-cities)
        ;; Priority: player cities first
        priority-targets (if (seq player-off) player-off free-off)]
    (when (seq priority-targets)
      (let [claimed @atoms/claimed-transport-targets
            unclaimed (remove claimed priority-targets)
            candidates (if (seq unclaimed) unclaimed priority-targets)
            best (apply min-key
                        #(score-target-city transport-pos %)
                        candidates)]
        (when best
          (swap! atoms/claimed-transport-targets conj best)
          best)))))

(defn- move-toward-position
  "Move transport one step toward target using greedy neighbor selection."
  [pos target]
  (let [passable (get-passable-sea-neighbors pos)
        closest (core/move-toward pos target passable)]
    (when (and closest (core/move-unit-to pos closest))
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility closest :computer)
      (load-adjacent-armies closest)
      closest)))

(defn- find-next-pickup-continent-pos
  "After unloading, find the nearest continent with enough computer armies,
   excluding the current unload continent. Returns an army position on
   that continent, or nil if none qualifies.
   min-armies defaults to 3 (require >3 armies)."
  ([transport-pos current-continent]
   (find-next-pickup-continent-pos transport-pos current-continent 3))
  ([transport-pos current-continent min-armies]
   (let [game-map @atoms/game-map
         all-armies (for [i (range (count game-map))
                          j (range (count (first game-map)))
                          :let [cell (get-in game-map [i j])
                                unit (:contents cell)]
                          :when (and unit
                                     (= :computer (:owner unit))
                                     (= :army (:type unit))
                                     (or (nil? current-continent)
                                         (not (contains? current-continent [i j]))))]
                      [i j])]
     ;; Group armies by continent, avoiding redundant flood-fills
     (loop [remaining all-armies
            seen #{}
            continents []]
       (if (empty? remaining)
         (let [qualifying (filter #(> (count (:armies %)) min-armies) continents)]
           (when (seq qualifying)
             (let [best (apply min-key
                               (fn [{:keys [armies]}]
                                 (apply min (map #(core/distance transport-pos %) armies)))
                               qualifying)]
               (apply min-key #(core/distance transport-pos %) (:armies best)))))
         (let [army-pos (first remaining)]
           (if (contains? seen army-pos)
             (recur (rest remaining) seen continents)
             (let [cont (land-objectives/flood-fill-continent army-pos)
                   cont-armies (filter #(contains? cont %) all-armies)]
               (recur (rest remaining)
                      (into seen cont)
                      (conj continents {:continent cont :armies cont-armies}))))))))))

(defn- build-unloaded-army
  "Builds an army map for unloading from transport."
  [transport]
  (let [unload-eid (:unload-event-id transport)
        unload-cid (or (:unload-country-id transport) (:country-id transport))]
    (cond-> {:type :army :owner :computer :mode :move-inland :hits 1}
      unload-eid (assoc :unload-event-id unload-eid)
      unload-cid (assoc :country-id unload-cid))))

(defn- unloadable-neighbors
  "Returns adjacent land/city cells that are empty and not on pickup continent."
  [pos pickup-continent]
  (filter (fn [neighbor]
            (let [cell (get-in @atoms/game-map neighbor)]
              (and cell
                   (#{:land :city} (:type cell))
                   (nil? (:contents cell))
                   (or (nil? pickup-continent)
                       (not (contains? pickup-continent neighbor))))))
          (core/get-neighbors pos)))

(declare transition-to-loading)

(defn unload-armies
  "Unload armies onto adjacent land, excluding pickup continent. Returns true if any unloaded."
  [pos pickup-continent]
  (let [transport (get-in @atoms/game-map (conj pos :contents))
        army-count (:army-count transport 0)]
    (when (pos? army-count)
      (let [land-neighbors (unloadable-neighbors pos pickup-continent)
            to-unload (min army-count (count land-neighbors))]
        (when (pos? to-unload)
          (let [army (build-unloaded-army transport)]
            (doseq [land-pos (take to-unload land-neighbors)]
              (debug/log-computer-event! :transport-unload-army pos {:to land-pos :eid (:unload-event-id transport)})
              (swap! atoms/game-map assoc-in (conj land-pos :contents) army)
              (core/stamp-territory land-pos army)
              (visibility/update-cell-visibility land-pos :computer)))
          (let [unloaded-country-id (->> (take to-unload land-neighbors)
                                         (keep #(:country-id (get-in @atoms/game-map %)))
                                         first)]
            (when unloaded-country-id
              (swap! atoms/game-map update-in (conj pos :contents :unloaded-countries)
                     assoc unloaded-country-id @atoms/round-number)))
          (swap! atoms/game-map update-in (conj pos :contents :army-count) - to-unload)
          (when (<= (- army-count to-unload) 0)
            (transition-to-loading pos))
          true)))))

(defn- adjacent-empty-land
  "Returns adjacent land/city positions that are empty (no unit).
   Excludes positions on the pickup continent and land belonging to
   any country-id in exclude-ids set."
  [pos exclude-ids pickup-continent]
  (let [game-map @atoms/game-map]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (#{:land :city} (:type cell))
                     (nil? (:contents cell))
                     (or (empty? exclude-ids)
                         (not (contains? exclude-ids (:country-id cell))))
                     (or (nil? pickup-continent)
                         (not (contains? pickup-continent neighbor))))))
            (core/get-neighbors pos))))

(defn- pickup-exclude-ids
  "Returns set of country-ids to exclude: transport's own country-id,
   pickup-country-id, and the country-id at pickup-continent-pos."
  [transport]
  (disj (set [(:country-id transport)
              (:pickup-country-id transport)
              (when-let [pcp (:pickup-continent-pos transport)]
                (:country-id (get-in @atoms/game-map pcp)))])
        nil))

(defn- pickup-continent-if-needed
  "Returns the pickup continent set only when country-id exclusion is
   insufficient (no country-id at pickup pos). Uses cached flood-fill."
  [transport]
  (when-let [pcp (:pickup-continent-pos transport)]
    (when-not (:country-id (get-in @atoms/game-map pcp))
      (land-objectives/flood-fill-continent pcp))))

(defn- try-opportunistic-unload
  "If transport has armies and there is adjacent unclaimed land,
   unload all possible armies onto targets. Returns true if any unloaded."
  [pos]
  (let [transport (get-in @atoms/game-map (conj pos :contents))
        army-count (:army-count transport 0)
        exclude-ids (pickup-exclude-ids transport)
        pickup-continent (pickup-continent-if-needed transport)
        targets (when (pos? army-count)
                  (adjacent-empty-land pos exclude-ids pickup-continent))
        to-unload (min army-count (count targets))]
    (when (pos? to-unload)
      (let [unload-eid (:unload-event-id transport)
            unload-cid (or (:unload-country-id transport) (:country-id transport))
            army (cond-> {:type :army :owner :computer :mode :move-inland :hits 1}
                   unload-eid (assoc :unload-event-id unload-eid)
                   unload-cid (assoc :country-id unload-cid))]
        (doseq [land-pos (take to-unload targets)]
          (debug/log-computer-event! :transport-unload-army pos {:to land-pos :eid unload-eid})
          (swap! atoms/game-map assoc-in (conj land-pos :contents) army)
          (core/stamp-territory land-pos army)
          (visibility/update-cell-visibility land-pos :computer))
        ;; Record unloaded country-id from land cells
        (let [unloaded-cid (->> (take to-unload targets)
                                (keep #(:country-id (get-in @atoms/game-map %)))
                                first)]
          (when unloaded-cid
            (swap! atoms/game-map update-in (conj pos :contents :unloaded-countries)
                   assoc unloaded-cid @atoms/round-number)))
        ;; Update army count
        (swap! atoms/game-map update-in (conj pos :contents :army-count) - to-unload)
        ;; If fully unloaded, transition to loading
        (when (<= (- army-count to-unload) 0)
          (swap! atoms/game-map assoc-in (conj pos :contents :transport-mission) :loading)
          (swap! atoms/game-map assoc-in (conj pos :contents :loading-since) @atoms/round-number)
          (swap! atoms/game-map update-in (conj pos :contents) dissoc :unload-target-city)
          (let [current-continent (when-let [lp (find-adjacent-land-pos pos)]
                                    (land-objectives/flood-fill-continent lp))
                next-pickup (find-next-pickup-continent-pos pos current-continent)]
            (swap! atoms/game-map assoc-in
                   (conj pos :contents :pickup-continent-pos) next-pickup)))
        true))))

(defn- load-adjacent-armies
  "Loads computer armies from adjacent land cells. Returns number loaded.
   Skips armies from recently unloaded countries."
  [pos]
  (let [game-map @atoms/game-map
        transport (get-in game-map (conj pos :contents))
        army-count (:army-count transport 0)
        capacity (- 6 army-count)
        unloaded-countries (:unloaded-countries transport)
        neighbors (core/get-neighbors pos)
        armies (filter (fn [n]
                         (let [cell (get-in game-map n)
                               unit (:contents cell)]
                           (and unit
                                (= :army (:type unit))
                                (= :computer (:owner unit))
                                (not (and (seq unloaded-countries)
                                          (:country-id unit)
                                          (recently-unloaded-country?
                                            unloaded-countries (:country-id unit)))))))
                       neighbors)
        to-load (min capacity (count armies))]
    (let [loaded-positions (vec (take to-load armies))]
      (doseq [army-pos loaded-positions]
        (debug/log-computer-event! :transport-load-army pos {:from army-pos})
        (swap! atoms/game-map update-in army-pos dissoc :contents)
        (visibility/update-cell-visibility army-pos :computer))
      (when (pos? to-load)
        (swap! atoms/game-map update-in (conj pos :contents :army-count) (fnil + 0) to-load))
      ;; Wake nearby sentries to advance the transport queue
      (doseq [army-pos loaded-positions]
        (core/wake-nearby-sentries army-pos 3))
      to-load)))

(defn- coastal-crawl-move
  "Moves transport to adjacent sea cell that is also adjacent to land.
   Avoids recent positions from crawl-history."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        history (set (:crawl-history unit []))
        passable (get-passable-sea-neighbors pos)
        empty-passable (filter (fn [n]
                                 (nil? (:contents (get-in @atoms/game-map n))))
                               passable)
        coastal-cells (filter adjacent-to-land? empty-passable)
        preferred (remove history coastal-cells)
        targets (if (seq preferred) preferred coastal-cells)]
    (when (seq targets)
      (let [target (rand-nth targets)]
        (core/move-unit-to pos target)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility target :computer)
        (let [new-history (vec (take-last 3 (conj (:crawl-history unit []) pos)))]
          (swap! atoms/game-map assoc-in (conj target :contents :crawl-history) new-history))
        ;; Auto-load armies from adjacent land at new position
        (load-adjacent-armies target)
        target))))

(defn- set-transport-mission
  "Set the transport's mission state."
  [pos mission]
  (swap! atoms/game-map assoc-in (conj pos :contents :transport-mission) mission)
  (when (= mission :loading)
    (swap! atoms/game-map assoc-in (conj pos :contents :loading-since) @atoms/round-number)))

(defn- mint-unload-event-id
  "Mint a new unload-event-id each time transport transitions to unloading.
   Always mints a fresh ID so armies from previous unload cycles can be loaded."
  [pos _transport]
  (let [id @atoms/next-unload-event-id]
    (swap! atoms/next-unload-event-id inc)
    (swap! atoms/game-map assoc-in
           (conj pos :contents :unload-event-id) id)))

(defn- mint-unload-country-id
  "Mint a new country-id for armies unloaded in this sailing cycle."
  [pos]
  (let [cid @atoms/next-country-id]
    (swap! atoms/next-country-id inc)
    (swap! atoms/game-map assoc-in
           (conj pos :contents :unload-country-id) cid)))

(defn- record-pickup-continent-pos
  "When transport becomes full, record the nearest adjacent land position
   as the pickup continent reference point and its country-id."
  [pos transport]
  (when-not (:pickup-continent-pos transport)
    (when-let [land-pos (find-adjacent-land-pos pos)]
      (swap! atoms/game-map assoc-in
             (conj pos :contents :pickup-continent-pos) land-pos)
      (when-let [cid (:country-id (get-in @atoms/game-map land-pos))]
        (swap! atoms/game-map assoc-in
               (conj pos :contents :pickup-country-id) cid)))))

(defn- loadable-army-at?
  "Returns true if neighbor n has a loadable computer army."
  [n game-map unloaded-countries unload-eid]
  (let [cell (get-in game-map n)
        unit (:contents cell)]
    (and unit
         (= :army (:type unit))
         (= :computer (:owner unit))
         (not (and (seq unloaded-countries)
                   (:country-id unit)
                   (recently-unloaded-country?
                     unloaded-countries (:country-id unit))))
         (not (and unload-eid
                   (= (:unload-event-id unit) unload-eid))))))

(defn- passable-coastal-sea?
  "Returns true if pos is an unvisited coastal sea cell passable by a computer transport."
  [pos visited game-map]
  (and (not (visited pos))
       (let [cell (get-in game-map pos)]
         (and cell
              (= :sea (:type cell))
              (or (nil? (:contents cell))
                  (= :computer (:owner (:contents cell))))
              (adjacent-to-land? pos)))))

(defn- has-nearby-loadable-armies?
  "BFS along coastal sea cells up to max-depth hops from pos.
   Returns true if any adjacent land cell at any visited position
   has a loadable computer army."
  [pos transport max-depth]
  (let [game-map @atoms/game-map
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
            (let [coastal-neighbors
                  (filter #(passable-coastal-sea? % visited game-map)
                          (core/get-neighbors current))]
              (recur (reduce #(conj %1 [%2 (inc depth)]) (pop queue) coastal-neighbors)
                     (into visited coastal-neighbors)))))))))

(defn- unloadable-land-cell?
  "Returns true if cell is empty land/city not excluded by country-id or pickup continent."
  [cell neighbor-pos exclude-ids pickup-continent]
  (and cell
       (#{:land :city} (:type cell))
       (nil? (:contents cell))
       (or (empty? exclude-ids)
           (not (contains? exclude-ids (:country-id cell))))
       (or (nil? pickup-continent)
           (not (contains? pickup-continent neighbor-pos)))))

(defn- has-nearby-unloadable-land?
  "BFS along coastal sea cells up to max-depth hops from pos.
   Returns true if any visited position has adjacent empty land
   not excluded by country-id or pickup continent."
  [pos transport max-depth]
  (let [game-map @atoms/game-map
        exclude-ids (pickup-exclude-ids transport)
        pickup-continent (pickup-continent-if-needed transport)
        has-unloadable-neighbor? (fn [p]
                                   (some (fn [n]
                                           (unloadable-land-cell?
                                             (get-in game-map n) n exclude-ids pickup-continent))
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

(defn- passable-sea?
  "Returns true if pos is a passable sea cell for a transport."
  [pos]
  (let [cell (get-in @atoms/game-map pos)]
    (and cell
         (= :sea (:type cell))
         (or (nil? (:contents cell))
             (= :computer (:owner (:contents cell)))))))

(defn- continue-pos
  "Returns pos + direction vector, or nil if out of bounds or not passable sea."
  [from to]
  (let [dr (- (first to) (first from))
        dc (- (second to) (second from))
        candidate [(+ (first to) dr) (+ (second to) dc)]]
    (when (passable-sea? candidate) candidate)))

(defn- compute-sail-path
  "Compute BFS path from transport position to best coastal target.
   Looks 4 levels past first hit; prefers unowned coast over unexplored."
  [pos]
  (pathfinding-bfs/bfs-to-coast-target
    pos @atoms/computer-map))

(defn- transition-to-loading
  "Switch an empty transport to loading mode and find next pickup continent."
  [pos]
  (set-transport-mission pos :loading)
  (swap! atoms/game-map update-in (conj pos :contents) dissoc :unload-target-city)
  (let [current-continent (when-let [lp (find-adjacent-land-pos pos)]
                            (land-objectives/flood-fill-continent lp))
        next-pickup (find-next-pickup-continent-pos pos current-continent)]
    (swap! atoms/game-map assoc-in
           (conj pos :contents :pickup-continent-pos) next-pickup)))

(defn- unloading-crawl-move
  "Moves unloading transport to adjacent coastal sea cell to find empty land.
   Like coastal-crawl-move but without auto-loading armies."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        history (set (:crawl-history unit []))
        passable (get-passable-sea-neighbors pos)
        empty-passable (filter (fn [n]
                                 (nil? (:contents (get-in @atoms/game-map n))))
                               passable)
        coastal-cells (filter adjacent-to-land? empty-passable)
        preferred (remove history coastal-cells)
        targets (if (seq preferred) preferred coastal-cells)]
    (when (seq targets)
      (let [target (rand-nth targets)]
        (core/move-unit-to pos target)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility target :computer)
        (let [new-history (vec (take-last 3 (conj (:crawl-history unit []) pos)))]
          (swap! atoms/game-map assoc-in (conj target :contents :crawl-history) new-history))
        target))))

(defn- start-sailing
  "Transition transport from loading to sailing with BFS path."
  [pos transport]
  (set-transport-mission pos :sailing)
  (mint-unload-event-id pos transport)
  (when-not @atoms/transport-fully-loaded?
    (reset! atoms/transport-fully-loaded? true))
  (mint-unload-country-id pos)
  (record-pickup-continent-pos pos transport)
  (when-let [path (compute-sail-path pos)]
    (swap! atoms/game-map assoc-in
           (conj pos :contents :sail-path) path)))

(defn- should-try-opportunistic-unload?
  [army-count mission]
  (and (pos? army-count)
       (#{:sailing :unloading} mission)))

(defn- process-unloading-mission
  [pos army-count]
  (if (zero? army-count)
    (transition-to-loading pos)
    (let [transport (get-in @atoms/game-map (conj pos :contents))]
      (if (has-nearby-unloadable-land? pos transport 5)
        (if-let [pos1 (unloading-crawl-move pos)]
          (or (unloading-crawl-move pos1) pos1)
          (start-sailing pos transport))
        (start-sailing pos transport)))))

(defn- sail-retreat
  [pos sail-path]
  (let [retreat (first (get-passable-sea-neighbors pos))]
    (when (core/move-unit-to pos retreat)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility retreat :computer)
      (swap! atoms/game-map assoc-in
             (conj retreat :contents :sail-path)
             (vec (cons pos sail-path)))
      retreat)))

(defn- sail-take-second-step
  [from-pos next-pos remaining]
  (let [step2 (or (first remaining) (continue-pos from-pos next-pos))
        remaining2 (if (seq remaining) (vec (rest remaining)) [])
        moved2 (when step2 (core/move-unit-to next-pos step2))]
    (if moved2
      (do (visibility/update-cell-visibility next-pos :computer)
          (visibility/update-cell-visibility step2 :computer)
          (swap! atoms/game-map assoc-in
                 (conj step2 :contents :sail-path) remaining2)
          (try-opportunistic-unload step2)
          step2)
      (do (swap! atoms/game-map assoc-in
                 (conj next-pos :contents :sail-path) remaining)
          (try-opportunistic-unload next-pos)
          next-pos))))

(defn- sail-follow-path
  [pos sail-path]
  (let [next-pos (first sail-path)
        remaining (vec (rest sail-path))]
    (if (core/move-unit-to pos next-pos)
      (do (visibility/update-cell-visibility pos :computer)
          (visibility/update-cell-visibility next-pos :computer)
          (sail-take-second-step pos next-pos remaining))
      (sail-retreat pos sail-path))))

(defn- process-sailing-mission
  [pos]
  (let [transport (get-in @atoms/game-map (conj pos :contents))
        sail-path (:sail-path transport)
        army-count (:army-count transport 0)]
    (cond
      (and (empty? sail-path) (zero? army-count))
      (set-transport-mission pos :loading)

      (and (empty? sail-path) (pos? army-count))
      (do (set-transport-mission pos :unloading)
          (try-opportunistic-unload pos))

      (seq sail-path)
      (sail-follow-path pos sail-path))))

(defn- loading-crawl-move
  [pos]
  (let [move-one (fn [p]
                   (let [t (get-in @atoms/game-map (conj p :contents))
                         pcp (:pickup-continent-pos t)]
                     (if pcp
                       (or (move-toward-position p pcp)
                           (coastal-crawl-move p))
                       (coastal-crawl-move p))))]
    (when-let [pos1 (move-one pos)]
      (or (move-one pos1) pos1))))

(defn- should-start-sailing?
  [pos transport army-count]
  (and (>= army-count 4)
       (or (>= army-count 6)
           (not (has-nearby-loadable-armies? pos transport 3)))))

(defn- clear-pickup-continent-if-arrived
  [pos]
  (let [transport (get-in @atoms/game-map (conj pos :contents))
        pcp (:pickup-continent-pos transport)]
    (when (and pcp
               (adjacent-to-land? pos)
               (adjacent-to-pickup-continent? pos pcp))
      (swap! atoms/game-map update-in (conj pos :contents)
             dissoc :pickup-continent-pos))))

(def ^:private max-loading-rounds 10)

(defn- loading-stale?
  [transport]
  (let [since (:loading-since transport)]
    (and since (> (- @atoms/round-number since) max-loading-rounds))))

(defn- handle-stale-loading
  "When loading has stalled, sail with what we have or find a new pcp."
  [pos transport army-count]
  (if (pos? army-count)
    (start-sailing pos transport)
    (let [new-pcp (find-next-pickup-continent-pos pos nil 0)]
      (swap! atoms/game-map assoc-in (conj pos :contents :pickup-continent-pos) new-pcp)
      (swap! atoms/game-map assoc-in (conj pos :contents :loading-since) @atoms/round-number)
      (loading-crawl-move pos))))

(defn- process-loading-mission
  [pos]
  (load-adjacent-armies pos)
  (clear-pickup-continent-if-arrived pos)
  (let [transport' (get-in @atoms/game-map (conj pos :contents))
        army-count' (:army-count transport' 0)]
    (cond
      (should-start-sailing? pos transport' army-count')
      (start-sailing pos transport')

      (loading-stale? transport')
      (handle-stale-loading pos transport' army-count')

      :else
      (loading-crawl-move pos))))

(defn- fix-idle-mission
  [pos mission]
  (when (or (nil? mission) (= :idle mission))
    (set-transport-mission pos :loading)))

(defn- process-invading-mission
  "Follow precomputed invasion path. Steps up to 2 cells per round.
   When path exhausted, transition to unloading with coast-crawl."
  [pos]
  (let [transport (get-in @atoms/game-map (conj pos :contents))
        path (:invasion-path transport)]
    (if (empty? path)
      (set-transport-mission pos :unloading)
      (let [step1 (first path)
            remaining1 (vec (rest path))]
        (if (core/move-unit-to pos step1)
          (do
            (visibility/update-cell-visibility pos :computer)
            (visibility/update-cell-visibility step1 :computer)
            (if (empty? remaining1)
              (do (swap! atoms/game-map update-in (conj step1 :contents) dissoc :invasion-path)
                  (set-transport-mission step1 :unloading))
              (let [step2 (first remaining1)
                    remaining2 (vec (rest remaining1))]
                (if (core/move-unit-to step1 step2)
                  (do
                    (visibility/update-cell-visibility step1 :computer)
                    (visibility/update-cell-visibility step2 :computer)
                    (if (empty? remaining2)
                      (do (swap! atoms/game-map update-in (conj step2 :contents) dissoc :invasion-path)
                          (set-transport-mission step2 :unloading))
                      (swap! atoms/game-map assoc-in
                             (conj step2 :contents :invasion-path) remaining2)))
                  (swap! atoms/game-map assoc-in
                         (conj step1 :contents :invasion-path) remaining1)))))
          ;; Blocked — keep path for retry next round
          nil)))))

(defn- opportunistic-unload? [pos army-count mission]
  (and (should-try-opportunistic-unload? army-count mission)
       (try-opportunistic-unload pos)))

(defn- dispatch-transport-mission
  [pos transport]
  (let [army-count (:army-count transport 0)
        raw-mission (:transport-mission transport)
        mission (or raw-mission :loading)]
    (fix-idle-mission pos raw-mission)
    (debug/log-computer-event! :transport-process pos
                               {:mission mission :armies army-count
                                :pcp (:pickup-continent-pos transport)})
    (when-not (opportunistic-unload? pos army-count mission)
      (case mission
        :invading (process-invading-mission pos)
        :unloading (process-unloading-mission pos army-count)
        :sailing (process-sailing-mission pos)
        :loading (process-loading-mission pos)
        nil))))

(defn process-transport
  "Processes a transport unit using simplified 3-state mission flow.
   Returns nil after processing — transports only move once per round."
  [pos]
  (let [transport (:contents (get-in @atoms/game-map pos))]
    (when (and transport
               (= :computer (:owner transport))
               (= :transport (:type transport)))
      (dispatch-transport-mission pos transport)))
  nil)
