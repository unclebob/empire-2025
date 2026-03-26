(ns empire.game.loop.monitor
  (:require [clojure.string :as str]))

(def ^:private monitoring-rounds 20)

(defn now-ms [] (/ (System/nanoTime) 1000000.0))

(defn create-monitor [threshold-ms]
  {:status :watching
   :threshold-ms threshold-ms
   :rounds []})

(defn check-round [monitor round-number elapsed-ms]
  (if (and (= :watching (:status monitor))
           (> elapsed-ms (:threshold-ms monitor)))
    (assoc monitor
           :status :recording
           :trigger-round round-number
           :trigger-ms elapsed-ms
           :rounds-remaining monitoring-rounds)
    monitor))

(defn record-round [monitor round-number round-data]
  (let [entry (assoc round-data :round-number round-number)
        rounds (conj (:rounds monitor) entry)
        remaining (dec (:rounds-remaining monitor))]
    (assoc monitor
           :rounds rounds
           :rounds-remaining remaining
           :status (if (zero? remaining) :done :recording))))

(defmacro time-phase [phase-name & body]
  `(let [start# (System/nanoTime)
         _# (do ~@body)
         elapsed# (/ (- (System/nanoTime) start#) 1000000.0)]
     [~phase-name elapsed#]))

(defmacro time-phase-returning [phase-name & body]
  `(let [start# (System/nanoTime)
         result# (do ~@body)
         elapsed# (/ (- (System/nanoTime) start#) 1000000.0)]
     [[~phase-name elapsed#] result#]))

(def ^:dynamic *phase-sink* nil)

(defn recording? [monitor] (= :recording (:status monitor)))
(defn done? [monitor] (= :done (:status monitor)))

(defn- phase-stats [rounds]
  (let [all-phases (mapcat :phases rounds)
        grouped (group-by first all-phases)]
    (for [[phase-name timings] (sort-by first grouped)
          :let [ms-vals (map second timings)
                total (reduce + ms-vals)
                cnt (count ms-vals)
                avg (double (/ total cnt))
                mx (apply max ms-vals)]]
      {:phase phase-name :avg avg :max mx :total total :count cnt})))

(defn format-report [monitor]
  (let [rounds (:rounds monitor)
        totals (map :total-ms rounds)
        avg-total (double (/ (reduce + totals) (count totals)))
        max-total (apply max totals)
        stats (phase-stats rounds)]
    (str (format "\n=== Performance Monitor Report ===\n")
         (format "Triggered at round %d (%.0f ms, threshold %d ms)\n"
                 (:trigger-round monitor)
                 (double (:trigger-ms monitor))
                 (:threshold-ms monitor))
         (format "Monitored %d rounds\n\n" (count rounds))
         (format "%-40s %8s %8s %8s\n" "Phase" "Avg ms" "Max ms" "Total ms")
         (format "%-40s %8s %8s %8s\n" (apply str (repeat 40 "-")) "--------" "--------" "--------")
         (str/join ""
                   (for [{:keys [phase avg max total]} stats]
                     (format "%-40s %8.1f %8.0f %8.0f\n" (name phase) avg (double max) (double total))))
         (format "%-40s %8s %8s %8s\n" (apply str (repeat 40 "-")) "--------" "--------" "--------")
         (format "%-40s %8.1f %8.0f %8.0f\n" "TOTAL" avg-total (double max-total) (double (reduce + totals))))))
