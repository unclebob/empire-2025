(ns empire.computer.navigation
  "Degree-based navigation for computer ships.
   Heading 0=N, 90=E, 180=S, 270=W."
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]))

(def ^:private sector-deltas
  "Maps sector index (0-7) to [dr dc] movement vector.
   Sector 0 = N, 1 = NE, 2 = E, ... 7 = NW."
  [[-1 0] [-1 1] [0 1] [1 1] [1 0] [1 -1] [0 -1] [-1 -1]])

(defn heading-to-delta
  "Converts degree heading to [dr dc] movement vector.
   Quantizes to nearest 45-degree sector."
  [degrees]
  (let [sector (mod (Math/round (/ (double degrees) 45.0)) 8)]
    (nth sector-deltas sector)))

(defn apply-heading
  "Returns next position [col row] from pos along heading.
   Heading deltas are [dr dc] (row-change, col-change);
   positions are [col row], so dc applies to first and dr to second."
  [pos heading]
  (let [[dr dc] (heading-to-delta heading)]
    [(+ (first pos) dc) (+ (second pos) dr)]))

(defn reflect-heading
  "Reflects heading off a surface with +-10 degree jitter.
   surface is :horizontal (top/bottom) or :vertical (left/right).
   Horizontal: flips vertical component (180-h). Vertical: flips horizontal component (360-h)."
  [heading surface]
  (let [reflected (case surface
                    :horizontal (mod (+ 540 (- heading)) 360)
                    :vertical (mod (- 360 heading) 360))
        jitter (- (rand-int 21) 10)]
    (mod (+ reflected jitter 360) 360)))

(defn heading-from-to
  "Computes heading in degrees from pos1 to pos2.
   Positions are [col row]. 0=N, 90=E, 180=S, 270=W."
  [[c1 r1] [c2 r2]]
  (let [dc (- c2 c1)
        dr (- r2 r1)]
    (if (and (zero? dc) (zero? dr))
      0
      (mod (+ (Math/toDegrees (Math/atan2 (double dc) (double (- dr)))) 360) 360))))

(defn is-explored-coast?
  "Returns true if pos is a sea cell adjacent to visible land on computer-map."
  [pos]
  (let [game-map @atoms/game-map
        cell (get-in game-map pos)]
    (and cell
         (= :sea (:type cell))
         (some (fn [neighbor]
                 (let [comp-cell (get-in @atoms/computer-map neighbor)]
                   (and comp-cell
                        (#{:land :city} (:type comp-cell)))))
               (core/get-neighbors pos)))))
