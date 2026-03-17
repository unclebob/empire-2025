(ns empire.computer.transport-decisions-spec
  (:require [empire.computer.transport-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "load-for-invasion-action"
  (it "unloads in unload zone"
    (should= :unload (decisions/load-for-invasion-action {:has-armies? true :in-unload-zone? true})))

  (it "sails when timed out with armies"
    (should= :sail (decisions/load-for-invasion-action {:has-armies? true :timed-out? true})))

  (it "sails when nearby unloadable land is found"
    (should= :sail (decisions/load-for-invasion-action {:has-armies? true :nearby-unloadable-land? true})))

  (it "reverts loading when empty and timed out"
    (should= :revert-loading (decisions/load-for-invasion-action {:has-armies? false :timed-out? true})))

  (it "does nothing when still loading without armies"
    (should-be-nil (decisions/load-for-invasion-action {:has-armies? false :timed-out? false}))))

(describe "loading-mission-action"
  (it "starts sailing when full"
    (should= :start-sailing (decisions/loading-mission-action {:should-start-sailing? true})))

  (it "handles stale loading"
    (should= :handle-stale (decisions/loading-mission-action {:loading-stale? true})))

  (it "otherwise crawls"
    (should= :crawl (decisions/loading-mission-action {}))))

(describe "transport-process-action"
  (it "ignores non-transports"
    (should= :ignore (decisions/transport-process-action {:transport? false :computer-owned? true})))

  (it "chooses random walk"
    (should= :random-walk (decisions/transport-process-action {:transport? true :computer-owned? true :random-walk? true})))

  (it "chooses active transport processing"
    (should= :active (decisions/transport-process-action {:transport? true :computer-owned? true :random-walk? false}))))
