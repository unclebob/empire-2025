(ns empire.computer.army-transport-staging-spec
  (:require [empire.computer.army.coastal :as coastal]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "army transport staging movement"
  (before (reset-all-atoms!))

  (it "uses a cheap greedy step before full pathing for transport staging"
    (set-test-world! [[{:type :land
                        :contents {:type :army :owner :computer :hits 1
                                   :mode :move-to-coast-for-transport
                                   :country-id 1
                                   :transport-staging-target [3 0]}}
                       {:type :land}
                       {:type :land}
                       {:type :land}]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] false)
                  empire.computer.army.movement/get-empty-passable-neighbors (fn [_ _] [[1 0]])
                  empire.computer.army.movement/move-toward-objective
                  (fn [& _] (should-not "should not use full pathing when a greedy step is available"))
                  empire.computer.army.movement/try-move
                  (fn [from to]
                    (should= [0 0] from)
                    (should= [1 0] to)
                    (update-test-world! assoc-in [0 0] {:type :land})
                    (update-test-world! assoc-in [1 0]
                                        {:type :land
                                         :contents {:type :army :owner :computer :hits 1
                                                    :mode :move-to-coast-for-transport
                                                    :country-id 1
                                                    :transport-staging-target [3 0]}})
                    (test-utils/set-test-computer-map! (test-utils/read-test-state :game-map))
                    to)]
      (should= [1 0] (coastal/process-move-to-coast-for-transport [0 0] 1))))

  (it "uses a local sidestep before full pathing when no greedy step reduces distance"
    (set-test-world! [[{:type :land
                        :contents {:type :army :owner :computer :hits 1
                                   :mode :move-to-coast-for-transport
                                   :country-id 1
                                   :transport-staging-target [3 0]}}
                       {:type :land}]
                      [{:type :land}
                       {:type :land}]])
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [coastal/should-sentry-on-coast? (fn [_ _] false)
                  empire.computer.army.movement/get-empty-passable-neighbors (fn [_ _] [[0 1]])
                  empire.computer.army.movement/move-toward-objective
                  (fn [& _] (should-not "should not use full pathing when a local sidestep is available"))
                  empire.computer.army.movement/try-move
                  (fn [from to]
                    (should= [0 0] from)
                    (should= [0 1] to)
                    (update-test-world! assoc-in [0 0] {:type :land})
                    (update-test-world! assoc-in [0 1]
                                        {:type :land
                                         :contents {:type :army :owner :computer :hits 1
                                                    :mode :move-to-coast-for-transport
                                                    :country-id 1
                                                    :transport-staging-target [3 0]}})
                    (test-utils/set-test-computer-map! (test-utils/read-test-state :game-map))
                    to)]
      (should= [0 1] (coastal/process-move-to-coast-for-transport [0 0] 1)))))
