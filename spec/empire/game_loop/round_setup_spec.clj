(ns empire.game.loop.round-setup-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game.loop.round-setup :as setup]
            [empire.game.loop.round-setup.fuel :as fuel]
            [empire.game.loop.round-setup.lakes :as lakes]
            [empire.game.loop.round-setup.repair :as repair]
            [empire.game.loop.round-setup.waking :as waking]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game-mechanics.movement.satellite :as satellite]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.game-mechanics.movement.wake-conditions :as wake]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.test.utils :refer [build-test-map reset-all-atoms!
                                       set-test-unit get-test-unit
                                       set-test-world! set-test-computer-map!
                                       update-test-world!]]))

;; --- dead-unit? ---

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

;; --- computer-carrier? ---

(describe "computer-carrier?"
  (it "returns true for computer carrier"
    (should (setup/computer-carrier? {:type :carrier :owner :computer})))

  (it "returns false for player carrier"
    (should-not (setup/computer-carrier? {:type :carrier :owner :player})))

  (it "returns false for computer non-carrier"
    (should-not (setup/computer-carrier? {:type :destroyer :owner :computer})))

  (it "returns false for nil"
    (should-not (setup/computer-carrier? nil))))

;; --- remove-dead-units ---

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

;; --- reset-steps-remaining ---

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
    ;; Destroyer: base speed=2, max hits=3, current hits=1
    ;; effective-speed = (quot (+ (* 2 1) (dec 3)) 3) = (quot 4 3) = 1
    (let [game-map (build-test-map ["D"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :contents :hits] 1)
      (update-test-world! assoc-in [0 0 :contents :steps-remaining] 0)
      (setup/reset-steps-remaining)
      (should= 1 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :steps-remaining])))))

;; --- wake-airport-fighters ---

(describe "wake-airport-fighters"
  (before (reset-all-atoms!))

  (it "wakes fighters in player city airport"
    (let [game-map (build-test-map ["O"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :fighter-count] 3)
      (waking/wake-airport-fighters)
      (should= 3 (get-in (test-utils/read-test-state :game-map) [0 0 :awake-fighters]))))

  (it "does NOT wake fighters in computer city"
    (let [game-map (build-test-map ["X"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :fighter-count] 2)
      (waking/wake-airport-fighters)
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :awake-fighters] 0))))

  (it "does nothing when city has no fighters"
    (let [game-map (build-test-map ["O"])]
      (set-test-world! game-map)
      (waking/wake-airport-fighters)
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :awake-fighters] 0)))))

;; --- wake-carrier-fighters ---

(describe "wake-carrier-fighters"
  (before (reset-all-atoms!))

  (it "wakes fighters on player carrier"
    (let [game-map (build-test-map ["C"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :contents :fighter-count] 4)
      (waking/wake-carrier-fighters)
      (should= 4 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :awake-fighters]))))

  (it "does NOT wake fighters on computer carrier"
    (let [game-map (build-test-map ["c"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :contents :fighter-count] 3)
      (waking/wake-carrier-fighters)
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :awake-fighters] 0))))

  (it "does nothing when carrier has no fighters"
    (let [game-map (build-test-map ["C"])]
      (set-test-world! game-map)
      (waking/wake-carrier-fighters)
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :awake-fighters] 0)))))

;; --- consume-sentry-fighter-fuel ---

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
    ;; bingo threshold = 32/4 = 8, fuel at 9 -> new-fuel = 8 -> <= 8 -> bingo
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

;; --- wake-sentries-seeing-enemy ---

(describe "wake-sentries-seeing-enemy"
  (before (reset-all-atoms!))

  (it "wakes player sentry that sees enemy"
    (let [game-map (build-test-map ["Da"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "D" :mode :sentry)
      (with-redefs [wake/enemy-unit-visible? (fn [_ _ _] true)]
        (waking/wake-sentries-seeing-enemy))
      (should= :awake (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode]))
      (should= :enemy-spotted (get-in (test-utils/read-test-state :game-map) [0 0 :contents :reason]))))

  (it "does NOT wake sentry that sees no enemy"
    (let [game-map (build-test-map ["D~"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "D" :mode :sentry)
      (with-redefs [wake/enemy-unit-visible? (fn [_ _ _] false)]
        (waking/wake-sentries-seeing-enemy))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode]))))

  (it "does NOT wake computer sentries"
    (let [game-map (build-test-map ["dA"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "d" :mode :sentry)
      (with-redefs [wake/enemy-unit-visible? (fn [_ _ _] true)]
        (waking/wake-sentries-seeing-enemy))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode])))))

;; --- move-satellites ---

(describe "move-satellites"
  (before (reset-all-atoms!))

  (it "moves satellite with turns-remaining"
    (let [game-map (build-test-map ["V###########"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 10 :target [0 11])
      (let [move-count (atom 0)]
        (with-redefs [satellite/move-satellite
                      (fn [coords]
                        (swap! move-count inc)
                        (let [[r c] coords
                              cell (get-in (test-utils/read-test-state :game-map) coords)
                              sat (:contents cell)
                              new-c (inc c)
                              new-coords [r new-c]]
                          (update-test-world! assoc-in [r c :contents] nil)
                          (update-test-world! assoc-in (conj new-coords :contents) sat)
                          new-coords))
                      visibility/update-cell-visibility (fn [_ _])]
          (setup/move-satellites)
          ;; satellite speed is 10, so move-satellite should be called 10 times
          (should= 10 @move-count)))))

  (it "removes expired satellite with turns-remaining=0"
    (let [game-map (build-test-map ["V"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 0)
      (with-redefs [visibility/update-cell-visibility (fn [_ _])]
        (setup/move-satellites))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))))

  (it "decrements turns-remaining after all steps"
    (let [game-map (build-test-map ["V###########"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 5 :target [0 11])
      (with-redefs [satellite/move-satellite
                    (fn [coords]
                      (let [[r c] coords
                            cell (get-in (test-utils/read-test-state :game-map) coords)
                            sat (:contents cell)
                            new-c (inc c)
                            new-coords [r new-c]]
                        (update-test-world! assoc-in [r c :contents] nil)
                        (update-test-world! assoc-in (conj new-coords :contents) sat)
                        new-coords))
                    visibility/update-cell-visibility (fn [_ _])]
        (setup/move-satellites)
        ;; After 10 steps, turns-remaining should be decremented from 5 to 4
        (let [sat-pos [0 10]  ;; moved 10 steps right
              sat (get-in (test-utils/read-test-state :game-map) (conj sat-pos :contents))]
          (should= 4 (:turns-remaining sat))))))

  (it "removes satellite when turns-remaining reaches 0 after steps"
    (let [game-map (build-test-map ["V###########"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 1 :target [0 11])
      (with-redefs [satellite/move-satellite
                    (fn [coords]
                      (let [[r c] coords
                            cell (get-in (test-utils/read-test-state :game-map) coords)
                            sat (:contents cell)
                            new-c (inc c)
                            new-coords [r new-c]]
                        (update-test-world! assoc-in [r c :contents] nil)
                        (update-test-world! assoc-in (conj new-coords :contents) sat)
                        new-coords))
                    visibility/update-cell-visibility (fn [_ _])]
        (setup/move-satellites)
        ;; After all steps, turns-remaining was 1, dec to 0, satellite removed
        (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 10 :contents])))))

  (it "does not move satellite with turns-remaining=0"
    (let [game-map (build-test-map ["V##"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 0)
      (let [move-count (atom 0)]
        (with-redefs [satellite/move-satellite
                      (fn [coords] (swap! move-count inc) coords)
                      visibility/update-cell-visibility (fn [_ _])]
          (setup/move-satellites)
          (should= 0 @move-count)))))

  (it "satellite with turns-remaining=1 still moves before expiring"
    (let [game-map (build-test-map ["V###########"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 1 :target [0 11])
      (let [move-count (atom 0)]
        (with-redefs [satellite/move-satellite
                      (fn [coords]
                        (swap! move-count inc)
                        (let [[r c] coords
                              cell (get-in (test-utils/read-test-state :game-map) coords)
                              sat (:contents cell)
                              new-coords [r (inc c)]]
                          (update-test-world! assoc-in [r c :contents] nil)
                          (update-test-world! assoc-in (conj new-coords :contents) sat)
                          new-coords))
                      visibility/update-cell-visibility (fn [_ _])]
          (setup/move-satellites)
          (should= 10 @move-count)))))

  (it "satellite with turns-remaining=2 survives with 1 turn left"
    (let [game-map (build-test-map ["V###########"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "V" :turns-remaining 2 :target [0 11])
      (with-redefs [satellite/move-satellite
                    (fn [coords]
                      (let [[r c] coords
                            cell (get-in (test-utils/read-test-state :game-map) coords)
                            sat (:contents cell)
                            new-coords [r (inc c)]]
                        (update-test-world! assoc-in [r c :contents] nil)
                        (update-test-world! assoc-in (conj new-coords :contents) sat)
                        new-coords))
                    visibility/update-cell-visibility (fn [_ _])]
        (setup/move-satellites)
        (let [sat (get-in (test-utils/read-test-state :game-map) [0 10 :contents])]
          (should-not-be-nil sat)
          (should= 1 (:turns-remaining sat)))))))

;; --- repair-damaged-ships ---

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
      ;; Destroyer at 2/3 hits, will be repaired to 3 (full)
      (update-test-world! assoc-in [1 0 :shipyard] [{:type :destroyer :hits 2}])
      (repair/repair-damaged-ships)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])
            ship (:contents city)]
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
        ;; Fully repaired and launched to adjacent sea
        (should= [] (:shipyard city))
        (should= :submarine (:type (:contents city)))
        (let [sea0 (get-in (test-utils/read-test-state :game-map) [0 0])
              sea2 (get-in (test-utils/read-test-state :game-map) [2 0])]
          (should (or (= :destroyer (:type (:contents sea0)))
                      (= :destroyer (:type (:contents sea2))))))))))

;; --- evacuate-lake-patrol-boats ---

(describe "evacuate-lake-patrol-boats"
  (before (reset-all-atoms!))

  (it "moves patrol boat from computer lake-shore city to adjacent sea"
    (let [world (build-test-map ["~~~"
                                 "~X~"
                                 "~~~"])
          computer-map (build-test-map ["~~~"
                                        "~X~"
                                        "~~~"])]
      (set-test-world! world)
      (set-test-computer-map! computer-map)
      (test-utils/set-test-state! :lake-max-cells 10)
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :patrol-boat :owner :computer :hits 1 :mode :awake})
      (lakes/evacuate-lake-patrol-boats)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents]))
      (let [sea-neighbors (for [pos [[0 0] [0 1] [0 2] [1 0] [1 2] [2 0] [2 1] [2 2]]
                                :let [u (get-in (test-utils/read-test-state :game-map) (conj pos :contents))]
                                :when (= :patrol-boat (:type u))]
                            pos)]
        (should= 1 (count sea-neighbors)))))

  (it "leaves patrol boat in city when no adjacent sea is empty"
    (let [world (build-test-map ["~~~"
                                 "~X~"
                                 "~~~"])
          computer-map (build-test-map ["~~~"
                                        "~X~"
                                        "~~~"])]
      (set-test-world! world)
      (set-test-computer-map! computer-map)
      (test-utils/set-test-state! :lake-max-cells 10)
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :patrol-boat :owner :computer :hits 1 :mode :awake})
      (doseq [pos [[0 0] [0 1] [0 2] [1 0] [1 2] [2 0] [2 1] [2 2]]]
        (update-test-world! assoc-in (conj pos :contents)
                           {:type :transport :owner :computer :hits 1 :mode :awake}))
      (lakes/evacuate-lake-patrol-boats)
      (should= :patrol-boat (get-in (test-utils/read-test-state :game-map) [1 1 :contents :type]))
      (should= :computer (get-in (test-utils/read-test-state :game-map) [1 1 :contents :owner]))))

  (it "moves transport out of computer lake-shore city and preserves cargo state"
    (let [world (build-test-map ["~~~"
                                 "~X~"
                                 "~~~"])
          computer-map (build-test-map ["~~~"
                                        "~X~"
                                        "~~~"])]
      (set-test-world! world)
      (set-test-computer-map! computer-map)
      (test-utils/set-test-state! :lake-max-cells 10)
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :transport
                          :owner :computer
                          :hits 1
                          :mode :awake
                          :army-count 3
                          :transport-mission :invading})
      (lakes/evacuate-lake-patrol-boats)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents]))
      (let [moved (first (for [pos [[0 0] [0 1] [0 2] [1 0] [1 2] [2 0] [2 1] [2 2]]
                               :let [u (get-in (test-utils/read-test-state :game-map) (conj pos :contents))]
                               :when (= :transport (:type u))]
                           u))]
        (should-not-be-nil moved)
        (should= 3 (:army-count moved))
        (should= :land-locked (:transport-mission moved))
        (should= true (:never-reload? moved)))))

  (it "prefers ocean sea over lake sea when both are adjacent"
    (let [world [[{:type :sea}
                  {:type :city :city-status :computer
                   :contents {:type :patrol-boat :owner :computer :hits 1}}
                  {:type :sea}]]
          computer-map world]
      (set-test-world! world)
      (set-test-computer-map! computer-map)
      (with-redefs [empire.game-mechanics.movement.lakes/lake-cells (fn [_ _] #{[0 0]})
                    visibility/update-cell-visibility (fn [_ _] nil)]
        (lakes/evacuate-lake-patrol-boats))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 1 :contents]))
      (should= :patrol-boat (get-in (test-utils/read-test-state :game-map) [0 2 :contents :type])))))

(describe "lake discovery army retask"
  (before (reset-all-atoms!))

  (it "wakes and retasks all computer armies on lake-adjacent landmass"
    (let [world (build-test-map ["~~~~~"
                                 "#####"
                                 "##~##"
                                 "#####"
                                 "#####"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-test-state! :lake-max-cells 2)
      (test-utils/set-test-state! :known-lake-cells #{})
      (update-test-world! assoc-in [0 4 :country-id] 1)
      (update-test-world! assoc-in [4 4 :country-id] 1)
      (update-test-world! assoc-in [0 4 :contents]
                         {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1})
      (update-test-world! assoc-in [4 4 :contents]
                         {:type :army :owner :computer :hits 1 :mode :awake :country-id 1})
      (lakes/mark-lake-locked-ships)
      (should= :move-to-coast-for-invasion (get-in (test-utils/read-test-state :game-map) [0 4 :contents :mode]))
      (should= :move-to-coast-for-invasion (get-in (test-utils/read-test-state :game-map) [4 4 :contents :mode]))
      (should (vector? (get-in (test-utils/read-test-state :game-map) [0 4 :contents :coast-target])))
      (should (vector? (get-in (test-utils/read-test-state :game-map) [4 4 :contents :coast-target])))
      (should= #{[2 2]} (test-utils/read-test-state :known-lake-cells))))

  (it "does not retask again when no newly discovered lake cells"
    (let [world (build-test-map ["###"
                                 "#~#"
                                 "###"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-test-state! :lake-max-cells 10)
      (test-utils/set-test-state! :known-lake-cells #{[1 1]})
      (update-test-world! assoc-in [0 0 :country-id] 1)
      (update-test-world! assoc-in [0 0 :contents]
                         {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1})
      (lakes/mark-lake-locked-ships)
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode]))))

  (it "removes :lake-locked? from ships no longer in a lake"
    (let [world (build-test-map ["d~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-test-state! :lake-max-cells 1)
      (test-utils/set-test-state! :known-lake-cells #{})
      (update-test-world! assoc-in [0 0 :contents :lake-locked?] true)
      (lakes/mark-lake-locked-ships)
      (should= false (contains? (get-in (test-utils/read-test-state :game-map) [0 0 :contents]) :lake-locked?))))

  (it "marks empty transport in lake as never-reload without forcing mission"
    (let [world (build-test-map ["~t~"])]
      (set-test-world! world)
      (set-test-computer-map! world)
      (test-utils/set-test-state! :lake-max-cells 10)
      (test-utils/set-test-state! :known-lake-cells #{})
      (update-test-world! assoc-in [1 0 :contents :army-count] 0)
      (lakes/mark-lake-locked-ships)
      (should= true (get-in (test-utils/read-test-state :game-map) [1 0 :contents :lake-locked?]))
      (should= true (get-in (test-utils/read-test-state :game-map) [1 0 :contents :never-reload?]))
      (should-not= :land-locked (get-in (test-utils/read-test-state :game-map) [1 0 :contents :transport-mission])))))

(run-specs)
