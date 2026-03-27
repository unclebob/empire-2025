(ns empire.computer.ship.patrol.repulsion
  "Patrol boat dispersal — avoids clustering patrol boats."
  (:require [empire.state.api :as sa]))

(defn nearby-patrol-boat-count
  "Counts computer patrol boats within radius of pos, excluding the one at self-pos."
  [pos self-pos radius]
  (let [computer-map (sa/read-state :computer-map)
        [px py] pos]
    (count (for [dx (range (- radius) (inc radius))
                 dy (range (- radius) (inc radius))
                 :let [nx (+ px dx) ny (+ py dy)
                       npos [nx ny]]
                 :when (and (not= npos self-pos)
                            (not (and (zero? dx) (zero? dy))))
                 :let [unit (:contents (get-in computer-map npos))]
                 :when (and (= :patrol-boat (:type unit))
                            (= :computer (:owner unit)))]
             npos))))

(defn prefer-dispersed
  "From candidates, pick the one farthest from other patrol boats.
   Falls back to rand-nth if all equally clear."
  [self-pos candidates current-pos]
  (if (<= (count candidates) 1)
    (first candidates)
    (let [scored (map (fn [c] [c (nearby-patrol-boat-count c self-pos 3)]) candidates)
          min-score (apply min (map second scored))
          best (map first (filter #(= min-score (second %)) scored))]
      (if (= (count best) 1)
        (first best)
        (rand-nth (vec best))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T10:51:51.087159-05:00", :module-hash "814135926", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-979747001"} {:id "defn/nearby-patrol-boat-count", :kind "defn", :line 5, :end-line 19, :hash "-1155909470"} {:id "defn/prefer-dispersed", :kind "defn", :line 21, :end-line 32, :hash "842639306"}]}
;; clj-mutate-manifest-end
