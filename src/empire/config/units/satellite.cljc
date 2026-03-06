(ns empire.config.units.satellite
  (:require [empire.config.units.config :as units-config]))

(defn- extend-to-boundary
  [[x y] [dx dy] map-height map-width]
  (loop [px x py y]
    (let [nx (+ px dx)
          ny (+ py dy)]
      (if (and (>= nx 0) (< nx map-height)
               (>= ny 0) (< ny map-width))
        (recur nx ny)
        [px py]))))

(defn- bounce-vertical [at-top? map-height map-width]
  [(if at-top? (dec map-height) 0) (rand-int map-width)])

(defn- bounce-horizontal [at-left? map-height map-width]
  [(rand-int map-height) (if at-left? (dec map-width) 0)])

(def ^:private bounce-dispatch
  {:vertical bounce-vertical
   :horizontal bounce-horizontal})

(defn initial-state
  []
  {:turns-remaining units-config/satellite-turns})

(defn can-move-to?
  [_cell]
  true)

(defn needs-attention?
  [unit]
  (nil? (:target unit)))

(defn extend-target-to-boundary
  [unit-coords target-coords map-height map-width]
  (let [[ux uy] unit-coords
        [tx ty] target-coords
        dx (Integer/signum (- tx ux))
        dy (Integer/signum (- ty uy))]
    (extend-to-boundary unit-coords [dx dy] map-height map-width)))

(defn calculate-bounce-target
  [[x y] map-height map-width]
  (let [edges (cond-> []
                (= x 0)                (conj [:vertical true])
                (= x (dec map-height)) (conj [:vertical false])
                (= y 0)                (conj [:horizontal true])
                (= y (dec map-width))  (conj [:horizontal false]))]
    (if (empty? edges)
      [x y]
      (let [[edge-type near-origin?] (rand-nth edges)]
        ((bounce-dispatch edge-type) near-origin? map-height map-width)))))

(defn move-one-step
  "Returns {:pos new-pos :world-updates [[path value] ...]} — caller applies updates."
  [[x y] world]
  (let [cell (get-in world [x y])
        unit (:contents cell)
        target (:target unit)]
    (if-not target
      {:pos [x y] :world-updates []}
      (let [map-height (count world)
            map-width (count (first world))
            [tx ty] target
            at-target? (and (= x tx) (= y ty))]
        (if at-target?
          (let [new-target (calculate-bounce-target [x y] map-height map-width)
                updated-unit (assoc unit :target new-target)]
            {:pos [x y]
             :world-updates [[[x y :contents] updated-unit]]})
          (let [dx (Integer/signum (- tx x))
                dy (Integer/signum (- ty y))
                new-pos [(+ x dx) (+ y dy)]]
            {:pos new-pos
             :world-updates [[[x y :contents] nil]
                             [(conj new-pos :contents) unit]]}))))))
