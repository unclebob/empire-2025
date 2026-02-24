;; mutation-tested: 2026-02-24
(ns empire.computer.land-objectives
  "Land objective detection using flood-fill on fog-of-war map.
   Implements VMS Empire style continent recognition that respects unexplored territory."
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.movement.map-utils :as map-utils]))

;; Cache for continent flood-fill results.
;; Maps position -> continent-set. Cleared each round.
(def continent-cache (atom {}))

(defn clear-continent-cache!
  "Clears the continent cache. Called at the start of each round."
  []
  (reset! continent-cache {}))

(defn- get-terrain
  "Returns terrain type for a cell: :land, :sea, :city, or :unexplored."
  [cell]
  (cond
    (nil? cell) :unexplored
    (= :city (:type cell)) :land  ; cities count as land for continent purposes
    :else (:type cell)))

(defn- flood-fill-continent-uncached
  "Flood-fill from start-pos to find all connected land cells on computer-map.
   Marks unexplored cells adjacent to continent but does NOT expand through them.
   Returns a set of positions that are part of this continent (including adjacent unexplored)."
  [start-pos]
  (let [comp-map @atoms/computer-map
        height (count comp-map)
        width (when (pos? height) (count (first comp-map)))]
    (when (and (pos? height) (pos? width))
      (loop [frontier #{start-pos}
             visited #{}
             continent #{}]
        (if (empty? frontier)
          continent
          (let [pos (first frontier)
                rest-frontier (disj frontier pos)]
            (if (visited pos)
              (recur rest-frontier visited continent)
              (let [[r c] pos
                    cell (get-in comp-map pos)
                    terrain (get-terrain cell)]
                (cond
                  ;; Off map
                  (or (neg? r) (neg? c) (>= r height) (>= c width))
                  (recur rest-frontier (conj visited pos) continent)

                  ;; Sea - not part of continent
                  (= terrain :sea)
                  (recur rest-frontier (conj visited pos) continent)

                  ;; Unexplored - mark as part of continent but don't expand
                  (= terrain :unexplored)
                  (recur rest-frontier
                         (conj visited pos)
                         (conj continent pos))

                  ;; Land/City - part of continent, expand to neighbors
                  :else
                  (let [neighbors (for [[dr dc] map-utils/neighbor-offsets]
                                    [(+ r dr) (+ c dc)])
                        new-frontier (into rest-frontier
                                           (remove visited neighbors))]
                    (recur new-frontier
                           (conj visited pos)
                           (conj continent pos))))))))))))

(defn flood-fill-continent
  "Cached flood-fill from start-pos. Returns a set of positions.
   Caches the result for all positions in the continent so subsequent
   lookups from any position on the same continent are O(1)."
  [start-pos]
  (if-let [cached (get @continent-cache start-pos)]
    cached
    (when-let [continent (flood-fill-continent-uncached start-pos)]
      ;; Cache result for all positions in the continent
      (let [cache-entries (zipmap continent (repeat continent))]
        (swap! continent-cache merge cache-entries))
      continent)))

(defn scan-continent
  "Scan a continent (set of positions) and return counts of items of interest."
  [continent-positions]
  (let [comp-map @atoms/computer-map
        game-map @atoms/game-map]
    (reduce
     (fn [counts pos]
       (let [comp-cell (get-in comp-map pos)
             game-cell (get-in game-map pos)
             terrain (get-terrain comp-cell)]
         (cond-> counts
           ;; Count unexplored
           (= terrain :unexplored)
           (update :unexplored (fnil inc 0))

           ;; Count land cells (size)
           (= terrain :land)
           (update :size (fnil inc 0))

           ;; Count cities by owner
           (= :city (:type comp-cell))
           (cond->
             (= :computer (:city-status comp-cell))
             (update :computer-cities (fnil inc 0))

             (= :player (:city-status comp-cell))
             (update :player-cities (fnil inc 0))

             (= :free (:city-status comp-cell))
             (update :free-cities (fnil inc 0)))

           ;; Count units by owner
           (and (:contents comp-cell) (= :computer (:owner (:contents comp-cell))))
           (update :computer-units (fnil inc 0))

           (and (:contents comp-cell) (= :player (:owner (:contents comp-cell))))
           (update :player-units (fnil inc 0)))))
     {:unexplored 0 :size 0
      :computer-cities 0 :player-cities 0 :free-cities 0
      :computer-units 0 :player-units 0}
     continent-positions)))

(defn has-land-objective?
  "Returns true if continent has unexplored territory or attackable cities."
  [continent-counts]
  (or (pos? (:unexplored continent-counts 0))
      (pos? (:free-cities continent-counts 0))
      (pos? (:player-cities continent-counts 0))))

(defn find-all-objectives-on-continent
  "Returns all attackable cities and unexplored cells on the continent."
  [continent-positions]
  (let [comp-map @atoms/computer-map]
    (filter (fn [pos]
              (let [cell (get-in comp-map pos)]
                (or (nil? cell)
                    (and (= :city (:type cell))
                         (#{:free :player} (:city-status cell))))))
            continent-positions)))

(defn find-nearest-on-continent
  "Find the nearest position on the continent matching the predicate."
  [start-pos continent-positions pred]
  (let [comp-map @atoms/computer-map
        candidates (filter (fn [pos]
                            (let [cell (get-in comp-map pos)]
                              (pred cell pos)))
                          continent-positions)]
    (when (seq candidates)
      (apply min-key #(core/distance start-pos %) candidates))))

(defn find-unexplored-on-continent
  "Find nearest unexplored cell on the continent."
  [start-pos continent-positions]
  (find-nearest-on-continent start-pos continent-positions
                             (fn [cell _pos] (nil? cell))))

(defn find-free-city-on-continent
  "Find nearest free city on the continent."
  [start-pos continent-positions]
  (find-nearest-on-continent start-pos continent-positions
                             (fn [cell _pos]
                               (and (= :city (:type cell))
                                    (= :free (:city-status cell))))))

(defn find-player-city-on-continent
  "Find nearest player city on the continent."
  [start-pos continent-positions]
  (find-nearest-on-continent start-pos continent-positions
                             (fn [cell _pos]
                               (and (= :city (:type cell))
                                    (= :player (:city-status cell))))))
