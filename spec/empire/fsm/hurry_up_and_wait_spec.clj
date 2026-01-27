(ns empire.fsm.hurry-up-and-wait-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.hurry-up-and-wait :as huaw]
            [empire.fsm.context :as context]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Hurry-Up-And-Wait FSM"

  (describe "create-hurry-up-and-wait-data"

    (it "starts in :moving when destination provided"
      (let [data (huaw/create-hurry-up-and-wait-data [5 5] [10 10])]
        (should= :moving (:fsm-state data))))

    (it "starts in [:terminal :arrived] when already at destination"
      (let [data (huaw/create-hurry-up-and-wait-data [5 5] [5 5])]
        (should= [:terminal :arrived] (:fsm-state data))))

    (it "starts in [:terminal :arrived] when no destination provided"
      (let [data (huaw/create-hurry-up-and-wait-data [5 5] nil)]
        (should= [:terminal :arrived] (:fsm-state data))))

    (it "sets position in fsm-data"
      (let [data (huaw/create-hurry-up-and-wait-data [5 5] [10 10])]
        (should= [5 5] (get-in data [:fsm-data :position]))))

    (it "sets destination in fsm-data"
      (let [data (huaw/create-hurry-up-and-wait-data [5 5] [10 10])]
        (should= [10 10] (get-in data [:fsm-data :destination]))))

    (it "sets unit-id in fsm-data"
      (let [data (huaw/create-hurry-up-and-wait-data [5 5] [10 10] :army-123)]
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

    (it "stays in :moving while pathfinding toward destination"
      (let [data (huaw/create-hurry-up-and-wait-data [1 1] [2 3])
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :moving (:fsm-state result))
        (should-contain :move-to (:fsm-data result))))

    (it "transitions to [:terminal :arrived] when reaching destination"
      (let [data (huaw/create-hurry-up-and-wait-data [2 3] [2 3])
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (assoc (:fsm-data data) :position [2 3] :destination [2 3])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= [:terminal :arrived] (:fsm-state result))))

    (it "returns :enter-sentry-mode true when arriving"
      (let [data (huaw/create-hurry-up-and-wait-data [2 3] [2 3])
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (assoc (:fsm-data data) :position [2 3] :destination [2 3])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= true (get-in result [:fsm-data :enter-sentry-mode]))))

    (it "emits :mission-ended event when arriving"
      (let [data (huaw/create-hurry-up-and-wait-data [2 3] [2 3] :army-123)
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (assoc (:fsm-data data) :position [2 3] :destination [2 3])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            mission-event (first (filter #(= :mission-ended (:type %)) (:event-queue result)))]
        (should-not-be-nil mission-event)
        (should= :arrived (get-in mission-event [:data :reason])))))

  (describe "destination blocked by friendly army"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~"
                                       "~#a##"
                                       "~####"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "transitions to :sidestepping-destination when destination blocked by army"
      (let [data (huaw/create-hurry-up-and-wait-data [1 1] [1 2])
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :sidestepping-destination (:fsm-state result))))

    (it "computes sidestep move when destination blocked"
      (let [data (huaw/create-hurry-up-and-wait-data [1 1] [1 2])
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            move-to (get-in result [:fsm-data :move-to])]
        (should-not-be-nil move-to))))

  (describe "destination blocked by friendly city"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~"
                                       "~#X##"
                                       "~####"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "transitions to :sidestepping-destination when destination is friendly city"
      (let [data (huaw/create-hurry-up-and-wait-data [1 1] [1 2])
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :sidestepping-destination (:fsm-state result)))))

  (describe "sidestepping to empty land"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~"
                                       "~#a##"
                                       "~####"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "arrives at empty land near destination"
      (let [data (huaw/create-hurry-up-and-wait-data [2 1] [1 2])
            entity {:fsm (:fsm data)
                    :fsm-state :sidestepping-destination
                    :fsm-data (assoc (:fsm-data data) :position [2 1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= [:terminal :arrived] (:fsm-state result)))))

  (describe "stuck detection"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~"
                                       "~#~"
                                       "~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "transitions to [:terminal :stuck] when no valid moves from :moving"
      (let [data (huaw/create-hurry-up-and-wait-data [1 1] [10 10])
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= [:terminal :stuck] (:fsm-state result))))

    (it "emits :mission-ended event with :stuck reason"
      (let [data (huaw/create-hurry-up-and-wait-data [1 1] [10 10] :army-456)
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
      (let [data (huaw/create-hurry-up-and-wait-data [1 1] [1 4])
            entity {:fsm (:fsm data)
                    :fsm-state :moving
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :moving (:fsm-state result))
        (should-contain :move-to (:fsm-data result))))))

(run-specs)
