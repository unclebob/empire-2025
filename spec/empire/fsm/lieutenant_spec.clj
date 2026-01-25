(ns empire.fsm.lieutenant-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.lieutenant :as lieutenant]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Lieutenant FSM"

  (describe "create-lieutenant"
    (it "creates lieutenant in :initializing state"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (should= :initializing (:fsm-state lt))))

    (it "assigns the given name"
      (let [lt (lieutenant/create-lieutenant "Bravo" [10 20])]
        (should= "Bravo" (:name lt))))

    (it "assigns the city to cities list"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (should= [[10 20]] (:cities lt))))

    (it "starts with empty direct-reports"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (should= [] (:direct-reports lt))))

    (it "starts with empty free-cities-known"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (should= [] (:free-cities-known lt))))

    (it "has FSM defined"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (should-not-be-nil (:fsm lt)))))

  (describe "state transitions"
    (before (reset-all-atoms!))

    (it "transitions from :initializing to :exploring when unit-needs-orders received"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])
            lt-with-event (engine/post-event lt {:type :unit-needs-orders
                                                  :priority :high
                                                  :data {:unit-id :army-1 :coords [10 20]}
                                                  :from :army-1})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= :exploring (:fsm-state result))))

    (it "stays in :initializing when no events"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])
            result (lieutenant/process-lieutenant lt)]
        (should= :initializing (:fsm-state result)))))

  (describe "handling :unit-needs-orders"
    (before (reset-all-atoms!))

    (it "adds unit to direct-reports as explorer"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])
            lt-with-event (engine/post-event lt {:type :unit-needs-orders
                                                  :priority :high
                                                  :data {:unit-id :army-1 :coords [11 20]}
                                                  :from :army-1})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= 1 (count (:direct-reports result)))))

    (it "assigns explorer mission to new unit"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])
            lt-with-event (engine/post-event lt {:type :unit-needs-orders
                                                  :priority :high
                                                  :data {:unit-id :army-1 :coords [11 20]}
                                                  :from :army-1})
            result (lieutenant/process-lieutenant lt-with-event)
            unit (first (:direct-reports result))]
        (should (#{:explore-coastline :explore-interior} (:mission-type unit)))))

    (it "consumes the event from queue"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])
            lt-with-event (engine/post-event lt {:type :unit-needs-orders
                                                  :priority :high
                                                  :data {:unit-id :army-1 :coords [11 20]}
                                                  :from :army-1})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= [] (:event-queue result)))))

  (describe "handling :free-city-found"
    (before (reset-all-atoms!))

    (it "adds city to free-cities-known list"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :exploring))
            lt-with-event (engine/post-event lt {:type :free-city-found
                                                  :priority :normal
                                                  :data {:coords [30 40]}
                                                  :from :army-1})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= [[30 40]] (:free-cities-known result))))

    (it "does not add duplicate cities"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :exploring)
                   (assoc :free-cities-known [[30 40]]))
            lt-with-event (engine/post-event lt {:type :free-city-found
                                                  :priority :normal
                                                  :data {:coords [30 40]}
                                                  :from :army-2})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= [[30 40]] (:free-cities-known result)))))

  (describe "handling :city-conquered"
    (before (reset-all-atoms!))

    (it "adds conquered city to cities list"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :exploring))
            lt-with-event (engine/post-event lt {:type :city-conquered
                                                  :priority :high
                                                  :data {:coords [30 40]}
                                                  :from :squad-1})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= [[10 20] [30 40]] (:cities result))))

    (it "removes conquered city from free-cities-known"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :exploring)
                   (assoc :free-cities-known [[30 40] [50 60]]))
            lt-with-event (engine/post-event lt {:type :city-conquered
                                                  :priority :high
                                                  :data {:coords [30 40]}
                                                  :from :squad-1})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= [[50 60]] (:free-cities-known result)))))

  (describe "city naming"
    (it "names cities with lieutenant name and sequence number"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (should= "Alpha-1" (lieutenant/city-name lt 0))
        (should= "Alpha-2" (lieutenant/city-name lt 1))
        (should= "Alpha-3" (lieutenant/city-name lt 2))))

    (it "uses lieutenant's name prefix"
      (let [lt (lieutenant/create-lieutenant "Bravo" [10 20])]
        (should= "Bravo-1" (lieutenant/city-name lt 0)))))

  (describe "process-lieutenant"
    (before (reset-all-atoms!))

    (it "processes events in priority order"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (engine/post-event {:type :free-city-found :priority :low
                                       :data {:coords [50 60]} :from :army-2})
                   (engine/post-event {:type :unit-needs-orders :priority :high
                                       :data {:unit-id :army-1 :coords [11 20]} :from :army-1}))
            result (lieutenant/process-lieutenant lt)]
        ;; High priority unit-needs-orders should be processed first
        (should= 1 (count (:direct-reports result)))
        ;; Low priority event should still be in queue
        (should= 1 (count (:event-queue result)))))))
