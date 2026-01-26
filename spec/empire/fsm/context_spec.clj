(ns empire.fsm.context-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.context :as context]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "FSM Context"

  (describe "build-context"
    (before (reset-all-atoms!))

    (it "includes entity's fsm-data"
      (let [entity {:fsm-data {:target [5 10] :mission :explore}}
            ctx (context/build-context entity)]
        (should= {:target [5 10] :mission :explore} (:fsm-data ctx))))

    (it "includes entity's event-queue"
      (let [entity {:event-queue [{:type :test :priority :high}]}
            ctx (context/build-context entity)]
        (should= [{:type :test :priority :high}] (:event-queue ctx))))

    (it "includes game-map from atoms"
      (reset! atoms/game-map (build-test-map ["##" "~~"]))
      (let [entity {:fsm-data {}}
            ctx (context/build-context entity)]
        (should= @atoms/game-map (:game-map ctx))))

    (it "includes computer-map from atoms"
      (reset! atoms/computer-map {:explored true})
      (let [entity {:fsm-data {}}
            ctx (context/build-context entity)]
        (should= {:explored true} (:computer-map ctx))))

    (it "includes round-number from atoms"
      (reset! atoms/round-number 42)
      (let [entity {:fsm-data {}}
            ctx (context/build-context entity)]
        (should= 42 (:round-number ctx))))

    (it "includes the entity itself"
      (let [entity {:fsm-data {} :fsm-state :exploring :id :unit-1}
            ctx (context/build-context entity)]
        (should= entity (:entity ctx)))))

  (describe "has-event?"
    (it "returns true when queue has event of given type"
      (let [entity {:event-queue [{:type :city-found :priority :high}]}
            ctx (context/build-context entity)]
        (should (context/has-event? ctx :city-found))))

    (it "returns false when queue lacks event of given type"
      (let [entity {:event-queue [{:type :other :priority :high}]}
            ctx (context/build-context entity)]
        (should-not (context/has-event? ctx :city-found))))

    (it "returns false for empty queue"
      (let [entity {:event-queue []}
            ctx (context/build-context entity)]
        (should-not (context/has-event? ctx :city-found)))))

  (describe "get-cell"
    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["#~"
                                               "~#"])))

    (it "returns cell at given coordinates from game-map"
      (let [ctx (context/build-context {:fsm-data {}})]
        (should= :land (:type (context/get-cell ctx [0 0])))
        (should= :sea (:type (context/get-cell ctx [0 1])))
        (should= :sea (:type (context/get-cell ctx [1 0])))
        (should= :land (:type (context/get-cell ctx [1 1])))))

    (it "returns nil for out-of-bounds coordinates"
      (let [ctx (context/build-context {:fsm-data {}})]
        (should-be-nil (context/get-cell ctx [99 99])))))

  (describe "get-computer-cell"
    (before
      (reset-all-atoms!)
      (reset! atoms/computer-map (build-test-map ["# "
                                                   " ~"])))

    (it "returns cell from computer's fog-of-war map"
      (let [ctx (context/build-context {:fsm-data {}})]
        (should= :land (:type (context/get-computer-cell ctx [0 0])))
        (should= :unexplored (:type (context/get-computer-cell ctx [0 1])))
        (should= :sea (:type (context/get-computer-cell ctx [1 1]))))))

  (describe "cell-explored?"
    (before
      (reset-all-atoms!)
      (reset! atoms/computer-map (build-test-map ["# "])))

    (it "returns true for explored cells"
      (let [ctx (context/build-context {:fsm-data {}})]
        (should (context/cell-explored? ctx [0 0]))))

    (it "returns false for unexplored cells"
      (let [ctx (context/build-context {:fsm-data {}})]
        (should-not (context/cell-explored? ctx [0 1]))))))
