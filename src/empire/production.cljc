(ns empire.production
  (:require [empire.atoms :as atoms]
            [empire.config :as config]))

(defn set-city-production
  "Sets the production for a city at given coordinates to the specified item."
  [coords item]
  (swap! atoms/production assoc coords {:item item :remaining-rounds (config/item-cost item)}))

(defn update-production
  "Updates production for all cities by decrementing remaining rounds."
  []
  (doseq [[coords prod] @atoms/production]
    (when (map? prod)
      (let [cell (get-in @atoms/game-map coords)]
        (when-not (:contents cell)
          (let [item (:item prod)
                remaining (dec (:remaining-rounds prod))]
            (if (zero? remaining)
              (do
                (let [game-map @atoms/game-map
                      cell (get-in game-map coords)
                      owner (:city-status cell)
                      marching-orders (:marching-orders cell)
                      flight-path (:flight-path cell)
                      unit {:type item :hits (config/item-hits item) :mode :awake :owner owner}
                      unit (if (= item :fighter) (assoc unit :fuel config/fighter-fuel) unit)
                      unit (cond
                             (and (= item :army) marching-orders)
                             (assoc unit :mode :moving :target marching-orders)
                             (and (= item :fighter) flight-path)
                             (assoc unit :mode :moving :target flight-path)
                             :else unit)
                      cell (assoc cell :contents unit)]
                  (swap! atoms/game-map assoc-in coords cell)
                  (swap! atoms/production assoc coords (assoc prod :remaining-rounds (config/item-cost item)))))
              (swap! atoms/production assoc coords (assoc prod :remaining-rounds remaining)))))))))