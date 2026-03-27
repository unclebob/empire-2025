(ns empire.game-mechanics.movement.wake-conditions.fighter
  (:require [empire.config.core :as config]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.movement.movement-pathing :as pathing]))

(defn- map-data
  [current-map]
  (map-utils/resolve-map-source current-map))

(defn- hostile-city? [next-cell]
  (and (= (:type next-cell) :city)
       (config/hostile-city? (:city-status next-cell))))

(defn- friendly-city-in-range?
  [pos max-dist current-map]
  (let [[px py] pos
        world (map-data current-map)
        height (count world)
        width (count (first world))]
    (some (fn [[i j]]
            (let [cell (get-in world [i j])]
              (and (= (:type cell) :city)
                   (= (:city-status cell) :player)
                   (<= (max (abs (- i px)) (abs (- j py))) max-dist))))
          (for [i (range height) j (range width)] [i j]))))

(defn- friendly-city? [cell]
  (and (= (:type cell) :city)
       (= (:city-status cell) :player)))

(defn- friendly-carrier? [carrier unit]
  (and (= (:type carrier) :carrier)
       (= (:owner carrier) (:owner unit))))

(defn- target-is-reachable-friendly-city? [unit final-pos fuel current-map]
  (when-let [target (:target unit)]
    (let [world (map-data current-map)
          [tx ty] target
          [fx fy] final-pos
          target-cell (get-in world target)
          target-contents (:contents target-cell)
          distance (max (abs (- tx fx)) (abs (- ty fy)))]
      (or (and (friendly-city? target-cell)
               (<= distance fuel))
          ;; Carrier may be moving away, so account for chase:
          ;; fuel needed = distance * fighter-speed / (fighter-speed - carrier-speed)
          ;; = distance * 8 / 6 = distance * 4/3
          (and (friendly-carrier? target-contents unit)
               (<= (* distance 4/3) fuel))))))

(defn- landing-site-on-path-status [unit target next-pos next-cell]
  (cond
    (nil? next-pos) :blocked
    (hostile-city? next-cell) :blocked
    (friendly-city? next-cell) :landing-site
    (friendly-carrier? (:contents next-cell) unit) :landing-site
    (= next-pos target) :blocked
    :else :continue))

(defn- reachable-landing-site-on-path?
  [unit final-pos fuel current-map]
  (when-let [target (:target unit)]
    (loop [pos final-pos
           remaining-fuel fuel]
      (when (pos? remaining-fuel)
        (let [next-pos (pathing/next-step-pos pos target)
              next-cell (get-in (map-data current-map) next-pos)]
          (case (landing-site-on-path-status unit target next-pos next-cell)
            :landing-site true
            :blocked false
            (recur next-pos (dec remaining-fuel))))))))

(defn- build-fighter-checks [unit final-pos current-map]
  (let [world (map-data current-map)
        dest-cell (get-in world final-pos)
        entering-city? (= (:type dest-cell) :city)
        friendly-city? (= (:city-status dest-cell) :player)
        hostile-city? (and entering-city? (not friendly-city?))
        fuel (:fuel unit config/fighter-fuel)
        low-fuel? (<= fuel 1)
        bingo-fuel? (and (<= fuel (quot config/fighter-fuel 4))
                         (friendly-city-in-range? final-pos fuel current-map)
                         (not (target-is-reachable-friendly-city? unit final-pos fuel current-map))
                         (not (reachable-landing-site-on-path? unit final-pos fuel current-map)))]
    [[hostile-city?  {:wake? true :reason :fighter-shot-down :shot-down? true}]
     [entering-city? {:wake? true :reason :fighter-landed-and-refueled :refuel? true}]
     [low-fuel?      {:wake? true :reason :fighter-out-of-fuel}]
     [bingo-fuel?    {:wake? true :reason :fighter-bingo}]]))

(defn wake-check [unit _from-pos final-pos current-map]
  (let [checks (build-fighter-checks unit final-pos current-map)]
    (some (fn [[pred result]] (when pred result)) checks)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:33:21.407717-05:00", :module-hash "-1476559965", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "346476271"} {:id "defn-/map-data", :kind "defn-", :line 6, :end-line 8, :hash "406913368"} {:id "defn-/hostile-city?", :kind "defn-", :line 10, :end-line 12, :hash "1644532547"} {:id "defn-/friendly-city-in-range?", :kind "defn-", :line 14, :end-line 25, :hash "-15153908"} {:id "defn-/friendly-city?", :kind "defn-", :line 27, :end-line 29, :hash "798434623"} {:id "defn-/friendly-carrier?", :kind "defn-", :line 31, :end-line 33, :hash "-1325282601"} {:id "defn-/target-is-reachable-friendly-city?", :kind "defn-", :line 35, :end-line 49, :hash "468885002"} {:id "defn-/landing-site-on-path-status", :kind "defn-", :line 51, :end-line 58, :hash "-941817352"} {:id "defn-/reachable-landing-site-on-path?", :kind "defn-", :line 60, :end-line 71, :hash "-1551364575"} {:id "defn-/build-fighter-checks", :kind "defn-", :line 73, :end-line 88, :hash "1568246404"} {:id "defn/wake-check", :kind "defn", :line 90, :end-line 92, :hash "-2136016964"}]}
;; clj-mutate-manifest-end
