(ns empire.fsm.transport-fsm-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.transport-fsm :as transport]
            [empire.fsm.context :as context]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Transport FSM"

  (describe "create-transport"

    (it "starts in :moving-to-landing"
      (let [data (transport/create-transport :transport-1 [3 3] {:transport-landing [1 5] :beach [[1 4] [2 4] [2 5]]} :lt-1)]
        (should= :moving-to-landing (:fsm-state data))))

    (it "sets transport-id in fsm-data"
      (let [data (transport/create-transport :transport-1 [3 3] {:transport-landing [1 5] :beach [[1 4] [2 4] [2 5]]} :lt-1)]
        (should= :transport-1 (get-in data [:fsm-data :transport-id]))))

    (it "sets position in fsm-data"
      (let [data (transport/create-transport :transport-1 [3 3] {:transport-landing [1 5] :beach [[1 4] [2 4] [2 5]]} :lt-1)]
        (should= [3 3] (get-in data [:fsm-data :position]))))

    (it "sets lieutenant-id in fsm-data"
      (let [data (transport/create-transport :transport-1 [3 3] {:transport-landing [1 5] :beach [[1 4] [2 4] [2 5]]} :lt-1)]
        (should= :lt-1 (get-in data [:fsm-data :lieutenant-id]))))

    (it "sets home-landing in fsm-data"
      (let [home-landing {:transport-landing [1 5] :beach [[1 4] [2 4] [2 5]]}
            data (transport/create-transport :transport-1 [3 3] home-landing :lt-1)]
        (should= home-landing (get-in data [:fsm-data :home-landing]))))

    (it "starts with empty cargo"
      (let [data (transport/create-transport :transport-1 [3 3] {:transport-landing [1 5] :beach [[1 4]]} :lt-1)]
        (should= [] (get-in data [:fsm-data :cargo]))))

    (it "starts with exploring target type"
      (let [data (transport/create-transport :transport-1 [3 3] {:transport-landing [1 5] :beach [[1 4]]} :lt-1)]
        (should= :exploring (get-in data [:fsm-data :target :type])))))

  (describe "at-home-landing guard"
    (it "returns true when position equals home transport-landing"
      (let [data (transport/create-transport :transport-1 [1 5] {:transport-landing [1 5] :beach [[1 4]]} :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving-to-landing
                    :fsm-data (assoc (:fsm-data data) :position [1 5])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (transport/at-home-landing? ctx))))

    (it "returns false when position not at home transport-landing"
      (let [data (transport/create-transport :transport-1 [3 3] {:transport-landing [1 5] :beach [[1 4]]} :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :moving-to-landing
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (transport/at-home-landing? ctx)))))

  (describe "fully-loaded guard"
    (it "returns true when cargo has 6 armies"
      (let [data (transport/create-transport :transport-1 [1 5] {:transport-landing [1 5] :beach [[1 4]]} :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :loading
                    :fsm-data (assoc (:fsm-data data) :cargo [:a1 :a2 :a3 :a4 :a5 :a6])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (transport/fully-loaded? ctx))))

    (it "returns false when cargo has fewer than 6 armies"
      (let [data (transport/create-transport :transport-1 [1 5] {:transport-landing [1 5] :beach [[1 4]]} :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :loading
                    :fsm-data (assoc (:fsm-data data) :cargo [:a1 :a2])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (transport/fully-loaded? ctx)))))

  (describe "fully-unloaded guard"
    (it "returns true when cargo is empty"
      (let [data (transport/create-transport :transport-1 [1 5] {:transport-landing [1 5] :beach [[1 4]]} :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :unloading
                    :fsm-data (assoc (:fsm-data data) :cargo [])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (transport/fully-unloaded? ctx))))

    (it "returns false when cargo has armies"
      (let [data (transport/create-transport :transport-1 [1 5] {:transport-landing [1 5] :beach [[1 4]]} :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :unloading
                    :fsm-data (assoc (:fsm-data data) :cargo [:a1])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (transport/fully-unloaded? ctx)))))

  (describe "land-found guard"
    (before
      (reset-all-atoms!)
      ;; Sea with land nearby - land at [1 2]
      (let [test-map (build-test-map ["~~~~~"
                                       "~~#~~"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "returns true when adjacent to land"
      ;; Transport at [1 1] (sea) is adjacent to [1 2] (land)
      (let [data (transport/create-transport :transport-1 [1 1] {:transport-landing [0 0] :beach [[0 1]]} :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (transport/land-found? ctx))))

    (it "returns false when not adjacent to land"
      ;; Transport at [0 0] (sea) is not adjacent to any land
      (let [data (transport/create-transport :transport-1 [0 0] {:transport-landing [0 0] :beach [[0 1]]} :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data) :position [0 0])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (transport/land-found? ctx)))))

  (describe "landing-found guard"
    (before
      (reset-all-atoms!)
      ;; Sea cell at [1 3] with 3 adjacent land cells
      (let [test-map (build-test-map ["~~~~~"
                                       "~###~"
                                       "~###~"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "returns true when at sea cell with 3+ adjacent land cells"
      (let [data (transport/create-transport :transport-1 [0 2] {:transport-landing [0 0] :beach [[0 1]]} :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :scouting
                    :fsm-data (assoc (:fsm-data data) :position [0 2])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (transport/landing-found? ctx))))

    (it "returns false when fewer than 3 adjacent land cells"
      (reset! atoms/game-map (build-test-map ["~~~~~"
                                               "~~#~~"
                                               "~~~~~"]))
      (let [data (transport/create-transport :transport-1 [1 1] {:transport-landing [0 0] :beach [[0 1]]} :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :scouting
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (transport/landing-found? ctx)))))

  (describe "state transitions"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["~~~~~"
                                       "~###~"
                                       "~###~"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (describe ":moving-to-landing -> :loading"
      (it "transitions when at home landing"
        (let [data (transport/create-transport :transport-1 [0 2] {:transport-landing [0 2] :beach [[1 2]]} :lt-1)
              entity {:fsm (:fsm data)
                      :fsm-state :moving-to-landing
                      :fsm-data (assoc (:fsm-data data) :position [0 2])
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :loading (:fsm-state result)))))

    (describe ":loading -> :exploring"
      (it "transitions to :exploring when fully loaded with exploring target"
        (let [data (transport/create-transport :transport-1 [0 2] {:transport-landing [0 2] :beach [[1 2]]} :lt-1)
              entity {:fsm (:fsm data)
                      :fsm-state :loading
                      :fsm-data (-> (:fsm-data data)
                                    (assoc :position [0 2])
                                    (assoc :cargo [:a1 :a2 :a3 :a4 :a5 :a6])
                                    (assoc :target {:type :exploring :coords nil}))
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :exploring (:fsm-state result)))))

    (describe ":loading -> :sailing"
      (it "transitions to :sailing when fully loaded with directed target"
        (let [data (transport/create-transport :transport-1 [0 2] {:transport-landing [0 2] :beach [[1 2]]} :lt-1)
              entity {:fsm (:fsm data)
                      :fsm-state :loading
                      :fsm-data (-> (:fsm-data data)
                                    (assoc :position [0 2])
                                    (assoc :cargo [:a1 :a2 :a3 :a4 :a5 :a6])
                                    (assoc :target {:type :directed :coords [10 10]}))
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :sailing (:fsm-state result)))))

    (describe ":exploring -> :scouting"
      (before
        (reset-all-atoms!)
        (let [test-map (build-test-map ["~~~~~"
                                         "~~#~~"
                                         "~~~~~"])]
          (reset! atoms/game-map test-map)
          (reset! atoms/computer-map test-map)))

      (it "transitions when land found"
        (let [data (transport/create-transport :transport-1 [1 1] {:transport-landing [0 0] :beach [[0 1]]} :lt-1)
              entity {:fsm (:fsm data)
                      :fsm-state :exploring
                      :fsm-data (assoc (:fsm-data data)
                                       :position [1 1]
                                       :cargo [:a1 :a2 :a3 :a4 :a5 :a6])
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :scouting (:fsm-state result)))))

    (describe ":scouting -> :unloading"
      (before
        (reset-all-atoms!)
        (let [test-map (build-test-map ["~~~~~"
                                         "~###~"
                                         "~###~"
                                         "~~~~~"])]
          (reset! atoms/game-map test-map)
          (reset! atoms/computer-map test-map)))

      (it "transitions when landing found"
        (let [data (transport/create-transport :transport-1 [0 2] {:transport-landing [0 0] :beach [[0 1]]} :lt-1)
              entity {:fsm (:fsm data)
                      :fsm-state :scouting
                      :fsm-data (assoc (:fsm-data data)
                                       :position [0 2]
                                       :cargo [:a1 :a2 :a3 :a4 :a5 :a6])
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :unloading (:fsm-state result)))))

    (describe ":unloading -> :returning"
      (it "transitions when fully unloaded"
        (let [data (transport/create-transport :transport-1 [0 2] {:transport-landing [0 0] :beach [[0 1]]} :lt-1)
              entity {:fsm (:fsm data)
                      :fsm-state :unloading
                      :fsm-data (assoc (:fsm-data data)
                                       :position [0 2]
                                       :cargo [])
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :returning (:fsm-state result)))))

    (describe ":returning -> :loading"
      (it "transitions when back at home landing"
        (let [data (transport/create-transport :transport-1 [0 2] {:transport-landing [0 2] :beach [[1 2]]} :lt-1)
              entity {:fsm (:fsm data)
                      :fsm-state :returning
                      :fsm-data (assoc (:fsm-data data)
                                       :position [0 2]
                                       :cargo [])
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :loading (:fsm-state result))))))

  (describe "cargo management"
    (it "load-army adds army to cargo"
      (let [data (transport/create-transport :transport-1 [0 2] {:transport-landing [0 2] :beach [[1 2]]} :lt-1)
            result (transport/load-army data :army-1)]
        (should= [:army-1] (get-in result [:fsm-data :cargo]))))

    (it "unload-army removes army from cargo"
      (let [data (transport/create-transport :transport-1 [0 2] {:transport-landing [0 2] :beach [[1 2]]} :lt-1)
            loaded (-> data
                       (transport/load-army :army-1)
                       (transport/load-army :army-2))
            result (transport/unload-army loaded)]
        (should= [:army-2] (get-in result [:fsm-data :cargo])))))

  (describe "depart-action"
    (it "emits :transport-departed event"
      (let [data (transport/create-transport :transport-1 [0 2] {:transport-landing [0 2] :beach [[1 2]]} :lt-1)
            entity {:fsm (:fsm data)
                    :fsm-state :loading
                    :fsm-data (-> (:fsm-data data)
                                  (assoc :cargo [:a1 :a2 :a3 :a4 :a5 :a6]))
                    :event-queue []}
            ctx (context/build-context entity)
            result (transport/depart-action ctx)
            departed-event (first (filter #(= :transport-departed (:type %)) (:events result)))]
        (should-not-be-nil departed-event)
        (should= :transport-1 (get-in departed-event [:data :transport-id]))
        (should= 6 (get-in departed-event [:data :cargo-count]))))))

(run-specs)
