(ns empire.repair-launch-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.test.utils :as tu]
            [empire.game-mechanics.movement.api :as movement]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.player.production :as production]))
(describe "launch-ship-from-shipyard"
  (before
    (tu/reset-all-atoms!))

  (it "removes ship from shipyard and places on map"
    (let [game-map (tu/build-test-map ["~O~"])]
      (tu/set-test-world! game-map)
      (tu/update-test-world! assoc-in [1 0 :shipyard] [{:type :destroyer :hits 3}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [city (get-in (test-utils/read-test-state :game-map) [1 0])
            ship (:contents city)]
        (should= [] (:shipyard city))
        (should= :destroyer (:type ship))
        (should= :player (:owner ship))
        (should= :awake (:mode ship))
        (should= 3 (:hits ship))
        (should= 2 (:steps-remaining ship)))))

  (it "reconstructs ship with correct owner from city status"
    (let [game-map (tu/build-test-map ["~X~"])]
      (tu/set-test-world! game-map)
      (tu/update-test-world! assoc-in [1 0 :shipyard] [{:type :battleship :hits 10}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (should= :computer (:owner ship)))))

  (it "sets scaled speed for partially repaired ship"
    (let [game-map (tu/build-test-map ["~O~"])]
      (tu/set-test-world! game-map)
      ;; Battleship at 5/10 hits, speed=2 -> effective speed 1
      (tu/update-test-world! assoc-in [1 0 :shipyard] [{:type :battleship :hits 5}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (should= 1 (:steps-remaining ship)))))

  (it "stamps carrier fields on computer carrier"
    (let [game-map (tu/build-test-map ["~X~"])]
      (tu/set-test-world! game-map)
      (tu/update-test-world! assoc-in [1 0 :shipyard] [{:type :carrier :hits 8}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (should= :carrier (:type ship))
        (should= :computer (:owner ship))
        (should= :positioning (:carrier-mode ship))
        (should (integer? (:carrier-id ship)))
        (should= nil (:group-battleship-id ship))
        (should= [] (:group-submarine-ids ship)))))

  (it "stamps destroyer fields on computer destroyer"
    (let [game-map (tu/build-test-map ["~X~"])]
      (tu/set-test-world! game-map)
      (tu/update-test-world! assoc-in [1 0 :shipyard] [{:type :destroyer :hits 3}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (should= :destroyer (:type ship))
        (should= :computer (:owner ship))
        (should (integer? (:destroyer-id ship)))
        (should= :seeking (:escort-mode ship)))))

  (it "stamps escort fields on computer battleship"
    (let [game-map (tu/build-test-map ["~X~"])]
      (tu/set-test-world! game-map)
      (tu/update-test-world! assoc-in [1 0 :shipyard] [{:type :battleship :hits 10}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (should= :battleship (:type ship))
        (should= :computer (:owner ship))
        (should (integer? (:escort-id ship)))
        (should= :seeking (:escort-mode ship)))))

  (it "stamps transport fields on computer transport"
    (let [game-map (tu/build-test-map ["~X~"])]
      (tu/set-test-world! game-map)
      (tu/update-test-world! assoc-in [1 0 :shipyard] [{:type :transport :hits 3}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (should= :transport (:type ship))
        (should= :computer (:owner ship))
        (should= :loading (:transport-mission ship))
        (should (integer? (:transport-id ship))))))

  (it "stamps patrol fields on computer patrol-boat from country city"
    (let [game-map (tu/build-test-map ["~X~"])]
      (tu/set-test-world! game-map)
      (tu/update-test-world! assoc-in [1 0 :country-id] 5)
      (tu/update-test-world! assoc-in [1 0 :shipyard] [{:type :patrol-boat :hits 2}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (should= :patrol-boat (:type ship))
        (should= :computer (:owner ship))
        (should= :crawling (:patrol-mode ship)))))

  (it "does not stamp computer fields on player ships"
    (let [game-map (tu/build-test-map ["~O~"])]
      (tu/set-test-world! game-map)
      (tu/update-test-world! assoc-in [1 0 :shipyard] [{:type :carrier :hits 8}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in (test-utils/read-test-state :game-map) [1 0 :contents])]
        (should= :carrier (:type ship))
        (should= :player (:owner ship))
        (should-be-nil (:carrier-mode ship))
        (should-be-nil (:carrier-id ship))))))
(run-specs)
