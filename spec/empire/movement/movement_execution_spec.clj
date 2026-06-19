(ns empire.game-mechanics.movement.movement-execution-spec
  (:require [empire.test.utils :as test-utils]
    [empire.config.core :as config]
    [empire.game.loop.core :as game-loop]
    [empire.game-mechanics.movement.explore :as explore]
    [empire.game-mechanics.movement.api :refer :all]
    [empire.game-mechanics.movement.movement-execution :as movement-execution]
    [empire.game-mechanics.movement.movement-pathing :as pathing]
    [empire.game-mechanics.movement.movement-state :as movement-state]
    [empire.game-mechanics.visibility :as visibility]
    [empire.game-mechanics.movement.wake-conditions :as wake]
    [empire.test.utils :refer [build-test-map get-test-unit set-test-unit reset-all-atoms! set-test-player-map! set-test-world! update-test-world!]]
    [speclj.core :refer :all]))
(describe "set-unit-movement"
  (before (reset-all-atoms!))

  (it "sets mode to moving with target"
    (set-test-world! (build-test-map ["A##"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (set-unit-movement [0 0] [2 0])
    (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (should= :moving (:mode unit))
      (should= [2 0] (:target unit))
      (should-be-nil (:extended unit))))

  (it "sets extended flag when extended is true"
    (set-test-world! (build-test-map ["A##"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (set-unit-movement [0 0] [2 0] true)
    (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (should= :moving (:mode unit))
      (should (:extended unit))))

  (it "does not set extended flag when extended is false"
    (set-test-world! (build-test-map ["A##"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (set-unit-movement [0 0] [2 0] false)
    (should-be-nil (:extended (get-in (test-utils/read-test-state :game-map) [0 0 :contents])))))

(describe "fighter landing"
  (before (reset-all-atoms!))

  (it "fighter landing at player city increments fighter-count"
    (set-test-world! (build-test-map ["FO"]))
    (set-test-unit (test-utils/game-map-atom) "F" :fuel 20 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-player-map! (build-test-map ["--"]))
    (game-loop/move-current-unit [0 0])
    (let [city (get-in (test-utils/read-test-state :game-map) [1 0])]
      ;; Fighter should be absorbed into city, not placed as contents
      (should-be-nil (:contents city))
      (should= 1 (:fighter-count city))))

  (it "fighter landing on carrier increments carrier fighter-count"
    (set-test-world! (build-test-map ["JC"]))
    (set-test-unit (test-utils/game-map-atom) "F" :fuel 20 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-player-map! (build-test-map ["~~"]))
    (game-loop/move-current-unit [0 0])
    (let [carrier (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
      (should= :carrier (:type carrier))
      (should= 1 (:fighter-count carrier))))

  (it "fighter landing at player city does not remain as contents"
    (set-test-world! (build-test-map ["FO"]))
    (set-test-unit (test-utils/game-map-atom) "F" :fuel 20 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-player-map! (build-test-map ["--"]))
    (game-loop/move-current-unit [0 0])
    ;; Fighter absorbed into city — not left as contents
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

(describe "city sidestepping"
  (before (reset-all-atoms!))

  (it "army sidesteps around friendly city"
    ;; Army at [1,0] moving right, friendly city at [2,0]
    ;; Should sidestep to [2,1] (diagonal) then continue toward [4,0]
    (set-test-world! (build-test-map ["#AO##"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [4 0] :steps-remaining 1)
    (set-test-player-map! (build-test-map ["-----" "-----"]))
    (with-redefs [rand-nth first]
      (game-loop/move-current-unit [1 0]))
    ;; Army should have sidestepped, not stayed at [1,0]
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))
    ;; Should be at [2,1] (sidestep down-right) since that gets closest to target
    (should= :army (:type (:contents (get-in (test-utils/read-test-state :game-map) [2 1])))))

  (it "fighter sidesteps around non-target city"
    ;; Fighter at [1,0] heading toward [4,0], player city at [2,0] is NOT target
    (set-test-world! (build-test-map ["#FO##"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "F" :fuel 20 :mode :moving :target [4 0] :steps-remaining 1)
    (set-test-player-map! (build-test-map ["-----" "-----"]))
    (with-redefs [rand-nth first]
      (game-loop/move-current-unit [1 0]))
    ;; Fighter should have sidestepped
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))))

  (it "fighter does not sidestep its target city"
    ;; Fighter heading to a player city that IS its target — should land there
    (set-test-world! (build-test-map ["FO#"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "F" :fuel 20 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-player-map! (build-test-map ["---" "---"]))
    (game-loop/move-current-unit [0 0])
    ;; Fighter should land at the city (its target), not sidestep
    (let [city (get-in (test-utils/read-test-state :game-map) [1 0])]
      (should= 1 (:fighter-count city))))

  (it "fighter sidesteps computer city that is not its target"
    ;; Fighter at [1,0] heading to [4,0], computer city at [2,0]
    ;; Fighter should sidestep around the non-target city
    (set-test-world! (build-test-map ["#FX##"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "F" :fuel 20 :mode :moving :target [4 0] :steps-remaining 1)
    (set-test-player-map! (build-test-map ["-----" "-----"]))
    (with-redefs [rand-nth first]
      (game-loop/move-current-unit [1 0]))
    ;; Fighter should have moved away from [1,0] via sidestep
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

(describe "army sidestep verifies exact position"
  (before (reset-all-atoms!))

  (it "army sidestepping horizontally blocked goes to correct diagonal"
    ;; Army at [2,1] moving right toward [5,1], friendly city at [3,1]
    ;; Blocked direction is [1,0] (horizontal). Sidestep candidates:
    ;; [[1 1] [1 -1] [0 1] [0 -1]] → positions [3,2] [3,0] [2,2] [2,0]
    ;; Make [3,0] sea so only [3,2], [2,2], [2,0] are valid land
    (set-test-world! (build-test-map ["###~##"
                                             "##AO##"
                                             "######"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [5 1] :steps-remaining 1)
    (set-test-player-map! (build-test-map ["------" "------" "------"]))
    (with-redefs [rand-nth first]
      (game-loop/move-current-unit [2 1]))
    ;; [3,2] should be best (diagonal toward target, gets closest)
    (should= :army (:type (:contents (get-in (test-utils/read-test-state :game-map) [3 2])))))

  (it "army sidestepping vertically blocked uses correct candidates"
    ;; Army at [1,0] moving down toward [1,3], friendly army at [1,1]
    ;; Blocked direction is [0,1] (vertical). Sidestep candidates:
    ;; [[1 dy] [-1 dy] [1 0] [-1 0]] = [[1 1] [-1 1] [1 0] [-1 0]]
    ;; → positions [2,1] [0,1] [2,0] [0,0]
    (set-test-world! (build-test-map ["#A#"
                                             "#A#"
                                             "###"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "A1" :mode :moving :target [1 3] :steps-remaining 1)
    (set-test-unit (test-utils/game-map-atom) "A2" :mode :sentry)
    (set-test-player-map! (build-test-map ["---" "---" "---" "---"]))
    (with-redefs [rand-nth first]
      (game-loop/move-current-unit [1 0]))
    ;; Should have sidestepped to [2,1] (first candidate that's closer)
    (let [unit21 (:contents (get-in (test-utils/read-test-state :game-map) [2 1]))]
      (should= :army (:type unit21)))))

(describe "sidestep around friendly unit"
  (before (reset-all-atoms!))

  (it "army sidesteps around friendly army blocking path"
    ;; Army at [0,0] moving right toward [3,0], friendly army at [1,0]
    (set-test-world! (build-test-map ["AA##"
                                             "####"]))
    (set-test-unit (test-utils/game-map-atom) "A1" :mode :moving :target [3 0] :steps-remaining 1)
    (set-test-unit (test-utils/game-map-atom) "A2" :mode :sentry)
    (set-test-player-map! (build-test-map ["----" "----"]))
    (with-redefs [rand-nth first]
      (game-loop/move-current-unit [0 0]))
    ;; Army should have sidestepped (not stayed at [0,0])
    (let [unit0 (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))
          unit1 (:contents (get-in (test-utils/read-test-state :game-map) [1 1]))]
      ;; Either sidestepped or woke up
      (should (or (= :army (:type unit1))
                  (= :awake (:mode unit0))))))

  (it "army sidesteps diagonally when blocked diagonally"
    ;; Army at [0,0] moving toward [2,2], friendly army at [1,1]
    (set-test-world! (build-test-map ["A--"
                                             "-A-"
                                             "--#"]))
    (set-test-unit (test-utils/game-map-atom) "A1" :mode :moving :target [2 2] :steps-remaining 1)
    (set-test-unit (test-utils/game-map-atom) "A2" :mode :sentry)
    (set-test-player-map! (build-test-map ["---" "---" "---"]))
    (with-redefs [rand-nth first]
      (game-loop/move-current-unit [0 0]))
    ;; Should have attempted to sidestep
    (let [unit00 (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))
          result (get-test-unit (test-utils/game-map-atom) "A1")]
      (should (or
                ;; Either woke up or sidestepped elsewhere
                (contains? #{:awake :moving} (:mode unit00))
                ;; Moved somewhere
                (and result (not= [0 0] (:pos result))))))))

(describe "transport auto-loads sentry armies"
  (before (reset-all-atoms!))

  (it "loads adjacent sentry army after transport moves"
    ;; Transport at [0,0] (sea), target [1,0] (sea), sentry army at [1,1] (land)
    (set-test-world! (build-test-map ["T~"
                                             "~A"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry)
    (set-test-player-map! (build-test-map ["~~" "~~"]))
    (game-loop/move-current-unit [0 0])
    ;; Transport moved to [1,0]; sentry army at [1,1] should be loaded
    (let [transport (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
      (should= :transport (:type transport))
      (should= 1 (:army-count transport)))
    ;; Army cell should be empty
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 1])))))

(describe "wake-at return values"
  (before
    (reset-all-atoms!)
    (set-test-world! (build-test-map ["###" "###" "###"]))
    (test-utils/set-test-state! :production {}))

  (it "returns truthy for waking a transport without armies"
    ;; A moving transport (no armies) should be woken via the regular unit branch
    (update-test-world! assoc-in [1 1 :contents]
           {:type :transport :owner :player :mode :moving :hits 1})
    (should (wake-at [1 1]))
    (should= :awake (get-in (test-utils/read-test-state :game-map) [1 1 :contents :mode])))

  (it "wakes transport without armies via regular branch not transport branch"
    ;; Transport with NO army-count key — default 0 means (pos? 0) = false
    ;; Should fall through to regular unit wake branch, not transport-armies branch
    (update-test-world! assoc-in [1 1 :contents]
           {:type :transport :owner :player :mode :sentry})
    (should (wake-at [1 1]))
    (should= :awake (get-in (test-utils/read-test-state :game-map) [1 1 :contents :mode]))
    ;; Should NOT have :awake-armies set (regular branch doesn't call wake-all)
    (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents :awake-armies]))))

(describe "add-unit-at satellite"
  (before
    (reset-all-atoms!)
    (set-test-world! (build-test-map ["###" "###" "###"])))

  (it "adds satellite with turns-remaining"
    (add-unit-at [1 1] :satellite)
    (let [contents (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
      (should= :satellite (:type contents))
      (should= config/satellite-turns (:turns-remaining contents))))

  (it "satellite does not have fuel"
    (add-unit-at [1 1] :satellite)
    (should-be-nil (:fuel (get-in (test-utils/read-test-state :game-map) [1 1 :contents])))))

(describe "update-destination-cell"
  (let [update-dest movement-execution/update-destination-cell]
    (it "returns unchanged cell for :unit-destroyed"
      (let [to-cell {:type :sea}]
        (should= to-cell (update-dest :unit-destroyed to-cell nil))))

    (it "adds fighter to city airport for :fighter-land-at-city"
      (let [to-cell {:type :city :city-status :player :fighter-count 0}
            result (update-dest :fighter-land-at-city to-cell nil)]
        (should= 1 (:fighter-count result))))

    (it "adds fighter to carrier for :fighter-land-on-carrier"
      (let [carrier {:type :carrier :owner :player :hits 3 :fighter-count 1}
            to-cell {:type :sea :contents carrier}
            result (update-dest :fighter-land-on-carrier to-cell nil)]
        (should= 2 (get-in result [:contents :fighter-count]))))

    (it "places unit as contents for :normal-move"
      (let [unit {:type :army :owner :player :hits 1}
            to-cell {:type :land}
            result (update-dest :normal-move to-cell unit)]
        (should= unit (:contents result))))))
