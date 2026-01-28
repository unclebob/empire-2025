(ns empire.fsm.board-transport-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.board-transport :as bt]
            [empire.fsm.context :as context]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Board-Transport FSM"

  (describe "create-board-transport-data"

    (it "starts in :moving-to-beach when not at beach cell"
      (let [data (bt/create-board-transport-data [5 5] :transport-1 [3 3] [4 3] :army-1)]
        (should= :moving-to-beach (:fsm-state data))))

    (it "starts in :boarding when already at assigned beach cell adjacent to transport"
      (let [data (bt/create-board-transport-data [4 3] :transport-1 [3 3] [4 3] :army-1)]
        (should= :boarding (:fsm-state data))))

    (it "sets position in fsm-data"
      (let [data (bt/create-board-transport-data [5 5] :transport-1 [3 3] [4 3] :army-1)]
        (should= [5 5] (get-in data [:fsm-data :position]))))

    (it "sets transport-id in fsm-data"
      (let [data (bt/create-board-transport-data [5 5] :transport-1 [3 3] [4 3] :army-1)]
        (should= :transport-1 (get-in data [:fsm-data :transport-id]))))

    (it "sets transport-landing in fsm-data"
      (let [data (bt/create-board-transport-data [5 5] :transport-1 [3 3] [4 3] :army-1)]
        (should= [3 3] (get-in data [:fsm-data :transport-landing]))))

    (it "sets assigned-beach-cell in fsm-data"
      (let [data (bt/create-board-transport-data [5 5] :transport-1 [3 3] [4 3] :army-1)]
        (should= [4 3] (get-in data [:fsm-data :assigned-beach-cell]))))

    (it "sets unit-id in fsm-data"
      (let [data (bt/create-board-transport-data [5 5] :transport-1 [3 3] [4 3] :army-1)]
        (should= :army-1 (get-in data [:fsm-data :unit-id])))))

  (describe "state transitions - moving to beach"
    (before
      (reset-all-atoms!)
      ;; Map: transport at [1 5], beach cells at [1 4], [2 4], [2 5]
      ;; Army starts at [2 2], needs to path to beach cell [2 4]
      (let [test-map (build-test-map ["~~~~~~~"
                                       "~~###t~"
                                       "~~#####"
                                       "~~~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "stays in :moving-to-beach while pathfinding toward beach cell"
      (let [data (bt/create-board-transport-data [2 2] :transport-1 [1 5] [2 4] :army-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving-to-beach
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :moving-to-beach (:fsm-state result))
        (should-contain :move-to (:fsm-data result))))

    (it "transitions to :boarding when adjacent to transport-landing"
      (let [data (bt/create-board-transport-data [2 4] :transport-1 [1 5] [2 4] :army-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving-to-beach
                    :fsm-data (assoc (:fsm-data data) :position [2 4])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :boarding (:fsm-state result)))))

  (describe "boarding transport"
    (before
      (reset-all-atoms!)
      ;; Map: transport at [1 5], army on beach cell [2 4] which is adjacent to transport
      (let [test-map (build-test-map ["~~~~~~~"
                                       "~~###t~"
                                       "~~#####"
                                       "~~~~~~~"])]
        (reset! atoms/game-map test-map)
        ;; Transport at [1 5] has army-count 0 (room available)
        (swap! atoms/game-map assoc-in [1 5 :army-count] 0)
        (reset! atoms/computer-map @atoms/game-map)))

    (it "transitions to [:terminal :boarded] when transport has room"
      (let [data (bt/create-board-transport-data [2 4] :transport-1 [1 5] [2 4] :army-1)
            entity {:fsm (:fsm data)
                    :fsm-state :boarding
                    :fsm-data (assoc (:fsm-data data) :position [2 4])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= [:terminal :boarded] (:fsm-state result))))

    (it "emits :army-boarded event when boarding"
      (let [data (bt/create-board-transport-data [2 4] :transport-1 [1 5] [2 4] :army-1)
            entity {:fsm (:fsm data)
                    :fsm-state :boarding
                    :fsm-data (assoc (:fsm-data data) :position [2 4])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            boarded-event (first (filter #(= :army-boarded (:type %)) (:event-queue result)))]
        (should-not-be-nil boarded-event)
        (should= :transport-1 (get-in boarded-event [:data :transport-id]))
        (should= :army-1 (get-in boarded-event [:data :army-id]))))

    (it "emits :mission-ended event when boarding"
      (let [data (bt/create-board-transport-data [2 4] :transport-1 [1 5] [2 4] :army-1)
            entity {:fsm (:fsm data)
                    :fsm-state :boarding
                    :fsm-data (assoc (:fsm-data data) :position [2 4])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            mission-event (first (filter #(= :mission-ended (:type %)) (:event-queue result)))]
        (should-not-be-nil mission-event)
        (should= :boarded (get-in mission-event [:data :reason]))))

    (it "returns :board-transport key with transport position"
      (let [data (bt/create-board-transport-data [2 4] :transport-1 [1 5] [2 4] :army-1)
            entity {:fsm (:fsm data)
                    :fsm-state :boarding
                    :fsm-data (assoc (:fsm-data data) :position [2 4])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= [1 5] (get-in result [:fsm-data :board-transport])))))

  (describe "transport gone"
    (before
      (reset-all-atoms!)
      ;; Map: no transport at expected location (just sea)
      (let [test-map (build-test-map ["~~~~~~"
                                       "~~##~~"
                                       "~~####"
                                       "~~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "transitions to [:terminal :aborted] when transport is gone from :moving-to-beach"
      (let [data (bt/create-board-transport-data [2 4] :transport-1 [1 4] [2 3] :army-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving-to-beach
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= [:terminal :aborted] (:fsm-state result))))

    (it "emits :mission-ended with :aborted reason when transport gone"
      (let [data (bt/create-board-transport-data [2 4] :transport-1 [1 4] [2 3] :army-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving-to-beach
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            mission-event (first (filter #(= :mission-ended (:type %)) (:event-queue result)))]
        (should-not-be-nil mission-event)
        (should= :aborted (get-in mission-event [:data :reason])))))

  (describe "stuck detection"
    (it "transitions to [:terminal :stuck] when no valid moves and transport gone"
      (reset-all-atoms!)
      ;; Army on isolated land cell, no transport
      (let [test-map (build-test-map ["~~~"
                                       "~#~"
                                       "~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)
        (let [;; Transport-landing at [0 1] (sea), but no transport there
              ;; Army at [1 1] (land), no valid moves (surrounded by sea)
              data (bt/create-board-transport-data [1 1] :transport-1 [0 1] [1 1] :army-1)
              entity {:fsm (:fsm data)
                      :fsm-state :moving-to-beach
                      :fsm-data (assoc (:fsm-data data) :position [1 1])
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          ;; stuck? is checked before transport-gone?, so should be stuck
          (should= [:terminal :stuck] (:fsm-state result)))))

    (it "transitions to [:terminal :aborted] when transport is gone"
      (reset-all-atoms!)
      ;; Army on land, can move, but transport is missing
      (let [test-map (build-test-map ["~~~~~"
                                       "~###~"
                                       "~###~"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)
        (let [;; Army at [1 1], transport-landing at [0 2] (sea, no transport)
              data (bt/create-board-transport-data [1 1] :transport-1 [0 2] [1 2] :army-1)
              entity {:fsm (:fsm data)
                      :fsm-state :moving-to-beach
                      :fsm-data (:fsm-data data)
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          ;; Not stuck (can move), but transport is gone
          (should= [:terminal :aborted] (:fsm-state result))))))

  (describe "transport full"
    (before
      (reset-all-atoms!)
      ;; Transport at [1 5] with army-count 6 (full)
      (let [test-map (build-test-map ["~~~~~~~"
                                       "~~###t~"
                                       "~~#####"
                                       "~~~~~~~"])]
        (reset! atoms/game-map test-map)
        (swap! atoms/game-map assoc-in [1 5 :army-count] 6)
        (reset! atoms/computer-map @atoms/game-map)))

    (it "stays in :boarding when transport is full (waiting for space)"
      (let [data (bt/create-board-transport-data [2 4] :transport-1 [1 5] [2 4] :army-1)
            entity {:fsm (:fsm data)
                    :fsm-state :boarding
                    :fsm-data (assoc (:fsm-data data) :position [2 4])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        ;; When transport is full, army waits in boarding state
        (should= :boarding (:fsm-state result))))))

(run-specs)
