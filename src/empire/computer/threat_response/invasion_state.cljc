(ns empire.computer.threat-response.invasion-state
  (:require [empire.computer.shared.grid :as grid]
            [empire.computer.shared.world-query :as world-query]))

(defn land-or-city?
  [cell]
  (and cell (#{:land :city} (:type cell))))

(defn flood-fill-land
  [world start]
  (when (land-or-city? (get-in world start))
    (loop [frontier #{start}
           visited #{}]
      (if (empty? frontier)
        visited
        (let [pos (first frontier)
              rest-frontier (disj frontier pos)]
          (if (contains? visited pos)
            (recur rest-frontier visited)
            (let [neighbors (filter (fn [n]
                                      (land-or-city? (get-in world n)))
                                    (world-query/get-neighbors pos))]
              (recur (into rest-frontier neighbors)
                     (conj visited pos)))))))))

(defn recompute-target-land
  [world detection-points]
  (reduce (fn [acc p]
            (into acc (or (flood-fill-land world p) #{})))
          #{}
          detection-points))

(defn activate-state
  [state pos round-number]
  (-> state
      (assoc :active? true)
      (update :detection-points conj pos)
      (assoc :started-round (or (:started-round state) round-number))))

(defn nearest-target
  [state pos]
  (when-let [pts (seq (:detection-points state))]
    (apply min-key #(grid/distance pos %) pts)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:58:45.322195-05:00", :module-hash "-847766295", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-830954805"} {:id "defn/land-or-city?", :kind "defn", :line 4, :end-line 6, :hash "-177206691"} {:id "defn/flood-fill-land", :kind "defn", :line 8, :end-line 23, :hash "2146363231"} {:id "defn/recompute-target-land", :kind "defn", :line 25, :end-line 30, :hash "-61417461"} {:id "defn/activate-state", :kind "defn", :line 32, :end-line 37, :hash "1392644556"} {:id "defn/nearest-target", :kind "defn", :line 39, :end-line 42, :hash "-1523050205"}]}
;; clj-mutate-manifest-end
