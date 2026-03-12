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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:59:52.641178-05:00", :module-hash "-1291594515", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "1895315784"} {:id "defn-/extend-to-boundary", :kind "defn-", :line 4, :end-line 12, :hash "-1387405322"} {:id "defn-/bounce-vertical", :kind "defn-", :line 14, :end-line 15, :hash "559788624"} {:id "defn-/bounce-horizontal", :kind "defn-", :line 17, :end-line 18, :hash "-149276283"} {:id "def/bounce-dispatch", :kind "def", :line 20, :end-line 22, :hash "1945317151"} {:id "defn/initial-state", :kind "defn", :line 24, :end-line 26, :hash "1766263462"} {:id "defn/can-move-to?", :kind "defn", :line 28, :end-line 30, :hash "1923566926"} {:id "defn/needs-attention?", :kind "defn", :line 32, :end-line 34, :hash "-505480771"} {:id "defn/extend-target-to-boundary", :kind "defn", :line 36, :end-line 42, :hash "175957141"} {:id "defn/calculate-bounce-target", :kind "defn", :line 44, :end-line 54, :hash "-1142182432"} {:id "defn/move-one-step", :kind "defn", :line 56, :end-line 78, :hash "543569292"}]}
;; clj-mutate-manifest-end
