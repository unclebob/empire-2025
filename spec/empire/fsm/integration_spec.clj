(ns empire.fsm.integration-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.integration :as integration]
            [empire.fsm.general :as general]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "FSM Integration"

  (describe "commanding-general atom"
    (before (reset-all-atoms!))

    (it "exists and starts as nil"
      (should-be-nil @atoms/commanding-general))

    (it "can hold a General entity"
      (reset! atoms/commanding-general (general/create-general))
      (should= :awaiting-city (:fsm-state @atoms/commanding-general))))

  (describe "initialize-general"
    (before (reset-all-atoms!))

    (it "creates a general when computer has a city"
      (reset! atoms/game-map (build-test-map ["~X~"
                                               "~~~"
                                               "~~~"]))
      (integration/initialize-general)
      (should-not-be-nil @atoms/commanding-general)
      (should= :awaiting-city (:fsm-state @atoms/commanding-general)))

    (it "does not create general if no computer city"
      (reset! atoms/game-map (build-test-map ["~O~"
                                               "~~~"
                                               "~~~"]))
      (integration/initialize-general)
      (should-be-nil @atoms/commanding-general))

    (it "posts city-needs-orders event for initial computer city"
      (reset! atoms/game-map (build-test-map ["~X~"
                                               "~~~"
                                               "~~~"]))
      (integration/initialize-general)
      (let [events (:event-queue @atoms/commanding-general)]
        (should= 1 (count events))
        (should= :city-needs-orders (:type (first events)))
        (should= [0 1] (get-in (first events) [:data :coords])))))

  (describe "process-general-turn"
    (before (reset-all-atoms!))

    (it "processes general FSM step"
      (reset! atoms/game-map (build-test-map ["~X~"
                                               "~~~"
                                               "~~~"]))
      (integration/initialize-general)
      ;; General starts in :awaiting-city with city-needs-orders event
      (should= :awaiting-city (:fsm-state @atoms/commanding-general))
      ;; Process should transition to :operational and create lieutenant
      (integration/process-general-turn)
      (should= :operational (:fsm-state @atoms/commanding-general))
      (should= 1 (count (:lieutenants @atoms/commanding-general))))

    (it "does nothing if no general exists"
      (should-be-nil @atoms/commanding-general)
      (integration/process-general-turn) ; should not throw
      (should-be-nil @atoms/commanding-general)))

  (describe "notify-city-captured"
    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["~X~"
                                               "~~~"
                                               "~+~"]))
      (integration/initialize-general))

    (it "posts city-needs-orders event when computer captures a city"
      ;; Process first event to create first lieutenant
      (integration/process-general-turn)
      (should= 1 (count (:lieutenants @atoms/commanding-general)))
      ;; Simulate capturing new city at [2 1]
      (integration/notify-city-captured [2 1])
      (let [events (:event-queue @atoms/commanding-general)]
        (should= 1 (count events))
        (should= :city-needs-orders (:type (first events)))
        (should= [2 1] (get-in (first events) [:data :coords]))))

    (it "does nothing if no general exists"
      (reset! atoms/commanding-general nil)
      (integration/notify-city-captured [2 1]) ; should not throw
      (should-be-nil @atoms/commanding-general)))

  (describe "find-computer-cities"
    (before (reset-all-atoms!))

    (it "returns all computer city coordinates"
      (reset! atoms/game-map (build-test-map ["~X~X"
                                               "~O~~"
                                               "~X~~"]))
      (let [cities (integration/find-computer-cities)]
        (should= 3 (count cities))
        (should-contain [0 1] cities)
        (should-contain [0 3] cities)
        (should-contain [2 1] cities)))

    (it "returns empty list when no computer cities"
      (reset! atoms/game-map (build-test-map ["~O~"
                                               "~+~"
                                               "~~~"]))
      (should= [] (integration/find-computer-cities))))

  (describe "process-general-turn processes lieutenants"
    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["X##"
                                               "###"
                                               "~~~"]))
      (integration/initialize-general)
      ;; Create a lieutenant by processing the general
      (integration/process-general-turn))

    (it "processes lieutenant events when processing general turn"
      ;; Lieutenant starts with empty known-coastal-cells
      (let [lt (first (:lieutenants @atoms/commanding-general))]
        (should= #{} (:known-coastal-cells lt)))
      ;; Post a cells-discovered event to the lieutenant
      (swap! atoms/commanding-general
             update :lieutenants
             (fn [lts]
               (mapv #(engine/post-event % {:type :cells-discovered
                                            :priority :low
                                            :data {:cells [{:pos [1 1] :terrain :coastal}]}})
                     lts)))
      ;; Process general turn - should also process lieutenant events
      (integration/process-general-turn)
      ;; Lieutenant should now know about the coastal cell
      (let [lt (first (:lieutenants @atoms/commanding-general))]
        (should-contain [1 1] (:known-coastal-cells lt))))))
