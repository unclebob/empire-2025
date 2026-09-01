(ns empire.game-mechanics.visibility.territory
  (:require [empire.game-mechanics.visibility.core :as core]
            [empire.game-mechanics.spatial.neighbors :as neighbors]))

(defn- army-country-id
  [unit]
  (when (and (= :computer (:owner unit))
             (= :army (:type unit)))
    (:country-id unit)))

(defn- claimed-country-id
  [cell]
  (let [cell-cid (:country-id cell)
        unit-cid (army-country-id (:contents cell))]
    (when (or cell-cid unit-cid)
      (min (or cell-cid Long/MAX_VALUE)
           (or unit-cid Long/MAX_VALUE)))))

(defn- unclaimed-visible-land?
  [visible-map pos]
  (let [cell (get-in visible-map pos)]
    (and (= :land (:type cell))
         (nil? (:country-id cell))
         (nil? (claimed-country-id cell)))))

(defn- connected-visible-land-component
  [visible-map start valid-positions]
  (loop [frontier [start]
         seen #{}
         component #{}]
    (if-let [pos (peek frontier)]
      (let [frontier (pop frontier)]
        (if (seen pos)
          (recur frontier seen component)
          (let [neighbors (filter valid-positions
                                  (neighbors/get-matching-neighbors pos visible-map neighbors/neighbor-offsets
                                                                   (constantly true)))]
            (recur (into frontier neighbors)
                   (conj seen pos)
                   (conj component pos)))))
      component)))

(defn- adjacent-claimed-cids
  [visible-map positions]
  (->> positions
       (mapcat #(neighbors/get-matching-neighbors % visible-map neighbors/neighbor-offsets
                                                  (comp some? claimed-country-id)))
       (map #(claimed-country-id (get-in visible-map %)))
       (remove nil?)
       set))

(defn- lowest-adjacent-cid
  [candidate-cids]
  (when (seq candidate-cids)
    (apply min candidate-cids)))

(defn stamp-exposed-territory!
  [visible-map-source exposed-positions]
  (let [visible-map (core/read-visible-map visible-map-source)
        exposed-land (set (filter #(unclaimed-visible-land? visible-map %) exposed-positions))
        visible-unclaimed-land (set (for [row (range (count visible-map))
                                          col (range (count (first visible-map)))
                                          :let [pos [row col]]
                                          :when (unclaimed-visible-land? visible-map pos)]
                                      pos))]
    (loop [remaining exposed-land]
      (when-let [start (first remaining)]
        (let [component (connected-visible-land-component visible-map start visible-unclaimed-land)
              candidate-cids (adjacent-claimed-cids visible-map component)]
          (when-let [claim-cid (lowest-adjacent-cid candidate-cids)]
            (doseq [pos component]
              (core/update-game-map! assoc-in (conj pos :country-id) claim-cid)
              (core/update-visible-map! visible-map-source assoc-in (conj pos :country-id) claim-cid)))
          (when (> (count candidate-cids) 1)
            (let [lowest-cid (apply min candidate-cids)]
              (doseq [other-cid (disj candidate-cids lowest-cid)]
                (core/merge-continents! lowest-cid other-cid))))
          (recur (reduce disj remaining component)))))))

(defn refresh-visible-map!
  [owner]
  (let [game-map (core/current-world)
        visible-map-key (core/visible-map-key-for owner)
        current-map (core/read-runtime-state visible-map-key)
        visible-map (if (and (vector? current-map)
                             (= (count current-map) (count game-map))
                             (= (count (first current-map))
                                (count (first game-map))))
                      current-map
                      (vec (repeat (count game-map)
                                   (vec (repeat (count (first game-map)) nil)))))]
    (when-let [updated (core/update-combatant-map-state visible-map owner game-map)]
      (core/write-runtime-state! visible-map-key updated))))

(defn update-combatant-map
  [visible-map-source owner]
  (when-let [visible-map (core/read-visible-map visible-map-source)]
    (let [game-map (core/current-world)
          updated (core/update-combatant-map-state visible-map owner game-map)]
      (core/write-visible-map! visible-map-source updated))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:59:57.360576-05:00", :module-hash "-807042950", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "395748854"} {:id "defn-/army-country-id", :kind "defn-", :line 5, :end-line nil, :hash "1827912408"} {:id "defn-/claimed-country-id", :kind "defn-", :line 11, :end-line nil, :hash "1079422157"} {:id "defn-/unclaimed-visible-land?", :kind "defn-", :line 19, :end-line nil, :hash "-1719014480"} {:id "defn-/connected-visible-land-component", :kind "defn-", :line 26, :end-line nil, :hash "-1798189151"} {:id "defn-/adjacent-claimed-cids", :kind "defn-", :line 43, :end-line nil, :hash "1222079328"} {:id "defn-/lowest-adjacent-cid", :kind "defn-", :line 52, :end-line nil, :hash "621399243"} {:id "defn/stamp-exposed-territory!", :kind "defn", :line 57, :end-line nil, :hash "-1516205335"} {:id "defn/refresh-visible-map!", :kind "defn", :line 80, :end-line nil, :hash "1116774847"} {:id "defn/update-combatant-map", :kind "defn", :line 95, :end-line nil, :hash "-199905816"}]}
;; clj-mutate-manifest-end
