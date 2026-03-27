(ns empire.computer.army.assignment-decisions)

(defn assignable-armies
  [game-map]
  (for [i (range (count game-map))
        j (range (count (first game-map)))
        :let [cell (get-in game-map [i j])
              unit (:contents cell)]
        :when (and unit
                   (= :computer (:owner unit))
                   (= :army (:type unit))
                   (not= :coast-walk (:mode unit)))]
    {:pos [i j] :unit unit}))

(defn visible-target-cities
  [computer-map]
  (when (vector? computer-map)
    (for [i (range (count computer-map))
          j (range (count (first computer-map)))
          :let [cell (get-in computer-map [i j])]
          :when (and cell
                     (= :city (:type cell))
                     (#{:free :player} (:city-status cell)))]
      [i j])))

(defn city-attack-assignments
  [cities armies assigned? flood-fill-continent distance]
  (reduce
   (fn [{:keys [assigned assignments]} city]
     (let [city-continent (flood-fill-continent city)
           available (remove #(assigned? assigned (:pos %)) armies)
           reachable (filter #(contains? city-continent (:pos %)) available)
           closest (take 6 (sort-by #(distance (:pos %) city) reachable))
           claimed (mapv :pos closest)]
       {:assigned (into assigned claimed)
        :assignments (into assignments (map (fn [pos] {:pos pos :target city}) claimed))}))
   {:assigned #{} :assignments []}
   cities))

(defn assignment-updates
  [cities armies assigned? flood-fill-continent distance]
  (:assignments (city-attack-assignments cities armies assigned? flood-fill-continent distance)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:17:27.330792-05:00", :module-hash "364535003", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "892288706"} {:id "defn/assignable-armies", :kind "defn", :line 3, :end-line 13, :hash "1851158526"} {:id "defn/visible-target-cities", :kind "defn", :line 15, :end-line 24, :hash "-1158428639"} {:id "defn/city-attack-assignments", :kind "defn", :line 26, :end-line 38, :hash "630981612"} {:id "defn/assignment-updates", :kind "defn", :line 40, :end-line 42, :hash "-1274429761"}]}
;; clj-mutate-manifest-end
