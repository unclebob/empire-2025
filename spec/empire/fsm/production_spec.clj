(ns empire.fsm.production-spec
  (:require [speclj.core :refer :all]
            [empire.fsm.production :as fsm-prod]
            [empire.fsm.lieutenant :as lieutenant]
            [empire.fsm.general :as general]
            [empire.fsm.integration :as integration]
            [empire.atoms :as atoms]
            [empire.player.production :as production]
            [empire.test-utils :refer [reset-all-atoms! build-test-map]]))

(describe "FSM Production"

  (describe "Lieutenant decide-production"
    (before (reset-all-atoms!))

    (it "returns :army in early game (few armies)"
      (let [lt (lieutenant/create-lieutenant "Alpha" [5 5])]
        (should= :army (fsm-prod/decide-production lt [5 5] {}))))

    (it "returns :army when no unit counts provided"
      (let [lt (lieutenant/create-lieutenant "Alpha" [5 5])]
        (should= :army (fsm-prod/decide-production lt [5 5] nil)))))

  (describe "find-lieutenant-for-city"
    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["~X~"
                                               "~~~"
                                               "~~~"]))
      (integration/initialize-general)
      (integration/process-general-turn))

    (it "finds lieutenant that owns the city"
      (let [lt (fsm-prod/find-lieutenant-for-city [0 1])]
        (should-not-be-nil lt)
        (should= "Alpha" (:name lt))))

    (it "returns nil for unknown city"
      (should-be-nil (fsm-prod/find-lieutenant-for-city [9 9]))))

  (describe "process-computer-city-production"
    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["~X~"
                                               "~~~"
                                               "~~~"]))
      (integration/initialize-general)
      (integration/process-general-turn))

    (it "sets production for city without current production"
      (should-be-nil (get @atoms/production [0 1]))
      (fsm-prod/process-computer-city-production [0 1])
      (should-not-be-nil (get @atoms/production [0 1]))
      (should= :army (get-in @atoms/production [[0 1] :item])))

    (it "does not change existing production"
      (production/set-city-production [0 1] :fighter)
      (fsm-prod/process-computer-city-production [0 1])
      (should= :fighter (get-in @atoms/production [[0 1] :item]))))

  (describe "integration with computer/production"
    (before
      (reset-all-atoms!)
      (reset! atoms/game-map (build-test-map ["~X~"
                                               "~~~"
                                               "~~~"]))
      (integration/initialize-general)
      (integration/process-general-turn))

    (it "process-computer-city triggers production decision"
      (should-be-nil (get @atoms/production [0 1]))
      ;; This should now trigger FSM production
      ((requiring-resolve 'empire.computer.production/process-computer-city) [0 1])
      (should-not-be-nil (get @atoms/production [0 1])))))
