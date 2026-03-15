(ns empire.game-mechanics.services.combat-resolution-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.services.combat :as combat]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map get-test-city get-test-unit set-test-unit reset-all-atoms! set-test-computer-map!
                                       set-test-player-map! set-test-world! update-test-world!]]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.computer.core :as computer-core]
            [empire.computer.production :as comp-production]
            [empire.player.production :as production]))

(describe "predicates"
  (before (reset-all-atoms!))

  (context "hostile-city?"
    (it "returns true for free city"
      (set-test-world! (build-test-map ["+"]))
      (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "+"))]
        (should (combat/hostile-city? (test-utils/read-test-state :game-map) city-coords))))

    (it "returns true for computer city"
      (set-test-world! (build-test-map ["X"]))
      (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "X"))]
        (should (combat/hostile-city? (test-utils/read-test-state :game-map) city-coords))))

    (it "returns false for player city"
      (set-test-world! (build-test-map ["O"]))
      (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
        (should-not (combat/hostile-city? (test-utils/read-test-state :game-map) city-coords))))

    (it "returns false for non-city cells"
      (set-test-world! (build-test-map ["#"]))
      (should-not (combat/hostile-city? (test-utils/read-test-state :game-map) [0 0])))

    (it "returns false for sea cells"
      (set-test-world! (build-test-map ["~"]))
      (should-not (combat/hostile-city? (test-utils/read-test-state :game-map) [0 0]))))

  (context "hostile-unit?"
    (it "returns true for computer unit when checking from player perspective"
      (let [unit {:type :army :owner :computer}]
        (should (combat/hostile-unit? unit :player))))

    (it "returns true for player unit when checking from computer perspective"
      (let [unit {:type :army :owner :player}]
        (should (combat/hostile-unit? unit :computer))))

    (it "returns false for player unit when checking from player perspective"
      (let [unit {:type :army :owner :player}]
        (should-not (combat/hostile-unit? unit :player))))

    (it "returns false for computer unit when checking from computer perspective"
      (let [unit {:type :army :owner :computer}]
        (should-not (combat/hostile-unit? unit :computer))))

    (it "returns false for nil unit"
      (should-not (combat/hostile-unit? nil :player)))))

(describe "combat resolution"
  (context "format-combat-log"
    (it "formats simple attacker win"
      (let [log [{:hit :defender :damage 1}]
            attacker-type :destroyer
            defender-type :army]
        (should= "a-1. Army destroyed."
                 (combat/format-combat-log log attacker-type defender-type :attacker))))

    (it "formats simple attacker loss"
      (let [log [{:hit :attacker :damage 1}]
            attacker-type :army
            defender-type :destroyer]
        (should= "A-1. Army destroyed."
                 (combat/format-combat-log log attacker-type defender-type :defender))))

    (it "formats multi-round combat"
      (let [log [{:hit :defender :damage 3}
                 {:hit :attacker :damage 1}
                 {:hit :defender :damage 3}
                 {:hit :defender :damage 3}]
            attacker-type :submarine
            defender-type :carrier]
        (should= "c-3,S-1,c-3,c-3. Carrier destroyed."
                 (combat/format-combat-log log attacker-type defender-type :attacker))))

    (it "uses lowercase for defender hits"
      (let [log [{:hit :defender :damage 2}]
            attacker-type :battleship
            defender-type :destroyer]
        (should= "d-2. Destroyer destroyed."
                 (combat/format-combat-log log attacker-type defender-type :attacker))))

    (it "uses uppercase for attacker hits"
      (let [log [{:hit :attacker :damage 1}]
            attacker-type :transport
            defender-type :destroyer]
        (should= "T-1. Transport destroyed."
                 (combat/format-combat-log log attacker-type defender-type :defender)))))

  (context "fight-round"
    (it "attacker hits when rand < 0.5"
      (with-redefs [rand (constantly 0.4)]
        (let [attacker {:type :destroyer :hits 3 :owner :player}
              defender {:type :transport :hits 1 :owner :computer}
              [new-attacker new-defender log-entry] (combat/fight-round attacker defender)]
          (should= 3 (:hits new-attacker))
          (should= 0 (:hits new-defender))
          (should= {:hit :defender :damage 1} log-entry))))

    (it "defender hits when rand >= 0.5"
      (with-redefs [rand (constantly 0.6)]
        (let [attacker {:type :destroyer :hits 3 :owner :player}
              defender {:type :transport :hits 1 :owner :computer}
              [new-attacker new-defender log-entry] (combat/fight-round attacker defender)]
          (should= 2 (:hits new-attacker))
          (should= 1 (:hits new-defender))
          (should= {:hit :attacker :damage 1} log-entry))))

    (it "submarine deals 3 damage"
      (with-redefs [rand (constantly 0.4)]
        (let [attacker {:type :submarine :hits 2 :owner :player}
              defender {:type :carrier :hits 8 :owner :computer}
              [_ new-defender log-entry] (combat/fight-round attacker defender)]
          (should= 5 (:hits new-defender))
          (should= {:hit :defender :damage 3} log-entry))))

    (it "battleship deals 2 damage"
      (with-redefs [rand (constantly 0.4)]
        (let [attacker {:type :battleship :hits 10 :owner :player}
              defender {:type :carrier :hits 8 :owner :computer}
              [_ new-defender log-entry] (combat/fight-round attacker defender)]
          (should= 6 (:hits new-defender))
          (should= {:hit :defender :damage 2} log-entry))))

    (it "army deals 1 damage"
      (with-redefs [rand (constantly 0.4)]
        (let [attacker {:type :army :hits 1 :owner :player}
              defender {:type :army :hits 1 :owner :computer}
              [_ new-defender log-entry] (combat/fight-round attacker defender)]
          (should= 0 (:hits new-defender))
          (should= {:hit :defender :damage 1} log-entry))))

    (it "defender hits at exactly 0.5"
      (with-redefs [rand (constantly 0.5)]
        (let [attacker {:type :destroyer :hits 3 :owner :player}
              defender {:type :destroyer :hits 3 :owner :computer}
              [new-attacker new-defender log-entry] (combat/fight-round attacker defender)]
          (should= 2 (:hits new-attacker))
          (should= 3 (:hits new-defender))
          (should= {:hit :attacker :damage 1} log-entry)))))

  (context "resolve-combat"
    (it "attacker wins when always hitting"
      (with-redefs [rand (constantly 0.4)]
        (let [attacker {:type :destroyer :hits 3 :owner :player}
              defender {:type :transport :hits 1 :owner :computer}
              result (combat/resolve-combat attacker defender)]
          (should= :attacker (:winner result))
          (should= 3 (:hits (:survivor result))))))

    (it "returns combat log with hit entries"
      (with-redefs [rand (constantly 0.4)]
        (let [attacker {:type :submarine :hits 2 :owner :player}
              defender {:type :carrier :hits 8 :owner :computer}
              result (combat/resolve-combat attacker defender)]
          (should= :attacker (:winner result))
          (should= [{:hit :defender :damage 3}
                    {:hit :defender :damage 3}
                    {:hit :defender :damage 3}] (:log result)))))

    (it "logs defender hits with defender's strength"
      ;; Submarine has 2 hits, carrier deals 1 damage per hit
      ;; Roll 0.6: carrier hits submarine (1 damage), submarine has 1 hit left
      ;; Roll 0.4: submarine hits carrier (3 damage), carrier has 5 hits left
      ;; Roll 0.6: carrier hits submarine (1 damage), submarine has 0 hits -> dies
      (let [rolls (atom [0.6 0.4 0.6])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (let [attacker {:type :submarine :hits 2 :owner :player}
                defender {:type :carrier :hits 8 :owner :computer}
                result (combat/resolve-combat attacker defender)]
            (should= :defender (:winner result))
            (should= [{:hit :attacker :damage 1}
                      {:hit :defender :damage 3}
                      {:hit :attacker :damage 1}] (:log result))))))

    (it "defender wins when always hitting"
      (with-redefs [rand (constantly 0.6)]
        (let [attacker {:type :transport :hits 1 :owner :player}
              defender {:type :destroyer :hits 3 :owner :computer}
              result (combat/resolve-combat attacker defender)]
          (should= :defender (:winner result))
          (should= 3 (:hits (:survivor result))))))

    (it "submarine can defeat battleship with lucky rolls"
      (let [rolls (atom [0.4 0.4 0.4 0.4])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (let [attacker {:type :submarine :hits 2 :owner :player}
                defender {:type :battleship :hits 10 :owner :computer}
                result (combat/resolve-combat attacker defender)]
            (should= :attacker (:winner result))
            (should= 2 (:hits (:survivor result)))))))

    (it "army vs army is 50/50"
      (with-redefs [rand (constantly 0.4)]
        (let [attacker {:type :army :hits 1 :owner :player}
              defender {:type :army :hits 1 :owner :computer}
              result (combat/resolve-combat attacker defender)]
          (should= :attacker (:winner result)))))

    (it "preserves unit type and owner on survivor"
      (with-redefs [rand (constantly 0.4)]
        (let [attacker {:type :destroyer :hits 3 :owner :player}
              defender {:type :transport :hits 1 :owner :computer}
              result (combat/resolve-combat attacker defender)]
          (should= :destroyer (:type (:survivor result)))
          (should= :player (:owner (:survivor result))))))))
