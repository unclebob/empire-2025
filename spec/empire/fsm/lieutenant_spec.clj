(ns empire.fsm.lieutenant-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.lieutenant :as lieutenant]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Lieutenant FSM"

  (describe "create-lieutenant"
    (it "creates lieutenant in :start-exploring-coastline state"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (should= :start-exploring-coastline (:fsm-state lt))))

    (it "starts with zero coastline-explorer-count"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (should= 0 (:coastline-explorer-count lt))))

    (it "starts with zero interior-explorer-count"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (should= 0 (:interior-explorer-count lt))))

    (it "starts with zero waiting-army-count"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (should= 0 (:waiting-army-count lt))))

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

    (it "starts with empty known-coastal-cells set"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (should= #{} (:known-coastal-cells lt))))

    (it "starts with empty known-landlocked-cells set"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (should= #{} (:known-landlocked-cells lt))))

    (it "starts with empty frontier-coastal-cells set"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (should= #{} (:frontier-coastal-cells lt))))

    (it "has FSM defined"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (should-not-be-nil (:fsm lt)))))

  (describe "state transitions"
    (before (reset-all-atoms!))

    (it "stays in :start-exploring-coastline when less than 2 coastline explorers"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :coastline-explorer-count 1))
            result (lieutenant/process-lieutenant lt)]
        (should= :start-exploring-coastline (:fsm-state result))))

    (it "transitions to :start-exploring-interior when 2 coastline explorers"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :coastline-explorer-count 2))
            result (lieutenant/process-lieutenant lt)]
        (should= :start-exploring-interior (:fsm-state result))))

    (it "stays in :start-exploring-interior when less than 2 interior explorers"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :start-exploring-interior)
                   (assoc :coastline-explorer-count 2 :interior-explorer-count 1))
            result (lieutenant/process-lieutenant lt)]
        (should= :start-exploring-interior (:fsm-state result))))

    (it "transitions to :recruiting-for-transport when 2 interior explorers"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :start-exploring-interior)
                   (assoc :coastline-explorer-count 2 :interior-explorer-count 2))
            result (lieutenant/process-lieutenant lt)]
        (should= :recruiting-for-transport (:fsm-state result))))

    (it "stays in :recruiting-for-transport when less than 6 waiting armies"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :recruiting-for-transport)
                   (assoc :waiting-army-count 5))
            result (lieutenant/process-lieutenant lt)]
        (should= :recruiting-for-transport (:fsm-state result))))

    (it "transitions to :waiting-for-transport when 6 waiting armies"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :recruiting-for-transport)
                   (assoc :waiting-army-count 6))
            result (lieutenant/process-lieutenant lt)]
        (should= :waiting-for-transport (:fsm-state result))))

    (it "stays in :waiting-for-transport indefinitely"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :waiting-for-transport)
                   (assoc :waiting-army-count 6))
            result (lieutenant/process-lieutenant lt)]
        (should= :waiting-for-transport (:fsm-state result))))

    (it "stays in :start-exploring-coastline when no events"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])
            result (lieutenant/process-lieutenant lt)]
        (should= :start-exploring-coastline (:fsm-state result)))))

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
        (should= [] (:event-queue result))))

    (it "assigns first army :explore-coastline mission"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])
            lt-with-event (engine/post-event lt {:type :unit-needs-orders
                                                  :priority :high
                                                  :data {:unit-id :army-1 :coords [11 20]}})
            result (lieutenant/process-lieutenant lt-with-event)
            unit (first (:direct-reports result))]
        (should= :explore-coastline (:mission-type unit))))

    (it "assigns second army :explore-coastline mission"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :coastline-explorer-count 1))
            lt-with-event (engine/post-event lt {:type :unit-needs-orders
                                                  :priority :high
                                                  :data {:unit-id :army-2 :coords [11 21]}})
            result (lieutenant/process-lieutenant lt-with-event)
            unit (first (:direct-reports result))]
        (should= :explore-coastline (:mission-type unit))))

    (it "assigns third army :explore-interior mission"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :coastline-explorer-count 2))
            lt-with-event (engine/post-event lt {:type :unit-needs-orders
                                                  :priority :high
                                                  :data {:unit-id :army-3 :coords [11 22]}})
            result (lieutenant/process-lieutenant lt-with-event)
            unit (first (:direct-reports result))]
        (should= :explore-interior (:mission-type unit))))

    (it "assigns fourth army :explore-interior mission"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :coastline-explorer-count 2 :interior-explorer-count 1))
            lt-with-event (engine/post-event lt {:type :unit-needs-orders
                                                  :priority :high
                                                  :data {:unit-id :army-4 :coords [11 23]}})
            result (lieutenant/process-lieutenant lt-with-event)
            unit (first (:direct-reports result))]
        (should= :explore-interior (:mission-type unit))))

    (it "assigns fifth army :hurry-up-and-wait mission in recruiting-for-transport state"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :coastline-explorer-count 2 :interior-explorer-count 2)
                   (assoc :fsm-state :recruiting-for-transport))
            lt-with-event (engine/post-event lt {:type :unit-needs-orders
                                                  :priority :high
                                                  :data {:unit-id :army-5 :coords [11 24]}})
            result (lieutenant/process-lieutenant lt-with-event)
            unit (first (:direct-reports result))]
        (should= :hurry-up-and-wait (:mission-type unit))))

    (it "increments waiting-army-count when assigning hurry-up-and-wait mission"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :recruiting-for-transport))
            lt-with-event (engine/post-event lt {:type :unit-needs-orders
                                                  :priority :high
                                                  :data {:unit-id :army-5 :coords [11 24]}})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= 1 (:waiting-army-count result))))

    (it "does not add unit to direct-reports in :waiting-for-transport state"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :waiting-for-transport)
                   (assoc :waiting-army-count 6))
            lt-with-event (engine/post-event lt {:type :unit-needs-orders
                                                  :priority :high
                                                  :data {:unit-id :army-11 :coords [11 25]}})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= 0 (count (:direct-reports result)))))

    (it "increments coastline-explorer-count when assigning coastline mission"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])
            lt-with-event (engine/post-event lt {:type :unit-needs-orders
                                                  :priority :high
                                                  :data {:unit-id :army-1 :coords [11 20]}})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= 1 (:coastline-explorer-count result))))

    (it "increments interior-explorer-count when assigning interior mission"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :coastline-explorer-count 2))
            lt-with-event (engine/post-event lt {:type :unit-needs-orders
                                                  :priority :high
                                                  :data {:unit-id :army-3 :coords [11 22]}})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= 1 (:interior-explorer-count result)))))

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

  (describe "handling :cells-discovered"
    (before (reset-all-atoms!))

    (it "adds coastal cells to known-coastal-cells set"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :exploring))
            lt-with-event (engine/post-event lt {:type :cells-discovered
                                                  :priority :normal
                                                  :data {:cells [{:pos [5 10] :terrain :coastal}
                                                                 {:pos [5 11] :terrain :coastal}]}})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= #{[5 10] [5 11]} (:known-coastal-cells result))))

    (it "adds landlocked cells to known-landlocked-cells set"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :exploring))
            lt-with-event (engine/post-event lt {:type :cells-discovered
                                                  :priority :normal
                                                  :data {:cells [{:pos [5 10] :terrain :landlocked}
                                                                 {:pos [5 11] :terrain :landlocked}]}})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= #{[5 10] [5 11]} (:known-landlocked-cells result))))

    (it "handles mixed coastal and landlocked cells"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :exploring))
            lt-with-event (engine/post-event lt {:type :cells-discovered
                                                  :priority :normal
                                                  :data {:cells [{:pos [5 10] :terrain :coastal}
                                                                 {:pos [5 11] :terrain :landlocked}]}})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= #{[5 10]} (:known-coastal-cells result))
        (should= #{[5 11]} (:known-landlocked-cells result))))

    (it "accumulates cells across multiple events"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :exploring)
                   (assoc :known-coastal-cells #{[1 1]}))
            lt-with-event (engine/post-event lt {:type :cells-discovered
                                                  :priority :normal
                                                  :data {:cells [{:pos [5 10] :terrain :coastal}]}})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= #{[1 1] [5 10]} (:known-coastal-cells result))))

    (it "does not add duplicate cells"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :fsm-state :exploring)
                   (assoc :known-coastal-cells #{[5 10]}))
            lt-with-event (engine/post-event lt {:type :cells-discovered
                                                  :priority :normal
                                                  :data {:cells [{:pos [5 10] :terrain :coastal}]}})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= #{[5 10]} (:known-coastal-cells result)))))

  (describe "city naming"
    (it "names cities with lieutenant name and sequence number"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (should= "Alpha-1" (lieutenant/city-name lt 0))
        (should= "Alpha-2" (lieutenant/city-name lt 1))
        (should= "Alpha-3" (lieutenant/city-name lt 2))))

    (it "uses lieutenant's name prefix"
      (let [lt (lieutenant/create-lieutenant "Bravo" [10 20])]
        (should= "Bravo-1" (lieutenant/city-name lt 0)))))

  (describe "get-exploration-target"
    (before
      (reset-all-atoms!)
      ;; Map with coastal cells
      ;; ~~~~~~
      ;; ~#####
      ;; ~#####
      ;; ~~~~~~
      (reset! atoms/game-map (build-test-map ["~~~~~~"
                                               "~#####"
                                               "~#####"
                                               "~~~~~~"]))
      ;; Computer map with some explored and unexplored areas (space = unexplored)
      (reset! atoms/computer-map (build-test-map ["~~~~~~"
                                                   "~##   "
                                                   "~##   "
                                                   "~~~~~~"])))

    (it "returns nil when no known coastal cells"
      (let [lt (lieutenant/create-lieutenant "Alpha" [1 1])]
        (should-be-nil (lieutenant/get-exploration-target lt [1 1]))))

    (it "returns nil when no frontier cells (all coastal cells fully explored)"
      ;; All neighbors of coastal cells are explored
      (reset! atoms/computer-map (build-test-map ["~~~~~~"
                                                   "~#####"
                                                   "~#####"
                                                   "~~~~~~"]))
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [1 1])
                   (assoc :known-coastal-cells #{[1 1] [2 1]}))]
        (should-be-nil (lieutenant/get-exploration-target lt [1 1]))))

    (it "returns frontier coastal cell adjacent to unexplored"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [1 1])
                   ;; [1 1] and [1 2] are explored coastal cells
                   ;; [1 2] is adjacent to unexplored [1 3]
                   (assoc :known-coastal-cells #{[1 1] [1 2]}))]
        (let [target (lieutenant/get-exploration-target lt [1 1])]
          (should-not-be-nil target)
          (should= [1 2] target))))

    (it "returns nearest frontier cell when multiple exist"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [1 1])
                   ;; [1 2] and [2 2] are both adjacent to unexplored [1 3] and [2 3]
                   (assoc :known-coastal-cells #{[1 1] [1 2] [2 1] [2 2]}))]
        ;; From [1 1], both [1 2] and [2 2] are frontier cells
        ;; [1 2] is distance 1, [2 2] is distance 2
        (let [target (lieutenant/get-exploration-target lt [1 1])]
          (should= [1 2] target))))

    (it "returns nil if current position is already a frontier cell"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [1 1])
                   ;; [1 2] is adjacent to unexplored [1 3]
                   (assoc :known-coastal-cells #{[1 2]}))]
        (let [target (lieutenant/get-exploration-target lt [1 2])]
          ;; Should return nil since we're already at a frontier cell
          (should-be-nil target)))))

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

(describe "initialize-with-visible-cells"
  (before
    (reset-all-atoms!)
    ;; Map with city on coast
    ;; ~~~
    ;; ~X#
    ;; ~##
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~X#"
                                             "~##"])))

  (it "adds coastal cells around city to known-coastal-cells"
    ;; Computer has explored the area around the city
    (let [test-map (build-test-map ["~~~"
                                     "~X#"
                                     "~##"])]
      (reset! atoms/computer-map test-map)
      (let [lt (lieutenant/create-lieutenant "Alpha" [1 1])
            initialized (lieutenant/initialize-with-visible-cells lt)]
        ;; City at [1,1] is adjacent to sea, so it's coastal
        ;; Land at [1,2] is adjacent to sea at [0,2], so coastal
        ;; Land at [2,1] is adjacent to sea at [2,0], so coastal
        ;; Land at [2,2] is NOT adjacent to sea, so landlocked
        (should (contains? (:known-coastal-cells initialized) [1 1]))
        (should (contains? (:known-coastal-cells initialized) [1 2]))
        (should (contains? (:known-coastal-cells initialized) [2 1])))))

  (it "adds landlocked cells to known-landlocked-cells"
    (let [test-map (build-test-map ["~~~"
                                     "~X#"
                                     "~##"])]
      (reset! atoms/computer-map test-map)
      (let [lt (lieutenant/create-lieutenant "Alpha" [1 1])
            initialized (lieutenant/initialize-with-visible-cells lt)]
        ;; Land at [2,2] is NOT adjacent to sea, so landlocked
        (should (contains? (:known-landlocked-cells initialized) [2 2])))))

  (it "only includes explored cells from computer-map"
    ;; Only partial exploration - column 2 is unexplored
    (reset! atoms/computer-map (build-test-map ["~~ "
                                                 "~X "
                                                 "~# "]))
    (let [lt (lieutenant/create-lieutenant "Alpha" [1 1])
          initialized (lieutenant/initialize-with-visible-cells lt)]
      ;; [1,2] and [2,2] are unexplored, should not be included
      (should-not (contains? (:known-coastal-cells initialized) [1 2]))
      (should-not (contains? (:known-landlocked-cells initialized) [2 2]))
      ;; [1,1] (city) and [2,1] (land) are explored and coastal
      (should (contains? (:known-coastal-cells initialized) [1 1]))
      (should (contains? (:known-coastal-cells initialized) [2 1])))))

(describe "should-produce?"
  (it "returns true in :start-exploring-coastline state"
    (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
      (should (lieutenant/should-produce? lt))))

  (it "returns true in :start-exploring-interior state"
    (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                 (assoc :fsm-state :start-exploring-interior))]
      (should (lieutenant/should-produce? lt))))

  (it "returns true in :recruiting-for-transport state"
    (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                 (assoc :fsm-state :recruiting-for-transport))]
      (should (lieutenant/should-produce? lt))))

  (it "returns false in :waiting-for-transport state"
    (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                 (assoc :fsm-state :waiting-for-transport))]
      (should-not (lieutenant/should-produce? lt)))))

(describe "mission status tracking"
  (before (reset-all-atoms!))

  (it "marks new missions as :active"
    (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])
          lt-with-event (engine/post-event lt {:type :unit-needs-orders
                                                :priority :high
                                                :data {:unit-id :army-1 :coords [11 20]}})
          result (lieutenant/process-lieutenant lt-with-event)
          unit (first (:direct-reports result))]
      (should= :active (:status unit))))

  (it "updates mission to :ended when receiving :mission-ended event"
    (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                 (assoc :direct-reports [{:unit-id :army-1
                                          :coords [11 20]
                                          :mission-type :explore-coastline
                                          :fsm-state :following-coast
                                          :status :active}])
                 (assoc :coastline-explorer-count 1))
          lt-with-event (engine/post-event lt {:type :mission-ended
                                                :priority :high
                                                :data {:unit-id :army-1 :reason :stuck}})
          result (lieutenant/process-lieutenant lt-with-event)
          unit (first (:direct-reports result))]
      (should= :ended (:status unit))))

  (it "sets end-reason when mission ends"
    (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                 (assoc :direct-reports [{:unit-id :army-1
                                          :coords [11 20]
                                          :mission-type :explore-coastline
                                          :fsm-state :following-coast
                                          :status :active}])
                 (assoc :coastline-explorer-count 1))
          lt-with-event (engine/post-event lt {:type :mission-ended
                                                :priority :high
                                                :data {:unit-id :army-1 :reason :stuck}})
          result (lieutenant/process-lieutenant lt-with-event)
          unit (first (:direct-reports result))]
      (should= :stuck (:end-reason unit))))

  (it "decrements coastline-explorer-count when coastline mission ends"
    (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                 (assoc :direct-reports [{:unit-id :army-1
                                          :coords [11 20]
                                          :mission-type :explore-coastline
                                          :fsm-state :following-coast
                                          :status :active}])
                 (assoc :coastline-explorer-count 2))
          lt-with-event (engine/post-event lt {:type :mission-ended
                                                :priority :high
                                                :data {:unit-id :army-1 :reason :stuck}})
          result (lieutenant/process-lieutenant lt-with-event)]
      (should= 1 (:coastline-explorer-count result))))

  (it "decrements interior-explorer-count when interior mission ends"
    (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                 (assoc :direct-reports [{:unit-id :army-2
                                          :coords [11 20]
                                          :mission-type :explore-interior
                                          :fsm-state :exploring
                                          :status :active}])
                 (assoc :interior-explorer-count 2))
          lt-with-event (engine/post-event lt {:type :mission-ended
                                                :priority :high
                                                :data {:unit-id :army-2 :reason :stuck}})
          result (lieutenant/process-lieutenant lt-with-event)]
      (should= 1 (:interior-explorer-count result))))

  (it "decrements waiting-army-count when waiting mission ends"
    (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                 (assoc :direct-reports [{:unit-id :army-5
                                          :coords [11 20]
                                          :mission-type :hurry-up-and-wait
                                          :fsm-state :waiting
                                          :status :active}])
                 (assoc :waiting-army-count 3))
          lt-with-event (engine/post-event lt {:type :mission-ended
                                                :priority :high
                                                :data {:unit-id :army-5 :reason :stuck}})
          result (lieutenant/process-lieutenant lt-with-event)]
      (should= 2 (:waiting-army-count result))))

  (it "only updates the matching unit when mission ends"
    (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                 (assoc :direct-reports [{:unit-id :army-1
                                          :coords [11 20]
                                          :mission-type :explore-coastline
                                          :fsm-state :following-coast
                                          :status :active}
                                         {:unit-id :army-2
                                          :coords [12 20]
                                          :mission-type :explore-coastline
                                          :fsm-state :following-coast
                                          :status :active}])
                 (assoc :coastline-explorer-count 2))
          lt-with-event (engine/post-event lt {:type :mission-ended
                                                :priority :high
                                                :data {:unit-id :army-1 :reason :stuck}})
          result (lieutenant/process-lieutenant lt-with-event)
          unit1 (first (filter #(= :army-1 (:unit-id %)) (:direct-reports result)))
          unit2 (first (filter #(= :army-2 (:unit-id %)) (:direct-reports result)))]
      (should= :ended (:status unit1))
      (should= :active (:status unit2)))))

(describe "Squad management"
  (before (reset-all-atoms!))

  (describe "create-squad-for-free-city"
    (it "creates a squad targeting the free city"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (reset! atoms/round-number 100)
        (let [result (lieutenant/create-squad-for-free-city lt [15 25])]
          (should= 1 (count (:squads result)))
          (should= [15 25] (get-in (first (:squads result)) [:fsm-data :target-city])))))

    (it "generates unique squad-id"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])]
        (reset! atoms/round-number 100)
        (let [result (lieutenant/create-squad-for-free-city lt [15 25])
              squad (first (:squads result))]
          (should-not-be-nil (get-in squad [:fsm-data :squad-id]))))))

  (describe "handling :squad-mission-complete"
    (it "removes squad from squads list on success"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :squads [{:fsm-data {:squad-id :squad-1 :target-city [15 25]}}]))
            lt-with-event (engine/post-event lt {:type :squad-mission-complete
                                                  :priority :high
                                                  :data {:squad-id :squad-1
                                                         :result :success
                                                         :surviving-armies [:army-1 :army-2]}})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= 0 (count (:squads result)))))

    (it "adds surviving armies to waiting-armies on success"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :squads [{:fsm-data {:squad-id :squad-1 :target-city [15 25]}}]))
            lt-with-event (engine/post-event lt {:type :squad-mission-complete
                                                  :priority :high
                                                  :data {:squad-id :squad-1
                                                         :result :success
                                                         :surviving-armies [:army-1 :army-2]}})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= [:army-1 :army-2] (:waiting-armies result))))))

(describe "Transport-landing management"
  (before (reset-all-atoms!))

  (describe "handling :transport-landing-found"
    (it "adds transport-landing to list"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])
            lt-with-event (engine/post-event lt {:type :transport-landing-found
                                                  :priority :normal
                                                  :data {:transport-landing [5 10]
                                                         :beach [[5 9] [6 9] [6 10]]
                                                         :capacity 3}})
            result (lieutenant/process-lieutenant lt-with-event)]
        (should= 1 (count (:transport-landings result)))))

    (it "stores transport-landing data correctly"
      (let [lt (lieutenant/create-lieutenant "Alpha" [10 20])
            landing-data {:transport-landing [5 10]
                          :beach [[5 9] [6 9] [6 10]]
                          :capacity 3}
            lt-with-event (engine/post-event lt {:type :transport-landing-found
                                                  :priority :normal
                                                  :data landing-data})
            result (lieutenant/process-lieutenant lt-with-event)
            stored-landing (first (:transport-landings result))]
        (should= [5 10] (:transport-landing stored-landing))
        (should= [[5 9] [6 9] [6 10]] (:beach stored-landing))
        (should= 3 (:capacity stored-landing)))))

  (describe "handling :transport-returned"
    (it "increments available-transport count"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [10 20])
                   (assoc :transports [{:transport-id :t1 :status :sailing}]))
            lt-with-event (engine/post-event lt {:type :transport-returned
                                                  :priority :normal
                                                  :data {:transport-id :t1}})
            result (lieutenant/process-lieutenant lt-with-event)
            transport (first (:transports result))]
        (should= :loading (:status transport))))))
