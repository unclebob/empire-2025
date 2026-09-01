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

(defn- landing-site?
  [unit next-cell]
  (or (friendly-city? next-cell)
      (friendly-carrier? (:contents next-cell) unit)))

(defn- landing-site-on-path-status [unit target next-pos next-cell]
  (cond
    (or (nil? next-pos) (hostile-city? next-cell) (= next-pos target)) :blocked
    (landing-site? unit next-cell) :landing-site
    :else :continue))

(defn- scan-path-for-landing
  [unit target pos fuel current-map]
  (loop [pos pos
         remaining-fuel fuel]
    (when (pos? remaining-fuel)
      (let [next-pos (pathing/next-step-pos pos target)
            next-cell (get-in (map-data current-map) next-pos)]
        (case (landing-site-on-path-status unit target next-pos next-cell)
          :landing-site true
          :blocked false
          (recur next-pos (dec remaining-fuel)))))))

(defn- reachable-landing-site-on-path?
  [unit final-pos fuel current-map]
  (when-let [target (:target unit)]
    (scan-path-for-landing unit target final-pos fuel current-map)))

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
;; {:version 1, :tested-at "2026-09-01T16:05:39.56539-05:00", :module-hash "226814959", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "346476271"} {:id "defn-/map-data", :kind "defn-", :line 6, :end-line nil, :hash "406913368"} {:id "defn-/hostile-city?", :kind "defn-", :line 10, :end-line nil, :hash "1644532547"} {:id "defn-/friendly-city-in-range?", :kind "defn-", :line 14, :end-line nil, :hash "-15153908"} {:id "defn-/friendly-city?", :kind "defn-", :line 27, :end-line nil, :hash "798434623"} {:id "defn-/friendly-carrier?", :kind "defn-", :line 31, :end-line nil, :hash "-1325282601"} {:id "defn-/target-is-reachable-friendly-city?", :kind "defn-", :line 35, :end-line nil, :hash "468885002"} {:id "defn-/landing-site?", :kind "defn-", :line 51, :end-line nil, :hash "2096481169"} {:id "defn-/landing-site-on-path-status", :kind "defn-", :line 56, :end-line nil, :hash "-880027193"} {:id "defn-/scan-path-for-landing", :kind "defn-", :line 62, :end-line nil, :hash "585486318"} {:id "defn-/reachable-landing-site-on-path?", :kind "defn-", :line 74, :end-line nil, :hash "-589150384"} {:id "defn-/build-fighter-checks", :kind "defn-", :line 79, :end-line nil, :hash "1568246404"} {:id "defn/wake-check", :kind "defn", :line 96, :end-line nil, :hash "-2136016964"}]}
;; clj-mutate-manifest-end
