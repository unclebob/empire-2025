(ns empire.game-mechanics.containers.launch
  (:require [empire.game-mechanics.spatial.neighbors :as neighbors]))

(defn launch-steps-toward
  [[cx cy] [tx ty]]
  (let [distance (fn [[x y]]
                   (+ (Math/abs (long (- tx x)))
                      (Math/abs (long (- ty y)))))]
    (->> neighbors/neighbor-offsets
         (map (fn [[dx dy]] [(+ cx dx) (+ cy dy)]))
         (sort-by (juxt distance identity))
         vec)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:54:23.628036-05:00", :module-hash "-982383285", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "1318585253"} {:id "defn/launch-steps-toward", :kind "defn", :line 4, :end-line 12, :hash "1529889001"}]}
;; clj-mutate-manifest-end
