(ns empire.computer.transport.sailing-invasion-spec
  (:require [empire.computer.transport.sailing-invasion :as sut]
            [speclj.core :refer :all]))

(describe "transport sailing invasion"
  (it "does nothing when threat handling already consumed the turn"
    (with-redefs [empire.state.api/current-world (fn [] [[{:contents {}}]])
                  empire.state.api/read-state (fn [k]
                                                (when (= :computer-map k)
                                                  [[{:contents {}}]]))
                  empire.computer.transport.sailing-invasion/handle-invasion-threat-near-target! (fn [& _] true)
                  empire.computer.transport.sailing-decisions/invading-action (fn [_] {:action :threat})]
      (should-be-nil (sut/process-invading-mission [0 0]))))

  (it "falls back to blocked-path handling when a stored path cannot advance"
    (let [blocked-calls (atom 0)]
      (with-redefs [empire.state.api/current-world (fn [] [[{:contents {:invasion-path [[1 0]]
                                                                         :invasion-target [2 0]}}]])
                    empire.state.api/read-state (fn [k]
                                                  (when (= :computer-map k)
                                                    [[{:contents {:invasion-path [[1 0]]
                                                                  :invasion-target [2 0]}}]]))
                    empire.computer.transport.sailing-invasion/handle-invasion-threat-near-target! (fn [& _] false)
                    empire.computer.transport.sailing-decisions/invading-action (fn [_] {:action :path})
                    empire.computer.transport.sailing-invasion/continue-invading-via-path! (fn [& _] :blocked)
                    empire.computer.transport.sailing-invasion/handle-blocked-invading-path! (fn [& _]
                                                                                                (swap! blocked-calls inc))]
        (sut/process-invading-mission [0 0])
        (should= 1 @blocked-calls))))

  (it "uses crawl movement when there is no invasion path"
    (let [crawl-calls (atom [])]
      (with-redefs [empire.state.api/current-world (fn [] [[{:contents {:major-invasion-target [3 3]}}]])
                    empire.state.api/read-state (fn [k]
                                                  (when (= :computer-map k)
                                                    [[{:contents {:major-invasion-target [3 3]}}]]))
                    empire.computer.transport.sailing-invasion/handle-invasion-threat-near-target! (fn [& _] false)
                    empire.computer.transport.sailing-decisions/invading-action (fn [_] {:action :crawl})
                    empire.computer.transport.sailing-invasion/continue-invading-without-path! (fn [pos target invading-step]
                                                                                                  (swap! crawl-calls conj [pos target (fn? invading-step)])
                                                                                                  :crawled)]
        (should= :crawled (sut/process-invading-mission [0 0]))
        (should= [[[0 0] [3 3] true]]
                 @crawl-calls))))

  (it "switches to unloading at the current position when a nearby threat prevents retreat"
    (let [missions (atom [])]
      (with-redefs [empire.computer.transport.sailing-support/enemy-ship-near-target? (fn [& _] true)
                    empire.computer.transport.sailing-invasion/retreat-away-from-target! (fn [& _] nil)
                    empire.computer.transport.core/set-transport-mission (fn [pos mission]
                                                                            (swap! missions conj [pos mission]))]
        (should (@#'sut/handle-invasion-threat-near-target! [0 0] [0 0]))
        (should= [[[0 0] :unloading]] @missions))))

  (it "starts a random walk when crawl follow-up asks for one"
    (let [world (atom [[{:contents {:type :transport :transport-mission :invading}}]])
          syncs (atom [])]
      (with-redefs [empire.state.api/update-world! (fn [f & args]
                                                     (apply swap! world f args))
                    empire.game-mechanics.visibility/sync-ai-unit-to-computer-map! (fn [pos]
                                                                                     (swap! syncs conj pos))]
        (@#'sut/apply-crawl-follow-up! [0 0] [0 0] {:start-random-walk? true})
        (should= 5 (get-in @world [0 0 :contents :oscillation-random-walk-rounds-left]))
        (should= [[0 0]] @syncs))))

  (it "uses the larger unload radius when an enemy ship is near the invasion target"
    (with-redefs [empire.computer.transport.sailing-support/enemy-ship-near-target? (constantly true)
                  empire.computer.transport.unloading/has-nearby-unloadable-land? (constantly false)]
      (should (@#'sut/unload-zone? [3 0] [0 0] nil)))
    (with-redefs [empire.computer.transport.sailing-support/enemy-ship-near-target? (constantly false)
                  empire.computer.transport.unloading/has-nearby-unloadable-land? (constantly false)]
      (should-not (@#'sut/unload-zone? [3 0] [0 0] nil))))

  (it "starts a random walk when a blocked invasion path cannot sidestep"
    (let [world (atom [[{:contents {:type :transport :transport-mission :invading}}]])
          syncs (atom [])]
      (with-redefs [empire.computer.transport.sailing-invasion/invading-step (fn [& _] nil)
                    empire.computer.transport.sailing-decisions/blocked-path-follow-up
                    (fn [_] {:start-random-walk? true})
                    empire.state.api/update-world! (fn [f & args]
                                                     (apply swap! world f args))
                    empire.game-mechanics.visibility/sync-ai-unit-to-computer-map! (fn [pos]
                                                                                     (swap! syncs conj pos))]
        (@#'sut/handle-blocked-invading-path! [0 0] [3 0])
        (should= 5 (get-in @world [0 0 :contents :oscillation-random-walk-rounds-left]))
        (should= [[0 0]] @syncs)))))
