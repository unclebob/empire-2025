(ns empire.game-mechanics.movement.lakes
  "Helpers for classifying small sea components as lakes."
  (:require [empire.game-mechanics.movement.map-utils :as map-utils]))

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

(defn- no-lakes?
  [the-map lake-max-cells]
  (or (nil? the-map) (<= (or lake-max-cells 0) 0)))

(defn- lake-component?
  [the-map component rows cols lake-max-cells]
  (and (<= (count component) lake-max-cells)
       (component-fully-explored? the-map component rows cols)))

(defn lake-cells
  "Returns sea cells in connected components with size <= lake-max-cells."
  [the-map lake-max-cells]
  (if (no-lakes? the-map lake-max-cells)
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
                   (if (lake-component? the-map component rows cols lake-max-cells)
                     (into lakes component)
                     lakes))))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:07:52.247709-05:00", :module-hash "798164675", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "678409883"} {:id "defn-/in-bounds?", :kind "defn-", :line 5, :end-line 7, :hash "-1113606377"} {:id "defn-/sea-cell?", :kind "defn-", :line 9, :end-line 11, :hash "452144751"} {:id "defn-/bfs-sea-component", :kind "defn-", :line 13, :end-line 31, :hash "1754859198"} {:id "defn-/explored-cell?", :kind "defn-", :line 33, :end-line 35, :hash "-1906120449"} {:id "defn-/component-fully-explored?", :kind "defn-", :line 37, :end-line 45, :hash "1234831981"} {:id "defn/lake-cells", :kind "defn", :line 47, :end-line 68, :hash "685693919"}]}
;; clj-mutate-manifest-end
