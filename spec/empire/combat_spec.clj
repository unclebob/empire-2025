(ns empire.combat-spec
  (:require [speclj.core :refer :all]
            [empire.combat :as combat]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map get-test-city get-test-unit set-test-unit reset-all-atoms!]]
            [empire.units.dispatcher :as dispatcher]
            [empire.containers.helpers :as uc]
            [empire.computer.core :as computer-core]
            [empire.computer.production :as comp-production]
            [empire.player.production :as production]))

(describe "predicates"
  (before (reset-all-atoms!))

  (context "hostile-city?"
    (it "returns true for free city"
      (reset! atoms/game-map (build-test-map ["+"]))
      (let [city-coords (:pos (get-test-city atoms/game-map "+"))]
        (should (combat/hostile-city? city-coords))))

    (it "returns true for computer city"
      (reset! atoms/game-map (build-test-map ["X"]))
      (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
        (should (combat/hostile-city? city-coords))))

    (it "returns false for player city"
      (reset! atoms/game-map (build-test-map ["O"]))
      (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
        (should-not (combat/hostile-city? city-coords))))

    (it "returns false for non-city cells"
      (reset! atoms/game-map (build-test-map ["#"]))
      (should-not (combat/hostile-city? [0 0])))

    (it "returns false for sea cells"
      (reset! atoms/game-map (build-test-map ["~"]))
      (should-not (combat/hostile-city? [0 0]))))

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
      (reset! atoms/game-map (build-test-map ["A#"]))
      (set-test-unit atoms/game-map "A" :hits 1)
      (should= false (combat/attempt-attack [0 0] [1 0])))

    (it "returns false when target unit is friendly"
      (reset! atoms/game-map (build-test-map ["AA"]))
      (set-test-unit atoms/game-map "A1" :hits 1)
      (set-test-unit atoms/game-map "A2" :hits 1)
      (should= false (combat/attempt-attack [0 0] [1 0])))

    (it "returns true when attacking enemy unit"
      (reset! atoms/game-map (build-test-map ["Aa"]))
      (set-test-unit atoms/game-map "A" :hits 1)
      (set-test-unit atoms/game-map "a" :hits 1)
      (with-redefs [rand (constantly 0.4)]
        (should= true (combat/attempt-attack [0 0] [1 0]))))

    (it "attacker wins and occupies cell when victorious"
      (reset! atoms/game-map (build-test-map ["Da"]))
      (set-test-unit atoms/game-map "D" :hits 3)
      (set-test-unit atoms/game-map "a" :hits 1)
      (with-redefs [rand (constantly 0.4)]
        (combat/attempt-attack [0 0] [1 0])
        (should= nil (:contents (get-in @atoms/game-map [0 0])))
        (should= :destroyer (:type (:contents (get-in @atoms/game-map [1 0]))))
        (should= :player (:owner (:contents (get-in @atoms/game-map [1 0]))))))

    (it "attacker loses and defender remains"
      (reset! atoms/game-map (build-test-map ["aD"]))
      (set-test-unit atoms/game-map "a" :hits 1)
      (set-test-unit atoms/game-map "D" :hits 3)
      (with-redefs [rand (constantly 0.6)]
        (combat/attempt-attack [0 0] [1 0])
        (should= nil (:contents (get-in @atoms/game-map [0 0])))
        (should= :destroyer (:type (:contents (get-in @atoms/game-map [1 0]))))
        (should= :player (:owner (:contents (get-in @atoms/game-map [1 0]))))))

    (it "removes attacker from original cell even when losing"
      (reset! atoms/game-map (build-test-map ["Tb"]))
      (set-test-unit atoms/game-map "T" :hits 1)
      (set-test-unit atoms/game-map "b" :hits 10)
      (with-redefs [rand (constantly 0.6)]
        (combat/attempt-attack [0 0] [1 0])
        (should= nil (:contents (get-in @atoms/game-map [0 0])))))

    (it "survivor has reduced hits after combat"
      (reset! atoms/game-map (build-test-map ["Dd"]))
      (set-test-unit atoms/game-map "D" :hits 3)
      (set-test-unit atoms/game-map "d" :hits 3)
      ;; Rolls: 0.4 (D hits d:2), 0.6 (d hits D:2), 0.4 (D hits d:1), 0.4 (D hits d:0)
      (let [rolls (atom [0.4 0.6 0.4 0.4])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/attempt-attack [0 0] [1 0])
          (let [survivor (:contents (get-in @atoms/game-map [1 0]))]
            (should= :destroyer (:type survivor))
            (should= :player (:owner survivor))
            (should= 2 (:hits survivor))))))

    (it "displays combat log when attacker wins"
      (reset! atoms/game-map (build-test-map ["Da"]))
      (set-test-unit atoms/game-map "D" :hits 3)
      (set-test-unit atoms/game-map "a" :hits 1)
      (reset! atoms/turn-message "")
      (with-redefs [rand (constantly 0.4)]
        (combat/attempt-attack [0 0] [1 0])
        (should= "a-1. Army destroyed." @atoms/turn-message)))

    (it "displays combat log when attacker loses"
      (reset! atoms/game-map (build-test-map ["Ad"]))
      (set-test-unit atoms/game-map "A" :hits 1)
      (set-test-unit atoms/game-map "d" :hits 3)
      (reset! atoms/turn-message "")
      (with-redefs [rand (constantly 0.6)]
        (combat/attempt-attack [0 0] [1 0])
        (should= "A-1. Army destroyed." @atoms/turn-message)))

    (it "displays combat log with multiple exchanges"
      (reset! atoms/game-map (build-test-map ["Dd"]))
      (set-test-unit atoms/game-map "D" :hits 3)
      (set-test-unit atoms/game-map "d" :hits 3)
      (reset! atoms/turn-message "")
      ;; Rolls: 0.4 (D hits d:2), 0.6 (d hits D:2), 0.4 (D hits d:1), 0.4 (D hits d:0)
      (let [rolls (atom [0.4 0.6 0.4 0.4])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/attempt-attack [0 0] [1 0])
          (should= "d-1,D-1,d-1,d-1. Destroyer destroyed." @atoms/turn-message))))

    (it "displays combat log for submarine vs carrier"
      (reset! atoms/game-map (build-test-map ["Sc"]))
      (set-test-unit atoms/game-map "S" :hits 2)
      (set-test-unit atoms/game-map "c" :hits 8)
      (reset! atoms/turn-message "")
      ;; Rolls: 0.6 (c hits S:1), 0.6 (c hits S:0)
      (let [rolls (atom [0.6 0.6])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/attempt-attack [0 0] [1 0])
          (should= "S-1,S-1. Submarine destroyed." @atoms/turn-message))))

    (it "displays combat log for submarine defeating carrier"
      (reset! atoms/game-map (build-test-map ["Sc"]))
      (set-test-unit atoms/game-map "S" :hits 2)
      (set-test-unit atoms/game-map "c" :hits 8)
      (reset! atoms/turn-message "")
      ;; Rolls: 0.4 (S hits c:5), 0.6 (c hits S:1), 0.4 (S hits c:2), 0.4 (S hits c:0)
      (let [rolls (atom [0.4 0.6 0.4 0.4])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/attempt-attack [0 0] [1 0])
          (should= "c-3,S-1,c-3,c-3. Carrier destroyed." @atoms/turn-message)))))

  (context "attempt-conquest"
    (it "removes army from original cell on success"
      (with-redefs [rand (constantly 0.1)]
        (reset! atoms/game-map (build-test-map ["A+"]))
        (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
              city-coords (:pos (get-test-city atoms/game-map "+"))]
          (combat/attempt-conquest army-coords city-coords)
          (should= nil (:contents (get-in @atoms/game-map army-coords))))))

    (it "converts city to player on success"
      (with-redefs [rand (constantly 0.1)]
        (reset! atoms/game-map (build-test-map ["A+"]))
        (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
              city-coords (:pos (get-test-city atoms/game-map "+"))]
          (combat/attempt-conquest army-coords city-coords)
          (should= :player (:city-status (get-in @atoms/game-map city-coords))))))

    (it "removes army from original cell on failure"
      (with-redefs [rand (constantly 0.9)]
        (reset! atoms/game-map (build-test-map ["A+"]))
        (reset! atoms/error-message "")
        (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
              city-coords (:pos (get-test-city atoms/game-map "+"))]
          (combat/attempt-conquest army-coords city-coords)
          (should= nil (:contents (get-in @atoms/game-map army-coords))))))

    (it "keeps city status on failure"
      (with-redefs [rand (constantly 0.9)]
        (reset! atoms/game-map (build-test-map ["A+"]))
        (reset! atoms/error-message "")
        (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
              city-coords (:pos (get-test-city atoms/game-map "+"))]
          (combat/attempt-conquest army-coords city-coords)
          (should= :free (:city-status (get-in @atoms/game-map city-coords))))))

    (it "sets failure message on failed conquest"
      (with-redefs [rand (constantly 0.9)]
        (reset! atoms/game-map (build-test-map ["A+"]))
        (reset! atoms/error-message "")
        (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
              city-coords (:pos (get-test-city atoms/game-map "+"))]
          (combat/attempt-conquest army-coords city-coords)
          (should= (:conquest-failed config/messages) @atoms/error-message))))

    (it "returns true regardless of outcome"
      (with-redefs [rand (constantly 0.5)]
        (reset! atoms/game-map (build-test-map ["A+"]))
        (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
              city-coords (:pos (get-test-city atoms/game-map "+"))]
          (should (combat/attempt-conquest army-coords city-coords))))))

  (context "attempt-city-conquest"
    (it "converts city to player on successful roll"
      (with-redefs [rand (constantly 0.1)]
        (reset! atoms/game-map (build-test-map ["+"]))
        (let [city-coords (:pos (get-test-city atoms/game-map "+"))]
          (combat/attempt-city-conquest city-coords)
          (should= :player (:city-status (get-in @atoms/game-map city-coords))))))

    (it "returns true on successful roll"
      (with-redefs [rand (constantly 0.1)]
        (reset! atoms/game-map (build-test-map ["+"]))
        (let [city-coords (:pos (get-test-city atoms/game-map "+"))]
          (should (combat/attempt-city-conquest city-coords)))))

    (it "does not convert city on failed roll"
      (with-redefs [rand (constantly 0.9)]
        (reset! atoms/game-map (build-test-map ["+"]))
        (reset! atoms/error-message "")
        (let [city-coords (:pos (get-test-city atoms/game-map "+"))]
          (combat/attempt-city-conquest city-coords)
          (should= :free (:city-status (get-in @atoms/game-map city-coords))))))

    (it "sets failure message on failed roll"
      (with-redefs [rand (constantly 0.9)]
        (reset! atoms/game-map (build-test-map ["+"]))
        (reset! atoms/error-message "")
        (let [city-coords (:pos (get-test-city atoms/game-map "+"))]
          (combat/attempt-city-conquest city-coords)
          (should= (:conquest-failed config/messages) @atoms/error-message))))

    (it "returns true on failed roll"
      (with-redefs [rand (constantly 0.9)]
        (reset! atoms/game-map (build-test-map ["+"]))
        (reset! atoms/error-message "")
        (let [city-coords (:pos (get-test-city atoms/game-map "+"))]
          (should (combat/attempt-city-conquest city-coords)))))

    (it "calls conquer-city-contents on success"
      (with-redefs [rand (constantly 0.1)]
        (reset! atoms/game-map (build-test-map ["X"]))
        (swap! atoms/production assoc [0 0] {:item :fighter :remaining-rounds 5})
        (combat/attempt-city-conquest [0 0])
        (should= :player (get-in @atoms/game-map [0 0 :city-status]))
        (should-be-nil (get @atoms/production [0 0]))))

    (it "updates computer-map city-status on successful conquest"
      (with-redefs [rand (constantly 0.1)]
        (reset! atoms/game-map (build-test-map ["X"]))
        (reset! atoms/computer-map (build-test-map ["X"]))
        (combat/attempt-city-conquest [0 0])
        (should= :player (get-in @atoms/computer-map [0 0 :city-status])))))

  (context "attempt-fighter-overfly"
    (it "returns true"
      (reset! atoms/game-map (build-test-map ["FX"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :fighter :owner :player :mode :awake :hits 1 :fuel 20})
      (reset! atoms/error-message "")
      (should (combat/attempt-fighter-overfly [0 0] [1 0])))

    (it "places shot-down fighter on city with 0 hits and 0 steps-remaining"
      (reset! atoms/game-map (build-test-map ["FX"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :fighter :owner :player :mode :moving :hits 1 :fuel 20
              :steps-remaining 5})
      (reset! atoms/error-message "")
      (combat/attempt-fighter-overfly [0 0] [1 0])
      (let [shot-down (:contents (get-in @atoms/game-map [1 0]))]
        (should= :fighter (:type shot-down))
        (should= 0 (:hits shot-down))
        (should= 0 (:steps-remaining shot-down))
        (should= :awake (:mode shot-down))))

    (it "removes fighter from original cell"
      (reset! atoms/game-map (build-test-map ["FX"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :fighter :owner :player :mode :moving :hits 1 :fuel 20
              :steps-remaining 5})
      (reset! atoms/error-message "")
      (combat/attempt-fighter-overfly [0 0] [1 0])
      (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))

    (it "sets :reason to :fighter-shot-down on shot-down fighter"
      (reset! atoms/game-map (build-test-map ["FX"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :fighter :owner :player :mode :moving :hits 1 :fuel 20
              :steps-remaining 5})
      (reset! atoms/error-message "")
      (combat/attempt-fighter-overfly [0 0] [1 0])
      (should= :fighter-shot-down (:reason (:contents (get-in @atoms/game-map [1 0])))))

    (it "sets error message to fighter-destroyed-by-city"
      (reset! atoms/game-map (build-test-map ["FX"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :fighter :owner :player :mode :awake :hits 1 :fuel 20})
      (reset! atoms/error-message "")
      (combat/attempt-fighter-overfly [0 0] [1 0])
      (should= (:fighter-destroyed-by-city config/messages) @atoms/error-message))

    (it "preserves fighter owner on shot-down fighter"
      (reset! atoms/game-map (build-test-map ["FX"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :fighter :owner :player :mode :moving :hits 1 :fuel 20
              :steps-remaining 5})
      (reset! atoms/error-message "")
      (combat/attempt-fighter-overfly [0 0] [1 0])
      (should= :player (:owner (:contents (get-in @atoms/game-map [1 0])))))))

(describe "conquer-city-contents"
  (before (reset-all-atoms!))

  (it "removes army from conquered city (L27)"
    (reset! atoms/game-map (build-test-map ["X"]))
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :army :owner :computer :mode :sentry :hits 1})
    (combat/conquer-city-contents [0 0] :player)
    (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))

  (it "leaves satellite unchanged (L31)"
    (reset! atoms/game-map (build-test-map ["X"]))
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :satellite :owner :computer :mode :sentry :hits 1})
    (combat/conquer-city-contents [0 0] :player)
    (should= :satellite (:type (:contents (get-in @atoms/game-map [0 0])))))

  (it "flips fighter ownership and wakes it (L24)"
    (reset! atoms/game-map (build-test-map ["X"]))
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :fighter :owner :computer :mode :moving :hits 1
            :target [5 5] :reason :patrol})
    (combat/conquer-city-contents [0 0] :player)
    (let [f (:contents (get-in @atoms/game-map [0 0]))]
      (should= :player (:owner f))
      (should= :awake (:mode f))
      (should-be-nil (:target f))
      (should-be-nil (:reason f))))

  (it "flips transport and clears cargo (L41, L42)"
    (reset! atoms/game-map (build-test-map ["X"]))
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :transport :owner :computer :mode :moving :hits 1
            :army-count 3 :awake-armies 2})
    (combat/conquer-city-contents [0 0] :player)
    (let [t (:contents (get-in @atoms/game-map [0 0]))]
      (should= :player (:owner t))
      (should= 0 (:army-count t))
      (should= 0 (:awake-armies t))))

  (it "flips carrier and clears cargo (L43, L44)"
    (reset! atoms/game-map (build-test-map ["X"]))
    (swap! atoms/game-map assoc-in [0 0 :contents]
           {:type :carrier :owner :computer :mode :moving :hits 8
            :fighter-count 4 :awake-fighters 2})
    (combat/conquer-city-contents [0 0] :player)
    (let [c (:contents (get-in @atoms/game-map [0 0]))]
      (should= :player (:owner c))
      (should= 0 (:fighter-count c))
      (should= 0 (:awake-fighters c))))

  (it "clears standing orders (L49)"
    (reset! atoms/game-map (build-test-map ["X"]))
    (swap! atoms/game-map assoc-in [0 0 :marching-orders] [1 1])
    (swap! atoms/game-map assoc-in [0 0 :flight-path] [[1 0] [2 0]])
    (combat/conquer-city-contents [0 0] :player)
    (should-be-nil (:marching-orders (get-in @atoms/game-map [0 0])))
    (should-be-nil (:flight-path (get-in @atoms/game-map [0 0])))))

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
      (reset! atoms/game-map (build-test-map ["~"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :transport :owner :computer :mode :moving :hits 1
              :transport-id 42 :escort-destroyer-id 7})
      (combat/clear-escort-on-death {:type :destroyer :escort-transport-id 42})
      (should-be-nil (get-in @atoms/game-map [0 0 :contents :escort-destroyer-id])))

    (it "sets destroyer to seeking when transport dies (L202)"
      (reset! atoms/game-map (build-test-map ["~"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :destroyer :owner :computer :mode :moving :hits 3
              :destroyer-id 7 :escort-transport-id 42 :escort-mode :escorting})
      (combat/clear-escort-on-death {:type :transport :escort-destroyer-id 7})
      (let [d (get-in @atoms/game-map [0 0 :contents])]
        (should= :seeking (:escort-mode d))
        (should-be-nil (:escort-transport-id d))))

    (it "clears carrier group-battleship-id when battleship dies (L156)"
      (reset! atoms/game-map (build-test-map ["~"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :carrier :owner :computer :mode :moving :hits 8
              :carrier-id 10 :group-battleship-id 5})
      (combat/clear-escort-on-death {:type :battleship :escort-carrier-id 10 :escort-id 5})
      (should-be-nil (get-in @atoms/game-map [0 0 :contents :group-battleship-id])))

    (it "removes submarine from carrier group-submarine-ids (L156)"
      (reset! atoms/game-map (build-test-map ["~"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :carrier :owner :computer :mode :moving :hits 8
              :carrier-id 10 :group-submarine-ids [5 7]})
      (combat/clear-escort-on-death {:type :submarine :escort-carrier-id 10 :escort-id 5})
      (should= [7] (get-in @atoms/game-map [0 0 :contents :group-submarine-ids])))

    (it "releases all carrier escorts to seeking when carrier dies (L170, L184)"
      (reset! atoms/game-map (build-test-map ["~~"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :battleship :owner :computer :mode :moving :hits 10
              :escort-carrier-id 10 :escort-mode :escorting :orbit-angle 45})
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :submarine :owner :computer :mode :moving :hits 2
              :escort-carrier-id 10 :escort-mode :escorting :orbit-angle 90})
      (combat/clear-escort-on-death {:type :carrier :carrier-id 10})
      (let [b (get-in @atoms/game-map [0 0 :contents])]
        (should= :seeking (:escort-mode b))
        (should-be-nil (:escort-carrier-id b)))
      (let [s (get-in @atoms/game-map [1 0 :contents])]
        (should= :seeking (:escort-mode s))
        (should-be-nil (:escort-carrier-id s))))))

(describe "attempt-attack advanced"
  (before (reset-all-atoms!))

  (context "drown-excess-cargo (L219)"
    (it "drowns excess fighters when carrier takes damage"
      (reset! atoms/game-map (build-test-map ["~~"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :submarine :owner :player :mode :awake :hits 2})
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :carrier :owner :computer :mode :sentry :hits 8
              :fighter-count 6 :awake-fighters 3})
      ;; carrier hits sub (1dmg), sub hits carrier (3dmg), carrier hits sub (dies)
      (let [rolls (atom [0.6 0.4 0.6])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/attempt-attack [0 0] [1 0])
          (let [survivor (get-in @atoms/game-map [1 0 :contents])]
            (should= 5 (:hits survivor))
            (should= 5 (:fighter-count survivor))
            (should= 3 (:awake-fighters survivor))))))

    (it "does not drown cargo when within capacity (L223)"
      (reset! atoms/game-map (build-test-map ["~~"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :submarine :owner :player :mode :awake :hits 2})
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :carrier :owner :computer :mode :sentry :hits 8
              :fighter-count 3 :awake-fighters 2})
      ;; Same combat: carrier survives with 5 hits, capacity 5
      (let [rolls (atom [0.6 0.4 0.6])]
        (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
          (combat/attempt-attack [0 0] [1 0])
          (let [survivor (get-in @atoms/game-map [1 0 :contents])]
            (should= 3 (:fighter-count survivor))
            (should= 2 (:awake-fighters survivor)))))))

  (context "dead unit identification (L245)"
    (it "clears dead attacker escort when attacker loses"
      (reset! atoms/game-map (build-test-map ["~~~"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :destroyer :owner :player :mode :awake :hits 1
              :destroyer-id 7 :escort-transport-id 42 :escort-mode :escorting})
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :battleship :owner :computer :mode :sentry :hits 10})
      (swap! atoms/game-map assoc-in [2 0 :contents]
             {:type :transport :owner :player :mode :moving :hits 1
              :transport-id 42 :escort-destroyer-id 7})
      (with-redefs [rand (constantly 0.6)]
        (combat/attempt-attack [0 0] [1 0])
        (should-be-nil (get-in @atoms/game-map [2 0 :contents :escort-destroyer-id]))))

    (it "clears dead defender escort when attacker wins"
      (reset! atoms/game-map (build-test-map ["~~~"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :battleship :owner :player :mode :awake :hits 10})
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :destroyer :owner :computer :mode :sentry :hits 1
              :destroyer-id 7 :escort-transport-id 42 :escort-mode :escorting})
      (swap! atoms/game-map assoc-in [2 0 :contents]
             {:type :transport :owner :computer :mode :moving :hits 1
              :transport-id 42 :escort-destroyer-id 7})
      (with-redefs [rand (constantly 0.4)]
        (combat/attempt-attack [0 0] [1 0])
        (should-be-nil (get-in @atoms/game-map [2 0 :contents :escort-destroyer-id]))))))

(run-specs)
