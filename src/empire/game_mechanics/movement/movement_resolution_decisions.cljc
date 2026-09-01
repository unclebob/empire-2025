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

(defn- blocked-movement-action
  [{:keys [blocked-by-friendly? can-attack-enemy?]}]
  (cond
    blocked-by-friendly? :sidestep-friendly
    can-attack-enemy? :combat
    :else nil))

(defn- blocked-or-woke-action
  [{:keys [woke?] :as ctx}]
  (or (blocked-movement-action ctx)
      (if woke? :woke :normal)))

(defn movement-action
  "Classifies the next movement-resolution action."
  [{:keys [sidestep-city? blocked? woke?] :as ctx}]
  (cond
    sidestep-city? :sidestep-city
    blocked? (blocked-or-woke-action ctx)
    woke? :woke
    :else :normal))

(defn move-unit-phase
  [{:keys [ship-can-dock?]}]
  (if ship-can-dock?
    :dock
    :move))

(defn movement-result
  [result pos]
  {:result result :pos pos})

(defn combat-visibility-pos
  [unit next-pos combat-result]
  (when (and unit combat-result)
    {:pos next-pos
     :owner (:owner unit)}))

(defn target-unit-state
  [unit safe-target]
  (if (= (:target unit) safe-target)
    unit
    (assoc unit :target safe-target)))

(defn target-cell-state
  [cell safe-unit]
  (if (= safe-unit (:contents cell))
    cell
    (assoc cell :contents safe-unit)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:13:18.127839-05:00", :module-hash "-1581900343", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-146474826"} {:id "defn/normalize-target", :kind "defn", :line 3, :end-line 10, :hash "1545249409"} {:id "defn/blocked-by-friendly?", :kind "defn", :line 12, :end-line 17, :hash "-1563023550"} {:id "defn/blocked-by-enemy?", :kind "defn", :line 19, :end-line 24, :hash "1978022855"} {:id "defn/can-attack-enemy?", :kind "defn", :line 26, :end-line 32, :hash "1468265873"} {:id "defn/should-sidestep-city?", :kind "defn", :line 34, :end-line 41, :hash "-255452290"} {:id "defn/movement-action", :kind "defn", :line 43, :end-line 55, :hash "-1387063043"} {:id "defn/move-unit-phase", :kind "defn", :line 57, :end-line 61, :hash "1563863224"} {:id "defn/movement-result", :kind "defn", :line 63, :end-line 65, :hash "1624799954"} {:id "defn/combat-visibility-pos", :kind "defn", :line 67, :end-line 71, :hash "269641850"} {:id "defn/target-unit-state", :kind "defn", :line 73, :end-line 77, :hash "759128502"} {:id "defn/target-cell-state", :kind "defn", :line 79, :end-line 83, :hash "-1969774274"}]}
;; clj-mutate-manifest-end
