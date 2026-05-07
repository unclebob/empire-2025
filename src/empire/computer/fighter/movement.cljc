(ns empire.computer.fighter.movement
  "Fighter movement primitives: combat, hopping, fuel management."
  (:require [empire.computer.fighter.movement-impl :as impl]))

(defn get-passable-neighbors
  [pos]
  (impl/get-passable-neighbors pos))

(defn occupied?
  [pos]
  (impl/occupied? pos))

(defn friendly-occupied?
  [pos]
  (impl/friendly-occupied? pos))

(defn direction-from
  [[r1 c1] [r2 c2]]
  (impl/direction-from [r1 c1] [r2 c2]))

(defn in-bounds?
  [[r c]]
  (impl/in-bounds? [r c]))

(defn hop-over-friendly
  [pos target]
  (impl/hop-over-friendly pos target))

(defn find-adjacent-enemy
  [pos]
  (impl/find-adjacent-enemy pos))

(defn attack-enemy
  [fighter-pos enemy-pos]
  (impl/attack-enemy fighter-pos enemy-pos))

(defn find-nearest-refueling-site
  [pos]
  (impl/find-nearest-refueling-site pos))

(defn distance-to
  [[r1 c1] [r2 c2]]
  (impl/distance-to [r1 c1] [r2 c2]))

(defn- fuel-to-return
  [pos]
  (if-let [site (find-nearest-refueling-site pos)]
    (distance-to pos site)
    999))

(defn should-return-to-refuel?
  [pos fuel]
  (impl/low-fuel-for-return? (fuel-to-return pos) fuel))

(def fighter-speed impl/fighter-speed)

(defn land-at-city
  [pos city-pos]
  (impl/land-at-city pos city-pos))

(defn consume-fighter-fuel
  [pos]
  (impl/consume-fighter-fuel pos))

(defn consume-hop-fuel
  [pos hops]
  (impl/consume-hop-fuel pos hops consume-fighter-fuel))

(defn execute-hop
  [from-pos {:keys [dest hops attack]}]
  (impl/execute-hop from-pos {:dest dest :hops hops :attack attack}))

(defn do-patrol
  [pos]
  (impl/do-patrol pos))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:01:45.400764-05:00", :module-hash "1705422979", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "1790435308"} {:id "defn/get-passable-neighbors", :kind "defn", :line 5, :end-line 7, :hash "-1252804139"} {:id "defn/occupied?", :kind "defn", :line 9, :end-line 11, :hash "1148960363"} {:id "defn/friendly-occupied?", :kind "defn", :line 13, :end-line 15, :hash "40619059"} {:id "defn/direction-from", :kind "defn", :line 17, :end-line 19, :hash "1755642553"} {:id "defn/in-bounds?", :kind "defn", :line 21, :end-line 23, :hash "-485554041"} {:id "defn/hop-over-friendly", :kind "defn", :line 25, :end-line 27, :hash "-1213433610"} {:id "defn/find-adjacent-enemy", :kind "defn", :line 29, :end-line 31, :hash "277060429"} {:id "defn/attack-enemy", :kind "defn", :line 33, :end-line 35, :hash "1772416857"} {:id "defn/find-nearest-refueling-site", :kind "defn", :line 37, :end-line 39, :hash "-1240693072"} {:id "defn/distance-to", :kind "defn", :line 41, :end-line 43, :hash "1512898993"} {:id "defn-/fuel-to-return", :kind "defn-", :line 45, :end-line 49, :hash "-1874606390"} {:id "defn/should-return-to-refuel?", :kind "defn", :line 51, :end-line 54, :hash "976392460"} {:id "def/fighter-speed", :kind "def", :line 56, :end-line 56, :hash "-753717379"} {:id "defn/land-at-city", :kind "defn", :line 58, :end-line 60, :hash "-1988784989"} {:id "defn/consume-fighter-fuel", :kind "defn", :line 62, :end-line 64, :hash "664639697"} {:id "defn/consume-hop-fuel", :kind "defn", :line 66, :end-line 68, :hash "1702954350"} {:id "defn/execute-hop", :kind "defn", :line 70, :end-line 72, :hash "2072958478"} {:id "defn/do-patrol", :kind "defn", :line 74, :end-line 76, :hash "-1212956782"}]}
;; clj-mutate-manifest-end
