(ns empire.computer.lake-naval
  "Lake-specific naval behavior: retreat from shore and park as sentry."
  (:require [empire.computer.movement :as computer-movement]))

(def ^:private neighbor-offsets
  [[-1 -1] [-1 0] [-1 1]
   [0 -1]          [0 1]
   [1 -1]  [1 0]  [1 1]])

(defn- in-bounds?
  [world [r c]]
  (and (<= 0 r) (< r (count world))
       (<= 0 c) (< c (count (first world)))))

(defn- matching-neighbors
  [pos world pred]
  (for [[dr dc] neighbor-offsets
        :let [n [(+ (first pos) dr) (+ (second pos) dc)]]
        :when (and (in-bounds? world n)
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
  (for [[dr dc] neighbor-offsets
        :let [n [(+ (first pos) dr) (+ (second pos) dc)]]
        :when (sea-passable? world lake-cells-set start n)]
    n))

(defn retreat-step-from-shore
  "Returns first step toward nearest deep-water lake cell, or nil."
  [world lake-cells-set start]
  (when (contains? lake-cells-set start)
    (if (deep-water? world start)
      nil
      (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [start []])
             visited #{start}]
        (if (empty? queue)
          nil
          (let [[current path] (peek queue)
                rest-queue (pop queue)]
            (if (and (not= current start) (deep-water? world current))
              (first path)
              (let [nbrs (remove visited (neighbors* world lake-cells-set start current))
                    next-visited (into visited nbrs)
                    next-queue (reduce (fn [q n] (conj q [n (conj path n)]))
                                       rest-queue
                                       nbrs)]
                (recur next-queue next-visited)))))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:57:50.252217-05:00", :module-hash "-426536300", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "557526847"} {:id "def/neighbor-offsets", :kind "def", :line 5, :end-line 8, :hash "-1254756339"} {:id "defn-/in-bounds?", :kind "defn-", :line 10, :end-line 13, :hash "-541133760"} {:id "defn-/matching-neighbors", :kind "defn-", :line 15, :end-line 21, :hash "1628731295"} {:id "form/4/defonce", :kind "defonce", :line 23, :end-line 23, :hash "-765703590"} {:id "defn/lake-cells", :kind "defn", :line 25, :end-line 36, :hash "-91240742"} {:id "defn-/in-lake?", :kind "defn-", :line 38, :end-line 40, :hash "1182847924"} {:id "defn-/adjacent-land-or-city?", :kind "defn-", :line 42, :end-line 46, :hash "-1617224619"} {:id "defn/deep-water?", :kind "defn", :line 48, :end-line 50, :hash "-284089372"} {:id "defn-/sea-passable?", :kind "defn-", :line 52, :end-line 60, :hash "-1378176541"} {:id "defn-/neighbors*", :kind "defn-", :line 62, :end-line 67, :hash "-1742308839"} {:id "defn/retreat-step-from-shore", :kind "defn", :line 69, :end-line 88, :hash "1972536003"}]}
;; clj-mutate-manifest-end
