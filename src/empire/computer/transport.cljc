(ns empire.computer.transport
  "Computer transport module - VMS Empire style transport movement.
   Loading: coastal crawl, auto-load armies
   Sailing: random heading with reflection off borders/explored coasts
   Unloading: stop at unexplored coast, drop armies"
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.navigation :as nav]
            [empire.debug :as debug]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.visibility :as visibility]
            [empire.movement.map-utils :as map-utils]))

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

(defn- find-armies-to-load
  "Find computer armies that should board transports."
  []
  (let [game-map @atoms/game-map]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [cell (get-in game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :computer (:owner unit))
                     (= :army (:type unit)))]
      [i j])))

(defn- recently-unloaded-country?
  "Returns true if the country-id was unloaded to within the last 10 rounds."
  [unloaded-countries country-id]
  (when-let [unload-round (get unloaded-countries country-id)]
    (< (- @atoms/round-number unload-round) 10)))

(defn- find-nearest-army
  "Find the nearest army to the transport. When pickup-continent is provided,
   only considers armies on that continent. Excludes armies from countries
   the transport recently unloaded into. Excludes armies with matching
   transport-unload-event-id to prevent reloading just-unloaded armies."
  [transport-pos pickup-continent unloaded-countries transport-unload-event-id]
  (let [armies (find-armies-to-load)
        candidates (cond->> armies
                     pickup-continent
                     (filter #(contains? pickup-continent %))

                     (seq unloaded-countries)
                     (remove (fn [army-pos]
                               (let [unit (get-in @atoms/game-map (conj army-pos :contents))]
                                 (and (:country-id unit)
                                      (recently-unloaded-country?
                                        unloaded-countries (:country-id unit))))))

                     transport-unload-event-id
                     (remove (fn [army-pos]
                               (let [unit (get-in @atoms/game-map (conj army-pos :contents))]
                                 (= (:unload-event-id unit) transport-unload-event-id)))))]
    (when (seq candidates)
      (apply min-key #(core/distance transport-pos %) candidates))))

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

(defn- find-unload-position
  "Find a sea cell adjacent to land near the target city, closest to transport.
   Excludes positions whose adjacent land is on the pickup continent."
  [target-city pickup-continent transport-pos]
  (let [game-map @atoms/game-map
        [tr tc] target-city
        candidates (for [dr (range -3 4)
                         dc (range -3 4)
                         :let [pos [(+ tr dr) (+ tc dc)]
                               cell (get-in game-map pos)]
                         :when (and cell
                                    (= :sea (:type cell))
                                    (adjacent-to-land? pos)
                                    (nil? (:contents cell))
                                    (or (nil? pickup-continent)
                                        (let [adj-land (find-adjacent-land-pos pos)]
                                          (not (contains? pickup-continent adj-land)))))]
                     pos)]
    (when (seq candidates)
      (apply min-key #(core/distance transport-pos %) candidates))))

(defn- move-toward-position
  "Move transport one step toward target. Returns new position."
  [pos target]
  (if-let [next-step (pathfinding/next-step pos target :transport)]
    (do
      (core/move-unit-to pos next-step)
      (visibility/update-cell-visibility pos :computer)
      (visibility/update-cell-visibility next-step :computer)
      next-step)
    ;; No path - try direct movement
    (let [passable (get-passable-sea-neighbors pos)
          closest (core/move-toward pos target passable)]
      (when closest
        (core/move-unit-to pos closest)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility closest :computer)
        closest))))

(defn- explore-sea
  "Move transport toward unexplored coastline first, then any unexplored sea.
   Stays put if all sea is explored."
  [pos]
  (if-let [target (pathfinding/find-nearest-unexplored-coastline pos :transport)]
    (move-toward-position pos target)
    (when-let [target (pathfinding/find-nearest-unexplored pos :transport)]
      (move-toward-position pos target))))

(defn- find-next-pickup-continent-pos
  "After unloading, find the nearest continent with >3 computer armies,
   excluding the current unload continent. Returns an army position on
   that continent, or nil if none qualifies."
  [transport-pos current-continent]
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
        ;; Find nearest qualifying continent (>3 armies)
        (let [qualifying (filter #(> (count (:armies %)) 3) continents)]
          (when (seq qualifying)
            (let [best (apply min-key
                              (fn [{:keys [armies]}]
                                (apply min (map #(core/distance transport-pos %) armies)))
                              qualifying)]
              ;; Return the nearest army position from the best continent
              (apply min-key #(core/distance transport-pos %) (:armies best)))))
        (let [army-pos (first remaining)]
          (if (contains? seen army-pos)
            (recur (rest remaining) seen continents)
            (let [cont (land-objectives/flood-fill-continent army-pos)
                  cont-armies (filter #(contains? cont %) all-armies)]
              (recur (rest remaining)
                     (into seen cont)
                     (conj continents {:continent cont :armies cont-armies})))))))))

(defn unload-armies
  "Unload armies onto adjacent land, excluding pickup continent. Returns true if any unloaded."
  [pos pickup-continent]
  (let [transport (get-in @atoms/game-map (conj pos :contents))
        army-count (:army-count transport 0)]
    (when (pos? army-count)
      (let [land-neighbors (filter (fn [neighbor]
                                     (let [cell (get-in @atoms/game-map neighbor)]
                                       (and cell
                                            (#{:land :city} (:type cell))
                                            (nil? (:contents cell))
                                            (or (nil? pickup-continent)
                                                (not (contains? pickup-continent neighbor))))))
                                   (core/get-neighbors pos))
            to-unload (min army-count (count land-neighbors))]
        (when (pos? to-unload)
          ;; Unload armies onto land cells
          (let [unload-eid (:unload-event-id transport)]
            (doseq [land-pos (take to-unload land-neighbors)]
              (let [army (cond-> {:type :army :owner :computer :mode :awake :hits 1}
                           unload-eid (assoc :unload-event-id unload-eid))]
                (debug/log-computer-event! :transport-unload-army pos {:to land-pos :eid unload-eid})
                (swap! atoms/game-map assoc-in (conj land-pos :contents) army)
                (core/stamp-territory land-pos army)
                (visibility/update-cell-visibility land-pos :computer))))
          ;; Record unloaded country-id
          (let [unloaded-country-id (->> (take to-unload land-neighbors)
                                          (keep #(:country-id (get-in @atoms/game-map %)))
                                          first)]
            (when unloaded-country-id
              (swap! atoms/game-map update-in (conj pos :contents :unloaded-countries)
                     assoc unloaded-country-id @atoms/round-number)))
          ;; Update transport army count
          (swap! atoms/game-map update-in (conj pos :contents :army-count) - to-unload)
          ;; If fully unloaded, change mission to loading and update pickup continent
          (when (<= (- army-count to-unload) 0)
            (swap! atoms/game-map assoc-in (conj pos :contents :transport-mission) :loading)
            (swap! atoms/game-map update-in (conj pos :contents) dissoc :unload-target-city)
            (let [current-continent (when-let [land-pos (find-adjacent-land-pos pos)]
                                     (land-objectives/flood-fill-continent land-pos))
                  next-pickup (find-next-pickup-continent-pos pos current-continent)]
              (swap! atoms/game-map assoc-in
                     (conj pos :contents :pickup-continent-pos) next-pickup)))
          true)))))

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
  (swap! atoms/game-map assoc-in (conj pos :contents :transport-mission) mission))

(defn- mint-unload-event-id
  "Mint a new unload-event-id each time transport transitions to unloading.
   Always mints a fresh ID so armies from previous unload cycles can be loaded."
  [pos _transport]
  (let [id @atoms/next-unload-event-id]
    (swap! atoms/next-unload-event-id inc)
    (swap! atoms/game-map assoc-in
           (conj pos :contents :unload-event-id) id)))

(defn- record-pickup-continent-pos
  "When transport becomes full, record the nearest adjacent land position
   as the pickup continent reference point."
  [pos transport]
  (when-not (:pickup-continent-pos transport)
    (when-let [land-pos (find-adjacent-land-pos pos)]
      (swap! atoms/game-map assoc-in
             (conj pos :contents :pickup-continent-pos) land-pos))))


(defn- in-bounds?
  "Returns true if position is within map bounds."
  [pos]
  (let [[c r] pos
        game-map @atoms/game-map]
    (and (>= c 0) (>= r 0)
         (< c (count game-map))
         (< r (count (first game-map))))))


(defn- unexplored-coast?
  "Returns true if pos is a sea cell adjacent to land NOT visible on computer-map."
  [pos]
  (let [game-map @atoms/game-map
        cell (get-in game-map pos)]
    (and cell
         (= :sea (:type cell))
         (some (fn [neighbor]
                 (let [gm-cell (get-in game-map neighbor)
                       cm-cell (get-in @atoms/computer-map neighbor)]
                   (and gm-cell
                        (#{:land :city} (:type gm-cell))
                        (nil? cm-cell))))
               (core/get-neighbors pos)))))

(defn- detect-reflection-surface
  "Returns :horizontal or :vertical reflection surface for map border,
   or nil if not at a border."
  [pos]
  (let [[c r] pos
        game-map @atoms/game-map
        max-c (dec (count game-map))
        max-r (dec (count (first game-map)))]
    (cond
      (or (<= r 0) (>= r max-r)) :horizontal
      (or (<= c 0) (>= c max-c)) :vertical
      :else nil)))

(defn- own-country-coast?
  "Returns true if all explored land neighbors of pos belong to the given country-id."
  [pos country-id]
  (when country-id
    (every? (fn [neighbor]
              (let [comp-cell (get-in @atoms/computer-map neighbor)]
                (or (nil? comp-cell)
                    (not (#{:land :city} (:type comp-cell)))
                    (= country-id (:country-id comp-cell)))))
            (core/get-neighbors pos))))

(defn- sail-one-step
  "Moves transport one step along its heading. Handles:
   - Open sea: move there
   - Map border: reflect heading
   - Explored coast: reflect heading (skip own country coast)
   - Unexplored coast: stop and begin unloading
   Returns new position or nil if no move."
  [pos]
  (let [transport (get-in @atoms/game-map (conj pos :contents))
        heading (:heading transport)
        next-pos (nav/apply-heading pos heading)]
    (cond
      ;; Off map → reflect
      (not (in-bounds? next-pos))
      (let [surface (or (detect-reflection-surface pos) :horizontal)
            new-heading (nav/reflect-heading heading surface)]
        (swap! atoms/game-map assoc-in (conj pos :contents :heading) new-heading)
        nil)

      ;; Unexplored coast → stop, switch to unloading
      (unexplored-coast? next-pos)
      (do
        (core/move-unit-to pos next-pos)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility next-pos :computer)
        (set-transport-mission next-pos :unloading)
        next-pos)

      ;; Explored coast of foreign country → reflect
      (and (nav/is-explored-coast? next-pos)
           (not (own-country-coast? next-pos (:country-id transport))))
      (let [surface (or (detect-reflection-surface pos) :horizontal)
            new-heading (nav/reflect-heading heading surface)]
        (swap! atoms/game-map assoc-in (conj pos :contents :heading) new-heading)
        nil)

      ;; Sea cell with no unit → move there (includes own-country coast)
      (and (= :sea (:type (get-in @atoms/game-map next-pos)))
           (nil? (:contents (get-in @atoms/game-map next-pos))))
      (do
        (core/move-unit-to pos next-pos)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility next-pos :computer)
        next-pos)

      ;; Occupied or land → reflect
      :else
      (let [new-heading (nav/reflect-heading heading :horizontal)]
        (swap! atoms/game-map assoc-in (conj pos :contents :heading) new-heading)
        nil))))


(defn- find-assigned-cities
  "Returns set of target-city positions from all directed transports on the map."
  []
  (let [game-map @atoms/game-map]
    (into #{}
          (for [i (range (count game-map))
                j (range (count (first game-map)))
                :let [unit (get-in game-map [i j :contents])]
                :when (and unit
                           (= :transport (:type unit))
                           (= :computer (:owner unit))
                           (= :directed (:transport-mission unit))
                           (:target-city unit))]
            (:target-city unit)))))

(defn- find-unassigned-city
  "Find nearest free or player city on computer-map not targeted by any directed transport."
  [pos]
  (let [comp-map @atoms/computer-map
        assigned (find-assigned-cities)]
    (when comp-map
      (let [candidates (for [i (range (count comp-map))
                              j (range (count (first comp-map)))
                              :let [cell (get-in comp-map [i j])]
                              :when (and cell
                                         (= :city (:type cell))
                                         (#{:free :player} (:city-status cell))
                                         (not (contains? assigned [i j])))]
                          [i j])]
        (when (seq candidates)
          (apply min-key #(core/distance pos %) candidates))))))

(defn- find-nearest-coastal-cell
  "Find the nearest sea cell adjacent to target city, closest to transport."
  [target-city transport-pos]
  (let [game-map @atoms/game-map
        neighbors (core/get-neighbors target-city)
        sea-neighbors (filter (fn [n]
                                (let [cell (get-in game-map n)]
                                  (and cell (= :sea (:type cell)))))
                              neighbors)]
    (when (seq sea-neighbors)
      (apply min-key #(core/distance transport-pos %) sea-neighbors))))

(defn- compute-sea-path
  "Compute A* path from transport position to destination through sea cells.
   Returns path excluding the start position, or empty vector if no path."
  [from to]
  (if (and from to)
    (let [full-path (pathfinding/a-star from to :transport @atoms/game-map)]
      (if full-path
        (vec (rest full-path))
        []))
    []))

(defn- clear-directed-assignment
  "Remove directed assignment data from transport."
  [pos]
  (swap! atoms/game-map update-in (conj pos :contents) dissoc :target-city :path)
  (set-transport-mission pos :loading))

(defn- process-directed
  "Handle a directed transport. Follow path or unload at destination."
  [pos transport]
  (let [target-city (:target-city transport)
        path (:path transport)
        target-cell (when target-city (get-in @atoms/game-map target-city))]
    (cond
      ;; Target city conquered → drop assignment
      (and target-city
           (or (nil? target-cell)
               (not (= :city (:type target-cell)))
               (= :computer (:city-status target-cell))))
      (clear-directed-assignment pos)

      ;; At destination (empty path) → unload
      (empty? path)
      (do (unload-armies pos nil)
          (clear-directed-assignment pos))

      ;; Follow path
      :else
      (let [next-pos (first path)]
        (if (and next-pos
                 (nil? (:contents (get-in @atoms/game-map next-pos))))
          ;; Move to next step
          (do (core/move-unit-to pos next-pos)
              (visibility/update-cell-visibility pos :computer)
              (visibility/update-cell-visibility next-pos :computer)
              (swap! atoms/game-map assoc-in (conj next-pos :contents :path) (vec (rest path))))
          ;; Blocked → wait
          nil)))))

(defn process-transport
  "Processes a transport unit using VMS Empire style logic.
   Directed: follow stored A* path to target city, unload on arrival
   Loading: move toward armies, collect them
   Unloading: move toward enemy cities on OTHER continents, drop armies
   Returns nil after processing - transports only move once per round."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        transport (:contents cell)]
    (when (and transport
               (= :computer (:owner transport))
               (= :transport (:type transport)))
      (let [army-count (:army-count transport 0)
            mission (:transport-mission transport :idle)]

        ;; Determine mission if idle
        (when (= mission :idle)
          (set-transport-mission pos :loading))

        (let [current-mission (or (:transport-mission transport) :loading)]
          (debug/log-computer-event! :transport-process pos
                                     {:mission current-mission :armies army-count
                                      :pcp (:pickup-continent-pos transport)})
          (cond
            ;; Directed transport - follow stored path
            (= current-mission :directed)
            (process-directed pos transport)

            ;; Full transport - check for unassigned city, else sail
            (>= army-count 6)
            (if-let [target-city (find-unassigned-city pos)]
              (let [coastal (find-nearest-coastal-cell target-city pos)
                    path (compute-sea-path pos coastal)]
                (set-transport-mission pos :directed)
                (swap! atoms/game-map assoc-in (conj pos :contents :target-city) target-city)
                (swap! atoms/game-map assoc-in (conj pos :contents :path) path))
              (do
                (when (not= current-mission :sailing)
                  (set-transport-mission pos :sailing)
                  (mint-unload-event-id pos transport)
                  (record-pickup-continent-pos pos transport)
                  (swap! atoms/game-map assoc-in
                         (conj pos :contents :sailing-start) pos))
                (let [transport' (get-in @atoms/game-map (conj pos :contents))]
                  (or
                    (when (:heading transport')
                      ;; Phase 2: have heading — sail along it
                      (sail-one-step pos))
                    ;; Phase 1 (or Phase 2 fallback): navigate toward fog of war
                    (let [target (or (pathfinding/find-nearest-unexplored-coastline pos :transport)
                                     (pathfinding/find-nearest-unexplored pos :transport))]
                      (when target
                        ;; Clear stale heading so BFS drives movement
                        (swap! atoms/game-map update-in (conj pos :contents) dissoc :heading)
                        (when-let [new-pos (move-toward-position pos target)]
                          ;; Reached BFS target? Compute heading for Phase 2
                          (when (= new-pos target)
                            (let [t (get-in @atoms/game-map (conj new-pos :contents))
                                  start (:sailing-start t)
                                  heading (nav/heading-from-to (or start pos) new-pos)]
                              (swap! atoms/game-map assoc-in
                                     (conj new-pos :contents :heading) heading))))))))))

            ;; Loading transport - coastal crawl, auto-load armies
            (= current-mission :loading)
            (do
              ;; Load any adjacent armies first
              (load-adjacent-armies pos)
              ;; Clear pickup-continent-pos once adjacent to that continent
              (when-let [pcp (:pickup-continent-pos transport)]
                (when (adjacent-to-land? pos)
                  (let [continent (land-objectives/flood-fill-continent
                                   (find-adjacent-land-pos pos))]
                    (when (contains? continent pcp)
                      (swap! atoms/game-map update-in (conj pos :contents)
                             dissoc :pickup-continent-pos)))))
              ;; Navigate toward pickup continent if set, else coastal crawl
              (let [transport' (get-in @atoms/game-map (conj pos :contents))]
                (when-let [new-pos (if-let [pcp (:pickup-continent-pos transport')]
                                     (or (move-toward-position pos pcp)
                                         (coastal-crawl-move pos)
                                         (explore-sea pos))
                                     (or (coastal-crawl-move pos)
                                         (explore-sea pos)))]
                  new-pos)))

            ;; Sailing transport - continue along heading
            (= current-mission :sailing)
            (sail-one-step pos)

            ;; Unloading transport - unload armies at current location
            (= current-mission :unloading)
            (let [pickup-continent (when-let [ocp (:pickup-continent-pos transport)]
                                     (land-objectives/flood-fill-continent ocp))]
              (when (unload-armies pos pickup-continent)
                ;; After unloading, check if empty → switch to loading
                nil))

            :else nil)))))
  nil)
