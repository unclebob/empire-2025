(ns empire.fsm.squad-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.squad :as squad]
            [empire.fsm.engine :as engine]
            [empire.fsm.context :as context]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Squad FSM"

  (describe "create-squad"
    (it "creates squad in :assembling state"
      (let [sq (squad/create-squad :lt-1 [10 20] [:army-1 :army-2])]
        (should= :assembling (:fsm-state sq))))

    (it "sets target coordinates"
      (let [sq (squad/create-squad :lt-1 [10 20] [:army-1 :army-2])]
        (should= [10 20] (get-in sq [:fsm-data :target]))))

    (it "sets mission type to attack-city"
      (let [sq (squad/create-squad :lt-1 [10 20] [:army-1 :army-2])]
        (should= :attack-city (get-in sq [:fsm-data :mission-type]))))

    (it "stores expected unit ids"
      (let [sq (squad/create-squad :lt-1 [10 20] [:army-1 :army-2 :army-3])]
        (should= [:army-1 :army-2 :army-3] (get-in sq [:fsm-data :expected-units]))))

    (it "stores lieutenant id for reporting"
      (let [sq (squad/create-squad :lt-1 [10 20] [:army-1])]
        (should= :lt-1 (:lieutenant-id sq))))

    (it "starts with empty units list"
      (let [sq (squad/create-squad :lt-1 [10 20] [:army-1 :army-2])]
        (should= [] (:units sq))))

    (it "has FSM defined"
      (let [sq (squad/create-squad :lt-1 [10 20] [:army-1])]
        (should-not-be-nil (:fsm sq)))))

  (describe "state transitions"
    (before (reset-all-atoms!))

    (describe ":assembling → :moving"
      (it "transitions when all expected units have joined"
        (let [sq (-> (squad/create-squad :lt-1 [10 20] [:army-1 :army-2])
                     (assoc :units [{:unit-id :army-1} {:unit-id :army-2}]))
              result (squad/process-squad sq)]
          (should= :moving (:fsm-state result))))

      (it "stays in :assembling when not all units present"
        (let [sq (-> (squad/create-squad :lt-1 [10 20] [:army-1 :army-2])
                     (assoc :units [{:unit-id :army-1}]))
              result (squad/process-squad sq)]
          (should= :assembling (:fsm-state result)))))

    (describe ":moving → :attacking"
      (before
        (reset-all-atoms!)
        (reset! atoms/game-map (build-test-map ["###"
                                                 "#+"
                                                 "###"])))

      (it "transitions when any unit is adjacent to target"
        (let [sq (-> (squad/create-squad :lt-1 [1 1] [:army-1 :army-2])
                     (assoc :fsm-state :moving)
                     (assoc :units [{:unit-id :army-1 :coords [0 1]}
                                    {:unit-id :army-2 :coords [2 2]}]))
              result (squad/process-squad sq)]
          (should= :attacking (:fsm-state result))))

      (it "stays in :moving when no unit adjacent to target"
        (let [sq (-> (squad/create-squad :lt-1 [5 5] [:army-1 :army-2])
                     (assoc :fsm-state :moving)
                     (assoc :units [{:unit-id :army-1 :coords [0 0]}
                                    {:unit-id :army-2 :coords [9 9]}]))
              result (squad/process-squad sq)]
          (should= :moving (:fsm-state result)))))

    (describe ":attacking → :defending"
      (it "transitions when city-conquered event received"
        (let [sq (-> (squad/create-squad :lt-1 [1 1] [:army-1])
                     (assoc :fsm-state :attacking)
                     (engine/post-event {:type :city-conquered
                                         :priority :high
                                         :data {:coords [1 1]}
                                         :from :army-1}))
              result (squad/process-squad sq)]
          (should= :defending (:fsm-state result))))

      (it "stays in :attacking when no city-conquered event"
        (let [sq (-> (squad/create-squad :lt-1 [1 1] [:army-1])
                     (assoc :fsm-state :attacking))
              result (squad/process-squad sq)]
          (should= :attacking (:fsm-state result))))))

  (describe "unit management"
    (it "adds unit to squad via join-squad"
      (let [sq (squad/create-squad :lt-1 [10 20] [:army-1 :army-2])
            result (squad/join-squad sq {:unit-id :army-1 :coords [5 5]})]
        (should= 1 (count (:units result)))
        (should= :army-1 (:unit-id (first (:units result))))))

    (it "tracks multiple units"
      (let [sq (-> (squad/create-squad :lt-1 [10 20] [:army-1 :army-2])
                   (squad/join-squad {:unit-id :army-1 :coords [5 5]})
                   (squad/join-squad {:unit-id :army-2 :coords [6 6]}))]
        (should= 2 (count (:units sq))))))

  (describe "adjacency check"
    (it "returns true for horizontally adjacent cells"
      (should (squad/adjacent? [5 5] [5 6]))
      (should (squad/adjacent? [5 5] [5 4])))

    (it "returns true for vertically adjacent cells"
      (should (squad/adjacent? [5 5] [4 5]))
      (should (squad/adjacent? [5 5] [6 5])))

    (it "returns true for diagonally adjacent cells"
      (should (squad/adjacent? [5 5] [4 4]))
      (should (squad/adjacent? [5 5] [6 6])))

    (it "returns false for same cell"
      (should-not (squad/adjacent? [5 5] [5 5])))

    (it "returns false for non-adjacent cells"
      (should-not (squad/adjacent? [5 5] [5 7]))
      (should-not (squad/adjacent? [5 5] [7 5]))))

  (describe "terminal state"
    (it "squad can reach terminal state"
      (let [sq (-> (squad/create-squad :lt-1 [1 1] [:army-1])
                   (assoc :fsm-state [:terminal :mission-complete]))]
        (should (engine/terminal? sq)))))

  (describe "expanded squad functionality"
    (before (reset-all-atoms!))

    (describe "create-squad-for-city"
      (it "creates squad with target-city"
        (let [sq (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 5 100)]
          (should= [10 20] (get-in sq [:fsm-data :target-city]))))

      (it "creates squad with rally-point"
        (let [sq (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 5 100)]
          (should= [8 18] (get-in sq [:fsm-data :rally-point]))))

      (it "sets assembly-deadline"
        (let [sq (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 5 100)]
          (should= 110 (get-in sq [:fsm-data :assembly-deadline]))))

      (it "sets target-size"
        (let [sq (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 5 100)]
          (should= 5 (get-in sq [:fsm-data :target-size]))))

      (it "starts with empty armies list"
        (let [sq (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 5 100)]
          (should= [] (get-in sq [:fsm-data :armies]))))

      (it "starts with zero armies-present-count"
        (let [sq (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 5 100)]
          (should= 0 (get-in sq [:fsm-data :armies-present-count])))))

    (describe "army management"
      (it "adds army to squad with :rallying status"
        (let [sq (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 5 100)
              result (squad/add-army sq :army-1)]
          (should= 1 (count (get-in result [:fsm-data :armies])))
          (should= :rallying (:status (first (get-in result [:fsm-data :armies]))))))

      (it "marks army as :present when arrived"
        (let [sq (-> (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 3 100)
                     (squad/add-army :army-1))
              result (squad/mark-army-arrived sq :army-1 [8 18])]
          (should= :present (:status (first (get-in result [:fsm-data :armies]))))
          (should= 1 (get-in result [:fsm-data :armies-present-count]))))

      (it "marks army as :lost when destroyed"
        (let [sq (-> (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 3 100)
                     (squad/add-army :army-1)
                     (squad/mark-army-arrived :army-1 [8 18]))
              result (squad/mark-army-lost sq :army-1)]
          (should= :lost (:status (first (get-in result [:fsm-data :armies])))))))

    (describe "assembly completion"
      (before
        (reset-all-atoms!)
        (reset! atoms/round-number 100))

      (it "assembly-complete? returns true when all target armies present"
        (let [sq (-> (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 3 100)
                     (squad/add-army :army-1)
                     (squad/add-army :army-2)
                     (squad/add-army :army-3)
                     (squad/mark-army-arrived :army-1 [8 18])
                     (squad/mark-army-arrived :army-2 [8 17])
                     (squad/mark-army-arrived :army-3 [8 19]))
              ctx (context/build-context sq)]
          (should (squad/assembly-complete? ctx))))

      (it "assembly-complete? returns false when not enough armies present"
        (let [sq (-> (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 3 100)
                     (squad/add-army :army-1)
                     (squad/add-army :army-2)
                     (squad/mark-army-arrived :army-1 [8 18]))
              ctx (context/build-context sq)]
          (should-not (squad/assembly-complete? ctx)))))

    (describe "assembly timeout"
      (it "assembly-timeout? returns true when deadline passed with 3+ present"
        (reset! atoms/round-number 115)
        (let [sq (-> (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 5 100)
                     (squad/add-army :army-1)
                     (squad/add-army :army-2)
                     (squad/add-army :army-3)
                     (squad/mark-army-arrived :army-1 [8 18])
                     (squad/mark-army-arrived :army-2 [8 17])
                     (squad/mark-army-arrived :army-3 [8 19]))
              ctx (context/build-context sq)]
          (should (squad/assembly-timeout? ctx))))

      (it "assembly-timeout? returns false when deadline passed with <3 present"
        (reset! atoms/round-number 115)
        (let [sq (-> (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 5 100)
                     (squad/add-army :army-1)
                     (squad/add-army :army-2)
                     (squad/mark-army-arrived :army-1 [8 18])
                     (squad/mark-army-arrived :army-2 [8 17]))
              ctx (context/build-context sq)]
          (should-not (squad/assembly-timeout? ctx))))

      (it "assembly-timeout? returns false when deadline not yet passed"
        (reset! atoms/round-number 105)
        (let [sq (-> (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 5 100)
                     (squad/add-army :army-1)
                     (squad/add-army :army-2)
                     (squad/add-army :army-3)
                     (squad/mark-army-arrived :army-1 [8 18])
                     (squad/mark-army-arrived :army-2 [8 17])
                     (squad/mark-army-arrived :army-3 [8 19]))
              ctx (context/build-context sq)]
          (should-not (squad/assembly-timeout? ctx)))))

    (describe "squad destroyed detection"
      (it "squad-destroyed? returns true when all armies lost"
        (let [sq (-> (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 3 100)
                     (squad/add-army :army-1)
                     (squad/add-army :army-2)
                     (squad/mark-army-lost :army-1)
                     (squad/mark-army-lost :army-2))
              ctx (context/build-context sq)]
          (should (squad/squad-destroyed? ctx))))

      (it "squad-destroyed? returns false when some armies active"
        (let [sq (-> (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 3 100)
                     (squad/add-army :army-1)
                     (squad/add-army :army-2)
                     (squad/mark-army-arrived :army-1 [8 18])
                     (squad/mark-army-lost :army-2))
              ctx (context/build-context sq)]
          (should-not (squad/squad-destroyed? ctx)))))

    (describe "city conquered detection"
      (before
        (reset-all-atoms!)
        (reset! atoms/game-map (build-test-map ["###"
                                                 "#X#"
                                                 "###"])))

      (it "city-conquered? returns true when target city is computer-owned"
        (let [sq (squad/create-squad-for-city :squad-1 [1 1] [0 1] :lt-1 3 100)
              ctx (context/build-context sq)]
          (should (squad/city-conquered? ctx))))

      (it "city-conquered? returns false when target city is not computer-owned"
        (reset! atoms/game-map (build-test-map ["###"
                                                 "#+#"
                                                 "###"]))
        (let [sq (squad/create-squad-for-city :squad-1 [1 1] [0 1] :lt-1 3 100)
              ctx (context/build-context sq)]
          (should-not (squad/city-conquered? ctx)))))

    (describe "disband actions"
      (before (reset-all-atoms!))

      (it "disband-success-action emits :squad-mission-complete event"
        (let [sq (-> (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 3 100)
                     (squad/add-army :army-1)
                     (squad/add-army :army-2)
                     (squad/mark-army-arrived :army-1 [8 18])
                     (squad/mark-army-arrived :army-2 [8 17]))
              ctx (context/build-context sq)
              result (squad/disband-success-action ctx)
              complete-event (first (filter #(= :squad-mission-complete (:type %)) (:events result)))]
          (should-not-be-nil complete-event)
          (should= :success (get-in complete-event [:data :result]))
          (should= :lt-1 (:to complete-event))))

      (it "disband-failure-action emits :squad-mission-complete with :failed"
        (let [sq (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 3 100)
              ctx (context/build-context sq)
              result (squad/disband-failure-action ctx)
              complete-event (first (filter #(= :squad-mission-complete (:type %)) (:events result)))]
          (should-not-be-nil complete-event)
          (should= :failed (get-in complete-event [:data :result])))))

    (describe "get-surviving-armies"
      (it "returns only armies not marked as lost"
        (let [sq (-> (squad/create-squad-for-city :squad-1 [10 20] [8 18] :lt-1 3 100)
                     (squad/add-army :army-1)
                     (squad/add-army :army-2)
                     (squad/add-army :army-3)
                     (squad/mark-army-arrived :army-1 [8 18])
                     (squad/mark-army-lost :army-2)
                     (squad/mark-army-arrived :army-3 [8 19]))
              survivors (squad/get-surviving-armies sq)]
          (should= 2 (count survivors))
          (should= #{:army-1 :army-3} (set (map :unit-id survivors))))))))
