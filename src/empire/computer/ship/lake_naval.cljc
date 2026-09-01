(ns empire.computer.ship.lake-naval
  "Lake-specific naval behavior: retreat from shore and park as sentry."
  (:require [empire.computer.shared.grid :as grid]
            [empire.computer.shared.movement :as computer-movement]
            [empire.state.api :as sa]))

(defn- matching-neighbors
  [pos world pred]
  (for [[dr dc] grid/neighbor-offsets
        :let [n [(+ (first pos) dr) (+ (second pos) dc)]]
        :when (and (grid/in-bounds? world n)
                   (pred (get-in world n)))]
    n))

(defonce ^:private lake-cache* (atom {:computer-map nil :limit nil :cells #{}}))

(defn lake-cells
  [computer-map lake-max-cells]
  (if (or (nil? computer-map) (<= (or lake-max-cells 0) 0))
    #{}
    (let [{:keys [previous-map limit cells]}
          (assoc @lake-cache* :previous-map (:computer-map @lake-cache*))]
        (if (and (identical? previous-map computer-map)
               (= limit lake-max-cells))
        cells
        (let [computed (computer-movement/lake-cells computer-map lake-max-cells)]
          (reset! lake-cache* {:computer-map computer-map :limit lake-max-cells :cells computed})
          computed)))))

(defn known-lake-cells
  []
  (lake-cells (sa/read-state :computer-map)
              (sa/read-state :lake-max-cells)))

(defn- in-lake?
  [lake-cells-set pos]
  (contains? lake-cells-set pos))

(defn- adjacent-land-or-city?
  [world pos]
  (some (fn [n]
          (#{:land :city} (get-in world (conj n :type))))
        (matching-neighbors pos world some?)))

(defn deep-water?
  [world pos]
  (not (adjacent-land-or-city? world pos)))

(defn- sea-passable?
  [world lake-cells-set start pos]
  (let [cell (get-in world pos)
        occupant (:contents cell)]
    (and cell
         (= :sea (:type cell))
         (contains? lake-cells-set pos)
         (or (= pos start)
             (nil? occupant)))))

(defn- neighbors*
  [world lake-cells-set start pos]
  (for [[dr dc] grid/neighbor-offsets
        :let [n [(+ (first pos) dr) (+ (second pos) dc)]]
        :when (sea-passable? world lake-cells-set start n)]
    n))

(defn- deep-water-arrived?
  [world start current]
  (and (not= current start) (deep-water? world current)))

(defn- search-deep-water-step
  [world lake-cells-set start]
  (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [start []])
         visited #{start}]
    (when-not (empty? queue)
      (let [[current path] (peek queue)
            rest-queue (pop queue)]
        (if (deep-water-arrived? world start current)
          (first path)
          (let [nbrs (remove visited (neighbors* world lake-cells-set start current))]
            (recur (reduce (fn [q n] (conj q [n (conj path n)])) rest-queue nbrs)
                   (into visited nbrs))))))))

(defn retreat-step-from-shore
  "Returns first step toward nearest deep-water lake cell, or nil."
  [world lake-cells-set start]
  (when (contains? lake-cells-set start)
    (when-not (deep-water? world start)
      (search-deep-water-step world lake-cells-set start))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:56:45.959017-05:00", :module-hash "-1265238577", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "710301658"} {:id "defn-/matching-neighbors", :kind "defn-", :line 7, :end-line nil, :hash "-597024471"} {:id "form/2/defonce", :kind "defonce", :line 15, :end-line nil, :hash "-765703590"} {:id "defn/lake-cells", :kind "defn", :line 17, :end-line nil, :hash "-91240742"} {:id "defn/known-lake-cells", :kind "defn", :line 30, :end-line nil, :hash "691833352"} {:id "defn-/in-lake?", :kind "defn-", :line 35, :end-line nil, :hash "1182847924"} {:id "defn-/adjacent-land-or-city?", :kind "defn-", :line 39, :end-line nil, :hash "-1617224619"} {:id "defn/deep-water?", :kind "defn", :line 45, :end-line nil, :hash "-284089372"} {:id "defn-/sea-passable?", :kind "defn-", :line 49, :end-line nil, :hash "-1378176541"} {:id "defn-/neighbors*", :kind "defn-", :line 59, :end-line nil, :hash "-763770683"} {:id "defn-/deep-water-arrived?", :kind "defn-", :line 66, :end-line nil, :hash "-1179361641"} {:id "defn-/search-deep-water-step", :kind "defn-", :line 70, :end-line nil, :hash "817229032"} {:id "defn/retreat-step-from-shore", :kind "defn", :line 83, :end-line nil, :hash "1311098201"}]}
;; clj-mutate-manifest-end
