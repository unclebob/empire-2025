(ns empire.computer.transport-mission-handlers-spec
  (:require [speclj.core :refer :all]
            [empire.computer.transport-mission-handlers :as mh]))

(describe "transport mission-handlers"
  (context "process-land-locked-mission coverage"
    (it "handles missing unit at position without attempting crawl"
      (let [crawl-called (atom false)
            deps {:current-world (fn [] [[{:type :sea}]])
                  :update-game-map! (fn [& _] nil)
                  :move-unit-to (fn [& _] true)
                  :retreat-step-from-shore (fn [& _] [0 1])
                  :deep-water? (fn [& _] false)
                  :process-unloading-crawl (fn [& _] (reset! crawl-called true) [0 1])
                  :try-opportunistic-unload-any-land (fn [& _] false)}]
        (should-be-nil (mh/process-land-locked-mission deps [0 0] #{}))
        (should= false @crawl-called)))))
