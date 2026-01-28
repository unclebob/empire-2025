(ns empire.fsm.rally-to-squad-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.rally-to-squad :as rts]
            [empire.fsm.context :as context]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Rally-to-Squad FSM"

  (describe "create-rally-to-squad-data"

    (it "starts in :moving when rally-point provided"
      (let [data (rts/create-rally-to-squad-data [5 5] [10 10] :squad-1)]
        (should= :moving (:fsm-state data))))

    (it "starts in [:terminal :joined] when already at rally-point"
      (let [data (rts/create-rally-to-squad-data [5 5] [5 5] :squad-1)]
        (should= [:terminal :joined] (:fsm-state data))))

    (it "sets position in fsm-data"
      (let [data (rts/create-rally-to-squad-data [5 5] [10 10] :squad-1)]
        (should= [5 5] (get-in data [:fsm-data :position]))))

    (it "sets rally-point in fsm-data"
      (let [data (rts/create-rally-to-squad-data [5 5] [10 10] :squad-1)]
        (should= [10 10] (get-in data [:fsm-data :rally-point]))))

    (it "sets squad-id in fsm-data"
      (let [data (rts/create-rally-to-squad-data [5 5] [10 10] :squad-1)]
        (should= :squad-1 (get-in data [:fsm-data :squad-id]))))

    (it "sets unit-id in fsm-data"
      (let [data (rts/create-rally-to-squad-data [5 5] [10 10] :squad-1 :army-123)]
        (should= :army-123 (get-in data [:fsm-data :unit-id])))))

  (describe "state transitions - normal pathfinding"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~"
                                       "~####"
                                       "~####"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "stays in :moving while pathfinding toward rally-point"
      (let [data (rts/create-rally-to-squad-data [1 1] [2 3] :squad-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :moving (:fsm-state result))
        (should-contain :move-to (:fsm-data result))))

    (it "transitions to [:terminal :joined] when reaching rally-point"
      (let [data (rts/create-rally-to-squad-data [2 3] [2 3] :squad-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (assoc (:fsm-data data) :position [2 3] :rally-point [2 3])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= [:terminal :joined] (:fsm-state result))))

    (it "emits :unit-arrived event when joining squad"
      (let [data (rts/create-rally-to-squad-data [2 3] [2 3] :squad-1 :army-123)
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (assoc (:fsm-data data) :position [2 3] :rally-point [2 3])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            arrived-event (first (filter #(= :unit-arrived (:type %)) (:event-queue result)))]
        (should-not-be-nil arrived-event)
        (should= :squad-1 (get-in arrived-event [:data :squad-id]))
        (should= :army-123 (get-in arrived-event [:data :unit-id]))
        (should= [2 3] (get-in arrived-event [:data :coords]))))

    (it "emits :mission-ended event when joining squad"
      (let [data (rts/create-rally-to-squad-data [2 3] [2 3] :squad-1 :army-123)
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (assoc (:fsm-data data) :position [2 3] :rally-point [2 3])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            mission-event (first (filter #(= :mission-ended (:type %)) (:event-queue result)))]
        (should-not-be-nil mission-event)
        (should= :joined (get-in mission-event [:data :reason])))))

  (describe "rally-point blocked by friendly army"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~"
                                       "~#a##"
                                       "~####"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "transitions to :sidestepping-rally when rally-point blocked"
      (let [data (rts/create-rally-to-squad-data [1 1] [1 2] :squad-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :sidestepping-rally (:fsm-state result))))

    (it "computes sidestep move when rally-point blocked"
      (let [data (rts/create-rally-to-squad-data [1 1] [1 2] :squad-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            move-to (get-in result [:fsm-data :move-to])]
        (should-not-be-nil move-to))))

  (describe "sidestepping to adjacent land near rally-point"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~"
                                       "~#a##"
                                       "~####"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "joins squad when on empty land adjacent to rally-point"
      (let [data (rts/create-rally-to-squad-data [2 1] [1 2] :squad-1 :army-123)
            entity {:fsm (:fsm data)
                    :fsm-state :sidestepping-rally
                    :fsm-data (assoc (:fsm-data data) :position [2 1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= [:terminal :joined] (:fsm-state result))))

    (it "emits :unit-arrived when joining via sidestep"
      (let [data (rts/create-rally-to-squad-data [2 1] [1 2] :squad-1 :army-123)
            entity {:fsm (:fsm data)
                    :fsm-state :sidestepping-rally
                    :fsm-data (assoc (:fsm-data data) :position [2 1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            arrived-event (first (filter #(= :unit-arrived (:type %)) (:event-queue result)))]
        (should-not-be-nil arrived-event)
        (should= :squad-1 (get-in arrived-event [:data :squad-id])))))

  (describe "stuck detection"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~"
                                       "~#~"
                                       "~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "transitions to [:terminal :stuck] when no valid moves from :moving"
      (let [data (rts/create-rally-to-squad-data [1 1] [10 10] :squad-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= [:terminal :stuck] (:fsm-state result))))

    (it "emits :mission-ended event with :stuck reason"
      (let [data (rts/create-rally-to-squad-data [1 1] [10 10] :squad-1 :army-456)
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            mission-event (first (filter #(= :mission-ended (:type %)) (:event-queue result)))]
        (should-not-be-nil mission-event)
        (should= :stuck (get-in mission-event [:data :reason])))))

  (describe "sidestep along path"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~~"
                                       "~#a###"
                                       "~#####"
                                       "~~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "sidesteps around blocking army en route"
      (let [data (rts/create-rally-to-squad-data [1 1] [1 4] :squad-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :moving (:fsm-state result))
        (should-contain :move-to (:fsm-data result))))))

(run-specs)
