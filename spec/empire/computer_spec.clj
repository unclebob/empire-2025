(ns empire.computer-spec
  "Tests for computer AI modules - post CommandingGeneral refactor.
   Decision logic has been gutted; these tests cover preserved utilities."
  (:require [speclj.core :refer :all]
            [empire.game-loop :as game-loop]
            [empire.computer :as computer]
            [empire.computer.army :as army]
            [empire.computer.core :as computer-core]
            [empire.computer.fighter :as fighter]
            [empire.computer.production :as computer-production]
            [empire.computer.ship :as ship]
            [empire.computer.threat :as threat]
            [empire.computer.transport :as transport]
            [empire.ui.coordinates :as coords]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

;; ============================================================================
;; Preserved Utilities: computer/core.cljc
;; ============================================================================

(describe "computer-core/get-neighbors"
  (before (reset-all-atoms!))

  (it "returns neighbors for center position"
    (reset! atoms/game-map (build-test-map ["###"
                                             "###"
                                             "###"]))
    (let [neighbors (computer-core/get-neighbors [1 1])]
      (should= 8 (count neighbors))))

  (it "returns fewer neighbors for corner position"
    (reset! atoms/game-map (build-test-map ["###"
                                             "###"
                                             "###"]))
    (let [neighbors (computer-core/get-neighbors [0 0])]
      (should= 3 (count neighbors)))))

(describe "coords/manhattan-distance (consolidated from computer-core)"
  (it "calculates manhattan distance"
    (should= 0 (coords/manhattan-distance [0 0] [0 0]))
    (should= 1 (coords/manhattan-distance [0 0] [0 1]))
    (should= 2 (coords/manhattan-distance [0 0] [1 1]))
    (should= 5 (coords/manhattan-distance [0 0] [2 3]))))

(describe "computer-core/attackable-target?"
  (it "returns true for player unit"
    (should (computer-core/attackable-target? {:contents {:owner :player}})))

  (it "returns true for free city"
    (should (computer-core/attackable-target? {:type :city :city-status :free})))

  (it "returns true for player city"
    (should (computer-core/attackable-target? {:type :city :city-status :player})))

  (it "returns false for computer city"
    (should-not (computer-core/attackable-target? {:type :city :city-status :computer})))

  (it "returns false for empty cell"
    (should-not (computer-core/attackable-target? {:type :land}))))

(describe "computer-core/find-visible-cities"
  (before (reset-all-atoms!))

  (it "finds cities matching status predicate"
    (reset! atoms/computer-map (build-test-map ["X+O"]))
    (should= [[0 0]] (computer-core/find-visible-cities #{:computer}))
    (should= [[0 1]] (computer-core/find-visible-cities #{:free}))
    (should= [[0 2]] (computer-core/find-visible-cities #{:player}))))

(describe "computer-core/move-toward"
  (it "returns neighbor closest to target"
    (let [passable [[0 1] [1 0] [1 1]]]
      (should= [0 1] (computer-core/move-toward [0 0] [0 5] passable))))

  (it "returns nil for empty passable list"
    (should-be-nil (computer-core/move-toward [0 0] [5 5] []))))

(describe "computer-core/adjacent-to-computer-unexplored?"
  (before (reset-all-atoms!))

  (it "returns true when adjacent to nil cell"
    (reset! atoms/computer-map [[{:type :land} nil]
                                 [{:type :land} {:type :land}]])
    (should (computer-core/adjacent-to-computer-unexplored? [0 0])))

  (it "returns false when all neighbors explored"
    (reset! atoms/computer-map [[{:type :land} {:type :land}]
                                 [{:type :land} {:type :land}]])
    (should-not (computer-core/adjacent-to-computer-unexplored? [0 0]))))

(describe "computer-core/move-unit-to"
  (before (reset-all-atoms!))

  (it "moves unit from one position to another"
    (reset! atoms/game-map (build-test-map ["a#"]))
    (computer-core/move-unit-to [0 0] [0 1])
    (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
    (should= :army (:type (:contents (get-in @atoms/game-map [0 1]))))))

(describe "computer-core/find-visible-player-units"
  (before (reset-all-atoms!))

  (it "finds player units on computer-map"
    (reset! atoms/computer-map (build-test-map ["aA#"]))
    (should= [[0 1]] (computer-core/find-visible-player-units))))

(describe "computer-core/board-transport"
  (before (reset-all-atoms!))

  (it "loads army onto adjacent transport"
    (reset! atoms/game-map (build-test-map ["at"]))
    (computer-core/board-transport [0 0] [0 1])
    (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
    (should= 1 (:army-count (:contents (get-in @atoms/game-map [0 1])))))

  (it "throws when positions are not adjacent"
    (reset! atoms/game-map (build-test-map ["a#t"]))
    (should-throw (computer-core/board-transport [0 0] [0 2]))))

(describe "computer-core/attempt-conquest-computer"
  (before (reset-all-atoms!))

  (it "removes army from original position on conquest attempt"
    (reset! atoms/game-map (build-test-map ["a+"]))
    (reset! atoms/computer-map @atoms/game-map)
    (with-redefs [rand (constantly 0.3)]  ;; Success case
      (computer-core/attempt-conquest-computer [0 0] [0 1]))
    (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))

  (it "conquers city on success (rand < 0.5)"
    (reset! atoms/game-map (build-test-map ["a+"]))
    (reset! atoms/computer-map @atoms/game-map)
    (with-redefs [rand (constantly 0.3)]
      (computer-core/attempt-conquest-computer [0 0] [0 1]))
    (should= :computer (:city-status (get-in @atoms/game-map [0 1]))))

  (it "army dies on failure (rand >= 0.5)"
    (reset! atoms/game-map (build-test-map ["a+"]))
    (reset! atoms/computer-map @atoms/game-map)
    (with-redefs [rand (constantly 0.7)]
      (computer-core/attempt-conquest-computer [0 0] [0 1]))
    (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
    (should= :free (:city-status (get-in @atoms/game-map [0 1]))))

  (it "returns nil in both success and failure cases"
    (reset! atoms/game-map (build-test-map ["a+"]))
    (reset! atoms/computer-map @atoms/game-map)
    (with-redefs [rand (constantly 0.3)]
      (should-be-nil (computer-core/attempt-conquest-computer [0 0] [0 1])))
    (reset! atoms/game-map (build-test-map ["a+"]))
    (with-redefs [rand (constantly 0.7)]
      (should-be-nil (computer-core/attempt-conquest-computer [0 0] [0 1])))))

(describe "computer-core/find-loading-transport"
  (before (reset-all-atoms!))

  (it "finds transport with loading mission"
    (reset! atoms/game-map (build-test-map ["~t~"]))
    (swap! atoms/game-map assoc-in [0 1 :contents :transport-mission] :loading)
    (should= [0 1] (computer-core/find-loading-transport)))

  (it "returns nil when no loading transport exists"
    (reset! atoms/game-map (build-test-map ["~t~"]))
    (should-be-nil (computer-core/find-loading-transport)))

  (it "ignores player transports"
    (reset! atoms/game-map (build-test-map ["~T~"]))
    (swap! atoms/game-map assoc-in [0 1 :contents :transport-mission] :loading)
    (should-be-nil (computer-core/find-loading-transport)))

  (it "ignores full transports (army-count >= 6)"
    (reset! atoms/game-map (build-test-map ["~t~"]))
    (swap! atoms/game-map assoc-in [0 1 :contents :transport-mission] :loading)
    (swap! atoms/game-map assoc-in [0 1 :contents :army-count] 6)
    (should-be-nil (computer-core/find-loading-transport))))

(describe "computer-core/find-adjacent-loading-transport"
  (before (reset-all-atoms!))

  (it "finds adjacent transport with loading mission"
    (reset! atoms/game-map (build-test-map ["at~"]))
    (swap! atoms/game-map assoc-in [0 1 :contents :transport-mission] :loading)
    (should= [0 1] (computer-core/find-adjacent-loading-transport [0 0])))

  (it "returns nil when no adjacent loading transport"
    (reset! atoms/game-map (build-test-map ["a~t"]))
    (swap! atoms/game-map assoc-in [0 2 :contents :transport-mission] :loading)
    (should-be-nil (computer-core/find-adjacent-loading-transport [0 0])))

  (it "ignores adjacent transport without loading mission"
    (reset! atoms/game-map (build-test-map ["at~"]))
    (should-be-nil (computer-core/find-adjacent-loading-transport [0 0]))))

(describe "computer-core/army-should-board-transport?"
  (before (reset-all-atoms!))

  (it "returns nil when no loading transport exists"
    (reset! atoms/game-map (build-test-map ["a~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (should-be-nil (computer-core/army-should-board-transport? [0 0] (constantly nil))))

  (it "returns nil when no target cities exist"
    (reset! atoms/game-map (build-test-map ["a~t"]))
    (swap! atoms/game-map assoc-in [0 2 :contents :transport-mission] :loading)
    (reset! atoms/computer-map @atoms/game-map)
    (should-be-nil (computer-core/army-should-board-transport? [0 0] (constantly nil))))

  (it "returns true when transport exists and no land route to cities"
    (reset! atoms/game-map (build-test-map ["a~t+"]))
    (swap! atoms/game-map assoc-in [0 2 :contents :transport-mission] :loading)
    (reset! atoms/computer-map @atoms/game-map)
    (should (computer-core/army-should-board-transport? [0 0] (constantly nil))))

  (it "returns falsy when land route to cities exists"
    (reset! atoms/game-map (build-test-map ["a#t+"]))
    (swap! atoms/game-map assoc-in [0 2 :contents :transport-mission] :loading)
    (reset! atoms/computer-map @atoms/game-map)
    ;; Pathfinding returns a step, meaning land route exists
    (should-not (computer-core/army-should-board-transport? [0 0] (constantly [0 1])))))

;; ============================================================================
;; Preserved Utilities: computer/threat.cljc
;; ============================================================================

(describe "threat/unit-threat"
  (it "returns correct threat values for unit types"
    (should= 10 (threat/unit-threat :battleship))
    (should= 8 (threat/unit-threat :carrier))
    (should= 6 (threat/unit-threat :destroyer))
    (should= 5 (threat/unit-threat :submarine))
    (should= 4 (threat/unit-threat :fighter))
    (should= 3 (threat/unit-threat :patrol-boat))
    (should= 2 (threat/unit-threat :army))
    (should= 1 (threat/unit-threat :transport))
    (should= 0 (threat/unit-threat :satellite))))

(describe "threat/threat-level"
  (before (reset-all-atoms!))

  (it "returns 0 with no enemies nearby"
    (reset! atoms/computer-map (build-test-map ["~~~"
                                                 "~d~"
                                                 "~~~"]))
    (should= 0 (threat/threat-level @atoms/computer-map [1 1])))

  (it "sums threat of adjacent enemies"
    (reset! atoms/computer-map (build-test-map ["~B~"
                                                 "~d~"
                                                 "~D~"]))
    ;; Battleship = 10, Destroyer = 6
    (should= 16 (threat/threat-level @atoms/computer-map [1 1])))

  (it "ignores friendly units"
    (reset! atoms/computer-map (build-test-map ["~b~"
                                                 "~d~"
                                                 "~b~"]))
    (should= 0 (threat/threat-level @atoms/computer-map [1 1]))))

(describe "threat/safe-moves"
  (before (reset-all-atoms!))

  (it "returns all moves unchanged when unit at full health"
    (reset! atoms/computer-map (build-test-map ["~B~"
                                                 "~d~"
                                                 "~~~"]))
    (let [unit {:type :destroyer :hits 3}
          moves [[0 1] [1 0] [1 2] [2 1]]]
      (should= moves (threat/safe-moves @atoms/computer-map [1 1] unit moves))))

  (it "sorts moves by threat level when unit is damaged"
    (reset! atoms/computer-map (build-test-map ["B~~~~"
                                                 "~~~~~"
                                                 "~~d~~"
                                                 "~~~~~"
                                                 "~~~~~"]))
    (let [unit {:type :destroyer :hits 2}  ;; damaged (max is 3)
          moves [[2 1] [2 3]]]  ;; [2 1] is closer to Battleship, [2 3] is safer
      ;; Should return safest moves first (lowest threat)
      (let [result (threat/safe-moves @atoms/computer-map [2 2] unit moves)]
        (should= [2 3] (first result))))))

(describe "threat/should-retreat?"
  (before (reset-all-atoms!))

  (it "returns true when damaged and under threat"
    (reset! atoms/computer-map (build-test-map ["~B~"
                                                 "~d~"
                                                 "~~~"]))
    (let [unit {:type :destroyer :hits 2}]
      (should (threat/should-retreat? [1 1] unit @atoms/computer-map))))

  (it "returns false for healthy unit under threat"
    (reset! atoms/computer-map (build-test-map ["~B~"
                                                 "~d~"
                                                 "~~~"]))
    (let [unit {:type :destroyer :hits 3}]
      (should-not (threat/should-retreat? [1 1] unit @atoms/computer-map))))

  (it "returns true for transport with armies under high threat"
    (reset! atoms/computer-map (build-test-map ["~BB"
                                                 "~t~"
                                                 "~~~"]))
    ;; Two battleships = threat of 20, which is > 5
    (let [unit {:type :transport :hits 3 :army-count 2}]
      (should (threat/should-retreat? [1 1] unit @atoms/computer-map))))

  (it "returns false for transport with armies under low threat"
    (reset! atoms/computer-map (build-test-map ["~P~"
                                                 "~t~"
                                                 "~~~"]))
    ;; Patrol boat = threat of 3, which is not > 5
    (let [unit {:type :transport :hits 3 :army-count 2}]
      (should-not (threat/should-retreat? [1 1] unit @atoms/computer-map))))

  (it "returns true when severely damaged (< 50% health)"
    (reset! atoms/computer-map (build-test-map ["~~~"
                                                 "~b~"
                                                 "~~~"]))
    ;; Battleship has 10 hits max, 4 < 5 (50%)
    (let [unit {:type :battleship :hits 4}]
      (should (threat/should-retreat? [1 1] unit @atoms/computer-map))))

  (it "returns false when at exactly 50% health"
    (reset! atoms/computer-map (build-test-map ["~~~"
                                                 "~b~"
                                                 "~~~"]))
    ;; Battleship has 10 hits max, 5 = 50%
    (let [unit {:type :battleship :hits 5}]
      (should-not (threat/should-retreat? [1 1] unit @atoms/computer-map)))))

(describe "threat/find-nearest-friendly-base"
  (before (reset-all-atoms!))

  (it "returns nearest computer city"
    (reset! atoms/computer-map (build-test-map ["X~~X~"
                                                 "~~~~~"
                                                 "~~d~~"]))
    ;; [0 0] is distance 4, [0 3] is distance 3
    (should= [0 3] (threat/find-nearest-friendly-base [2 2] :destroyer)))

  (it "returns nil when no computer cities exist"
    (reset! atoms/computer-map (build-test-map ["O~~+~"
                                                 "~~~~~"
                                                 "~~d~~"]))
    (should-be-nil (threat/find-nearest-friendly-base [2 2] :destroyer))))

(describe "threat/retreat-move"
  (before (reset-all-atoms!))

  (it "moves toward nearest friendly city"
    (reset! atoms/game-map (build-test-map ["X~~~B"
                                             "~~~~~"
                                             "~~d~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit {:type :destroyer :hits 1}
          passable [[1 2] [1 3] [2 1] [2 3]]]
      (let [retreat (threat/retreat-move [2 2] unit @atoms/computer-map passable)]
        (should-not-be-nil retreat)
        (should (#{[1 2] [2 1] [1 3]} retreat)))))

  (it "returns nil when no friendly city exists"
    (reset! atoms/game-map (build-test-map ["~~~~B"
                                             "~~~~~"
                                             "~~d~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit {:type :destroyer :hits 1}
          passable [[1 2] [1 3] [2 1] [2 3]]]
      (should-be-nil (threat/retreat-move [2 2] unit @atoms/computer-map passable))))

  (it "returns nil when passable-moves is empty"
    (reset! atoms/game-map (build-test-map ["X~~~~"
                                             "~~~~~"
                                             "~~d~~"]))
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit {:type :destroyer :hits 1}]
      (should-be-nil (threat/retreat-move [2 2] unit @atoms/computer-map [])))))

;; ============================================================================
;; Preserved Utilities: computer/production.cljc
;; ============================================================================

(describe "computer-production/city-is-coastal?"
  (before (reset-all-atoms!))

  (it "returns true when city has adjacent sea"
    (reset! atoms/game-map (build-test-map ["~X#"]))
    (should (computer-production/city-is-coastal? [0 1])))

  (it "returns false when city has no adjacent sea"
    (reset! atoms/game-map (build-test-map ["#X#"]))
    (should-not (computer-production/city-is-coastal? [0 1]))))

(describe "computer-production/count-computer-units"
  (before (reset-all-atoms!))

  (it "counts computer units by type"
    (reset! atoms/game-map (build-test-map ["aad"]))
    (let [counts (computer-production/count-computer-units)]
      (should= 2 (get counts :army))
      (should= 1 (get counts :destroyer))))

  (it "ignores player units"
    (reset! atoms/game-map (build-test-map ["aAD"]))
    (let [counts (computer-production/count-computer-units)]
      (should= 1 (get counts :army))
      (should-be-nil (get counts :destroyer)))))

;; ============================================================================
;; Gutted Modules: Verify they do nothing
;; ============================================================================

(describe "gutted army module"
  (before (reset-all-atoms!))

  (it "process-army returns nil and does nothing"
    (reset! atoms/game-map (build-test-map ["a#"]))
    (let [result (army/process-army [0 0])]
      (should-be-nil result)
      ;; Army should still be at original position
      (should= :army (:type (:contents (get-in @atoms/game-map [0 0])))))))

(describe "gutted fighter module"
  (before (reset-all-atoms!))

  (it "process-fighter returns nil and does nothing"
    (reset! atoms/game-map (build-test-map ["f#"]))
    (let [unit (:contents (get-in @atoms/game-map [0 0]))
          result (fighter/process-fighter [0 0] unit)]
      (should-be-nil result)
      (should= :fighter (:type (:contents (get-in @atoms/game-map [0 0])))))))

(describe "gutted ship module"
  (before (reset-all-atoms!))

  (it "process-ship returns nil and does nothing"
    (reset! atoms/game-map (build-test-map ["d~"]))
    (let [result (ship/process-ship [0 0] :destroyer)]
      (should-be-nil result)
      (should= :destroyer (:type (:contents (get-in @atoms/game-map [0 0])))))))

(describe "gutted transport module"
  (before (reset-all-atoms!))

  (it "process-transport returns nil and does nothing"
    (reset! atoms/game-map (build-test-map ["t~"]))
    (let [result (transport/process-transport [0 0])]
      (should-be-nil result)
      (should= :transport (:type (:contents (get-in @atoms/game-map [0 0])))))))

(describe "gutted production module"
  (before (reset-all-atoms!))

  (it "process-computer-city returns nil and does nothing"
    (reset! atoms/game-map (build-test-map ["X#"]))
    (reset! atoms/production {})
    (let [result (computer-production/process-computer-city [0 0])]
      (should-be-nil result)
      ;; Production should NOT be set (function does nothing)
      (should-be-nil (@atoms/production [0 0])))))

;; ============================================================================
;; Main Dispatcher
;; ============================================================================

(describe "process-computer-unit dispatcher"
  (before (reset-all-atoms!))

  (it "dispatches to army module (which does nothing)"
    (reset! atoms/game-map (build-test-map ["a#"]))
    (let [result (computer/process-computer-unit [0 0])]
      (should-be-nil result)
      (should= :army (:type (:contents (get-in @atoms/game-map [0 0]))))))

  (it "dispatches to fighter module (which does nothing)"
    (reset! atoms/game-map (build-test-map ["f#"]))
    (let [result (computer/process-computer-unit [0 0])]
      (should-be-nil result)
      (should= :fighter (:type (:contents (get-in @atoms/game-map [0 0]))))))

  (it "dispatches to ship module (which does nothing)"
    (reset! atoms/game-map (build-test-map ["d~"]))
    (let [result (computer/process-computer-unit [0 0])]
      (should-be-nil result)
      (should= :destroyer (:type (:contents (get-in @atoms/game-map [0 0]))))))

  (it "dispatches to transport module (which does nothing)"
    (reset! atoms/game-map (build-test-map ["t~"]))
    (let [result (computer/process-computer-unit [0 0])]
      (should-be-nil result)
      (should= :transport (:type (:contents (get-in @atoms/game-map [0 0]))))))

  (it "returns nil for non-computer unit"
    (reset! atoms/game-map (build-test-map ["A#"]))
    (should-be-nil (computer/process-computer-unit [0 0])))

  (it "returns nil for empty cell"
    (reset! atoms/game-map (build-test-map ["##"]))
    (should-be-nil (computer/process-computer-unit [0 0]))))

;; ============================================================================
;; Game Loop Integration
;; ============================================================================

(describe "game loop with gutted AI"
  (before (reset-all-atoms!))

  (it "build-computer-items returns computer city coordinates"
    (reset! atoms/game-map (build-test-map ["#X"]))
    (let [items (game-loop/build-computer-items)]
      (should-contain [0 1] items)))

  (it "build-computer-items returns computer unit coordinates"
    (reset! atoms/game-map (build-test-map ["a#"]))
    (let [items (game-loop/build-computer-items)]
      (should-contain [0 0] items)))

  (it "game runs without errors (computer just doesn't move)"
    (reset! atoms/game-map (build-test-map ["Oa#X"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [[0 1] [0 3]])  ;; army at [0 1], city at [0 3]
    ;; Process computer items - should complete without error
    (doseq [item @atoms/computer-items]
      (computer/process-computer-unit item))
    ;; Army should still be where it was (no movement)
    (should= :army (:type (:contents (get-in @atoms/game-map [0 1]))))))
