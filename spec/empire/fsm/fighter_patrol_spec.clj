(ns empire.fsm.fighter-patrol-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.fighter-patrol :as patrol]
            [empire.fsm.context :as context]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Fighter Patrol FSM"

  (describe "create-fighter-patrol"

    (it "starts in :launching state"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)]
        (should= :launching (:fsm-state data))))

    (it "sets fighter-id in fsm-data"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)]
        (should= :fighter-1 (get-in data [:fsm-data :fighter-id]))))

    (it "sets position to base-city"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)]
        (should= [5 5] (get-in data [:fsm-data :position]))))

    (it "sets base-city"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)]
        (should= [5 5] (get-in data [:fsm-data :base-city]))))

    (it "sets lieutenant-id"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)]
        (should= :lt-1 (get-in data [:fsm-data :lieutenant-id]))))

    (it "sets fuel-remaining to max-fuel"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)]
        (should= 20 (get-in data [:fsm-data :fuel-remaining]))))

    (it "sets max-fuel"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)]
        (should= 20 (get-in data [:fsm-data :max-fuel]))))

    (it "starts with nil patrol-direction"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)]
        (should-be-nil (get-in data [:fsm-data :patrol-direction]))))

    (it "starts with empty enemies-reported set"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)]
        (should= #{} (get-in data [:fsm-data :enemies-reported])))))

  (describe "clear-of-city? guard"
    (it "returns true when position differs from base-city"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)
            entity {:fsm (:fsm data)
                    :fsm-state :launching
                    :fsm-data (assoc (:fsm-data data) :position [5 6])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (patrol/clear-of-city? ctx))))

    (it "returns false when at base-city"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)
            entity {:fsm (:fsm data)
                    :fsm-state :launching
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (patrol/clear-of-city? ctx)))))

  (describe "fuel-at-half? guard"
    (it "returns true when fuel is at half"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)
            entity {:fsm (:fsm data)
                    :fsm-state :flying-out
                    :fsm-data (assoc (:fsm-data data) :fuel-remaining 10)
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (patrol/fuel-at-half? ctx))))

    (it "returns true when fuel is below half"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)
            entity {:fsm (:fsm data)
                    :fsm-state :flying-out
                    :fsm-data (assoc (:fsm-data data) :fuel-remaining 8)
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (patrol/fuel-at-half? ctx))))

    (it "returns false when fuel is above half"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)
            entity {:fsm (:fsm data)
                    :fsm-state :flying-out
                    :fsm-data (assoc (:fsm-data data) :fuel-remaining 15)
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (patrol/fuel-at-half? ctx)))))

  (describe "at-base? guard"
    (it "returns true when at base-city"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)
            entity {:fsm (:fsm data)
                    :fsm-state :returning
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (patrol/at-base? ctx))))

    (it "returns false when not at base-city"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)
            entity {:fsm (:fsm data)
                    :fsm-state :returning
                    :fsm-data (assoc (:fsm-data data) :position [8 8])
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (patrol/at-base? ctx)))))

  (describe "refueled? guard"
    (it "returns true when fuel equals max-fuel"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)
            entity {:fsm (:fsm data)
                    :fsm-state :landing
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (patrol/refueled? ctx))))

    (it "returns false when fuel is below max"
      (let [data (patrol/create-fighter-patrol :fighter-1 [5 5] :lt-1 20)
            entity {:fsm (:fsm data)
                    :fsm-state :landing
                    :fsm-data (assoc (:fsm-data data) :fuel-remaining 15)
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (patrol/refueled? ctx)))))

  (describe "at-map-edge? guard"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["#####"
                                       "#####"
                                       "#####"
                                       "#####"
                                       "#####"])]
        (reset! atoms/game-map test-map)))

    (it "returns true when at top edge moving up"
      (let [data (patrol/create-fighter-patrol :fighter-1 [2 2] :lt-1 20)
            entity {:fsm (:fsm data)
                    :fsm-state :flying-out
                    :fsm-data (-> (:fsm-data data)
                                  (assoc :position [0 2])
                                  (assoc :patrol-direction [-1 0]))
                    :event-queue []}
            ctx (context/build-context entity)]
        (should (patrol/at-map-edge? ctx))))

    (it "returns false when not at edge in patrol direction"
      (let [data (patrol/create-fighter-patrol :fighter-1 [2 2] :lt-1 20)
            entity {:fsm (:fsm data)
                    :fsm-state :flying-out
                    :fsm-data (-> (:fsm-data data)
                                  (assoc :position [2 2])
                                  (assoc :patrol-direction [1 0]))
                    :event-queue []}
            ctx (context/build-context entity)]
        (should-not (patrol/at-map-edge? ctx)))))

  (describe "state transitions"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["#####"
                                       "#####"
                                       "#####"
                                       "#####"
                                       "#####"])]
        (reset! atoms/game-map test-map)))

    (describe ":launching -> :flying-out"
      (it "transitions when clear of city"
        (let [data (patrol/create-fighter-patrol :fighter-1 [2 2] :lt-1 20)
              entity {:fsm (:fsm data)
                      :fsm-state :launching
                      :fsm-data (-> (:fsm-data data)
                                    (assoc :position [2 3])
                                    (assoc :patrol-direction [0 1]))
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :flying-out (:fsm-state result)))))

    (describe ":flying-out -> :returning"
      (it "transitions when fuel at half"
        (let [data (patrol/create-fighter-patrol :fighter-1 [2 2] :lt-1 20)
              entity {:fsm (:fsm data)
                      :fsm-state :flying-out
                      :fsm-data (-> (:fsm-data data)
                                    (assoc :position [2 4])
                                    (assoc :fuel-remaining 10)
                                    (assoc :patrol-direction [0 1]))
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :returning (:fsm-state result)))))

    (describe ":returning -> :landing"
      (it "transitions when at base"
        (let [data (patrol/create-fighter-patrol :fighter-1 [2 2] :lt-1 20)
              entity {:fsm (:fsm data)
                      :fsm-state :returning
                      :fsm-data (assoc (:fsm-data data) :fuel-remaining 5)
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :landing (:fsm-state result)))))

    (describe ":landing -> :launching"
      (it "transitions when refueled"
        (let [data (patrol/create-fighter-patrol :fighter-1 [2 2] :lt-1 20)
              entity {:fsm (:fsm data)
                      :fsm-state :landing
                      :fsm-data (:fsm-data data)
                      :event-queue []}
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :launching (:fsm-state result))))))

  (describe "actions"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["#####"
                                       "#####"
                                       "#####"
                                       "#####"
                                       "#####"])]
        (reset! atoms/game-map test-map)))

    (describe "pick-direction-action"
      (it "returns a patrol-direction"
        (let [data (patrol/create-fighter-patrol :fighter-1 [2 2] :lt-1 20)
              entity {:fsm (:fsm data)
                      :fsm-state :launching
                      :fsm-data (:fsm-data data)
                      :event-queue []}
              ctx (context/build-context entity)
              result (patrol/pick-direction-action ctx)]
          (should-not-be-nil (:patrol-direction result))
          (should (vector? (:patrol-direction result))))))

    (describe "fly-outbound-action"
      (it "returns move-to in patrol direction"
        (let [data (patrol/create-fighter-patrol :fighter-1 [2 2] :lt-1 20)
              entity {:fsm (:fsm data)
                      :fsm-state :flying-out
                      :fsm-data (-> (:fsm-data data)
                                    (assoc :position [2 3])
                                    (assoc :patrol-direction [0 1]))
                      :event-queue []}
              ctx (context/build-context entity)
              result (patrol/fly-outbound-action ctx)]
          (should= [2 4] (:move-to result))))

      (it "decrements fuel"
        (let [data (patrol/create-fighter-patrol :fighter-1 [2 2] :lt-1 20)
              entity {:fsm (:fsm data)
                      :fsm-state :flying-out
                      :fsm-data (-> (:fsm-data data)
                                    (assoc :position [2 3])
                                    (assoc :patrol-direction [0 1])
                                    (assoc :fuel-remaining 15))
                      :event-queue []}
              ctx (context/build-context entity)
              result (patrol/fly-outbound-action ctx)]
          (should= 14 (:fuel-remaining result)))))

    (describe "fly-toward-base-action"
      (it "returns move-to toward base"
        (let [data (patrol/create-fighter-patrol :fighter-1 [2 2] :lt-1 20)
              entity {:fsm (:fsm data)
                      :fsm-state :returning
                      :fsm-data (-> (:fsm-data data)
                                    (assoc :position [2 4])
                                    (assoc :fuel-remaining 10))
                      :event-queue []}
              ctx (context/build-context entity)
              result (patrol/fly-toward-base-action ctx)]
          (should= [2 3] (:move-to result)))))

    (describe "refuel-action"
      (it "increases fuel by refuel rate"
        (let [data (patrol/create-fighter-patrol :fighter-1 [2 2] :lt-1 20)
              entity {:fsm (:fsm data)
                      :fsm-state :landing
                      :fsm-data (assoc (:fsm-data data) :fuel-remaining 10)
                      :event-queue []}
              ctx (context/build-context entity)
              result (patrol/refuel-action ctx)]
          (should= 12 (:fuel-remaining result))))

      (it "does not exceed max fuel"
        (let [data (patrol/create-fighter-patrol :fighter-1 [2 2] :lt-1 20)
              entity {:fsm (:fsm data)
                      :fsm-state :landing
                      :fsm-data (assoc (:fsm-data data) :fuel-remaining 19)
                      :event-queue []}
              ctx (context/build-context entity)
              result (patrol/refuel-action ctx)]
          (should= 20 (:fuel-remaining result)))))

    (describe "land-action"
      (it "clears enemies-reported"
        (let [data (patrol/create-fighter-patrol :fighter-1 [2 2] :lt-1 20)
              entity {:fsm (:fsm data)
                      :fsm-state :returning
                      :fsm-data (-> (:fsm-data data)
                                    (assoc :enemies-reported #{:enemy-1 :enemy-2}))
                      :event-queue []}
              ctx (context/build-context entity)
              result (patrol/land-action ctx)]
          (should= #{} (:enemies-reported result)))))))

(run-specs)
