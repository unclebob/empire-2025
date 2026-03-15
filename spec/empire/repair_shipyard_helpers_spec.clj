(ns empire.repair-shipyard-helpers-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.test.utils :as tu]
            [empire.game-mechanics.movement.api :as movement]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.player.production :as production]))
(describe "Shipyard helpers"
  (context "add-ship-to-shipyard"
    (it "adds a ship to empty shipyard"
      (let [city {:type :city :city-status :player}
            result (uc/add-ship-to-shipyard city :destroyer 2)]
        (should= [{:type :destroyer :hits 2}] (:shipyard result))))

    (it "adds a ship to existing shipyard"
      (let [city {:type :city :city-status :player
                  :shipyard [{:type :battleship :hits 7}]}
            result (uc/add-ship-to-shipyard city :destroyer 2)]
        (should= [{:type :battleship :hits 7}
                  {:type :destroyer :hits 2}]
                 (:shipyard result)))))

  (context "remove-ship-from-shipyard"
    (it "removes ship at index 0"
      (let [city {:type :city :shipyard [{:type :destroyer :hits 2}
                                          {:type :battleship :hits 7}]}
            result (uc/remove-ship-from-shipyard city 0)]
        (should= [{:type :battleship :hits 7}] (:shipyard result))))

    (it "removes ship at index 1"
      (let [city {:type :city :shipyard [{:type :destroyer :hits 2}
                                          {:type :battleship :hits 7}]}
            result (uc/remove-ship-from-shipyard city 1)]
        (should= [{:type :destroyer :hits 2}] (:shipyard result))))

    (it "returns empty shipyard when last ship removed"
      (let [city {:type :city :shipyard [{:type :destroyer :hits 2}]}
            result (uc/remove-ship-from-shipyard city 0)]
        (should= [] (:shipyard result)))))

  (context "get-shipyard-ships"
    (it "returns empty vector when no shipyard"
      (let [city {:type :city :city-status :player}]
        (should= [] (uc/get-shipyard-ships city))))

    (it "returns ships when shipyard exists"
      (let [city {:type :city :shipyard [{:type :destroyer :hits 2}]}]
        (should= [{:type :destroyer :hits 2}] (uc/get-shipyard-ships city)))))

  (context "repair-ship"
    (it "increments hits by 1"
      (let [ship {:type :destroyer :hits 2}
            result (uc/repair-ship ship)]
        (should= 3 (:hits result))))

    (it "caps hits at max for unit type"
      (let [ship {:type :destroyer :hits 3}  ; destroyer max is 3
            result (uc/repair-ship ship)]
        (should= 3 (:hits result))))

    (it "repairs battleship toward max of 10"
      (let [ship {:type :battleship :hits 8}
            result (uc/repair-ship ship)]
        (should= 9 (:hits result)))))

  (context "ship-fully-repaired?"
    (it "returns true when hits equal max"
      (let [ship {:type :destroyer :hits 3}]
        (should (uc/ship-fully-repaired? ship))))

    (it "returns false when hits below max"
      (let [ship {:type :destroyer :hits 2}]
        (should-not (uc/ship-fully-repaired? ship))))

    (it "works for battleship"
      (let [damaged {:type :battleship :hits 9}
            repaired {:type :battleship :hits 10}]
        (should-not (uc/ship-fully-repaired? damaged))
        (should (uc/ship-fully-repaired? repaired))))))
(run-specs)
