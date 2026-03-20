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
  (let [return-distance (fuel-to-return pos)]
    (<= fuel (+ return-distance 2))))

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
