(ns empire.game-mechanics.movement.satellite-impl
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.visibility :as visibility]))

(defn update-game-map!
  [f & args]
  (apply sa/update-world! f args))

(defn current-world
  []
  (sa/current-world))

(defn extend-to-boundary
  [[x y] [dx dy] map-height map-width]
  (loop [px x py y]
    (let [nx (+ px dx)
          ny (+ py dy)]
      (if (and (>= nx 0) (< nx map-height)
               (>= ny 0) (< ny map-width))
        (recur nx ny)
        [px py]))))

(defn calculate-satellite-target
  [unit-coords target-coords]
  (let [[ux uy] unit-coords
        [tx ty] target-coords
        dx (Integer/signum (- tx ux))
        dy (Integer/signum (- ty uy))
        world (current-world)
        map-height (count world)
        map-width (count (first world))]
    (extend-to-boundary unit-coords [dx dy] map-height map-width)))

(defn opposite-row [x map-height]
  (if (zero? x) (dec map-height) 0))

(defn opposite-col [y map-width]
  (if (zero? y) (dec map-width) 0))

(defn target-on-opposite-row [x map-height map-width]
  [(opposite-row x map-height) (rand-int map-width)])

(defn target-on-opposite-col [y map-height map-width]
  [(rand-int map-height) (opposite-col y map-width)])

(defn boundary-type [[x y] map-height map-width]
  (let [h (if (or (zero? x) (= x (dec map-height))) 1 0)
        v (if (or (zero? y) (= y (dec map-width))) 1 0)]
    (get {[1 1] :corner [1 0] :row [0 1] :col} [h v])))

(defn pick-corner-target [x y map-height map-width]
  (if (zero? (rand-int 2))
    (target-on-opposite-row x map-height map-width)
    (target-on-opposite-col y map-height map-width)))

(defn calculate-new-satellite-target
  [[x y] map-height map-width]
  (case (boundary-type [x y] map-height map-width)
    :corner (pick-corner-target x y map-height map-width)
    :row (target-on-opposite-row x map-height map-width)
    :col (target-on-opposite-col y map-height map-width)
    [x y]))

(defn blocked-cell?
  [cell]
  (or (= :city (:type cell))
      (some? (:contents cell))))

(defn in-bounds?
  [[x y] map-height map-width]
  (and (>= x 0) (< x map-height) (>= y 0) (< y map-width)))

(defn find-open-cell
  [[nx ny] [dx dy] map-height map-width]
  (loop [cx nx cy ny]
    (let [pos [cx cy]]
      (cond
        (not (in-bounds? pos map-height map-width)) {:hit-edge true}
        (blocked-cell? (get-in (current-world) pos)) (recur (+ cx dx) (+ cy dy))
        :else {:dest pos}))))

(defn move-satellite-to!
  [from dest satellite]
  (update-game-map! assoc-in (conj from :contents) nil)
  (update-game-map! assoc-in (conj dest :contents) satellite)
  (visibility/update-cell-visibility from (:owner satellite))
  (visibility/update-cell-visibility dest (:owner satellite))
  dest)

(defn bounce-move
  [bounce-direction-fn pos satellite world map-height map-width]
  (when-let [new-dir (bounce-direction-fn pos map-height map-width)]
    (let [[x y] pos
          [bx by] new-dir
          dest [(+ x bx) (+ y by)]
          updated (assoc satellite :direction new-dir)]
      (when (and (in-bounds? dest map-height map-width)
                 (not (blocked-cell? (get-in world dest))))
        {:dest dest :satellite updated}))))

(defn straight-move-action
  [nx ny direction map-height map-width]
  (if (in-bounds? [nx ny] map-height map-width)
    (let [{:keys [dest hit-edge]} (find-open-cell [nx ny] direction map-height map-width)]
      (cond
        dest {:kind :move :dest dest}
        hit-edge {:kind :bounce}
        :else {:kind :stay}))
    {:kind :bounce}))

(defn move-satellite-straight
  [bounce-direction-fn [x y]]
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
        (if-let [{:keys [dest satellite]} (bounce-move bounce-direction-fn
                                                        [x y]
                                                        satellite
                                                        world
                                                        map-height
                                                        map-width)]
          (move-satellite-to! [x y] dest satellite)
          [x y])

        [x y]))))

(defn move-satellite
  [bounce-direction-fn [x y]]
  (let [world (current-world)
        cell (get-in world [x y])
        satellite (:contents cell)]
    (if (:direction satellite)
      (move-satellite-straight bounce-direction-fn [x y])
      (let [target (:target satellite)]
        (if-not target
          [x y]
          (let [map-height (count world)
                map-width (count (first world))
                [tx ty] target
                at-target? (and (= x tx) (= y ty))]
            (if at-target?
              (let [new-target (calculate-new-satellite-target [x y] map-height map-width)
                    updated-satellite (assoc satellite :target new-target)]
                (update-game-map! assoc-in [x y :contents] updated-satellite)
                (visibility/update-cell-visibility [x y] (:owner satellite))
                [x y])
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
;; {:version 1, :tested-at "2026-03-27T01:26:21.545597-05:00", :module-hash "1125470330", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "1361079119"} {:id "defn/update-game-map!", :kind "defn", :line 5, :end-line 7, :hash "1484461537"} {:id "defn/current-world", :kind "defn", :line 9, :end-line 11, :hash "-1099101758"} {:id "defn/extend-to-boundary", :kind "defn", :line 13, :end-line 21, :hash "148767333"} {:id "defn/calculate-satellite-target", :kind "defn", :line 23, :end-line 32, :hash "1796796084"} {:id "defn/opposite-row", :kind "defn", :line 34, :end-line 35, :hash "673010459"} {:id "defn/opposite-col", :kind "defn", :line 37, :end-line 38, :hash "-1648502411"} {:id "defn/target-on-opposite-row", :kind "defn", :line 40, :end-line 41, :hash "-14779067"} {:id "defn/target-on-opposite-col", :kind "defn", :line 43, :end-line 44, :hash "1289820126"} {:id "defn/boundary-type", :kind "defn", :line 46, :end-line 49, :hash "786501016"} {:id "defn/pick-corner-target", :kind "defn", :line 51, :end-line 54, :hash "-1510145388"} {:id "defn/calculate-new-satellite-target", :kind "defn", :line 56, :end-line 62, :hash "-889882695"} {:id "defn/blocked-cell?", :kind "defn", :line 64, :end-line 67, :hash "1044356496"} {:id "defn/in-bounds?", :kind "defn", :line 69, :end-line 71, :hash "-1306676454"} {:id "defn/find-open-cell", :kind "defn", :line 73, :end-line 80, :hash "1361778132"} {:id "defn/move-satellite-to!", :kind "defn", :line 82, :end-line 88, :hash "530758615"} {:id "defn/bounce-move", :kind "defn", :line 90, :end-line 99, :hash "4022779"} {:id "defn/straight-move-action", :kind "defn", :line 101, :end-line 109, :hash "947347066"} {:id "defn/move-satellite-straight", :kind "defn", :line 111, :end-line 136, :hash "1463161510"} {:id "defn/move-satellite", :kind "defn", :line 138, :end-line 178, :hash "1636052399"}]}
;; clj-mutate-manifest-end
