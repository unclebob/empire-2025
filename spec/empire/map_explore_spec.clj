(ns empire.map-explore-spec
  (:require [empire.ui.util.input.actions.movement :as input-movement]
            [empire.game-mechanics.movement.explore :as explore]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.ui.util.input.actions :as input]
            [empire.config.core :as config]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map get-test-unit make-initial-test-map reset-all-atoms! set-test-unit set-test-world! set-test-player-map!]]
            [speclj.core :refer :all]))

(describe "explore mode"
  (before (reset-all-atoms!))

  (it "handle-key with 'l' puts army in explore mode"
    (set-test-world! (build-test-map ["A"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (test-utils/set-test-state! :cells-needing-attention [[0 0]])
    (input/handle-key :l)
    (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
      (should= :explore (:mode unit))
      (should= config/explore-steps (:explore-steps unit))))

  (it "handle-key with 'x' moves non-army units south"
    (set-test-world! (build-test-map ["F#"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :fuel 20)
    (test-utils/set-test-state! :cells-needing-attention [[0 0]])
    (input/handle-key :x)
    (should= :moving (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))))

  (it "explore army moves to valid adjacent cell"
    (set-test-world! (build-test-map ["A#"
                                      "##"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
    (set-test-player-map! (make-initial-test-map 2 2 nil))
    (let [result (explore/move-explore-unit [0 0])]
      (should= nil result)
      (should= nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (let [moved-unit (some #(:contents (get-in (test-utils/read-test-state :game-map) %))
                             [[1 0] [0 1] [1 1]])]
        (should= :army (:type moved-unit))
        (should= :explore (:mode moved-unit))
        (should= 49 (:explore-steps moved-unit)))))

  (it "explore army avoids cells with units"
    (set-test-world! (build-test-map ["Aa"
                                      "a#"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
    (set-test-player-map! (make-initial-test-map 2 2 nil))
    (explore/move-explore-unit [0 0])
    (should-not-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 1]))))

  (it "explore army avoids cities"
    (set-test-world! (build-test-map ["A+"
                                      "O#"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
    (set-test-player-map! (make-initial-test-map 2 2 nil))
    (explore/move-explore-unit [0 0])
    (should-not-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 1]))))

  (it "explore army wakes up after 50 steps"
    (set-test-world! (build-test-map ["A#"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 1)
    (set-test-player-map! (make-initial-test-map 1 2 nil))
    (let [result (explore/move-explore-unit [0 0])]
      (should= nil result)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= :awake (:mode unit))
        (should= nil (:explore-steps unit)))))

  (it "explore army wakes up when stuck"
    (set-test-world! (build-test-map ["A~"
                                      "~~"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
    (set-test-player-map! (make-initial-test-map 2 2 nil))
    (let [result (explore/move-explore-unit [0 0])]
      (should= nil result)
      (should= :awake (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))))

  (it "explore army prefers coastal moves when on coast"
    (let [initial-map (build-test-map ["~A#"
                                       "~##"
                                       "###"])]
      (set-test-world! initial-map)
      (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
      (set-test-player-map! (test-utils/read-test-state :game-map))
      (dotimes [_ 10]
        (set-test-world! initial-map)
        (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
        (explore/move-explore-unit [1 0])
        (let [{:keys [pos]} (get-test-unit (test-utils/game-map-atom) "A")]
          (should (map-utils/adjacent-to-sea? pos (test-utils/game-map-atom)))))))

  (it "explore army prefers moves towards unexplored cells"
    (let [initial-map (build-test-map ["#A#"
                                       "###"
                                       "###"])
          player-map [[{:type :land} {:type :land} nil]
                      [{:type :land} {:type :land} nil]
                      [{:type :land} {:type :land} nil]]]
      (dotimes [_ 10]
        (set-test-world! initial-map)
        (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
        (set-test-player-map! player-map)
        (explore/move-explore-unit [1 0])
        (let [{:keys [pos]} (get-test-unit (test-utils/game-map-atom) "A")]
          (should= 1 (second pos))))))

  (it "explore army does not retrace steps"
    (let [initial-map (build-test-map ["#A#"])]
      (set-test-world! initial-map)
      (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50 :visited #{[0 0]})
      (set-test-player-map! (test-utils/read-test-state :game-map))
      (dotimes [_ 10]
        (set-test-world! initial-map)
        (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50 :visited #{[0 0]})
        (explore/move-explore-unit [1 0])
        (should-not-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [2 0]))))))

  (it "explore army wakes up when finding enemy city"
    (set-test-world! (build-test-map ["A#X"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
    (set-test-player-map! (test-utils/read-test-state :game-map))
    (explore/move-explore-unit [0 0])
    (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
      (should= :awake (:mode unit))
      (should= :army-found-city (:reason unit))
      (should= nil (:explore-steps unit))))

  (it "explore army wakes up when finding free city"
    (set-test-world! (build-test-map ["A#+"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :explore-steps 50)
    (set-test-player-map! (test-utils/read-test-state :game-map))
    (explore/move-explore-unit [0 0])
    (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
      (should= :awake (:mode unit))
      (should= :army-found-city (:reason unit)))))

(describe "calculate-extended-target"
  (before (reset-all-atoms!))

  (it "calculates target at map edge going east"
    (set-test-world! (build-test-map ["#####"
                                      "#####"
                                      "#####"
                                      "#####"
                                      "#####"]))
    (should= [4 0] (#'input-movement/calculate-extended-target [0 0] [1 0])))

  (it "calculates target at map edge going south"
    (set-test-world! (build-test-map ["#####"
                                      "#####"
                                      "#####"
                                      "#####"
                                      "#####"]))
    (should= [0 4] (#'input-movement/calculate-extended-target [0 0] [0 1])))

  (it "calculates target at map edge going southeast"
    (set-test-world! (build-test-map ["#####"
                                      "#####"
                                      "#####"
                                      "#####"
                                      "#####"]))
    (should= [4 4] (#'input-movement/calculate-extended-target [0 0] [1 1])))

  (it "calculates target at map edge going west"
    (set-test-world! (build-test-map ["#####"
                                      "#####"
                                      "#####"
                                      "#####"
                                      "#####"]))
    (should= [0 2] (#'input-movement/calculate-extended-target [4 2] [-1 0])))

  (it "calculates target at map edge going north"
    (set-test-world! (build-test-map ["#####"
                                      "#####"
                                      "#####"
                                      "#####"
                                      "#####"]))
    (should= [2 0] (#'input-movement/calculate-extended-target [2 4] [0 -1])))

  (it "returns starting position when already at edge"
    (set-test-world! (build-test-map ["#####"
                                      "#####"
                                      "#####"
                                      "#####"
                                      "#####"]))
    (should= [0 0] (#'input-movement/calculate-extended-target [0 0] [-1 0])))

  (it "works with non-square maps"
    (set-test-world! (build-test-map ["###"
                                      "###"
                                      "###"
                                      "###"
                                      "###"
                                      "###"
                                      "###"
                                      "###"
                                      "###"
                                      "###"]))
    (should= [2 1] (#'input-movement/calculate-extended-target [0 1] [1 0]))))
