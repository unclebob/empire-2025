(ns empire.debug.profile-spec
  (:require [speclj.core :refer :all]
            [empire.debug.profile :as profile]))

(defn- reset-profiler-state!
  []
  (reset! @#'profile/profiling-stats {})
  (reset! @#'profile/profiling-session
          {:status :idle
           :start-round nil
           :rounds-recorded 0
           :output-path nil
           :exit-requested? false}))

(describe "tick-round!"
  (before (reset-profiler-state!))

  (it "returns nil when profiler is not running"
    (should-be-nil (profile/tick-round! 3))
    (should= :idle (:status @@#'profile/profiling-session)))

  (it "updates rounds-recorded while under budget"
    (swap! @#'profile/profiling-session assoc :status :running :start-round 5)
    (should-be-nil (profile/tick-round! 8))
    (should= 3 (:rounds-recorded @@#'profile/profiling-session))
    (should= :running (:status @@#'profile/profiling-session)))

  (it "finalizes session when round budget is reached"
    (swap! @#'profile/profiling-session assoc :status :running :start-round 10)
    (with-redefs [profile/write! (fn [] "/tmp/profile.edn")]
      (should= "/tmp/profile.edn" (profile/tick-round! 20)))
    (should= :completed (:status @@#'profile/profiling-session))
    (should= "/tmp/profile.edn" (:output-path @@#'profile/profiling-session))
    (should= true (:exit-requested? @@#'profile/profiling-session))))

(describe "finalize!"
  (before (reset-profiler-state!))

  (it "writes and marks completed when running"
    (swap! @#'profile/profiling-session assoc :status :running)
    (with-redefs [profile/write! (fn [] "/tmp/final.edn")]
      (should= "/tmp/final.edn" (profile/finalize!)))
    (should= :completed (:status @@#'profile/profiling-session))
    (should= "/tmp/final.edn" (:output-path @@#'profile/profiling-session)))

  (it "returns existing output-path when already completed"
    (swap! @#'profile/profiling-session assoc :status :completed :output-path "/tmp/done.edn")
    (should= "/tmp/done.edn" (profile/finalize!)))

  (it "returns nil when profile is idle"
    (should-be-nil (profile/finalize!))))

(describe "consume-exit-request!"
  (before (reset-profiler-state!))

  (it "returns true once and clears exit request flag"
    (swap! @#'profile/profiling-session assoc :exit-requested? true)
    (should= true (profile/consume-exit-request!))
    (should= false (profile/consume-exit-request!))))

(describe "start!"
  (before (reset-profiler-state!))

  (it "starts a fresh session when enabled and idle"
    (alter-var-root #'profile/profiling-enabled? (constantly true))
    (try
      (profile/start! 7)
      (should= :running (:status @@#'profile/profiling-session))
      (should= 7 (:start-round @@#'profile/profiling-session))
      (finally
        (alter-var-root #'profile/profiling-enabled? (constantly false)))))

  (it "does nothing when profiler is disabled"
    (profile/start! 4)
    (should= :idle (:status @@#'profile/profiling-session))))

(describe "begin/end!"
  (before (reset-profiler-state!))

  (it "records timing stats when active"
    (alter-var-root #'profile/profiling-enabled? (constantly true))
    (try
      (swap! @#'profile/profiling-session assoc :status :running)
      (with-redefs [profile/now-ns (let [ticks (atom [100 175])]
                                     (fn []
                                       (let [v (first @ticks)]
                                         (swap! ticks rest)
                                         v)))]
        (let [token (profile/begin :pathfind)]
          (profile/end! token)))
      (should= {:calls 1 :total-ns 75 :max-ns 75}
               (get @@#'profile/profiling-stats :pathfind))
      (finally
        (alter-var-root #'profile/profiling-enabled? (constantly false)))))

  (it "ignores nil start token"
    (profile/end! nil)
    (should= {} @@#'profile/profiling-stats)))
