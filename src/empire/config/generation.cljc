(ns empire.config.generation)

(def default-map-size [100 60])
(def smooth-count 10)
(def land-fraction 0.3)
(def number-of-cities 70)
(def min-city-distance 5)
(def max-placement-attempts 1000)
(def min-surrounding-land 10)

(defn compute-size-constants
  "Computes constants derived from the map size [cols rows].
   Returns a map to be stored in atoms/map-size-constants."
  [cols rows]
  (let [area (* cols rows)
        ref-area 6000]
    {:cols cols
     :rows rows
     :number-of-cities (max 10 (int (* 70 (/ area ref-area))))}))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:59:33.911274-05:00", :module-hash "-1402572681", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "475860131"} {:id "def/default-map-size", :kind "def", :line 3, :end-line 3, :hash "-209570765"} {:id "def/smooth-count", :kind "def", :line 4, :end-line 4, :hash "215537876"} {:id "def/land-fraction", :kind "def", :line 5, :end-line 5, :hash "1732259718"} {:id "def/number-of-cities", :kind "def", :line 6, :end-line 6, :hash "-412173247"} {:id "def/min-city-distance", :kind "def", :line 7, :end-line 7, :hash "40647050"} {:id "def/max-placement-attempts", :kind "def", :line 8, :end-line 8, :hash "-1290752449"} {:id "def/min-surrounding-land", :kind "def", :line 9, :end-line 9, :hash "-1709304120"} {:id "defn/compute-size-constants", :kind "defn", :line 11, :end-line 19, :hash "-1691064370"}]}
;; clj-mutate-manifest-end
