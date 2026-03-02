;; mutation-tested: no
(ns empire.debug.profile
  "Runtime profiler for targeted invasion analysis.
   Starts on demand, records named timing metrics, writes EDN snapshot,
   and can request app auto-exit when profiling window completes."
  (:require #?(:clj [clojure.java.io :as io])))

(def ^:private profile-round-budget 10)
(def ^:private profiling-enabled? false)

(def ^:private profiling-stats (atom {}))

(def ^:private profiling-session
  (atom {:status :idle
         :start-round nil
         :rounds-recorded 0
         :output-path nil
         :exit-requested? false}))

(defn- now-ns
  []
  #?(:clj (System/nanoTime)
     :cljs (long (* 1000000 (.now js/Date)))))

(defn active?
  []
  (and profiling-enabled?
       (= :running (:status @profiling-session))))

(defn begin
  [metric]
  (when (active?)
    [metric (now-ns)]))

(defn end!
  [start-token]
  (when-let [[metric started-ns] start-token]
    (when (active?)
      (let [elapsed-ns (- (now-ns) started-ns)]
        (swap! profiling-stats update metric
               (fn [m]
                 {:calls (inc (:calls m 0))
                  :total-ns (+ (:total-ns m 0) elapsed-ns)
                  :max-ns (max (:max-ns m 0) elapsed-ns)}))))))

(defn profile-snapshot
  []
  @profiling-stats)

(defn reset-profile!
  []
  (reset! profiling-stats {})
  (swap! profiling-session assoc
         :start-round nil
         :rounds-recorded 0
         :output-path nil
         :exit-requested? false))

(defn start!
  [round-number]
  (when (and profiling-enabled?
             (= :idle (:status @profiling-session)))
    (reset-profile!)
    (swap! profiling-session assoc :status :running :start-round round-number)))

(defn write!
  "Writes current profiling snapshot to disk as EDN and returns absolute file path.
   Default output directory is `saves`."
  ([] (write! "saves"))
  ([dir-path]
   #?(:clj
      (let [dir (io/file dir-path)
            _ (.mkdirs dir)
            filename (format "profile-%d.edn" (System/currentTimeMillis))
            out-file (io/file dir filename)
            payload {:written-at-ms (System/currentTimeMillis)
                     :profiling-session @profiling-session
                     :snapshot (profile-snapshot)}]
        (spit out-file (pr-str payload))
        (.getAbsolutePath out-file))
      :cljs nil)))

(defn tick-round!
  "Advances profiling session by current round number.
   When 10 rounds elapse from start, writes profile and requests app exit."
  [round-number]
  (when (= :running (:status @profiling-session))
    (let [start-round (:start-round @profiling-session)
          elapsed (max 0 (- (or round-number 0) (or start-round round-number 0)))]
      (swap! profiling-session assoc :rounds-recorded elapsed)
      (when (>= elapsed profile-round-budget)
        (let [path (write!)]
          (swap! profiling-session assoc
                 :status :completed
                 :output-path path
                 :exit-requested? true)
          path)))))

(defn finalize!
  "Writes profile immediately when session is running and returns output path."
  []
  (case (:status @profiling-session)
    :running (let [path (write!)]
               (swap! profiling-session assoc :status :completed :output-path path)
               path)
    :completed (:output-path @profiling-session)
    nil))

(defn consume-exit-request!
  []
  (let [requested? (:exit-requested? @profiling-session)]
    (when requested?
      (swap! profiling-session assoc :exit-requested? false))
    requested?))
