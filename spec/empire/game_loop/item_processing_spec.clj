(ns empire.game.loop.item-processing-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game.loop.item-processing :as ip]
            [empire.config.core :as config]
            [empire.state.api :as sa]
            [empire.game-mechanics.movement.api :as movement]
            [empire.game-mechanics.movement.explore :as explore]
            [empire.game-mechanics.movement.coastline :as coastline]
            [empire.player.attention :as attention]
            [empire.computer.coordinator :as computer]
            [empire.computer.production :as computer-production]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.test.utils :refer [reset-all-atoms! set-test-world! update-test-world!]]))

(defn- land-cell [] {:type :land})
(defn- sea-cell [] {:type :sea})

(defn- make-land-map [n]
  (vec (repeat n (vec (repeat n (land-cell))))))

(defn- mock-move
  "Returns a mock move-unit fn that moves the unit and returns given result."
  [result-type]
  (fn [from-coords _target cell _game-map-source]
    (let [unit (:contents cell)
          [c r] from-coords
          new-pos [(inc c) r]]
      (sa/update-world! assoc-in (conj from-coords :contents) nil)
      (sa/update-world! assoc-in (conj new-pos :contents) unit)
      {:result result-type :pos new-pos})))

(defn- mock-move-to
  "Mock move-unit that moves unit to a specific position."
  [result-type new-pos]
  (fn [from-coords _target cell _game-map-source]
    (let [unit (:contents cell)]
      (sa/update-world! assoc-in (conj from-coords :contents) nil)
      (sa/update-world! assoc-in (conj new-pos :contents) unit)
      {:result result-type :pos new-pos})))

;; ===== check-player-victory! (L21, L22, L29, L39) =====

(describe "check-player-victory!"
  (before (reset-all-atoms!))

  (it "does not pause game when no computer city exists"
    (test-utils/set-test-state! :game-over-check-enabled true)
    (set-test-world! [[{:type :land}]])
    (ip/check-player-victory!)
    (should= false (test-utils/read-test-state :paused)))

  (it "does not declare victory when computer city exists (L21)"
    (test-utils/set-test-state! :game-over-check-enabled true)
    (set-test-world! [[{:type :city :city-status :computer}]])
    (ip/check-player-victory!)
    (should= false (test-utils/read-test-state :paused)))

  (it "does not declare victory when computer unit exists (L22)"
    (test-utils/set-test-state! :game-over-check-enabled true)
    (set-test-world! [[{:type :land :contents {:type :army :owner :computer}}]])
    (ip/check-player-victory!)
    (should= false (test-utils/read-test-state :paused)))

  (it "does not flush item lists (city elimination handled on conquest)"
    (test-utils/set-test-state! :game-over-check-enabled true)
    (set-test-world! [[{:type :land}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (test-utils/set-test-state! :computer-items [[1 1]])
    (ip/check-player-victory!)
    (should= [[0 0]] (test-utils/read-test-state :player-items))
    (should= [[1 1]] (test-utils/read-test-state :computer-items)))

  (it "does not declare victory when check disabled"
    (test-utils/set-test-state! :game-over-check-enabled false)
    (set-test-world! [[{:type :land}]])
    (ip/check-player-victory!)
    (should= false (test-utils/read-test-state :paused))))

;; ===== move-current-unit: guard clause (L50, L51) =====

(describe "move-current-unit guard"
  (before (reset-all-atoms!))

  (it "returns nil when unit not in :moving mode (L50)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :awake :steps-remaining 2}}
                              (land-cell)]])
    (with-redefs [movement/move-unit (mock-move :normal)]
      (should-be-nil (ip/move-current-unit [0 0]))))

  (it "returns nil when steps-remaining is 0 (L50)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [1 0]
                                                      :steps-remaining 0}}
                              (land-cell)]])
    (with-redefs [movement/move-unit (mock-move :normal)]
      (should-be-nil (ip/move-current-unit [0 0]))))

  (it "processes unit without explicit steps-remaining using default 1 (L51)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [1 0]}}
                              (land-cell)]])
    (let [called? (atom false)]
      (with-redefs [movement/move-unit
                    (fn [from _t cell gm]
                      (reset! called? true)
                      ((mock-move :normal) from _t cell gm))]
        (ip/move-current-unit [0 0])
        (should @called?)))))

;; ===== move-current-unit: normal branch (L68-75) =====

(describe "move-current-unit normal"
  (before (reset-all-atoms!))

  (it "returns pos when steps remain after normal move (L71, L75)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [2 0]
                                                      :steps-remaining 3}}
                              (land-cell) (land-cell)]])
    (with-redefs [movement/move-unit (mock-move :normal)]
      (let [result (ip/move-current-unit [0 0])]
        (should= [1 0] result))))

  (it "returns nil when steps reach 0 after normal move (L75 > boundary)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [1 0]
                                                      :steps-remaining 1}}
                              (land-cell)]])
    (with-redefs [movement/move-unit (mock-move :normal)]
      (should-be-nil (ip/move-current-unit [0 0]))))

  (it "decrements steps-remaining after normal move (L72 dec)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [2 0]
                                                      :steps-remaining 3}}
                              (land-cell) (land-cell)]])
    (with-redefs [movement/move-unit (mock-move :normal)]
      (ip/move-current-unit [0 0])
      (should= 2 (get-in (test-utils/read-test-state :game-map) [1 0 :contents :steps-remaining]))))

  (it "returns pos when exactly 1 step remains after move (L75 0→1 boundary)"
    ;; steps-remaining=2, dec→1, (> 1 0)=true → returns pos
    ;; Mutant (> 1 1)=false → returns nil
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [2 0]
                                                      :steps-remaining 2}}
                              (land-cell) (land-cell)]])
    (with-redefs [movement/move-unit (mock-move :normal)]
      (should= [1 0] (ip/move-current-unit [0 0]))))

  (it "uses default 1 for moved-unit steps-remaining (L72 1→0)"
    ;; Unit without :steps-remaining key; default 1, dec→0
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [1 0]}}
                              (land-cell)]])
    (with-redefs [movement/move-unit (mock-move :normal)]
      (ip/move-current-unit [0 0])
      ;; With default 1: dec→0. With mutant default 0: dec→-1.
      (should= 0 (get-in (test-utils/read-test-state :game-map) [1 0 :contents :steps-remaining])))))

;; ===== move-current-unit: sidestep branch (L56-64) =====

(describe "move-current-unit sidestep"
  (before (reset-all-atoms!))

  (it "decrements steps on sidestep (L59, L60 dec)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [2 0]
                                                      :steps-remaining 3}}
                              (land-cell) (land-cell)]])
    ;; Use max-sidesteps=0 so no recur after sidestep
    (with-redefs [movement/move-unit (mock-move :sidestep)]
      (ip/move-current-unit [0 0] 0)
      (should= 2 (get-in (test-utils/read-test-state :game-map) [1 0 :contents :steps-remaining]))))

  (it "returns pos when steps remain but max-sidesteps exhausted (L62, L63)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [2 0]
                                                      :steps-remaining 3}}
                              (land-cell) (land-cell)]])
    (with-redefs [movement/move-unit (mock-move :sidestep)]
      (should= [1 0] (ip/move-current-unit [0 0] 0))))

  (it "recurs when max-sidesteps > 0 (L63 if→if-not)"
    ;; With max-sidesteps=1: first sidestep recurs, second call returns
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [3 0]
                                                      :steps-remaining 4}}
                              (land-cell) (land-cell) (land-cell)]])
    (let [call-count (atom 0)]
      (with-redefs [movement/move-unit
                    (fn [from _t cell gm]
                      (swap! call-count inc)
                      ((mock-move :sidestep) from _t cell gm))]
        ;; max-sidesteps=1: first sidestep recurs, second sidestep has max=0 → returns pos
        (ip/move-current-unit [0 0] 1)
        (should= 2 @call-count))))

  (it "returns nil when steps reach 0 on sidestep (L62 boundary)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [1 0]
                                                      :steps-remaining 1}}
                              (land-cell)]])
    (with-redefs [movement/move-unit (mock-move :sidestep)]
      (should-be-nil (ip/move-current-unit [0 0] 0))))

  (it "returns pos when exactly 1 step remains after sidestep (L62 0→1)"
    ;; steps=2, dec→1, (> 1 0)=true with max-sidesteps=0 → returns pos
    ;; Mutant (> 1 1)=false → returns nil
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [2 0]
                                                      :steps-remaining 2}}
                              (land-cell) (land-cell)]])
    (with-redefs [movement/move-unit (mock-move :sidestep)]
      (should= [1 0] (ip/move-current-unit [0 0] 0))))

  (it "uses default 1 for moved-unit steps-remaining (L60 1→0)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [1 0]}}
                              (land-cell)]])
    (with-redefs [movement/move-unit (mock-move :sidestep)]
      (ip/move-current-unit [0 0] 0)
      (should= 0 (get-in (test-utils/read-test-state :game-map) [1 0 :contents :steps-remaining])))))

;; ===== move-current-unit: combat branch (L78-84) =====

(describe "move-current-unit combat"
  (before (reset-all-atoms!))

  (it "returns nil and sets steps to 0 when attacker wins (L82, L84)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [1 0]
                                                      :steps-remaining 2}}
                              {;; After combat, winner occupies the cell
                               :type :land :contents {:type :army :owner :computer}}]])
    (with-redefs [movement/move-unit
                  (fn [from _t cell gm]
                    ;; Simulate combat: attacker wins, moves to new pos
                    (let [attacker (:contents cell)]
                      (sa/update-world! assoc-in (conj from :contents) nil)
                      (sa/update-world! assoc-in [1 0 :contents] attacker)
                      {:result :combat :pos [1 0]}))]
      (let [result (ip/move-current-unit [0 0])]
        (should-be-nil result)
        (should= 0 (get-in (test-utils/read-test-state :game-map) [1 0 :contents :steps-remaining])))))

  (it "returns nil when attacker loses combat (L82 owner check)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [1 0]
                                                      :steps-remaining 2}}
                              {:type :land :contents {:type :army :owner :computer}}]])
    (with-redefs [movement/move-unit
                  (fn [from _t _cell gm]
                    ;; Attacker lost: removed from map, defender stays
                    (sa/update-world! assoc-in (conj from :contents) nil)
                    {:result :combat :pos [1 0]})]
      (should-be-nil (ip/move-current-unit [0 0])))))

;; ===== move-current-unit: woke/docked branches =====

(describe "move-current-unit woke/docked"
  (before (reset-all-atoms!))

  (it "returns pos on :woke result"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [1 0]
                                                      :steps-remaining 2}}
                              (land-cell)]])
    (with-redefs [movement/move-unit
                  (fn [from _t cell gm]
                    (let [unit (:contents cell)]
                      (sa/update-world! update-in (conj from :contents) assoc :mode :awake)
                      {:result :woke :pos from}))]
      (should= [0 0] (ip/move-current-unit [0 0]))))

  (it "returns nil on :docked result"
    (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :player
                                                     :mode :moving :target [1 0]
                                                     :steps-remaining 2 :hits 3}}
                              (sea-cell)]])
    (with-redefs [movement/move-unit
                  (fn [from _t _cell gm]
                    (sa/update-world! assoc-in (conj from :contents) nil)
                    {:result :docked :pos [1 0]})]
      (should-be-nil (ip/move-current-unit [0 0])))))

;; ===== process-player-items-batch: auto-launch-fighter (L111, L113) =====

(describe "process-player-items-batch auto-launch-fighter"
  (before (reset-all-atoms!))

  (it "launches fighter from airport with flight-path (L113)"
    ;; City with awake fighter and flight-path, no unit on city
    (set-test-world! [[{:type :city :city-status :player
                               :flight-path [1 0]
                               :awake-fighters 1 :fighter-count 1}
                              (land-cell)]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (let [launched? (atom false)]
      (with-redefs [container-ops/launch-fighter-from-airport
                    (fn [coords fp]
                      (reset! launched? true)
                      (test-utils/update-test-world! assoc-in [0 0 :awake-fighters] 0)
                      [1 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should @launched?))))

  (it "launches fighter from carrier with flight-path (L111)"
    (set-test-world! [[{:type :sea :contents {:type :carrier :owner :player
                                                     :mode :sentry :hits 8
                                                     :flight-path [1 0]
                                                     :awake-fighters 1 :fighter-count 1}}
                              (sea-cell)]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (let [launched? (atom false)]
      (with-redefs [container-ops/launch-fighter-from-carrier
                    (fn [coords fp]
                      (reset! launched? true)
                      [1 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should @launched?))))

  (it "does not launch fighter without flight-path (L113 when→when-not)"
    (set-test-world! [[{:type :city :city-status :player
                               :awake-fighters 1 :fighter-count 1}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (let [launched? (atom false)]
      (with-redefs [container-ops/launch-fighter-from-airport
                    (fn [_ _] (reset! launched? true) [0 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should-not @launched?)))))

;; ===== process-player-items-batch: auto-disembark-army (L128-L141) =====

(describe "process-player-items-batch auto-disembark-army"
  (before (reset-all-atoms!))

  (it "disembarks army from transport with marching-orders (L128, L129, L130)"
    ;; Transport at [1,1] with marching-orders and awake army, land at [1,0]
    (set-test-world! [[(sea-cell) (sea-cell) (sea-cell)]
                             [{:type :land}
                              {:type :sea :contents {:type :transport :owner :player
                                                     :mode :sentry :hits 3
                                                     :marching-orders [0 0]
                                                     :awake-armies 1 :army-count 1}}
                              (sea-cell)]
                             [(sea-cell) (sea-cell) (sea-cell)]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [disembarked? (atom false)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [tc vc mo]
                      (reset! disembarked? true)
                      vc)
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should @disembarked?))))

  (it "does not disembark when awake-armies key is missing (L129 0→1)"
    ;; Transport with marching-orders but no :awake-armies key
    ;; Default 0: (pos? 0)=false → no disembark
    ;; Mutant default 1: (pos? 1)=true → wrongly tries disembark
    (set-test-world! [[(sea-cell) (sea-cell)]
                             [{:type :land}
                              {:type :sea :contents {:type :transport :owner :player
                                                     :mode :sentry :hits 3
                                                     :marching-orders [0 0]
                                                     :army-count 0}}]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [disembarked? (atom false)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_ _ _] (reset! disembarked? true) [0 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should-not @disembarked?))))

  (it "does not disembark from non-transport (L128 =→not=)"
    ;; Destroyer with marching-orders should not trigger disembark
    (set-test-world! [[{:type :sea :contents {:type :destroyer :owner :player
                                                     :mode :sentry :hits 3
                                                     :marching-orders [0 0]
                                                     :awake-armies 1}}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (let [disembarked? (atom false)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_ _ _] (reset! disembarked? true) [0 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should-not @disembarked?))))

  (it "does not disembark without marching-orders (L130 when→when-not)"
    (set-test-world! [[(sea-cell) (sea-cell)]
                             [{:type :land}
                              {:type :sea :contents {:type :transport :owner :player
                                                     :mode :sentry :hits 3
                                                     :awake-armies 1 :army-count 1}}]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [disembarked? (atom false)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_ _ _] (reset! disembarked? true) [0 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should-not @disembarked?))))

  (it "only targets empty land cells (L138 =→not=)"
    ;; Transport surrounded by sea except one occupied land cell
    (set-test-world! [[(sea-cell) (sea-cell) (sea-cell)]
                             [(sea-cell)
                              {:type :sea :contents {:type :transport :owner :player
                                                     :mode :sentry :hits 3
                                                     :marching-orders [0 0]
                                                     :awake-armies 1 :army-count 1}}
                              (sea-cell)]
                             [(sea-cell) (sea-cell) (sea-cell)]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [disembarked? (atom false)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_ _ _] (reset! disembarked? true) [0 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        ;; No land cells adjacent → should not disembark
        (should-not @disembarked?))))

  (it "requires valid-target to disembark (L141 when→when-not)"
    ;; All adjacent land cells are occupied
    (set-test-world! [[(sea-cell) (sea-cell) (sea-cell)]
                             [{:type :land :contents {:type :army :owner :player :hits 1}}
                              {:type :sea :contents {:type :transport :owner :player
                                                     :mode :sentry :hits 3
                                                     :marching-orders [0 0]
                                                     :awake-armies 1 :army-count 1}}
                              (sea-cell)]
                             [(sea-cell) (sea-cell) (sea-cell)]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [disembarked? (atom false)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_ _ _] (reset! disembarked? true) [0 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should-not @disembarked?))))

  (it "finds land at dx=0 position (L132 dx 0→1)"
    ;; Transport at [1,1], only land at [1,0] (dx=0, dy=-1)
    (set-test-world! [[(sea-cell) (sea-cell) (sea-cell)]
                             [{:type :land}
                              {:type :sea :contents {:type :transport :owner :player
                                                     :mode :sentry :hits 3
                                                     :marching-orders [0 0]
                                                     :awake-armies 1 :army-count 1}}
                              (sea-cell)]
                             [(sea-cell) (sea-cell) (sea-cell)]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [target-pos (atom nil)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_tc vc _mo] (reset! target-pos vc) vc)
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should= [1 0] @target-pos))))

  (it "finds land at dx=1 position (L132 dx 1→0)"
    ;; Transport at [1,1], only land at [2,0] (dx=+1, dy=-1)
    (set-test-world! [[(sea-cell) (sea-cell) (sea-cell)]
                             [(sea-cell)
                              {:type :sea :contents {:type :transport :owner :player
                                                     :mode :sentry :hits 3
                                                     :marching-orders [0 0]
                                                     :awake-armies 1 :army-count 1}}
                              (sea-cell)]
                             [{:type :land} (sea-cell) (sea-cell)]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [target-pos (atom nil)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_tc vc _mo] (reset! target-pos vc) vc)
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should= [2 0] @target-pos))))

  (it "finds land at dy=0 position (L132 dy 0→1)"
    ;; Transport at [1,1], only land at [0,1] (dx=-1, dy=0)
    (set-test-world! [[(sea-cell) {:type :land} (sea-cell)]
                             [(sea-cell)
                              {:type :sea :contents {:type :transport :owner :player
                                                     :mode :sentry :hits 3
                                                     :marching-orders [0 0]
                                                     :awake-armies 1 :army-count 1}}
                              (sea-cell)]
                             [(sea-cell) (sea-cell) (sea-cell)]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [target-pos (atom nil)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_tc vc _mo] (reset! target-pos vc) vc)
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should= [0 1] @target-pos))))

  (it "finds land at dy=1 position (L132 dy 1→0)"
    ;; Transport at [1,1], only land at [0,2] (dx=-1, dy=+1)
    (set-test-world! [[(sea-cell) (sea-cell) {:type :land}]
                             [(sea-cell)
                              {:type :sea :contents {:type :transport :owner :player
                                                     :mode :sentry :hits 3
                                                     :marching-orders [0 0]
                                                     :awake-armies 1 :army-count 1}}
                              (sea-cell)]
                             [(sea-cell) (sea-cell) (sea-cell)]])
    (test-utils/set-test-state! :player-items [[1 1]])
    (let [target-pos (atom nil)]
      (with-redefs [container-ops/disembark-army-with-target
                    (fn [_tc vc _mo] (reset! target-pos vc) vc)
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should= [0 2] @target-pos)))))

;; ===== process-player-items-batch: process-auto-movement (L150) =====

(describe "process-player-items-batch auto-movement"
  (before (reset-all-atoms!))

  (it "keeps item in list when movement returns new coords (L150 if→if-not)"
    ;; Moving unit returns new coords → stays in player-items
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [2 0]
                                                      :steps-remaining 3}}
                              (land-cell) (land-cell)]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (with-redefs [movement/move-unit (mock-move :normal)
                  attention/item-needs-attention? (fn [_] false)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      ;; Item should be consumed (moved then continued), not waiting
      (should-not (test-utils/read-test-state :waiting-for-input))))

  (it "removes item when movement returns nil (L150)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :moving :target [1 0]
                                                      :steps-remaining 1}}
                              (land-cell)]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (with-redefs [movement/move-unit (mock-move :normal)
                  attention/item-needs-attention? (fn [_] false)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      (should (empty? (test-utils/read-test-state :player-items))))))

;; ===== process-player-items-batch: process-one-item satellite (L161, L163) =====

(describe "process-player-items-batch satellite handling"
  (before (reset-all-atoms!))

  (it "skips satellite with target (L161)"
    (set-test-world! [[{:type :land :contents {:type :satellite :owner :player
                                                      :mode :moving :target [5 0]
                                                      :steps-remaining 10}}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (with-redefs [attention/item-needs-attention? (fn [_] false)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      (should (empty? (test-utils/read-test-state :player-items)))))

  (it "does not skip non-satellite unit with target (L161 =→not=)"
    ;; Army with :target key should NOT be treated as satellite
    ;; Mutant (not= :army :satellite)=true → wrongly skips as satellite
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :awake :target [1 0]
                                                      :steps-remaining 1}}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (with-redefs [attention/item-needs-attention? (fn [_] true)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      ;; Army should ask for attention, not be skipped
      (should (test-utils/read-test-state :waiting-for-input))))

  (it "checks auto-launch for non-satellite (L163 when-not→when)"
    ;; Non-satellite unit: auto-launch should be checked
    ;; City with flight-path and awake fighter
    (set-test-world! [[{:type :city :city-status :player
                               :flight-path [1 0]
                               :awake-fighters 1 :fighter-count 1}
                              (land-cell)]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (let [launched? (atom false)]
      (with-redefs [container-ops/launch-fighter-from-airport
                    (fn [_ _]
                      (reset! launched? true)
                      (test-utils/update-test-world! assoc-in [0 0 :awake-fighters] 0)
                      [1 0])
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should @launched?)))))

;; ===== process-player-items-batch: attention/waiting (L180) =====

(describe "process-player-items-batch attention"
  (before (reset-all-atoms!))

  (it "sets waiting-for-input when item needs attention (L180)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode :awake :steps-remaining 1}}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (with-redefs [attention/item-needs-attention? (fn [_] true)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[0 0]] (test-utils/read-test-state :cells-needing-attention)))))

;; ===== process-computer-items (L191, L192, L194, L197, L199, L208-211) =====

(describe "process-computer-items"
  (before (reset-all-atoms!))

  (it "does nothing when computer-items is empty"
    (test-utils/set-test-state! :computer-items [])
    (set-test-world! (make-land-map 5))
    (ip/process-computer-items)
    (should= [] (test-utils/read-test-state :computer-items)))

  (it "processes all items when fewer than 100"
    (set-test-world! (make-land-map 5))
    (test-utils/set-test-state! :computer-items [[0 0] [1 1] [2 2] [3 3] [4 4]])
    (ip/process-computer-items)
    (should= [] (test-utils/read-test-state :computer-items)))

  (it "stops after 100 items (L208, L209)"
    (let [n 5
          coords (for [c (range n) r (range n)] [c r])]
      (set-test-world! (make-land-map n))
      (test-utils/set-test-state! :computer-items (vec (apply concat (repeat 10 coords))))
      (should= 250 (count (test-utils/read-test-state :computer-items)))
      (ip/process-computer-items)
      (should= 150 (count (test-utils/read-test-state :computer-items)))))

  (it "processes computer city production (L191 city-status, L194)"
    (set-test-world! [[{:type :city :city-status :computer}]])
    (test-utils/set-test-state! :computer-items [[0 0]])
    (let [produced? (atom false)]
      (with-redefs [computer-production/process-computer-city
                    (fn [_] (reset! produced? true))]
        (ip/process-computer-items)
        (should @produced?))))

  (it "requeues a computer city after launching a kamikazee fighter from its airport"
    (set-test-world! [[{:type :city :city-status :computer}]])
    (test-utils/set-test-state! :computer-items [[0 0]])
    (let [produced? (atom false)
          launched? (atom false)]
      (with-redefs [computer-production/process-computer-city
                    (fn [_] (reset! produced? true))
                    empire.computer.threat-response/launch-kamikazee-from-airport!
                    (fn [coords]
                      (reset! launched? true)
                      coords)]
        (ip/process-computer-items)
        (should @produced?)
        (should @launched?)
        (should= [[0 0]] (test-utils/read-test-state :computer-items)))))

  (it "does not process non-computer city (L191 =→not=)"
    (set-test-world! [[{:type :city :city-status :player}]])
    (test-utils/set-test-state! :computer-items [[0 0]])
    (let [produced? (atom false)]
      (with-redefs [computer-production/process-computer-city
                    (fn [_] (reset! produced? true))]
        (ip/process-computer-items)
        (should-not @produced?))))

  (it "does not process non-city as city (L191 type =→not=)"
    (set-test-world! [[{:type :land}]])
    (test-utils/set-test-state! :computer-items [[0 0]])
    (let [produced? (atom false)]
      (with-redefs [computer-production/process-computer-city
                    (fn [_] (reset! produced? true))]
        (ip/process-computer-items)
        (should-not @produced?))))

  (it "processes computer unit movement (L192, L197)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :computer
                                                      :mode :awake}}
                              (land-cell)]])
    (test-utils/set-test-state! :computer-items [[0 0]])
    (let [moved? (atom false)]
      (with-redefs [computer/process-computer-unit
                    (fn [_] (reset! moved? true) nil)]
        (ip/process-computer-items)
        (should @moved?))))

  (it "continues processing when computer unit returns new coords (L199)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :computer
                                                      :mode :moving}}
                              {:type :land :contents {:type :army :owner :computer
                                                      :mode :awake}}]])
    (test-utils/set-test-state! :computer-items [[0 0] [1 0]])
    (let [call-count (atom 0)]
      (with-redefs [computer/process-computer-unit
                    (fn [coords]
                      (swap! call-count inc)
                      (when (= coords [0 0]) [0 0]))]
        (ip/process-computer-items)
        ;; First call returns [0 0] (continue), second processes [0 0] again,
        ;; third returns nil, fourth processes [1 0]
        (should (>= @call-count 2)))))

  (it "increments processed counter correctly (L211 inc→dec)"
    ;; With 101 items and inc→dec mutant, loop would never reach 100
    ;; (processed goes 0,-1,-2...). Test that exactly 100 are processed.
    (let [n 5
          coords (for [c (range n) r (range n)] [c r])]
      (set-test-world! (make-land-map n))
      ;; 125 items = 5 repetitions of 25
      (test-utils/set-test-state! :computer-items (vec (apply concat (repeat 5 coords))))
      (should= 125 (count (test-utils/read-test-state :computer-items)))
      (ip/process-computer-items)
      (should= 25 (count (test-utils/read-test-state :computer-items)))))

  )

;; ===== process-player-items-batch =====

(describe "process-player-items-batch"
  (before (reset-all-atoms!))

  (it "stops when player-items is empty"
    (test-utils/set-test-state! :player-items '())
    (set-test-world! (make-land-map 3))
    (ip/process-player-items-batch)
    (should= '() (test-utils/read-test-state :player-items)))

  (it "stops when paused (victory declared)"
    (set-test-world! (make-land-map 3))
    (test-utils/set-test-state! :player-items (list [0 0] [1 1]))
    (test-utils/set-test-state! :paused true)
    (ip/process-player-items-batch)
    (should= 2 (count (test-utils/read-test-state :player-items))))

  (it "stops when waiting-for-input is set"
    (set-test-world! (make-land-map 3))
    (test-utils/set-test-state! :player-items (list [0 0] [1 1]))
    (test-utils/set-test-state! :waiting-for-input true)
    (ip/process-player-items-batch)
    (should= 2 (count (test-utils/read-test-state :player-items))))

  (it "stops after processing 100 items"
    (set-test-world! (make-land-map 5))
    (let [coords (for [c (range 5) r (range 5)] [c r])]
      (test-utils/set-test-state! :player-items (apply list (apply concat (repeat 6 coords)))))
    (should= 150 (count (test-utils/read-test-state :player-items)))
    (ip/process-player-items-batch)
    (should= 50 (count (test-utils/read-test-state :player-items))))

  (it "stops when process-one-item returns :waiting (awake unit needs attention)"
    (let [game-map (make-land-map 3)
          unit {:type :army :owner :player :mode :awake :hits 1}
          game-map (assoc-in game-map [1 1 :contents] unit)]
      (set-test-world! game-map)
      (test-utils/set-test-state! :player-items (list [1 1] [0 0]))
      (ip/process-player-items-batch)
      (should (test-utils/read-test-state :waiting-for-input))
      (should= [[1 1]] (test-utils/read-test-state :cells-needing-attention))))

  (it "does not declare resignation when computer has no cities left"
    (let [game-map (make-land-map 3)]
      (set-test-world! game-map)
      (test-utils/set-test-state! :game-over-check-enabled true)
      (test-utils/set-test-state! :player-items (list [0 0] [1 1]))
      (ip/process-player-items-batch)
      (should-not (test-utils/read-test-state :paused)))))

(describe "satellite-with-target?"
  (it "returns truthy for satellite with target"
    (should (#'empire.game.loop.item-processing/satellite-with-target?
              {:type :satellite :target [5 0]})))

  (it "returns falsy for satellite without target"
    (should-not (#'empire.game.loop.item-processing/satellite-with-target?
                  {:type :satellite})))

  (it "returns falsy for non-satellite with target"
    (should-not (#'empire.game.loop.item-processing/satellite-with-target?
                  {:type :army :target [5 0]})))

  (it "returns falsy for nil unit"
    (should-not (#'empire.game.loop.item-processing/satellite-with-target? nil))))

(describe "process-player-items-batch else branch"
  (before (reset-all-atoms!))

  (it "unit with nil mode falls through to else (process-auto-movement)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                                      :mode nil :hits 1}}]])
    (test-utils/set-test-state! :player-items [[0 0]])
    (with-redefs [attention/item-needs-attention? (fn [_] false)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      ;; nil mode → not in auto-mode set, not needing attention → else branch
      ;; process-auto-movement with nil mode → case returns nil → item removed
      (should (empty? (test-utils/read-test-state :player-items))))))

(describe "process-player-items-batch loop (mutation coverage)"
  (before (reset-all-atoms!))

  (it "processes items counting from 0 (L219 0→1)"
    (let [processed (atom 0)]
      (set-test-world! (make-land-map 15))
      (test-utils/set-test-state! :player-items (vec (for [c (range 10) r (range 10)] [c r])))
      (with-redefs [attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should (empty? (test-utils/read-test-state :player-items))))))

  (it "stops at batch limit of 100 (L224 >=→>)"
    (let [n 5]
      (set-test-world! (make-land-map n))
      (test-utils/set-test-state! :player-items (vec (for [_ (range 6) c (range n) r (range n)] [c r])))
      (should= 150 (count (test-utils/read-test-state :player-items)))
      (with-redefs [attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should= 50 (count (test-utils/read-test-state :player-items))))))

  (it "increments processed counter on :done (L230 inc→dec)"
    (let [call-count (atom 0)]
      (set-test-world! (vec (repeat 110 [(land-cell)])))
      (update-test-world! assoc-in [0 0 :contents]
             {:type :army :owner :player :mode :moving :target [109 0] :steps-remaining 200})
      (test-utils/set-test-state! :player-items [[0 0]])
      (with-redefs [movement/move-unit
                    (fn [from _t cell gm]
                      (swap! call-count inc)
                      (let [unit (:contents cell)
                            [c r] from
                            new-pos [(inc c) r]]
                        (sa/update-world! assoc-in (conj from :contents) nil)
                        (sa/update-world! assoc-in (conj new-pos :contents) unit)
                        {:result :normal :pos new-pos}))
                    attention/item-needs-attention? (fn [_] false)
                    attention/set-attention-message (fn [_])]
        (ip/process-player-items-batch)
        (should (<= @call-count 100)))))

  (it "does not pause when no city conquest occurred mid-batch"
    (test-utils/set-test-state! :game-over-check-enabled true)
    (set-test-world! [[{:type :land} {:type :land}]])
    (test-utils/set-test-state! :player-items [[0 0] [1 0]])
    (with-redefs [attention/item-needs-attention? (fn [_] false)
                  attention/set-attention-message (fn [_])]
      (ip/process-player-items-batch)
      (should-not (test-utils/read-test-state :paused)))))

(run-specs)
