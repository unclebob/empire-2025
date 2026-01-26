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
    (reset! atoms/computer-map (build-test-map ["~~~"
                                                 "~X#"
                                                 "~##"]))
    (let [lt (lieutenant/create-lieutenant "Alpha" [1 1])
          initialized (lieutenant/initialize-with-visible-cells lt)]
      ;; City at [1,1] is adjacent to sea, so it's coastal
      ;; Land at [1,2] is adjacent to sea at [0,2], so coastal
      ;; Land at [2,1] is adjacent to sea at [2,0], so coastal
      ;; Land at [2,2] is NOT adjacent to sea, so landlocked
      (should (contains? (:known-coastal-cells initialized) [1 1]))
      (should (contains? (:known-coastal-cells initialized) [1 2]))
      (should (contains? (:known-coastal-cells initialized) [2 1]))))

  (it "adds landlocked cells to known-landlocked-cells"
    (reset! atoms/computer-map (build-test-map ["~~~"
                                                 "~X#"
                                                 "~##"]))
    (let [lt (lieutenant/create-lieutenant "Alpha" [1 1])
          initialized (lieutenant/initialize-with-visible-cells lt)]
      ;; Land at [2,2] is NOT adjacent to sea, so landlocked
      (should (contains? (:known-landlocked-cells initialized) [2 2]))))

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
