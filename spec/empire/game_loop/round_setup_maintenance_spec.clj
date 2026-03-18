(ns empire.game-loop-round-setup-maintenance-spec
  (:require [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.game-mechanics.movement.wake-conditions :as wake]
            [empire.game.loop.round-setup :as setup]
            [empire.game.loop.round-setup.fuel :as fuel]
            [empire.game.loop.round-setup.repair :as repair]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map get-test-unit reset-all-atoms! set-test-unit set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "dead-unit?"
  (it "returns true for unit with hits=0"
    (should (setup/dead-unit? {:hits 0})))

  (it "returns true for unit with negative hits"
    (should (setup/dead-unit? {:hits -1})))

  (it "returns false for unit with hits>0"
    (should-not (setup/dead-unit? {:hits 1})))

  (it "returns false for nil contents"
    (should-not (setup/dead-unit? nil)))

  (it "defaults to hits=1 when missing"
    (should-not (setup/dead-unit? {:type :army}))))

(describe "computer-carrier?"
  (it "returns true for computer carrier"
    (should (setup/computer-carrier? {:type :carrier :owner :computer})))

  (it "returns false for player carrier"
    (should-not (setup/computer-carrier? {:type :carrier :owner :player})))

  (it "returns false for computer non-carrier"
    (should-not (setup/computer-carrier? {:type :destroyer :owner :computer})))

  (it "returns false for nil"
    (should-not (setup/computer-carrier? nil))))

(describe "remove-dead-units"
  (before (reset-all-atoms!))

  (it "removes a unit with hits=0"
    (let [game-map (build-test-map ["A"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :contents :hits] 0)
      (with-redefs [visibility/update-cell-visibility (fn [_ _])]
        (setup/remove-dead-units))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))))

  (it "leaves a unit with hits>0"
    (let [game-map (build-test-map ["A"])]
      (set-test-world! game-map)
      (with-redefs [visibility/update-cell-visibility (fn [_ _])]
        (setup/remove-dead-units))
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))))

  (it "removes a unit with negative hits"
    (let [game-map (build-test-map ["D"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :contents :hits] -1)
      (with-redefs [visibility/update-cell-visibility (fn [_ _])]
        (setup/remove-dead-units))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))))

  (it "handles map with no units"
    (let [game-map (build-test-map ["~#"])]
      (set-test-world! game-map)
      (with-redefs [visibility/update-cell-visibility (fn [_ _])]
        (setup/remove-dead-units))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 0 :contents]))))

  (it "removes dead unit but keeps alive unit on same map"
    (let [game-map (build-test-map ["AD"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :contents :hits] 0)
      (with-redefs [visibility/update-cell-visibility (fn [_ _])]
        (setup/remove-dead-units))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
      (should= :destroyer (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type]))))

  (it "removes dead computer carrier and updates carrier-positions cache"
    (let [game-map (build-test-map ["c"])]
      (set-test-world! game-map)
      (test-utils/set-test-state! :computer-carrier-positions #{[0 0]})
      (update-test-world! assoc-in [0 0 :contents :hits] 0)
      (with-redefs [visibility/update-cell-visibility (fn [_ _])]
        (setup/remove-dead-units))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
      (should= #{} (test-utils/read-test-state :computer-carrier-positions))))

  (it "calls update-cell-visibility with correct pos and owner"
    (let [game-map (build-test-map ["A"])
          vis-calls (atom [])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :contents :hits] 0)
      (with-redefs [visibility/update-cell-visibility
                    (fn [pos owner] (swap! vis-calls conj [pos owner]))]
        (setup/remove-dead-units))
      (should= [[0 0] :player] (first @vis-calls))))

  (it "does not update carrier-positions for dead player carrier"
    (let [game-map (build-test-map ["C"])]
      (set-test-world! game-map)
      (test-utils/set-test-state! :computer-carrier-positions #{[5 5]})
      (update-test-world! assoc-in [0 0 :contents :hits] 0)
      (with-redefs [visibility/update-cell-visibility (fn [_ _])]
        (setup/remove-dead-units))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
      (should= #{[5 5]} (test-utils/read-test-state :computer-carrier-positions))))

  (it "does not update carrier-positions for dead computer non-carrier"
    (let [game-map (build-test-map ["d"])]
      (set-test-world! game-map)
      (test-utils/set-test-state! :computer-carrier-positions #{[5 5]})
      (update-test-world! assoc-in [0 0 :contents :hits] 0)
      (with-redefs [visibility/update-cell-visibility (fn [_ _])]
        (setup/remove-dead-units))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
      (should= #{[5 5]} (test-utils/read-test-state :computer-carrier-positions)))))

(describe "reset-steps-remaining"
  (before (reset-all-atoms!))

  (it "resets steps for player army (speed 1)"
    (let [game-map (build-test-map ["A"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :contents :steps-remaining] 0)
      (setup/reset-steps-remaining)
      (should= 1 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :steps-remaining]))))

  (it "resets steps for player destroyer (speed 2, full health hits=3)"
    (let [game-map (build-test-map ["D"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :contents :steps-remaining] 0)
      (setup/reset-steps-remaining)
      (should= 2 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :steps-remaining]))))

  (it "does NOT reset steps for computer units"
    (let [game-map (build-test-map ["a"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :contents :steps-remaining] 0)
      (setup/reset-steps-remaining)
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :steps-remaining]))))

  (it "scales speed by remaining hits for damaged ship"
    (let [game-map (build-test-map ["D"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :contents :hits] 1)
      (update-test-world! assoc-in [0 0 :contents :steps-remaining] 0)
      (setup/reset-steps-remaining)
      (should= 1 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :steps-remaining])))))

(describe "consume-sentry-fighter-fuel"
  (before (reset-all-atoms!))

  (it "decrements fuel by 1 for sentry fighter"
    (let [game-map (build-test-map ["F"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 20)
      (with-redefs [wake/friendly-city-in-range? (fn [_ _ _] false)]
        (fuel/consume-sentry-fighter-fuel))
      (should= 19 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :fuel]))))

  (it "crashes fighter when fuel reaches 0"
    (let [game-map (build-test-map ["F"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 1)
      (with-redefs [wake/friendly-city-in-range? (fn [_ _ _] false)]
        (fuel/consume-sentry-fighter-fuel))
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :hits]))))

  (it "wakes fighter with :fighter-out-of-fuel when fuel reaches 1"
    (let [game-map (build-test-map ["F"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 2)
      (with-redefs [wake/friendly-city-in-range? (fn [_ _ _] false)]
        (fuel/consume-sentry-fighter-fuel))
      (should= 1 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :fuel]))
      (should= :awake (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode]))
      (should= :fighter-out-of-fuel (get-in (test-utils/read-test-state :game-map) [0 0 :contents :reason]))))

  (it "wakes fighter with :fighter-bingo at bingo threshold when city in range"
    (let [game-map (build-test-map ["F"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 9)
      (with-redefs [wake/friendly-city-in-range? (fn [_ _ _] true)]
        (fuel/consume-sentry-fighter-fuel))
      (should= 8 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :fuel]))
      (should= :awake (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode]))
      (should= :fighter-bingo (get-in (test-utils/read-test-state :game-map) [0 0 :contents :reason]))))

  (it "does not wake at bingo threshold when no city in range"
    (let [game-map (build-test-map ["F"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 9)
      (with-redefs [wake/friendly-city-in-range? (fn [_ _ _] false)]
        (fuel/consume-sentry-fighter-fuel))
      (should= 8 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :fuel]))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode]))))

  (it "does not affect non-sentry fighters"
    (let [game-map (build-test-map ["F"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :fuel 20)
      (with-redefs [wake/friendly-city-in-range? (fn [_ _ _] false)]
        (fuel/consume-sentry-fighter-fuel))
      (should= 20 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :fuel]))))

  (it "does not affect non-fighter sentry units"
    (let [game-map (build-test-map ["D"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "D" :mode :sentry)
      (with-redefs [wake/friendly-city-in-range? (fn [_ _ _] false)]
        (fuel/consume-sentry-fighter-fuel))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode]))))

  (it "processes only sentry fighters on a mixed map"
    (let [game-map (build-test-map ["F~D"
                                    "~A~"
                                    "O~F"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "F" :mode :sentry :fuel 20)
      (update-test-world! update-in [2 2 :contents] assoc :mode :sentry :fuel 10)
      (with-redefs [wake/friendly-city-in-range? (fn [_ _ _] false)]
        (fuel/consume-sentry-fighter-fuel))
      (should= 19 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :fuel]))
      (should= 9 (get-in (test-utils/read-test-state :game-map) [2 2 :contents :fuel]))
      (should= :army (get-in (test-utils/read-test-state :game-map) [1 1 :contents :type])))))

(describe "repair-damaged-ships"
  (before (reset-all-atoms!))

  (it "repairs ship in player city shipyard by 1 hit"
    (let [game-map (build-test-map ["~O~"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [1 0 :shipyard] [{:type :destroyer :hits 1}])
      (repair/repair-damaged-ships)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])]
        (should= [{:type :destroyer :hits 2}] (:shipyard city)))))

  (it "launches fully repaired ship when city cell is empty"
    (let [game-map (build-test-map ["~O~"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [1 0 :shipyard] [{:type :destroyer :hits 2}])
      (repair/repair-damaged-ships)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])
            ship (or (get-in (test-utils/read-test-state :game-map) [0 0 :contents])
                     (get-in (test-utils/read-test-state :game-map) [1 0 :contents])
                     (get-in (test-utils/read-test-state :game-map) [2 0 :contents]))]
        (should= [] (:shipyard city))
        (should= :destroyer (:type ship))
        (should= :player (:owner ship))
        (should= 3 (:hits ship)))))

  (it "repairs ship in computer city"
    (let [game-map (build-test-map ["~X~"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [1 0 :shipyard] [{:type :battleship :hits 5}])
      (repair/repair-damaged-ships)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])]
        (should= [{:type :battleship :hits 6}] (:shipyard city)))))

  (it "does not repair ships in free cities"
    (let [game-map (build-test-map ["~+~"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [1 0 :shipyard] [{:type :destroyer :hits 1}])
      (repair/repair-damaged-ships)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])]
        (should= [{:type :destroyer :hits 1}] (:shipyard city)))))

  (it "launches repaired ship to adjacent sea when city is occupied"
    (let [game-map (build-test-map ["~O~"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [1 0 :shipyard] [{:type :destroyer :hits 2}])
      (update-test-world! assoc-in [1 0 :contents]
                         {:type :submarine :owner :player :hits 2 :mode :sentry})
      (repair/repair-damaged-ships)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])]
        (should= [] (:shipyard city))
        (should= :submarine (:type (:contents city)))
        (let [sea0 (get-in (test-utils/read-test-state :game-map) [0 0])
              sea2 (get-in (test-utils/read-test-state :game-map) [2 0])]
          (should (or (= :destroyer (:type (:contents sea0)))
                      (= :destroyer (:type (:contents sea2))))))))))
