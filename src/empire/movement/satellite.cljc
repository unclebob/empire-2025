(ns empire.movement.satellite
  (:require [empire.atoms :as atoms]
            [empire.move-log :as move-log]
            [empire.movement.visibility :as visibility]))

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

(defn calculate-satellite-target
  "For satellites, extends the target to the map boundary in the direction of travel."
  [unit-coords target-coords]
  (let [[ux uy] unit-coords
        [tx ty] target-coords
        dx (Integer/signum (- tx ux))
        dy (Integer/signum (- ty uy))
        map-height (count @atoms/game-map)
        map-width (count (first @atoms/game-map))]
    (extend-to-boundary unit-coords [dx dy] map-height map-width)))

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
            (visibility/update-cell-visibility [x y] (:owner satellite))
            [x y])
          ;; Not at target - move toward it
          (let [dx (Integer/signum (- tx x))
                dy (Integer/signum (- ty y))
                new-pos [(+ x dx) (+ y dy)]]
            (move-log/log-move! [x y] new-pos :satellite (:owner satellite)
                                (str "target:" (pr-str target)))
            ;; Remove from old position
            (swap! atoms/game-map assoc-in [x y :contents] nil)
            ;; Place at new position
            (swap! atoms/game-map assoc-in (conj new-pos :contents) satellite)
            (visibility/update-cell-visibility new-pos (:owner satellite))
            new-pos))))))
