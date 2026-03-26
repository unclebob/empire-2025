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

  (it "rejects tiles whose armies are not already on the coast"
    (let [computer-map (build-test-map ["#~~~~"
                                        "~t~~~"
                                        "~###~"
                                        "~#aa#"
                                        "~#aa#"])]
      (set-test-world! computer-map)
      (update-test-world! assoc-in [0 0 :country-id] 1)
      (doseq [pos [[1 2] [2 2] [3 2] [1 3] [4 3] [1 4] [4 4]]]
        (update-test-world! assoc-in (conj pos :country-id) 2))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should-be-nil
       (load-targeting/choose-load-target-cell [1 1] (test-utils/read-test-state :computer-map)))))

  (it "rejects a qualifying tile when one of its coastal cells is reserved"
    (let [computer-map (build-test-map ["#~~~~#~~~~"
                                        "~t~~~~~~~~"
                                        "aaaa~aaaa~"
                                        "~~~~~~~~~~"
                                        "~~~~~~~~~~"])]
      (set-test-world! computer-map)
      (doseq [pos [[0 0] [5 0]]]
        (update-test-world! assoc-in (conj pos :country-id) 1))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (test-utils/set-test-state! :transport-load-reservations
                                  {99 {:coastal-cell [0 0]
                                       :army-ids #{}}})
      (should= [0 0]
               (load-targeting/choose-load-target-cell
                [1 1]
                (test-utils/read-test-state :computer-map)))
      (should= [5 0]
               (load-targeting/choose-load-target-cell
                [1 1]
                (test-utils/read-test-state :computer-map)
                {:reserved-coastal-cells #{[0 0]}
                 :reserved-army-ids #{}}))))

  (it "does not choose a computer city as the coastal load target"
    (let [computer-map (build-test-map ["O~~~~#~~~~"
                                        "~t~~~~~~~~"
                                        "aaaa~aaaa~"
                                        "~~~~~~~~~~"
                                        "~~~~~~~~~~"])]
      (set-test-world! computer-map)
      (update-test-world! assoc-in [0 0 :city-status] :computer)
      (update-test-world! assoc-in [0 0 :country-id] 1)
      (update-test-world! assoc-in [5 0 :country-id] 1)
      (doseq [[idx pos] (map-indexed vector [[0 2] [1 2] [2 2] [3 2]
                                             [5 2] [6 2] [7 2] [8 2]])]
        (update-test-world! assoc-in (conj pos :contents :computer-unit-id) idx))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should= [5 0]
               (load-targeting/choose-load-target-cell
                [1 1]
                (test-utils/read-test-state :computer-map)))))

  (it "does not build a load path through an occupied ship"
    (let [computer-map [[{:type :sea} {:type :sea}]
                        [{:type :sea
                          :contents {:type :destroyer :owner :computer}}
                         {:type :sea}]
                        [{:type :sea} {:type :sea}]
                        [{:type :sea} {:type :sea}]
                        [{:type :land :country-id 1} {:type :land :country-id 1}]]]
      (let [path (load-targeting/path-to-load-target [0 0] computer-map [4 0])]
        (should-not-be-nil path)
        (should-not-contain [1 0] path)
        (should= [3 0] (last path))))))

  (it "does not count reserved armies toward tile qualification"
    (let [computer-map (build-test-map ["#~~~~#~~~~"
                                        "~t~~~~~~~~"
                                        "aaaa~aaaa~"
                                        "~~~~~~~~~~"
                                        "~~~~~~~~~~"])]
      (set-test-world! computer-map)
      (doseq [[idx pos] (map-indexed vector [[0 0] [5 0]
                                             [0 2] [1 2] [2 2] [3 2]
                                             [5 2] [6 2] [7 2] [8 2]])]
        (when (#{[0 0] [5 0]} pos)
          (update-test-world! assoc-in (conj pos :country-id) 1))
        (when (not (#{[0 0] [5 0]} pos))
          (update-test-world! assoc-in (conj pos :contents :computer-unit-id) idx)))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should= [0 0]
               (load-targeting/choose-load-target-cell [1 1] (test-utils/read-test-state :computer-map)))
      (should= [5 0]
               (load-targeting/choose-load-target-cell
                [1 1]
                (test-utils/read-test-state :computer-map)
                {:reserved-coastal-cells #{}
                 :reserved-army-ids #{2 3}}))))

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
      (with-redefs [empire.computer.army.assignment/assign-returning-transport-staging-at!
                    (fn
                      ([_] [1 2 3 4])
                      ([_ _] [1 2 3 4]))]
        (@#'transport/transition-to-loading [1 1]))
      (let [unit (get-in (test-utils/read-test-state :game-map) [1 1 :contents])]
        (should= :sail-to-load (:transport-mission unit))
        (should= [5 0] (:load-target-cell unit))
        (should= [[2 0] [3 0] [4 0]] (:sail-path unit)))))

  (it "chooses a nearer coast when enough coastal armies are nearby across neighboring tiles"
    (let [world (build-test-map ["##~~~#~~~~"
                                 "a~~~~a~~~~"
                                 "a~t~~a~~~~"
                                 "~~~~~a~~~~"
                                 "~~~~~#~~~~"])]
      (set-test-world! world)
      (doseq [pos [[0 0] [1 0] [5 0]
                   [0 1] [5 1] [0 2] [5 2] [5 3] [5 4]]]
        (update-test-world! assoc-in (conj pos :country-id) 1))
      (doseq [[idx pos] (map-indexed vector [[0 1] [0 2] [5 1] [5 2] [5 3]])]
        (update-test-world! assoc-in (conj pos :contents :computer-unit-id) idx))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should= [0 0]
               (load-targeting/choose-load-target-cell [2 2]
                                                       (test-utils/read-test-state :computer-map)))))

  (it "does not choose a load target on a recently unloaded country"
    (let [world (build-test-map ["#~~~~#~~~~"
                                 "~t~~~~~~~~"
                                 "aaaa~aaaa~"
                                 "~~~~~~~~~~"
                                 "~~~~~~~~~~"])]
      (set-test-world! world)
      (doseq [pos [[0 0] [5 0]]]
        (update-test-world! assoc-in (conj pos :country-id) 1))
      (doseq [pos [[0 2] [1 2] [2 2] [3 2]]]
        (update-test-world! assoc-in (conj pos :country-id) 2))
      (doseq [pos [[5 2] [6 2] [7 2] [8 2]]]
        (update-test-world! assoc-in (conj pos :country-id) 3))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (should= [0 0]
               (load-targeting/choose-load-target-cell [1 1]
                                                       (test-utils/read-test-state :computer-map)))
      (should= [5 0]
               (load-targeting/choose-load-target-cell [1 1]
                                                       (test-utils/read-test-state :computer-map)
                                                       {:excluded-country-ids #{1}}))))

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
                          :load-manifest [99]
                          :sail-path [[2 1] [3 1] [4 1]]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [1 1])
      (let [unit (get-in (test-utils/read-test-state :game-map) [2 1 :contents])]
        (should= :transport (:type unit))
        (should= :sail-to-load (:transport-mission unit))
        (should= [4 0] (:load-target-cell unit))
        (should= [[3 1] [4 1]] (:sail-path unit)))))

  (it "stages the six nearest armies from the target tile neighborhood"
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
      (with-redefs [rand-nth first]
        (assignment/assign-returning-transport-staging-at! [9 9]))
      (let [staged (for [pos [[0 5] [1 5] [2 5] [3 5] [4 5] [5 1] [5 2] [5 3] [5 4] [5 5]]
                         :let [unit (get-in (test-utils/read-test-state :game-map) (conj pos :contents))]
                         :when (= :move-to-coast-for-transport (:mode unit))]
                     pos)]
        (should= [[5 1] [5 2] [5 3] [5 4] [0 5] [1 5]] staged)
        (should (every? #(= [5 0]
                            (get-in (test-utils/read-test-state :game-map)
                                    (conj % :contents :transport-staging-target)))
                        staged))
        (should= nil
                 (get-in (test-utils/read-test-state :game-map)
                         [5 5 :contents :mode])))))

  (it "keeps returning transport staging focused on the chosen load target"
    (let [world (build-test-map ["#~~~#"
                                 "a~t~a"
                                 "a~~~a"])]
      (set-test-world! world)
      (doseq [[id pos] (map vector [11 12 13 14] [[0 1] [0 2] [4 1] [4 2]])]
        (update-test-world! assoc-in (conj pos :country-id) 1)
        (update-test-world! assoc-in (conj pos :contents :computer-unit-id) id))
      (doseq [pos [[0 0] [4 0]]]
        (update-test-world! assoc-in (conj pos :country-id) 1))
      (update-test-world! assoc-in [2 1 :contents]
                         {:type :transport
                          :owner :computer
                          :hits 1
                          :transport-id 7
                          :transport-mission :sail-to-load
                          :load-target-cell [4 0]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [rand-nth last]
        (assignment/assign-returning-transport-staging-at! [2 1] [4 0]))
      (let [assigned-targets (->> [[0 1] [0 2] [4 1] [4 2]]
                                  (map #(get-in (test-utils/read-test-state :game-map)
                                                (conj % :contents :transport-staging-target)))
                                  (remove nil?)
                                  distinct)]
        (should= [[4 0]] assigned-targets))))

  (it "does not recruit armies already reserved for another transport"
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
      (doseq [[id pos] (map vector (range 1 12)
                            [[5 1] [5 2] [5 3] [5 4] [0 5] [1 5] [2 5] [3 5] [4 5] [5 5] [5 0]])]
        (when (= :army (get-in (test-utils/read-test-state :game-map) (conj pos :contents :type)))
          (update-test-world! assoc-in (conj pos :contents :computer-unit-id) id)))
      (update-test-world! assoc-in [9 9 :contents]
                         {:type :transport
                          :owner :computer
                          :hits 1
                          :transport-id 10
                          :transport-mission :sail-to-load
                          :load-target-cell [5 0]})
      (test-utils/set-test-state! :transport-load-reservations
                                  {99 {:coastal-cell [0 0]
                                       :army-ids #{5 6}}})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [rand-nth first]
        (let [assigned (assignment/assign-returning-transport-staging-at! [9 9])]
          (should= 6 (count assigned))
          (should-not-contain 5 assigned)
          (should-not-contain 6 assigned)))))

  (it "recruits armies using an explicit target even before the transport stores it in state"
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
      (doseq [[id pos] (map vector (range 1 12)
                            [[5 1] [5 2] [5 3] [5 4] [0 5] [1 5] [2 5] [3 5] [4 5] [5 5] [5 0]])]
        (when (= :army (get-in (test-utils/read-test-state :game-map) (conj pos :contents :type)))
          (update-test-world! assoc-in (conj pos :contents :computer-unit-id) id)))
      (update-test-world! assoc-in [9 9 :contents]
                         {:type :transport
                          :owner :computer
                          :hits 1
                          :transport-id 10
                          :transport-mission :sail-to-load})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [rand-nth first]
        (let [assigned (assignment/assign-returning-transport-staging-at! [9 9] [5 0])]
          (should= 6 (count assigned))))))

  (it "keeps an army's existing landing-zone target when it is already valid"
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
      (update-test-world! assoc-in [5 1 :contents]
                         {:type :army :owner :computer :hits 1
                          :mode :move-to-coast-for-transport
                          :transport-staging-target [5 0]})
      (update-test-world! assoc-in [9 9 :contents]
                         {:type :transport
                          :owner :computer
                          :hits 1
                          :transport-id 10
                          :transport-mission :sail-to-load
                          :load-target-cell [5 0]})
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (with-redefs [rand-nth (fn [_] [4 5])]
        (assignment/assign-returning-transport-staging-at! [9 9]))
      (should= [5 0]
               (get-in (test-utils/read-test-state :game-map)
                       [5 1 :contents :transport-staging-target]))))
