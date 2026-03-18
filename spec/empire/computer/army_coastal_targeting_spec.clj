(ns empire.computer.army-coastal-targeting-spec
  (:require [empire.computer.army.coastal :as coastal]
            [empire.computer.army.movement :as movement]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [speclj.core :refer :all]))

(defn- run-local-empty-coast-target
  [neighbors target-pos]
  (with-redefs [movement/get-passable-neighbors
                (fn [pos _country-id] (get neighbors pos []))
                empire.computer.army.coastal/empty-coastal-cell?
                (fn [pos _country-id] (= pos target-pos))]
    (@#'coastal/local-empty-coast-target [0 0] 1)))

(defn- run-fill-coastal-cell
  [{:keys [should-sentry? coastal-target can-settle? queue-target wake-count current-world]}]
  (let [updates (atom [])]
    (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] should-sentry?)
                  empire.computer.army.coastal/find-nearest-unoccupied-coastal-cell (fn [_ _] coastal-target)
                  coastal/can-settle-here? (fn [_ _] can-settle?)
                  empire.computer.army.coastal/find-nearest-cell-close-to-coast (fn [_ _] queue-target)
                  empire.computer.core/wake-nearby-sentries (fn [_ _] wake-count)
                  sa/read-state (fn [k]
                                  (when (= k :computer-map)
                                    current-world))
                  sa/update-world! (fn [& args] (swap! updates conj args))
                  empire.game-mechanics.movement.visibility/sync-ai-unit-to-computer-map!
                  (fn [_] nil)
                  empire.game-mechanics.debug.logging/log-computer-event! (fn [& _] nil)]
      {:result (coastal/fill-coastal-cell [1 1] 1)
       :updates @updates})))

(describe "local-empty-coast-target"
  (before (reset-all-atoms!))

  (it "searches local BFS up to depth two for empty coastal cells"
    (doseq [{:keys [neighbors target expected]}
            [{:description "prefers nearest match"
              :neighbors {[0 0] [[1 0] [0 1]]
                          [1 0] []
                          [0 1] []}
              :target [1 0]
              :expected [1 0]}
             {:description "returns nil beyond radius two"
              :neighbors {[0 0] [[1 0]]
                          [1 0] [[2 0]]
                          [2 0] [[3 0]]}
              :target [3 0]
              :expected nil}
             {:description "accepts an exact depth-two match"
              :neighbors {[0 0] [[1 0]]
                          [1 0] [[2 0]]}
              :target [2 0]
              :expected [2 0]}]]
      (should= expected
               (run-local-empty-coast-target neighbors target)))))

(describe "fill-coastal-cell"
  (before (reset-all-atoms!))

  (it "handles settle-in-place and wake-neighbors outcomes"
    (let [{settle-result :result settle-updates :updates}
          (run-fill-coastal-cell {:should-sentry? true
                                  :coastal-target nil
                                  :can-settle? false
                                  :queue-target nil
                                  :wake-count 0
                                  :current-world [[{:type :land} {:type :land} {:type :land}]
                                                  [{:type :land} {:type :land :contents {}} {:type :land}]
                                                  [{:type :land} {:type :land} {:type :land}]]})
          {wake-result :result}
          (run-fill-coastal-cell {:should-sentry? false
                                  :coastal-target nil
                                  :can-settle? false
                                  :queue-target nil
                                  :wake-count 2
                                  :current-world [[{:type :land} {:type :land} {:type :land}]
                                                  [{:type :land} {:type :land :contents {}} {:type :land}]
                                                  [{:type :land} {:type :land} {:type :land}]]})]
      (should= [1 1] settle-result)
      (should= 1 (count settle-updates))
      (should-be-nil wake-result))))

  (it "logs and avoids creating malformed contents when coastal sentry write has no unit"
    (let [logged (atom nil)]
      (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] true)
                    sa/read-state (fn [k]
                                    (when (= k :computer-map)
                                      [[{:type :land} {:type :land} {:type :land}]
                                       [{:type :land} {:type :land :country-id 1} {:type :land}]
                                       [{:type :land} {:type :land} {:type :land}]]))
                    sa/update-world! (fn [& _] (should-not "should not write malformed contents"))
                    empire.game-mechanics.debug.logging/log-computer-event! (fn [& _] nil)
                    empire.game-mechanics.debug.integrity/write-stacktrace-error-log!
                    (fn [_prefix context _throwable]
                      (reset! logged context)
                      "army-error123.log")]
        (should= [1 1] (coastal/fill-coastal-cell [1 1] 1))
        (should= :fill-coastal-cell (:operation @logged)))))
