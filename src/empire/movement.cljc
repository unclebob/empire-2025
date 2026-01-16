(ns empire.movement
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.map-utils :as map-utils]
            [empire.unit-container :as uc]
            [empire.units.dispatcher :as dispatcher]))

(defn is-players?
  "Returns true if the cell is owned by the player."
  [cell]
  (or (= (:city-status cell) :player)
      (= (:owner (:contents cell)) :player)))

(defn is-computers?
  "Returns true if the cell is owned by the computer."
  [cell]
  (or (= (:city-status cell) :computer)
      (= (:owner (:contents cell)) :computer)))

(defn next-step-pos [pos target]
  (let [[x y] pos
        [tx ty] target
        dx (cond (zero? (- tx x)) 0
                 (pos? (- tx x)) 1
                 :else -1)
        dy (cond (zero? (- ty y)) 0
                 (pos? (- ty y)) 1
                 :else -1)]
    [(+ x dx) (+ y dy)]))

(defn chebyshev-distance
  "Returns the Chebyshev (chessboard) distance between two positions."
  [[x1 y1] [x2 y2]]
  (max (abs (- x2 x1)) (abs (- y2 y1))))

(defn- can-move-to?
  "Returns true if the unit type can move to the given cell.
   Delegates terrain validation to unit-specific modules via dispatcher."
  [unit-type cell]
  (and cell
       (nil? (:contents cell))
       (dispatcher/can-move-to? unit-type cell)))

(defn- get-sidestep-directions
  "Returns candidate sidestep directions given the blocked direction.
   First returns the two diagonals adjacent to the blocked direction,
   then the two orthogonals perpendicular to it."
  [[dx dy]]
  (cond
    ;; Blocked moving diagonally - try the two adjacent diagonals, then orthogonals
    (and (not (zero? dx)) (not (zero? dy)))
    [[dx 0] [0 dy] [(- dx) dy] [dx (- dy)]]

    ;; Blocked moving horizontally - try diagonals first, then pure vertical
    (zero? dy)
    [[dx 1] [dx -1] [0 1] [0 -1]]

    ;; Blocked moving vertically - try diagonals first, then pure horizontal
    :else
    [[1 dy] [-1 dy] [1 0] [-1 0]]))

(defn- simulate-path
  "Simulates n moves from start-pos toward target, returning the final position.
   Returns nil if the first move is invalid."
  [start-pos target unit-type n current-map]
  (loop [pos start-pos
         remaining n]
    (if (or (zero? remaining) (= pos target))
      pos
      (let [next-pos (next-step-pos pos target)
            next-cell (get-in @current-map next-pos)]
        (if (can-move-to? unit-type next-cell)
          (recur next-pos (dec remaining))
          pos)))))

(defn- find-best-sidestep
  "Finds the best sidestep direction using 4-round look-ahead.
   Returns the position to sidestep to, or nil if no valid sidestep exists
   or if sidestepping doesn't get us closer to the target."
  [from-pos target unit-type blocked-dir current-map]
  (let [candidates (get-sidestep-directions blocked-dir)
        [fx fy] from-pos
        current-dist (chebyshev-distance from-pos target)
        valid-sidesteps
        (for [[sdx sdy] candidates
              :let [sidestep-pos [(+ fx sdx) (+ fy sdy)]
                    sidestep-cell (get-in @current-map sidestep-pos)]
              :when (can-move-to? unit-type sidestep-cell)
              :let [final-pos (simulate-path sidestep-pos target unit-type 3 current-map)
                    final-dist (chebyshev-distance final-pos target)]]
          {:pos sidestep-pos :dist final-dist})]
    (when (seq valid-sidesteps)
      (let [best-dist (apply min (map :dist valid-sidesteps))
            best-options (filter #(= (:dist %) best-dist) valid-sidesteps)]
        ;; Only sidestep if it gets us closer than staying put
        (when (< best-dist current-dist)
          (:pos (rand-nth best-options)))))))

(defn update-combatant-map
  "Updates a combatant's visible map by revealing cells near their owned units.
   Optimized to use direct vector access instead of get-in/assoc-in."
  [visible-map-atom owner]
  (when-let [visible-map @visible-map-atom]
    (let [game-map @atoms/game-map
          ownership-predicate (if (= owner :player) is-players? is-computers?)
          height (count game-map)
          width (count (first game-map))
          max-i (dec height)
          max-j (dec width)]
      (loop [i 0
             result (transient (mapv transient visible-map))]
        (if (>= i height)
          (reset! visible-map-atom (mapv persistent! (persistent! result)))
          (let [game-row (game-map i)]
            (recur
              (inc i)
              (loop [j 0
                     res result]
                (if (>= j width)
                  res
                  (if (ownership-predicate (game-row j))
                    ;; This cell is owned - reveal 9 surrounding cells
                    (let [min-di (if (zero? i) 0 -1)
                          max-di (if (= i max-i) 0 1)
                          min-dj (if (zero? j) 0 -1)
                          max-dj (if (= j max-j) 0 1)]
                      (recur
                        (inc j)
                        (loop [di min-di
                               r res]
                          (if (> di max-di)
                            r
                            (recur
                              (inc di)
                              (loop [dj min-dj
                                     r2 r]
                                (if (> dj max-dj)
                                  r2
                                  (let [ni (+ i di)
                                        nj (+ j dj)
                                        cell ((game-map ni) nj)]
                                    (recur
                                      (inc dj)
                                      (assoc! r2 ni (assoc! (r2 ni) nj cell)))))))))))
                    (recur (inc j) res)))))))))))

(defn update-cell-visibility [pos owner]
  "Updates visibility around a specific cell for the given owner.
   Satellites reveal two rectangular rings (distances 1 and 2)."
  (let [visible-map-atom (if (= owner :player) atoms/player-map atoms/computer-map)
        [x y] pos
        cell (get-in @atoms/game-map pos)
        is-satellite? (= :satellite (:type (:contents cell)))
        radius (if is-satellite? 2 1)]
    (when @visible-map-atom
      (let [height (count @atoms/game-map)
            width (count (first @atoms/game-map))]
        (doseq [di (range (- radius) (inc radius))
                dj (range (- radius) (inc radius))]
          (let [ni (+ x di)
                nj (+ y dj)]
            (when (and (>= ni 0) (< ni height)
                       (>= nj 0) (< nj width))
              (let [game-cell (get-in @atoms/game-map [ni nj])]
                (swap! visible-map-atom assoc-in [ni nj] game-cell)))))))))

(defn wake-before-move [unit next-cell]
  (let [next-contents (:contents next-cell)
        fighter-landing-carrier? (and (= (:type unit) :fighter)
                                      (= (:type next-contents) :carrier)
                                      (= (:owner next-contents) (:owner unit))
                                      (not (uc/full? next-contents :fighter-count config/carrier-capacity)))]
    (cond
      (and (:contents next-cell) (not fighter-landing-carrier?))
      [(assoc (dissoc (assoc unit :mode :awake) :target) :reason :somethings-in-the-way) true]

      (and (= (:type unit) :army) (= (:type next-cell) :sea))
      [(assoc (dissoc (assoc unit :mode :awake) :target) :reason :cant-move-into-water) true]

      (and (= (:type unit) :army) (= (:type next-cell) :city) (= (:city-status next-cell) :player))
      [(assoc (dissoc (assoc unit :mode :awake) :target) :reason :cant-move-into-city) true]

      (and (= (:type unit) :fighter)
           (= (:type next-cell) :city)
           (config/hostile-city? (:city-status next-cell)))
      [(assoc (dissoc (assoc unit :mode :awake) :target) :reason :fighter-over-defended-city) true]

      (and (config/naval-unit? (:type unit)) (= (:type next-cell) :land))
      [(assoc (dissoc (assoc unit :mode :awake) :target) :reason :ships-cant-drive-on-land) true]

      :else [unit false])))

(defn near-hostile-city? [pos current-map]
  (some (fn [[di dj]]
          (let [ni (+ (first pos) di)
                nj (+ (second pos) dj)
                adj-cell (get-in @current-map [ni nj])]
            (and adj-cell
                 (= (:type adj-cell) :city)
                 (config/hostile-city? (:city-status adj-cell)))))
        map-utils/neighbor-offsets))

(defn friendly-city-in-range? [pos max-dist current-map]
  (let [[px py] pos
        height (count @current-map)
        width (count (first @current-map))]
    (some (fn [[i j]]
            (let [cell (get-in @current-map [i j])]
              (and (= (:type cell) :city)
                   (= (:city-status cell) :player)
                   (<= (max (abs (- i px)) (abs (- j py))) max-dist))))
          (for [i (range height) j (range width)] [i j]))))

(defn adjacent-to-land?
  "Returns true if the position is adjacent to a land cell."
  [pos current-map]
  (let [[x y] pos
        height (count @current-map)
        width (count (first @current-map))]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)]
              (and (>= nx 0) (< nx height)
                   (>= ny 0) (< ny width)
                   (= :land (:type (get-in @current-map [nx ny]))))))
          map-utils/neighbor-offsets)))

(defn completely-surrounded-by-sea?
  "Returns true if the position has no adjacent land cells (completely in open sea)."
  [pos current-map]
  (not (adjacent-to-land? pos current-map)))

(defn orthogonally-adjacent-to-land?
  "Returns true if the position is orthogonally adjacent to a land cell (N/S/E/W only)."
  [pos current-map]
  (let [[x y] pos
        height (count @current-map)
        width (count (first @current-map))]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)]
              (and (>= nx 0) (< nx height)
                   (>= ny 0) (< ny width)
                   (= :land (:type (get-in @current-map [nx ny]))))))
          map-utils/orthogonal-offsets)))

(defn- wake-army-check [_unit _from-pos final-pos current-map]
  (when (near-hostile-city? final-pos current-map)
    {:wake? true :reason :army-found-city}))

(defn- friendly-city? [cell]
  (and (= (:type cell) :city)
       (= (:city-status cell) :player)))

(defn- friendly-carrier? [carrier unit]
  (and (= (:type carrier) :carrier)
       (= (:owner carrier) (:owner unit))))

(defn- target-is-reachable-friendly-city? [unit final-pos fuel current-map]
  (when-let [target (:target unit)]
    (let [[tx ty] target
          [fx fy] final-pos
          target-cell (get-in @current-map target)
          target-contents (:contents target-cell)
          distance (max (abs (- tx fx)) (abs (- ty fy)))]
      (or (and (friendly-city? target-cell)
               (<= distance fuel))
          ;; Carrier may be moving away, so account for chase:
          ;; fuel needed = distance * fighter-speed / (fighter-speed - carrier-speed)
          ;; = distance * 8 / 6 = distance * 4/3
          (and (friendly-carrier? target-contents unit)
               (<= (* distance 4/3) fuel))))))

(defn- wake-fighter-check [unit _from-pos final-pos current-map]
  (let [dest-cell (get-in @current-map final-pos)
        entering-city? (= (:type dest-cell) :city)
        friendly-city? (= (:city-status dest-cell) :player)
        hostile-city? (and entering-city? (not friendly-city?))
        fuel (:fuel unit config/fighter-fuel)
        low-fuel? (<= fuel 1)
        bingo-fuel? (and (<= fuel (quot config/fighter-fuel 4))
                         (friendly-city-in-range? final-pos fuel current-map)
                         (not (target-is-reachable-friendly-city? unit final-pos fuel current-map)))]
    (cond
      hostile-city? {:wake? true :reason :fighter-shot-down :shot-down? true}
      entering-city? {:wake? true :reason :fighter-landed-and-refueled :refuel? true}
      low-fuel? {:wake? true :reason :fighter-out-of-fuel}
      bingo-fuel? {:wake? true :reason :fighter-bingo}
      :else nil)))

(defn- wake-transport-check [unit from-pos final-pos current-map]
  (let [has-armies? (pos? (:army-count unit 0))
        at-beach? (adjacent-to-land? final-pos current-map)
        was-in-open-sea? (completely-surrounded-by-sea? from-pos current-map)
        found-land? (and was-in-open-sea? at-beach?)]
    (cond
      found-land? {:wake? true :reason :transport-found-land}
      (and has-armies? at-beach?) {:wake? true :reason :transport-at-beach}
      :else nil)))

(def ^:private wake-check-handlers
  {:army wake-army-check
   :fighter wake-fighter-check
   :transport wake-transport-check})

(defn- apply-wake-result [unit result]
  (cond-> (assoc unit :mode :awake)
    (:reason result) (assoc :reason (:reason result))
    (:refuel? result) (assoc :fuel config/fighter-fuel)
    (:shot-down? result) (assoc :hits 0 :steps-remaining 0)))

(defn- get-waypoint-orders
  "Returns the waypoint marching orders at final-pos if unit is an army, else nil."
  [unit final-pos current-map]
  (when (= :army (:type unit))
    (let [cell (get-in @current-map final-pos)]
      (:marching-orders (:waypoint cell)))))

(defn wake-after-move [unit from-pos final-pos current-map]
  (let [is-at-target? (= final-pos (:target unit))
        handler (wake-check-handlers (:type unit))
        result (when handler (handler unit from-pos final-pos current-map))
        waypoint-orders (get-waypoint-orders unit final-pos current-map)
        wake-up? (and (or is-at-target? (:wake? result))
                      (not waypoint-orders))]
    (when (:shot-down? result)
      (atoms/set-line3-message (:fighter-destroyed-by-city config/messages) 3000))
    (cond
      waypoint-orders
      (assoc unit :target waypoint-orders)

      wake-up?
      (dissoc (apply-wake-result unit result) :target)

      :else
      unit)))

(defn process-consumables [unit to-cell]
  (if (and unit (= (:type unit) :fighter))
    (if (= (:type to-cell) :city)
      unit
      (let [current-fuel (:fuel unit config/fighter-fuel)
            new-fuel (dec current-fuel)]
        (if (<= new-fuel -1)
          nil
          (assoc unit :fuel new-fuel))))
    unit))

(defn load-adjacent-sentry-armies
  "Loads adjacent sentry armies onto a transport at the given coords.
   Wakes up the transport if it has armies and is at a beach."
  [transport-coords]
  (let [cell (get-in @atoms/game-map transport-coords)
        unit (:contents cell)]
    (when (and (= (:type unit) :transport)
               (not (uc/full? unit :army-count config/transport-capacity)))
      (let [[tx ty] transport-coords
            height (count @atoms/game-map)
            width (count (first @atoms/game-map))]
        (doseq [[dx dy] map-utils/neighbor-offsets]
          (let [nx (+ tx dx)
                ny (+ ty dy)]
            (when (and (>= nx 0) (< nx height) (>= ny 0) (< ny width))
              (let [adj-cell (get-in @atoms/game-map [nx ny])
                    adj-unit (:contents adj-cell)
                    transport (get-in @atoms/game-map (conj transport-coords :contents))]
                (when (and adj-unit
                           (= (:type adj-unit) :army)
                           (= (:mode adj-unit) :sentry)
                           (= (:owner adj-unit) (:owner transport))
                           (not (uc/full? transport :army-count config/transport-capacity)))
                  (swap! atoms/game-map assoc-in [nx ny] (dissoc adj-cell :contents))
                  (swap! atoms/game-map update-in (conj transport-coords :contents) uc/add-unit :army-count))))))
        ;; After loading, wake transport if at beach with armies
        (let [updated-transport (get-in @atoms/game-map (conj transport-coords :contents))
              has-armies? (pos? (uc/get-count updated-transport :army-count))
              at-beach? (adjacent-to-land? transport-coords atoms/game-map)]
          (when (and has-armies? at-beach? (= (:mode updated-transport) :sentry))
            (swap! atoms/game-map update-in (conj transport-coords :contents)
                   #(assoc % :mode :awake :reason :transport-at-beach))))))))

(defn- fighter-landing-city? [unit to-cell]
  (and unit
       (= (:type unit) :fighter)
       (= (:type to-cell) :city)
       (= (:city-status to-cell) :player)))

(defn- fighter-landing-carrier? [unit to-cell]
  (let [to-contents (:contents to-cell)]
    (and unit
         (= (:type unit) :fighter)
         (= (:type to-contents) :carrier)
         (= (:owner to-contents) (:owner unit))
         (not (uc/full? to-contents :fighter-count config/carrier-capacity)))))

(defn- classify-move [processed-unit to-cell _original-target _final-pos]
  (cond
    (nil? processed-unit) :unit-destroyed
    (fighter-landing-city? processed-unit to-cell) :fighter-land-at-city
    (fighter-landing-carrier? processed-unit to-cell) :fighter-land-on-carrier
    :else :normal-move))

(defn- update-destination-cell [move-type to-cell processed-unit]
  (case move-type
    :unit-destroyed to-cell  ;; Unit crashed - leave destination unchanged
    :fighter-land-at-city (uc/add-unit to-cell :fighter-count)
    :fighter-land-on-carrier (update to-cell :contents uc/add-unit :fighter-count)
    :normal-move (assoc to-cell :contents processed-unit)))

(defn do-move [from-coords final-pos cell final-unit]
  (let [from-cell (dissoc cell :contents)
        to-cell (get-in @atoms/game-map final-pos)
        processed-unit (process-consumables final-unit to-cell)
        original-target (:target (:contents cell))
        move-type (classify-move processed-unit to-cell original-target final-pos)
        updated-to-cell (update-destination-cell move-type to-cell processed-unit)]
    (swap! atoms/game-map assoc-in from-coords from-cell)
    (swap! atoms/game-map assoc-in final-pos updated-to-cell)
    (update-cell-visibility final-pos (:owner (:contents cell)))
    (when (= (:type processed-unit) :transport)
      (load-adjacent-sentry-armies final-pos))))

(defn- blocked-by-friendly?
  "Returns true if the next cell contains a friendly unit (same owner)."
  [unit next-cell]
  (let [blocker (:contents next-cell)]
    (and blocker
         (= (:owner blocker) (:owner unit)))))

(defn- should-sidestep-city?
  "Returns true if unit should sidestep around the city in next-cell.
   Armies sidestep friendly cities. Fighters sidestep all cities except their target."
  [unit next-cell next-pos]
  (when (= :city (:type next-cell))
    (cond
      ;; Army should sidestep friendly cities
      (and (= :army (:type unit))
           (= :player (:city-status next-cell)))
      true

      ;; Fighter should sidestep any city that's not its target
      (and (= :fighter (:type unit))
           (not= next-pos (:target unit)))
      true

      :else false)))

(defn- get-blocked-direction
  "Returns the direction [dx dy] from pos to next-pos."
  [[x y] [nx ny]]
  [(- nx x) (- ny y)])

(defn- try-sidestep
  "Attempts to sidestep around a blocked cell. Returns {:result :sidestep :pos new-pos}
   if successful, or {:result :woke :pos from-coords} if no valid sidestep exists."
  [from-coords next-pos target-coords cell woken-unit current-map]
  (let [unit (:contents cell)
        blocked-dir (get-blocked-direction from-coords next-pos)
        sidestep-pos (find-best-sidestep from-coords target-coords (:type unit) blocked-dir current-map)]
    (if sidestep-pos
      (let [final-unit (wake-after-move unit from-coords sidestep-pos current-map)]
        (do-move from-coords sidestep-pos cell final-unit)
        {:result :sidestep :pos sidestep-pos})
      (let [updated-cell (assoc cell :contents woken-unit)]
        (swap! atoms/game-map assoc-in from-coords updated-cell)
        (update-cell-visibility from-coords (:owner unit))
        {:result :woke :pos from-coords}))))

(defn- wake-unit-for-city [unit]
  "Creates a woken unit with appropriate reason for city blocking."
  (let [reason (if (= :army (:type unit)) :cant-move-into-city :fighter-over-defended-city)]
    (assoc (dissoc (assoc unit :mode :awake) :target) :reason reason)))

(defn move-unit
  "Moves a unit one step toward target. Returns a map with:
   :result - :normal, :sidestep, or :woke
   :pos - the new position (or original if woke)"
  [from-coords target-coords cell current-map]
  (let [unit (:contents cell)
        next-pos (next-step-pos from-coords target-coords)
        next-cell (get-in @current-map next-pos)
        [woken-unit woke?] (wake-before-move unit next-cell)]
    (cond
      ;; Sidestep around cities (armies around friendly, fighters around non-target)
      (should-sidestep-city? unit next-cell next-pos)
      (try-sidestep from-coords next-pos target-coords cell (wake-unit-for-city unit) current-map)

      ;; Blocked by friendly unit - try to sidestep
      (and woke?
           (= (:reason woken-unit) :somethings-in-the-way)
           (blocked-by-friendly? unit next-cell))
      (try-sidestep from-coords next-pos target-coords cell woken-unit current-map)

      ;; Other wake conditions - just wake up
      woke?
      (let [updated-cell (assoc cell :contents woken-unit)]
        (swap! atoms/game-map assoc-in from-coords updated-cell)
        (update-cell-visibility from-coords (:owner unit))
        {:result :woke :pos from-coords})

      ;; Normal move
      :else
      (let [final-unit (wake-after-move unit from-coords next-pos current-map)]
        (do-move from-coords next-pos cell final-unit)
        {:result :normal :pos next-pos}))))

(defn- extend-to-boundary
  "Extends from position in direction until hitting a boundary."
  [[x y] [dx dy] map-height map-width]
  (loop [px x py y]
    (let [nx (+ px dx)
          ny (+ py dy)]
      (if (and (>= nx 0) (< nx map-height)
               (>= ny 0) (< ny map-width))
        (recur nx ny)
        [px py]))))

(defn- calculate-satellite-target
  "For satellites, extends the target to the map boundary in the direction of travel."
  [unit-coords target-coords]
  (let [[ux uy] unit-coords
        [tx ty] target-coords
        dx (Integer/signum (- tx ux))
        dy (Integer/signum (- ty uy))
        map-height (count @atoms/game-map)
        map-width (count (first @atoms/game-map))]
    (extend-to-boundary unit-coords [dx dy] map-height map-width)))

(defn set-unit-movement [unit-coords target-coords]
  (let [first-cell (get-in @atoms/game-map unit-coords)
        unit (:contents first-cell)
        ;; For satellites, extend target to the boundary
        actual-target (if (= :satellite (:type unit))
                        (calculate-satellite-target unit-coords target-coords)
                        target-coords)
        updated-contents (-> unit
                             (assoc :mode :moving :target actual-target)
                             (dissoc :reason))]
    (swap! atoms/game-map assoc-in unit-coords (assoc first-cell :contents updated-contents))))

(defn get-active-unit
  "Returns the unit currently needing attention: awake army aboard transport, awake fighter on carrier,
   then awake contents, then awake airport fighter.
   For armies aboard transport, returns a synthetic army map with :aboard-transport true.
   For fighters on carrier, returns a synthetic fighter map with :from-carrier true.
   For fighters in airport, returns a synthetic fighter map with :from-airport true."
  [cell]
  (let [contents (:contents cell)
        has-awake-army-aboard? (and (= (:type contents) :transport)
                                    (uc/has-awake? contents :awake-armies))
        has-awake-carrier-fighter? (and (= (:type contents) :carrier)
                                        (uc/has-awake? contents :awake-fighters))
        has-awake-airport-fighter? (uc/has-awake? cell :awake-fighters)]
    (cond
      has-awake-army-aboard? {:type :army :mode :awake :owner (:owner contents) :aboard-transport true}
      has-awake-carrier-fighter? {:type :fighter :mode :awake :owner (:owner contents) :fuel config/fighter-fuel :from-carrier true}
      (and contents (= (:mode contents) :awake)) contents
      has-awake-airport-fighter? {:type :fighter :mode :awake :owner :player :fuel config/fighter-fuel :from-airport true}
      :else nil)))

(defn is-army-aboard-transport?
  "Returns true if the active unit is an army aboard a transport."
  [active-unit]
  (and active-unit
       (:aboard-transport active-unit)))

(defn is-fighter-from-airport?
  "Returns true if the active unit is a fighter from the airport."
  [active-unit]
  (and active-unit
       (:from-airport active-unit)))

(defn is-fighter-from-carrier?
  "Returns true if the active unit is a fighter from a carrier."
  [active-unit]
  (and active-unit
       (:from-carrier active-unit)))

(defn movement-context
  "Determines the movement context for a cell and active unit.
   Returns :airport-fighter, :carrier-fighter, :army-aboard, or :standard-unit."
  [_cell active-unit]
  (cond
    (is-fighter-from-airport? active-unit) :airport-fighter
    (is-fighter-from-carrier? active-unit) :carrier-fighter
    (is-army-aboard-transport? active-unit) :army-aboard
    :else :standard-unit))

(defn launch-fighter-from-airport
  "Removes first awake fighter from airport and sets it moving to target.
   Returns the coordinates where the fighter was placed."
  [city-coords target-coords]
  (let [cell (get-in @atoms/game-map city-coords)
        after-remove (uc/remove-awake-unit cell :fighter-count :awake-fighters)
        moving-fighter {:type :fighter :mode :moving :owner :player :fuel config/fighter-fuel :target target-coords :hits 1
                        :steps-remaining (config/unit-speed :fighter)}
        updated-cell (assoc after-remove :contents moving-fighter)]
    (swap! atoms/game-map assoc-in city-coords updated-cell)
    city-coords))

(defn wake-armies-on-transport
  "Wakes up all armies aboard the transport at the given coords."
  [transport-coords]
  (let [cell (get-in @atoms/game-map transport-coords)
        transport (:contents cell)
        updated-transport (-> transport
                              (uc/wake-all :army-count :awake-armies)
                              (assoc :mode :sentry)
                              (dissoc :reason))
        updated-cell (assoc cell :contents updated-transport)]
    (swap! atoms/game-map assoc-in transport-coords updated-cell)))

(defn sleep-armies-on-transport
  "Puts all armies aboard the transport back to sleep (sentry mode).
   Wakes up the transport so it can receive orders."
  [transport-coords]
  (let [cell (get-in @atoms/game-map transport-coords)
        transport (:contents cell)
        updated-transport (-> transport
                              (uc/sleep-all :awake-armies)
                              (assoc :mode :awake)
                              (dissoc :reason))
        updated-cell (assoc cell :contents updated-transport)]
    (swap! atoms/game-map assoc-in transport-coords updated-cell)))

(defn disembark-army-from-transport
  "Removes first awake army from transport and places it on target land cell.
   Army remains awake and ready for orders. Other armies remain on transport.
   Wakes the transport when no more awake armies remain.
   Returns the coordinates where the army was placed."
  [transport-coords target-coords]
  (let [cell (get-in @atoms/game-map transport-coords)
        transport (:contents cell)
        after-remove (uc/remove-awake-unit transport :army-count :awake-armies)
        no-more-awake? (not (uc/has-awake? after-remove :awake-armies))
        disembarked-army {:type :army :mode :awake :owner (:owner transport) :hits 1
                         :steps-remaining (config/unit-speed :army)}
        updated-transport (cond-> after-remove
                            no-more-awake? (assoc :mode :awake)
                            no-more-awake? (dissoc :reason))
        updated-cell (assoc cell :contents updated-transport)]
    (swap! atoms/game-map assoc-in transport-coords updated-cell)
    (swap! atoms/game-map assoc-in (conj target-coords :contents) disembarked-army)
    (update-cell-visibility target-coords (:owner transport))
    target-coords))

(defn disembark-army-with-target
  "Removes first awake army from transport and places it on adjacent cell in moving mode.
   Army will continue moving toward the extended target on subsequent turns.
   Steps-remaining is 0 because the disembark used the army's one step."
  [transport-coords adjacent-coords extended-target]
  (let [cell (get-in @atoms/game-map transport-coords)
        transport (:contents cell)
        after-remove (uc/remove-awake-unit transport :army-count :awake-armies)
        no-more-awake? (not (uc/has-awake? after-remove :awake-armies))
        moving-army {:type :army :mode :moving :owner (:owner transport) :hits 1
                     :steps-remaining 0
                     :target extended-target}
        updated-transport (cond-> after-remove
                            no-more-awake? (assoc :mode :awake)
                            no-more-awake? (dissoc :reason))
        updated-cell (assoc cell :contents updated-transport)]
    (swap! atoms/game-map assoc-in transport-coords updated-cell)
    (swap! atoms/game-map assoc-in (conj adjacent-coords :contents) moving-army)
    (update-cell-visibility adjacent-coords (:owner transport))))

(defn disembark-army-to-explore
  "Removes first awake army from transport and places it on target land cell in explore mode.
   Returns the coordinates where the army was placed."
  [transport-coords target-coords]
  (let [cell (get-in @atoms/game-map transport-coords)
        transport (:contents cell)
        after-remove (uc/remove-awake-unit transport :army-count :awake-armies)
        no-more-awake? (not (uc/has-awake? after-remove :awake-armies))
        exploring-army {:type :army :mode :explore :owner (:owner transport) :hits 1
                        :steps-remaining (config/unit-speed :army)
                        :explore-steps config/explore-steps
                        :visited #{target-coords}}
        updated-transport (cond-> after-remove
                            no-more-awake? (assoc :mode :awake)
                            no-more-awake? (dissoc :reason))
        updated-cell (assoc cell :contents updated-transport)]
    (swap! atoms/game-map assoc-in transport-coords updated-cell)
    (swap! atoms/game-map assoc-in (conj target-coords :contents) exploring-army)
    (update-cell-visibility target-coords (:owner transport))
    target-coords))

(defn wake-fighters-on-carrier
  "Wakes up all fighters aboard the carrier at the given coords."
  [carrier-coords]
  (let [cell (get-in @atoms/game-map carrier-coords)
        carrier (:contents cell)
        updated-carrier (-> carrier
                            (uc/wake-all :fighter-count :awake-fighters)
                            (assoc :mode :sentry)
                            (dissoc :reason))
        updated-cell (assoc cell :contents updated-carrier)]
    (swap! atoms/game-map assoc-in carrier-coords updated-cell)))

(defn sleep-fighters-on-carrier
  "Puts all fighters aboard the carrier back to sleep.
   Wakes up the carrier so it can receive orders."
  [carrier-coords]
  (let [cell (get-in @atoms/game-map carrier-coords)
        carrier (:contents cell)
        updated-carrier (-> carrier
                            (uc/sleep-all :awake-fighters)
                            (assoc :mode :awake)
                            (dissoc :reason))
        updated-cell (assoc cell :contents updated-carrier)]
    (swap! atoms/game-map assoc-in carrier-coords updated-cell)))

(defn launch-fighter-from-carrier
  "Removes first awake fighter from carrier and sets it moving to target.
   Fighter is placed at the adjacent cell toward target.
   Carrier stays in its current mode (sentry carriers remain sentry).
   Returns the coordinates where the fighter was placed."
  [carrier-coords target-coords]
  (let [cell (get-in @atoms/game-map carrier-coords)
        carrier (:contents cell)
        after-remove (uc/remove-awake-unit carrier :fighter-count :awake-fighters)
        ;; Calculate first step toward target
        [cx cy] carrier-coords
        [tx ty] target-coords
        dx (cond (zero? (- tx cx)) 0 (pos? (- tx cx)) 1 :else -1)
        dy (cond (zero? (- ty cy)) 0 (pos? (- ty cy)) 1 :else -1)
        first-step [(+ cx dx) (+ cy dy)]
        moving-fighter {:type :fighter :mode :moving :owner (:owner carrier) :fuel config/fighter-fuel :target target-coords :hits 1
                        :steps-remaining (dec (config/unit-speed :fighter))}
        updated-cell (assoc cell :contents after-remove)
        target-cell (get-in @atoms/game-map first-step)]
    ;; Update carrier
    (swap! atoms/game-map assoc-in carrier-coords updated-cell)
    ;; Place fighter at first step position
    (swap! atoms/game-map assoc-in first-step (assoc target-cell :contents moving-fighter))
    (update-cell-visibility first-step (:owner carrier))
    first-step))

(defn set-unit-mode [coords mode]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        updated-unit (dissoc (assoc unit :mode mode) :reason)
        updated-cell (assoc cell :contents updated-unit)]
    (swap! atoms/game-map assoc-in coords updated-cell)))

(defn set-explore-mode [coords]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        updated-unit (-> unit
                         (assoc :mode :explore
                                :explore-steps config/explore-steps
                                :visited #{coords})
                         (dissoc :reason :target))]
    (swap! atoms/game-map assoc-in coords (assoc cell :contents updated-unit))))

(defn valid-explore-cell?
  "Returns true if a cell is valid for army exploration (land, no city, no unit)."
  [cell]
  (and cell
       (= :land (:type cell))
       (nil? (:contents cell))))

(defn adjacent-to-sea?
  "Returns true if the position has an adjacent sea cell."
  [pos current-map]
  (let [[x y] pos
        height (count @current-map)
        width (count (first @current-map))]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)]
              (and (>= nx 0) (< nx height)
                   (>= ny 0) (< ny width)
                   (= :sea (:type (get-in @current-map [nx ny]))))))
          map-utils/neighbor-offsets)))

(defn get-valid-explore-moves
  "Returns list of valid adjacent positions for exploration."
  [pos current-map]
  (let [[x y] pos
        height (count @current-map)
        width (count (first @current-map))]
    (for [[dx dy] map-utils/neighbor-offsets
          :let [nx (+ x dx)
                ny (+ y dy)
                cell (when (and (>= nx 0) (< nx height)
                                (>= ny 0) (< ny width))
                       (get-in @current-map [nx ny]))]
          :when (valid-explore-cell? cell)]
      [nx ny])))

(defn adjacent-to-unexplored?
  "Returns true if the position has an adjacent unexplored cell."
  [pos]
  (let [[x y] pos
        height (count @atoms/player-map)
        width (count (first @atoms/player-map))]
    (some (fn [[dx dy]]
            (let [nx (+ x dx)
                  ny (+ y dy)]
              (and (>= nx 0) (< nx height)
                   (>= ny 0) (< ny width)
                   (nil? (get-in @atoms/player-map [nx ny])))))
          map-utils/neighbor-offsets)))

(defn get-unexplored-explore-moves
  "Returns valid moves that are adjacent to unexplored cells."
  [pos current-map]
  (filter adjacent-to-unexplored?
          (get-valid-explore-moves pos current-map)))

(defn pick-explore-move
  "Picks the next explore move - prefers unexplored, then coast following, then random.
   Avoids visited cells unless no other option."
  [pos current-map visited]
  (let [all-moves (get-valid-explore-moves pos current-map)
        unvisited-moves (remove visited all-moves)
        unexplored-moves (filter adjacent-to-unexplored? unvisited-moves)
        on-coast? (adjacent-to-sea? pos current-map)
        coastal-moves (when on-coast? (filter #(adjacent-to-sea? % current-map) unvisited-moves))]
    (cond
      ;; Prefer moves towards unexplored areas
      (seq unexplored-moves)
      (rand-nth unexplored-moves)

      ;; On coast with coastal moves available - follow coast
      (seq coastal-moves)
      (rand-nth coastal-moves)

      ;; Unvisited random walk
      (seq unvisited-moves)
      (rand-nth unvisited-moves)

      ;; All visited - allow revisiting as last resort
      (seq all-moves)
      (rand-nth all-moves)

      ;; No valid moves - stuck
      :else nil)))

(defn move-explore-unit
  "Moves an exploring army one step. Returns new position or nil if done/stuck."
  [coords]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        remaining-steps (dec (:explore-steps unit config/explore-steps))
        visited (or (:visited unit) #{})]
    (if (<= remaining-steps 0)
      ;; Wake up after 50 steps
      (do
        (swap! atoms/game-map assoc-in coords
               (assoc cell :contents (-> unit
                                         (assoc :mode :awake)
                                         (dissoc :explore-steps :visited))))
        nil)
      ;; Try to move (return nil to limit to one step per round)
      (if-let [next-pos (pick-explore-move coords atoms/game-map visited)]
        (let [next-cell (get-in @atoms/game-map next-pos)
              found-city? (near-hostile-city? next-pos atoms/game-map)
              moved-unit (if found-city?
                           (-> unit
                               (assoc :mode :awake :reason :army-found-city)
                               (dissoc :explore-steps :visited))
                           (-> unit
                               (assoc :explore-steps remaining-steps)
                               (assoc :visited (conj visited next-pos))))]
          (swap! atoms/game-map assoc-in coords (dissoc cell :contents))
          (swap! atoms/game-map assoc-in next-pos (assoc next-cell :contents moved-unit))
          (update-cell-visibility next-pos (:owner unit))
          nil)
        ;; Stuck - wake up
        (do
          (swap! atoms/game-map assoc-in coords
                 (assoc cell :contents (-> unit
                                           (assoc :mode :awake)
                                           (dissoc :explore-steps :visited))))
          nil)))))

;; Coastline following functions for transports and patrol boats

(defn coastline-follow-eligible?
  "Returns true if unit can use coastline-follow mode (transport or patrol-boat near coast)."
  [unit near-coast?]
  (and near-coast?
       (#{:transport :patrol-boat} (:type unit))))

(defn coastline-follow-rejection-reason
  "Returns the reason why a unit can't use coastline-follow, or nil if eligible or not applicable."
  [unit near-coast?]
  (when (and (#{:transport :patrol-boat} (:type unit))
             (not near-coast?))
    :not-near-coast))

(defn set-coastline-follow-mode
  "Sets a unit to coastline-follow mode with initial state."
  [coords]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        updated-unit (-> unit
                         (assoc :mode :coastline-follow
                                :coastline-steps config/coastline-steps
                                :start-pos coords
                                :visited #{coords}
                                :prev-pos nil)
                         (dissoc :reason :target))]
    (swap! atoms/game-map assoc-in coords (assoc cell :contents updated-unit))))

(defn valid-coastline-cell?
  "Returns true if a cell is valid for coastline movement (sea, no unit)."
  [cell]
  (and cell
       (= :sea (:type cell))
       (nil? (:contents cell))))

(defn get-valid-coastline-moves
  "Returns list of valid adjacent sea positions for coastline following."
  [pos current-map]
  (let [[x y] pos
        height (count @current-map)
        width (count (first @current-map))]
    (for [[dx dy] map-utils/neighbor-offsets
          :let [nx (+ x dx)
                ny (+ y dy)
                cell (when (and (>= nx 0) (< nx height)
                                (>= ny 0) (< ny width))
                       (get-in @current-map [nx ny]))]
          :when (valid-coastline-cell? cell)]
      [nx ny])))

(defn pick-coastline-move
  "Picks the next coastline move - prefers cells that expose unexplored territory,
   then orthogonally adjacent to land (shore hugging), then diagonally adjacent.
   Avoids visited cells and backsteps. Returns nil if no valid moves."
  [pos current-map visited prev-pos]
  (let [all-moves (remove #{prev-pos} (get-valid-coastline-moves pos current-map))
        unvisited-moves (vec (remove visited all-moves))
        ;; Prefer moves orthogonally adjacent to land (true shore hugging)
        orthogonal-coastal (vec (filter #(orthogonally-adjacent-to-land? % current-map) all-moves))
        unvisited-orthogonal (vec (filter #(orthogonally-adjacent-to-land? % current-map) unvisited-moves))
        ;; Fallback to any coastal moves (diagonal adjacency)
        coastal-moves (vec (filter #(adjacent-to-land? % current-map) all-moves))
        unvisited-coastal (vec (filter #(adjacent-to-land? % current-map) unvisited-moves))
        ;; Prefer moves that expose unexplored territory
        unvisited-orthogonal-unexplored (vec (filter adjacent-to-unexplored? unvisited-orthogonal))
        unvisited-coastal-unexplored (vec (filter adjacent-to-unexplored? unvisited-coastal))]
    (cond
      ;; Best: unvisited orthogonal coastal exposing unexplored
      (seq unvisited-orthogonal-unexplored)
      (rand-nth unvisited-orthogonal-unexplored)

      ;; Unvisited orthogonal coastal (no unexplored)
      (seq unvisited-orthogonal)
      (rand-nth unvisited-orthogonal)

      ;; Unvisited coastal exposing unexplored
      (seq unvisited-coastal-unexplored)
      (rand-nth unvisited-coastal-unexplored)

      ;; Unvisited coastal (no unexplored)
      (seq unvisited-coastal)
      (rand-nth unvisited-coastal)

      ;; Visited orthogonally coastal moves
      (seq orthogonal-coastal)
      (rand-nth orthogonal-coastal)

      ;; Any coastal move (even visited, diagonal)
      (seq coastal-moves)
      (rand-nth coastal-moves)

      ;; Any unvisited move
      (seq unvisited-moves)
      (rand-nth unvisited-moves)

      ;; All visited - allow revisiting (but not backstep)
      (seq all-moves)
      (rand-nth (vec all-moves))

      ;; No valid moves - stuck
      :else nil)))

(defn at-map-edge?
  "Returns true if position is at the edge of the map."
  [pos]
  (let [[x y] pos
        height (count @atoms/game-map)
        width (count (first @atoms/game-map))]
    (or (zero? x) (zero? y)
        (= x (dec height))
        (= y (dec width)))))

(defn- adjacent-positions
  "Returns all adjacent positions to coords."
  [[x y]]
  (for [[dx dy] map-utils/neighbor-offsets]
    [(+ x dx) (+ y dy)]))

(defn- wake-coastline-unit
  "Wakes a coastline unit with the given reason."
  [coords reason]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)]
    (swap! atoms/game-map assoc-in coords
           (assoc cell :contents (-> unit
                                     (assoc :mode :awake :reason reason)
                                     (dissoc :coastline-steps :visited :start-pos :target :prev-pos))))))

(defn- move-coastline-step
  "Moves a coastline-following unit one step. Returns new coords or nil if done."
  [coords]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        remaining-steps (dec (:coastline-steps unit config/coastline-steps))
        visited (or (:visited unit) #{})
        start-pos (:start-pos unit)
        prev-pos (:prev-pos unit)
        traveled-enough? (> (count visited) 5)
        start-adjacent? (and traveled-enough?
                             (some #{start-pos} (adjacent-positions coords)))]
    (cond
      (at-map-edge? coords)
      (do (wake-coastline-unit coords :hit-edge) nil)

      start-adjacent?
      (do (wake-coastline-unit coords :returned-to-start) nil)

      :else
      (if-let [next-pos (pick-coastline-move coords atoms/game-map visited prev-pos)]
        (let [next-cell (get-in @atoms/game-map next-pos)
              steps-exhausted? (<= remaining-steps 0)
              moved-unit (if steps-exhausted?
                           (-> unit
                               (assoc :mode :awake :reason :steps-exhausted)
                               (dissoc :coastline-steps :visited :start-pos :target :prev-pos))
                           (-> unit
                               (assoc :coastline-steps remaining-steps
                                      :visited (conj visited next-pos)
                                      :prev-pos coords)))]
          (swap! atoms/game-map assoc-in coords (dissoc cell :contents))
          (swap! atoms/game-map assoc-in next-pos (assoc next-cell :contents moved-unit))
          (update-cell-visibility next-pos (:owner unit))
          (when-not steps-exhausted? next-pos))
        (do (wake-coastline-unit coords :blocked) nil)))))

(defn move-coastline-unit
  "Moves a coastline-following unit based on its speed. Returns nil when done."
  [coords]
  (let [unit (:contents (get-in @atoms/game-map coords))
        speed (config/unit-speed (:type unit))]
    (loop [current-coords coords
           steps-left speed]
      (if (zero? steps-left)
        nil
        (if-let [new-coords (move-coastline-step current-coords)]
          (recur new-coords (dec steps-left))
          nil)))))

(defn add-unit-at
  "Adds a unit of the given type at the specified cell coordinates.
   Only adds if the cell is empty."
  [[cx cy] unit-type]
  (let [cell (get-in @atoms/game-map [cx cy])
        unit {:type unit-type
              :hits (config/item-hits unit-type)
              :mode :awake
              :owner :player}
        unit (if (= unit-type :fighter)
               (assoc unit :fuel config/fighter-fuel)
               unit)
        unit (if (= unit-type :satellite)
               (assoc unit :turns-remaining config/satellite-turns)
               unit)]
    (when-not (:contents cell)
      (swap! atoms/game-map assoc-in [cx cy :contents] unit))))

(defn wake-at
  "Wakes a city (removes production so it needs attention) or a sleeping unit.
   Returns true if something was woken, nil otherwise."
  [[cx cy]]
  (let [cell (get-in @atoms/game-map [cx cy])
        contents (:contents cell)]
    (cond
      ;; Wake a friendly city - remove production so it needs attention
      (and (= (:type cell) :city)
           (= (:city-status cell) :player))
      (do (swap! atoms/production dissoc [cx cy])
          true)

      ;; Wake a sleeping/sentry/explore friendly unit (not already awake)
      (and contents
           (= (:owner contents) :player)
           (not= (:mode contents) :awake))
      (do (swap! atoms/game-map assoc-in [cx cy :contents :mode] :awake)
          true)

      :else nil)))

(defn- calculate-new-satellite-target
  "Calculates a new target on the opposite boundary when satellite reaches its target.
   At corners, randomly chooses one of the two opposite boundaries."
  [[x y] map-height map-width]
  (let [at-top? (= x 0)
        at-bottom? (= x (dec map-height))
        at-left? (= y 0)
        at-right? (= y (dec map-width))
        at-corner? (and (or at-top? at-bottom?) (or at-left? at-right?))]
    (cond
      ;; Corner - choose one of the two opposite boundaries randomly
      at-corner?
      (if (zero? (rand-int 2))
        [(if at-top? (dec map-height) 0) (rand-int map-width)]
        [(rand-int map-height) (if at-left? (dec map-width) 0)])

      ;; At top/bottom edge - target opposite vertical edge
      (or at-top? at-bottom?)
      [(if at-top? (dec map-height) 0) (rand-int map-width)]

      ;; At left/right edge - target opposite horizontal edge
      (or at-left? at-right?)
      [(rand-int map-height) (if at-left? (dec map-width) 0)]

      ;; Not at boundary (shouldn't happen)
      :else
      [x y])))

(defn move-satellite
  "Moves a satellite one step toward its target.
   When at target (always on boundary), calculates new target on opposite boundary.
   Satellites without a target don't move - they wait for user input."
  [[x y]]
  (let [cell (get-in @atoms/game-map [x y])
        satellite (:contents cell)
        target (:target satellite)]
    (if-not target
      ;; No target - satellite doesn't move, waits for user input
      [x y]
      (let [map-height (count @atoms/game-map)
            map-width (count (first @atoms/game-map))
            [tx ty] target
            at-target? (and (= x tx) (= y ty))]
        (if at-target?
          ;; At target (on boundary) - bounce to opposite side
          (let [new-target (calculate-new-satellite-target [x y] map-height map-width)
                updated-satellite (assoc satellite :target new-target)]
            (swap! atoms/game-map assoc-in [x y :contents] updated-satellite)
            (update-cell-visibility [x y] (:owner satellite))
            [x y])
          ;; Not at target - move toward it
          (let [dx (Integer/signum (- tx x))
                dy (Integer/signum (- ty y))
                new-pos [(+ x dx) (+ y dy)]]
            ;; Remove from old position
            (swap! atoms/game-map assoc-in [x y :contents] nil)
            ;; Place at new position
            (swap! atoms/game-map assoc-in (conj new-pos :contents) satellite)
            (update-cell-visibility new-pos (:owner satellite))
            new-pos))))))