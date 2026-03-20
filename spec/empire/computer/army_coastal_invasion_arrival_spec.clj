(ns empire.computer.army-coastal-invasion-arrival-spec
  (:require [empire.computer.army.coastal :as coastal]
            [empire.state.api :as sa]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [reset-all-atoms! update-test-world!]]
            [speclj.core :refer :all]))

(describe "process-move-to-coast-for-invasion arrival handling"
  (before (reset-all-atoms!))

  (it "settles to sentry when it reaches its staged coast target"
    (test-utils/set-test-world-with-country! ["~~~~~"
                                              "#####"
                                              "#####"
                                              "#####"
                                              "#####"]
                                             1)
    (test-utils/set-test-state! :lake-max-cells 2)
    (update-test-world! assoc-in [2 3 :contents]
                        {:type :army :owner :computer :hits 1
                         :mode :move-to-coast-for-invasion
                         :country-id 1
                         :coast-target [2 3]})
    (test-utils/set-test-state! :computer-map (test-utils/read-test-state :game-map))
    (coastal/process-move-to-coast-for-invasion [2 3] 1)
    (should= :sentry (get-in (test-utils/read-test-state :game-map) [2 3 :contents :mode])))

  (it "lake-retask army goes sentry when blocked and cannot progress"
    (test-utils/set-test-world-with-country! ["~~~~~"
                                              "#####"
                                              "#####"]
                                             1)
    (update-test-world! assoc-in [2 2 :contents]
                        {:type :army :owner :computer :hits 1
                         :mode :move-to-coast-for-invasion
                         :country-id 1
                         :coast-target [2 1]
                         :lake-retask? true})
    (doseq [p [[1 1] [2 1] [3 1] [1 2] [3 2]]]
      (update-test-world! assoc-in (conj p :contents)
                          {:type :army :owner :computer :hits 1 :mode :sentry :country-id 1}))
    (test-utils/set-test-state! :computer-map (test-utils/read-test-state :game-map))
    (coastal/process-move-to-coast-for-invasion [2 2] 1)
    (should= :sentry (get-in (test-utils/read-test-state :game-map) [2 2 :contents :mode])))

  (it "switches to sentry when already on coastal cell"
    (let [updates (atom [])]
      (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] true)
                    sa/update-world! (fn [& args] (swap! updates conj args))
                    sa/read-state (fn [k]
                                    (when (= k :computer-map)
                                      [[{:type :land
                                         :contents {:type :army
                                                    :owner :computer
                                                    :hits 1
                                                    :mode :move-to-coast
                                                    :coast-target [2 2]}}]]))
                    empire.game-mechanics.visibility/sync-ai-unit-to-computer-map!
                    (fn [_] nil)]
        (should= [0 0] (coastal/process-move-to-coast-for-invasion [0 0] 1))
        (should= 1 (count @updates)))))

  (it "avoids creating malformed contents when invasion arrival sentry write has no unit"
    (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] true)
                  sa/read-state (fn [k]
                                  (when (= k :computer-map)
                                    [[{:type :land :country-id 1}]]))
                  sa/update-world! (fn [& _] (should-not "should not write malformed contents"))]
      (should= [0 0] (coastal/process-move-to-coast-for-invasion [0 0] 1)))))
