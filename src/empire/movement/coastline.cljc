(ns empire.movement.coastline
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.move-log :as move-log]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.visibility :as visibility]
            [empire.movement.explore :as explore]))

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
  (map-utils/get-matching-neighbors pos @current-map map-utils/neighbor-offsets
                                    valid-coastline-cell?))

(defn pick-coastline-move
  "Picks the next coastline move - prefers cells that expose unexplored territory,
   then orthogonally adjacent to land (shore hugging), then diagonally adjacent.
   Avoids visited cells and backsteps. Returns nil if no valid moves."
  [pos current-map visited prev-pos]
  (let [all-moves (remove #{prev-pos} (get-valid-coastline-moves pos current-map))
        unvisited-moves (vec (remove visited all-moves))
        ;; Prefer moves orthogonally adjacent to land (true shore hugging)
        orthogonal-coastal (vec (filter #(map-utils/orthogonally-adjacent-to-land? % current-map) all-moves))
        unvisited-orthogonal (vec (filter #(map-utils/orthogonally-adjacent-to-land? % current-map) unvisited-moves))
        ;; Fallback to any coastal moves (diagonal adjacency)
        coastal-moves (vec (filter #(map-utils/adjacent-to-land? % current-map) all-moves))
        unvisited-coastal (vec (filter #(map-utils/adjacent-to-land? % current-map) unvisited-moves))
        ;; Prefer moves that expose unexplored territory
        unvisited-orthogonal-unexplored (vec (filter explore/adjacent-to-unexplored? unvisited-orthogonal))
        unvisited-coastal-unexplored (vec (filter explore/adjacent-to-unexplored? unvisited-coastal))]
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

(defn- transport-with-armies-in-bay?
  "Returns true if the unit is a transport with armies aboard and is in a bay."
  [unit pos current-map]
  (and (= :transport (:type unit))
       (pos? (:army-count unit 0))
       (map-utils/in-bay? pos current-map)))

(defn- pre-move-wake-reason
  "Returns wake reason if unit should wake before moving, or nil to continue."
  [coords visited start-pos]
  (let [traveled-enough? (> (count visited) 5)
        start-adjacent? (and traveled-enough?
                             (some #{start-pos} (adjacent-positions coords)))]
    (when start-adjacent?
      :returned-to-start)))

(defn- post-move-wake-reason
  "Returns wake reason after moving, or nil if unit should continue."
  [unit next-pos remaining-steps start-pos]
  (let [started-at-edge? (map-utils/at-map-edge? start-pos atoms/game-map)
        now-at-edge? (map-utils/at-map-edge? next-pos atoms/game-map)]
    (cond
      (and now-at-edge? (not started-at-edge?)) :hit-edge
      (transport-with-armies-in-bay? unit next-pos atoms/game-map) :found-a-bay
      (<= remaining-steps 0) :steps-exhausted
      :else nil)))

(defn- make-woken-unit
  "Creates a woken unit with the given reason."
  [unit reason]
  (-> unit
      (assoc :mode :awake :reason reason)
      (dissoc :coastline-steps :visited :start-pos :target :prev-pos)))

(defn- make-continuing-unit
  "Creates a unit that continues coastline following."
  [unit remaining-steps visited next-pos from-pos]
  (-> unit
      (assoc :coastline-steps remaining-steps
             :visited (conj visited next-pos)
             :prev-pos from-pos)))

(defn- move-coastline-step
  "Moves a coastline-following unit one step. Returns new coords or nil if done."
  [coords]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        remaining-steps (dec (:coastline-steps unit config/coastline-steps))
        visited (or (:visited unit) #{})
        start-pos (:start-pos unit)
        prev-pos (:prev-pos unit)]
    (if-let [pre-wake (pre-move-wake-reason coords visited start-pos)]
      (do (wake-coastline-unit coords pre-wake) nil)
      (if-let [next-pos (pick-coastline-move coords atoms/game-map visited prev-pos)]
        (let [next-cell (get-in @atoms/game-map next-pos)
              post-wake (post-move-wake-reason unit next-pos remaining-steps start-pos)
              moved-unit (if post-wake
                           (make-woken-unit unit post-wake)
                           (make-continuing-unit unit remaining-steps visited next-pos coords))]
          (move-log/log-move! coords next-pos (:type unit) (:owner unit)
                              (str "coastline steps-left:" remaining-steps))
          (swap! atoms/game-map assoc-in coords (dissoc cell :contents))
          (swap! atoms/game-map assoc-in next-pos (assoc next-cell :contents moved-unit))
          (visibility/update-cell-visibility next-pos (:owner unit))
          (when-not post-wake next-pos))
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
