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
    (should (empty? @atoms/player-items)))

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
    (should= :transport (:item (get @atoms/production [1 1]))))

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

;; ========== handle-key: space key for units ==========

(describe "handle-key - space key for units"
  (before (reset-all-atoms!))

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

;; ========== handle-key: unit movement ==========

(describe "handle-key - unit movement"
  (before (reset-all-atoms!))

  (it "sets army to :moving mode when direction key pressed"
    (reset! atoms/game-map (build-test-map ["##"
                                             "#A"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (setup-unit-attention [1 1])
    ;; :q = northwest = [-1 -1] -> target [0 0]
    (commands/handle-key :q)
    (let [unit (get-in @atoms/game-map [1 1 :contents])]
      (should= :moving (:mode unit))
      (should= [0 0] (:target unit))))

  (it "sets extended target when shift-direction key pressed"
    (reset! atoms/game-map (build-test-map ["###"
                                             "###"
                                             "##A"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (setup-unit-attention [2 2])
    ;; :Q = far northwest = [-1 -1] -> should go to [0 0] (map corner)
    (commands/handle-key :Q)
    (let [unit (get-in @atoms/game-map [2 2 :contents])]
      (should= :moving (:mode unit))
      (should= [0 0] (:target unit))))

  (it "returns nil for non-direction key"
    (reset! atoms/game-map (build-test-map ["A"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (setup-unit-attention [0 0])
    (should-be-nil (commands/handle-key :j)))

  (it "does not move computer-owned units"
    (reset! atoms/game-map (build-test-map ["a#"]))
    ;; Computer army at [0 0], land at [1 0]
    (reset! atoms/cells-needing-attention [[0 0]])
    (reset! atoms/player-items (list [0 0]))
    (let [result (commands/handle-key :d)]
      ;; should not move the computer unit; active-unit check filters by :player
      (should-be-nil result))))

;; ========== handle-key: army conquest ==========

(describe "handle-key - army conquest"
  (before (reset-all-atoms!))

  (it "attempts conquest when army moves toward hostile city"
    (let [conquest-called (atom false)]
      (reset! atoms/game-map (build-test-map ["AX"]))
      (set-test-unit atoms/game-map "A" :mode :awake)
      (setup-unit-attention [0 0])
      (with-redefs [combat/attempt-conquest (fn [_ _] (reset! conquest-called true) true)]
        (commands/handle-key :d)
        (should @conquest-called)))))

;; ========== handle-key: fighter overfly hostile city ==========

(describe "handle-key - fighter overfly hostile city"
  (before (reset-all-atoms!))

  (it "attempts fighter overfly when fighter moves toward hostile city"
    (let [overfly-called (atom false)]
      (reset! atoms/game-map (build-test-map ["FX"]))
      (set-test-unit atoms/game-map "F" :mode :awake :fuel 32)
      (setup-unit-attention [0 0])
      (with-redefs [combat/hostile-city? (fn [_] true)
                    combat/attempt-fighter-overfly (fn [_ _] (reset! overfly-called true) true)]
        (commands/handle-key :d)
        (should @overfly-called)))))

;; ========== handle-key: undamaged ship entering friendly city ==========

(describe "handle-key - undamaged ship entering friendly city"
  (before (reset-all-atoms!))

  (it "shows error when undamaged destroyer tries to enter player city"
    (reset! atoms/game-map (build-test-map ["DO"]))
    (set-test-unit atoms/game-map "D" :mode :awake :hits (dispatcher/hits :destroyer))
    (setup-unit-attention [0 0])
    (commands/handle-key :d)
    (should-contain "not damaged" @atoms/error-message))

  (it "allows damaged destroyer to enter player city (sets movement)"
    (reset! atoms/game-map (build-test-map ["DO"]))
    (set-test-unit atoms/game-map "D" :mode :awake :hits 1)
    (setup-unit-attention [0 0])
    (commands/handle-key :d)
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should= :moving (:mode unit))
      (should= [1 0] (:target unit)))))

;; ========== handle-key: sentry ==========

(describe "handle-key - sentry key"
  (before (reset-all-atoms!))

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

;; ========== handle-key: unload ==========

(describe "handle-key - unload key"
  (before (reset-all-atoms!))

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

;; ========== handle-key: look-around / explore ==========

(describe "handle-key - look-around key"
  (before (reset-all-atoms!))

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

;; ========== handle-key: airport fighter launch ==========

(describe "handle-key - airport fighter launch"
  (before (reset-all-atoms!))

  (it "launches fighter from airport on direction key"
    (let [launch-called (atom false)]
      (reset! atoms/game-map (build-test-map ["O#"]))
      (swap! atoms/game-map assoc-in [0 0 :awake-fighters] 1)
      (swap! atoms/game-map assoc-in [0 0 :fighter-count] 1)
      (setup-unit-attention [0 0])
      (with-redefs [container-ops/launch-fighter-from-airport
                    (fn [_ _] (reset! launch-called true) [0 0])]
        (commands/handle-key :d)
        (should @launch-called)
        (should= false @atoms/waiting-for-input)
        (should= "" @atoms/attention-message)))))

;; ========== handle-key: carrier fighter launch ==========

(describe "handle-key - carrier fighter launch"
  (before (reset-all-atoms!))

  (it "launches fighter from carrier on direction key"
    (let [launch-called (atom false)]
      (reset! atoms/game-map (build-test-map ["C~"]))
      (set-test-unit atoms/game-map "C" :mode :sentry :fighter-count 2 :awake-fighters 1)
      (setup-unit-attention [0 0])
      (with-redefs [container-ops/launch-fighter-from-carrier
                    (fn [_ _] (reset! launch-called true) [1 0])]
        (commands/handle-key :d)
        (should @launch-called)))))

;; ========== handle-key: army aboard transport disembark ==========

(describe "handle-key - army aboard transport"
  (before (reset-all-atoms!))

  (it "disembarks army to adjacent land"
    (let [disembark-called (atom false)]
      ;; Transport at [0 0] (sea), land at [1 0]
      (reset! atoms/game-map (build-test-map ["T#"]))
      (set-test-unit atoms/game-map "T" :mode :sentry :army-count 2 :awake-armies 2)
      (setup-unit-attention [0 0])
      (with-redefs [container-ops/disembark-army-from-transport
                    (fn [_ _] (reset! disembark-called true) [1 0])]
        (commands/handle-key :d)
        (should @disembark-called))))

  (it "disembarks army with extended target on shift key"
    (let [disembark-target-called (atom false)]
      ;; Transport at [0 0] (sea), land cells to the east
      (reset! atoms/game-map (build-test-map ["T###"]))
      (set-test-unit atoms/game-map "T" :mode :sentry :army-count 2 :awake-armies 2)
      (setup-unit-attention [0 0])
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_ _ _] (reset! disembark-target-called true))]
        (commands/handle-key :D)
        (should @disembark-target-called))))

  (it "does not disembark army to sea"
    (let [disembark-called (atom false)]
      (reset! atoms/game-map (build-test-map ["T~"]))
      (set-test-unit atoms/game-map "T" :mode :sentry :army-count 2 :awake-armies 2)
      (setup-unit-attention [0 0])
      (with-redefs [container-ops/disembark-army-from-transport
                    (fn [_ _] (reset! disembark-called true) [1 0])]
        (commands/handle-key :d)
        ;; Should not disembark to sea
        (should-not @disembark-called))))

  (it "does not disembark army to occupied land"
    (let [disembark-called (atom false)]
      (reset! atoms/game-map (build-test-map ["TA"]))
      (set-test-unit atoms/game-map "T" :mode :sentry :army-count 2 :awake-armies 2)
      (setup-unit-attention [0 0])
      (with-redefs [container-ops/disembark-army-from-transport
                    (fn [_ _] (reset! disembark-called true) [1 0])]
        (commands/handle-key :d)
        (should-not @disembark-called)))))

;; ========== handle-cell-click ==========

(describe "handle-cell-click"
  (before (reset-all-atoms!))

  (it "calls handle-unit-click when attention unit exists"
    (let [movement-called (atom false)]
      (reset! atoms/game-map (build-test-map ["A##"
                                               "###"
                                               "###"]))
      (set-test-unit atoms/game-map "A" :mode :awake)
      (reset! atoms/cells-needing-attention [[0 0]])
      (reset! atoms/player-items (list [0 0]))
      (with-redefs [movement/set-unit-movement (fn [_ _] (reset! movement-called true))]
        (commands/handle-cell-click 2 2)
        (should @movement-called))))

  (it "does nothing when no attention units exist"
    (reset! atoms/game-map (build-test-map ["#"]))
    (reset! atoms/cells-needing-attention [])
    (should-be-nil (commands/handle-cell-click 0 0))))

;; ========== handle-unit-click ==========

(describe "handle-unit-click"
  (before (reset-all-atoms!))

  (it "sets movement for standard unit click"
    (let [movement-called (atom false)]
      (reset! atoms/game-map (build-test-map ["A##"
                                               "###"
                                               "###"]))
      (set-test-unit atoms/game-map "A" :mode :awake)
      (reset! atoms/cells-needing-attention [[0 0]])
      (reset! atoms/player-items (list [0 0]))
      (with-redefs [movement/set-unit-movement (fn [from to]
                                                  (reset! movement-called true))]
        (commands/handle-unit-click [2 2] [[0 0]])
        (should @movement-called))))

  (it "launches airport fighter on click"
    (let [launch-called (atom false)]
      (reset! atoms/game-map (build-test-map ["O##"
                                               "###"
                                               "###"]))
      (swap! atoms/game-map assoc-in [0 0 :awake-fighters] 1)
      (swap! atoms/game-map assoc-in [0 0 :fighter-count] 1)
      (reset! atoms/cells-needing-attention [[0 0]])
      (reset! atoms/player-items (list [0 0]))
      (with-redefs [container-ops/launch-fighter-from-airport
                    (fn [_ _] (reset! launch-called true) [0 0])]
        (commands/handle-unit-click [2 2] [[0 0]])
        (should @launch-called)
        (should= false @atoms/waiting-for-input))))

  (it "disembarks army from transport on adjacent land click"
    (let [disembark-called (atom false)]
      (reset! atoms/game-map (build-test-map ["T#"]))
      (set-test-unit atoms/game-map "T" :mode :sentry :army-count 2 :awake-armies 2)
      (reset! atoms/cells-needing-attention [[0 0]])
      (reset! atoms/player-items (list [0 0]))
      (with-redefs [container-ops/disembark-army-from-transport
                    (fn [_ _] (reset! disembark-called true) [1 0])]
        (commands/handle-unit-click [1 0] [[0 0]])
        (should @disembark-called))))

  (it "ignores invalid army-aboard click targets"
    (reset! atoms/game-map (build-test-map ["T~"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :army-count 2 :awake-armies 2)
    (reset! atoms/cells-needing-attention [[0 0]])
    (reset! atoms/player-items (list [0 0]))
    ;; Click on sea cell - should be ignored for army disembark
    (commands/handle-unit-click [1 0] [[0 0]])
    ;; item-processed still gets called at the end of handle-unit-click
    (should= [] @atoms/cells-needing-attention))

  (it "attempts conquest when army clicks adjacent hostile city"
    (let [conquest-called (atom false)]
      (reset! atoms/game-map (build-test-map ["AX"]))
      (set-test-unit atoms/game-map "A" :mode :awake)
      (reset! atoms/cells-needing-attention [[0 0]])
      (reset! atoms/player-items (list [0 0]))
      (with-redefs [combat/attempt-conquest (fn [_ _] (reset! conquest-called true) true)]
        (commands/handle-unit-click [1 0] [[0 0]])
        (should @conquest-called))))

  (it "attempts fighter overfly when fighter clicks adjacent hostile city"
    (let [overfly-called (atom false)]
      (reset! atoms/game-map (build-test-map ["FX"]))
      (set-test-unit atoms/game-map "F" :mode :awake :fuel 32)
      (reset! atoms/cells-needing-attention [[0 0]])
      (reset! atoms/player-items (list [0 0]))
      (with-redefs [combat/hostile-city? (fn [_] true)
                    combat/attempt-fighter-overfly (fn [_ _] (reset! overfly-called true) true)]
        (commands/handle-unit-click [1 0] [[0 0]])
        (should @overfly-called))))

  (it "attempts conquest when army clicks adjacent hostile city at non-origin coords"
    (let [conquest-called (atom false)]
      (reset! atoms/game-map (build-test-map ["###" "###" "#AX"]))
      (set-test-unit atoms/game-map "A" :mode :awake)
      (reset! atoms/cells-needing-attention [[1 2]])
      (reset! atoms/player-items (list [1 2]))
      (with-redefs [combat/attempt-conquest (fn [_ _] (reset! conquest-called true) true)]
        (commands/handle-unit-click [2 2] [[1 2]])
        (should @conquest-called))))

  (it "attempts conquest on diagonally adjacent hostile city click"
    (let [conquest-called (atom false)]
      (reset! atoms/game-map (build-test-map ["###" "#A#" "##X"]))
      (set-test-unit atoms/game-map "A" :mode :awake)
      (reset! atoms/cells-needing-attention [[1 1]])
      (reset! atoms/player-items (list [1 1]))
      (with-redefs [combat/attempt-conquest (fn [_ _] (reset! conquest-called true) true)]
        (commands/handle-unit-click [2 2] [[1 1]])
        (should @conquest-called))))

  (it "resets waiting-for-input on airport fighter launch click"
    (let [launch-called (atom false)]
      (reset! atoms/game-map (build-test-map ["O##" "###" "###"]))
      (swap! atoms/game-map assoc-in [0 0 :awake-fighters] 1)
      (swap! atoms/game-map assoc-in [0 0 :fighter-count] 1)
      (reset! atoms/cells-needing-attention [[0 0]])
      (reset! atoms/player-items (list [0 0]))
      (reset! atoms/waiting-for-input true)
      (with-redefs [container-ops/launch-fighter-from-airport
                    (fn [_ _] (reset! launch-called true) [0 0])]
        (commands/handle-unit-click [2 2] [[0 0]])
        (should @launch-called)
        (should= false @atoms/waiting-for-input)))))

;; ========== handle-key: no attention items ==========

(describe "handle-key - no attention items"
  (before (reset-all-atoms!))

  (it "returns nil when cells-needing-attention is empty"
    (reset! atoms/game-map (build-test-map ["A"]))
    (reset! atoms/cells-needing-attention [])
    (should-be-nil (commands/handle-key :w))))

;; ========== handle-key: item-processed side effects ==========

(describe "handle-key - item-processed clearing"
  (before (reset-all-atoms!))

  (it "clears waiting-for-input and cells-needing-attention"
    (reset! atoms/game-map (build-test-map ["A"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (setup-unit-attention [0 0])
    (reset! atoms/waiting-for-input true)
    (commands/handle-key :space)
    (should= false @atoms/waiting-for-input)
    (should= [] @atoms/cells-needing-attention)))

;; ========== handle-key: destroyer production variants ==========

(describe "handle-key - production variants"
  (before (reset-all-atoms!))

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
    (should= :satellite (:item (get @atoms/production [0 0])))))

;; ========== handle-key: space on fighter with various fuel levels ==========

(describe "handle-key - fighter fuel edge cases"
  (before (reset-all-atoms!))

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

;; ========== handle-key: standard movement sets target ==========

(describe "handle-key - standard unit movement target"
  (before (reset-all-atoms!))

  (it "sets movement target equal to adjacent cell for normal direction"
    (reset! atoms/game-map (build-test-map ["~D"
                                             "~~"]))
    (set-test-unit atoms/game-map "D" :mode :awake)
    (setup-unit-attention [1 0])
    ;; :c = southeast [1, 1] -> target [2 1], but map is only 2x2
    ;; :x = south [0, 1] -> target [1 1]
    (commands/handle-key :x)
    (let [unit (get-in @atoms/game-map [1 0 :contents])]
      (should= :moving (:mode unit))
      (should= [1 1] (:target unit))))

  (it "sets extended target to map edge for shift direction"
    (reset! atoms/game-map (build-test-map ["~~D"
                                             "~~~"
                                             "~~~"]))
    (set-test-unit atoms/game-map "D" :mode :awake)
    (setup-unit-attention [2 0])
    ;; :X = far south [0, 1] -> target [2 2] (bottom of map)
    (commands/handle-key :X)
    (let [unit (get-in @atoms/game-map [2 0 :contents])]
      (should= :moving (:mode unit))
      (should= [2 2] (:target unit))))

  (it "sets extended target to column edge for shift direction along columns"
    (reset! atoms/game-map (build-test-map ["D~~"]))
    (set-test-unit atoms/game-map "D" :mode :awake)
    (setup-unit-attention [0 0])
    ;; :D = far east [1, 0] -> extends along columns to [2 0]
    (commands/handle-key :D)
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should= :moving (:mode unit))
      (should= [2 0] (:target unit)))))

(run-specs)
