(ns empire.computer.transport-invading-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.transport :as transport]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world! set-test-computer-map! update-test-computer-map!]]))

(defn make-map [height width cell-fn]
  (mapv (fn [r] (mapv (fn [c] (cell-fn r c)) (range width))) (range height)))

(describe "transport invading mode"
  (before (reset-all-atoms!))

  (context "with path remaining"
    (it "follows the path up to 2 steps per round"
      (let [game-map (make-map 1 5
                       (fn [_ _] {:type :sea}))]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [0 0 :contents]
                            {:type :transport :owner :computer
                             :transport-mission :invading
                             :invasion-target [0 4]
                             :invasion-path [[0 1] [0 2] [0 3]]
                             :army-count 4})
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (transport/process-transport [0 0])
        ;; Should have moved 2 steps to [0 2]
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
        (let [transport (get-in (test-utils/read-test-state :game-map) [0 2 :contents])]
          (should= :invading (:transport-mission transport))
          (should= [[0 3]] (:invasion-path transport))
          (should= [0 2] (:invasion-path-origin transport))))))

  (context "when path is exhausted"
    (it "transitions to unloading mode"
      ;; Row 0: sea(transport), sea | Row 1: land, city
      (let [game-map (make-map 2 2
                       (fn [r c]
                         (cond
                           (and (= r 1) (= c 1)) {:type :city :city-status :free}
                           (= r 1) {:type :land}
                           :else {:type :sea})))]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [0 0 :contents]
                            {:type :transport :owner :computer
                             :transport-mission :invading
                             :invasion-target [1 1]
                             :invasion-path [[0 1]]
                             :army-count 4})
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (transport/process-transport [0 0])
        ;; Should have moved to [0 1] and transitioned to unloading
        (let [transport (get-in (test-utils/read-test-state :game-map) [0 1 :contents])]
          (should= :unloading (:transport-mission transport))
          (should-be-nil (:invasion-path-origin transport))))))

  (context "when the direct fog corridor is much shorter than the invasion path"
    (it "cuts across the unexplored corridor until blocked"
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "t~~~O"
                                      "~~~~~"
                                      "~~~~~"])
            computer-map (build-test-map ["~~~~~"
                                          "~###~"
                                          "....O"
                                          "....."
                                          "....."])]
        (set-test-world! game-map)
        (set-test-computer-map! computer-map)
        (update-test-world! assoc-in [0 2 :contents]
                            {:type :transport :owner :computer
                             :transport-mission :invading
                             :invasion-target [4 2]
                             :invasion-path [[0 1] [0 0] [1 0] [2 0] [3 0] [4 0] [4 1] [3 2]]
                             :army-count 4})
        (set-test-computer-map!
                (assoc-in (test-utils/read-test-state :computer-map)
                          [0 2 :contents]
                          {:type :transport :owner :computer
                           :transport-mission :invading
                           :invasion-target [4 2]
                           :invasion-path [[0 1] [0 0] [1 0] [2 0] [3 0] [4 0] [4 1] [3 2]]
                           :army-count 4}))
        (transport/process-transport [0 2])
        (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 2])))
        (let [transport (get-in (test-utils/read-test-state :game-map) [2 0 :contents])]
          (should= :unloading (:transport-mission transport))
          (should-be-nil (:invasion-path transport))
          (should= [1 1] (:invasion-last-pos transport)))))))
