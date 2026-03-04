;; mutation-tested: 2026-03-02
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
