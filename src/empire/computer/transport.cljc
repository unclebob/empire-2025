(ns empire.computer.transport
  "Computer transport module - VMS Empire style transport movement.
   Loading: coastal crawl, auto-load armies
   Sailing: random heading with reflection off borders/explored coasts
   Unloading: stop at unexplored coast, drop armies"
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.navigation :as nav]
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
    (doseq [army-pos (take to-load armies)]
      (swap! atoms/game-map update-in army-pos dissoc :contents)
      (visibility/update-cell-visibility army-pos :computer))
    (when (pos? to-load)
      (swap! atoms/game-map update-in (conj pos :contents :army-count) + to-load))
    to-load))

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

(defn- stuck?
  "Returns true if transport hasn't moved in 10 or more rounds."
  [transport]
  (let [since (:stuck-since-round transport)]
    (and since (>= (- @atoms/round-number since) 10))))

(defn- scuttle-unload
  "Unloads as many armies as possible to adjacent land before scuttling."
  [pos transport]
  (let [neighbors (core/get-neighbors pos)
        game-map @atoms/game-map
        land-cells (filter (fn [n]
                             (let [cell (get-in game-map n)]
                               (and cell
                                    (#{:land :city} (:type cell))
                                    (nil? (:contents cell)))))
                           neighbors)
        army-count (:army-count transport 0)
        to-unload (min army-count (count land-cells))]
    (doseq [land-pos (take to-unload land-cells)]
      (let [army {:type :army :owner :computer :mode :awake :hits 1}]
        (swap! atoms/game-map assoc-in (conj land-pos :contents) army)
        (visibility/update-cell-visibility land-pos :computer)))))

(defn- mark-city-landlocked
  "Marks the producing city as landlocked so no more ships are built there."
  [city-pos]
  (when city-pos
    (swap! atoms/game-map assoc-in (conj city-pos :landlocked) true)))

(defn- reset-stuck-counter
  "Resets the stuck-since-round counter after a successful move."
  [new-pos]
  (swap! atoms/game-map assoc-in (conj new-pos :contents :stuck-since-round) @atoms/round-number))

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

(defn- sail-one-step
  "Moves transport one step along its heading. Handles:
   - Open sea: move there
   - Map border: reflect heading
   - Explored coast: reflect heading
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

      ;; Explored coast → reflect
      (nav/is-explored-coast? next-pos)
      (let [surface (or (detect-reflection-surface pos) :horizontal)
            new-heading (nav/reflect-heading heading surface)]
        (swap! atoms/game-map assoc-in (conj pos :contents :heading) new-heading)
        nil)

      ;; Sea cell with no unit → move there
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


(defn process-transport
  "Processes a transport unit using VMS Empire style logic.
   Loading: move toward armies, collect them
   Unloading: move toward enemy cities on OTHER continents, drop armies
   Returns nil after processing - transports only move once per round."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        transport (:contents cell)]
    (when (and transport
               (= :computer (:owner transport))
               (= :transport (:type transport)))
      (if (stuck? transport)
        ;; Scuttle: unload armies, mark city landlocked, remove transport
        (do (scuttle-unload pos transport)
            (mark-city-landlocked (:produced-at transport))
            (swap! atoms/game-map assoc-in (conj pos :contents) nil))
        (let [army-count (:army-count transport 0)
              mission (:transport-mission transport :idle)]

          ;; Determine mission if idle
          (when (= mission :idle)
            (set-transport-mission pos :loading))

          (let [current-mission (or (:transport-mission transport) :loading)]
            (cond
              ;; Full transport - assign heading and start sailing
              (>= army-count 6)
              (do
                (set-transport-mission pos :sailing)
                (mint-unload-event-id pos transport)
                (record-pickup-continent-pos pos transport)
                (when-not (:heading transport)
                  (swap! atoms/game-map assoc-in
                         (conj pos :contents :heading) (rand-int 360)))
                (when-let [new-pos (sail-one-step pos)]
                  (reset-stuck-counter new-pos)))

              ;; Loading transport - coastal crawl, auto-load armies
              (= current-mission :loading)
              (do
                ;; Load any adjacent armies first
                (load-adjacent-armies pos)
                ;; Coastal crawl if near land, otherwise explore toward coastline
                (when-let [new-pos (or (coastal-crawl-move pos) (explore-sea pos))]
                  (reset-stuck-counter new-pos)))

              ;; Sailing transport - continue along heading
              (= current-mission :sailing)
              (when-let [new-pos (sail-one-step pos)]
                (reset-stuck-counter new-pos))

              ;; Unloading transport - unload armies at current location
              (= current-mission :unloading)
              (let [pickup-continent (when-let [ocp (:pickup-continent-pos transport)]
                                       (land-objectives/flood-fill-continent ocp))]
                (when (unload-armies pos pickup-continent)
                  ;; After unloading, check if empty → switch to loading
                  nil))

              :else nil))))))
  nil)
