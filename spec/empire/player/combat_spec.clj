(ns empire.player.combat-spec
  (:require [speclj.core :refer :all]
            [empire.player.combat :as combat]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map get-test-city get-test-unit set-test-unit reset-all-atoms!]]
            [empire.units.dispatcher :as dispatcher]))

(describe "hostile-city?"
  (before (reset-all-atoms!))
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

(describe "attempt-conquest"
  (before (reset-all-atoms!))
  (with-stubs)

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
      (reset! atoms/line3-message "")
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (combat/attempt-conquest army-coords city-coords)
        (should= nil (:contents (get-in @atoms/game-map army-coords))))))

  (it "keeps city status on failure"
    (with-redefs [rand (constantly 0.9)]
      (reset! atoms/game-map (build-test-map ["A+"]))
      (reset! atoms/line3-message "")
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (combat/attempt-conquest army-coords city-coords)
        (should= :free (:city-status (get-in @atoms/game-map city-coords))))))

  (it "sets failure message on failed conquest"
    (with-redefs [rand (constantly 0.9)]
      (reset! atoms/game-map (build-test-map ["A+"]))
      (reset! atoms/line3-message "")
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (combat/attempt-conquest army-coords city-coords)
        (should= (:conquest-failed config/messages) @atoms/line3-message))))

  (it "returns true regardless of outcome"
    (with-redefs [rand (constantly 0.5)]
      (reset! atoms/game-map (build-test-map ["A+"]))
      (let [army-coords (:pos (get-test-unit atoms/game-map "A"))
            city-coords (:pos (get-test-city atoms/game-map "+"))]
        (should (combat/attempt-conquest army-coords city-coords))))))

(describe "hostile-unit?"
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
    (should-not (combat/hostile-unit? nil :player))))

(describe "format-combat-log"
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

(describe "fight-round"
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
        (should= {:hit :defender :damage 1} log-entry)))))

(describe "resolve-combat"
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
        (should= :player (:owner (:survivor result)))))))

(describe "attempt-attack"
  (before (reset-all-atoms!))

  (it "returns false when target has no unit"
    (reset! atoms/game-map (build-test-map ["A#"]))
    (set-test-unit atoms/game-map "A" :hits 1)
    (should-not (combat/attempt-attack [0 0] [0 1])))

  (it "returns false when target unit is friendly"
    (reset! atoms/game-map (build-test-map ["AA"]))
    (set-test-unit atoms/game-map "A1" :hits 1)
    (set-test-unit atoms/game-map "A2" :hits 1)
    (should-not (combat/attempt-attack [0 0] [0 1])))

  (it "returns true when attacking enemy unit"
    (reset! atoms/game-map (build-test-map ["Aa"]))
    (set-test-unit atoms/game-map "A" :hits 1)
    (set-test-unit atoms/game-map "a" :hits 1)
    (with-redefs [rand (constantly 0.4)]
      (should (combat/attempt-attack [0 0] [0 1]))))

  (it "attacker wins and occupies cell when victorious"
    (reset! atoms/game-map (build-test-map ["Da"]))
    (set-test-unit atoms/game-map "D" :hits 3)
    (set-test-unit atoms/game-map "a" :hits 1)
    (with-redefs [rand (constantly 0.4)]
      (combat/attempt-attack [0 0] [0 1])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :destroyer (:type (:contents (get-in @atoms/game-map [0 1]))))
      (should= :player (:owner (:contents (get-in @atoms/game-map [0 1]))))))

  (it "attacker loses and defender remains"
    (reset! atoms/game-map (build-test-map ["aD"]))
    (set-test-unit atoms/game-map "a" :hits 1)
    (set-test-unit atoms/game-map "D" :hits 3)
    (with-redefs [rand (constantly 0.6)]
      (combat/attempt-attack [0 0] [0 1])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :destroyer (:type (:contents (get-in @atoms/game-map [0 1]))))
      (should= :player (:owner (:contents (get-in @atoms/game-map [0 1]))))))

  (it "removes attacker from original cell even when losing"
    (reset! atoms/game-map (build-test-map ["Tb"]))
    (set-test-unit atoms/game-map "T" :hits 1)
    (set-test-unit atoms/game-map "b" :hits 10)
    (with-redefs [rand (constantly 0.6)]
      (combat/attempt-attack [0 0] [0 1])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))))

  (it "survivor has reduced hits after combat"
    (reset! atoms/game-map (build-test-map ["Dd"]))
    (set-test-unit atoms/game-map "D" :hits 3)
    (set-test-unit atoms/game-map "d" :hits 3)
    ;; Rolls: 0.4 (D hits d:2), 0.6 (d hits D:2), 0.4 (D hits d:1), 0.4 (D hits d:0)
    (let [rolls (atom [0.4 0.6 0.4 0.4])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (combat/attempt-attack [0 0] [0 1])
        (let [survivor (:contents (get-in @atoms/game-map [0 1]))]
          (should= :destroyer (:type survivor))
          (should= :player (:owner survivor))
          (should= 2 (:hits survivor))))))

  (it "displays combat log when attacker wins"
    (reset! atoms/game-map (build-test-map ["Da"]))
    (set-test-unit atoms/game-map "D" :hits 3)
    (set-test-unit atoms/game-map "a" :hits 1)
    (reset! atoms/line2-message "")
    (with-redefs [rand (constantly 0.4)]
      (combat/attempt-attack [0 0] [0 1])
      (should= "a-1. Army destroyed." @atoms/line2-message)))

  (it "displays combat log when attacker loses"
    (reset! atoms/game-map (build-test-map ["Ad"]))
    (set-test-unit atoms/game-map "A" :hits 1)
    (set-test-unit atoms/game-map "d" :hits 3)
    (reset! atoms/line2-message "")
    (with-redefs [rand (constantly 0.6)]
      (combat/attempt-attack [0 0] [0 1])
      (should= "A-1. Army destroyed." @atoms/line2-message)))

  (it "displays combat log with multiple exchanges"
    (reset! atoms/game-map (build-test-map ["Dd"]))
    (set-test-unit atoms/game-map "D" :hits 3)
    (set-test-unit atoms/game-map "d" :hits 3)
    (reset! atoms/line2-message "")
    ;; Rolls: 0.4 (D hits d:2), 0.6 (d hits D:2), 0.4 (D hits d:1), 0.4 (D hits d:0)
    (let [rolls (atom [0.4 0.6 0.4 0.4])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (combat/attempt-attack [0 0] [0 1])
        (should= "d-1,D-1,d-1,d-1. Destroyer destroyed." @atoms/line2-message))))

  (it "displays combat log for submarine vs carrier"
    (reset! atoms/game-map (build-test-map ["Sc"]))
    (set-test-unit atoms/game-map "S" :hits 2)
    (set-test-unit atoms/game-map "c" :hits 8)
    (reset! atoms/line2-message "")
    ;; Rolls: 0.6 (c hits S:1), 0.6 (c hits S:0)
    (let [rolls (atom [0.6 0.6])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (combat/attempt-attack [0 0] [0 1])
        (should= "S-1,S-1. Submarine destroyed." @atoms/line2-message))))

  (it "displays combat log for submarine defeating carrier"
    (reset! atoms/game-map (build-test-map ["Sc"]))
    (set-test-unit atoms/game-map "S" :hits 2)
    (set-test-unit atoms/game-map "c" :hits 8)
    (reset! atoms/line2-message "")
    ;; Rolls: 0.4 (S hits c:5), 0.6 (c hits S:1), 0.4 (S hits c:2), 0.4 (S hits c:0)
    (let [rolls (atom [0.4 0.6 0.4 0.4])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (combat/attempt-attack [0 0] [0 1])
        (should= "c-3,S-1,c-3,c-3. Carrier destroyed." @atoms/line2-message))))

  (it "releases beach reservation when computer transport is destroyed"
    (reset! atoms/game-map (build-test-map ["Dt"]))
    (set-test-unit atoms/game-map "D" :hits 3)
    (set-test-unit atoms/game-map "t" :hits 1 :transport-id 42)
    ;; Set up beach reservation for this transport
    (reset! atoms/reserved-beaches {[5 5] 42 [6 6] 99})
    (with-redefs [rand (constantly 0.4)]
      (combat/attempt-attack [0 0] [0 1])
      ;; Transport's beach reservation should be removed
      (should-be-nil (get @atoms/reserved-beaches [5 5]))
      ;; Other reservation should remain
      (should= 99 (get @atoms/reserved-beaches [6 6]))))

  (it "does not release beach reservation when player transport is destroyed"
    (reset! atoms/game-map (build-test-map ["dT"]))
    (set-test-unit atoms/game-map "d" :hits 3)
    (set-test-unit atoms/game-map "T" :hits 1 :transport-id 42)
    ;; Set up beach reservation (shouldn't be released for player transport)
    (reset! atoms/reserved-beaches {[5 5] 42})
    (with-redefs [rand (constantly 0.4)]
      (combat/attempt-attack [0 0] [0 1])
      ;; Reservation should remain since it was a player transport
      (should= 42 (get @atoms/reserved-beaches [5 5])))))
