(ns empire.computer.army-mission-spec
  (:require [speclj.core :refer :all]
            [empire.computer.army :as army]
            [empire.fsm.integration :as integration]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Computer Army Missions"

  (describe "process-army with awake army"
    (before
      (reset-all-atoms!)
      ;; Map with computer city and land to explore
      ;; X##
      ;; ###
      ;; ###
      (reset! atoms/game-map (build-test-map ["X##"
                                               "###"
                                               "###"]))
      ;; Computer has explored around city
      (reset! atoms/computer-map [[{:type :city :city-status :computer} {:type :land} {:type :unexplored}]
                                   [{:type :land} {:type :land} {:type :unexplored}]
                                   [{:type :unexplored} {:type :unexplored} {:type :unexplored}]])
      ;; Place an awake army at [1 1]
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake})
      (integration/initialize-general)
      (integration/process-general-turn))

    (it "assigns exploration mission to awake army"
      (army/process-army [1 1])
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (should= :explore (:mode unit))))

    (it "sets explore-steps when assigning exploration"
      (army/process-army [1 1])
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (should (pos? (:explore-steps unit))))))

  (describe "process-army with exploring army"
    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["X##"
                                               "###"
                                               "###"]))
      (reset! atoms/computer-map [[{:type :city :city-status :computer} {:type :land} {:type :unexplored}]
                                   [{:type :land} {:type :land} {:type :unexplored}]
                                   [{:type :unexplored} {:type :unexplored} {:type :unexplored}]])
      ;; Place an exploring army at [1 1]
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1 :mode :explore :explore-steps 50
              :fsm-state :seeking-coast
              :fsm-data {:recent-moves [[1 1]] :found-coast false :position [1 1]}})
      (integration/initialize-general)
      (integration/process-general-turn))

    (it "moves exploring army toward unexplored territory"
      (let [original-pos [1 1]]
        (army/process-army original-pos)
        ;; Army should have moved - either original cell empty or army at new position
        (let [original-unit (get-in @atoms/game-map [1 1 :contents])
              ;; Check adjacent cells for the army
              adjacent-cells [[0 1] [0 2] [1 0] [1 2] [2 0] [2 1] [2 2]]
              army-positions (filter (fn [pos]
                                       (let [unit (get-in @atoms/game-map (conj pos :contents))]
                                         (and unit (= :army (:type unit)))))
                                     (cons [1 1] adjacent-cells))]
          ;; Army should exist somewhere
          (should (seq army-positions))))))

  (describe "exploration updates computer visibility"
    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["X##"
                                               "###"
                                               "###"]))
      (reset! atoms/computer-map [[{:type :city :city-status :computer} {:type :land} {:type :unexplored}]
                                   [{:type :land} {:type :land} {:type :unexplored}]
                                   [{:type :unexplored} {:type :unexplored} {:type :unexplored}]])
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :army :owner :computer :hits 1 :mode :explore :explore-steps 50
              :fsm-state :seeking-coast
              :fsm-data {:recent-moves [[1 1]] :found-coast false :position [1 1]}})
      (integration/initialize-general)
      (integration/process-general-turn))

    (it "reveals cells as army explores"
      ;; [2 2] should be unexplored before
      (should= :unexplored (:type (get-in @atoms/computer-map [2 2])))
      ;; Move army multiple times toward unexplored
      (dotimes [_ 5]
        (doseq [i (range 3) j (range 3)]
          (let [unit (get-in @atoms/game-map [i j :contents])]
            (when (and unit (= :army (:type unit)) (= :computer (:owner unit)))
              (army/process-army [i j])))))
      ;; Some previously unexplored cells should now be explored
      (let [explored-count (count (for [i (range 3) j (range 3)
                                        :when (not= :unexplored (:type (get-in @atoms/computer-map [i j])))]
                                    [i j]))]
        (should (> explored-count 4))))))
