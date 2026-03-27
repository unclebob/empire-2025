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
;; {:version 1, :tested-at "2026-03-26T23:39:56.324996-05:00", :module-hash "-511397461", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-1421237018"} {:id "defn/land-or-city?", :kind "defn", :line 5, :end-line 7, :hash "-177206691"} {:id "defn/flood-fill-land", :kind "defn", :line 9, :end-line 24, :hash "2079928900"} {:id "defn/recompute-target-land", :kind "defn", :line 26, :end-line 31, :hash "-61417461"} {:id "defn/activate-state", :kind "defn", :line 33, :end-line 38, :hash "1392644556"} {:id "defn/nearest-target", :kind "defn", :line 40, :end-line 43, :hash "1518767761"}]}
;; clj-mutate-manifest-end
