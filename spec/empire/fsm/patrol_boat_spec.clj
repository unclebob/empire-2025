(ns empire.fsm.patrol-boat-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.patrol-boat :as patrol]
            [empire.fsm.context :as context]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Patrol Boat FSM"

  (describe "create-patrol-boat"

    (it "starts in :exploring state"
      (let [data (patrol/create-patrol-boat :pb-1 [5 5] :lt-1)]
        (should= :exploring (:fsm-state data))))

    (it "sets patrol-boat-id in fsm-data"
      (let [data (patrol/create-patrol-boat :pb-1 [5 5] :lt-1)]
        (should= :pb-1 (get-in data [:fsm-data :patrol-boat-id]))))

    (it "sets position in fsm-data"
      (let [data (patrol/create-patrol-boat :pb-1 [5 5] :lt-1)]
        (should= [5 5] (get-in data [:fsm-data :position]))))

    (it "sets lieutenant-id in fsm-data"
      (let [data (patrol/create-patrol-boat :pb-1 [5 5] :lt-1)]
        (should= :lt-1 (get-in data [:fsm-data :lieutenant-id]))))

    (it "starts with default explore-direction"
      (let [data (patrol/create-patrol-boat :pb-1 [5 5] :lt-1)]
        (should= [0 1] (get-in data [:fsm-data :explore-direction]))))

    (it "starts with nil coastline-data"
      (let [data (patrol/create-patrol-boat :pb-1 [5 5] :lt-1)]
        (should-be-nil (get-in data [:fsm-data :coastline-data]))))

    (it "starts with empty enemies-reported set"
      (let [data (patrol/create-patrol-boat :pb-1 [5 5] :lt-1)]
        (should= #{} (get-in data [:fsm-data :enemies-reported]))))

    (it "starts with empty continents-reported set"
      (let [data (patrol/create-patrol-boat :pb-1 [5 5] :lt-1)]
        (should= #{} (get-in data [:fsm-data :continents-reported])))))

  (describe "land-adjacent? guard"
    (before
      (reset-all-atoms!)
      ;; Sea with land at [1 2]
      (let [test-map (build-test-map ["~~~~~"
                                       "~~#~~"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)))

    (it "returns true when adjacent to land"
      (let [data (patrol/create-patrol-boat :pb-1 [1 1] :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (patrol/land-adjacent? ctx))))

    (it "returns false when not adjacent to land"
      (let [data (patrol/create-patrol-boat :pb-1 [0 0] :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data) :position [0 0])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (patrol/land-adjacent? ctx)))))

  (describe "enemy-adjacent? guard"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~"
                                       "~~~~~"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)))

    (it "returns true when enemy ship adjacent"
      (swap! atoms/game-map assoc-in [1 2 :contents] {:type :destroyer :owner :player})
      (let [data (patrol/create-patrol-boat :pb-1 [1 1] :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (patrol/enemy-adjacent? ctx))))

    (it "returns false when no enemy adjacent"
      (let [data (patrol/create-patrol-boat :pb-1 [1 1] :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (patrol/enemy-adjacent? ctx)))))

  (describe "unexplored-sea-nearby? guard"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~"
                                       "~~~~~"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "returns true when unexplored sea adjacent"
      ;; Make [1 2] unexplored
      (swap! atoms/computer-map assoc-in [1 2 :type] :unexplored)
      (let [data (patrol/create-patrol-boat :pb-1 [1 1] :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (patrol/unexplored-sea-nearby? ctx))))

    (it "returns false when all sea explored"
      (let [data (patrol/create-patrol-boat :pb-1 [1 1] :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (patrol/unexplored-sea-nearby? ctx)))))

  (describe "coast-complete? guard"
    (it "returns true when back at start with enough cells visited"
      (let [data (patrol/create-patrol-boat :pb-1 [1 1] :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :following-coast
                    :fsm-data (-> (:fsm-data data)
                                  (assoc :position [1 1])
                                  (assoc :coastline-data {:start-pos [1 1]
                                                          :coast-visited #{[1 1] [1 2] [1 3] [2 3] [2 2]}}))
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (patrol/coast-complete? ctx))))

    (it "returns false when not at start position"
      (let [data (patrol/create-patrol-boat :pb-1 [1 1] :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :following-coast
                    :fsm-data (-> (:fsm-data data)
                                  (assoc :position [1 3])
                                  (assoc :coastline-data {:start-pos [1 1]
                                                          :coast-visited #{[1 1] [1 2] [1 3]}}))
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (patrol/coast-complete? ctx))))

    (it "returns false when too few cells visited"
      (let [data (patrol/create-patrol-boat :pb-1 [1 1] :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :following-coast
                    :fsm-data (-> (:fsm-data data)
                                  (assoc :position [1 1])
                                  (assoc :coastline-data {:start-pos [1 1]
                                                          :coast-visited #{[1 1] [1 2]}}))
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (patrol/coast-complete? ctx)))))

  (describe "safe-distance? guard"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~~"
                                       "~~~~~~"
                                       "~~~~~~"
                                       "~~~~~~"
                                       "~~~~~~"
                                       "~~~~~~"])]
        (reset! atoms/game-map test-map)))

    (it "returns true when no enemies within 3 cells"
      (let [data (patrol/create-patrol-boat :pb-1 [1 1] :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :fleeing
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (patrol/safe-distance? ctx))))

    (it "returns false when enemy within 3 cells"
      (swap! atoms/game-map assoc-in [1 3 :contents] {:type :destroyer :owner :player})
      (let [data (patrol/create-patrol-boat :pb-1 [1 1] :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :fleeing
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (patrol/safe-distance? ctx)))))

  (describe "state transitions"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~"
                                       "~~#~~"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (describe ":exploring -> :following-coast"
      (it "transitions when land is adjacent"
        (let [data (patrol/create-patrol-boat :pb-1 [1 1] :lt-1)
              entity {:fsm (:fsm data)
                      :fsm-state :exploring
                      :fsm-data (assoc (:fsm-data data) :position [1 1])
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :following-coast (:fsm-state result)))))

    (describe ":exploring -> :fleeing"
      (it "transitions when enemy is adjacent"
        (swap! atoms/game-map assoc-in [0 0 :contents] {:type :destroyer :owner :player})
        (let [data (patrol/create-patrol-boat :pb-1 [0 1] :lt-1)
              entity {:fsm (:fsm data)
                      :fsm-state :exploring
                      :fsm-data (assoc (:fsm-data data) :position [0 1])
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :fleeing (:fsm-state result)))))

    (describe ":following-coast -> :exploring"
      (it "transitions when coast complete"
        (let [data (patrol/create-patrol-boat :pb-1 [0 0] :lt-1)
              entity {:fsm (:fsm data)
                      :fsm-state :following-coast
                      :fsm-data (-> (:fsm-data data)
                                    (assoc :position [0 0])
                                    (assoc :coastline-data {:start-pos [0 0]
                                                            :coast-visited #{[0 0] [0 1] [0 2] [1 3] [2 3]}}))
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :exploring (:fsm-state result)))))

    (describe ":fleeing -> :exploring"
      (it "transitions when at safe distance"
        (let [data (patrol/create-patrol-boat :pb-1 [2 2] :lt-1)
              entity {:fsm (:fsm data)
                      :fsm-state :fleeing
                      :fsm-data (assoc (:fsm-data data) :position [2 2])
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :exploring (:fsm-state result))))))

  (describe "actions"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~"
                                       "~~#~~"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (describe "begin-coast-follow-action"
      (it "sets coastline-data with start position"
        (let [data (patrol/create-patrol-boat :pb-1 [1 1] :lt-1)
              entity {:fsm (:fsm data)
                      :fsm-state :exploring
                      :fsm-data (assoc (:fsm-data data) :position [1 1])
                      :event-queue []}
              ctx (context/build-context entity)
              result (patrol/begin-coast-follow-action ctx)]
          (should= [1 1] (get-in result [:coastline-data :start-pos]))))

      (it "emits continent-found event for new discovery"
        (let [data (patrol/create-patrol-boat :pb-1 [1 1] :lt-1)
              entity {:fsm (:fsm data)
                      :fsm-state :exploring
                      :fsm-data (assoc (:fsm-data data) :position [1 1])
                      :event-queue []}
              ctx (context/build-context entity)
              result (patrol/begin-coast-follow-action ctx)
              event (first (:events result))]
          (should-not-be-nil event)
          (should= :continent-found (:type event)))))

    (describe "flee-and-report-action"
      (before
        (swap! atoms/game-map assoc-in [1 3 :contents] {:type :destroyer :owner :player}))

      (it "emits enemy-spotted event"
        (let [data (patrol/create-patrol-boat :pb-1 [1 2] :lt-1)
              entity {:fsm (:fsm data)
                      :fsm-state :exploring
                      :fsm-data (assoc (:fsm-data data) :position [1 2])
                      :event-queue []}
              ctx (context/build-context entity)
              result (patrol/flee-and-report-action ctx)
              event (first (:events result))]
          (should-not-be-nil event)
          (should= :enemy-spotted (:type event)))))

    (describe "resume-explore-action"
      (it "clears coastline-data"
        (let [data (patrol/create-patrol-boat :pb-1 [1 1] :lt-1)
              entity {:fsm (:fsm data)
                      :fsm-state :following-coast
                      :fsm-data (-> (:fsm-data data)
                                    (assoc :coastline-data {:start-pos [0 0]
                                                            :coast-visited #{[0 0]}}))
                      :event-queue []}
              ctx (context/build-context entity)
              result (patrol/resume-explore-action ctx)]
          (should-be-nil (:coastline-data result)))))))

(run-specs)
