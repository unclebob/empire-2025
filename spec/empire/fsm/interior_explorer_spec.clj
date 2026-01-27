(ns empire.fsm.interior-explorer-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.interior-explorer :as interior]
            [empire.fsm.context :as context]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Interior Explorer FSM"

  (describe "create-interior-explorer-data"

    (it "starts in :moving-to-start when target provided"
      (let [data (interior/create-interior-explorer-data [5 5] [10 10])]
        (should= :moving-to-start (:fsm-state data))))

    (it "starts in :exploring-interior when no target"
      (let [data (interior/create-interior-explorer-data [5 5] nil)]
        (should= :exploring-interior (:fsm-state data))))

    (it "sets position in fsm-data"
      (let [data (interior/create-interior-explorer-data [5 5] nil)]
        (should= [5 5] (get-in data [:fsm-data :position]))))

    (it "sets destination when target provided"
      (let [data (interior/create-interior-explorer-data [5 5] [10 10])]
        (should= [10 10] (get-in data [:fsm-data :destination])))))

  (describe "state transitions"
    (before
      (reset-all-atoms!)
      ;; Map with landlocked interior
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

    (it "transitions from :moving-to-start to :exploring-interior at destination"
      (let [data (interior/create-interior-explorer-data [2 2] [2 2])
            entity {:fsm (:fsm data)
                    :fsm-state :moving-to-start
                    :fsm-data (assoc (:fsm-data data) :position [2 2] :destination [2 2])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :exploring-interior (:fsm-state result))))

    (it "stays in :moving-to-start when not at destination"
      (let [data (interior/create-interior-explorer-data [2 2] [3 3])
            entity {:fsm (:fsm data)
                    :fsm-state :moving-to-start
                    :fsm-data (assoc (:fsm-data data) :position [2 2] :destination [3 3])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :moving-to-start (:fsm-state result))))

    (it "stays in :exploring-interior while exploring"
      (let [data (interior/create-interior-explorer-data [2 2] nil)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring-interior
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :exploring-interior (:fsm-state result)))))

  (describe "explore-interior-action"
    (before
      (reset-all-atoms!)
      ;; Map with landlocked interior and some unexplored areas
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
      ;; Only partially explored
      (reset! atoms/computer-map (build-test-map ["~~~~~"
                                                   "~##  "
                                                   "~##  "
                                                   "~    "
                                                   "~~~~~"])))

    (it "returns move-to in result"
      (let [data (interior/create-interior-explorer-data [2 2] nil)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring-interior
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should-contain :move-to (:fsm-data result))))

    (it "prefers unexplored neighbors"
      (let [data (interior/create-interior-explorer-data [2 2] nil)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring-interior
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            move-to (get-in result [:fsm-data :move-to])]
        ;; Move should be toward unexplored area (columns 3-4)
        (should (>= (second move-to) 2))))

    (it "emits cells-discovered event"
      (let [data (interior/create-interior-explorer-data [2 2] nil)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring-interior
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should (some #(= :cells-discovered (:type %)) (:event-queue result))))))

  (describe "avoids coastal cells"
    (before
      (reset-all-atoms!)
      ;; Map where army at [2,2] could move to coastal [2,1] or interior [2,3]
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

    (it "avoids moving to coastal cells when interior options exist"
      ;; This test verifies the preference for interior over coastal
      ;; Position [2,1] is coastal (adjacent to sea at [2,0])
      ;; Position [2,3] is interior (not adjacent to sea)
      (let [data (interior/create-interior-explorer-data [2 2] nil)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring-interior
                    :fsm-data (assoc (:fsm-data data) :recent-moves [[2 2]])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            move-to (get-in result [:fsm-data :move-to])]
        ;; Should prefer non-coastal moves (column 2 or greater)
        (when move-to
          (should (>= (second move-to) 2)))))))
