(ns empire.game-mechanics.movement.api-decisions)

(defn move-unit-result
  [result]
  (merge {:result nil
          :pos nil}
         result))

(defn set-unit-movement-args
  [unit-coords target-coords extended?]
  {:unit-coords unit-coords
   :target-coords target-coords
   :extended? (boolean extended?)})

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T14:23:33.70084-05:00", :module-hash "1164079036", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "75188729"} {:id "defn/move-unit-result", :kind "defn", :line 3, :end-line 7, :hash "-1226607212"} {:id "defn/set-unit-movement-args", :kind "defn", :line 9, :end-line 13, :hash "-373466033"}]}
;; clj-mutate-manifest-end
