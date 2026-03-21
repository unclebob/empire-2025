(ns empire.computer.fighter.exploration
  "Fighter exploration: sorties, drone operations, unexplored-cell scoring."
  (:require [empire.state.api :as sa]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.movement :as computer-movement]
            [empire.computer.fighter.movement :as fm]))

(def ^:private compass-directions
  "All 8 compass directions as [dr dc] vectors."
  [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])

(defn count-unexplored-neighbors
  "Count neighbors of pos that are unexplored on computer-map."
  [pos]
  (let [computer-map (sa/read-state :computer-map)
        [r c] pos
        height (count computer-map)
        width (count (first computer-map))]
    (count (filter (fn [[dr dc]]
                     (let [nr (+ r dr) nc (+ c dc)]
                       (and (>= nr 0) (< nr height)
                            (>= nc 0) (< nc width)
                            (nil? (get-in computer-map [nr nc])))))
                   compass-directions))))

(defn count-unexplored-along-direction
  "Project n steps from start in direction [dr dc], count unexplored cells
   (including their visibility neighbors) along the ray."
  [start direction n]
  (let [computer-map (sa/read-state :computer-map)
        height (count computer-map)
        width (count (first computer-map))
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

(defn nearest-unexplored-distance
  "Return the distance from pos to the nearest unexplored cell on computer-map."
  [pos]
  (let [computer-map (sa/read-state :computer-map)
        height (count computer-map)
        width (count (first computer-map))]
    (when-let [distances (seq (for [r (range height)
                                    c (range width)
                                    :when (nil? (get-in computer-map [r c]))]
                                (fm/distance-to pos [r c])))]
      (apply min distances))))

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

(defn- simple-explore-move
  "Direct move to best-pos. Returns {:pos best-pos :hops 1} or nil."
  [pos best-pos]
  (when (action-resolution/move-unit-to pos best-pos)
    (computer-movement/update-cell-visibility! pos :computer)
    (computer-movement/update-cell-visibility! best-pos :computer)
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
        (when-let [hop (fm/hop-over-friendly pos endpoint)]
          (fm/execute-hop pos hop))
        (simple-explore-move pos best-pos)))))

(defn- return-to-landing-zone
  [pos unit]
  (let [remaining (dec (:explore-steps-remaining unit))
        landing-site (:explore-landing-site unit)]
    (sa/update-world! assoc-in
                      (conj pos :contents :explore-steps-remaining) remaining)
    (when (<= remaining 0)
      (sa/update-world! update-in (conj pos :contents)
                        assoc :flight-mode :regular
                        :flight-target-site landing-site))
    (computer-movement/update-cell-visibility! pos :computer)))

(defn sortie-step
  "One outbound sortie step for explore or drone flights.
   At zero, switches to :regular mode targeting the planned landing site.
   Returns {:pos p :hops n} or nil."
  [pos unit]
  (let [endpoint (:flight-target-site unit)]
    (when-let [{:keys [pos hops]} (explore-move-step pos endpoint)]
      (return-to-landing-zone pos unit)
      {:pos pos :hops hops})))

(defn explore-step
  "Compatibility wrapper for explore sorties."
  [pos unit]
  (sortie-step pos unit))

(defn drone-step
  "Compatibility wrapper for drone sorties."
  [pos unit]
  (sortie-step pos unit))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:57:39.982213-05:00", :module-hash "-2063118544", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "407161768"} {:id "def/compass-directions", :kind "def", :line 8, :end-line 10, :hash "1521788393"} {:id "defn/count-unexplored-neighbors", :kind "defn", :line 12, :end-line 25, :hash "552922855"} {:id "defn-/count-unexplored-along-direction", :kind "defn-", :line 27, :end-line 50, :hash "-915357597"} {:id "defn/best-exploration-heading", :kind "defn", :line 52, :end-line 61, :hash "-915439036"} {:id "defn-/select-best-explore-target", :kind "defn-", :line 63, :end-line 71, :hash "1629491586"} {:id "defn-/simple-explore-move", :kind "defn-", :line 73, :end-line 80, :hash "464458532"} {:id "defn-/explore-move-step", :kind "defn-", :line 82, :end-line 93, :hash "1272575161"} {:id "defn/explore-step", :kind "defn", :line 95, :end-line 109, :hash "324745724"} {:id "defn/drone-step", :kind "defn", :line 111, :end-line 115, :hash "1856648356"}]}
;; clj-mutate-manifest-end
