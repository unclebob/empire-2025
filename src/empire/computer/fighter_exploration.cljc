;; mutation-tested: 2026-02-27
(ns empire.computer.fighter-exploration
  "Fighter exploration: sorties, drone operations, unexplored-cell scoring."
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.movement.visibility :as visibility]
            [empire.computer.fighter-movement :as fm]))

(def ^:private compass-directions
  "All 8 compass directions as [dr dc] vectors."
  [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])

(defn count-unexplored-neighbors
  "Count neighbors of pos that are unexplored on computer-map."
  [pos]
  (let [computer-map @atoms/computer-map
        [r c] pos
        height (count @atoms/game-map)
        width (count (first @atoms/game-map))]
    (count (filter (fn [[dr dc]]
                     (let [nr (+ r dr) nc (+ c dc)]
                       (and (>= nr 0) (< nr height)
                            (>= nc 0) (< nc width)
                            (nil? (get-in computer-map [nr nc])))))
                   compass-directions))))

(defn- count-unexplored-along-direction
  "Project n steps from start in direction [dr dc], count unexplored cells
   (including their visibility neighbors) along the ray."
  [start direction n]
  (let [computer-map @atoms/computer-map
        game-map @atoms/game-map
        height (count game-map)
        width (count (first game-map))
        [dr dc] direction]
    (reduce (fn [total step]
              (let [pr (+ (first start) (* step dr))
                    pc (+ (second start) (* step dc))]
                (if (and (>= pr 0) (< pr height)
                         (>= pc 0) (< pc width))
                  (+ total
                     (count (filter (fn [[vr vc]]
                                      (let [nr (+ pr vr) nc (+ pc vc)]
                                        (and (>= nr 0) (< nr height)
                                             (>= nc 0) (< nc width)
                                             (nil? (get-in computer-map [nr nc])))))
                                    (conj compass-directions [0 0]))))
                  total)))
            0
            (range 1 (inc n)))))

(defn best-exploration-heading
  "Score all 8 compass directions by unexplored cell count, return best.
   Tie-break with rand-nth."
  [pos projection-length]
  (let [scored (map (fn [dir]
                      [dir (count-unexplored-along-direction pos dir projection-length)])
                    compass-directions)
        best-score (apply max (map second scored))
        best-dirs (map first (filter #(= best-score (second %)) scored))]
    (rand-nth (vec best-dirs))))

(defn- select-best-explore-target
  "Score passable neighbors by unexplored count, break ties by proximity."
  [passable endpoint]
  (let [scored (map (fn [n]
                      [n (count-unexplored-neighbors n) (fm/distance-to n endpoint)])
                    passable)
        best-unexplored (apply max (map second scored))
        at-best (filter #(= best-unexplored (second %)) scored)]
    (first (first (sort-by #(nth % 2) at-best)))))

(defn- land-after-hop
  "Executes hop landing at dest from pos. Returns {:pos dest :hops hops} or nil."
  [pos dest hops]
  (when (core/move-unit-to pos dest)
    (visibility/update-cell-visibility pos :computer)
    (visibility/update-cell-visibility dest :computer)
    (when (fm/consume-hop-fuel dest hops)
      (when (fm/consume-fighter-fuel dest)
        {:pos dest :hops hops}))))

(defn- explore-hop-over
  "Hops over friendly units in the direction from pos toward best-pos.
   Returns {:pos p :hops n} or nil."
  [pos best-pos]
  (let [[dr dc] (fm/direction-from pos best-pos)]
    (loop [sr (first best-pos) sc (second best-pos) hops 1]
      (let [next-pos [(+ sr dr) (+ sc dc)]]
        (when (fm/in-bounds? next-pos)
          (if-not (fm/occupied? next-pos)
            (land-after-hop pos next-pos (inc hops))
            (when (fm/friendly-occupied? next-pos)
              (recur (+ sr dr) (+ sc dc) (inc hops)))))))))

(defn- simple-explore-move
  "Direct move to best-pos. Returns {:pos best-pos :hops 1} or nil."
  [pos best-pos]
  (when (core/move-unit-to pos best-pos)
    (visibility/update-cell-visibility pos :computer)
    (visibility/update-cell-visibility best-pos :computer)
    (when (fm/consume-fighter-fuel best-pos)
      {:pos best-pos :hops 1})))

(defn- explore-move-step
  "Shared movement logic for sorties and drones.
   Score all passable neighbors by unexplored-neighbor count, break ties by
   proximity to endpoint. If the best is friendly-occupied, hop over it.
   Returns {:pos p :hops n} or nil."
  [pos endpoint]
  (let [passable (fm/get-passable-neighbors pos)]
    (when-let [best-pos (and (seq passable) (select-best-explore-target passable endpoint))]
      (if (fm/friendly-occupied? best-pos)
        (explore-hop-over pos best-pos)
        (simple-explore-move pos best-pos)))))

(defn explore-step
  "One outbound sortie step. Calls explore-move-step, decrements steps-remaining.
   At zero, switches to :regular mode targeting origin.
   Returns {:pos p :hops n} or nil."
  [pos unit]
  (let [endpoint (:flight-target-site unit)]
    (when-let [{:keys [pos hops]} (explore-move-step pos endpoint)]
      (let [remaining (dec (:explore-steps-remaining unit))]
        (swap! atoms/game-map assoc-in
               (conj pos :contents :explore-steps-remaining) remaining)
        (when (<= remaining 0)
          (swap! atoms/game-map update-in (conj pos :contents)
                 assoc :flight-mode :regular
                 :flight-target-site (:explore-origin unit))))
      {:pos pos :hops hops})))

(defn drone-step
  "One drone step. Delegates to explore-move-step.
   Returns {:pos p :hops n} or nil."
  [pos unit]
  (explore-move-step pos (:flight-target-site unit)))
