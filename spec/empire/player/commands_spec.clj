(ns empire.player.commands-spec
  (:require [speclj.core :refer :all]
            [empire.player.commands :as commands]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.combat :as combat]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.game-loop :as game-loop]
            [empire.movement.coastline :as coastline]
            [empire.movement.explore :as explore]
            [empire.movement.movement :as movement]
            [empire.player.production :as production]
            [empire.units.dispatcher :as dispatcher]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-unit]]))

;; Helpers

(defn- setup-unit-attention
  "Sets up game-map, cells-needing-attention, and player-items for a unit at coords."
  [coords]
  (reset! atoms/cells-needing-attention [coords])
  (reset! atoms/player-items (list coords)))

;; ========== handle-key: city production ==========

(describe "handle-key - city production"
  (before (reset-all-atoms!))

  (context "basic production keys"
    (it "sets army production on player city when :a pressed"
      (reset! atoms/game-map (build-test-map ["O"]))
      (setup-unit-attention [0 0])
      (commands/handle-key :a)
      (should= :army (:item (get @atoms/production [0 0]))))

    (it "sets fighter production on player city when :f pressed"
      (reset! atoms/game-map (build-test-map ["O"]))
      (setup-unit-attention [0 0])
      (commands/handle-key :f)
      (should= :fighter (:item (get @atoms/production [0 0]))))

    (it "sets production to :none when :x pressed on player city"
      (reset! atoms/game-map (build-test-map ["O"]))
      (setup-unit-attention [0 0])
      (commands/handle-key :x)
      (should= :none (get @atoms/production [0 0])))

    (it "advances player-items when :space pressed on player city"
      (reset! atoms/game-map (build-test-map ["O"]))
      (setup-unit-attention [0 0])
      (commands/handle-key :space)
      (should (empty? @atoms/player-items))))

  (context "coastal city restrictions"
    (it "shows error for naval production on non-coastal city"
      (reset! atoms/game-map (build-test-map ["###"
                                               "#O#"
                                               "###"]))
      (setup-unit-attention [1 1])
      (commands/handle-key :t)
      (should-not (get @atoms/production [1 1]))
      (should-contain "coastal" @atoms/error-message))

    (it "allows naval production on coastal city"
      (reset! atoms/game-map (build-test-map ["~##"
                                               "#O#"
                                               "###"]))
      (setup-unit-attention [1 1])
      (commands/handle-key :t)
      (should= :transport (:item (get @atoms/production [1 1])))))

  (context "city with active unit"
    (it "does not handle production key when city has active unit"
      (reset! atoms/game-map (build-test-map ["O"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [0 0])
      ;; With an active unit present, :a becomes a movement key, not production
      ;; The unit movement handler will handle it (no direction for :a as direction IS defined)
      ;; Actually :a IS a direction key (west). So it tries to move west from [0 0].
      ;; On a 1x1 map there's no cell to move to, so handle-key returns nil.
      (let [result (commands/handle-key :a)]
        ;; No production should be set
        (should-not (get @atoms/production [0 0])))))

  (context "production variants"
    (it "sets destroyer production"
      (reset! atoms/game-map (build-test-map ["~O"]))
      (setup-unit-attention [1 0])
      (commands/handle-key :d)
      (should= :destroyer (:item (get @atoms/production [1 0]))))

    (it "sets submarine production"
      (reset! atoms/game-map (build-test-map ["~O"]))
      (setup-unit-attention [1 0])
      (commands/handle-key :s)
      (should= :submarine (:item (get @atoms/production [1 0]))))

    (it "sets carrier production"
      (reset! atoms/game-map (build-test-map ["~O"]))
      (setup-unit-attention [1 0])
      (commands/handle-key :c)
      (should= :carrier (:item (get @atoms/production [1 0]))))

    (it "sets battleship production"
      (reset! atoms/game-map (build-test-map ["~O"]))
      (setup-unit-attention [1 0])
      (commands/handle-key :b)
      (should= :battleship (:item (get @atoms/production [1 0]))))

    (it "sets patrol-boat production"
      (reset! atoms/game-map (build-test-map ["~O"]))
      (setup-unit-attention [1 0])
      (commands/handle-key :p)
      (should= :patrol-boat (:item (get @atoms/production [1 0]))))

    (it "sets satellite production"
      (reset! atoms/game-map (build-test-map ["O"]))
      (setup-unit-attention [0 0])
      (commands/handle-key :z)
      (should= :satellite (:item (get @atoms/production [0 0]))))))

;; ========== handle-key: unit commands ==========

(describe "handle-key - unit commands"
  (before (reset-all-atoms!))

  (context "space key for units"
    (it "sets :reason to :skipping-this-round for army"
      (reset! atoms/game-map (build-test-map ["A"]))
      (set-test-unit atoms/game-map "A" :mode :awake)
      (setup-unit-attention [0 0])
      (commands/handle-key :space)
      (should= :skipping-this-round (get-in @atoms/game-map [0 0 :contents :reason])))

    (it "advances player-items for army"
      (reset! atoms/game-map (build-test-map ["A"]))
      (set-test-unit atoms/game-map "A" :mode :awake)
      (setup-unit-attention [0 0])
      (commands/handle-key :space)
      (should (empty? @atoms/player-items)))

    (it "reduces fuel for fighter with sufficient fuel"
      (reset! atoms/game-map (build-test-map ["F"]))
      (set-test-unit atoms/game-map "F" :mode :awake :fuel 32)
      (setup-unit-attention [0 0])
      (commands/handle-key :space)
      (let [unit (get-in @atoms/game-map [0 0 :contents])
            expected-fuel (- 32 (dispatcher/speed :fighter))]
        (should= expected-fuel (:fuel unit))
        (should-contain "Skipping this round" (:reason unit))))

    (it "crashes fighter when fuel reaches zero"
      (reset! atoms/game-map (build-test-map ["F"]))
      (let [fuel-cost (dispatcher/speed :fighter)]
        (set-test-unit atoms/game-map "F" :mode :awake :fuel fuel-cost)
        (setup-unit-attention [0 0])
        (commands/handle-key :space)
        (let [unit (get-in @atoms/game-map [0 0 :contents])]
          (should= 0 (:hits unit))
          (should= :skipping-this-round (:reason unit)))))

    (it "crashes fighter when fuel would go negative"
      (reset! atoms/game-map (build-test-map ["F"]))
      (set-test-unit atoms/game-map "F" :mode :awake :fuel 1)
      (setup-unit-attention [0 0])
      (commands/handle-key :space)
      (should= 0 (get-in @atoms/game-map [0 0 :contents :hits]))))

  (context "sentry key"
    (it "sets army to sentry mode on land"
      (reset! atoms/game-map (build-test-map ["A"]))
      (set-test-unit atoms/game-map "A" :mode :awake)
      (setup-unit-attention [0 0])
      (commands/handle-key :s)
      (should= :sentry (get-in @atoms/game-map [0 0 :contents :mode])))

    (it "puts armies to sleep on transport when army-aboard presses sentry"
      (let [sleep-called (atom false)]
        (reset! atoms/game-map (build-test-map ["T"]))
        (set-test-unit atoms/game-map "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/sleep-armies-on-transport
                      (fn [_] (reset! sleep-called true))]
          (commands/handle-key :s)
          (should @sleep-called))))

    (it "puts fighters to sleep on carrier when carrier-fighter presses sentry"
      (let [sleep-called (atom false)]
        (reset! atoms/game-map (build-test-map ["C"]))
        (set-test-unit atoms/game-map "C" :mode :sentry :fighter-count 2 :awake-fighters 2)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/sleep-fighters-on-carrier
                      (fn [_] (reset! sleep-called true))]
          (commands/handle-key :s)
          (should @sleep-called))))

    (it "does not set sentry on city"
      (reset! atoms/game-map (build-test-map ["O"]))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [0 0])
      (let [result (commands/handle-key :s)]
        ;; Should return nil -- sentry not allowed on city
        (should-be-nil result))))

  (context "unload key"
    (it "wakes armies on transport"
      (let [wake-called (atom false)]
        (reset! atoms/game-map (build-test-map ["T"]))
        (set-test-unit atoms/game-map "T" :mode :awake :army-count 3 :awake-armies 0)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/wake-armies-on-transport
                      (fn [_] (reset! wake-called true))]
          (commands/handle-key :u)
          (should @wake-called))))

    (it "wakes fighters on carrier"
      (let [wake-called (atom false)]
        (reset! atoms/game-map (build-test-map ["C"]))
        (set-test-unit atoms/game-map "C" :mode :awake :fighter-count 3 :awake-fighters 0)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/wake-fighters-on-carrier
                      (fn [_] (reset! wake-called true))]
          (commands/handle-key :u)
          (should @wake-called))))

    (it "returns nil for unit without cargo"
      (reset! atoms/game-map (build-test-map ["D"]))
      (set-test-unit atoms/game-map "D" :mode :awake)
      (setup-unit-attention [0 0])
      (should-be-nil (commands/handle-key :u))))

  (context "look-around key"
    (it "sets army to explore mode"
      (let [explore-called (atom false)]
        (reset! atoms/game-map (build-test-map ["A"]))
        (set-test-unit atoms/game-map "A" :mode :awake)
        (setup-unit-attention [0 0])
        (with-redefs [explore/set-explore-mode (fn [_] (reset! explore-called true))]
          (commands/handle-key :l)
          (should @explore-called))))

    (it "disembarks army to explore from transport when adjacent land exists"
      (let [disembark-called (atom false)]
        ;; Transport at [0 0] (sea), land at [1 0]
        (reset! atoms/game-map (build-test-map ["T#"]))
        (set-test-unit atoms/game-map "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/disembark-army-to-explore
                      (fn [_ _] (reset! disembark-called true) [1 0])]
          (commands/handle-key :l)
          (should @disembark-called))))

    (it "disembarks army to explore when land is in same row only"
      (let [disembark-called (atom false)]
        ;; Transport at [0 1], land only at [1 1] (same row after transpose)
        (reset! atoms/game-map (build-test-map ["~~" "T#" "~~"]))
        (set-test-unit atoms/game-map "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 1])
        (with-redefs [container-ops/disembark-army-to-explore
                      (fn [_ _] (reset! disembark-called true) [1 1])]
          (commands/handle-key :l)
          (should @disembark-called))))

    (it "disembarks army to explore when land is in row below only"
      (let [disembark-called (atom false)]
        ;; Transport at [0 0], land only at [0 1] (row below)
        (reset! atoms/game-map (build-test-map ["T" "#"]))
        (set-test-unit atoms/game-map "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/disembark-army-to-explore
                      (fn [_ _] (reset! disembark-called true) [0 1])]
          (commands/handle-key :l)
          (should @disembark-called))))

    (it "disembarks army to explore when land is to the left only"
      (let [disembark-called (atom false)]
        ;; Transport at [1 0], land only at [0 0] (column to the left)
        (reset! atoms/game-map (build-test-map ["#T"]))
        (set-test-unit atoms/game-map "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [1 0])
        (with-redefs [container-ops/disembark-army-to-explore
                      (fn [_ _] (reset! disembark-called true) [0 0])]
          (commands/handle-key :l)
          (should @disembark-called))))

    (it "disembarks army to explore when land is in row above only"
      (let [disembark-called (atom false)]
        ;; Transport at [0 1], land only at [0 0] (row above)
        (reset! atoms/game-map (build-test-map ["#" "T"]))
        (set-test-unit atoms/game-map "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 1])
        (with-redefs [container-ops/disembark-army-to-explore
                      (fn [_ _] (reset! disembark-called true) [0 0])]
          (commands/handle-key :l)
          (should @disembark-called))))

    (it "sets coastline-follow for transport near coast"
      (let [coastline-called (atom false)]
        ;; Transport at [1 0] (sea), land at [0 0]
        (reset! atoms/game-map (build-test-map ["#T"]))
        (set-test-unit atoms/game-map "T" :mode :awake)
        (setup-unit-attention [1 0])
        (with-redefs [coastline/set-coastline-follow-mode
                      (fn [_] (reset! coastline-called true))]
          (commands/handle-key :l)
          (should @coastline-called))))

    (it "shows rejection message for transport not near coast"
      (reset! atoms/game-map (build-test-map ["~T~"
                                               "~~~"
                                               "~~~"]))
      (set-test-unit atoms/game-map "T" :mode :awake)
      (setup-unit-attention [1 0])
      (commands/handle-key :l)
      (should-contain "coast" @atoms/attention-message)))

  (context "fighter fuel edge cases"
    (it "sets fuel-based reason string when fuel remains"
      (let [fuel-cost (dispatcher/speed :fighter)]
        (reset! atoms/game-map (build-test-map ["F"]))
        (set-test-unit atoms/game-map "F" :mode :awake :fuel (* 2 fuel-cost))
        (setup-unit-attention [0 0])
        (commands/handle-key :space)
        (let [unit (get-in @atoms/game-map [0 0 :contents])]
          (should= fuel-cost (:fuel unit))
          (should-contain "Fuel:" (:reason unit)))))

    (it "does not crash fighter when fuel is exactly one after skip"
      (let [fuel-cost (dispatcher/speed :fighter)]
        (reset! atoms/game-map (build-test-map ["F"]))
        (set-test-unit atoms/game-map "F" :mode :awake :fuel (inc fuel-cost))
        (setup-unit-attention [0 0])
        (commands/handle-key :space)
        (let [unit (get-in @atoms/game-map [0 0 :contents])]
          (should= 1 (:fuel unit))
          (should-not= 0 (:hits unit))))))

  (context "no attention items"
    (it "returns nil when cells-needing-attention is empty"
      (reset! atoms/game-map (build-test-map ["A"]))
      (reset! atoms/cells-needing-attention [])
      (should-be-nil (commands/handle-key :w))))

  (context "item-processed clearing"
    (it "clears waiting-for-input and cells-needing-attention"
      (reset! atoms/game-map (build-test-map ["A"]))
      (set-test-unit atoms/game-map "A" :mode :awake)
      (setup-unit-attention [0 0])
      (reset! atoms/waiting-for-input true)
      (commands/handle-key :space)
      (should= false @atoms/waiting-for-input)
      (should= [] @atoms/cells-needing-attention))))

(run-specs)
