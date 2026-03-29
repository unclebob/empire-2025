(ns empire.player.commands-unit-spec
  (:require [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game-mechanics.movement.coastline :as coastline]
            [empire.game-mechanics.movement.explore :as explore]
            [empire.player.commands :as commands]
            [empire.player.orders :as player-orders]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-unit set-test-world!]]
            [speclj.core :refer :all]))

(defn- setup-unit-attention
  [coords]
  (test-utils/set-test-state! :cells-needing-attention [coords])
  (test-utils/set-test-state! :player-items (list coords)))

(describe "handle-key - unit commands"
  (before (reset-all-atoms!))

  (context "space key for units"
    (it "sets :reason to :skipping-this-round for army"
      (set-test-world! (build-test-map ["A"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
      (setup-unit-attention [0 0])
      (commands/handle-key :space)
      (should= :skipping-this-round (get-in (test-utils/read-test-state :game-map) [0 0 :contents :reason])))

    (it "advances player-items for army"
      (set-test-world! (build-test-map ["A"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
      (setup-unit-attention [0 0])
      (commands/handle-key :space)
      (should (empty? (test-utils/read-test-state :player-items))))

    (it "reduces fuel for fighter with sufficient fuel"
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :fuel 32)
      (setup-unit-attention [0 0])
      (commands/handle-key :space)
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])
            expected-fuel (- 32 (dispatcher/speed :fighter))]
        (should= expected-fuel (:fuel unit))
        (should-contain "Skipping this round" (:reason unit))))

    (it "crashes fighter when fuel reaches zero"
      (set-test-world! (build-test-map ["F"]))
      (let [fuel-cost (dispatcher/speed :fighter)]
        (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :fuel fuel-cost)
        (setup-unit-attention [0 0])
        (commands/handle-key :space)
        (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
          (should= 0 (:hits unit))
          (should= :skipping-this-round (:reason unit)))))

    (it "crashes fighter when fuel would go negative"
      (set-test-world! (build-test-map ["F"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :fuel 1)
      (setup-unit-attention [0 0])
      (commands/handle-key :space)
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :hits]))))

  (context "sentry key"
    (it "sets army to sentry mode on land"
      (set-test-world! (build-test-map ["A"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
      (setup-unit-attention [0 0])
      (commands/handle-key :s)
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode])))

    (it "puts armies to sleep on transport when army-aboard presses sentry"
      (let [sleep-called (atom false)]
        (set-test-world! (build-test-map ["T"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/sleep-armies-on-transport
                      (fn [_] (reset! sleep-called true))]
          (commands/handle-key :s)
          (should @sleep-called))))

    (it "puts fighters to sleep on carrier when carrier-fighter presses sentry"
      (let [sleep-called (atom false)]
        (set-test-world! (build-test-map ["C"]))
        (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :fighter-count 2 :awake-fighters 2)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/sleep-fighters-on-carrier
                      (fn [_] (reset! sleep-called true))]
          (commands/handle-key :s)
          (should @sleep-called))))

    (it "does not set sentry on city"
      (set-test-world! (build-test-map ["O"]))
      (test-utils/update-test-world! assoc-in [0 0 :contents]
                                     {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [0 0])
      (let [result (commands/handle-key :s)]
        (should-be-nil result))))

  (context "unload key"
    (it "wakes armies on transport"
      (set-test-world! (build-test-map ["T"]))
      (set-test-unit (test-utils/game-map-atom) "T" :mode :awake :army-count 3 :awake-armies 0)
      (setup-unit-attention [0 0])
      (player-orders/wake-at [0 0])
      (should= 3 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :awake-armies])))

    (it "wakes fighters on carrier"
      (set-test-world! (build-test-map ["C"]))
      (set-test-unit (test-utils/game-map-atom) "C" :mode :awake :fighter-count 3 :awake-fighters 0)
      (setup-unit-attention [0 0])
      (player-orders/wake-at [0 0])
      (should= 3 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :awake-fighters])))

    (it "returns nil for unit without cargo"
      (set-test-world! (build-test-map ["D"]))
      (set-test-unit (test-utils/game-map-atom) "D" :mode :awake)
      (setup-unit-attention [0 0])
      (should-be-nil (commands/handle-key :u))))

  (context "look-around key"
    (it "sets army to explore mode"
      (let [explore-called (atom false)]
        (set-test-world! (build-test-map ["A"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
        (setup-unit-attention [0 0])
        (with-redefs [explore/set-explore-mode (fn [_] (reset! explore-called true))]
          (commands/handle-key :l)
          (should @explore-called))))

    (it "disembarks army to explore from transport when adjacent land exists"
      (let [disembark-called (atom false)]
        (set-test-world! (build-test-map ["T#"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/disembark-army-to-explore
                      (fn [_ _] (reset! disembark-called true) [1 0])]
          (commands/handle-key :l)
          (should @disembark-called))))

    (it "disembarks army to explore when land is in same row only"
      (let [disembark-called (atom false)]
        (set-test-world! (build-test-map ["~~" "T#" "~~"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 1])
        (with-redefs [container-ops/disembark-army-to-explore
                      (fn [_ _] (reset! disembark-called true) [1 1])]
          (commands/handle-key :l)
          (should @disembark-called))))

    (it "disembarks army to explore when land is in row below only"
      (let [disembark-called (atom false)]
        (set-test-world! (build-test-map ["T" "#"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/disembark-army-to-explore
                      (fn [_ _] (reset! disembark-called true) [0 1])]
          (commands/handle-key :l)
          (should @disembark-called))))

    (it "disembarks army to explore when land is to the left only"
      (let [disembark-called (atom false)]
        (set-test-world! (build-test-map ["#T"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [1 0])
        (with-redefs [container-ops/disembark-army-to-explore
                      (fn [_ _] (reset! disembark-called true) [0 0])]
          (commands/handle-key :l)
          (should @disembark-called))))

    (it "disembarks army to explore when land is in row above only"
      (let [disembark-called (atom false)]
        (set-test-world! (build-test-map ["#" "T"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 1])
        (with-redefs [container-ops/disembark-army-to-explore
                      (fn [_ _] (reset! disembark-called true) [0 0])]
          (commands/handle-key :l)
          (should @disembark-called))))

    (it "sets coastline-follow for transport near coast"
      (let [coastline-called (atom false)]
        (set-test-world! (build-test-map ["#T"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :awake)
        (setup-unit-attention [1 0])
        (with-redefs [coastline/set-coastline-follow-mode
                      (fn [_] (reset! coastline-called true))]
          (commands/handle-key :l)
          (should @coastline-called))))

    (it "shows rejection message for transport not near coast"
      (set-test-world! (build-test-map ["~T~"
                                        "~~~"
                                        "~~~"]))
      (set-test-unit (test-utils/game-map-atom) "T" :mode :awake)
      (setup-unit-attention [1 0])
      (commands/handle-key :l)
      (should-contain "coast" (test-utils/read-test-state :attention-message))))

  (context "fighter fuel edge cases"
    (it "sets fuel-based reason string when fuel remains"
      (let [fuel-cost (dispatcher/speed :fighter)]
        (set-test-world! (build-test-map ["F"]))
        (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :fuel (* 2 fuel-cost))
        (setup-unit-attention [0 0])
        (commands/handle-key :space)
        (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
          (should= fuel-cost (:fuel unit))
          (should-contain "Fuel:" (:reason unit)))))

    (it "does not crash fighter when fuel is exactly one after skip"
      (let [fuel-cost (dispatcher/speed :fighter)]
        (set-test-world! (build-test-map ["F"]))
        (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :fuel (inc fuel-cost))
        (setup-unit-attention [0 0])
        (commands/handle-key :space)
        (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
          (should= 1 (:fuel unit))
          (should-not= 0 (:hits unit))))))

  (context "no attention items"
    (it "returns nil when cells-needing-attention is empty"
      (set-test-world! (build-test-map ["A"]))
      (test-utils/set-test-state! :cells-needing-attention [])
      (should-be-nil (commands/handle-key :w))))

  (context "item-processed clearing"
    (it "clears waiting-for-input and cells-needing-attention"
      (set-test-world! (build-test-map ["A"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
      (setup-unit-attention [0 0])
      (test-utils/set-test-state! :waiting-for-input true)
      (commands/handle-key :space)
      (should= false (test-utils/read-test-state :waiting-for-input))
      (should= [] (test-utils/read-test-state :cells-needing-attention)))))
