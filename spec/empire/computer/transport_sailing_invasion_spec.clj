(ns empire.computer.transport-sailing-invasion-spec
  (:require [empire.computer.transport-sailing-invasion :as sut]
            [speclj.core :refer :all]))

(describe "transport sailing invasion"
  (it "does nothing when threat handling already consumed the turn"
    (with-redefs [empire.state.api/current-world (fn [] [[{:contents {}}]])
                  empire.state.api/read-state (fn [k]
                                                (when (= :computer-map k)
                                                  [[{:contents {}}]]))
                  empire.computer.transport-sailing-invasion/handle-invasion-threat-near-target! (fn [& _] true)
                  empire.computer.transport-sailing-decisions/invading-action (fn [_] {:action :threat})]
      (should-be-nil (sut/process-invading-mission [0 0]))))

  (it "falls back to blocked-path handling when a stored path cannot advance"
    (let [blocked-calls (atom 0)]
      (with-redefs [empire.state.api/current-world (fn [] [[{:contents {:invasion-path [[1 0]]
                                                                         :invasion-target [2 0]}}]])
                    empire.state.api/read-state (fn [k]
                                                  (when (= :computer-map k)
                                                    [[{:contents {:invasion-path [[1 0]]
                                                                  :invasion-target [2 0]}}]]))
                    empire.computer.transport-sailing-invasion/handle-invasion-threat-near-target! (fn [& _] false)
                    empire.computer.transport-sailing-decisions/invading-action (fn [_] {:action :path})
                    empire.computer.transport-sailing-invasion/continue-invading-via-path! (fn [& _] :blocked)
                    empire.computer.transport-sailing-invasion/handle-blocked-invading-path! (fn [& _]
                                                                                                (swap! blocked-calls inc))]
        (sut/process-invading-mission [0 0])
        (should= 1 @blocked-calls))))

  (it "uses crawl movement when there is no invasion path"
    (let [crawl-calls (atom [])]
      (with-redefs [empire.state.api/current-world (fn [] [[{:contents {:major-invasion-target [3 3]}}]])
                    empire.state.api/read-state (fn [k]
                                                  (when (= :computer-map k)
                                                    [[{:contents {:major-invasion-target [3 3]}}]]))
                    empire.computer.transport-sailing-invasion/handle-invasion-threat-near-target! (fn [& _] false)
                    empire.computer.transport-sailing-decisions/invading-action (fn [_] {:action :crawl})
                    empire.computer.transport-sailing-invasion/continue-invading-without-path! (fn [pos target invading-step]
                                                                                                  (swap! crawl-calls conj [pos target (fn? invading-step)])
                                                                                                  :crawled)]
        (should= :crawled (sut/process-invading-mission [0 0]))
        (should= [[[0 0] [3 3] true]]
                 @crawl-calls)))))
