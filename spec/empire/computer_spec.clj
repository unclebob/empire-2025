(ns empire.computer-spec
  "Tests for computer AI modules - post CommandingGeneral refactor.
   Decision logic has been gutted; these tests cover preserved utilities."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game.loop.core :as game-loop]
            [empire.computer.coordinator :as computer]
            [empire.computer.army :as army]
            [empire.computer.core :as computer-core]
            [empire.computer.fighter :as fighter]
            [empire.computer.production :as computer-production]
            [empire.computer.ship :as ship]
            [empire.computer.threat :as threat]
            [empire.computer.transport :as transport]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world! set-test-player-map! set-test-computer-map!]]))

;; ============================================================================
;; Preserved Utilities: computer/core.cljc
;; ============================================================================

(describe "Computer Core Utilities"
  (before (reset-all-atoms!))

  (context "computer-core/get-neighbors"
    (it "returns neighbors for center position"
      (set-test-world! (build-test-map ["###"
                                               "###"
                                               "###"]))
      (let [neighbors (computer-core/get-neighbors [1 1])]
        (should= 8 (count neighbors))))

    (it "returns fewer neighbors for corner position"
      (set-test-world! (build-test-map ["###"
                                               "###"
                                               "###"]))
      (let [neighbors (computer-core/get-neighbors [0 0])]
        (should= 3 (count neighbors)))))

  (context "computer-core/distance"
    (it "calculates manhattan distance"
      (should= 0 (computer-core/distance [0 0] [0 0]))
      (should= 1 (computer-core/distance [0 0] [0 1]))
      (should= 2 (computer-core/distance [0 0] [1 1]))
      (should= 5 (computer-core/distance [0 0] [2 3]))))

  (context "computer-core/attackable-target?"
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

  (context "computer-core/find-visible-cities"
    (it "finds cities matching status predicate"
      (set-test-computer-map! (build-test-map ["X+O"]))
      (should= [[0 0]] (computer-core/find-visible-cities #{:computer}))
      (should= [[1 0]] (computer-core/find-visible-cities #{:free}))
      (should= [[2 0]] (computer-core/find-visible-cities #{:player}))))

  (context "computer-core/move-toward"
    (it "returns neighbor closest to target"
      (let [passable [[0 1] [1 0] [1 1]]]
        (should= [0 1] (computer-core/move-toward [0 0] [0 5] passable))))

    (it "returns nil for empty passable list"
      (should-be-nil (computer-core/move-toward [0 0] [5 5] []))))

  (context "computer-core/adjacent-to-computer-unexplored?"
    (it "returns true when adjacent to nil cell"
      (set-test-computer-map! [[{:type :land} nil]
                                   [{:type :land} {:type :land}]])
      (should (computer-core/adjacent-to-computer-unexplored? [0 0])))

    (it "returns false when all neighbors explored"
      (set-test-computer-map! [[{:type :land} {:type :land}]
                                   [{:type :land} {:type :land}]])
      (should-not (computer-core/adjacent-to-computer-unexplored? [0 0]))))

  (context "computer-core/move-unit-to"
    (it "moves unit from one position to another"
      (set-test-world! (build-test-map ["a#"]))
      (computer-core/move-unit-to [0 0] [1 0])
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= :army (:type (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))))

  (context "computer-core/find-visible-player-units"
    (it "finds player units on computer-map"
      (set-test-computer-map! (build-test-map ["aA#"]))
      (should= [[1 0]] (computer-core/find-visible-player-units))))

  (context "computer-core/board-transport"
    (it "loads army onto adjacent transport"
      (set-test-world! (build-test-map ["at"]))
      (computer-core/board-transport [0 0] [1 0])
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= 1 (:army-count (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

    (it "throws when positions are not adjacent"
      (set-test-world! (build-test-map ["a#t"]))
      (should-throw (computer-core/board-transport [0 0] [2 0])))))

;; ============================================================================
;; Preserved Utilities: computer/threat.cljc
;; ============================================================================

(describe "Threat Assessment"
  (before (reset-all-atoms!))

  (context "threat/unit-threat"
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

  (context "threat/threat-level"
    (it "returns 0 with no enemies nearby"
      (set-test-computer-map! (build-test-map ["~~~"
                                                   "~d~"
                                                   "~~~"]))
      (should= 0 (threat/threat-level (test-utils/read-test-state :computer-map) [1 1])))

    (it "sums threat of adjacent enemies"
      (set-test-computer-map! (build-test-map ["~B~"
                                                   "~d~"
                                                   "~D~"]))
      ;; Battleship = 10, Destroyer = 6
      (should= 16 (threat/threat-level (test-utils/read-test-state :computer-map) [1 1])))

    (it "ignores friendly units"
      (set-test-computer-map! (build-test-map ["~b~"
                                                   "~d~"
                                                   "~b~"]))
      (should= 0 (threat/threat-level (test-utils/read-test-state :computer-map) [1 1]))))

  (context "threat/safe-moves"
    (it "returns all moves unchanged when unit at full health"
      (set-test-computer-map! (build-test-map ["~B~"
                                                   "~d~"
                                                   "~~~"]))
      (let [unit {:type :destroyer :hits 3}
            moves [[1 0] [0 1] [2 1] [1 2]]]
        (should= moves (threat/safe-moves (test-utils/read-test-state :computer-map) [1 1] unit moves)))))

  (context "threat/should-retreat?"
    (it "returns true when damaged and under threat"
      (set-test-computer-map! (build-test-map ["~B~"
                                                   "~d~"
                                                   "~~~"]))
      (let [unit {:type :destroyer :hits 2}]
        (should (threat/should-retreat? [1 1] unit (test-utils/read-test-state :computer-map)))))

    (it "returns false for healthy unit under threat"
      (set-test-computer-map! (build-test-map ["~B~"
                                                   "~d~"
                                                   "~~~"]))
      (let [unit {:type :destroyer :hits 3}]
        (should-not (threat/should-retreat? [1 1] unit (test-utils/read-test-state :computer-map))))))

  (context "threat/retreat-move"
    (it "moves toward nearest friendly city"
      (set-test-world! (build-test-map ["X~~~B"
                                               "~~~~~"
                                               "~~d~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit {:type :destroyer :hits 1}
            passable [[2 1] [3 1] [1 2] [3 2]]]
        (let [retreat (threat/retreat-move [2 2] unit (test-utils/read-test-state :computer-map) passable)]
          (should-not-be-nil retreat)
          (should (#{[2 1] [1 2] [3 1]} retreat)))))

    (it "returns nil when no friendly city exists"
      (set-test-world! (build-test-map ["~~~~B"
                                               "~~~~~"
                                               "~~d~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [unit {:type :destroyer :hits 1}
            passable [[2 1] [3 1] [1 2] [3 2]]]
        (should-be-nil (threat/retreat-move [2 2] unit (test-utils/read-test-state :computer-map) passable))))))

;; ============================================================================
;; Preserved Utilities: computer/production.cljc
;; ============================================================================

(describe "Computer Production"
  (before (reset-all-atoms!))

  (context "computer-production/city-is-coastal?"
    (it "returns true when city has adjacent sea"
      (set-test-world! (build-test-map ["~X#"]))
      (should (computer-production/city-is-coastal? [1 0])))

    (it "returns false when city has no adjacent sea"
      (set-test-world! (build-test-map ["#X#"]))
      (should-not (computer-production/city-is-coastal? [1 0]))))

  (context "computer-production/count-computer-units"
    (it "counts computer units by type"
      (set-test-world! (build-test-map ["aad"]))
      (let [counts (computer-production/count-computer-units)]
        (should= 2 (get counts :army))
        (should= 1 (get counts :destroyer))))

    (it "ignores player units"
      (set-test-world! (build-test-map ["aAD"]))
      (let [counts (computer-production/count-computer-units)]
        (should= 1 (get counts :army))
        (should-be-nil (get counts :destroyer))))))

;; ============================================================================
;; VMS Empire AI Modules: Verify they take actions
;; ============================================================================

(describe "VMS AI Unit Modules"
  (before (reset-all-atoms!))

  (context "VMS army module"
    (it "process-army moves army in random-explore direction"
      (set-test-world! (build-test-map ["a#"]))
      (set-test-computer-map! (build-test-map ["a#"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :random-explore :random-explore-direction [1 0]})
      (army/process-army [0 0])
      ;; Army should have moved to [1 0]
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))))

  (context "VMS fighter module"
    (it "process-fighter patrols when fuel allows"
      (let [row (vec (concat [{:type :city :city-status :computer}
                               {:type :land :contents {:type :fighter :owner :computer
                                                        :fuel 20 :hits 1}}]
                              (repeat 10 {:type :land})))]
        (set-test-world! [row])
        ;; Computer map has unexplored cells to the right, giving patrol direction
        (set-test-computer-map! [(vec (concat [{:type :city :city-status :computer}
                                                   {:type :land :contents {:type :fighter :owner :computer
                                                                            :fuel 20 :hits 1}}]
                                                  (repeat 10 nil)))])
        (let [unit (get-in (test-utils/read-test-state :game-map) [0 1 :contents])]
          (fighter/process-fighter [0 1] unit)
          ;; Fighter should have moved from [0 1] toward unexplored territory
          (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 1 :contents]))))))

  (context "VMS ship module"
    (it "process-ship stays put when all sea explored"
      (set-test-world! (build-test-map ["d~"]))
      (set-test-computer-map! (build-test-map ["d~"]))
      (ship/process-ship [0 0] :destroyer)
      ;; Destroyer stays put - no unexplored territory
      (should= :destroyer (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))))

  (context "VMS transport module"
    (it "process-transport stays put when loading in open sea with no unexplored"
      (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                                       :transport-mission :loading
                                                       :army-count 0}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 0])
      ;; Transport stays put - open sea, no coastal targets, no unexplored territory
      (should= :transport (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))))

  (context "VMS production module"
    (it "process-computer-city sets production"
      (set-test-world! (build-test-map ["X+#"]))
      (set-test-computer-map! (build-test-map ["X+#"]))
      (update-test-world! assoc-in [0 0 :country-id] 1)
      (test-utils/set-test-state! :production {})
      (computer-production/process-computer-city [0 0])
      ;; Per-country production fires (0 armies < 10)
      (should-not-be-nil (get (test-utils/read-test-state :production) [0 0])))))

;; ============================================================================
;; Main Dispatcher and Game Loop Integration
;; ============================================================================

(describe "Dispatcher and Game Loop"
  (before (reset-all-atoms!))

  (context "process-computer-unit dispatcher"
    (it "dispatches to army module"
      (set-test-world! (build-test-map ["a#"]))
      (set-test-computer-map! (build-test-map ["a#"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :computer :hits 1
              :mode :random-explore :random-explore-direction [1 0]})
      (let [result (computer/process-computer-unit [0 0])]
        ;; Army module returns nil (units processed once per round)
        (should-be-nil result)
        ;; But army should have moved
        (should= :army (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))))

    (it "dispatches to fighter module"
      (set-test-world! [[{:type :city :city-status :computer}
                                {:type :land :contents {:type :fighter :owner :computer
                                                         :fuel 20 :hits 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [result (computer/process-computer-unit [0 1])]
        ;; Fighter module returns nil (units processed once per round)
        (should-be-nil result)))

    (it "dispatches to ship module - stays put when all sea explored"
      (set-test-world! (build-test-map ["d~"]))
      (set-test-computer-map! (build-test-map ["d~"]))
      (let [result (computer/process-computer-unit [0 0])]
        ;; Ship module returns nil (units processed once per round)
        (should-be-nil result)
        ;; No unexplored territory, no enemies - ship stays put
        (should= :destroyer (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))))

    (it "dispatches to transport module"
      (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                                       :transport-mission :loading
                                                       :army-count 0}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (let [result (computer/process-computer-unit [0 0])]
        ;; Transport module returns nil (units processed once per round)
        (should-be-nil result)
        ;; Transport stays put - open sea, no coastal targets, no unexplored territory
        (should= :transport (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))))

    (it "returns nil for non-computer unit"
      (set-test-world! (build-test-map ["A#"]))
      (should-be-nil (computer/process-computer-unit [0 0])))

    (it "returns nil for empty cell"
      (set-test-world! (build-test-map ["##"]))
      (should-be-nil (computer/process-computer-unit [0 0])))

    (it "returns nil for satellite (no processing needed)"
      (set-test-world! [[{:type :sea :contents {:type :satellite :owner :computer
                                                       :hits 1 :mode :awake
                                                       :direction [0 1]}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should-be-nil (computer/process-computer-unit [0 0])))

    (it "dispatches patrol-boat to ship module"
      (set-test-world! (build-test-map ["p~"]))
      (set-test-computer-map! (build-test-map ["p~"]))
      (should-be-nil (computer/process-computer-unit [0 0]))
      (should= :patrol-boat (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type])))

    (it "dispatches submarine to ship module"
      (set-test-world! (build-test-map ["s~"]))
      (set-test-computer-map! (build-test-map ["s~"]))
      (should-be-nil (computer/process-computer-unit [0 0]))
      (should= :submarine (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type])))

    (it "dispatches battleship to ship module"
      (set-test-world! (build-test-map ["b~"]))
      (set-test-computer-map! (build-test-map ["b~"]))
      (should-be-nil (computer/process-computer-unit [0 0]))
      (should= :battleship (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))))

  (context "game loop with VMS AI"
    (it "build-computer-items returns computer city coordinates"
      (set-test-world! (build-test-map ["#X"]))
      (let [items (game-loop/build-computer-items)]
        (should-contain [1 0] items)))

    (it "build-computer-items returns computer unit coordinates"
      (set-test-world! (build-test-map ["a#"]))
      (let [items (game-loop/build-computer-items)]
        (should-contain [0 0] items)))

    (it "game runs with VMS AI moving units"
      ;; Map: X##a# - computer city, land, land, computer army, land
      (set-test-world! (build-test-map ["X##a#"]))
      (set-test-player-map! (test-utils/read-test-state :game-map))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [])
      (test-utils/set-test-state! :computer-items [[3 0] [0 0]])  ;; army at [3 0], city at [0 0]
      ;; Process computer items - should complete without error
      (doseq [item (test-utils/read-test-state :computer-items)]
        (computer/process-computer-unit item))
      ;; Army should have moved (VMS AI takes actions) - toward unexplored or exploring
      ;; Since all is explored, army stays or moves to adjacent
      (let [army-at-3 (:contents (get-in (test-utils/read-test-state :game-map) [3 0]))
            army-at-4 (:contents (get-in (test-utils/read-test-state :game-map) [4 0]))
            army-at-2 (:contents (get-in (test-utils/read-test-state :game-map) [2 0]))]
        (should (or (= :army (:type army-at-4))
                    (= :army (:type army-at-2))
                    (= :army (:type army-at-3))))))))

(run-specs)
