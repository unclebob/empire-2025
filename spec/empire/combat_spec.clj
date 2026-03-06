(ns empire.application.combat-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.application.combat :as combat]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map get-test-city get-test-unit set-test-unit reset-all-atoms! set-test-computer-map!
                                       set-test-world! update-test-world!]]
            [empire.units.dispatcher :as dispatcher]
            [empire.containers.helpers :as uc]
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

(describe "attack and conquest"
  (before (reset-all-atoms!))
  (with-stubs)

  (context "attempt-attack"
    (it "returns false when target has no unit"
      (set-test-world! (build-test-map ["A#"]))
      (set-test-unit (test-utils/game-map-atom) "A" :hits 1)
      (should= false (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0])))

    (it "returns false when target unit is friendly"
      (set-test-world! (build-test-map ["AA"]))
      (set-test-unit (test-utils/game-map-atom) "A1" :hits 1)
      (set-test-unit (test-utils/game-map-atom) "A2" :hits 1)
      (should= false (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0])))

    (it "returns false when defender is a satellite"
      (set-test-world! (build-test-map ["Av"]))
      (set-test-unit (test-utils/game-map-atom) "A" :hits 1)
      (set-test-unit (test-utils/game-map-atom) "v" :hits 1)
      (should= false (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0])))

    (it "returns false when attacker is a satellite"
      (set-test-world! (build-test-map ["Va"]))
      (set-test-unit (test-utils/game-map-atom) "V" :hits 1)
      (set-test-unit (test-utils/game-map-atom) "a" :hits 1)
      (should= false (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0])))

    (it "returns a result map when attacking enemy unit"
      (set-test-world! (build-test-map ["Aa"]))
      (set-test-unit (test-utils/game-map-atom) "A" :hits 1)
      (set-test-unit (test-utils/game-map-atom) "a" :hits 1)
      (with-redefs [rand (constantly 0.4)]
        (let [result (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0])]
          (should (:world result))
          (should (:messages result)))))

    (it "attacker wins and occupies cell when victorious"
      (set-test-world! (build-test-map ["Da"]))
      (set-test-unit (test-utils/game-map-atom) "D" :hits 3)
      (set-test-unit (test-utils/game-map-atom) "a" :hits 1)
      (with-redefs [rand (constantly 0.4)]
        (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
        (should= nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
        (should= :destroyer (:type (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))
        (should= :player (:owner (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))))

    (it "attacker loses and defender remains"
      (set-test-world! (build-test-map ["aD"]))
      (set-test-unit (test-utils/game-map-atom) "a" :hits 1)
      (set-test-unit (test-utils/game-map-atom) "D" :hits 3)
      (with-redefs [rand (constantly 0.6)]
        (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
        (should= nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
        (should= :destroyer (:type (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))
        (should= :player (:owner (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))))

    (it "removes attacker from original cell even when losing"
      (set-test-world! (build-test-map ["Tb"]))
      (set-test-unit (test-utils/game-map-atom) "T" :hits 1)
      (set-test-unit (test-utils/game-map-atom) "b" :hits 10)
      (with-redefs [rand (constantly 0.6)]
        (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
        (should= nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

    (it "survivor has reduced hits after combat"
      (set-test-world! (build-test-map ["Dd"]))
      (set-test-unit (test-utils/game-map-atom) "D" :hits 3)
      (set-test-unit (test-utils/game-map-atom) "d" :hits 3)
      ;; Rolls: 0.4 (D hits d:2), 0.6 (d hits D:2), 0.4 (D hits d:1), 0.4 (D hits d:0)
      (let [rolls (atom [0.4 0.6 0.4 0.4])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
          (let [survivor (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
            (should= :destroyer (:type survivor))
            (should= :player (:owner survivor))
            (should= 2 (:hits survivor))))))

    (it "displays combat log when attacker wins"
      (set-test-world! (build-test-map ["Da"]))
      (set-test-unit (test-utils/game-map-atom) "D" :hits 3)
      (set-test-unit (test-utils/game-map-atom) "a" :hits 1)
      (test-utils/set-test-state! :turn-message "")
      (with-redefs [rand (constantly 0.4)]
        (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
        (should= "Battle: a-1. Army destroyed. Damage: Destroyer lost 0, Army lost 1."
                 (test-utils/read-test-state :turn-message))))

    (it "displays combat log when attacker loses"
      (set-test-world! (build-test-map ["Ad"]))
      (set-test-unit (test-utils/game-map-atom) "A" :hits 1)
      (set-test-unit (test-utils/game-map-atom) "d" :hits 3)
      (test-utils/set-test-state! :turn-message "")
      (with-redefs [rand (constantly 0.6)]
        (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
        (should= "Battle: A-1. Army destroyed. Damage: Army lost 1, Destroyer lost 0."
                 (test-utils/read-test-state :turn-message))))

    (it "displays combat log with multiple exchanges"
      (set-test-world! (build-test-map ["Dd"]))
      (set-test-unit (test-utils/game-map-atom) "D" :hits 3)
      (set-test-unit (test-utils/game-map-atom) "d" :hits 3)
      (test-utils/set-test-state! :turn-message "")
      ;; Rolls: 0.4 (D hits d:2), 0.6 (d hits D:2), 0.4 (D hits d:1), 0.4 (D hits d:0)
      (let [rolls (atom [0.4 0.6 0.4 0.4])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
          (should= "Battle: d-1,D-1,d-1,d-1. Destroyer destroyed. Damage: Destroyer lost 1, Destroyer lost 3."
                   (test-utils/read-test-state :turn-message)))))

    (it "displays combat log for submarine vs carrier"
      (set-test-world! (build-test-map ["Sc"]))
      (set-test-unit (test-utils/game-map-atom) "S" :hits 2)
      (set-test-unit (test-utils/game-map-atom) "c" :hits 8)
      (test-utils/set-test-state! :turn-message "")
      ;; Rolls: 0.6 (c hits S:1), 0.6 (c hits S:0)
      (let [rolls (atom [0.6 0.6])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
          (should= "Battle: S-1,S-1. Submarine destroyed. Damage: Submarine lost 2, Carrier lost 0."
                   (test-utils/read-test-state :turn-message)))))

    (it "displays combat log for submarine defeating carrier"
      (set-test-world! (build-test-map ["Sc"]))
      (set-test-unit (test-utils/game-map-atom) "S" :hits 2)
      (set-test-unit (test-utils/game-map-atom) "c" :hits 8)
      (test-utils/set-test-state! :turn-message "")
      ;; Rolls: 0.4 (S hits c:5), 0.6 (c hits S:1), 0.4 (S hits c:2), 0.4 (S hits c:0)
      (let [rolls (atom [0.4 0.6 0.4 0.4])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
          (should= "Battle: c-3,S-1,c-3,c-3. Carrier destroyed. Damage: Submarine lost 1, Carrier lost 9."
                   (test-utils/read-test-state :turn-message))))))

  (context "attempt-conquest"
    (it "removes army from original cell on success"
      (with-redefs [rand (constantly 0.1)]
        (set-test-world! (build-test-map ["A+"]))
        (let [army-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))
              city-coords (:pos (get-test-city (test-utils/game-map-atom) "+"))]
          (combat/apply-combat-result! (combat/attempt-conquest (test-utils/read-test-state :game-map) army-coords city-coords))
          (should= nil (:contents (get-in (test-utils/read-test-state :game-map) army-coords))))))

    (it "converts city to player on success"
      (with-redefs [rand (constantly 0.1)]
        (set-test-world! (build-test-map ["A+"]))
        (let [army-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))
              city-coords (:pos (get-test-city (test-utils/game-map-atom) "+"))]
          (combat/apply-combat-result! (combat/attempt-conquest (test-utils/read-test-state :game-map) army-coords city-coords))
          (should= :player (:city-status (get-in (test-utils/read-test-state :game-map) city-coords))))))

    (it "removes army from original cell on failure"
      (with-redefs [rand (constantly 0.9)]
        (set-test-world! (build-test-map ["A+"]))
        (test-utils/set-test-state! :error-message "")
        (let [army-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))
              city-coords (:pos (get-test-city (test-utils/game-map-atom) "+"))]
          (combat/apply-combat-result! (combat/attempt-conquest (test-utils/read-test-state :game-map) army-coords city-coords))
          (should= nil (:contents (get-in (test-utils/read-test-state :game-map) army-coords))))))

    (it "keeps city status on failure"
      (with-redefs [rand (constantly 0.9)]
        (set-test-world! (build-test-map ["A+"]))
        (test-utils/set-test-state! :error-message "")
        (let [army-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))
              city-coords (:pos (get-test-city (test-utils/game-map-atom) "+"))]
          (combat/apply-combat-result! (combat/attempt-conquest (test-utils/read-test-state :game-map) army-coords city-coords))
          (should= :free (:city-status (get-in (test-utils/read-test-state :game-map) city-coords))))))

    (it "sets failure message on failed conquest"
      (with-redefs [rand (constantly 0.9)]
        (set-test-world! (build-test-map ["A+"]))
        (test-utils/set-test-state! :error-message "")
        (let [army-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))
              city-coords (:pos (get-test-city (test-utils/game-map-atom) "+"))]
          (combat/apply-combat-result! (combat/attempt-conquest (test-utils/read-test-state :game-map) army-coords city-coords))
          (should= (:conquest-failed config/messages) (test-utils/read-test-state :error-message)))))

    (it "returns a result map regardless of outcome"
      (with-redefs [rand (constantly 0.5)]
        (set-test-world! (build-test-map ["A+"]))
        (let [army-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))
              city-coords (:pos (get-test-city (test-utils/game-map-atom) "+"))
              result (combat/attempt-conquest (test-utils/read-test-state :game-map) army-coords city-coords)]
          (should (:world result)))))))

  (context "attempt-city-conquest"
    (it "converts city to player on successful roll"
      (with-redefs [rand (constantly 0.1)]
        (set-test-world! (build-test-map ["+"]))
        (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "+"))]
          (combat/apply-combat-result! (combat/attempt-city-conquest (test-utils/read-test-state :game-map) city-coords))
          (should= :player (:city-status (get-in (test-utils/read-test-state :game-map) city-coords))))))

    (it "returns a result map on successful roll"
      (with-redefs [rand (constantly 0.1)]
        (set-test-world! (build-test-map ["+"]))
        (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "+"))
              result (combat/attempt-city-conquest (test-utils/read-test-state :game-map) city-coords)]
          (should (:world result)))))

    (it "does not convert city on failed roll"
      (with-redefs [rand (constantly 0.9)]
        (set-test-world! (build-test-map ["+"]))
        (test-utils/set-test-state! :error-message "")
        (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "+"))]
          (combat/apply-combat-result! (combat/attempt-city-conquest (test-utils/read-test-state :game-map) city-coords))
          (should= :free (:city-status (get-in (test-utils/read-test-state :game-map) city-coords))))))

    (it "sets failure message on failed roll"
      (with-redefs [rand (constantly 0.9)]
        (set-test-world! (build-test-map ["+"]))
        (test-utils/set-test-state! :error-message "")
        (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "+"))]
          (combat/apply-combat-result! (combat/attempt-city-conquest (test-utils/read-test-state :game-map) city-coords))
          (should= (:conquest-failed config/messages) (test-utils/read-test-state :error-message)))))

    (it "returns a result map on failed roll"
      (with-redefs [rand (constantly 0.9)]
        (set-test-world! (build-test-map ["+"]))
        (test-utils/set-test-state! :error-message "")
        (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "+"))
              result (combat/attempt-city-conquest (test-utils/read-test-state :game-map) city-coords)]
          (should (:world result)))))

    (it "calls conquer-city-contents on success"
      (with-redefs [rand (constantly 0.1)]
        (set-test-world! (build-test-map ["X"]))
        (test-utils/update-test-state! :production assoc [0 0] {:item :fighter :remaining-rounds 5})
        (combat/apply-combat-result! (combat/attempt-city-conquest (test-utils/read-test-state :game-map) [0 0]))
        (should= :player (get-in (test-utils/read-test-state :game-map) [0 0 :city-status]))
        (should-be-nil (get (test-utils/read-test-state :production) [0 0]))))

    (it "updates computer-map city-status on successful conquest"
      (with-redefs [rand (constantly 0.1)]
        (set-test-world! (build-test-map ["X"]))
        (set-test-computer-map! (build-test-map ["X"]))
        (combat/apply-combat-result! (combat/attempt-city-conquest (test-utils/read-test-state :game-map) [0 0]))
        (should= :player (get-in (test-utils/read-test-state :computer-map) [0 0 :city-status])))))

  (context "attempt-fighter-overfly"
    (it "returns a result map"
      (set-test-world! (build-test-map ["FX"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :player :mode :awake :hits 1 :fuel 20})
      (test-utils/set-test-state! :error-message "")
      (let [result (combat/attempt-fighter-overfly (test-utils/read-test-state :game-map) [0 0] [1 0])]
        (should (:world result))
        (should (:messages result))))

    (it "places shot-down fighter on city with 0 hits and 0 steps-remaining"
      (set-test-world! (build-test-map ["FX"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :player :mode :moving :hits 1 :fuel 20
              :steps-remaining 5})
      (test-utils/set-test-state! :error-message "")
      (combat/apply-combat-result! (combat/attempt-fighter-overfly (test-utils/read-test-state :game-map) [0 0] [1 0]))
      (let [shot-down (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
        (should= :fighter (:type shot-down))
        (should= 0 (:hits shot-down))
        (should= 0 (:steps-remaining shot-down))
        (should= :awake (:mode shot-down))))

    (it "removes fighter from original cell"
      (set-test-world! (build-test-map ["FX"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :player :mode :moving :hits 1 :fuel 20
              :steps-remaining 5})
      (test-utils/set-test-state! :error-message "")
      (combat/apply-combat-result! (combat/attempt-fighter-overfly (test-utils/read-test-state :game-map) [0 0] [1 0]))
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "sets :reason to :fighter-shot-down on shot-down fighter"
      (set-test-world! (build-test-map ["FX"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :player :mode :moving :hits 1 :fuel 20
              :steps-remaining 5})
      (test-utils/set-test-state! :error-message "")
      (combat/apply-combat-result! (combat/attempt-fighter-overfly (test-utils/read-test-state :game-map) [0 0] [1 0]))
      (should= :fighter-shot-down (:reason (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

    (it "sets error message to fighter-destroyed-by-city"
      (set-test-world! (build-test-map ["FX"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :player :mode :awake :hits 1 :fuel 20})
      (test-utils/set-test-state! :error-message "")
      (combat/apply-combat-result! (combat/attempt-fighter-overfly (test-utils/read-test-state :game-map) [0 0] [1 0]))
      (should= (:fighter-destroyed-by-city config/messages) (test-utils/read-test-state :error-message)))

    (it "preserves fighter owner on shot-down fighter"
      (set-test-world! (build-test-map ["FX"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :fighter :owner :player :mode :moving :hits 1 :fuel 20
              :steps-remaining 5})
      (test-utils/set-test-state! :error-message "")
      (combat/apply-combat-result! (combat/attempt-fighter-overfly (test-utils/read-test-state :game-map) [0 0] [1 0]))
      (should= :player (:owner (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))))

(describe "conquer-city-contents"
  (before (reset-all-atoms!))

  (it "removes army from conquered city (L27)"
    (set-test-world! (build-test-map ["X"]))
    (update-test-world! assoc-in [0 0 :contents]
           {:type :army :owner :computer :mode :sentry :hits 1})
    (combat/conquer-city-contents [0 0] :player)
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "leaves satellite unchanged (L31)"
    (set-test-world! (build-test-map ["X"]))
    (update-test-world! assoc-in [0 0 :contents]
           {:type :satellite :owner :computer :mode :sentry :hits 1})
    (combat/conquer-city-contents [0 0] :player)
    (should= :satellite (:type (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

  (it "flips fighter ownership and wakes it (L24)"
    (set-test-world! (build-test-map ["X"]))
    (update-test-world! assoc-in [0 0 :contents]
           {:type :fighter :owner :computer :mode :moving :hits 1
            :target [5 5] :reason :patrol})
    (combat/conquer-city-contents [0 0] :player)
    (let [f (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= :player (:owner f))
      (should= :awake (:mode f))
      (should-be-nil (:target f))
      (should-be-nil (:reason f))))

  (it "flips transport and clears cargo (L41, L42)"
    (set-test-world! (build-test-map ["X"]))
    (update-test-world! assoc-in [0 0 :contents]
           {:type :transport :owner :computer :mode :moving :hits 1
            :army-count 3 :awake-armies 2})
    (combat/conquer-city-contents [0 0] :player)
    (let [t (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= :player (:owner t))
      (should= 0 (:army-count t))
      (should= 0 (:awake-armies t))))

  (it "flips carrier and clears cargo (L43, L44)"
    (set-test-world! (build-test-map ["X"]))
    (update-test-world! assoc-in [0 0 :contents]
           {:type :carrier :owner :computer :mode :moving :hits 8
            :fighter-count 4 :awake-fighters 2})
    (combat/conquer-city-contents [0 0] :player)
    (let [c (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= :player (:owner c))
      (should= 0 (:fighter-count c))
      (should= 0 (:awake-fighters c))))

  (it "clears standing orders (L49)"
    (set-test-world! (build-test-map ["X"]))
    (update-test-world! assoc-in [0 0 :marching-orders] [1 1])
    (update-test-world! assoc-in [0 0 :flight-path] [[1 0] [2 0]])
    (combat/conquer-city-contents [0 0] :player)
    (should-be-nil (:marching-orders (get-in (test-utils/read-test-state :game-map) [0 0])))
    (should-be-nil (:flight-path (get-in (test-utils/read-test-state :game-map) [0 0])))))

(describe "dead-escort-destroyer?"
  (it "true for destroyer with escort-transport-id"
    (should (combat/dead-escort-destroyer? {:type :destroyer :escort-transport-id 42})))
  (it "false for destroyer without escort-transport-id"
    (should-not (combat/dead-escort-destroyer? {:type :destroyer})))
  (it "false for non-destroyer"
    (should-not (combat/dead-escort-destroyer? {:type :transport :escort-transport-id 42}))))

(describe "dead-escort-transport?"
  (it "true for transport with escort-destroyer-id"
    (should (combat/dead-escort-transport? {:type :transport :escort-destroyer-id 7})))
  (it "false for transport without escort-destroyer-id"
    (should-not (combat/dead-escort-transport? {:type :transport})))
  (it "false for non-transport"
    (should-not (combat/dead-escort-transport? {:type :destroyer :escort-destroyer-id 7}))))

(describe "escort death handling"
  (before (reset-all-atoms!))

  (context "clear-escort-on-death"
    (it "clears transport escort-destroyer-id when destroyer dies (L193)"
      (set-test-world! (build-test-map ["~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :transport :owner :computer :mode :moving :hits 1
              :transport-id 42 :escort-destroyer-id 7})
      (combat/clear-escort-on-death {:type :destroyer :escort-transport-id 42})
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents :escort-destroyer-id])))

    (it "sets destroyer to seeking when transport dies (L202)"
      (set-test-world! (build-test-map ["~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :destroyer :owner :computer :mode :moving :hits 3
              :destroyer-id 7 :escort-transport-id 42 :escort-mode :escorting})
      (combat/clear-escort-on-death {:type :transport :escort-destroyer-id 7})
      (let [d (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :seeking (:escort-mode d))
        (should-be-nil (:escort-transport-id d))))

    (it "clears carrier group-battleship-id when battleship dies (L156)"
      (set-test-world! (build-test-map ["~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :carrier :owner :computer :mode :moving :hits 8
              :carrier-id 10 :group-battleship-id 5})
      (combat/clear-escort-on-death {:type :battleship :escort-carrier-id 10 :escort-id 5})
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents :group-battleship-id])))

    (it "removes submarine from carrier group-submarine-ids (L156)"
      (set-test-world! (build-test-map ["~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :carrier :owner :computer :mode :moving :hits 8
              :carrier-id 10 :group-submarine-ids [5 7]})
      (combat/clear-escort-on-death {:type :submarine :escort-carrier-id 10 :escort-id 5})
      (should= [7] (get-in (test-utils/read-test-state :game-map) [0 0 :contents :group-submarine-ids])))

    (it "releases all carrier escorts to seeking when carrier dies (L170, L184)"
      (set-test-world! (build-test-map ["~~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :battleship :owner :computer :mode :moving :hits 10
              :escort-carrier-id 10 :escort-mode :escorting :orbit-angle 45})
      (update-test-world! assoc-in [1 0 :contents]
             {:type :submarine :owner :computer :mode :moving :hits 2
              :escort-carrier-id 10 :escort-mode :escorting :orbit-angle 90})
      (combat/clear-escort-on-death {:type :carrier :carrier-id 10})
      (let [b (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :seeking (:escort-mode b))
        (should-be-nil (:escort-carrier-id b)))
      (let [s (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (should= :seeking (:escort-mode s))
        (should-be-nil (:escort-carrier-id s))))))

(describe "attempt-attack advanced"
  (before (reset-all-atoms!))

  (context "drown-excess-cargo (L219)"
    (it "drowns excess fighters when carrier takes damage"
      (set-test-world! (build-test-map ["~~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :submarine :owner :player :mode :awake :hits 2})
      (update-test-world! assoc-in [1 0 :contents]
             {:type :carrier :owner :computer :mode :sentry :hits 8
              :fighter-count 6 :awake-fighters 3})
      ;; carrier hits sub (1dmg), sub hits carrier (3dmg), carrier hits sub (dies)
      (let [rolls (atom [0.6 0.4 0.6])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
          (let [survivor (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
            (should= 5 (:hits survivor))
            (should= 5 (:fighter-count survivor))
            (should= 3 (:awake-fighters survivor))))))

    (it "does not drown cargo when within capacity (L223)"
      (set-test-world! (build-test-map ["~~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :submarine :owner :player :mode :awake :hits 2})
      (update-test-world! assoc-in [1 0 :contents]
             {:type :carrier :owner :computer :mode :sentry :hits 8
              :fighter-count 3 :awake-fighters 2})
      ;; Same combat: carrier survives with 5 hits, capacity 5
      (let [rolls (atom [0.6 0.4 0.6])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
          (let [survivor (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
            (should= 3 (:fighter-count survivor))
            (should= 2 (:awake-fighters survivor)))))))

  (context "dead unit identification (L245)"
    (it "clears dead attacker escort when attacker loses"
      (set-test-world! (build-test-map ["~~~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :destroyer :owner :player :mode :awake :hits 1
              :destroyer-id 7 :escort-transport-id 42 :escort-mode :escorting})
      (update-test-world! assoc-in [1 0 :contents]
             {:type :battleship :owner :computer :mode :sentry :hits 10})
      (update-test-world! assoc-in [2 0 :contents]
             {:type :transport :owner :player :mode :moving :hits 1
              :transport-id 42 :escort-destroyer-id 7})
      (with-redefs [rand (constantly 0.6)]
        (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [2 0 :contents :escort-destroyer-id]))))

    (it "clears dead defender escort when attacker wins"
      (set-test-world! (build-test-map ["~~~"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :battleship :owner :player :mode :awake :hits 10})
      (update-test-world! assoc-in [1 0 :contents]
             {:type :destroyer :owner :computer :mode :sentry :hits 1
              :destroyer-id 7 :escort-transport-id 42 :escort-mode :escorting})
      (update-test-world! assoc-in [2 0 :contents]
             {:type :transport :owner :computer :mode :moving :hits 1
              :transport-id 42 :escort-destroyer-id 7})
      (with-redefs [rand (constantly 0.4)]
        (combat/apply-combat-result! (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [2 0 :contents :escort-destroyer-id]))))))

(run-specs)
