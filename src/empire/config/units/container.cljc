(ns empire.config.units.container)

(defn sea-can-move-to?
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defn needs-attention?
  [unit awake-key]
  (or (= (:mode unit) :awake)
      (pos? (get unit awake-key 0))))

(defn full?
  [unit count-key capacity]
  (>= (get unit count-key 0) capacity))

(defn has-contained?
  [unit count-key]
  (pos? (get unit count-key 0)))

(defn add-contained
  [unit count-key]
  (update unit count-key (fnil inc 0)))

(defn remove-contained
  [unit count-key]
  (update unit count-key (fnil dec 0)))

(defn wake-contained
  [unit count-key awake-key]
  (assoc unit awake-key (get unit count-key 0)))

(defn sleep-contained
  [unit awake-key]
  (assoc unit awake-key 0))

(defn remove-awake-contained
  [unit count-key awake-key]
  (-> unit
      (update count-key (fnil dec 0))
      (update awake-key (fnil dec 0))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-07T19:22:39.020405-05:00", :module-hash "-927374432", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "1153925927"} {:id "defn/sea-can-move-to?", :kind "defn", :line 3, :end-line 6, :hash "-1349780484"} {:id "defn/needs-attention?", :kind "defn", :line 8, :end-line 11, :hash "1878870595"} {:id "defn/full?", :kind "defn", :line 13, :end-line 15, :hash "-1405148096"} {:id "defn/has-contained?", :kind "defn", :line 17, :end-line 19, :hash "-1927413806"} {:id "defn/add-contained", :kind "defn", :line 21, :end-line 23, :hash "-1659865990"} {:id "defn/remove-contained", :kind "defn", :line 25, :end-line 27, :hash "-1405886065"} {:id "defn/wake-contained", :kind "defn", :line 29, :end-line 31, :hash "1473018358"} {:id "defn/sleep-contained", :kind "defn", :line 33, :end-line 35, :hash "-1464661224"} {:id "defn/remove-awake-contained", :kind "defn", :line 37, :end-line 41, :hash "-1103737686"}]}
;; clj-mutate-manifest-end
