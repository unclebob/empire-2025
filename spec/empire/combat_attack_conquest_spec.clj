(ns empire.game-mechanics.services.combat-attack-conquest-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.services.combat :as combat]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map get-test-city get-test-unit set-test-contents! set-test-unit reset-all-atoms! set-test-computer-map!
                                       set-test-player-map! set-test-world! update-test-world!]]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.computer.core :as computer-core]
            [empire.computer.production :as comp-production]
            [empire.player.production :as production]))
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

    (it "returns false when attacker has nil hits"
      (set-test-world! (build-test-map ["Aa"]))
      (set-test-unit (test-utils/game-map-atom) "A" :hits nil)
      (set-test-unit (test-utils/game-map-atom) "a" :hits 1)
      (should= false (combat/attempt-attack (test-utils/read-test-state :game-map) [0 0] [1 0])))

    (it "returns false when defender has nil hits"
      (set-test-world! (build-test-map ["Aa"]))
      (set-test-unit (test-utils/game-map-atom) "A" :hits 1)
      (set-test-unit (test-utils/game-map-atom) "a" :hits nil)
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
                   (test-utils/read-test-state :turn-message)))))

    (it "coastal army attack removes both units when the army wins"
      (set-test-world! (build-test-map ["Ad"]))
      (set-test-player-map! (build-test-map ["Ad"]))
      (set-test-unit (test-utils/game-map-atom) "A" :hits 1)
      (set-test-unit (test-utils/game-map-atom) "d" :hits 3)
      (test-utils/set-test-state! :turn-message "")
      (with-redefs [rand (constantly 0.4)]
        (combat/apply-combat-result! (combat/attempt-coastal-army-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))
        (should-be-nil (get-in (test-utils/read-test-state :player-map) [0 0 :contents]))
        (should-contain "That army drowned." (test-utils/read-test-state :turn-message))))

    (it "coastal army attack leaves the ship when the army loses"
      (set-test-world! (build-test-map ["Ad"]))
      (set-test-unit (test-utils/game-map-atom) "A" :hits 1)
      (set-test-unit (test-utils/game-map-atom) "d" :hits 3)
      (test-utils/set-test-state! :turn-message "")
      (with-redefs [rand (constantly 0.6)]
        (combat/apply-combat-result! (combat/attempt-coastal-army-attack (test-utils/read-test-state :game-map) [0 0] [1 0]))
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
        (should= :destroyer (:type (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))
        (should-contain "That army drowned." (test-utils/read-test-state :turn-message)))))

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
          (should= (:conquest-failed config/messages) (test-utils/read-test-state :turn-message))
          (should= "" (test-utils/read-test-state :error-message)))))

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

    (it "sets a success turn message and clears stale conquest error on successful roll"
      (with-redefs [rand (constantly 0.1)]
        (set-test-world! (build-test-map ["+"]))
        (test-utils/set-test-state! :error-message (:conquest-failed config/messages))
        (test-utils/set-test-state! :error-until Long/MAX_VALUE)
        (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "+"))]
          (combat/apply-combat-result! (combat/attempt-city-conquest (test-utils/read-test-state :game-map) city-coords))
          (should= "City conquered." (test-utils/read-test-state :turn-message))
          (should= "" (test-utils/read-test-state :error-message))
          (should= 0 (test-utils/read-test-state :error-until)))))

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
          (should= (:conquest-failed config/messages) (test-utils/read-test-state :turn-message))
          (should= "" (test-utils/read-test-state :error-message)))))

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
        (should= :player (get-in (test-utils/read-test-state :computer-map) [0 0 :city-status]))))

    (it "declares resignation when player conquers the last computer city"
      (with-redefs [rand (constantly 0.1)]
        (set-test-world! (build-test-map ["X"]))
        (set-test-computer-map! (build-test-map ["X"]))
        (test-utils/set-test-state! :game-over-check-enabled true)
        (test-utils/set-test-state! :player-items [[0 0]])
        (test-utils/set-test-state! :computer-items [[1 0]])
        (combat/apply-combat-result! (combat/attempt-city-conquest (test-utils/read-test-state :game-map) [0 0]))
        (should (test-utils/read-test-state :paused))
        (should-contain "I Resign" (test-utils/read-test-state :error-message))
        (should= :actual-map (test-utils/read-test-state :map-to-display))
        (should= [] (vec (test-utils/read-test-state :player-items)))
        (should= [] (vec (test-utils/read-test-state :computer-items))))))

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
