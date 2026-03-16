(ns empire.computer.army.assignment-decisions)

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T16:50:15.623732-05:00", :module-hash "-1439195386", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "892288706"} {:id "defn/city-attack-assignments", :kind "defn", :line 3, :end-line 15, :hash "630981612"}]}
;; clj-mutate-manifest-end
