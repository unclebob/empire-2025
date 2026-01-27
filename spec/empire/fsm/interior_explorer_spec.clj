(ns empire.fsm.interior-explorer-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.interior-explorer :as interior]
            [empire.fsm.context :as context]
            [empire.fsm.engine :as engine]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "Interior Explorer FSM"

  (describe "create-interior-explorer-data"

    (it "starts in :moving-to-target when target provided"
      (let [data (interior/create-interior-explorer-data [5 5] [10 10])]
        (should= :moving-to-target (:fsm-state data))))

    (it "starts in :exploring when no target or already at target"
      (let [data (interior/create-interior-explorer-data [5 5] nil)]
        (should= :exploring (:fsm-state data))))

    (it "starts in :exploring when position equals target"
      (let [data (interior/create-interior-explorer-data [5 5] [5 5])]
        (should= :exploring (:fsm-state data))))

    (it "sets position in fsm-data"
      (let [data (interior/create-interior-explorer-data [5 5] nil)]
        (should= [5 5] (get-in data [:fsm-data :position]))))

    (it "sets destination when target provided"
      (let [data (interior/create-interior-explorer-data [5 5] [10 10])]
        (should= [10 10] (get-in data [:fsm-data :destination]))))

    (it "sets initial-city when provided"
      (let [data (interior/create-interior-explorer-data [5 5] nil :army-1 [3 3])]
        (should= [3 3] (get-in data [:fsm-data :initial-city]))))

    (it "sets unit-id in fsm-data"
      (let [data (interior/create-interior-explorer-data [5 5] nil :army-123)]
        (should= :army-123 (get-in data [:fsm-data :unit-id])))))

  (describe "moving-to-target state"
    (before
      (reset-all-atoms!)
      ;; Large interior landmass
      (let [test-map (build-test-map ["~~~~~~~~~~"
                                       "~########~"
                                       "~########~"
                                       "~########~"
                                       "~########~"
                                       "~########~"
                                       "~~~~~~~~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "transitions to :exploring when reaching target"
      (let [data (interior/create-interior-explorer-data [2 2] [2 2])
            entity {:fsm (:fsm data)
                    :fsm-state :moving-to-target
                    :fsm-data (assoc (:fsm-data data) :position [2 2] :destination [2 2])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :exploring (:fsm-state result))))

    (it "stays in :moving-to-target while traveling to target"
      (let [data (interior/create-interior-explorer-data [2 2] [4 5])
            entity {:fsm (:fsm data)
                    :fsm-state :moving-to-target
                    :fsm-data (:fsm-data data)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :moving-to-target (:fsm-state result))
        (should-contain :move-to (:fsm-data result)))))

  (describe "exploring state - diagonal sweep"
    (before
      (reset-all-atoms!)
      ;; Interior with some unexplored areas
      ;; Position [3,3] is deep interior (not coastal)
      (let [game-map (build-test-map ["~~~~~~~~~~"
                                       "~########~"
                                       "~########~"
                                       "~########~"
                                       "~########~"
                                       "~########~"
                                       "~~~~~~~~~~"])
            ;; Computer has partially explored - only center
            computer-map (build-test-map ["~~~~~~~~~~"
                                           "~        ~"
                                           "~  ###   ~"
                                           "~  ###   ~"
                                           "~  ###   ~"
                                           "~        ~"
                                           "~~~~~~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map computer-map)))

    (it "stays in :exploring while unexplored cells exist"
      ;; Position [3,3] is deep interior, not coastal
      (let [data (interior/create-interior-explorer-data [3 3] nil :army-1 [3 3])
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data) :position [3 3] :explore-direction [1 1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :exploring (:fsm-state result))
        (should-contain :move-to (:fsm-data result))))

    (it "emits cells-discovered event"
      (let [data (interior/create-interior-explorer-data [2 2] nil :army-1 [1 1])
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data) :explore-direction [1 1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should (some #(= :cells-discovered (:type %)) (:event-queue result))))))

  (describe "exploring state - transitions to rastering"
    (before
      (reset-all-atoms!)
      ;; Position at coast - should trigger rastering
      (let [game-map (build-test-map ["~~~~~~~~~~"
                                       "~########~"
                                       "~########~"
                                       "~########~"
                                       "~~~~~~~~~~"])
            computer-map (build-test-map ["~~~~~~~~~~"
                                           "~###     ~"
                                           "~###     ~"
                                           "~###     ~"
                                           "~~~~~~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map computer-map)))

    (it "transitions to :rastering when reaching coast"
      (let [data (interior/create-interior-explorer-data [1 1] nil :army-1 [2 2])
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data)
                                     :position [1 1]
                                     :explore-direction [0 -1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        ;; At coast (adjacent to sea at [1,0]), should start rastering
        (should= :rastering (:fsm-state result)))))

  (describe "rastering state"
    (before
      (reset-all-atoms!)
      ;; Map for raster pattern testing
      (let [game-map (build-test-map ["~~~~~~~~~~"
                                       "~########~"
                                       "~########~"
                                       "~########~"
                                       "~########~"
                                       "~~~~~~~~~~"])
            computer-map (build-test-map ["~~~~~~~~~~"
                                           "~####    ~"
                                           "~####    ~"
                                           "~####    ~"
                                           "~####    ~"
                                           "~~~~~~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map computer-map)))

    (it "stays in :rastering while exploring"
      (let [data (interior/create-interior-explorer-data [2 4] nil :army-1 [1 1])
            entity {:fsm (:fsm data)
                    :fsm-state :rastering
                    :fsm-data (assoc (:fsm-data data)
                                     :position [2 4]
                                     :raster-axis :horizontal
                                     :raster-direction 1)
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= :rastering (:fsm-state result))
        (should-contain :move-to (:fsm-data result)))))

  (describe "terminal states"
    (before
      (reset-all-atoms!)
      ;; Small isolated land cell
      (let [test-map (build-test-map ["~~~"
                                       "~#~"
                                       "~~~"])]
        (reset! atoms/game-map test-map)
        (reset! atoms/computer-map test-map)))

    (it "transitions to [:terminal :stuck] when no valid moves"
      (let [data (interior/create-interior-explorer-data [1 1] nil :army-1)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= [:terminal :stuck] (:fsm-state result))))

    (it "emits :mission-ended event with :stuck reason"
      (let [data (interior/create-interior-explorer-data [1 1] nil :army-456)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data) :position [1 1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            mission-event (first (filter #(= :mission-ended (:type %)) (:event-queue result)))]
        (should-not-be-nil mission-event)
        (should= :stuck (get-in mission-event [:data :reason])))))

  (describe "no-unexplored terminal"
    (before
      (reset-all-atoms!)
      ;; Fully explored area
      (let [test-map (build-test-map ["~~~~~"
                                       "~###~"
                                       "~###~"
                                       "~~~~~"])]
        (reset! atoms/game-map test-map)
        ;; Computer has fully explored everything
        (reset! atoms/computer-map test-map)))

    (it "transitions to [:terminal :no-unexplored] when area fully explored"
      (let [data (interior/create-interior-explorer-data [1 2] nil :army-1)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data) :position [1 2])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        (should= [:terminal :no-unexplored] (:fsm-state result))))

    (it "emits :mission-ended event with :no-unexplored reason"
      (let [data (interior/create-interior-explorer-data [1 2] nil :army-789)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data) :position [1 2])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            mission-event (first (filter #(= :mission-ended (:type %)) (:event-queue result)))]
        (should-not-be-nil mission-event)
        (should= :no-unexplored (get-in mission-event [:data :reason])))))

  (describe "BFS pathfinding to unexplored"
    (before
      (reset-all-atoms!)
      ;; Map with obstacle requiring path around
      (let [game-map (build-test-map ["~~~~~~~~~"
                                       "~#######~"
                                       "~#a####~"
                                       "~#######~"
                                       "~~~~~~~~~"])
            ;; Unexplored area on far right, army blocked by explored
            computer-map (build-test-map ["~~~~~~~~~"
                                           "~####   ~"
                                           "~####   ~"
                                           "~####   ~"
                                           "~~~~~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map computer-map)))

    (it "uses BFS to route around obstacles toward unexplored"
      (let [data (interior/create-interior-explorer-data [2 2] nil :army-1 [2 2])
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data)
                                     :position [2 2]
                                     :explore-direction [0 1])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)]
        ;; Should have computed a move toward unexplored area
        (should-contain :move-to (:fsm-data result)))))

  (describe "free city detection"
    (before
      (reset-all-atoms!)
      ;; Consistent 8-column map with free city at [2,5]
      ;; Army at [2,4] (interior, not coastal) is adjacent to city
      (let [game-map (build-test-map ["~~~~~~~~"
                                       "~######~"
                                       "~###+##~"
                                       "~######~"
                                       "~~~~~~~~"])
            ;; Partially unexplored so exploration continues
            computer-map (build-test-map ["~~~~~~~~"
                                           "~###   ~"
                                           "~###   ~"
                                           "~###   ~"
                                           "~~~~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map computer-map)))

    (it "emits free-city-found event when adjacent to free city"
      ;; Army at [2,3] is adjacent to free city at [2,4]
      (let [data (interior/create-interior-explorer-data [2 3] nil :army-1)
            entity {:fsm (:fsm data)
                    :fsm-state :exploring
                    :fsm-data (assoc (:fsm-data data) :position [2 3])
                    :event-queue []}
            ctx (context/build-context entity)
            result (engine/step entity ctx)
            city-event (first (filter #(= :free-city-found (:type %)) (:event-queue result)))]
        (should-not-be-nil city-event)
        (should= [2 4] (get-in city-event [:data :coords]))))))

(run-specs)
