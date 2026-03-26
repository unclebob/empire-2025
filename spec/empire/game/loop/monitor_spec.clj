(ns empire.game.loop.monitor-spec
  (:require [empire.game.loop.monitor :as monitor]
            [speclj.core :refer :all]))

(describe "create-monitor"
  (it "returns a watching monitor with the given threshold"
    (let [m (monitor/create-monitor 500)]
      (should= :watching (:status m))
      (should= 500 (:threshold-ms m))
      (should= [] (:rounds m)))))

(describe "check-round"
  (it "stays watching when round is under threshold"
    (let [m (monitor/create-monitor 500)
          m2 (monitor/check-round m 1 400)]
      (should= :watching (:status m2))
      (should= [] (:rounds m2))))

  (it "transitions to recording when round exceeds threshold"
    (let [m (monitor/create-monitor 500)
          m2 (monitor/check-round m 1 600)]
      (should= :recording (:status m2))
      (should= 20 (:rounds-remaining m2))))

  (it "records phase data during recording"
    (let [m (-> (monitor/create-monitor 500)
                (monitor/check-round 1 600))
          m2 (monitor/record-round m 2 {:phases [[:a 10] [:b 20]] :total-ms 30})]
      (should= 19 (:rounds-remaining m2))
      (should= 1 (count (:rounds m2)))))

  (it "transitions to done after 20 recorded rounds"
    (let [m (-> (monitor/create-monitor 500)
                (monitor/check-round 1 600))
          m2 (reduce (fn [acc i]
                       (monitor/record-round acc (+ 2 i)
                                             {:phases [[:a 10]] :total-ms 10}))
                     m (range 20))]
      (should= :done (:status m2))
      (should= 20 (count (:rounds m2))))))

(describe "record-round"
  (it "accumulates round data with round number"
    (let [m (-> (monitor/create-monitor 500)
                (monitor/check-round 1 600))
          m2 (monitor/record-round m 2 {:phases [[:setup 15] [:items 25]] :total-ms 40})]
      (should= [{:round-number 2 :phases [[:setup 15] [:items 25]] :total-ms 40}]
               (:rounds m2)))))

(describe "time-phase"
  (it "returns phase name and elapsed ms"
    (let [[phase-name elapsed] (monitor/time-phase :test-phase (do (Thread/sleep 10) :ok))]
      (should= :test-phase phase-name)
      (should (>= elapsed 8)))))

(describe "recording?"
  (it "returns true when status is recording"
    (should (monitor/recording?
             (monitor/check-round (monitor/create-monitor 100) 1 200))))

  (it "returns false when status is watching"
    (should-not (monitor/recording? (monitor/create-monitor 100))))

  (it "returns false when status is done"
    (let [m (-> (monitor/create-monitor 100)
                (monitor/check-round 1 200))
          m2 (reduce (fn [acc i]
                       (monitor/record-round acc (+ 2 i)
                                             {:phases [[:a 1]] :total-ms 1}))
                     m (range 20))]
      (should-not (monitor/recording? m2)))))

(describe "done?"
  (it "returns true when status is done"
    (let [m (-> (monitor/create-monitor 100)
                (monitor/check-round 1 200))
          m2 (reduce (fn [acc i]
                       (monitor/record-round acc (+ 2 i)
                                             {:phases [[:a 1]] :total-ms 1}))
                     m (range 20))]
      (should (monitor/done? m2))))

  (it "returns false when still recording"
    (should-not (monitor/done?
                 (monitor/check-round (monitor/create-monitor 100) 1 200)))))

(describe "format-report"
  (it "produces a string report from recorded data"
    (let [m (-> (monitor/create-monitor 500)
                (monitor/check-round 1 600))
          m2 (-> m
                 (monitor/record-round 2 {:phases [[:phase-a 100] [:phase-b 200]] :total-ms 300})
                 (monitor/record-round 3 {:phases [[:phase-a 150] [:phase-b 250]] :total-ms 400}))
          report (monitor/format-report m2)]
      (should-contain "phase-a" report)
      (should-contain "phase-b" report)
      (should-contain "total" (clojure.string/lower-case report)))))
