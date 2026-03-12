(ns empire.game-mechanics.movement.satellite
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.visibility :as visibility]))

(defn- update-game-map!
  [f & args]
  (apply sa/update-world! f args))

(defn- current-world
  []
  (sa/current-world))

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

(defn calculate-satellite-target
  "For satellites, extends the target to the map boundary in the direction of travel."
  [unit-coords target-coords]
  (let [[ux uy] unit-coords
        [tx ty] target-coords
        dx (Integer/signum (- tx ux))
        dy (Integer/signum (- ty uy))
        world (current-world)
        map-height (count world)
        map-width (count (first world))]
    (extend-to-boundary unit-coords [dx dy] map-height map-width)))

(defn- opposite-row [x map-height]
  (if (zero? x) (dec map-height) 0))

(defn- opposite-col [y map-width]
  (if (zero? y) (dec map-width) 0))

(defn- target-on-opposite-row [x map-height map-width]
  [(opposite-row x map-height) (rand-int map-width)])

(defn- target-on-opposite-col [y map-height map-width]
  [(rand-int map-height) (opposite-col y map-width)])

(defn- boundary-type [[x y] map-height map-width]
  (let [h (if (or (zero? x) (= x (dec map-height))) 1 0)
        v (if (or (zero? y) (= y (dec map-width))) 1 0)]
    (get {[1 1] :corner [1 0] :row [0 1] :col} [h v])))

(defn- pick-corner-target [x y map-height map-width]
  (if (zero? (rand-int 2))
    (target-on-opposite-row x map-height map-width)
    (target-on-opposite-col y map-height map-width)))

(defn- calculate-new-satellite-target
  "Calculates a new target on the opposite boundary when satellite reaches its target.
   At corners, randomly chooses one of the two opposite boundaries."
  [[x y] map-height map-width]
  (case (boundary-type [x y] map-height map-width)
    :corner (pick-corner-target x y map-height map-width)
    :row (target-on-opposite-row x map-height map-width)
    :col (target-on-opposite-col y map-height map-width)
    [x y]))

(defn- blocked-cell?
  "Returns true if a cell contains a city or another unit."
  [cell]
  (or (= :city (:type cell))
      (some? (:contents cell))))

(defn- in-bounds?
  [[x y] map-height map-width]
  (and (>= x 0) (< x map-height) (>= y 0) (< y map-width)))

(defn- find-open-cell
  "Scans from [nx ny] in direction [dx dy] for first non-blocked cell.
   Returns {:dest [x y]} or {:hit-edge true} if blocked chain runs off-map."
  [[nx ny] [dx dy] map-height map-width]
  (loop [cx nx cy ny]
    (let [pos [cx cy]]
      (cond
        (not (in-bounds? pos map-height map-width)) {:hit-edge true}
        (blocked-cell? (get-in (current-world) pos)) (recur (+ cx dx) (+ cy dy))
        :else {:dest pos}))))

(defn- bounce-direction
  "Returns a random direction vector pointing away from the map edge.
   Filters the 8 compass directions to those that move inward from the edge."
  [[x y] map-height map-width]
  (let [at-top? (zero? x)
        at-bottom? (= x (dec map-height))
        at-left? (zero? y)
        at-right? (= y (dec map-width))
        directions [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]
        valid (filter (fn [[dx dy]]
                        (let [nx (+ x dx) ny (+ y dy)]
                          (and (if at-top? (>= dx 0) true)
                               (if at-bottom? (<= dx 0) true)
                               (if at-left? (>= dy 0) true)
                               (if at-right? (<= dy 0) true)
                               (>= nx 0) (< nx map-height)
                               (>= ny 0) (< ny map-width))))
                      directions)]
    (when (seq valid)
      (rand-nth (vec valid)))))

(defn- move-satellite-to!
  [from dest satellite]
  (update-game-map! assoc-in (conj from :contents) nil)
  (update-game-map! assoc-in (conj dest :contents) satellite)
  (visibility/update-cell-visibility from (:owner satellite))
  (visibility/update-cell-visibility dest (:owner satellite))
  dest)

(defn- bounce-move
  [pos satellite world map-height map-width]
  (when-let [new-dir (bounce-direction pos map-height map-width)]
    (let [[x y] pos
          [bx by] new-dir
          dest [(+ x bx) (+ y by)]
          updated (assoc satellite :direction new-dir)]
      (when (and (in-bounds? dest map-height map-width)
                 (not (blocked-cell? (get-in world dest))))
        {:dest dest :satellite updated}))))

(defn- straight-move-action
  [nx ny direction map-height map-width]
  (if (in-bounds? [nx ny] map-height map-width)
    (let [{:keys [dest hit-edge]} (find-open-cell [nx ny] direction map-height map-width)]
      (cond
        dest {:kind :move :dest dest}
        hit-edge {:kind :bounce}
        :else {:kind :stay}))
    {:kind :bounce}))

(defn- move-satellite-straight
  "Moves a computer satellite one step in its fixed direction.
   Bounces off map edges by picking a new random direction away from the edge."
  [[x y]]
  (let [world (current-world)
        cell (get-in world [x y])
        satellite (:contents cell)
        [dx dy] (:direction satellite)
        nx (+ x dx)
        ny (+ y dy)
        map-height (count world)
        map-width (count (first world))]
    (let [{:keys [kind dest]} (straight-move-action nx ny [dx dy] map-height map-width)]
      (case kind
        :move
        (move-satellite-to! [x y] dest satellite)

        :bounce
        (if-let [{:keys [dest satellite]} (bounce-move [x y] satellite world map-height map-width)]
          (move-satellite-to! [x y] dest satellite)
          [x y])

        [x y]))))

(defn move-satellite
  "Moves a satellite one step toward its target.
   Computer satellites with :direction move in a fixed straight line.
   When at target (always on boundary), calculates new target on opposite boundary.
  Satellites without a target don't move - they wait for user input."
  [[x y]]
  (let [world (current-world)
        cell (get-in world [x y])
        satellite (:contents cell)]
    (if (:direction satellite)
      (move-satellite-straight [x y])
      (let [target (:target satellite)]
        (if-not target
          ;; No target - satellite doesn't move, waits for user input
          [x y]
          (let [map-height (count world)
                map-width (count (first world))
                [tx ty] target
                at-target? (and (= x tx) (= y ty))]
            (if at-target?
              ;; At target (on boundary) - bounce to opposite side
              (let [new-target (calculate-new-satellite-target [x y] map-height map-width)
                    updated-satellite (assoc satellite :target new-target)]
                (update-game-map! assoc-in [x y :contents] updated-satellite)
                (visibility/update-cell-visibility [x y] (:owner satellite))
                [x y])
              ;; Not at target - move toward it, skipping blocked cells
              (let [dx (Integer/signum (- tx x))
                    dy (Integer/signum (- ty y))
                    next-pos [(+ x dx) (+ y dy)]]
                (let [{:keys [dest hit-edge]} (find-open-cell next-pos [dx dy] map-height map-width)]
                  (cond
                    dest
                    (do (update-game-map! assoc-in [x y :contents] nil)
                        (update-game-map! assoc-in (conj dest :contents) satellite)
                        (visibility/update-cell-visibility [x y] (:owner satellite))
                        (visibility/update-cell-visibility dest (:owner satellite))
                        dest)

                    hit-edge
                    (let [edge (extend-to-boundary [x y] [dx dy] map-height map-width)
                          new-target (calculate-new-satellite-target edge map-height map-width)
                          updated-satellite (assoc satellite :target new-target)]
                      (update-game-map! assoc-in [x y :contents] updated-satellite)
                      (visibility/update-cell-visibility [x y] (:owner satellite))
                      [x y])

                    :else [x y]))))))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:01:46.543045-05:00", :module-hash "-1207297436", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-1245406970"} {:id "defn-/update-game-map!", :kind "defn-", :line 5, :end-line 7, :hash "1805137569"} {:id "defn-/current-world", :kind "defn-", :line 9, :end-line 11, :hash "-640438772"} {:id "defn-/extend-to-boundary", :kind "defn-", :line 13, :end-line 22, :hash "974468210"} {:id "defn/calculate-satellite-target", :kind "defn", :line 24, :end-line 34, :hash "1999800491"} {:id "defn-/opposite-row", :kind "defn-", :line 36, :end-line 37, :hash "1729144679"} {:id "defn-/opposite-col", :kind "defn-", :line 39, :end-line 40, :hash "1218751045"} {:id "defn-/target-on-opposite-row", :kind "defn-", :line 42, :end-line 43, :hash "1076132881"} {:id "defn-/target-on-opposite-col", :kind "defn-", :line 45, :end-line 46, :hash "-1367885660"} {:id "defn-/boundary-type", :kind "defn-", :line 48, :end-line 51, :hash "105730157"} {:id "defn-/pick-corner-target", :kind "defn-", :line 53, :end-line 56, :hash "-919321911"} {:id "defn-/calculate-new-satellite-target", :kind "defn-", :line 58, :end-line 66, :hash "1106150626"} {:id "defn-/blocked-cell?", :kind "defn-", :line 68, :end-line 72, :hash "-1819699903"} {:id "defn-/in-bounds?", :kind "defn-", :line 74, :end-line 76, :hash "-768521146"} {:id "defn-/find-open-cell", :kind "defn-", :line 78, :end-line 87, :hash "1772835370"} {:id "defn-/bounce-direction", :kind "defn-", :line 89, :end-line 108, :hash "1823438433"} {:id "defn-/move-satellite-to!", :kind "defn-", :line 110, :end-line 116, :hash "-802385206"} {:id "defn-/bounce-move", :kind "defn-", :line 118, :end-line 127, :hash "-2098485205"} {:id "defn-/straight-move-action", :kind "defn-", :line 129, :end-line 137, :hash "607806084"} {:id "defn-/move-satellite-straight", :kind "defn-", :line 139, :end-line 161, :hash "-124621734"} {:id "defn/move-satellite", :kind "defn", :line 163, :end-line 210, :hash "69543404"}]}
;; clj-mutate-manifest-end
