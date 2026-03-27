(ns empire.ui.quil.headless
  (:require [empire.state.api :as sa]
            [empire.computer.threat-response.probe :as invasion-probe]
            [empire.config.core :as config]
            [empire.game.loop.core :as game-loop]
            [empire.game.initialization :as init]
            [empire.game-mechanics.debug.logging :as debug-logging]
            [empire.game-mechanics.debug.dump :as debug-dump]))

(defonce ^:private debug-dump-hook-installed? (atom false))

(defn maybe-write-debug-dump-on-exit!
  []
  (when (and (sa/read-state :debug-dump-on-exit?)
             (not (sa/read-state :debug-dump-written?))
             (seq (sa/read-state :map-size)))
    (let [filename (debug-dump/write-full-dump!)]
      (sa/write-state! :debug-dump-written? true)
      (println (str "Debug log written: " filename))
      filename)))

(defn install-debug-dump-shutdown-hook!
  []
  (when (and (sa/read-state :debug-dump-on-exit?)
             (compare-and-set! debug-dump-hook-installed? false true))
    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread. ^Runnable (fn [] (maybe-write-debug-dump-on-exit!))))))

(defn install-seeded-random!
  []
  (when-let [seed (sa/read-state :random-seed)]
    (let [rng (java.util.Random. seed)]
      (alter-var-root #'clojure.core/rand
                      (constantly (fn
                                   ([] (.nextDouble rng))
                                   ([n] (* n (.nextDouble rng))))))
      (alter-var-root #'clojure.core/rand-int
                      (constantly (fn [n] (.nextInt rng (int n))))))))

(defn initialize-map!
  []
  (let [num-cities (:number-of-cities (sa/read-state :map-size-constants) config/number-of-cities)]
    (init/make-initial-map (sa/read-state :map-size)
                           config/smooth-count
                           config/land-fraction
                           num-cities
                           config/min-city-distance)))

(defn- explored-percentage
  [computer-map]
  (let [cells (for [row computer-map
                    cell row]
                cell)
        total (count cells)
        explored (count (filter #(and % (not= :unexplored (:type %))) cells))]
    (if (pos? total)
      (* 100.0 (/ explored total))
      0.0)))

(defn- headless-progress-line
  [round-number computer-map major-invasion-state]
  (format "Round %d explored %.1f%% invasion %s"
          round-number
          (double (explored-percentage computer-map))
          (if (:active? major-invasion-state) "yes" "no")))

(defn- print-headless-progress!
  [round-number]
  (println (headless-progress-line round-number
                                   (sa/read-state :computer-map)
                                   (sa/read-state :major-invasion-state))))

(defn- maybe-print-final-headless-progress!
  [last-reported-round]
  (let [round-number (sa/read-state :round-number)]
    (when (> round-number last-reported-round)
      (print-headless-progress! round-number))))

(defn- finish-headless-run!
  [last-reported-round]
  (maybe-print-final-headless-progress! last-reported-round)
  (sa/write-state! :headless-mode? false)
  (sa/write-state! :headless-stop-on-major-invasion? false)
  (maybe-write-debug-dump-on-exit!))

(defn- headless-done?
  [round-number headless-rounds]
  (or (>= round-number headless-rounds)
      (sa/read-state :paused)))

(defn- report-progress
  [next-round last-reported-round]
  (if (and (> next-round last-reported-round)
           (zero? (mod next-round 20)))
    (do (print-headless-progress! next-round)
        next-round)
    last-reported-round))

(defn- run-headless-loop!
  [headless-rounds]
  (loop [last-reported-round 0]
    (if (headless-done? (sa/read-state :round-number) headless-rounds)
      (finish-headless-run! last-reported-round)
      (do
        (game-loop/update-player-map)
        (game-loop/update-computer-map)
        (game-loop/advance-game-batch)
        (recur (report-progress (sa/read-state :round-number)
                                last-reported-round))))))

(defn run-headless!
  [{:keys [headless-rounds]}]
  (install-seeded-random!)
  (initialize-map!)
  (debug-logging/log-game-map! 0)
  (invasion-probe/clear-log!)
  (sa/write-state! :headless-mode? true)
  (sa/write-state! :headless-stop-on-major-invasion? false)
  (sa/write-state! :major-invasion-probe-hit? false)
  (run-headless-loop! headless-rounds))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T11:54:43.430454-05:00", :module-hash "363561284", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "1234937160"} {:id "form/1/defonce", :kind "defonce", :line 10, :end-line 10, :hash "-760890569"} {:id "defn/maybe-write-debug-dump-on-exit!", :kind "defn", :line 12, :end-line 20, :hash "1426778471"} {:id "defn/install-debug-dump-shutdown-hook!", :kind "defn", :line 22, :end-line 28, :hash "967178954"} {:id "defn/install-seeded-random!", :kind "defn", :line 30, :end-line 39, :hash "1113929430"} {:id "defn/initialize-map!", :kind "defn", :line 41, :end-line 48, :hash "-1416015415"} {:id "defn-/explored-percentage", :kind "defn-", :line 50, :end-line 59, :hash "-837917730"} {:id "defn-/headless-progress-line", :kind "defn-", :line 61, :end-line 66, :hash "1561443472"} {:id "defn-/print-headless-progress!", :kind "defn-", :line 68, :end-line 72, :hash "1904600067"} {:id "defn-/maybe-print-final-headless-progress!", :kind "defn-", :line 74, :end-line 78, :hash "489370345"} {:id "defn-/finish-headless-run!", :kind "defn-", :line 80, :end-line 85, :hash "2020520941"} {:id "defn-/headless-done?", :kind "defn-", :line 87, :end-line 90, :hash "-79163376"} {:id "defn-/report-progress", :kind "defn-", :line 92, :end-line 98, :hash "-1728153657"} {:id "defn-/run-headless-loop!", :kind "defn-", :line 100, :end-line 110, :hash "-1933881243"} {:id "defn/run-headless!", :kind "defn", :line 112, :end-line 121, :hash "770737425"}]}
;; clj-mutate-manifest-end
