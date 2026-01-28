(ns empire.fsm.move-with-squad-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.move-with-squad :as move-squad]
            [empire.fsm.context :as context]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Move-with-Squad FSM"

  (describe "create-move-with-squad-data"

    (it "starts in :awaiting-orders state"
      (let [data (move-squad/create-move-with-squad-data [5 5] :squad-1)]
        (should= :awaiting-orders (:fsm-state data))))

    (it "sets mission-type to :move-with-squad"
      (let [data (move-squad/create-move-with-squad-data [5 5] :squad-1)]
        (should= :move-with-squad (get-in data [:fsm-data :mission-type]))))

    (it "sets position in fsm-data"
      (let [data (move-squad/create-move-with-squad-data [5 5] :squad-1)]
        (should= [5 5] (get-in data [:fsm-data :position]))))

    (it "sets squad-id in fsm-data"
      (let [data (move-squad/create-move-with-squad-data [5 5] :squad-1)]
        (should= :squad-1 (get-in data [:fsm-data :squad-id]))))

    (it "starts with nil ordered-position"
      (let [data (move-squad/create-move-with-squad-data [5 5] :squad-1)]
        (should-be-nil (get-in data [:fsm-data :ordered-position]))))

    (it "accepts optional unit-id"
      (let [data (move-squad/create-move-with-squad-data [5 5] :squad-1 :army-1)]
        (should= :army-1 (get-in data [:fsm-data :unit-id]))))

    (it "initializes recent-moves with starting position"
      (let [data (move-squad/create-move-with-squad-data [5 5] :squad-1)]
        (should= [[5 5]] (get-in data [:fsm-data :recent-moves])))))

  (describe "has-move-order? guard"
    (it "returns true when move-order event in queue"
      (let [data (move-squad/create-move-with-squad-data [5 5] :squad-1)
            entity (assoc data :event-queue [{:type :move-order :data {:target [3 3]}}])
            ctx (context/build-context entity)]
        (should (move-squad/has-move-order? ctx))))

    (it "returns false when no move-order event"
      (let [data (move-squad/create-move-with-squad-data [5 5] :squad-1)
            entity (assoc data :event-queue [])
            ctx (context/build-context entity)]
        (should-not (move-squad/has-move-order? ctx)))))

  (describe "squad-attacking? guard"
    (it "returns true when squad-attacking event in queue"
      (let [data (move-squad/create-move-with-squad-data [5 5] :squad-1)
            entity (assoc data :event-queue [{:type :squad-attacking :data {}}])
            ctx (context/build-context entity)]
        (should (move-squad/squad-attacking? ctx))))

    (it "returns false when no squad-attacking event"
      (let [data (move-squad/create-move-with-squad-data [5 5] :squad-1)
            entity (assoc data :event-queue [])
            ctx (context/build-context entity)]
        (should-not (move-squad/squad-attacking? ctx)))))

  (describe "squad-disbanded? guard"
    (it "returns true when squad-disbanded event in queue"
      (let [data (move-squad/create-move-with-squad-data [5 5] :squad-1)
            entity (assoc data :event-queue [{:type :squad-disbanded :data {}}])
            ctx (context/build-context entity)]
        (should (move-squad/squad-disbanded? ctx))))

    (it "returns false when no squad-disbanded event"
      (let [data (move-squad/create-move-with-squad-data [5 5] :squad-1)
            entity (assoc data :event-queue [])
            ctx (context/build-context entity)]
        (should-not (move-squad/squad-disbanded? ctx)))))

  (describe "at-ordered-position? guard"
    (it "returns true when position equals ordered-position"
      (let [data (move-squad/create-move-with-squad-data [5 5] :squad-1)
            entity {:fsm (:fsm data)
                    :fsm-state :executing-move
                    :fsm-data (assoc (:fsm-data data) :ordered-position [5 5])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (move-squad/at-ordered-position? ctx))))

    (it "returns false when position differs from ordered-position"
      (let [data (move-squad/create-move-with-squad-data [5 5] :squad-1)
            entity {:fsm (:fsm data)
                    :fsm-state :executing-move
                    :fsm-data (assoc (:fsm-data data) :ordered-position [3 3])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (move-squad/at-ordered-position? ctx)))))

  (describe "state transitions"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["#####"
                                       "#####"
                                       "#####"
                                       "#####"
                                       "#####"])]
        (reset! atoms/game-map test-map)))

    (describe ":awaiting-orders -> [:terminal :disbanded]"
      (it "transitions when squad disbanded"
        (let [data (move-squad/create-move-with-squad-data [2 2] :squad-1)
              entity (assoc data :event-queue [{:type :squad-disbanded :data {}}])
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= [:terminal :disbanded] (:fsm-state result)))))

    (describe ":awaiting-orders -> [:terminal :attack-mode]"
      (it "transitions when squad attacking"
        (let [data (move-squad/create-move-with-squad-data [2 2] :squad-1)
              entity (assoc data :event-queue [{:type :squad-attacking :data {}}])
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= [:terminal :attack-mode] (:fsm-state result)))))

    (describe ":awaiting-orders -> :executing-move"
      (it "transitions when move order received"
        (let [data (move-squad/create-move-with-squad-data [2 2] :squad-1)
              entity (assoc data :event-queue [{:type :move-order :data {:target [3 3]}}])
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :executing-move (:fsm-state result)))))

    (describe ":executing-move -> :awaiting-orders"
      (it "transitions when at ordered position"
        (let [data (move-squad/create-move-with-squad-data [3 3] :squad-1)
              entity {:fsm (:fsm data)
                      :fsm-state :executing-move
                      :fsm-data (assoc (:fsm-data data) :ordered-position [3 3])
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :awaiting-orders (:fsm-state result))))))

  (describe "actions"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["#####"
                                       "#####"
                                       "#####"
                                       "#####"
                                       "#####"])]
        (reset! atoms/game-map test-map)))

    (describe "terminal-disbanded-action"
      (it "emits mission-ended event with :disbanded reason"
        (let [data (move-squad/create-move-with-squad-data [2 2] :squad-1 :army-1)
              entity (assoc data :event-queue [])
              ctx (context/build-context entity)
              result (move-squad/terminal-disbanded-action ctx)
              event (first (:events result))]
          (should= :mission-ended (:type event))
          (should= :disbanded (get-in event [:data :reason])))))

    (describe "terminal-attack-action"
      (it "emits mission-ended event with :attack-mode reason"
        (let [data (move-squad/create-move-with-squad-data [2 2] :squad-1 :army-1)
              entity (assoc data :event-queue [])
              ctx (context/build-context entity)
              result (move-squad/terminal-attack-action ctx)
              event (first (:events result))]
          (should= :mission-ended (:type event))
          (should= :attack-mode (get-in event [:data :reason])))))

    (describe "accept-order-action"
      (it "sets ordered-position from event"
        (let [data (move-squad/create-move-with-squad-data [2 2] :squad-1)
              entity (assoc data :event-queue [{:type :move-order :data {:target [4 4]}}])
              ctx (context/build-context entity)
              result (move-squad/accept-order-action ctx)]
          (should= [4 4] (:ordered-position result)))))

    (describe "report-position-action"
      (it "emits unit-position-report event"
        (let [data (move-squad/create-move-with-squad-data [3 3] :squad-1 :army-1)
              entity {:fsm (:fsm data)
                      :fsm-state :executing-move
                      :fsm-data (:fsm-data data)
                      :event-queue []}
              ctx (context/build-context entity)
              result (move-squad/report-position-action ctx)
              event (first (:events result))]
          (should= :unit-position-report (:type event))
          (should= [3 3] (get-in event [:data :coords]))))

      (it "clears ordered-position"
        (let [data (move-squad/create-move-with-squad-data [3 3] :squad-1)
              entity {:fsm (:fsm data)
                      :fsm-state :executing-move
                      :fsm-data (assoc (:fsm-data data) :ordered-position [3 3])
                      :event-queue []}
              ctx (context/build-context entity)
              result (move-squad/report-position-action ctx)]
          (should-be-nil (:ordered-position result)))))

    (describe "report-blocked-action"
      (it "emits unit-blocked event"
        (let [data (move-squad/create-move-with-squad-data [2 2] :squad-1 :army-1)
              entity {:fsm (:fsm data)
                      :fsm-state :executing-move
                      :fsm-data (:fsm-data data)
                      :event-queue []}
              ctx (context/build-context entity)
              result (move-squad/report-blocked-action ctx)
              event (first (:events result))]
          (should= :unit-blocked (:type event))
          (should= [2 2] (get-in event [:data :coords]))))

      (it "clears ordered-position"
        (let [data (move-squad/create-move-with-squad-data [2 2] :squad-1)
              entity {:fsm (:fsm data)
                      :fsm-state :executing-move
                      :fsm-data (assoc (:fsm-data data) :ordered-position [4 4])
                      :event-queue []}
              ctx (context/build-context entity)
              result (move-squad/report-blocked-action ctx)]
          (should-be-nil (:ordered-position result)))))))

(run-specs)
