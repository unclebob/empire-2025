(ns empire.config.units.army)

(defn initial-state
  []
  {})

(defn can-move-to?
  [cell]
  (and cell
       (or (= (:type cell) :land)
           (and (= (:type cell) :city)
                (not= (:city-status cell) :player)))))

(defn needs-attention?
  [unit]
  (= (:mode unit) :awake))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:59:42.357856-05:00", :module-hash "275339645", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "1384524364"} {:id "defn/initial-state", :kind "defn", :line 3, :end-line 5, :hash "-142005869"} {:id "defn/can-move-to?", :kind "defn", :line 7, :end-line 12, :hash "-1193364589"} {:id "defn/needs-attention?", :kind "defn", :line 14, :end-line 16, :hash "335118728"}]}
;; clj-mutate-manifest-end
