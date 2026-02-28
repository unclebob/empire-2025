(ns empire.movement.movement-state-spec
  (:require [empire.atoms :as atoms]
            [empire.movement.movement-state :as state]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "movement-state"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["###" "###" "###"]))
    (reset! atoms/production {}))

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
    (swap! atoms/game-map assoc-in [1 1] {:type :city :city-status :player})
    (reset! atoms/production {[1 1] {:item :army :remaining-rounds 3}})
    (should (state/wake-at [1 1]))
    (should-be-nil (get @atoms/production [1 1])))

  (it "does not wake enemy transports or empty player transports"
    (swap! atoms/game-map assoc-in [1 1 :contents]
           {:type :transport :owner :computer :mode :sentry :army-count 2 :awake-armies 0})
    (should-be-nil (state/wake-at [1 1]))
    (swap! atoms/game-map assoc-in [1 1 :contents]
           {:type :transport :owner :player :mode :awake :awake-armies 0})
    (should-be-nil (state/wake-at [1 1])))

  (it "does not wake an already awake player unit"
    (swap! atoms/game-map assoc-in [1 1 :contents] {:type :army :owner :player :mode :awake})
    (should-be-nil (state/wake-at [1 1]))))
