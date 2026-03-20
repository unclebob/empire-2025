(ns empire.game-mechanics.services.ship-action-resolution
  (:require [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.visibility :as visibility]
            [empire.game-mechanics.services.combat :as combat]
            [empire.state.api :as sa]))

(defn- set-turn-message!
  [msg ms]
  (sa/write-state! :turn-message msg)
  (sa/write-state! :turn-message-until (if (= ms Long/MAX_VALUE)
                                         Long/MAX_VALUE
                                         (+ (System/currentTimeMillis) ms))))

(defn attack-enemy
  [ship-pos enemy-pos]
  (let [attacker (get-in (sa/current-world) (conj ship-pos :contents))
        defender (get-in (sa/current-world) (conj enemy-pos :contents))
        result (combat/resolve-combat attacker defender)
        message (combat/format-combat-status (:log result)
                                             (:type attacker)
                                             (:type defender)
                                             (:winner result))
        dead-unit (if (= :attacker (:winner result)) defender attacker)]
    (set-turn-message! message Long/MAX_VALUE)
    (sa/update-world! update-in ship-pos dissoc :contents)
    (if (= :attacker (:winner result))
      (do
        (sa/update-world! assoc-in (conj enemy-pos :contents) (:survivor result))
        (when (= :carrier (:type attacker))
          (sa/update-state! :computer-carrier-positions disj ship-pos)
          (sa/update-state! :computer-carrier-positions (fnil conj #{}) enemy-pos))
        (visibility/update-cell-visibility ship-pos :computer)
        (visibility/update-cell-visibility enemy-pos :computer)
        (combat/clear-escort-on-death dead-unit)
        enemy-pos)
      (do
        (sa/update-world! assoc-in (conj enemy-pos :contents) (:survivor result))
        (when (= :carrier (:type attacker))
          (sa/update-state! :computer-carrier-positions disj ship-pos))
        (visibility/update-cell-visibility ship-pos :computer)
        (combat/clear-escort-on-death dead-unit)
        nil))))

(defn dock-computer-ship
  [ship-pos city-pos]
  (let [cell (get-in (sa/current-world) ship-pos)
        unit (:contents cell)
        city-cell (get-in (sa/current-world) city-pos)
        updated-city (uc/add-ship-to-shipyard city-cell (:type unit) (:hits unit))]
    (sa/update-world! assoc-in ship-pos (dissoc cell :contents))
    (sa/update-world! assoc-in city-pos updated-city)
    (visibility/update-cell-visibility city-pos :computer)
    city-pos))
