(ns empire.units.satellite
  (:require [empire.atoms :as atoms]
            [empire.units.dispatcher :as dispatcher]))

;; Configuration
(def speed 10)
(def cost 50)
(def hits 1)
(def strength 1)
(def display-char "Z")
(def turns 50)
(def visibility-radius 2)

(defn initial-state
  "Returns initial state fields for a new satellite."
  []
  {:turns-remaining turns})

(defn can-move-to?
  "Satellites can move anywhere (they're in orbit)."
  [_cell]
  true)

(defn needs-attention?
  "Satellites need attention only when they have no target."
  [unit]
  (nil? (:target unit)))

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

(defn extend-target-to-boundary
  "Given a satellite position and clicked target, extends to the map boundary."
  [unit-coords target-coords map-height map-width]
  (let [[ux uy] unit-coords
        [tx ty] target-coords
        dx (Integer/signum (- tx ux))
        dy (Integer/signum (- ty uy))]
    (extend-to-boundary unit-coords [dx dy] map-height map-width)))

(defn calculate-bounce-target
  "Calculates new target on opposite boundary when satellite reaches edge.
   At corners, randomly chooses one of the two opposite boundaries."
  [[x y] map-height map-width]
  (let [at-top? (= x 0)
        at-bottom? (= x (dec map-height))
        at-left? (= y 0)
        at-right? (= y (dec map-width))
        at-corner? (and (or at-top? at-bottom?) (or at-left? at-right?))]
    (cond
      at-corner?
      (if (zero? (rand-int 2))
        [(if at-top? (dec map-height) 0) (rand-int map-width)]
        [(rand-int map-height) (if at-left? (dec map-width) 0)])

      (or at-top? at-bottom?)
      [(if at-top? (dec map-height) 0) (rand-int map-width)]

      (or at-left? at-right?)
      [(rand-int map-height) (if at-left? (dec map-width) 0)]

      :else
      [x y])))

(defn move-one-step
  "Moves a satellite one step toward its target.
   When at target (on boundary), calculates new target on opposite boundary.
   Satellites without a target don't move - they wait for user input.
   Returns new position."
  [[x y]]
  (let [cell (get-in @atoms/game-map [x y])
        satellite (:contents cell)
        target (:target satellite)]
    (if-not target
      [x y]
      (let [map-height (count @atoms/game-map)
            map-width (count (first @atoms/game-map))
            [tx ty] target
            at-target? (and (= x tx) (= y ty))]
        (if at-target?
          (let [new-target (calculate-bounce-target [x y] map-height map-width)
                updated-satellite (assoc satellite :target new-target)]
            (swap! atoms/game-map assoc-in [x y :contents] updated-satellite)
            [x y])
          (let [dx (Integer/signum (- tx x))
                dy (Integer/signum (- ty y))
                new-pos [(+ x dx) (+ y dy)]]
            (swap! atoms/game-map assoc-in [x y :contents] nil)
            (swap! atoms/game-map assoc-in (conj new-pos :contents) satellite)
            new-pos))))))

;; Register with dispatcher
(defmethod dispatcher/speed :satellite [_] speed)
(defmethod dispatcher/cost :satellite [_] cost)
(defmethod dispatcher/hits :satellite [_] hits)
(defmethod dispatcher/strength :satellite [_] strength)
(defmethod dispatcher/display-char :satellite [_] display-char)
(defmethod dispatcher/visibility-radius :satellite [_] visibility-radius)
(defmethod dispatcher/initial-state :satellite [_] (initial-state))
(defmethod dispatcher/can-move-to? :satellite [_ cell] (can-move-to? cell))
(defmethod dispatcher/needs-attention? :satellite [unit] (needs-attention? unit))
