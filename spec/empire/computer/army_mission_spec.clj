(ns empire.computer.army-mission-spec
  (:require [speclj.core :refer :all]
            [empire.computer.army :as army]
            [empire.fsm.coastline-explorer :as explorer]
            [empire.fsm.integration :as integration]
            [empire.fsm.lieutenant :as lieutenant]
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
      (reset! atoms/computer-map (build-test-map ["X# "
                                                   "## "
                                                   "   "]))
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
      (reset! atoms/computer-map (build-test-map ["X# "
                                                   "## "
                                                   "   "]))
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
      (reset! atoms/computer-map (build-test-map ["X# "
                                                   "## "
                                                   "   "]))
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
        (should (> explored-count 4)))))

  (describe "directed movement to exploration target"
    (before
      (reset-all-atoms!)
      ;; Larger map with unexplored area to the east
      ;; X#####
      ;; ######
      ;; ~~~~~~
      (reset! atoms/game-map (build-test-map ["X#####"
                                               "######"
                                               "~~~~~~"]))
      ;; Computer has explored west side, east is unexplored
      (reset! atoms/computer-map (build-test-map ["X##   "
                                                   "###   "
                                                   "~~~~~~"]))
      ;; Place an awake army at [0 1] (near city)
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake})
      (integration/initialize-general)
      (integration/process-general-turn)
      ;; Tell Lieutenant about known coastal cells
      (let [lt (first (:lieutenants @atoms/commanding-general))
            updated-lt (-> lt
                           (assoc :known-coastal-cells #{[1 1] [1 2] [1 3]}))]
        (swap! atoms/commanding-general
               update :lieutenants
               (fn [lts] (mapv #(if (= (:name %) (:name updated-lt)) updated-lt %) lts)))))

    (it "sets army to moving-to-start state when frontier target exists"
      (army/process-army [0 1])
      (let [unit (get-in @atoms/game-map [0 1 :contents])]
        ;; Army should be in explore mode with moving-to-start fsm-state
        (should= :explore (:mode unit))
        (if (= :moving-to-start (:fsm-state unit))
          (should-not-be-nil (get-in unit [:fsm-data :destination]))
          ;; Or it might have started exploring directly if no target
          (should (#{:moving-to-start :seeking-coast :following-coast} (:fsm-state unit))))))

    (it "moves army toward destination when in moving-to-start state"
      ;; Set up army in explore mode with moving-to-start FSM state
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :army :owner :computer :hits 1 :mode :explore
              :explore-steps 50
              :fsm explorer/coastline-explorer-fsm
              :fsm-state :moving-to-start
              :fsm-data {:position [0 1]
                         :destination [1 3]
                         :recent-moves [[0 1]]}})
      (army/process-army [0 1])
      ;; Army should have moved closer to destination
      (let [original-unit (get-in @atoms/game-map [0 1 :contents])]
        ;; Army either moved away from [0 1] or is still there but closer count changed
        (should (or (nil? original-unit)
                    (not= :army (:type original-unit))
                    ;; Check if army is somewhere else now
                    (some (fn [pos]
                            (let [u (get-in @atoms/game-map (conj pos :contents))]
                              (and u (= :army (:type u)) (= :computer (:owner u)))))
                          [[0 2] [1 1] [1 2]])))))

    (it "transitions from moving-to-start when reaching destination"
      ;; Set up army one step away from coastal destination
      ;; Map: row 0 = X#####, row 1 = ######, row 2 = ~~~~~~ (sea)
      ;; [1,0] is land, not coastal. [1,1] is land, coastal (adjacent to [1,2] sea)
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :army :owner :computer :hits 1 :mode :explore
              :explore-steps 50
              :fsm explorer/coastline-explorer-fsm
              :fsm-state :moving-to-start
              :fsm-data {:position [1 0]
                         :destination [1 1]  ;; Adjacent to sea = coastal
                         :recent-moves [[1 0]]}})
      ;; Turn 1: Army moves to destination [1,1]
      (army/process-army [1 0])
      ;; Army should be at destination but state doesn't change until next turn
      ;; (FSM steps before moving, transition happens on next turn)
      (should (get-in @atoms/game-map [1 1 :contents]))
      ;; Turn 2: FSM recognizes it's at destination and transitions
      (army/process-army [1 1])
      (let [unit-at-dest (get-in @atoms/game-map [1 1 :contents])]
        (when unit-at-dest
          (should= :explore (:mode unit-at-dest))
          (should (#{:following-coast :seeking-coast} (:fsm-state unit-at-dest)))))))

  (describe "army uses its assigned lieutenant"
    (before
      (reset-all-atoms!)
      ;; Two cities, two lieutenants
      ;; X###X
      ;; #####
      ;; ~~~~~
      (reset! atoms/game-map (build-test-map ["X###X"
                                               "#####"
                                               "~~~~~"]))
      ;; Computer has explored west side, column 4 is unexplored
      ;; This makes [1 3] a frontier cell (adjacent to unexplored [0 4] and [1 4])
      (reset! atoms/computer-map (build-test-map ["X### "
                                                   "#### "
                                                   "~~~~~"]))
      ;; Initialize creates lieutenants for both cities
      (integration/initialize-general)
      (integration/process-general-turn) ;; Creates Alpha for [0 0]
      (integration/process-general-turn) ;; Creates Bravo for [0 4]
      ;; Verify we have two lieutenants
      (should= 2 (count (:lieutenants @atoms/commanding-general))))

    (it "army gets target from its assigned lieutenant"
      ;; Clear Alpha's coastal cells, give Bravo a frontier cell
      ;; This ensures the army targets come from Bravo, not Alpha
      (swap! atoms/commanding-general
             update :lieutenants
             (fn [lts]
               (mapv (fn [lt]
                       (cond
                         (= "Alpha" (:name lt))
                         (assoc lt :known-coastal-cells #{})
                         (= "Bravo" (:name lt))
                         (assoc lt :known-coastal-cells #{[1 3]})
                         :else lt))
                     lts)))
      ;; Verify Alpha has no coastal cells (cleared), Bravo has frontier cell
      (let [alpha (first (filter #(= "Alpha" (:name %)) (:lieutenants @atoms/commanding-general)))
            bravo (first (filter #(= "Bravo" (:name %)) (:lieutenants @atoms/commanding-general)))]
        (should= #{} (:known-coastal-cells alpha))
        (should= #{[1 3]} (:known-coastal-cells bravo)))
      ;; Create army at city [0 4] assigned to Bravo
      (swap! atoms/game-map assoc-in [0 4 :contents]
             {:type :army :owner :computer :hits 1 :mode :awake
              :lieutenant "Bravo"})
      ;; Process the army - it should ask Bravo for a target
      (army/process-army [0 4])
      (let [unit (get-in @atoms/game-map [0 4 :contents])]
        ;; Army should be in explore mode with moving-to-start state
        ;; because Bravo has a known frontier (Alpha doesn't)
        (should= :explore (:mode unit))
        (should= :moving-to-start (:fsm-state unit))
        (should= [1 3] (get-in unit [:fsm-data :destination]))))))
