(ns empire.player.commands-spec
  (:require [empire.test-utils :as test-utils]
            [speclj.core :refer :all]
            [empire.player.commands :as commands]
            [empire.config :as config]
            [empire.combat :as combat]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.game-loop.core :as game-loop]
            [empire.movement.coastline :as coastline]
            [empire.movement.explore :as explore]
            [empire.movement.api :as movement]
            [empire.player.production :as production]
            [empire.units.dispatcher :as dispatcher]
            [empire.test-utils :refer [build-test-map set-test-world! update-test-world! reset-all-atoms! set-test-unit]]))

;; Helpers

(defn- setup-unit-attention
  "Sets up game-map, cells-needing-attention, and player-items for a unit at coords."
  [coords]
  (test-utils/set-test-state! :cells-needing-attention [coords])
  (test-utils/set-test-state! :player-items (list coords)))

;; ========== handle-key: city production ==========

(describe "handle-key - city production"
  (before (reset-all-atoms!))

  (context "basic production keys"
    (it "sets army production on player city when :a pressed"
      (set-test-world! (build-test-map ["O"]))
      (setup-unit-attention [0 0])
      (commands/handle-key :a)
      (should= :army (:item (get (test-utils/read-test-state :production) [0 0]))))

    (it "sets fighter production on player city when :f pressed"
      (set-test-world! (build-test-map ["O"]))
      (setup-unit-attention [0 0])
      (commands/handle-key :f)
      (should= :fighter (:item (get (test-utils/read-test-state :production) [0 0]))))

    (it "sets production to :none when :x pressed on player city"
      (set-test-world! (build-test-map ["O"]))
      (setup-unit-attention [0 0])
      (commands/handle-key :x)
      (should= :none (get (test-utils/read-test-state :production) [0 0])))

    (it "advances player-items when :space pressed on player city"
      (set-test-world! (build-test-map ["O"]))
      (setup-unit-attention [0 0])
      (commands/handle-key :space)
      (should (empty? (test-utils/read-test-state :player-items)))))

  (context "coastal city restrictions"
    (it "shows error for naval production on non-coastal city"
      (set-test-world! (build-test-map ["###"
                                               "#O#"
                                               "###"]))
      (setup-unit-attention [1 1])
      (commands/handle-key :t)
      (should-not (get (test-utils/read-test-state :production) [1 1]))
      (should-contain "coastal" (test-utils/read-test-state :error-message)))

    (it "allows naval production on coastal city"
      (set-test-world! (build-test-map ["~##"
                                               "#O#"
                                               "###"]))
      (setup-unit-attention [1 1])
      (commands/handle-key :t)
      (should= :transport (:item (get (test-utils/read-test-state :production) [1 1])))))

  (context "city with active unit"
    (it "does not handle production key when city has active unit"
      (set-test-world! (build-test-map ["O"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [0 0])
      ;; With an active unit present, :a becomes a movement key, not production
      ;; The unit movement handler will handle it (no direction for :a as direction IS defined)
      ;; Actually :a IS a direction key (west). So it tries to move west from [0 0].
      ;; On a 1x1 map there's no cell to move to, so handle-key returns nil.
      (let [result (commands/handle-key :a)]
        ;; No production should be set
        (should-not (get (test-utils/read-test-state :production) [0 0])))))

  (context "production variants"
    (it "sets destroyer production"
      (set-test-world! (build-test-map ["~O"]))
      (setup-unit-attention [1 0])
      (commands/handle-key :d)
      (should= :destroyer (:item (get (test-utils/read-test-state :production) [1 0]))))

    (it "sets submarine production"
      (set-test-world! (build-test-map ["~O"]))
      (setup-unit-attention [1 0])
      (commands/handle-key :s)
      (should= :submarine (:item (get (test-utils/read-test-state :production) [1 0]))))

    (it "sets carrier production"
      (set-test-world! (build-test-map ["~O"]))
      (setup-unit-attention [1 0])
      (commands/handle-key :c)
      (should= :carrier (:item (get (test-utils/read-test-state :production) [1 0]))))

    (it "sets battleship production"
      (set-test-world! (build-test-map ["~O"]))
      (setup-unit-attention [1 0])
      (commands/handle-key :b)
      (should= :battleship (:item (get (test-utils/read-test-state :production) [1 0]))))

    (it "sets patrol-boat production"
      (set-test-world! (build-test-map ["~O"]))
      (setup-unit-attention [1 0])
      (commands/handle-key :p)
      (should= :patrol-boat (:item (get (test-utils/read-test-state :production) [1 0]))))

    (it "sets satellite production"
      (set-test-world! (build-test-map ["O"]))
      (setup-unit-attention [0 0])
      (commands/handle-key :z)
      (should= :satellite (:item (get (test-utils/read-test-state :production) [0 0]))))))

;; ========== handle-key: unit commands ==========

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
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [0 0])
      (let [result (commands/handle-key :s)]
        ;; Should return nil -- sentry not allowed on city
        (should-be-nil result))))

  (context "unload key"
    (it "wakes armies on transport"
      (let [wake-called (atom false)]
        (set-test-world! (build-test-map ["T"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :awake :army-count 3 :awake-armies 0)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/wake-armies-on-transport
                      (fn [_] (reset! wake-called true))]
          (commands/handle-key :u)
          (should @wake-called))))

    (it "wakes fighters on carrier"
      (let [wake-called (atom false)]
        (set-test-world! (build-test-map ["C"]))
        (set-test-unit (test-utils/game-map-atom) "C" :mode :awake :fighter-count 3 :awake-fighters 0)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/wake-fighters-on-carrier
                      (fn [_] (reset! wake-called true))]
          (commands/handle-key :u)
          (should @wake-called))))

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
        ;; Transport at [0 0] (sea), land at [1 0]
        (set-test-world! (build-test-map ["T#"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/disembark-army-to-explore
                      (fn [_ _] (reset! disembark-called true) [1 0])]
          (commands/handle-key :l)
          (should @disembark-called))))

    (it "disembarks army to explore when land is in same row only"
      (let [disembark-called (atom false)]
        ;; Transport at [0 1], land only at [1 1] (same row after transpose)
        (set-test-world! (build-test-map ["~~" "T#" "~~"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 1])
        (with-redefs [container-ops/disembark-army-to-explore
                      (fn [_ _] (reset! disembark-called true) [1 1])]
          (commands/handle-key :l)
          (should @disembark-called))))

    (it "disembarks army to explore when land is in row below only"
      (let [disembark-called (atom false)]
        ;; Transport at [0 0], land only at [0 1] (row below)
        (set-test-world! (build-test-map ["T" "#"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/disembark-army-to-explore
                      (fn [_ _] (reset! disembark-called true) [0 1])]
          (commands/handle-key :l)
          (should @disembark-called))))

    (it "disembarks army to explore when land is to the left only"
      (let [disembark-called (atom false)]
        ;; Transport at [1 0], land only at [0 0] (column to the left)
        (set-test-world! (build-test-map ["#T"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [1 0])
        (with-redefs [container-ops/disembark-army-to-explore
                      (fn [_ _] (reset! disembark-called true) [0 0])]
          (commands/handle-key :l)
          (should @disembark-called))))

    (it "disembarks army to explore when land is in row above only"
      (let [disembark-called (atom false)]
        ;; Transport at [0 1], land only at [0 0] (row above)
        (set-test-world! (build-test-map ["#" "T"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 1])
        (with-redefs [container-ops/disembark-army-to-explore
                      (fn [_ _] (reset! disembark-called true) [0 0])]
          (commands/handle-key :l)
          (should @disembark-called))))

    (it "sets coastline-follow for transport near coast"
      (let [coastline-called (atom false)]
        ;; Transport at [1 0] (sea), land at [0 0]
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

;; ========== Mutation tests: movement keys ==========

(describe "handle-key - movement (mutation tests)"
  (before (reset-all-atoms!))

  (context "extended NW target calculation (L48-L50)"
    (it "reaches [0,0] from center of 5x5 map"
      (set-test-world! (build-test-map ["#####" "#####" "#####" "#####" "#####"]))
      (update-test-world! assoc-in [2 2 :contents]
             {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [2 2])
      (let [target (atom nil)]
        (with-redefs [movement/set-unit-movement (fn [_ to] (reset! target to))
                      game-loop/item-processed (fn [])]
          (commands/handle-key :Q)
          (should= [0 0] @target)))))

  (context "extended SE target at column edge (L50)"
    (it "reaches [4,4] from [0,0] on 5x5 map"
      (set-test-world! (build-test-map ["#####" "#####" "#####" "#####" "#####"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [0 0])
      (let [target (atom nil)]
        (with-redefs [movement/set-unit-movement (fn [_ to] (reset! target to))
                      game-loop/item-processed (fn [])]
          (commands/handle-key :C)
          (should= [4 4] @target)))))

  (context "extended south target at row edge (L50)"
    (it "reaches [0,4] from [0,0] on 5x5 map"
      (set-test-world! (build-test-map ["#####" "#####" "#####" "#####" "#####"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [0 0])
      (let [target (atom nil)]
        (with-redefs [movement/set-unit-movement (fn [_ to] (reset! target to))
                      game-loop/item-processed (fn [])]
          (commands/handle-key :X)
          (should= [0 4] @target)))))

  (context "standard diagonal movement (L105, L107, L110)"
    (it "moves army NE to correct adjacent cell"
      (set-test-world! (build-test-map ["###" "###" "###"]))
      (update-test-world! assoc-in [1 1 :contents]
             {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [1 1])
      (let [target (atom nil)]
        (with-redefs [movement/set-unit-movement (fn [_ to] (reset! target to))
                      game-loop/item-processed (fn [])]
          (commands/handle-key :e)
          (should= [2 0] @target)))))

  (context "extended east uses calculated target (L112)"
    (it "reaches far east edge"
      (set-test-world! (build-test-map ["#####"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :mode :awake :owner :player :hits 1})
      (setup-unit-attention [0 0])
      (let [target (atom nil)]
        (with-redefs [movement/set-unit-movement (fn [_ to] (reset! target to))
                      game-loop/item-processed (fn [])]
          (commands/handle-key :D)
          (should= [4 0] @target)))))

  (context "airport fighter launch (L56)"
    (it "resets waiting-for-input to false"
      (set-test-world! (build-test-map ["O#"]))
      (update-test-world! assoc-in [0 0 :awake-fighters] 1)
      (update-test-world! assoc-in [0 0 :fighter-count] 1)
      (setup-unit-attention [0 0])
      (test-utils/set-test-state! :waiting-for-input true)
      (with-redefs [container-ops/launch-fighter-from-airport (fn [_ _] [1 0])]
        (commands/handle-key :d)
        (should= false (test-utils/read-test-state :waiting-for-input)))))

  (context "army aboard disembark via key (L63-L65)"
    (it "calls disembark for non-extended direction toward land"
      (set-test-world! (build-test-map ["T#"]))
      (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 1)
      (setup-unit-attention [0 0])
      (let [disembark-called (atom false)]
        (with-redefs [container-ops/disembark-army-from-transport
                      (fn [_ _] (reset! disembark-called true))
                      container-ops/disembark-army-with-target
                      (fn [_ _ _] nil)
                      game-loop/item-processed (fn [])]
          (commands/handle-key :d)
          (should @disembark-called)))))

  (context "undamaged ship at friendly city (L76-L78)"
    (it "shows error for undamaged destroyer entering player city"
      (set-test-world! (build-test-map ["~O"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :destroyer :mode :awake :owner :player :hits 3})
      (setup-unit-attention [0 0])
      (with-redefs [movement/set-unit-movement (fn [_ _] nil)
                    game-loop/item-processed (fn [])]
        (commands/handle-key :d)
        (should-contain "not damaged" (test-utils/read-test-state :error-message)))))

  (context "army conquest via direction key (L82)"
    (it "attempts conquest when moving to hostile city"
      (set-test-world! (build-test-map ["A+"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
      (setup-unit-attention [0 0])
      (let [conquest-called (atom false)]
        (with-redefs [combat/attempt-conquest (fn [_ _ _] (reset! conquest-called true))
                      game-loop/item-processed (fn [])]
          (commands/handle-key :d)
          (should @conquest-called)))))

  (context "fighter overfly via direction key (L87)"
    (it "attempts overfly when moving to hostile city"
      (set-test-world! (build-test-map ["F+"]))
      (set-test-unit (test-utils/game-map-atom) "F" :mode :awake)
      (setup-unit-attention [0 0])
      (let [overfly-called (atom false)]
        (with-redefs [combat/attempt-fighter-overfly (fn [_ _ _] (reset! overfly-called true))
                      movement/set-unit-movement (fn [_ _] nil)
                      game-loop/item-processed (fn [])]
          (commands/handle-key :d)
          (should @overfly-called)))))

  (context "carrier-fighter launch via direction key"
    (it "launches fighter from carrier when carrier has awake fighters"
      (set-test-world! (build-test-map ["C~"]))
      (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :fighter-count 1 :awake-fighters 1)
      (setup-unit-attention [0 0])
      (let [launch-called (atom false)]
        (with-redefs [container-ops/launch-fighter-from-carrier (fn [_ _] (reset! launch-called true) [1 0])]
          (commands/handle-key :d)
          (should @launch-called)))))

  (context "non-player unit at attention cell"
    (it "returns nil when unit is computer-owned"
      (set-test-world! (build-test-map ["A#"]))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :mode :awake :owner :computer :hits 1})
      (setup-unit-attention [0 0])
      (should-be-nil (commands/handle-key :d)))))

;; ========== Mutation tests: cell click ==========

(describe "handle-cell-click (mutation tests)"
  (before (reset-all-atoms!))

  (context "click adjacent hostile city (L221, L231, L259)"
    (it "attempts conquest when clicking diagonally adjacent hostile city"
      (set-test-world! (build-test-map ["###" "###" "###"]))
      (update-test-world! assoc-in [1 1 :contents]
             {:type :army :mode :awake :owner :player :hits 1})
      (update-test-world! assoc-in [2 2] {:type :city :city-status :computer})
      (setup-unit-attention [1 1])
      (let [conquest-called (atom false)]
        (with-redefs [combat/attempt-conquest (fn [_ _ _] (reset! conquest-called true))
                      movement/set-unit-movement (fn [_ _] nil)
                      game-loop/item-processed (fn [])]
          (commands/handle-cell-click 2 2)
          (should @conquest-called)))))

  (context "click to disembark army aboard (L224, L225)"
    (it "disembarks army when clicking adjacent land"
      (set-test-world! (build-test-map ["~~~" "~T#" "~~~"]))
      (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 1)
      (setup-unit-attention [1 1])
      (let [disembark-called (atom false)]
        (with-redefs [container-ops/disembark-army-from-transport
                      (fn [_ _] (reset! disembark-called true))
                      game-loop/item-processed (fn [])]
          (commands/handle-cell-click 2 1)
          (should @disembark-called))))))

(run-specs)
