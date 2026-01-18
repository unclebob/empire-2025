(ns empire.combat
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.movement :as movement]
            [empire.units.dispatcher :as dispatcher]))

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

(defn hostile-unit?
  "Returns true if the unit is hostile to the given owner."
  [unit owner]
  (and unit (not= (:owner unit) owner)))

(defn fight-round
  "Executes one round of combat. 50% chance attacker hits, 50% chance defender hits.
   Returns [updated-attacker updated-defender]."
  [attacker defender]
  (if (< (rand) 0.5)
    (let [damage (dispatcher/strength (:type attacker))]
      [attacker (update defender :hits - damage)])
    (let [damage (dispatcher/strength (:type defender))]
      [(update attacker :hits - damage) defender])))

(defn resolve-combat
  "Fights combat rounds until one unit dies.
   Returns {:winner :attacker|:defender :survivor unit-map}."
  [attacker defender]
  (loop [a attacker d defender]
    (let [[new-a new-d] (fight-round a d)]
      (cond
        (<= (:hits new-d) 0) {:winner :attacker :survivor new-a}
        (<= (:hits new-a) 0) {:winner :defender :survivor new-d}
        :else (recur new-a new-d)))))

(defn attempt-attack
  "Attempts to attack an enemy unit at target-coords from attacker-coords.
   Returns true if attack was attempted, false otherwise."
  [attacker-coords target-coords]
  (let [attacker-cell (get-in @atoms/game-map attacker-coords)
        target-cell (get-in @atoms/game-map target-coords)
        attacker (:contents attacker-cell)
        defender (:contents target-cell)]
    (if (or (nil? defender) (not (hostile-unit? defender (:owner attacker))))
      false
      (let [result (resolve-combat attacker defender)]
        (swap! atoms/game-map assoc-in (conj attacker-coords :contents) nil)
        (if (= :attacker (:winner result))
          (swap! atoms/game-map assoc-in (conj target-coords :contents) (:survivor result))
          (swap! atoms/game-map assoc-in (conj target-coords :contents) (:survivor result)))
        true))))
