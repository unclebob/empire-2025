(ns empire.game-loop-auto-spec
  (:require [empire.game.loop.core :as game-loop]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map get-test-unit reset-all-atoms! set-test-player-map! set-test-unit set-test-world!]]
            [speclj.core :refer :all]))

(describe "auto-move logic"
  (before (reset-all-atoms!))

  (context "move-satellites"
    (it "removes satellite when turns-remaining reaches zero during movement"
      (set-test-world! (build-test-map ["V#"]))
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 1)
      (set-test-player-map! (build-test-map ["##"]))
      (game-loop/move-satellites)
      (let [result (get-test-unit (test-utils/game-map-atom) "V")]
        (should-be-nil result)))

    (it "removes satellite immediately when turns-remaining is already zero"
      (set-test-world! (build-test-map ["V"]))
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 0)
      (set-test-player-map! (build-test-map ["#"]))
      (game-loop/move-satellites)
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "decrements turns-remaining after movement"
      (set-test-world! (build-test-map ["V##"]))
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 5)
      (set-test-player-map! (build-test-map ["###"]))
      (game-loop/move-satellites)
      (let [{:keys [unit]} (get-test-unit (test-utils/game-map-atom) "V")]
        (should-not-be-nil unit)
        (should (< (:turns-remaining unit) 5)))))

  (context "move-explore-unit"
    (it "delegates to movement/move-explore-unit"
      (set-test-world! (build-test-map ["A#"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :visited #{[0 0]})
      (set-test-player-map! (build-test-map ["##"]))
      (let [result (game-loop/move-explore-unit [0 0])]
        (should (or (nil? result) (vector? result))))))

  (context "move-coastline-unit"
    (it "delegates to movement/move-coastline-unit"
      (set-test-world! (build-test-map ["#~~~~"
                                        "#~~~~"
                                        "#T~~~"
                                        "#~~~~"
                                        "#~~~~"]))
      (set-test-unit (test-utils/game-map-atom) "T" :mode :coastline-follow :coastline-steps 50
                     :start-pos [1 2] :visited #{[1 2]} :prev-pos nil)
      (set-test-player-map! (test-utils/read-test-state :game-map))
      (let [result (game-loop/move-coastline-unit [1 2])]
        (should-be-nil result))))

  (context "auto-launch-fighter from airport"
    (it "launches fighter when city has flight-path and awake fighters"
      (set-test-world! (-> (build-test-map ["O#"])
                           (assoc-in [0 0 :flight-path] [1 0])
                           (assoc-in [0 0 :awake-fighters] 1)
                           (assoc-in [0 0 :fighter-count] 1)))
      (set-test-player-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game)
      (let [city (get-in (test-utils/read-test-state :game-map) [0 0])]
        (should= 0 (:awake-fighters city 0)))
      (let [fighter-at-target (get-in (test-utils/read-test-state :game-map) [1 0])]
        (should= :fighter (:type (:contents fighter-at-target)))))

    (it "does not launch fighter when army is on city"
      (set-test-world! (-> (build-test-map ["O#"])
                           (assoc-in [0 0 :flight-path] [1 0])
                           (assoc-in [0 0 :awake-fighters] 1)
                           (assoc-in [0 0 :fighter-count] 1)
                           (assoc-in [0 0 :contents] {:type :army :mode :moving :target [1 0] :hits 1 :owner :player})))
      (set-test-player-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game)
      (let [city (get-in (test-utils/read-test-state :game-map) [0 0])]
        (should= 1 (:awake-fighters city))
        (should= 1 (:fighter-count city))))

    (it "launches fighter from carrier with flight-path"
      (set-test-world! (build-test-map ["C~"]))
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :flight-path [1 0] :awake-fighters 1 :fighter-count 1)
      (set-test-player-map! (build-test-map ["~~"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game)
      (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))]
        (should= 0 (:awake-fighters carrier 0)))))

  (context "auto-disembark-army"
    (it "disembarks army when transport has marching-orders and awake armies"
      (set-test-world! (build-test-map ["T#"]))
      (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :marching-orders [0 1] :awake-armies 1 :army-count 1)
      (set-test-player-map! (build-test-map ["~#"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game)
      (let [land-cell (get-in (test-utils/read-test-state :game-map) [1 0])]
        (should= :army (:type (:contents land-cell))))))

  (context "advance-game with explore mode"
    (it "processes exploring unit"
      (set-test-world! (build-test-map ["A#"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :explore :visited #{[0 0]})
      (set-test-player-map! (build-test-map ["##"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game)
      (should-not= [[0 0]] (test-utils/read-test-state :player-items))))

  (context "advance-game with coastline-follow mode"
    (it "processes coastline-following unit and continues when returning new coords"
      (set-test-world! (build-test-map ["#~~~~~~~~~"
                                        "#~~~~~~~~~"
                                        "#~~~~~~~~~"
                                        "#~~~~~~~~~"
                                        "#~~~~~~~~~"
                                        "#T~~~~~~~~"
                                        "#~~~~~~~~~"
                                        "#~~~~~~~~~"
                                        "#~~~~~~~~~"
                                        "#~~~~~~~~~"]))
      (set-test-unit (test-utils/game-map-atom) "T" :mode :coastline-follow :coastline-steps 50
                     :start-pos [1 5] :visited #{[1 5]} :prev-pos nil)
      (set-test-player-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[1 5]])
      (test-utils/set-test-state! :waiting-for-input false)
      (game-loop/advance-game)
      (should (or (empty? (test-utils/read-test-state :player-items))
                  (not= [[1 5]] (vec (test-utils/read-test-state :player-items)))))))

  (context "advance-game with fighter combat"
    (it "keeps a victorious fighter active when it still has steps remaining"
      (set-test-world! (build-test-map ["Fa#"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :moving :target [2 0] :steps-remaining 8 :hits 1)
      (set-test-unit (test-utils/game-map-atom) "a" :hits 1)
      (set-test-player-map! (build-test-map ["###"]))
      (test-utils/set-test-state! :production {})
      (test-utils/set-test-state! :player-items [[0 0]])
      (test-utils/set-test-state! :waiting-for-input false)
      (with-redefs [rand (constantly 0.0)]
        (game-loop/advance-game))
      (let [fighter (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
        (should= :fighter (:type fighter))
        (should= 7 (:steps-remaining fighter))
        (should= [[1 0]] (vec (test-utils/read-test-state :player-items)))
        (should= true (test-utils/read-test-state :waiting-for-input))))))
