(ns empire.fsm.squad-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.squad :as squad]
            [empire.fsm.engine :as engine]
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
        (should (engine/terminal? sq))))))
