(ns empire.game-mechanics.movement.wake-conditions
  (:require [empire.config.core :as config]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.movement.wake-conditions.pre-move :as pre-move]
            [empire.game-mechanics.movement.wake-conditions.post-move :as post-move]))

(defn friendly-city-in-range?
  "Returns true if there is a friendly city within max-dist cells."
  [pos max-dist current-map]
  (let [[px py] pos
        world (map-utils/resolve-map-source current-map)
        height (count world)
        width (count (first world))]
    (some (fn [[i j]]
            (let [cell (get-in world [i j])]
              (and (= (:type cell) :city)
                   (= (:city-status cell) :player)
                   (<= (max (abs (- i px)) (abs (- j py))) max-dist))))
          (for [i (range height) j (range width)] [i j]))))

(defn enemy-unit-visible?
  "Returns true if an enemy unit is within the unit's visibility radius."
  [unit pos current-map]
  (post-move/enemy-unit-visible? unit pos current-map))

(defn wake-before-move
  [unit next-cell]
  (pre-move/wake-before-move unit next-cell))

(defn wake-after-move
  [unit from-pos final-pos current-map]
  (post-move/wake-after-move unit from-pos final-pos current-map))

(defn near-hostile-city?
  [pos current-map]
  (post-move/near-hostile-city? pos current-map))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:32:07.800223-05:00", :module-hash "1167832280", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-2049180236"} {:id "defn/friendly-city-in-range?", :kind "defn", :line 7, :end-line 19, :hash "-1492505997"} {:id "defn/enemy-unit-visible?", :kind "defn", :line 21, :end-line 24, :hash "1802689446"} {:id "defn/wake-before-move", :kind "defn", :line 26, :end-line 28, :hash "1606535952"} {:id "defn/wake-after-move", :kind "defn", :line 30, :end-line 32, :hash "1829619743"} {:id "defn/near-hostile-city?", :kind "defn", :line 34, :end-line 36, :hash "-193007792"}]}
;; clj-mutate-manifest-end
