(ns empire.game-loop.round-setup-lake-evacuation-spec
  (:require [empire.game-mechanics.movement.satellite :as satellite]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.game-mechanics.movement.wake-conditions :as wake]
            [empire.game.loop.round-setup :as setup]
            [empire.game.loop.round-setup.lakes :as lakes]
            [empire.game.loop.round-setup.waking :as waking]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-unit set-test-world! update-test-world!]]
            [speclj.core :refer :all]))
(describe "evacuate-lake-patrol-boats"
  (before (reset-all-atoms!))

  (it "moves patrol boat from computer lake-shore city to adjacent sea"
    (let [world (build-test-map ["~~~"
                                 "~X~"
                                 "~~~"])
          computer-map (build-test-map ["~~~"
                                        "~X~"
                                        "~~~"])]
      (set-test-world! world)
      (set-test-computer-map! computer-map)
      (test-utils/set-test-state! :lake-max-cells 10)
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :patrol-boat :owner :computer :hits 1 :mode :awake})
      (lakes/evacuate-lake-patrol-boats)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents]))
      (let [sea-neighbors (for [pos [[0 0] [0 1] [0 2] [1 0] [1 2] [2 0] [2 1] [2 2]]
                                :let [u (get-in (test-utils/read-test-state :game-map) (conj pos :contents))]
                                :when (= :patrol-boat (:type u))]
                            pos)]
        (should= 1 (count sea-neighbors)))))

  (it "leaves patrol boat in city when no adjacent sea is empty"
    (let [world (build-test-map ["~~~"
                                 "~X~"
                                 "~~~"])
          computer-map (build-test-map ["~~~"
                                        "~X~"
                                        "~~~"])]
      (set-test-world! world)
      (set-test-computer-map! computer-map)
      (test-utils/set-test-state! :lake-max-cells 10)
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :patrol-boat :owner :computer :hits 1 :mode :awake})
      (doseq [pos [[0 0] [0 1] [0 2] [1 0] [1 2] [2 0] [2 1] [2 2]]]
        (update-test-world! assoc-in (conj pos :contents)
                           {:type :transport :owner :computer :hits 1 :mode :awake}))
      (lakes/evacuate-lake-patrol-boats)
      (should= :patrol-boat (get-in (test-utils/read-test-state :game-map) [1 1 :contents :type]))
      (should= :computer (get-in (test-utils/read-test-state :game-map) [1 1 :contents :owner]))))

  (it "moves transport out of computer lake-shore city and preserves cargo state"
    (let [world (build-test-map ["~~~"
                                 "~X~"
                                 "~~~"])
          computer-map (build-test-map ["~~~"
                                        "~X~"
                                        "~~~"])]
      (set-test-world! world)
      (set-test-computer-map! computer-map)
      (test-utils/set-test-state! :lake-max-cells 10)
      (update-test-world! assoc-in [1 1 :contents]
                         {:type :transport
                          :owner :computer
                          :hits 1
                          :mode :awake
                          :army-count 3
                          :transport-mission :invading})
      (lakes/evacuate-lake-patrol-boats)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [1 1 :contents]))
      (let [moved (first (for [pos [[0 0] [0 1] [0 2] [1 0] [1 2] [2 0] [2 1] [2 2]]
                               :let [u (get-in (test-utils/read-test-state :game-map) (conj pos :contents))]
                               :when (= :transport (:type u))]
                           u))]
        (should-not-be-nil moved)
        (should= 3 (:army-count moved))
        (should= :land-locked (:transport-mission moved))
        (should= true (:never-reload? moved)))))

  (it "prefers ocean sea over lake sea when both are adjacent"
    (let [world [[{:type :sea}
                  {:type :city :city-status :computer
                   :contents {:type :patrol-boat :owner :computer :hits 1}}
                  {:type :sea}]]
          computer-map world]
      (set-test-world! world)
      (set-test-computer-map! computer-map)
      (with-redefs [empire.game-mechanics.movement.lakes/lake-cells (fn [_ _] #{[0 0]})
                    visibility/update-cell-visibility (fn [_ _] nil)]
        (lakes/evacuate-lake-patrol-boats))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 1 :contents]))
      (should= :patrol-boat (get-in (test-utils/read-test-state :game-map) [0 2 :contents :type])))))

