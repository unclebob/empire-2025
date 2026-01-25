(ns empire.fsm.base-establishment-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.base-establishment :as base]
            [empire.fsm.general :as general]
            [empire.fsm.lieutenant :as lieutenant]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Base Establishment"

  (describe "beach candidate evaluation"
    (before
      (reset-all-atoms!)
      ;; Map with coastline
      ;; ~~~
      ;; ~##
      ;; ~##
      (reset! atoms/game-map (build-test-map ["~~~"
                                               "~##"
                                               "~##"])))

    (it "identifies valid beach candidate (land adjacent to sea)"
      (should (base/valid-beach-candidate? [1 1])))

    (it "rejects interior land (not adjacent to sea)"
      (should-not (base/valid-beach-candidate? [2 2])))

    (it "rejects sea cells"
      (should-not (base/valid-beach-candidate? [0 0])))

    (it "requires at least 3 adjacent land cells for army unloading"
      ;; Single land cell surrounded by sea is not valid
      ;; ~~~
      ;; ~#~
      ;; ~~~
      (reset! atoms/game-map (build-test-map ["~~~"
                                               "~#~"
                                               "~~~"]))
      (should-not (base/valid-beach-candidate? [1 1]))))

  (describe "transport loading detection"
    (before (reset-all-atoms!))

    (it "detects fully loaded transport (6 armies)"
      (let [transport {:type :transport :owner :computer :army-count 6}]
        (should (base/transport-fully-loaded? transport))))

    (it "detects partially loaded transport as not ready"
      (let [transport {:type :transport :owner :computer :army-count 5}]
        (should-not (base/transport-fully-loaded? transport))))

    (it "detects empty transport as not ready"
      (let [transport {:type :transport :owner :computer :army-count 0}]
        (should-not (base/transport-fully-loaded? transport))))

    (it "detects transport with nil army-count as not ready"
      (let [transport {:type :transport :owner :computer}]
        (should-not (base/transport-fully-loaded? transport)))))

  (describe "Lieutenant handling coastline-mapped event"
    (before (reset-all-atoms!))

    (it "adds beach candidate when coastline-mapped event received"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [0 0])
                   (engine/post-event {:type :coastline-mapped
                                       :priority :normal
                                       :data {:coords [5 5]}
                                       :from :army-1}))
            result (lieutenant/process-lieutenant lt)]
        (should= [[5 5]] (:beach-candidates result))))

    (it "does not add duplicate beach candidates"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [0 0])
                   (engine/post-event {:type :coastline-mapped
                                       :priority :normal
                                       :data {:coords [5 5]}
                                       :from :army-1})
                   (assoc :beach-candidates [[5 5]]))
            result (lieutenant/process-lieutenant lt)]
        (should= [[5 5]] (:beach-candidates result)))))

  (describe "base establishment detection"
    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["~~~"
                                               "~##"
                                               "~##"])))

    (it "detects base establishment conditions met (beach + loaded transport)"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [0 0])
                   (assoc :beach-candidates [[1 1]]))
            transport {:type :transport :owner :computer :army-count 6 :coords [0 1]}]
        (should (base/base-establishment-ready? lt [transport]))))

    (it "detects conditions not met when no beach candidates"
      (let [lt (lieutenant/create-lieutenant "Alpha" [0 0])
            transport {:type :transport :owner :computer :army-count 6 :coords [0 1]}]
        (should-not (base/base-establishment-ready? lt [transport]))))

    (it "detects conditions not met when transport not loaded"
      (let [lt (-> (lieutenant/create-lieutenant "Alpha" [0 0])
                   (assoc :beach-candidates [[1 1]]))
            transport {:type :transport :owner :computer :army-count 3 :coords [0 1]}]
        (should-not (base/base-establishment-ready? lt [transport])))))

  (describe "make-base-established-event"
    (it "creates event with correct structure"
      (let [event (base/make-base-established-event [5 5] :transport-1)]
        (should= :base-established (:type event))
        (should= :high (:priority event))
        (should= [5 5] (get-in event [:data :beach-coords]))
        (should= :transport-1 (get-in event [:data :transport-id])))))

  (describe "General handling base-established event"
    (before (reset-all-atoms!))

    (it "creates new Lieutenant when base-established event received"
      (let [gen (-> (general/create-general)
                    (assoc :fsm-state :operational)
                    (assoc :lieutenants [(lieutenant/create-lieutenant "Alpha" [0 0])])
                    (engine/post-event {:type :base-established
                                        :priority :high
                                        :data {:beach-coords [10 10]
                                               :transport-id :transport-1}}))
            result (general/process-general gen)]
        (should= 2 (count (:lieutenants result)))
        (should= "Bravo" (:name (second (:lieutenants result))))))

    (it "assigns beach as initial city for new Lieutenant"
      (let [gen (-> (general/create-general)
                    (assoc :fsm-state :operational)
                    (assoc :lieutenants [(lieutenant/create-lieutenant "Alpha" [0 0])])
                    (engine/post-event {:type :base-established
                                        :priority :high
                                        :data {:beach-coords [10 10]
                                               :transport-id :transport-1}}))
            result (general/process-general gen)
            new-lt (second (:lieutenants result))]
        (should= [[10 10]] (:cities new-lt))))))
