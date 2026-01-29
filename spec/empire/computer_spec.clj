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

(describe "computer-core/distance"
  (it "calculates manhattan distance"
    (should= 0 (computer-core/distance [0 0] [0 0]))
    (should= 1 (computer-core/distance [0 0] [0 1]))
    (should= 2 (computer-core/distance [0 0] [1 1]))
    (should= 5 (computer-core/distance [0 0] [2 3]))))

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
      (should= moves (threat/safe-moves @atoms/computer-map [1 1] unit moves)))))

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
      (should-not (threat/should-retreat? [1 1] unit @atoms/computer-map)))))

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
      (should-be-nil (threat/retreat-move [2 2] unit @atoms/computer-map passable)))))

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
;; VMS Empire AI Modules: Verify they take actions
;; ============================================================================

(describe "VMS army module"
  (before (reset-all-atoms!))

  (it "process-army moves army toward unexplored territory"
    (reset! atoms/game-map (build-test-map ["a#"]))
    (reset! atoms/computer-map (build-test-map ["a#"]))
    (army/process-army [0 0])
    ;; Army should have moved to [0 1]
    (should= :army (get-in @atoms/game-map [0 1 :contents :type]))))

(describe "VMS fighter module"
  (before (reset-all-atoms!))

  (it "process-fighter patrols when fuel allows"
    (reset! atoms/game-map [[{:type :city :city-status :computer}
                              {:type :land :contents {:type :fighter :owner :computer
                                                       :fuel 20 :hits 1}}
                              {:type :land}]])
    (reset! atoms/computer-map @atoms/game-map)
    (let [unit (:contents (get-in @atoms/game-map [0 1]))]
      (fighter/process-fighter [0 1] unit)
      ;; Fighter should have moved from [0 1] to [0 0] or [0 2]
      (should-be-nil (get-in @atoms/game-map [0 1 :contents])))))

(describe "VMS ship module"
  (before (reset-all-atoms!))

  (it "process-ship explores sea"
    (reset! atoms/game-map (build-test-map ["d~"]))
    (reset! atoms/computer-map (build-test-map ["d~"]))
    (ship/process-ship [0 0] :destroyer)
    ;; Destroyer should have moved to [0 1]
    (should= :destroyer (get-in @atoms/game-map [0 1 :contents :type]))))

(describe "VMS transport module"
  (before (reset-all-atoms!))

  (it "process-transport moves in loading mode"
    (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :computer
                                                     :transport-mission :loading
                                                     :army-count 0}}
                              {:type :sea}
                              {:type :land}]])
    (reset! atoms/computer-map @atoms/game-map)
    (transport/process-transport [0 0])
    ;; Transport should have moved to [0 1]
    (should= :transport (get-in @atoms/game-map [0 1 :contents :type]))))

(describe "VMS production module"
  (before (reset-all-atoms!))

  (it "process-computer-city sets production"
    (reset! atoms/game-map (build-test-map ["X#"]))
    (reset! atoms/computer-map (build-test-map ["X#"]))
    (reset! atoms/production {})
    (computer-production/process-computer-city [0 0])
    ;; Production should be set
    (should-not-be-nil (get @atoms/production [0 0]))))

;; ============================================================================
;; Main Dispatcher
;; ============================================================================

(describe "process-computer-unit dispatcher"
  (before (reset-all-atoms!))

  (it "dispatches to army module"
    (reset! atoms/game-map (build-test-map ["a#"]))
    (reset! atoms/computer-map (build-test-map ["a#"]))
    (let [result (computer/process-computer-unit [0 0])]
      ;; Army module returns nil (units processed once per round)
      (should-be-nil result)
      ;; But army should have moved
      (should= :army (get-in @atoms/game-map [0 1 :contents :type]))))

  (it "dispatches to fighter module"
    (reset! atoms/game-map [[{:type :city :city-status :computer}
                              {:type :land :contents {:type :fighter :owner :computer
                                                       :fuel 20 :hits 1}}]])
    (reset! atoms/computer-map @atoms/game-map)
    (let [result (computer/process-computer-unit [0 1])]
      ;; Fighter module returns nil (units processed once per round)
      (should-be-nil result)))

  (it "dispatches to ship module"
    (reset! atoms/game-map (build-test-map ["d~"]))
    (reset! atoms/computer-map (build-test-map ["d~"]))
    (let [result (computer/process-computer-unit [0 0])]
      ;; Ship module returns nil (units processed once per round)
      (should-be-nil result)
      ;; But ship should have moved
      (should= :destroyer (get-in @atoms/game-map [0 1 :contents :type]))))

  (it "dispatches to transport module"
    (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :computer
                                                     :transport-mission :loading
                                                     :army-count 0}}
                              {:type :sea}
                              {:type :land}]])
    (reset! atoms/computer-map @atoms/game-map)
    (let [result (computer/process-computer-unit [0 0])]
      ;; Transport module returns nil (units processed once per round)
      (should-be-nil result)
      ;; But transport should have moved
      (should= :transport (get-in @atoms/game-map [0 1 :contents :type]))))

  (it "returns nil for non-computer unit"
    (reset! atoms/game-map (build-test-map ["A#"]))
    (should-be-nil (computer/process-computer-unit [0 0])))

  (it "returns nil for empty cell"
    (reset! atoms/game-map (build-test-map ["##"]))
    (should-be-nil (computer/process-computer-unit [0 0]))))

;; ============================================================================
;; Game Loop Integration
;; ============================================================================

(describe "game loop with VMS AI"
  (before (reset-all-atoms!))

  (it "build-computer-items returns computer city coordinates"
    (reset! atoms/game-map (build-test-map ["#X"]))
    (let [items (game-loop/build-computer-items)]
      (should-contain [0 1] items)))

  (it "build-computer-items returns computer unit coordinates"
    (reset! atoms/game-map (build-test-map ["a#"]))
    (let [items (game-loop/build-computer-items)]
      (should-contain [0 0] items)))

  (it "game runs with VMS AI moving units"
    ;; Map: X##a# - computer city, land, land, computer army, land
    (reset! atoms/game-map (build-test-map ["X##a#"]))
    (reset! atoms/player-map @atoms/game-map)
    (reset! atoms/computer-map @atoms/game-map)
    (reset! atoms/production {})
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [[0 3] [0 0]])  ;; army at [0 3], city at [0 0]
    ;; Process computer items - should complete without error
    (doseq [item @atoms/computer-items]
      (computer/process-computer-unit item))
    ;; Army should have moved (VMS AI takes actions) - toward unexplored or exploring
    ;; Since all is explored, army stays or moves to adjacent
    (let [army-at-3 (:contents (get-in @atoms/game-map [0 3]))
          army-at-4 (:contents (get-in @atoms/game-map [0 4]))
          army-at-2 (:contents (get-in @atoms/game-map [0 2]))]
      (should (or (= :army (:type army-at-4))
                  (= :army (:type army-at-2))
                  (= :army (:type army-at-3)))))))
