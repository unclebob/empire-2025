(ns empire.game-mechanics.services.ship-action-resolution
  (:require [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.visibility :as visibility]
            [empire.game-mechanics.services.combat :as combat]
            [empire.state.api :as sa]))

(defn- set-warning-message!
  [msg]
  (sa/write-state! :warning-message msg))

(defn attack-enemy
  [ship-pos enemy-pos]
  (let [attacker (get-in (sa/current-world) (conj ship-pos :contents))
        defender (get-in (sa/current-world) (conj enemy-pos :contents))
        result (combat/resolve-combat attacker defender)
        message (combat/format-combat-outcome (:type attacker)
                                              (:type defender)
                                              (:winner result))
        dead-unit (if (= :attacker (:winner result)) defender attacker)]
    (set-warning-message! message)
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:42:16.700032-05:00", :module-hash "179466291", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "2140282499"} {:id "defn-/set-turn-message!", :kind "defn-", :line 7, :end-line 12, :hash "961870185"} {:id "defn/attack-enemy", :kind "defn", :line 14, :end-line 42, :hash "568815755"} {:id "defn/dock-computer-ship", :kind "defn", :line 44, :end-line 53, :hash "-1631234714"}]}
;; clj-mutate-manifest-end
