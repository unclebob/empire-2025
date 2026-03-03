(ns empire.computer.ship-core-spec
  (:require [speclj.core :refer :all]
            [empire.computer.ship-core :as ship-core]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-world!]]
            [empire.combat :as combat]))

(describe "ship-core"
  (before (reset-all-atoms!))

  (context "get-passable-sea-neighbors (L21)"
    (it "includes sea cell with player unit as passable"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                         {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (let [neighbors (ship-core/get-passable-sea-neighbors [0 0])]
        (should (some #{[0 1]} neighbors))))

    (it "excludes sea cell with computer unit as passable"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                         {:type :sea :contents {:type :submarine :owner :computer :hits 2}}]])
      (let [neighbors (ship-core/get-passable-sea-neighbors [0 0])]
        (should-not (some #{[0 1]} neighbors)))))

  (context "attack-enemy carrier tracking (L48, L56)"
    (it "updates carrier-positions when carrier wins"
      (set-test-world! [[{:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                         {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (reset! atoms/computer-carrier-positions #{[0 0]})
      (with-redefs [combat/resolve-combat (fn [_ _] {:winner :attacker
                                                      :survivor {:type :carrier :owner :computer :hits 7}
                                                      :log [{:hit :defender :damage 1}]})
                    combat/clear-escort-on-death (fn [_])]
        (ship-core/attack-enemy [0 0] [0 1]))
      (should-not (contains? @atoms/computer-carrier-positions [0 0]))
      (should (contains? @atoms/computer-carrier-positions [0 1])))

    (it "removes carrier from positions when carrier loses"
      (set-test-world! [[{:type :sea :contents {:type :carrier :owner :computer :hits 1}}
                         {:type :sea :contents {:type :battleship :owner :player :hits 8}}]])
      (reset! atoms/computer-carrier-positions #{[0 0]})
      (with-redefs [combat/resolve-combat (fn [_ _] {:winner :defender
                                                      :survivor {:type :battleship :owner :player :hits 7}
                                                      :log [{:hit :attacker :damage 2}]})
                    combat/clear-escort-on-death (fn [_])]
        (ship-core/attack-enemy [0 0] [0 1]))
      (should-not (contains? @atoms/computer-carrier-positions [0 0])))

    (it "updates surviving defender hits when attacker loses"
      (set-test-world! [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1}}
                         {:type :sea :contents {:type :battleship :owner :player :hits 10}}]])
      (with-redefs [combat/resolve-combat (fn [_ _] {:winner :defender
                                                      :survivor {:type :battleship :owner :player :hits 8}
                                                      :log [{:hit :attacker :damage 2}]})
                    combat/clear-escort-on-death (fn [_])]
        (ship-core/attack-enemy [0 0] [0 1]))
      (should= nil (get-in @atoms/game-map [0 0 :contents]))
      (should= 8 (get-in @atoms/game-map [0 1 :contents :hits])))

    (it "sets turn message describing battle and damage"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                         {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (reset! atoms/turn-message "")
      (with-redefs [combat/resolve-combat (fn [_ _] {:winner :attacker
                                                      :survivor {:type :destroyer :owner :computer :hits 3}
                                                      :log [{:hit :defender :damage 1}]})
                    combat/clear-escort-on-death (fn [_])]
        (ship-core/attack-enemy [0 0] [0 1]))
      (should= "Battle: p-1. Patrol-boat destroyed. Damage: Destroyer lost 0, Patrol-boat lost 1."
               @atoms/turn-message))))
