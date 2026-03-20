(ns empire.computer.transport-load-targeting-spec
  (:require [empire.computer.army.assignment :as assignment]
            [empire.computer.transport :as transport]
            [empire.computer.transport.load-targeting :as load-targeting]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "transport load targeting"
  (before (reset-all-atoms!))

  (it "chooses the nearest qualifying tile with a claimed coast and four armies"
    (let [computer-map (build-test-map ["#~~~~#####"
                                        "~t~~~~~~~~"
                                        "~~~~~aaaa~"
                                        "~~~~~~~~~~"
                                        "~~~~~~~~~~"])]
      (set-test-world! computer-map)
      (update-test-world! assoc-in [0 0 :country-id] 1)
      (doseq [pos [[5 0] [5 2] [6 2] [7 2] [8 2]]]
        (update-test-world! assoc-in (conj pos :country-id) 2))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should= [5 0]
               (load-targeting/choose-load-target-cell [1 1] (test-utils/read-test-state :computer-map)))
      (should= [[2 0] [3 0] [4 0]]
               (load-targeting/path-to-load-target [1 1]
                                                   (test-utils/read-test-state :computer-map)
                                                   [5 0]))))

  (it "stores the chosen coastal load target when an empty transport starts sailing to load"
    (let [world (build-test-map ["#~~~~#####"
                                 "~t~~~~~~~~"
                                 "~~~~~aaaa~"
                                 "~~~~~~~~~~"
                                 "~~~~~~~~~~"])]
      (set-test-world! world)
      (update-test-world! assoc-in [0 0 :country-id] 1)
      (doseq [pos [[5 0] [5 2] [6 2] [7 2] [8 2]]]
        (update-test-world! assoc-in (conj pos :country-id) 2))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (@#'transport/transition-to-loading [1 1])
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
        (should= :sail-to-load (:transport-mission unit))
        (should= [5 0] (:load-target-cell unit))
        (should= [[2 0] [3 0] [4 0]] (:sail-path unit)))))

  (it "does not switch to loading when adjacent to the wrong claimed coast mid mission"
    (let [world (build-test-map ["#####"
                                 "~t~~~"])]
      (set-test-world! world)
      (doseq [pos [[1 0] [4 0]]]
        (update-test-world! assoc-in (conj pos :country-id) 1))
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :transport
                          :owner :computer
                          :hits 1
                          :army-count 0
                          :transport-mission :sail-to-load
                          :load-target-cell [4 0]
                          :sail-path [[2 1] [3 1] [4 1]]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [1 1])
      (let [unit (get-in (test-utils/read-test-state :game-map) [2 1 :contents])]
        (should= :transport (:type unit))
        (should= :sail-to-load (:transport-mission unit))
        (should= [4 0] (:load-target-cell unit))
        (should= [[3 1] [4 1]] (:sail-path unit)))))

  (it "stages at most five random armies from the target tile neighborhood"
    (let [world (build-test-map ["~~~~~#~~~~"
                                 "~~~~~a~~~~"
                                 "~~~~~a~~~~"
                                 "~~~~~a~~~~"
                                 "~~~~~a~~~~"
                                 "aaaaaa~~~~"
                                 "~~~~~~~~~~"
                                 "~~~~~~~~~~"
                                 "~~~~~~~~~~"
                                 "~~~~~~~~~~"])]
      (set-test-world! world)
      (doseq [pos [[5 0] [5 1] [5 2] [5 3] [5 4]
                   [0 5] [1 5] [2 5] [3 5] [4 5] [5 5]]]
        (update-test-world! assoc-in (conj pos :country-id) 1))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [9 9 :contents]
                         {:type :transport
                          :owner :computer
                          :hits 1
                          :transport-mission :sail-to-load
                          :load-target-cell [5 0]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [shuffle identity]
        (assignment/assign-returning-transport-staging-at! [9 9]))
      (let [staged (for [pos [[0 5] [1 5] [2 5] [3 5] [4 5] [5 1] [5 2] [5 3] [5 4] [5 5]]
                         :let [unit (get-in (test-utils/read-test-state :game-map) (conj pos :contents))]
                         :when (= :move-to-coast-for-transport (:mode unit))]
                     pos)]
        (should= [[0 5] [1 5] [2 5] [3 5] [4 5]] staged)
        (should (every? #(= [5 0]
                            (get-in (test-utils/read-test-state :game-map)
                                    (conj % :contents :transport-staging-target)))
                        staged))
        (should= nil
                 (get-in (test-utils/read-test-state :game-map)
                         [5 5 :contents :mode]))))))
