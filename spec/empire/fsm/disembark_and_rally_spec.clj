(ns empire.fsm.disembark-and-rally-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.disembark-and-rally :as dar]
            [empire.fsm.context :as context]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Disembark-and-Rally FSM"

  (describe "create-disembark-and-rally-data"

    (it "starts in :disembarked when rally-point provided"
      (let [data (dar/create-disembark-and-rally-data [5 5] [10 10] :lt-new :transport-1)]
        (should= :disembarked (:fsm-state data))))

    (it "starts in :disembarked when no rally-point provided"
      (let [data (dar/create-disembark-and-rally-data [5 5] nil :lt-new :transport-1)]
        (should= :disembarked (:fsm-state data))))

    (it "sets position in fsm-data"
      (let [data (dar/create-disembark-and-rally-data [5 5] [10 10] :lt-new :transport-1)]
        (should= [5 5] (get-in data [:fsm-data :position]))))

    (it "sets rally-point in fsm-data"
      (let [data (dar/create-disembark-and-rally-data [5 5] [10 10] :lt-new :transport-1)]
        (should= [10 10] (get-in data [:fsm-data :rally-point]))))

    (it "sets new-lieutenant-id in fsm-data"
      (let [data (dar/create-disembark-and-rally-data [5 5] [10 10] :lt-new :transport-1)]
        (should= :lt-new (get-in data [:fsm-data :new-lieutenant-id]))))

    (it "sets transport-id in fsm-data"
      (let [data (dar/create-disembark-and-rally-data [5 5] [10 10] :lt-new :transport-1)]
        (should= :transport-1 (get-in data [:fsm-data :transport-id]))))

    (it "sets unit-id in fsm-data"
      (let [data (dar/create-disembark-and-rally-data [5 5] [10 10] :lt-new :transport-1 :army-123)]
        (should= :army-123 (get-in data [:fsm-data :unit-id])))))

  (describe "state transitions - from disembarked with rally-point"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~"
                                       "~####"
                                       "~####"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "transitions to :moving-inland when rally-point is assigned"
      (let [data (dar/create-disembark-and-rally-data [1 1] [2 3] :lt-new :transport-1)
            entity {:fsm (:fsm data)
                    :fsm-state :disembarked
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :moving-inland (:fsm-state result))
        (should-contain :move-to (:fsm-data result)))))

  (describe "state transitions - from disembarked without rally-point"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~"
                                       "~####"
                                       "~####"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "stays in :disembarked and requests orders when no rally-point"
      (let [data (dar/create-disembark-and-rally-data [1 1] nil :lt-new :transport-1 :army-123)
            entity {:fsm (:fsm data)
                    :fsm-state :disembarked
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :disembarked (:fsm-state result))))

    (it "emits :unit-needs-rally-point event when no rally-point"
      (let [data (dar/create-disembark-and-rally-data [1 1] nil :lt-new :transport-1 :army-123)
            entity {:fsm (:fsm data)
                    :fsm-state :disembarked
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            needs-rally-event (first (filter #(= :unit-needs-rally-point (:type %)) (:event-queue result)))]
        (should-not-be-nil needs-rally-event)
        (should= :army-123 (get-in needs-rally-event [:data :unit-id])))))

  (describe "state transitions - moving inland"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~"
                                       "~####"
                                       "~####"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "stays in :moving-inland while pathfinding toward rally-point"
      (let [data (dar/create-disembark-and-rally-data [1 1] [2 3] :lt-new :transport-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving-inland
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :moving-inland (:fsm-state result))
        (should-contain :move-to (:fsm-data result))))

    (it "transitions to [:terminal :reported] when reaching rally-point"
      (let [data (dar/create-disembark-and-rally-data [2 3] [2 3] :lt-new :transport-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving-inland
                    :fsm-data (assoc (:fsm-data data) :position [2 3] :rally-point [2 3])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= [:terminal :reported] (:fsm-state result))))

    (it "emits :unit-needs-orders event when reaching rally-point"
      (let [data (dar/create-disembark-and-rally-data [2 3] [2 3] :lt-new :transport-1 :army-123)
            entity {:fsm (:fsm data)
                    :fsm-state :moving-inland
                    :fsm-data (assoc (:fsm-data data) :position [2 3] :rally-point [2 3])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            needs-orders-event (first (filter #(= :unit-needs-orders (:type %)) (:event-queue result)))]
        (should-not-be-nil needs-orders-event)
        (should= :lt-new (get-in needs-orders-event [:to]))
        (should= :army-123 (get-in needs-orders-event [:data :unit-id]))
        (should= [2 3] (get-in needs-orders-event [:data :coords]))))

    (it "emits :mission-ended event when reaching rally-point"
      (let [data (dar/create-disembark-and-rally-data [2 3] [2 3] :lt-new :transport-1 :army-123)
            entity {:fsm (:fsm data)
                    :fsm-state :moving-inland
                    :fsm-data (assoc (:fsm-data data) :position [2 3] :rally-point [2 3])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            mission-event (first (filter #(= :mission-ended (:type %)) (:event-queue result)))]
        (should-not-be-nil mission-event)
        (should= :reported (get-in mission-event [:data :reason])))))

  (describe "rally-point blocked"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~"
                                       "~#a##"
                                       "~####"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "finds alternate rally position when rally-point blocked"
      (let [data (dar/create-disembark-and-rally-data [1 1] [1 2] :lt-new :transport-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving-inland
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        ;; Should sidestep toward blocked rally point
        (should= :moving-inland (:fsm-state result))
        (should-contain :move-to (:fsm-data result)))))

  (describe "stuck detection"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~"
                                       "~#~"
                                       "~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "transitions to [:terminal :stuck] when no valid moves"
      (let [data (dar/create-disembark-and-rally-data [1 1] [10 10] :lt-new :transport-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving-inland
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= [:terminal :stuck] (:fsm-state result))))

    (it "emits :mission-ended event with :stuck reason"
      (let [data (dar/create-disembark-and-rally-data [1 1] [10 10] :lt-new :transport-1 :army-456)
            entity {:fsm (:fsm data)
                    :fsm-state :moving-inland
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            mission-event (first (filter #(= :mission-ended (:type %)) (:event-queue result)))]
        (should-not-be-nil mission-event)
        (should= :stuck (get-in mission-event [:data :reason]))))))

(run-specs)
