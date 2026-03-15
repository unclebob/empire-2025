(ns empire.repair-cycle-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.test.utils :as tu]
            [empire.game-mechanics.movement.api :as movement]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.player.production :as production]))
(describe "repair-damaged-ships"
  (before
    (tu/reset-all-atoms!))

  (it "repairs ship by 1 hit"
    (let [game-map (tu/build-test-map ["~O~"])]
      (tu/set-test-world! game-map)
      (tu/update-test-world! assoc-in [1 0 :shipyard] [{:type :destroyer :hits 1}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])]
        (should= [{:type :destroyer :hits 2}] (:shipyard city)))))

  (it "caps repair at max hits"
    (let [game-map (tu/build-test-map ["~O~"])]
      (tu/set-test-world! game-map)
      (tu/update-test-world! assoc-in [1 0 :shipyard] [{:type :destroyer :hits 3}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])]
        ;; Ship should be launched when fully repaired
        (should= [] (:shipyard city)))))

  (it "launches fully repaired ship onto map"
    (let [game-map (tu/build-test-map ["~O~"])]
      (tu/set-test-world! game-map)
      (tu/update-test-world! assoc-in [1 0 :shipyard] [{:type :destroyer :hits 3}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])
            ship (:contents city)]
        (should= [] (:shipyard city))
        (should= :destroyer (:type ship))
        (should= :player (:owner ship))
        (should= :awake (:mode ship))
        (should= 3 (:hits ship)))))

  (it "repairs multiple ships in same city"
    (let [game-map (tu/build-test-map ["~O~"])]
      (tu/set-test-world! game-map)
      (tu/update-test-world! assoc-in [1 0 :shipyard]
             [{:type :destroyer :hits 1}
              {:type :battleship :hits 5}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])]
        (should= [{:type :destroyer :hits 2}
                  {:type :battleship :hits 6}]
                 (:shipyard city)))))

  (it "does not repair ships in free cities"
    (let [game-map (tu/build-test-map ["~+~"])]  ; + = free city
      (tu/set-test-world! game-map)
      (tu/update-test-world! assoc-in [1 0 :shipyard] [{:type :destroyer :hits 1}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])]
        ;; Should not repair in free city
        (should= [{:type :destroyer :hits 1}] (:shipyard city)))))

  (it "repairs computer ships in computer cities"
    (let [game-map (tu/build-test-map ["~X~"])]
      (tu/set-test-world! game-map)
      (tu/update-test-world! assoc-in [1 0 :shipyard] [{:type :destroyer :hits 1}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])]
        ;; Computer ships should be repaired at computer cities
        (should= [{:type :destroyer :hits 2}] (:shipyard city)))))

  (it "launches repaired ship to adjacent sea when city is occupied"
    (let [game-map (tu/build-test-map ["~O~"])]
      (tu/set-test-world! game-map)
      (tu/update-test-world! assoc-in [1 0 :shipyard]
             [{:type :destroyer :hits 2}   ; will be repaired to 3 (full)
              {:type :battleship :hits 5}]) ; will be repaired to 6
      ;; Put an existing ship on the city
      (tu/update-test-world! assoc-in [1 0 :contents]
             {:type :submarine :owner :player :hits 2 :mode :sentry})
      (game-loop/repair-damaged-ships)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])]
        ;; Destroyer should launch to adjacent sea since city is occupied
        (should= [{:type :battleship :hits 6}]
                 (:shipyard city))
        ;; Original ship still on city
        (should= :submarine (:type (:contents city)))
        ;; Destroyer should be on adjacent sea cell
        (let [sea0 (get-in (test-utils/read-test-state :game-map) [0 0])
              sea2 (get-in (test-utils/read-test-state :game-map) [2 0])]
          (should (or (= :destroyer (:type (:contents sea0)))
                      (= :destroyer (:type (:contents sea2))))))))))
(run-specs)
