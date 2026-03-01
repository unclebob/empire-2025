;; mutation-tested: no
(ns empire.computer.threat-response.invasion-state
  (:require [empire.computer.core :as core]))

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
                                    (core/get-neighbors pos))]
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
    (apply min-key #(core/distance pos %) pts)))
