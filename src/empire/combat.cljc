(ns empire.combat
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.movement :as movement]))

(defn hostile-city? [target-coords]
  (let [target-cell (get-in @atoms/game-map target-coords)]
    (and (= (:type target-cell) :city)
         (config/hostile-city? (:city-status target-cell)))))

(defn attempt-conquest
  "Attempts to conquer a city with an army. Returns true if conquest was attempted."
  [army-coords city-coords]
  (let [army-cell (get-in @atoms/game-map army-coords)
        city-cell (get-in @atoms/game-map city-coords)]
    (if (< (rand) 0.5)
      (do
        (swap! atoms/game-map assoc-in army-coords (dissoc army-cell :contents))
        (swap! atoms/game-map assoc-in city-coords (assoc city-cell :city-status :player))
        (movement/update-cell-visibility city-coords :player))
      (do
        (swap! atoms/game-map assoc-in army-coords (dissoc army-cell :contents))
        (movement/update-cell-visibility army-coords :player)
        (atoms/set-line3-message (:conquest-failed config/messages) 3000)))
    true))

(defn attempt-fighter-overfly
  "Fighter flies over hostile city and gets shot down."
  [fighter-coords city-coords]
  (let [fighter-cell (get-in @atoms/game-map fighter-coords)
        fighter (:contents fighter-cell)
        city-cell (get-in @atoms/game-map city-coords)
        shot-down-fighter (assoc fighter :mode :awake :hits 0 :steps-remaining 0 :reason :fighter-shot-down)]
    (swap! atoms/game-map assoc-in fighter-coords (dissoc fighter-cell :contents))
    (swap! atoms/game-map assoc-in city-coords (assoc city-cell :contents shot-down-fighter))
    (atoms/set-line3-message (:fighter-destroyed-by-city config/messages) 3000)
    true))
