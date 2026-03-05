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
