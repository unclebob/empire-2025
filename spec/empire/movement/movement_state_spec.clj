(ns empire.game-mechanics.movement.movement-state-spec
  (:require [empire.test.utils :as test-utils]
            [empire.game-mechanics.movement.movement-state :as state]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "movement-state"
  (before
    (reset-all-atoms!)
    (set-test-world! (build-test-map ["###" "###" "###"]))
    (test-utils/set-test-state! :production {}))

  (it "builds synthetic active units with expected flags"
    (should= true
             (:aboard-transport
               (state/get-active-unit {:contents {:type :transport :owner :player :awake-armies 1 :army-count 2}})))
    (should= true
             (:from-carrier
               (state/get-active-unit {:contents {:type :carrier :owner :player :awake-fighters 1 :fighter-count 2}})))
    (should= true
             (:from-airport
               (state/get-active-unit {:type :city :awake-fighters 1 :fighter-count 1}))))

  (it "wakes player city and removes production"
    (update-test-world! assoc-in [1 1] {:type :city :city-status :player})
    (test-utils/set-test-state! :production {[1 1] {:item :army :remaining-rounds 3}})
    (should (state/wake-at [1 1]))
    (should-be-nil (get (test-utils/read-test-state :production) [1 1])))

  (it "does not wake enemy transports or empty player transports"
    (update-test-world! assoc-in [1 1 :contents]
                        {:type :transport :owner :computer :mode :sentry :army-count 2 :awake-armies 0})
    (should-be-nil (state/wake-at [1 1]))
    (update-test-world! assoc-in [1 1 :contents]
                        {:type :transport :owner :player :mode :awake :awake-armies 0})
    (should-be-nil (state/wake-at [1 1])))

  (it "does not wake an already awake player unit"
    (update-test-world! assoc-in [1 1 :contents] {:type :army :owner :player :mode :awake})
    (should-be-nil (state/wake-at [1 1]))))
