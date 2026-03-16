(ns empire.game-mechanics.movement.movement-resolution-decisions)

(defn normalize-target
  "Normalizes a movement target, falling back to the origin when invalid."
  [clamp-to-map-bounds from-coords target-coords]
  (if (and (vector? target-coords)
           (= 2 (count target-coords))
           (every? number? target-coords))
    (clamp-to-map-bounds target-coords)
    from-coords))

(defn blocked-by-friendly?
  "Returns true when the next cell contains a friendly blocker."
  [unit next-cell]
  (let [blocker (:contents next-cell)]
    (and blocker
         (= (:owner blocker) (:owner unit)))))

(defn blocked-by-enemy?
  "Returns true when the next cell contains an enemy blocker."
  [unit next-cell]
  (let [blocker (:contents next-cell)]
    (and blocker
         (not= (:owner blocker) (:owner unit)))))

(defn can-attack-enemy?
  "Returns true when the mover may legally attack the enemy in the next cell."
  [blocked-by-enemy-fn terrain-passable? unit next-cell]
  (and (blocked-by-enemy-fn unit next-cell)
       (not= :satellite (:type unit))
       (not= :satellite (get-in next-cell [:contents :type]))
       (terrain-passable? (:type unit) (dissoc next-cell :contents))))

(defn should-sidestep-city?
  "Returns true when city terrain should trigger sidestep logic."
  [unit next-cell next-pos]
  (and (= :city (:type next-cell))
       (or (and (= :army (:type unit))
                (= :player (:city-status next-cell)))
           (and (= :fighter (:type unit))
                (not= next-pos (:target unit))))))

(defn movement-action
  "Classifies the next movement-resolution action."
  [{:keys [sidestep-city?
           blocked?
           blocked-by-friendly?
           can-attack-enemy?
           woke?]}]
  (cond
    sidestep-city? :sidestep-city
    (and blocked? blocked-by-friendly?) :sidestep-friendly
    (and blocked? can-attack-enemy?) :combat
    woke? :woke
    :else :normal))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T10:46:00.021298-05:00", :module-hash "-1515168762", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-146474826"} {:id "defn/normalize-target", :kind "defn", :line 3, :end-line 10, :hash "1545249409"} {:id "defn/blocked-by-friendly?", :kind "defn", :line 12, :end-line 17, :hash "-1563023550"} {:id "defn/blocked-by-enemy?", :kind "defn", :line 19, :end-line 24, :hash "1978022855"} {:id "defn/can-attack-enemy?", :kind "defn", :line 26, :end-line 32, :hash "1468265873"} {:id "defn/should-sidestep-city?", :kind "defn", :line 34, :end-line 47, :hash "1403440618"} {:id "defn/movement-action", :kind "defn", :line 49, :end-line 61, :hash "-1387063043"}]}
;; clj-mutate-manifest-end
