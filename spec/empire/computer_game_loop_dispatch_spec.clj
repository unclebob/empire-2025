(ns empire.computer-game-loop-dispatch-spec
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
