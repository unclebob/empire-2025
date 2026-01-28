(ns empire.fsm.attack-city-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.attack-city :as attack-city]
            [empire.fsm.context :as context]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Attack-City FSM"

  (describe "create-attack-city-data"

    (it "starts in :approaching state"
      (let [data (attack-city/create-attack-city-data [5 5] [3 3] :squad-1)]
        (should= :approaching (:fsm-state data))))

    (it "sets mission-type to :attack-city"
      (let [data (attack-city/create-attack-city-data [5 5] [3 3] :squad-1)]
        (should= :attack-city (get-in data [:fsm-data :mission-type]))))

    (it "sets position in fsm-data"
      (let [data (attack-city/create-attack-city-data [5 5] [3 3] :squad-1)]
        (should= [5 5] (get-in data [:fsm-data :position]))))

    (it "sets target-city in fsm-data"
      (let [data (attack-city/create-attack-city-data [5 5] [3 3] :squad-1)]
        (should= [3 3] (get-in data [:fsm-data :target-city]))))

    (it "sets squad-id in fsm-data"
      (let [data (attack-city/create-attack-city-data [5 5] [3 3] :squad-1)]
        (should= :squad-1 (get-in data [:fsm-data :squad-id]))))

    (it "accepts optional unit-id"
      (let [data (attack-city/create-attack-city-data [5 5] [3 3] :squad-1 :army-1)]
        (should= :army-1 (get-in data [:fsm-data :unit-id]))))

    (it "initializes recent-moves with starting position"
      (let [data (attack-city/create-attack-city-data [5 5] [3 3] :squad-1)]
        (should= [[5 5]] (get-in data [:fsm-data :recent-moves])))))

  (describe "adjacent-to-target? guard"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["#####"
                                       "#C###"
                                       "#####"
                                       "#####"
                                       "#####"])]
        (reset! atoms/game-map test-map)))

    (it "returns true when army is adjacent to target city"
      (let [data (attack-city/create-attack-city-data [2 2] [1 1] :squad-1)
            entity (assoc data :event-queue [])
            ctx (context/build-context entity)]
        (should (attack-city/adjacent-to-target? ctx))))

    (it "returns false when army is not adjacent"
      (let [data (attack-city/create-attack-city-data [4 4] [1 1] :squad-1)
            entity (assoc data :event-queue [])
            ctx (context/build-context entity)]
        (should-not (attack-city/adjacent-to-target? ctx)))))

  (describe "city-captured? guard"
    (before
      (reset-all-atoms!))

    (it "returns true when target city belongs to computer"
      (let [test-map (build-test-map ["#####"
                                       "#C###"
                                       "#####"
                                       "#####"
                                       "#####"])]
        (reset! atoms/game-map test-map)
        (swap! atoms/game-map assoc-in [1 1 :city-status] :computer)
        (let [data (attack-city/create-attack-city-data [2 2] [1 1] :squad-1)
              entity (assoc data :event-queue [])
              ctx (context/build-context entity)]
          (should (attack-city/city-captured? ctx)))))

    (it "returns false when city is free"
      (let [test-map (build-test-map ["#####"
                                       "#C###"
                                       "#####"
                                       "#####"
                                       "#####"])]
        (reset! atoms/game-map test-map)
        (let [data (attack-city/create-attack-city-data [2 2] [1 1] :squad-1)
              entity (assoc data :event-queue [])
              ctx (context/build-context entity)]
          (should-not (attack-city/city-captured? ctx)))))

    (it "returns false when city belongs to player"
      (let [test-map (build-test-map ["#####"
                                       "#C###"
                                       "#####"
                                       "#####"
                                       "#####"])]
        (reset! atoms/game-map test-map)
        (swap! atoms/game-map assoc-in [1 1 :city-status] :player)
        (let [data (attack-city/create-attack-city-data [2 2] [1 1] :squad-1)
              entity (assoc data :event-queue [])
              ctx (context/build-context entity)]
          (should-not (attack-city/city-captured? ctx))))))

  (describe "state transitions"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["#####"
                                       "#C###"
                                       "#####"
                                       "#####"
                                       "#####"])]
        (reset! atoms/game-map test-map)))

    (describe ":approaching -> :attacking"
      (it "transitions when adjacent to target city"
        (let [data (attack-city/create-attack-city-data [2 2] [1 1] :squad-1)
              entity (assoc data :event-queue [])
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= :attacking (:fsm-state result)))))

    (describe ":approaching -> [:terminal :conquered]"
      (it "transitions when city already belongs to computer"
        (swap! atoms/game-map assoc-in [1 1 :city-status] :computer)
        (let [data (attack-city/create-attack-city-data [3 3] [1 1] :squad-1)
              entity (assoc data :event-queue [])
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= [:terminal :conquered] (:fsm-state result)))))

    (describe ":attacking -> [:terminal :conquered]"
      (it "transitions when city is captured"
        (swap! atoms/game-map assoc-in [1 1 :city-status] :computer)
        (let [data (attack-city/create-attack-city-data [2 2] [1 1] :squad-1)
              entity (assoc data :fsm-state :attacking :event-queue [])
              ctx (context/build-context entity)
              result (engine/step entity ctx)]
          (should= [:terminal :conquered] (:fsm-state result))))))

  (describe "actions"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["#####"
                                       "#C###"
                                       "#####"
                                       "#####"
                                       "#####"])]
        (reset! atoms/game-map test-map)))

    (describe "attempt-capture-action"
      (it "returns move-to target city"
        (let [data (attack-city/create-attack-city-data [2 2] [1 1] :squad-1)
              entity (assoc data :fsm-state :attacking :event-queue [])
              ctx (context/build-context entity)
              result (attack-city/attempt-capture-action ctx)]
          (should= [1 1] (:move-to result)))))

    (describe "report-conquest-action"
      (it "emits city-conquered event"
        (let [data (attack-city/create-attack-city-data [2 2] [1 1] :squad-1 :army-1)
              entity (assoc data :event-queue [])
              ctx (context/build-context entity)
              result (attack-city/report-conquest-action ctx)
              conquest-event (first (filter #(= :city-conquered (:type %)) (:events result)))]
          (should-not-be-nil conquest-event)
          (should= [1 1] (get-in conquest-event [:data :coords]))))

      (it "emits mission-ended event with :conquered reason"
        (let [data (attack-city/create-attack-city-data [2 2] [1 1] :squad-1 :army-1)
              entity (assoc data :event-queue [])
              ctx (context/build-context entity)
              result (attack-city/report-conquest-action ctx)
              ended-event (first (filter #(= :mission-ended (:type %)) (:events result)))]
          (should-not-be-nil ended-event)
          (should= :conquered (get-in ended-event [:data :reason])))))

    (describe "prepare-attack-action"
      (it "returns nil (no action needed, just state transition)"
        (let [data (attack-city/create-attack-city-data [2 2] [1 1] :squad-1)
              entity (assoc data :event-queue [])
              ctx (context/build-context entity)
              result (attack-city/prepare-attack-action ctx)]
          (should-be-nil result)))))

  (describe "movement toward target"
    (before
      (reset-all-atoms!)
      (let [test-map (build-test-map ["#####"
                                       "#C###"
                                       "#####"
                                       "#####"
                                       "#####"])]
        (reset! atoms/game-map test-map)))

    (it "moves toward target when not adjacent"
      (let [data (attack-city/create-attack-city-data [4 4] [1 1] :squad-1)
            entity (assoc data :event-queue [])
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :approaching (:fsm-state result))
        (should-not-be-nil (get-in result [:fsm-data :move-to]))))))

(run-specs)
