(ns empire.player.commands-actions-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.player.commands :as commands]
            [empire.config.core :as config]
            [empire.game-mechanics.services.combat :as combat]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.movement.coastline :as coastline]
            [empire.game-mechanics.movement.explore :as explore]
            [empire.game-mechanics.movement.api :as movement]
            [empire.player.production :as production]
            [empire.player.orders :as player-orders]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-unit set-test-world! update-test-world!]]))

;; Helpers

(defn- setup-unit-attention
  "Sets up game-map, cells-needing-attention, and player-items for a unit at coords."
  [coords]
  (test-utils/set-test-state! :cells-needing-attention [coords])
  (test-utils/set-test-state! :player-items (list coords)))

;; ========== handle-key: movement and combat ==========

(describe "handle-key - movement and combat"
  (before (reset-all-atoms!))

  (context "unit movement"
    (it "sets army to :moving mode when direction key pressed"
      (set-test-world! (build-test-map ["##"
                                               "#A"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
      (setup-unit-attention [1 1])
      ;; :q = northwest = [-1 -1] -> target [0 0]
      (commands/handle-key :q)
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
        (should= :moving (:mode unit))
        (should= [0 0] (:target unit))))

    (it "sets extended target when shift-direction key pressed"
      (set-test-world! (build-test-map ["###"
                                               "###"
                                               "##A"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
      (setup-unit-attention [2 2])
      ;; :Q = far northwest = [-1 -1] -> should go to [0 0] (map corner)
      (commands/handle-key :Q)
      (let [unit (get-in (test-utils/read-test-state :game-map) [2 2 :contents])]
        (should= :moving (:mode unit))
        (should= [0 0] (:target unit))))

    (it "returns nil for non-direction key"
      (set-test-world! (build-test-map ["A"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
      (setup-unit-attention [0 0])
      (should-be-nil (commands/handle-key :j)))

    (it "does not move computer-owned units"
      (set-test-world! (build-test-map ["a#"]))
      ;; Computer army at [0 0], land at [1 0]
      (test-utils/set-test-state! :cells-needing-attention [[0 0]])
      (test-utils/set-test-state! :player-items (list [0 0]))
      (let [result (commands/handle-key :d)]
        ;; should not move the computer unit; active-unit check filters by :player
        (should-be-nil result))))

  (context "army conquest"
    (it "attempts conquest when army moves toward hostile city"
      (let [conquest-called (atom false)]
        (set-test-world! (build-test-map ["AX"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
        (setup-unit-attention [0 0])
        (with-redefs [combat/attempt-conquest (fn [_ _ _] (reset! conquest-called true) true)]
          (commands/handle-key :d)
          (should @conquest-called)))))

  (context "fighter overfly hostile city"
    (it "attempts fighter overfly when fighter moves toward hostile city"
      (let [overfly-called (atom false)]
        (set-test-world! (build-test-map ["FX"]))
        (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :fuel 32)
        (setup-unit-attention [0 0])
        (with-redefs [combat/hostile-city? (fn [_ _] true)
                      combat/attempt-fighter-overfly (fn [_ _ _] (reset! overfly-called true) true)]
          (commands/handle-key :d)
          (should @overfly-called)))))

  (context "undamaged ship entering friendly city"
    (it "shows error when undamaged destroyer tries to enter player city"
      (set-test-world! (build-test-map ["DO"]))
      (set-test-unit (test-utils/game-map-atom) "D" :mode :awake :hits (dispatcher/hits :destroyer))
      (setup-unit-attention [0 0])
      (commands/handle-key :d)
      (should-contain "not damaged" (test-utils/read-test-state :error-message)))

    (it "allows damaged destroyer to enter player city (sets movement)"
      (set-test-world! (build-test-map ["DO"]))
      (set-test-unit (test-utils/game-map-atom) "D" :mode :awake :hits 1)
      (setup-unit-attention [0 0])
      (commands/handle-key :d)
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :moving (:mode unit))
        (should= [1 0] (:target unit)))))

  (context "standard unit movement target"
    (it "sets movement target equal to adjacent cell for normal direction"
      (set-test-world! (build-test-map ["~D"
                                               "~~"]))
      (set-test-unit (test-utils/game-map-atom) "D" :mode :awake)
      (setup-unit-attention [1 0])
      ;; :c = southeast [1, 1] -> target [2 1], but map is only 2x2
      ;; :x = south [0, 1] -> target [1 1]
      (commands/handle-key :x)
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (should= :moving (:mode unit))
        (should= [1 1] (:target unit))))

    (it "sets extended target to map edge for shift direction"
      (set-test-world! (build-test-map ["~~D"
                                               "~~~"
                                               "~~~"]))
      (set-test-unit (test-utils/game-map-atom) "D" :mode :awake)
      (setup-unit-attention [2 0])
      ;; :X = far south [0, 1] -> target [2 2] (bottom of map)
      (commands/handle-key :X)
      (let [unit (get-in (test-utils/read-test-state :game-map) [2 0 :contents])]
        (should= :moving (:mode unit))
        (should= [2 2] (:target unit))))

    (it "sets extended target to column edge for shift direction along columns"
      (set-test-world! (build-test-map ["D~~"]))
      (set-test-unit (test-utils/game-map-atom) "D" :mode :awake)
      (setup-unit-attention [0 0])
      ;; :D = far east [1, 0] -> extends along columns to [2 0]
      (commands/handle-key :D)
      (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
        (should= :moving (:mode unit))
        (should= [2 0] (:target unit))))))

;; ========== handle-key: container operations ==========

(describe "handle-key - container operations"
  (before (reset-all-atoms!))

  (context "airport fighter launch"
    (it "launches fighter from airport on direction key"
      (let [launch-called (atom false)]
        (set-test-world! (build-test-map ["O#"]))
        (update-test-world! assoc-in [0 0 :awake-fighters] 1)
        (update-test-world! assoc-in [0 0 :fighter-count] 1)
        (test-utils/set-test-state! :production {[0 0] {:item :army :remaining-rounds 5}})
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/launch-fighter-from-airport
                      (fn [_ _] (reset! launch-called true) [0 0])]
          (commands/handle-key :d)
          (should @launch-called)
          (should= false (test-utils/read-test-state :waiting-for-input))
          (should= "" (test-utils/read-test-state :attention-message))))))

  (context "carrier fighter launch"
    (it "launches fighter from carrier on direction key"
      (let [launch-called (atom false)]
        (set-test-world! (build-test-map ["C~"]))
        (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :fighter-count 2 :awake-fighters 1)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/launch-fighter-from-carrier
                      (fn [_ _] (reset! launch-called true) [1 0])]
          (commands/handle-key :d)
          (should @launch-called)))))

  (context "army aboard transport"
    (it "disembarks army to adjacent land"
      (let [disembark-called (atom false)]
        ;; Transport at [0 0] (sea), land at [1 0]
        (set-test-world! (build-test-map ["T#"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/disembark-army-from-transport
                      (fn [_ _] (reset! disembark-called true) [1 0])]
          (commands/handle-key :d)
          (should @disembark-called))))

    (it "disembarks army with extended target on shift key"
      (let [disembark-target-called (atom false)]
        ;; Transport at [0 0] (sea), land cells to the east
        (set-test-world! (build-test-map ["T###"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/disembark-army-with-target
                      (fn [_ _ _] (reset! disembark-target-called true))]
          (commands/handle-key :D)
          (should @disembark-target-called))))

    (it "does not disembark army to sea"
      (let [disembark-called (atom false)]
        (set-test-world! (build-test-map ["T~"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/disembark-army-from-transport
                      (fn [_ _] (reset! disembark-called true) [1 0])]
          (commands/handle-key :d)
          ;; Should not disembark to sea
          (should-not @disembark-called))))

    (it "does not disembark army to occupied land"
      (let [disembark-called (atom false)]
        (set-test-world! (build-test-map ["TA"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/disembark-army-from-transport
                      (fn [_ _] (reset! disembark-called true) [1 0])]
          (commands/handle-key :d)
          (should-not @disembark-called))))))

  (context "u key - wake fighters on airport"
    (it "wakes all fighters on airport when city has fighters and u pressed"
      (set-test-world! (build-test-map ["O"]))
      (update-test-world! assoc-in [0 0 :fighter-count] 2)
      (update-test-world! assoc-in [0 0 :awake-fighters] 0)
      (setup-unit-attention [0 0])
      (player-orders/wake-at [0 0])
      (should= 2 (get-in (test-utils/read-test-state :game-map) [0 0 :awake-fighters])))

    (it "does not call wake-fighters-on-airport when city has no fighters"
      (let [wake-called (atom false)]
        (set-test-world! (build-test-map ["O"]))
        (update-test-world! assoc-in [0 0 :fighter-count] 0)
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/wake-fighters-on-airport
                      (fn [_] (reset! wake-called true))]
          (commands/handle-key :u)
          (should-not @wake-called)))))

  (context "s key - sleep fighters on airport"
    (it "calls sleep-fighters-on-airport when airport fighter is active and s pressed"
      (let [sleep-called (atom false)]
        (set-test-world! (build-test-map ["O"]))
        (update-test-world! assoc-in [0 0 :fighter-count] 2)
        (update-test-world! assoc-in [0 0 :awake-fighters] 1)
        (update-test-world! assoc-in [0 0 :contents] {:type :fighter :owner :player :from-airport true :mode :awake})
        (setup-unit-attention [0 0])
        (with-redefs [container-ops/sleep-fighters-on-airport
                      (fn [_] (reset! sleep-called true))]
          (commands/handle-key :s)
          (should @sleep-called)
          (should= false (test-utils/read-test-state :waiting-for-input))))))

;; ========== click handlers ==========

(describe "click handlers"
  (before (reset-all-atoms!))

  (context "handle-cell-click"
    (it "calls handle-unit-click when attention unit exists"
      (let [movement-called (atom false)]
        (set-test-world! (build-test-map ["A##"
                                                 "###"
                                                 "###"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
        (test-utils/set-test-state! :cells-needing-attention [[0 0]])
        (test-utils/set-test-state! :player-items (list [0 0]))
        (with-redefs [movement/set-unit-movement (fn [_ _] (reset! movement-called true))]
          (commands/handle-cell-click 2 2)
          (should @movement-called))))

    (it "does nothing when no attention units exist"
      (set-test-world! (build-test-map ["#"]))
      (test-utils/set-test-state! :cells-needing-attention [])
      (should-be-nil (commands/handle-cell-click 0 0))))

  (context "handle-unit-click"
    (it "sets movement for standard unit click"
      (let [movement-called (atom false)]
        (set-test-world! (build-test-map ["A##"
                                                 "###"
                                                 "###"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
        (test-utils/set-test-state! :cells-needing-attention [[0 0]])
        (test-utils/set-test-state! :player-items (list [0 0]))
        (with-redefs [movement/set-unit-movement (fn [from to]
                                                    (reset! movement-called true))]
          (commands/handle-unit-click [2 2] [[0 0]])
          (should @movement-called))))

    (it "attacks a hostile ship from the coast"
      (let [attack-called (atom false)]
        (set-test-world! (build-test-map ["Ad"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
        (set-test-unit (test-utils/game-map-atom) "d" :owner :computer :hits 3)
        (test-utils/set-test-state! :cells-needing-attention [[0 0]])
        (test-utils/set-test-state! :player-items (list [0 0]))
        (with-redefs [combat/attempt-coastal-army-attack (fn [_ _ _] (reset! attack-called true) {})
                      combat/apply-combat-result! (fn [_] nil)]
          (commands/handle-unit-click [1 0] [[0 0]])
          (should @attack-called))))

    (it "launches airport fighter on click"
      (let [launch-called (atom false)]
        (set-test-world! (build-test-map ["O##"
                                                 "###"
                                                 "###"]))
        (update-test-world! assoc-in [0 0 :awake-fighters] 1)
        (update-test-world! assoc-in [0 0 :fighter-count] 1)
        (test-utils/set-test-state! :production {[0 0] {:item :army :remaining-rounds 5}})
        (test-utils/set-test-state! :cells-needing-attention [[0 0]])
        (test-utils/set-test-state! :player-items (list [0 0]))
        (with-redefs [container-ops/launch-fighter-from-airport
                      (fn [_ _] (reset! launch-called true) [0 0])]
          (commands/handle-unit-click [2 2] [[0 0]])
          (should @launch-called)
          (should= false (test-utils/read-test-state :waiting-for-input)))))

    (it "disembarks army from transport on adjacent land click"
      (let [disembark-called (atom false)]
        (set-test-world! (build-test-map ["T#"]))
        (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
        (test-utils/set-test-state! :cells-needing-attention [[0 0]])
        (test-utils/set-test-state! :player-items (list [0 0]))
        (with-redefs [container-ops/disembark-army-from-transport
                      (fn [_ _] (reset! disembark-called true) [1 0])]
          (commands/handle-unit-click [1 0] [[0 0]])
          (should @disembark-called))))

    (it "ignores invalid army-aboard click targets"
      (set-test-world! (build-test-map ["T~"]))
      (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :army-count 2 :awake-armies 2)
      (test-utils/set-test-state! :cells-needing-attention [[0 0]])
      (test-utils/set-test-state! :player-items (list [0 0]))
      (test-utils/set-test-state! :waiting-for-input true)
      ;; Click on sea cell - should be ignored for army disembark
      (commands/handle-unit-click [1 0] [[0 0]])
      (should= [[0 0]] (test-utils/read-test-state :cells-needing-attention))
      (should= true (test-utils/read-test-state :waiting-for-input)))

    (it "keeps attention on the unit after an invalid standard click"
      (set-test-world! (build-test-map ["A~"]))
      (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
      (test-utils/set-test-state! :cells-needing-attention [[0 0]])
      (test-utils/set-test-state! :player-items (list [0 0]))
      (test-utils/set-test-state! :waiting-for-input true)
      (commands/handle-unit-click [1 0] [[0 0]])
      (should= [[0 0]] (test-utils/read-test-state :cells-needing-attention))
      (should= true (test-utils/read-test-state :waiting-for-input))
      (should= "Can't move into water." (test-utils/read-test-state :attention-message)))

    (it "attempts conquest when army clicks adjacent hostile city"
      (let [conquest-called (atom false)]
        (set-test-world! (build-test-map ["AX"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
        (test-utils/set-test-state! :cells-needing-attention [[0 0]])
        (test-utils/set-test-state! :player-items (list [0 0]))
        (with-redefs [combat/attempt-conquest (fn [_ _ _] (reset! conquest-called true) true)]
          (commands/handle-unit-click [1 0] [[0 0]])
          (should @conquest-called))))

    (it "attempts fighter overfly when fighter clicks adjacent hostile city"
      (let [overfly-called (atom false)]
        (set-test-world! (build-test-map ["FX"]))
        (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :fuel 32)
        (test-utils/set-test-state! :cells-needing-attention [[0 0]])
        (test-utils/set-test-state! :player-items (list [0 0]))
        (with-redefs [combat/hostile-city? (fn [_ _] true)
                      combat/attempt-fighter-overfly (fn [_ _ _] (reset! overfly-called true) true)]
          (commands/handle-unit-click [1 0] [[0 0]])
          (should @overfly-called))))

    (it "attempts conquest when army clicks adjacent hostile city at non-origin coords"
      (let [conquest-called (atom false)]
        (set-test-world! (build-test-map ["###" "###" "#AX"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
        (test-utils/set-test-state! :cells-needing-attention [[1 2]])
        (test-utils/set-test-state! :player-items (list [1 2]))
        (with-redefs [combat/attempt-conquest (fn [_ _ _] (reset! conquest-called true) true)]
          (commands/handle-unit-click [2 2] [[1 2]])
          (should @conquest-called))))

    (it "attempts conquest on diagonally adjacent hostile city click"
      (let [conquest-called (atom false)]
        (set-test-world! (build-test-map ["###" "#A#" "##X"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
        (test-utils/set-test-state! :cells-needing-attention [[1 1]])
        (test-utils/set-test-state! :player-items (list [1 1]))
        (with-redefs [combat/attempt-conquest (fn [_ _ _] (reset! conquest-called true) true)]
          (commands/handle-unit-click [2 2] [[1 1]])
          (should @conquest-called))))

    (it "resets waiting-for-input on airport fighter launch click"
      (let [launch-called (atom false)]
        (set-test-world! (build-test-map ["O##" "###" "###"]))
        (update-test-world! assoc-in [0 0 :awake-fighters] 1)
        (update-test-world! assoc-in [0 0 :fighter-count] 1)
        (test-utils/set-test-state! :production {[0 0] {:item :army :remaining-rounds 5}})
        (test-utils/set-test-state! :cells-needing-attention [[0 0]])
        (test-utils/set-test-state! :player-items (list [0 0]))
        (test-utils/set-test-state! :waiting-for-input true)
        (with-redefs [container-ops/launch-fighter-from-airport
                      (fn [_ _] (reset! launch-called true) [0 0])]
          (commands/handle-unit-click [2 2] [[0 0]])
          (should @launch-called)
          (should= false (test-utils/read-test-state :waiting-for-input)))))))

(run-specs)
