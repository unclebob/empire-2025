(ns empire.fsm.waiting-reserve-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.waiting-reserve :as reserve]
            [empire.fsm.context :as context]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Waiting Reserve FSM"

  (describe "create-waiting-reserve-data"

    (it "starts in :moving-to-station when target provided"
      (let [data (reserve/create-waiting-reserve-data [5 5] [10 10])]
        (should= :moving-to-station (:fsm-state data))))

    (it "starts in :holding when no target"
      (let [data (reserve/create-waiting-reserve-data [5 5] nil)]
        (should= :holding (:fsm-state data))))

    (it "sets position in fsm-data"
      (let [data (reserve/create-waiting-reserve-data [5 5] nil)]
        (should= [5 5] (get-in data [:fsm-data :position]))))

    (it "sets station as destination when target provided"
      (let [data (reserve/create-waiting-reserve-data [5 5] [10 10])]
        (should= [10 10] (get-in data [:fsm-data :station])))))

  (describe "state transitions"
    (before
      (reset-all-atoms!)
      ;; Simple map with land
      ;; ~~~~~
      ;; ~####
      ;; ~####
      ;; ~####
      ;; ~~~~~
      (reset! atoms/game-map (build-test-map ["~~~~~"
                                               "~####"
                                               "~####"
                                               "~####"
                                               "~~~~~"]))
      (reset! atoms/computer-map (build-test-map ["~~~~~"
                                                   "~####"
                                                   "~####"
                                                   "~####"
                                                   "~~~~~"])))

    (it "transitions from :moving-to-station to :holding at station"
      (let [data (reserve/create-waiting-reserve-data [2 2] [2 2])
            entity {:fsm (:fsm data)
                    :fsm-state :moving-to-station
                    :fsm-data (assoc (:fsm-data data) :position [2 2] :station [2 2])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :holding (:fsm-state result))))

    (it "stays in :moving-to-station when not at station"
      (let [data (reserve/create-waiting-reserve-data [2 2] [3 3])
            entity {:fsm (:fsm data)
                    :fsm-state :moving-to-station
                    :fsm-data (assoc (:fsm-data data) :position [2 2] :station [3 3])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :moving-to-station (:fsm-state result))))

    (it "stays in :holding while waiting"
      (let [data (reserve/create-waiting-reserve-data [2 2] nil)
            entity {:fsm (:fsm data)
                    :fsm-state :holding
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :holding (:fsm-state result)))))

  (describe "hold-action"
    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["~~~~~"
                                               "~####"
                                               "~####"
                                               "~####"
                                               "~~~~~"]))
      (reset! atoms/computer-map (build-test-map ["~~~~~"
                                                   "~####"
                                                   "~####"
                                                   "~####"
                                                   "~~~~~"])))

    (it "returns nil for move-to in holding state"
      (let [data (reserve/create-waiting-reserve-data [2 2] nil)
            entity {:fsm (:fsm data)
                    :fsm-state :holding
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should-be-nil (get-in result [:fsm-data :move-to]))))

    (it "army stays at current position while holding"
      (let [data (reserve/create-waiting-reserve-data [2 2] nil)
            entity {:fsm (:fsm data)
                    :fsm-state :holding
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= [2 2] (get-in result [:fsm-data :position])))))

  (describe "moving-to-station"
    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["~~~~~"
                                               "~####"
                                               "~####"
                                               "~####"
                                               "~~~~~"]))
      (reset! atoms/computer-map (build-test-map ["~~~~~"
                                                   "~####"
                                                   "~####"
                                                   "~####"
                                                   "~~~~~"])))

    (it "returns move-to toward station"
      (let [data (reserve/create-waiting-reserve-data [1 1] [3 3])
            entity {:fsm (:fsm data)
                    :fsm-state :moving-to-station
                    :fsm-data (assoc (:fsm-data data) :position [1 1] :station [3 3])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            move-to (get-in result [:fsm-data :move-to])]
        (should-not-be-nil move-to)
        ;; Should move closer to station
        (should (or (> (first move-to) 1) (> (second move-to) 1)))))))
