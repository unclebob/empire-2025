(ns empire.movement.lakes
  "Helpers for classifying small sea components as lakes."
  (:require [empire.movement.map-utils :as map-utils]))

(defn- in-bounds?
  [rows cols [r c]]
  (and (<= 0 r) (< r rows) (<= 0 c) (< c cols)))

(defn- sea-cell?
  [the-map pos]
  (= :sea (get-in the-map (conj pos :type))))

(defn- bfs-sea-component
  [the-map start rows cols]
  (loop [queue (conj clojure.lang.PersistentQueue/EMPTY start)
         visited #{start}
         component #{start}]
    (if (empty? queue)
      component
      (let [current (peek queue)
            rest-queue (pop queue)
            sea-neighbors (for [[dr dc] map-utils/neighbor-offsets
                                :let [n [(+ (first current) dr) (+ (second current) dc)]]
                                :when (and (in-bounds? rows cols n)
                                           (not (contains? visited n))
                                           (sea-cell? the-map n))]
                            n)
            next-visited (into visited sea-neighbors)]
        (recur (into rest-queue sea-neighbors)
               next-visited
               (into component sea-neighbors))))))

(defn- explored-cell?
  [cell]
  (and cell (not= :unexplored (:type cell))))

(defn- component-fully-explored?
  [the-map component rows cols]
  (every? (fn [[r c]]
            (every? (fn [[dr dc]]
                      (let [n [(+ r dr) (+ c dc)]]
                        (or (not (in-bounds? rows cols n))
                            (explored-cell? (get-in the-map n)))))
                    map-utils/neighbor-offsets))
          component))

(defn lake-cells
  "Returns sea cells in connected components with size <= lake-max-cells."
  [the-map lake-max-cells]
  (if (or (nil? the-map) (<= (or lake-max-cells 0) 0))
    #{}
    (let [rows (count the-map)
          cols (count (first the-map))]
      (loop [remaining (set (for [r (range rows)
                                  c (range cols)
                                  :when (sea-cell? the-map [r c])]
                              [r c]))
             lakes #{}]
        (if (empty? remaining)
          lakes
          (let [start (first remaining)
                component (bfs-sea-component the-map start rows cols)
                next-remaining (reduce disj remaining component)]
            (recur next-remaining
                   (if (and (<= (count component) lake-max-cells)
                            (component-fully-explored? the-map component rows cols))
                     (into lakes component)
                     lakes))))))))
