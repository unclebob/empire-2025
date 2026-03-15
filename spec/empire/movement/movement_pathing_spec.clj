(ns empire.game-mechanics.movement.movement-pathing-spec
  (:require [empire.test.utils :as test-utils]
    [empire.config.core :as config]
    [empire.game.loop.core :as game-loop]
    [empire.game-mechanics.movement.explore :as explore]
    [empire.game-mechanics.movement.api :refer :all]
    [empire.game-mechanics.movement.movement-execution :as movement-execution]
    [empire.game-mechanics.movement.movement-pathing :as pathing]
    [empire.game-mechanics.movement.movement-state :as movement-state]
    [empire.game-mechanics.movement.visibility :as visibility]
    [empire.game-mechanics.movement.wake-conditions :as wake]
    [empire.test.utils :refer [build-test-map get-test-unit set-test-unit reset-all-atoms! set-test-player-map! set-test-world! update-test-world!]]
    [speclj.core :refer :all]))
(describe "diagonal?"
  (it "returns true for diagonal direction"
    (should (pathing/diagonal? 1 1)))
  (it "returns true for negative diagonal"
    (should (pathing/diagonal? -1 -1)))
  (it "returns false when dx is zero"
    (should-not (pathing/diagonal? 0 1)))
  (it "returns false when dy is zero"
    (should-not (pathing/diagonal? 1 0)))
  (it "returns false when both zero"
    (should-not (pathing/diagonal? 0 0))))

(describe "get-sidestep-directions"
  (it "returns diagonal-first for diagonal direction [1 1]"
    (should= [[1 0] [0 1] [-1 1] [1 -1]]
             (pathing/get-sidestep-directions [1 1])))
  (it "returns diagonal-first for diagonal direction [-1 -1]"
    (should= [[-1 0] [0 -1] [1 -1] [-1 1]]
             (pathing/get-sidestep-directions [-1 -1])))
  (it "returns horizontal-adjacent for east [1 0]"
    (should= [[1 1] [1 -1] [0 1] [0 -1]]
             (pathing/get-sidestep-directions [1 0])))
  (it "returns horizontal-adjacent for west [-1 0]"
    (should= [[-1 1] [-1 -1] [0 1] [0 -1]]
             (pathing/get-sidestep-directions [-1 0])))
  (it "returns vertical-adjacent for south [0 1]"
    (should= [[1 1] [-1 1] [1 0] [-1 0]]
             (pathing/get-sidestep-directions [0 1])))
  (it "returns vertical-adjacent for north [0 -1]"
    (should= [[1 -1] [-1 -1] [1 0] [-1 0]]
             (pathing/get-sidestep-directions [0 -1]))))

(describe "wake-at"
  (before
    (reset-all-atoms!)
    (set-test-world! (build-test-map ["###" "###" "###"]))
    (test-utils/set-test-state! :production {}))

  (it "wakes a sleeping unit"
    (update-test-world! assoc-in [1 1 :contents]
           {:type :army :owner :player :mode :sentry})
    (should (wake-at [1 1]))
    (should= :awake (get-in (test-utils/read-test-state :game-map) [1 1 :contents :mode])))

  (it "wakes unit in explore mode"
    (update-test-world! assoc-in [1 1 :contents]
           {:type :army :owner :player :mode :explore})
    (should (wake-at [1 1]))
    (should= :awake (get-in (test-utils/read-test-state :game-map) [1 1 :contents :mode])))

  (it "returns nil for already awake unit"
    (update-test-world! assoc-in [1 1 :contents]
           {:type :army :owner :player :mode :awake})
    (should-not (wake-at [1 1])))

  (it "returns nil for enemy unit"
    (update-test-world! assoc-in [1 1 :contents]
           {:type :army :owner :computer :mode :sentry})
    (should-not (wake-at [1 1])))

  (it "wakes player city and removes production"
    (update-test-world! assoc-in [1 1]
           {:type :city :city-status :player :sleeping-fighters 0 :awake-fighters 0})
    (test-utils/set-test-state! :production {[1 1] {:item :army :remaining-rounds 5}})
    (should (wake-at [1 1]))
    (should-not (get (test-utils/read-test-state :production) [1 1])))

  (it "returns nil for empty cell"
    (should-not (wake-at [1 1])))

  (it "returns nil for enemy city"
    (update-test-world! assoc-in [1 1]
           {:type :city :city-status :computer})
    (should-not (wake-at [1 1])))

  (it "wakes armies aboard a sentry transport"
    (update-test-world! assoc-in [1 1 :contents]
           {:type :transport :owner :player :mode :sentry :army-count 3 :awake-armies 0})
    (should (wake-at [1 1]))
    (let [transport (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
      (should= :awake (:mode transport))
      (should= 3 (:awake-armies transport))))

  (it "wakes armies aboard an already-awake transport"
    (update-test-world! assoc-in [1 1 :contents]
           {:type :transport :owner :player :mode :awake :army-count 4 :awake-armies 0})
    (should (wake-at [1 1]))
    (should= 4 (get-in (test-utils/read-test-state :game-map) [1 1 :contents :awake-armies])))

  (it "clears coastline state when waking a coastline-follow unit"
    (update-test-world! assoc-in [1 1 :contents]
           {:type :patrol-boat :owner :player :mode :coastline-follow :hits 2
            :coastline-steps 5 :visited #{[0 0] [1 0]} :start-pos [0 0] :prev-pos [1 0]
            :target [2 2] :reason :something})
    (should (wake-at [1 1]))
    (let [unit (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
      (should= :awake (:mode unit))
      (should-be-nil (:coastline-steps unit))
      (should-be-nil (:visited unit))
      (should-be-nil (:start-pos unit))
      (should-be-nil (:prev-pos unit))
      (should-be-nil (:target unit))
      (should-be-nil (:reason unit)))))

(describe "combat during movement"
  (before (reset-all-atoms!))

  (it "army attacks enemy army when moving into its cell"
    (set-test-world! (build-test-map ["Aa"]))
    (set-test-unit (test-utils/game-map-atom) "A" :hits 1 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-unit (test-utils/game-map-atom) "a" :hits 1)
    (set-test-player-map! (build-test-map ["--"]))
    (with-redefs [rand (constantly 0.4)]
      (game-loop/move-current-unit [0 0])
      (should= nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= :army (:type (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))
      (should= :player (:owner (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))))

  (it "army is destroyed when losing to enemy army"
    (set-test-world! (build-test-map ["Aa"]))
    (set-test-unit (test-utils/game-map-atom) "A" :hits 1 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-unit (test-utils/game-map-atom) "a" :hits 1)
    (set-test-player-map! (build-test-map ["--"]))
    (with-redefs [rand (constantly 0.6)]
      (game-loop/move-current-unit [0 0])
      (should= nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= :army (:type (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))
      (should= :computer (:owner (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))))

  (it "destroyer attacks enemy transport on sea"
    (set-test-world! (build-test-map ["Dt"]))
    (set-test-unit (test-utils/game-map-atom) "D" :hits 3 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-unit (test-utils/game-map-atom) "t" :hits 1)
    (set-test-player-map! (build-test-map ["~~"]))
    (with-redefs [rand (constantly 0.4)]
      (game-loop/move-current-unit [0 0])
      (should= nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= :destroyer (:type (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))
      (should= :player (:owner (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))))

  (it "fighter attacks enemy fighter"
    (set-test-world! (build-test-map ["Ff"]))
    (set-test-unit (test-utils/game-map-atom) "F" :hits 1 :fuel 20 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-unit (test-utils/game-map-atom) "f" :hits 1)
    (set-test-player-map! (build-test-map ["--"]))
    (with-redefs [rand (constantly 0.4)]
      (game-loop/move-current-unit [0 0])
      (should= nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      (should= :fighter (:type (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))
      (should= :player (:owner (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))))

  (it "attacker survives with reduced hits"
    (set-test-world! (build-test-map ["Dd"]))
    (set-test-unit (test-utils/game-map-atom) "D" :hits 3 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-unit (test-utils/game-map-atom) "d" :hits 3)
    (set-test-player-map! (build-test-map ["~~"]))
    ;; Rolls: 0.4 (D hits d:2), 0.6 (d hits D:2), 0.4 (D hits d:1), 0.4 (D hits d:0)
    (let [rolls (atom [0.4 0.6 0.4 0.4])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (game-loop/move-current-unit [0 0])
        (let [survivor (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
          (should= :destroyer (:type survivor))
          (should= :player (:owner survivor))
          (should= 2 (:hits survivor))))))

  (it "does not attack friendly units"
    (set-test-world! (build-test-map ["AA"]))
    (set-test-unit (test-utils/game-map-atom) "A1" :hits 1 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-unit (test-utils/game-map-atom) "A2" :hits 1)
    (set-test-player-map! (build-test-map ["--"]))
    (game-loop/move-current-unit [0 0])
    ;; Should wake up, not attack
    (should= :awake (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))
    (should= :army (:type (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

  (it "army cannot attack ship on sea (terrain incompatible)"
    (set-test-world! (build-test-map ["A~"]))
    (update-test-world! assoc-in [1 0 :contents] {:type :destroyer :owner :computer :hits 3})
    (set-test-unit (test-utils/game-map-atom) "A" :hits 1 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-player-map! (build-test-map ["--"]))
    (game-loop/move-current-unit [0 0])
    ;; Army should wake up because it can't move onto sea
    (should= :awake (:mode (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))
    (should= :destroyer (:type (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))))

(describe "chebyshev-distance"
  (it "returns correct distance for asymmetric positions"
    (should= 5 (chebyshev-distance [1 2] [6 4]))
    (should= 3 (chebyshev-distance [0 0] [3 1]))
    (should= 4 (chebyshev-distance [5 1] [2 5])))

  (it "returns 0 for same position"
    (should= 0 (chebyshev-distance [3 7] [3 7])))

  (it "handles negative coordinate differences"
    (should= 3 (chebyshev-distance [3 3] [0 0]))))

(describe "process-consumables"
  (before (reset-all-atoms!))

  (it "returns non-fighter unit unchanged"
    (let [unit {:type :army :owner :player :hits 1}
          result (process-consumables unit {:type :sea})]
      (should= unit result)))

  (it "refuels fighter landing at city"
    (let [unit {:type :fighter :owner :player :fuel 5}
          result (process-consumables unit {:type :city :city-status :player})]
      (should= 5 (:fuel result))))

  (it "decrements fuel for fighter not at city"
    (let [unit {:type :fighter :owner :player :fuel 10}
          result (process-consumables unit {:type :land})]
      (should= 9 (:fuel result))))

  (it "destroys fighter when fuel runs out"
    (let [unit {:type :fighter :owner :player :fuel 0}
          result (process-consumables unit {:type :land})]
      (should-be-nil result)))

  (it "keeps fighter alive at fuel 1"
    (let [unit {:type :fighter :owner :player :fuel 1}
          result (process-consumables unit {:type :land})]
      (should= 0 (:fuel result))))

  (it "returns nil for nil unit"
    (should-be-nil (process-consumables nil {:type :land}))))

(describe "transport-with-awake-armies?"
  (it "returns true for transport with awake armies"
    (should (movement-state/transport-with-awake-armies?
              {:type :transport :awake-armies 1})))
  (it "returns false for non-transport"
    (should-not (movement-state/transport-with-awake-armies?
                  {:type :carrier :awake-armies 1})))
  (it "returns false for transport without awake armies"
    (should-not (movement-state/transport-with-awake-armies?
                  {:type :transport :awake-armies 0}))))

(describe "carrier-with-awake-fighters?"
  (it "returns true for carrier with awake fighters"
    (should (movement-state/carrier-with-awake-fighters?
              {:type :carrier :awake-fighters 1})))
  (it "returns false for non-carrier"
    (should-not (movement-state/carrier-with-awake-fighters?
                  {:type :transport :awake-fighters 1})))
  (it "returns false for carrier without awake fighters"
    (should-not (movement-state/carrier-with-awake-fighters?
                  {:type :carrier :awake-fighters 0}))))

(describe "awake-unit?"
  (it "returns true for awake unit"
    (should (movement-state/awake-unit?
              {:type :army :mode :awake})))
  (it "returns false for nil"
    (should-not (movement-state/awake-unit? nil)))
  (it "returns false for non-awake unit"
    (should-not (movement-state/awake-unit?
                  {:type :army :mode :sentry}))))

(describe "get-active-unit"
  (before (reset-all-atoms!))

  (it "returns awake army aboard transport"
    (let [cell {:contents {:type :transport :owner :player :awake-armies 1 :army-count 2}}
          result (get-active-unit cell)]
      (should= :army (:type result))
      (should (:aboard-transport result))
      (should= :player (:owner result))))

  (it "returns nil for transport without awake armies"
    (let [cell {:contents {:type :transport :owner :player :awake-armies 0 :army-count 2}}
          result (get-active-unit cell)]
      (should-be-nil result)))

  (it "returns awake fighter on carrier"
    (let [cell {:contents {:type :carrier :owner :player :awake-fighters 1 :fighter-count 2}}
          result (get-active-unit cell)]
      (should= :fighter (:type result))
      (should (:from-carrier result))
      (should= :player (:owner result))))

  (it "returns nil for carrier without awake fighters"
    (let [cell {:contents {:type :carrier :owner :player :awake-fighters 0 :fighter-count 2}}
          result (get-active-unit cell)]
      (should-be-nil result)))

  (it "returns awake contents directly"
    (let [unit {:type :destroyer :owner :player :mode :awake :hits 3}
          cell {:contents unit}
          result (get-active-unit cell)]
      (should= unit result)))

  (it "returns nil for sleeping contents"
    (let [cell {:contents {:type :army :owner :player :mode :sentry}}]
      (should-be-nil (get-active-unit cell))))

  (it "returns airport fighter when city has awake fighters"
    (let [cell {:type :city :awake-fighters 1 :fighter-count 2}
          result (get-active-unit cell)]
      (should= :fighter (:type result))
      (should (:from-airport result))))

  (it "prefers transport army over awake contents"
    (let [cell {:contents {:type :transport :owner :player :mode :awake :awake-armies 1 :army-count 1}}
          result (get-active-unit cell)]
      (should= :army (:type result))
      (should (:aboard-transport result)))))
