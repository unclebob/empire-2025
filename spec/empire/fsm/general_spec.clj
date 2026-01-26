(ns empire.fsm.general-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.general :as general]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "General FSM"

  (describe "create-general"
    (it "creates general in :awaiting-city state"
      (let [g (general/create-general)]
        (should= :awaiting-city (:fsm-state g))))

    (it "creates general with empty lieutenants list"
      (let [g (general/create-general)]
        (should= [] (:lieutenants g))))

    (it "creates general with empty event queue"
      (let [g (general/create-general)]
        (should= [] (:event-queue g))))

    (it "creates general with FSM defined"
      (let [g (general/create-general)]
        (should-not-be-nil (:fsm g)))))

  (describe "state transitions"
    (before (reset-all-atoms!))

    (it "transitions from :awaiting-city to :operational when city-needs-orders received"
      (let [g (general/create-general)
            g-with-event (engine/post-event g {:type :city-needs-orders
                                                :priority :high
                                                :data {:coords [10 20]}
                                                :from :game})
            result (general/process-general g-with-event)]
        (should= :operational (:fsm-state result))))

    (it "stays in :awaiting-city when no events"
      (let [g (general/create-general)
            result (general/process-general g)]
        (should= :awaiting-city (:fsm-state result))))

    (it "stays in :operational once transitioned"
      (let [g (-> (general/create-general)
                  (assoc :fsm-state :operational))
            result (general/process-general g)]
        (should= :operational (:fsm-state result)))))

  (describe "handling :city-needs-orders"
    (before (reset-all-atoms!))

    (it "creates a lieutenant when handling city-needs-orders"
      (let [g (general/create-general)
            g-with-event (engine/post-event g {:type :city-needs-orders
                                                :priority :high
                                                :data {:coords [10 20]}
                                                :from :game})
            result (general/process-general g-with-event)]
        (should= 1 (count (:lieutenants result)))))

    (it "assigns city coords to new lieutenant"
      (let [g (general/create-general)
            g-with-event (engine/post-event g {:type :city-needs-orders
                                                :priority :high
                                                :data {:coords [10 20]}
                                                :from :game})
            result (general/process-general g-with-event)
            lt (first (:lieutenants result))]
        (should= [[10 20]] (:cities lt))))

    (it "consumes the event from queue"
      (let [g (general/create-general)
            g-with-event (engine/post-event g {:type :city-needs-orders
                                                :priority :high
                                                :data {:coords [10 20]}
                                                :from :game})
            result (general/process-general g-with-event)]
        (should= [] (:event-queue result))))

    (it "names lieutenants sequentially (Alpha, Bravo, Charlie...)"
      (let [g (general/create-general)
            g1 (-> g
                   (engine/post-event {:type :city-needs-orders :priority :high
                                       :data {:coords [10 20]} :from :game})
                   general/process-general)
            g2 (-> g1
                   (engine/post-event {:type :city-needs-orders :priority :high
                                       :data {:coords [30 40]} :from :game})
                   general/process-general)
            g3 (-> g2
                   (engine/post-event {:type :city-needs-orders :priority :high
                                       :data {:coords [50 60]} :from :game})
                   general/process-general)]
        (should= "Alpha" (:name (nth (:lieutenants g3) 0)))
        (should= "Bravo" (:name (nth (:lieutenants g3) 1)))
        (should= "Charlie" (:name (nth (:lieutenants g3) 2)))))

    (it "stores lieutenant name on the city cell"
      (reset! atoms/game-map (build-test-map ["~X~"
                                               "~~~"]))
      (let [g (general/create-general)
            g-with-event (engine/post-event g {:type :city-needs-orders
                                                :priority :high
                                                :data {:coords [0 1]}})
            result (general/process-general g-with-event)
            city-cell (get-in @atoms/game-map [0 1])]
        (should= "Alpha" (:lieutenant city-cell)))))

  (describe "process-general"
    (before (reset-all-atoms!))

    (it "processes multiple events in priority order"
      (let [g (-> (general/create-general)
                  (engine/post-event {:type :city-needs-orders :priority :normal
                                      :data {:coords [10 20]} :from :game})
                  (engine/post-event {:type :city-needs-orders :priority :high
                                      :data {:coords [30 40]} :from :game}))
            result (general/process-general g)]
        ;; High priority event [30 40] should be processed first
        ;; But after one process call, only one event is handled
        (should= 1 (count (:lieutenants result)))
        (should= [[30 40]] (:cities (first (:lieutenants result))))))

    (it "can be called repeatedly to process all events"
      (let [g (-> (general/create-general)
                  (engine/post-event {:type :city-needs-orders :priority :high
                                      :data {:coords [10 20]} :from :game})
                  (engine/post-event {:type :city-needs-orders :priority :high
                                      :data {:coords [30 40]} :from :game}))
            result (-> g
                       general/process-general
                       general/process-general)]
        (should= 2 (count (:lieutenants result)))
        (should= [] (:event-queue result))))))
