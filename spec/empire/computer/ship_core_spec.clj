(ns empire.computer.ship.core-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.ship.core :as ship-core]
            [empire.computer.shared.threat :as threat]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! set-test-computer-map!]]
            [empire.game-mechanics.services.combat :as combat]))

(describe "ship-core"
  (before (reset-all-atoms!))

  (context "get-passable-sea-neighbors (L21)"
    (it "includes sea cell with player unit as passable"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                         {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [neighbors (ship-core/get-passable-sea-neighbors [0 0])]
        (should (some #{[0 1]} neighbors))))

    (it "excludes sea cell with computer unit as passable"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                         {:type :sea :contents {:type :submarine :owner :computer :hits 2}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [neighbors (ship-core/get-passable-sea-neighbors [0 0])]
        (should-not (some #{[0 1]} neighbors))))

    (it "ignores hidden enemy occupancy when computer-map shows open sea"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                         {:type :sea :contents {:type :submarine :owner :player :hits 2}}]])
      (set-test-computer-map! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                                {:type :sea}]])
      (let [neighbors (ship-core/get-passable-sea-neighbors [0 0])]
        (should (some #{[0 1]} neighbors)))))

  (context "attack-enemy carrier tracking (L48, L56)"
    (it "updates carrier-positions when carrier wins"
      (set-test-world! [[{:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                         {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (test-utils/set-test-state! :computer-carrier-positions #{[0 0]})
      (with-redefs [combat/resolve-combat (fn [_ _] {:winner :attacker
                                                      :survivor {:type :carrier :owner :computer :hits 7}
                                                      :log [{:hit :defender :damage 1}]})
                    combat/clear-escort-on-death (fn [_])]
        (ship-core/attack-enemy [0 0] [0 1]))
      (should-not (contains? (test-utils/read-test-state :computer-carrier-positions) [0 0]))
      (should (contains? (test-utils/read-test-state :computer-carrier-positions) [0 1])))

    (it "removes carrier from positions when carrier loses"
      (set-test-world! [[{:type :sea :contents {:type :carrier :owner :computer :hits 1}}
                         {:type :sea :contents {:type :battleship :owner :player :hits 8}}]])
      (test-utils/set-test-state! :computer-carrier-positions #{[0 0]})
      (with-redefs [combat/resolve-combat (fn [_ _] {:winner :defender
                                                      :survivor {:type :battleship :owner :player :hits 7}
                                                      :log [{:hit :attacker :damage 2}]})
                    combat/clear-escort-on-death (fn [_])]
        (ship-core/attack-enemy [0 0] [0 1]))
      (should-not (contains? (test-utils/read-test-state :computer-carrier-positions) [0 0])))

    (it "updates surviving defender hits when attacker loses"
      (set-test-world! [[{:type :sea :contents {:type :patrol-boat :owner :computer :hits 1}}
                         {:type :sea :contents {:type :battleship :owner :player :hits 10}}]])
      (with-redefs [combat/resolve-combat (fn [_ _] {:winner :defender
                                                      :survivor {:type :battleship :owner :player :hits 8}
                                                      :log [{:hit :attacker :damage 2}]})
                    combat/clear-escort-on-death (fn [_])]
        (ship-core/attack-enemy [0 0] [0 1]))
      (should= nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
      (should= 8 (get-in (test-utils/read-test-state :game-map) [0 1 :contents :hits])))

    (it "sets turn message describing battle and damage"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                         {:type :sea :contents {:type :patrol-boat :owner :player :hits 1}}]])
      (test-utils/set-test-state! :turn-message "")
      (with-redefs [combat/resolve-combat (fn [_ _] {:winner :attacker
                                                      :survivor {:type :destroyer :owner :computer :hits 3}
                                                      :log [{:hit :defender :damage 1}]})
                    combat/clear-escort-on-death (fn [_])]
        (ship-core/attack-enemy [0 0] [0 1]))
      (should= "Battle: p-1. Patrol-boat destroyed. Damage: Destroyer lost 0, Patrol-boat lost 1."
               (test-utils/read-test-state :turn-message))))

  (context "retreat-if-damaged (L129)"
    (it "retreats with passable sea neighbors when ship should retreat"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 2}}
                         {:type :sea}]
                        [{:type :sea}
                         {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [seen (atom nil)
            unit {:type :destroyer :owner :computer :hits 2}]
        (with-redefs [threat/should-retreat? (fn [pos u comp-map]
                                               (reset! seen {:pos pos :unit u :computer-map comp-map})
                                               true)
                      threat/retreat-move (fn [_ _ _ passable]
                                            (vec passable))]
          (should= [[0 1] [1 0]]
                   (ship-core/retreat-if-damaged [0 0] unit))
          (should= [0 0] (:pos @seen))
          (should= unit (:unit @seen))
          (should= (test-utils/read-test-state :game-map) (:computer-map @seen)))))

    (it "does not call retreat move when ship should not retreat"
      (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :computer :hits 3}}
                         {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [retreat-called? (atom false)]
        (with-redefs [threat/should-retreat? (constantly false)
                      threat/retreat-move (fn [& _]
                                            (reset! retreat-called? true)
                                            [0 1])]
          (should-be-nil (ship-core/retreat-if-damaged [0 0] {:type :destroyer :owner :computer :hits 3}))
          (should= false @retreat-called?)))))

  (context "move-toward direct-route heuristic"
    (it "prefers direct heading when known sea path is at least 2x chebyshev and corridor is sea-or-unexplored"
      (set-test-world! (build-test-map ["~~~~~"
                                        "~~~~~"
                                        "s~~~~"
                                        "~~~~~"
                                        "~~~~~"]))
      (set-test-computer-map!
       [[{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :sea}]
        [{:type :sea} {:type :land} {:type :land} {:type :land} {:type :sea}]
        [{:type :sea} nil nil nil {:type :sea}]
        [nil nil nil nil nil]
        [nil nil nil nil nil]])
      (let [long-path [[1 0] [0 0] [0 1] [0 2] [0 3] [0 4] [1 4] [2 4]]]
        (with-redefs [empire.computer.ship.core/sea-path-to-target (fn [& _] long-path)]
          (let [new-pos (ship-core/move-toward [2 0] [2 4])]
            (should= [2 1] new-pos))))
      )

    (it "falls back to recomputed sea path when direct corridor contains discovered land"
      (set-test-world! (build-test-map ["~~~~~"
                                        "~~~~~"
                                        "s~~~~"
                                        "~~~~~"
                                        "~~~~~"]))
      (set-test-computer-map!
       [[{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :sea}]
        [{:type :sea} {:type :land} {:type :land} {:type :land} {:type :sea}]
        [{:type :sea} {:type :land} {:type :land} {:type :land} {:type :sea}]
        [{:type :land} {:type :land} {:type :land} {:type :land} {:type :land}]
        [{:type :land} {:type :land} {:type :land} {:type :land} {:type :land}]])
      (let [new-pos (ship-core/move-toward [2 0] [2 4])]
        (should= [1 0] new-pos)))))
