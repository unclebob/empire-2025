(ns empire.game-mechanics.movement.movement-pathing
  (:require [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.movement.map-utils :as map-utils]))

(defn next-step-pos [pos target]
  (let [[x y] pos
        [tx ty] target
        dx (Integer/signum (- tx x))
        dy (Integer/signum (- ty y))]
    [(+ x dx) (+ y dy)]))

(defn chebyshev-distance
  "Returns the Chebyshev (chessboard) distance between two positions."
  [[x1 y1] [x2 y2]]
  (max (Math/abs (- x2 x1)) (Math/abs (- y2 y1))))

(defn can-move-to?
  "Returns true if the unit type can move to the given cell.
   Delegates terrain validation to unit-specific modules via dispatcher."
  [unit-type cell]
  (and cell
       (nil? (:contents cell))
       (dispatcher/can-move-to? unit-type cell)))

(defn diagonal? [dx dy]
  (and (not (zero? dx)) (not (zero? dy))))

(defn get-sidestep-directions
  "Returns candidate sidestep directions given the blocked direction.
   First returns the two diagonals adjacent to the blocked direction,
   then the two orthogonals perpendicular to it."
  [[dx dy]]
  (cond
    (diagonal? dx dy)  [[dx 0] [0 dy] [(- dx) dy] [dx (- dy)]]
    (zero? dy)         [[dx 1] [dx -1] [0 1] [0 -1]]
    :else              [[1 dy] [-1 dy] [1 0] [-1 0]]))

(defn- simulate-path
  "Simulates n moves from start-pos toward target, returning the final position.
   Returns nil if the first move is invalid."
  [start-pos target unit-type n current-map]
  (loop [pos start-pos
         remaining n]
    (if (or (zero? remaining) (= pos target))
      pos
      (let [next-pos (next-step-pos pos target)
            next-cell (get-in (map-utils/resolve-map-source current-map) next-pos)]
        (if (can-move-to? unit-type next-cell)
          (recur next-pos (dec remaining))
          pos)))))

(defn find-best-sidestep
  "Finds the best sidestep direction using 4-round look-ahead.
   Returns the position to sidestep to, or nil if no valid sidestep exists
   or if sidestepping doesn't get us closer to the target."
  [from-pos target unit-type blocked-dir current-map]
  (let [candidates (get-sidestep-directions blocked-dir)
        [fx fy] from-pos
        current-dist (chebyshev-distance from-pos target)
        valid-sidesteps
        (for [[sdx sdy] candidates
              :let [sidestep-pos [(+ fx sdx) (+ fy sdy)]
                    sidestep-cell (get-in (map-utils/resolve-map-source current-map) sidestep-pos)]
              :when (can-move-to? unit-type sidestep-cell)
              :let [final-pos (simulate-path sidestep-pos target unit-type 3 current-map)
                    final-dist (chebyshev-distance final-pos target)]]
          {:pos sidestep-pos :dist final-dist})]
    (when (seq valid-sidesteps)
      (let [best-dist (apply min (map :dist valid-sidesteps))
            best-options (filter #(= (:dist %) best-dist) valid-sidesteps)]
        (when (< best-dist current-dist)
          (:pos (rand-nth best-options)))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:11:44.995093-05:00", :module-hash "-739494555", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-486554360"} {:id "defn/next-step-pos", :kind "defn", :line 5, :end-line 10, :hash "-710299676"} {:id "defn/chebyshev-distance", :kind "defn", :line 12, :end-line 15, :hash "2102745561"} {:id "defn/can-move-to?", :kind "defn", :line 17, :end-line 23, :hash "546259425"} {:id "defn/diagonal?", :kind "defn", :line 25, :end-line 26, :hash "170011665"} {:id "defn/get-sidestep-directions", :kind "defn", :line 28, :end-line 36, :hash "-153256158"} {:id "defn-/simulate-path", :kind "defn-", :line 38, :end-line 50, :hash "-1866349737"} {:id "defn/find-best-sidestep", :kind "defn", :line 52, :end-line 72, :hash "1154656588"}]}
;; clj-mutate-manifest-end
