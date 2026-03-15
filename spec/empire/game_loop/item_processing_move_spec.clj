(ns empire.game-loop-item-processing-move-spec
  (:require [empire.config.core :as config]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game-mechanics.movement.api :as movement]
            [empire.game-mechanics.movement.coastline :as coastline]
            [empire.game-mechanics.movement.explore :as explore]
            [empire.game.loop.item-processing :as ip]
            [empire.player.attention :as attention]
            [empire.state.api :as sa]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(defn- land-cell [] {:type :land})
(defn- sea-cell [] {:type :sea})

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
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                               :mode :moving :target [2 0]
                                               :steps-remaining 2}}
                       (land-cell) (land-cell)]])
    (with-redefs [movement/move-unit (mock-move :normal)]
      (should= [1 0] (ip/move-current-unit [0 0]))))

  (it "uses default 1 for moved-unit steps-remaining (L72 1→0)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                               :mode :moving :target [1 0]}}
                       (land-cell)]])
    (with-redefs [movement/move-unit (mock-move :normal)]
      (ip/move-current-unit [0 0])
      (should= 0 (get-in (test-utils/read-test-state :game-map) [1 0 :contents :steps-remaining])))))

(describe "player visibility refresh after auto-movement"
  (before (reset-all-atoms!))

  (it "reveals a newly spotted enemy on the player map in the same processing pass"
    (set-test-world! (build-test-map ["A#a"]))
    (test-utils/set-test-unit (test-utils/game-map-atom) "A"
                              :mode :moving
                              :target [1 0]
                              :steps-remaining 1)
    (test-utils/set-test-player-map! (test-utils/make-initial-test-map 1 3 nil))
    (test-utils/set-test-state! :player-items [[0 0]])
    (ip/process-player-items-batch)
    (should= :enemy-spotted
             (get-in (test-utils/read-test-state :game-map) [1 0 :contents :reason]))
    (should= :computer
             (get-in (test-utils/read-test-state :player-map) [2 0 :contents :owner]))))

(describe "move-current-unit sidestep"
  (before (reset-all-atoms!))

  (it "decrements steps on sidestep (L59, L60 dec)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                               :mode :moving :target [2 0]
                                               :steps-remaining 3}}
                       (land-cell) (land-cell)]])
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
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                               :mode :moving :target [3 0]
                                               :steps-remaining 4}}
                       (land-cell) (land-cell) (land-cell)]])
    (let [call-count (atom 0)]
      (with-redefs [movement/move-unit
                    (fn [from _t cell gm]
                      (swap! call-count inc)
                      ((mock-move :sidestep) from _t cell gm))]
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

(describe "move-current-unit combat"
  (before (reset-all-atoms!))

  (it "returns nil and sets steps to 0 when attacker wins (L82, L84)"
    (set-test-world! [[{:type :land :contents {:type :army :owner :player
                                               :mode :moving :target [1 0]
                                               :steps-remaining 2}}
                       {:type :land :contents {:type :army :owner :computer}}]])
    (with-redefs [movement/move-unit
                  (fn [from _t cell gm]
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
                    (sa/update-world! assoc-in (conj from :contents) nil)
                    {:result :combat :pos [1 0]})]
      (should-be-nil (ip/move-current-unit [0 0])))))

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
