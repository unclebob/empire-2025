;; mutation-tested: 2026-02-25
(ns empire.units.satellite
  (:require [empire.application.ports.world-store :as world-ports]))

(def ^:private world-store-fn
  (delay
    (try
      (requiring-resolve 'empire.adapters.state.atoms/world-store)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(defn- update-game-map!
  [f & args]
  (when-let [resolver @world-store-fn]
    (let [store (resolver)
          world (world-ports/load-world store)]
      (world-ports/save-world! store (apply f world args)))))

(defn- current-world
  []
  (if-let [resolver @world-store-fn]
    (world-ports/load-world (resolver))
    []))

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

(defn- bounce-vertical [at-top? map-height map-width]
  [(if at-top? (dec map-height) 0) (rand-int map-width)])

(defn- bounce-horizontal [at-left? map-height map-width]
  [(rand-int map-height) (if at-left? (dec map-width) 0)])

(def ^:private bounce-dispatch
  {:vertical bounce-vertical
   :horizontal bounce-horizontal})

(defn calculate-bounce-target
  "Calculates new target on opposite boundary when satellite reaches edge.
   At corners, randomly chooses one of the two opposite boundaries."
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
  "Moves a satellite one step toward its target.
   When at target (on boundary), calculates new target on opposite boundary.
   Satellites without a target don't move - they wait for user input.
  Returns new position."
  [[x y]]
  (let [world (current-world)
        cell (get-in world [x y])
        satellite (:contents cell)
        target (:target satellite)]
    (if-not target
      [x y]
      (let [map-height (count world)
            map-width (count (first world))
            [tx ty] target
            at-target? (and (= x tx) (= y ty))]
        (if at-target?
          (let [new-target (calculate-bounce-target [x y] map-height map-width)
                updated-satellite (assoc satellite :target new-target)]
            (update-game-map! assoc-in [x y :contents] updated-satellite)
            [x y])
          (let [dx (Integer/signum (- tx x))
                dy (Integer/signum (- ty y))
                new-pos [(+ x dx) (+ y dy)]]
            (update-game-map! assoc-in [x y :contents] nil)
            (update-game-map! assoc-in (conj new-pos :contents) satellite)
            new-pos))))))
