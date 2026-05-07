(ns empire.computer.shared.land-objectives
  "Land objective detection using flood-fill on fog-of-war map.
   Implements VMS Empire style continent recognition that respects unexplored territory."
  (:require [empire.computer.shared.grid :as grid]
            [empire.state.api :as sa]))

(def ^:private neighbor-offsets
  [[-1 -1] [-1 0] [-1 1]
   [0 -1]          [0 1]
   [1 -1]  [1 0]  [1 1]])

;; Cache for continent flood-fill results.
;; Maps position -> continent-set. Cleared each round.
(def continent-cache (atom {}))

(defn clear-continent-cache!
  []
  (reset! continent-cache {}))

(defn- get-terrain
  "Returns terrain type for a cell: :land, :sea, :city, or :unexplored."
  [cell]
  (cond
    (nil? cell) :unexplored
    (= :city (:type cell)) :land  ; cities count as land for continent purposes
    :else (:type cell)))

(defn- in-bounds?
  "Returns true if [r c] is within map dimensions."
  [[r c] height width]
  (and (>= r 0) (< r height) (>= c 0) (< c width)))

(defn- classify-terrain-step
  "Classifies a position during flood-fill and returns updated
   [frontier visited continent] triple."
  [pos comp-map height width rest-frontier visited continent]
  (if-not (in-bounds? pos height width)
    [rest-frontier (conj visited pos) continent]
    (let [terrain (get-terrain (get-in comp-map pos))]
      (cond
        (= terrain :sea)
        [rest-frontier (conj visited pos) continent]

        (= terrain :unexplored)
        [rest-frontier (conj visited pos) (conj continent pos)]

        :else
        (let [[r c] pos
              neighbors (for [[dr dc] neighbor-offsets]
                          [(+ r dr) (+ c dc)])]
          [(into rest-frontier (remove visited neighbors))
           (conj visited pos)
           (conj continent pos)])))))

(defn- flood-fill-continent-uncached
  "Flood-fill from start-pos to find all connected land cells on computer-map.
   Marks unexplored cells adjacent to continent but does NOT expand through them.
   Returns a set of positions that are part of this continent (including adjacent unexplored)."
  [start-pos]
  (let [comp-map (sa/read-state :computer-map)]
    (when (seq comp-map)
      (let [height (count comp-map)
            width (count (first comp-map))]
        (loop [frontier #{start-pos}
             visited #{}
             continent #{}]
        (if (empty? frontier)
          continent
          (let [pos (first frontier)
                rest-frontier (disj frontier pos)]
            (if (visited pos)
              (recur rest-frontier visited continent)
              (let [[nf nv nc] (classify-terrain-step
                                 pos comp-map height width
                                 rest-frontier visited continent)]
                (recur nf nv nc))))))))))

(defn flood-fill-continent
  [start-pos]
  (if-let [cached (get @continent-cache start-pos)]
    cached
    (when-let [continent (flood-fill-continent-uncached start-pos)]
      ;; Cache result for all positions in the continent
      (let [cache-entries (zipmap continent (repeat continent))]
        (swap! continent-cache merge cache-entries))
      continent)))

(defn continent-id
  [continent]
  (when (seq continent)
    (first (sort continent))))

(defn continent-id-for-pos
  [start-pos]
  (some-> start-pos
          flood-fill-continent
          continent-id))

(def ^:private city-status->key
  {:computer :computer-cities
   :player :player-cities
   :free :free-cities})

(def ^:private owner->key
  {:computer :computer-units
   :player :player-units})

(defn city-status-key
  [cell]
  (when (= :city (:type cell))
    (city-status->key (:city-status cell))))

(defn unit-owner-key
  [cell]
  (owner->key (:owner (:contents cell))))

(defn scan-continent
  [continent-positions]
  (let [comp-map (sa/read-state :computer-map)]
    (reduce
     (fn [counts pos]
       (let [comp-cell (get-in comp-map pos)
             terrain (get-terrain comp-cell)]
         (cond-> counts
           (= terrain :unexplored) (update :unexplored inc)
           (= terrain :land) (update :size inc)
           (city-status-key comp-cell) (update (city-status-key comp-cell) inc)
           (unit-owner-key comp-cell) (update (unit-owner-key comp-cell) inc))))
     {:unexplored 0 :size 0
      :computer-cities 0 :player-cities 0 :free-cities 0
      :computer-units 0 :player-units 0}
     continent-positions)))

(defn has-land-objective?
  [continent-counts]
  (or (pos? (:unexplored continent-counts 0))
      (pos? (:free-cities continent-counts 0))
      (pos? (:player-cities continent-counts 0))))

(defn find-all-objectives-on-continent
  [continent-positions]
  (let [comp-map (sa/read-state :computer-map)]
    (filter (fn [pos]
              (let [cell (get-in comp-map pos)]
                (or (nil? cell)
                    (and (= :city (:type cell))
                         (#{:free :player} (:city-status cell))))))
            continent-positions)))

(defn find-nearest-on-continent
  [start-pos continent-positions pred]
  (let [comp-map (sa/read-state :computer-map)
        candidates (filter (fn [pos]
                            (let [cell (get-in comp-map pos)]
                              (pred cell pos)))
                          continent-positions)]
    (when (seq candidates)
      (apply min-key #(grid/distance start-pos %) candidates))))

(defn find-unexplored-on-continent
  [start-pos continent-positions]
  (find-nearest-on-continent start-pos continent-positions
                             (fn [cell _pos] (nil? cell))))

(defn- find-city-on-continent
  [start-pos continent-positions city-status]
  (find-nearest-on-continent start-pos continent-positions
                             (fn [cell _pos]
                               (and (= :city (:type cell))
                                    (= city-status (:city-status cell))))))

(defn find-free-city-on-continent
  [start-pos continent-positions]
  (find-city-on-continent start-pos continent-positions :free))

(defn find-player-city-on-continent
  [start-pos continent-positions]
  (find-city-on-continent start-pos continent-positions :player))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:04:19.076576-05:00", :module-hash "1395705710", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "2053740120"} {:id "def/neighbor-offsets", :kind "def", :line 7, :end-line 10, :hash "-1254756339"} {:id "def/continent-cache", :kind "def", :line 14, :end-line 14, :hash "-436619865"} {:id "defn/clear-continent-cache!", :kind "defn", :line 16, :end-line 18, :hash "767341213"} {:id "defn-/get-terrain", :kind "defn-", :line 20, :end-line 26, :hash "-109508658"} {:id "defn-/in-bounds?", :kind "defn-", :line 28, :end-line 31, :hash "-1793006247"} {:id "defn-/classify-terrain-step", :kind "defn-", :line 33, :end-line 53, :hash "908359139"} {:id "defn-/flood-fill-continent-uncached", :kind "defn-", :line 55, :end-line 76, :hash "-1256310192"} {:id "defn/flood-fill-continent", :kind "defn", :line 78, :end-line 86, :hash "549471743"} {:id "defn/continent-id", :kind "defn", :line 88, :end-line 91, :hash "342550384"} {:id "defn/continent-id-for-pos", :kind "defn", :line 93, :end-line 97, :hash "-1055750031"} {:id "def/city-status->key", :kind "def", :line 99, :end-line 102, :hash "-702473700"} {:id "def/owner->key", :kind "def", :line 104, :end-line 106, :hash "1113494898"} {:id "defn/city-status-key", :kind "defn", :line 108, :end-line 111, :hash "-170815179"} {:id "defn/unit-owner-key", :kind "defn", :line 113, :end-line 115, :hash "427346267"} {:id "defn/scan-continent", :kind "defn", :line 117, :end-line 132, :hash "1495219321"} {:id "defn/has-land-objective?", :kind "defn", :line 134, :end-line 138, :hash "832328724"} {:id "defn/find-all-objectives-on-continent", :kind "defn", :line 140, :end-line 148, :hash "1542179921"} {:id "defn/find-nearest-on-continent", :kind "defn", :line 150, :end-line 158, :hash "2140188398"} {:id "defn/find-unexplored-on-continent", :kind "defn", :line 160, :end-line 163, :hash "189800203"} {:id "defn/find-free-city-on-continent", :kind "defn", :line 165, :end-line 170, :hash "-634529870"} {:id "defn/find-player-city-on-continent", :kind "defn", :line 172, :end-line 177, :hash "-771469015"}]}
;; clj-mutate-manifest-end
