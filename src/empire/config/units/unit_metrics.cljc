;; mutation-tested: no
(ns empire.config.units.unit-metrics)

(def ^:private naval-units
  #{:transport :patrol-boat :destroyer :submarine :carrier :battleship})

(defn naval-unit?
  [unit-type]
  (contains? naval-units unit-type))

(defn scale-by-hits
  "VMS ceiling division scaling helper."
  [base-value current-hits max-hits]
  (quot (+ (* base-value current-hits) (dec max-hits)) max-hits))

(defn effective-speed
  [base-speed current-hits max-hits]
  (scale-by-hits base-speed current-hits max-hits))

(defn effective-capacity
  [base-cap current-hits max-hits]
  (scale-by-hits base-cap current-hits max-hits))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:50:31.33339-05:00", :module-hash "1971052060", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 2, :hash "1776888221"} {:id "def/naval-units", :kind "def", :line 4, :end-line 5, :hash "-569052656"} {:id "defn/naval-unit?", :kind "defn", :line 7, :end-line 9, :hash "838680342"} {:id "defn/scale-by-hits", :kind "defn", :line 11, :end-line 14, :hash "-679300535"} {:id "defn/effective-speed", :kind "defn", :line 16, :end-line 18, :hash "-743163770"} {:id "defn/effective-capacity", :kind "defn", :line 20, :end-line 22, :hash "-2107664833"}]}
;; clj-mutate-manifest-end
