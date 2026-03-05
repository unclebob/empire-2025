;; mutation-tested: 2026-02-25
(ns empire.units.impl.satellite
  (:require [empire.application.state-access :as sa]
            [empire.units.config :as units-config]
            [empire.units.satellite :as satellite]))

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

(defmethod satellite/initial-state :default
  []
  {:turns-remaining units-config/satellite-turns})

(defmethod satellite/can-move-to? :default
  [_cell]
  true)

(defmethod satellite/needs-attention? :default
  [unit]
  (nil? (:target unit)))

(defmethod satellite/extend-target-to-boundary :default
  [unit-coords target-coords map-height map-width]
  (let [[ux uy] unit-coords
        [tx ty] target-coords
        dx (Integer/signum (- tx ux))
        dy (Integer/signum (- ty uy))]
    (extend-to-boundary unit-coords [dx dy] map-height map-width)))

(defmethod satellite/calculate-bounce-target :default
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

(defmethod satellite/move-one-step :default
  [[x y]]
  (let [world (sa/current-world)
        cell (get-in world [x y])
        unit (:contents cell)
        target (:target unit)]
    (if-not target
      [x y]
      (let [map-height (count world)
            map-width (count (first world))
            [tx ty] target
            at-target? (and (= x tx) (= y ty))]
        (if at-target?
          (let [new-target (satellite/calculate-bounce-target [x y] map-height map-width)
                updated-unit (assoc unit :target new-target)]
            (sa/update-world! assoc-in [x y :contents] updated-unit)
            [x y])
          (let [dx (Integer/signum (- tx x))
                dy (Integer/signum (- ty y))
                new-pos [(+ x dx) (+ y dy)]]
            (sa/update-world! assoc-in [x y :contents] nil)
            (sa/update-world! assoc-in (conj new-pos :contents) unit)
            new-pos))))))

(defn load-methods!
  []
  true)
