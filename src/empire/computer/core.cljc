;; mutation-tested: 2026-03-03
(ns empire.computer.core
  "Contract namespace for computer AI core multimethods and pure helpers.")

(def neighbor-offsets
  [[-1 -1] [-1 0] [-1 1]
   [0 -1]          [0 1]
   [1 -1]  [1 0]  [1 1]])

(defn neighbors-in-map
  [the-map [r c]]
  (if (and (sequential? the-map) (seq the-map) (sequential? (first the-map)))
    (let [height (count the-map)
          width (count (first the-map))]
      (for [[dr dc] neighbor-offsets
            :let [nr (+ r dr)
                  nc (+ c dc)]
            :when (and (<= 0 nr) (< nr height)
                       (<= 0 nc) (< nc width))]
        [nr nc]))
    []))

(defn adjacent?
  "Returns true if pos1 and pos2 are adjacent (including diagonally)."
  [pos1 pos2]
  (let [[r1 c1] pos1
        [r2 c2] pos2
        dr (Math/abs (- r2 r1))
        dc (Math/abs (- c2 c1))]
    (and (<= dr 1) (<= dc 1) (not (and (zero? dr) (zero? dc))))))

(defmulti get-neighbors (fn [& _] :default))
(defmulti distance (fn [& _] :default))
(defmulti chebyshev-distance (fn [& _] :default))
(defmulti attackable-target? (fn [& _] :default))
(defmulti find-visible-cities (fn [& _] :default))
(defmulti move-toward (fn [& _] :default))
(defmulti adjacent-to-computer-unexplored? (fn [& _] :default))
(defmulti stamp-territory (fn [& _] :default))
(defmulti move-unit-to (fn [& _] :default))
(defmulti attempt-conquest-computer (fn [& _] :default))
(defmulti random-away-direction (fn [& _] :default))
(defmulti find-wakeable-sentries (fn [& _] :default))
(defmulti wake-nearby-sentries (fn [& _] :default))
(defmulti board-transport (fn [& _] :default))
(defmulti find-visible-player-units (fn [& _] :default))
(defmulti find-loading-transport (fn [& _] :default))
(defmulti find-adjacent-loading-transport (fn [& _] :default))
