(ns empire.movement
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.map-utils :as map-utils]
            [empire.unit-container :as uc]
            [empire.units.dispatcher :as dispatcher]
            [empire.visibility :as visibility]
            [empire.wake-conditions :as wake]
            [empire.explore :as explore]
            [empire.coastline :as coastline]
            [empire.satellite :as satellite]
            [empire.container-ops :as container-ops]))

;; Re-export functions from new modules for backward compatibility

;; From visibility
(def update-combatant-map visibility/update-combatant-map)
(def update-cell-visibility visibility/update-cell-visibility)

;; From wake-conditions
(def wake-before-move wake/wake-before-move)
(def wake-after-move wake/wake-after-move)
(def near-hostile-city? wake/near-hostile-city?)
(def friendly-city-in-range? wake/friendly-city-in-range?)

;; From map-utils (terrain geometry)
(def adjacent-to-land? map-utils/adjacent-to-land?)
(def orthogonally-adjacent-to-land? map-utils/orthogonally-adjacent-to-land?)
(def completely-surrounded-by-sea? map-utils/completely-surrounded-by-sea?)
(def in-bay? map-utils/in-bay?)
(def adjacent-to-sea? map-utils/adjacent-to-sea?)
(def at-map-edge? map-utils/at-map-edge?)

;; From explore
(def valid-explore-cell? explore/valid-explore-cell?)
(def get-valid-explore-moves explore/get-valid-explore-moves)
(def adjacent-to-unexplored? explore/adjacent-to-unexplored?)
(def get-unexplored-explore-moves explore/get-unexplored-explore-moves)
(def pick-explore-move explore/pick-explore-move)
(def move-explore-unit explore/move-explore-unit)
(def set-explore-mode explore/set-explore-mode)

;; From coastline
(def coastline-follow-eligible? coastline/coastline-follow-eligible?)
(def coastline-follow-rejection-reason coastline/coastline-follow-rejection-reason)
(def set-coastline-follow-mode coastline/set-coastline-follow-mode)
(def valid-coastline-cell? coastline/valid-coastline-cell?)
(def get-valid-coastline-moves coastline/get-valid-coastline-moves)
(def pick-coastline-move coastline/pick-coastline-move)
(def move-coastline-unit coastline/move-coastline-unit)

;; From satellite
(def move-satellite satellite/move-satellite)

;; From container-ops
(def load-adjacent-sentry-armies container-ops/load-adjacent-sentry-armies)
(def wake-armies-on-transport container-ops/wake-armies-on-transport)
(def sleep-armies-on-transport container-ops/sleep-armies-on-transport)
(def disembark-army-from-transport container-ops/disembark-army-from-transport)
(def disembark-army-with-target container-ops/disembark-army-with-target)
(def disembark-army-to-explore container-ops/disembark-army-to-explore)
(def wake-fighters-on-carrier container-ops/wake-fighters-on-carrier)
(def sleep-fighters-on-carrier container-ops/sleep-fighters-on-carrier)
(def launch-fighter-from-carrier container-ops/launch-fighter-from-carrier)
(def launch-fighter-from-airport container-ops/launch-fighter-from-airport)

;; Core movement functions

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
    (visibility/update-cell-visibility final-pos (:owner (:contents cell)))
    (when (= (:type processed-unit) :transport)
      (container-ops/load-adjacent-sentry-armies final-pos))))

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
      (let [final-unit (wake/wake-after-move unit from-coords sidestep-pos current-map)]
        (do-move from-coords sidestep-pos cell final-unit)
        {:result :sidestep :pos sidestep-pos})
      (let [updated-cell (assoc cell :contents woken-unit)]
        (swap! atoms/game-map assoc-in from-coords updated-cell)
        (visibility/update-cell-visibility from-coords (:owner unit))
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
        [woken-unit woke?] (wake/wake-before-move unit next-cell)]
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
        (visibility/update-cell-visibility from-coords (:owner unit))
        {:result :woke :pos from-coords})

      ;; Normal move
      :else
      (let [final-unit (wake/wake-after-move unit from-coords next-pos current-map)]
        (do-move from-coords next-pos cell final-unit)
        {:result :normal :pos next-pos}))))

(defn set-unit-movement [unit-coords target-coords]
  (let [first-cell (get-in @atoms/game-map unit-coords)
        unit (:contents first-cell)
        ;; For satellites, extend target to the boundary
        actual-target (if (= :satellite (:type unit))
                        (satellite/calculate-satellite-target unit-coords target-coords)
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

(defn set-unit-mode [coords mode]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        updated-unit (dissoc (assoc unit :mode mode) :reason)
        updated-cell (assoc cell :contents updated-unit)]
    (swap! atoms/game-map assoc-in coords updated-cell)))

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
