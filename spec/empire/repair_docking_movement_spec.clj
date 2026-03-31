(ns empire.repair-docking-movement-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.test.utils :as tu]
            [empire.game-mechanics.movement.api :as movement]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.player.production :as production]))
(describe "Docking during movement"
  (before
    (tu/reset-all-atoms!))

  (it "damaged ship entering friendly city goes into shipyard"
    (let [game-map (tu/build-test-map ["~D~"
                                       "~O~"
                                       "~~~"])]
      (tu/set-test-world! game-map)
      (tu/set-test-unit (test-utils/game-map-atom) "D" :hits 2 :mode :moving :target [1 1])
      (let [result (movement/move-unit [1 0] [1 1]
                                       (get-in (test-utils/read-test-state :game-map) [1 0])
                                       (test-utils/game-map-atom))
            city (get-in (test-utils/read-test-state :game-map) [1 1])
            origin (get-in (test-utils/read-test-state :game-map) [1 0])]
        (should= :docked (:result result))
        (should= [1 1] (:pos result))
        (should= [{:type :destroyer :hits 2}] (:shipyard city))
        (should-not (:contents city))
        (should-not (:contents origin)))))

  (it "damaged ship docks at city that has a unit on it"
    (let [game-map (tu/build-test-map ["~D~"
                                       "~O~"
                                       "~~~"])]
      (tu/set-test-world! game-map)
      (tu/set-test-unit (test-utils/game-map-atom) "D" :hits 2 :mode :moving :target [1 1])
      (tu/update-test-world! assoc-in [1 1 :contents]
             {:type :army :mode :moving :target [2 1] :hits 1 :owner :player})
      (let [result (movement/move-unit [1 0] [1 1]
                                       (get-in (test-utils/read-test-state :game-map) [1 0])
                                       (test-utils/game-map-atom))
            city (get-in (test-utils/read-test-state :game-map) [1 1])]
        (should= :docked (:result result))
        (should= [{:type :destroyer :hits 2}] (:shipyard city))
        (should= :army (:type (:contents city))))))

  (it "undamaged ship cannot enter city"
    (let [game-map (tu/build-test-map ["~D~"
                                       "~O~"
                                       "~~~"])]
      (tu/set-test-world! game-map)
      (tu/set-test-unit (test-utils/game-map-atom) "D" :hits 3 :mode :moving :target [1 1])
      (let [result (movement/move-unit [1 0] [1 1]
                                       (get-in (test-utils/read-test-state :game-map) [1 0])
                                       (test-utils/game-map-atom))]
        ;; Should wake up, not dock
        (should= :woke (:result result)))))

  (it "displays dock message on line 2"
    (let [game-map (tu/build-test-map ["~D~"
                                       "~O~"
                                       "~~~"])]
      (tu/set-test-world! game-map)
      (test-utils/set-test-state! :command-message "")
      (tu/set-test-unit (test-utils/game-map-atom) "D" :hits 2 :mode :moving :target [1 1])
      (movement/move-unit [1 0] [1 1]
                          (get-in (test-utils/read-test-state :game-map) [1 0])
                          (test-utils/game-map-atom))
      (should= "Destroyer docked for repair. 2/3 hits remain." (test-utils/read-test-state :command-message))))

  (it "displays dock message for battleship"
    (let [game-map (tu/build-test-map ["~B~"
                                       "~O~"
                                       "~~~"])]
      (tu/set-test-world! game-map)
      (test-utils/set-test-state! :command-message "")
      (tu/set-test-unit (test-utils/game-map-atom) "B" :hits 5 :mode :moving :target [1 1])
      (movement/move-unit [1 0] [1 1]
                          (get-in (test-utils/read-test-state :game-map) [1 0])
                          (test-utils/game-map-atom))
      (should= "Battleship docked for repair. 5/10 hits remain." (test-utils/read-test-state :command-message)))))
(run-specs)
