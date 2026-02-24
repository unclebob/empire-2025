(ns empire.repair-spec
  (:require [speclj.core :refer :all]
            [empire.containers.helpers :as uc]
            [empire.units.dispatcher :as dispatcher]
            [empire.atoms :as atoms]
            [empire.test-utils :as tu]
            [empire.movement.movement :as movement]
            [empire.game-loop :as game-loop]
            [empire.containers.ops :as container-ops]
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

(describe "Ship docking"
  (before
    (tu/reset-all-atoms!))

  (context "ship-can-dock?"
    (it "returns true for damaged player ship at player city"
      (let [ship {:type :destroyer :owner :player :hits 2}  ; max is 3
            city {:type :city :city-status :player}]
        (should (uc/ship-can-dock? ship city))))

    (it "returns false for undamaged ship"
      (let [ship {:type :destroyer :owner :player :hits 3}  ; full health
            city {:type :city :city-status :player}]
        (should-not (uc/ship-can-dock? ship city))))

    (it "returns false for damaged ship at enemy city"
      (let [ship {:type :destroyer :owner :player :hits 2}
            city {:type :city :city-status :computer}]
        (should-not (uc/ship-can-dock? ship city))))

    (it "returns false for damaged ship at free city"
      (let [ship {:type :destroyer :owner :player :hits 2}
            city {:type :city :city-status :free}]
        (should-not (uc/ship-can-dock? ship city))))

    (it "returns false for non-city cell"
      (let [ship {:type :destroyer :owner :player :hits 2}
            sea {:type :sea}]
        (should-not (uc/ship-can-dock? ship sea))))

    (it "returns false for non-naval unit"
      (let [army {:type :army :owner :player :hits 1}
            city {:type :city :city-status :player}]
        (should-not (uc/ship-can-dock? army city))))

    (it "returns true for computer ship at computer city"
      (let [ship {:type :destroyer :owner :computer :hits 2}
            city {:type :city :city-status :computer}]
        (should (uc/ship-can-dock? ship city))))))

(describe "Docking during movement"
  (before
    (tu/reset-all-atoms!))

  (it "damaged ship entering friendly city goes into shipyard"
    (let [game-map (tu/build-test-map ["~D~"
                                       "~O~"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (tu/set-test-unit atoms/game-map "D" :hits 2 :mode :moving :target [1 1])
      (let [result (movement/move-unit [1 0] [1 1]
                                       (get-in @atoms/game-map [1 0])
                                       atoms/game-map)
            city (get-in @atoms/game-map [1 1])
            origin (get-in @atoms/game-map [1 0])]
        (should= :docked (:result result))
        (should= [1 1] (:pos result))
        (should= [{:type :destroyer :hits 2}] (:shipyard city))
        (should-not (:contents city))
        (should-not (:contents origin)))))

  (it "damaged ship docks at city that has a unit on it"
    (let [game-map (tu/build-test-map ["~D~"
                                       "~O~"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (tu/set-test-unit atoms/game-map "D" :hits 2 :mode :moving :target [1 1])
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :army :mode :moving :target [2 1] :hits 1 :owner :player})
      (let [result (movement/move-unit [1 0] [1 1]
                                       (get-in @atoms/game-map [1 0])
                                       atoms/game-map)
            city (get-in @atoms/game-map [1 1])]
        (should= :docked (:result result))
        (should= [{:type :destroyer :hits 2}] (:shipyard city))
        (should= :army (:type (:contents city))))))

  (it "undamaged ship cannot enter city"
    (let [game-map (tu/build-test-map ["~D~"
                                       "~O~"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (tu/set-test-unit atoms/game-map "D" :hits 3 :mode :moving :target [1 1])
      (let [result (movement/move-unit [1 0] [1 1]
                                       (get-in @atoms/game-map [1 0])
                                       atoms/game-map)]
        ;; Should wake up, not dock
        (should= :woke (:result result)))))

  (it "displays dock message on line 2"
    (let [game-map (tu/build-test-map ["~D~"
                                       "~O~"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (reset! atoms/turn-message "")
      (tu/set-test-unit atoms/game-map "D" :hits 2 :mode :moving :target [1 1])
      (movement/move-unit [1 0] [1 1]
                          (get-in @atoms/game-map [1 0])
                          atoms/game-map)
      (should= "Destroyer docked for repair." @atoms/turn-message)))

  (it "displays dock message for battleship"
    (let [game-map (tu/build-test-map ["~B~"
                                       "~O~"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (reset! atoms/turn-message "")
      (tu/set-test-unit atoms/game-map "B" :hits 5 :mode :moving :target [1 1])
      (movement/move-unit [1 0] [1 1]
                          (get-in @atoms/game-map [1 0])
                          atoms/game-map)
      (should= "Battleship docked for repair." @atoms/turn-message))))

(describe "repair-damaged-ships"
  (before
    (tu/reset-all-atoms!))

  (it "repairs ship by 1 hit"
    (let [game-map (tu/build-test-map ["~O~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [1 0 :shipyard] [{:type :destroyer :hits 1}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in @atoms/game-map [1 0])]
        (should= [{:type :destroyer :hits 2}] (:shipyard city)))))

  (it "caps repair at max hits"
    (let [game-map (tu/build-test-map ["~O~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [1 0 :shipyard] [{:type :destroyer :hits 3}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in @atoms/game-map [1 0])]
        ;; Ship should be launched when fully repaired
        (should= [] (:shipyard city)))))

  (it "launches fully repaired ship onto map"
    (let [game-map (tu/build-test-map ["~O~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [1 0 :shipyard] [{:type :destroyer :hits 3}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in @atoms/game-map [1 0])
            ship (:contents city)]
        (should= [] (:shipyard city))
        (should= :destroyer (:type ship))
        (should= :player (:owner ship))
        (should= :awake (:mode ship))
        (should= 3 (:hits ship)))))

  (it "repairs multiple ships in same city"
    (let [game-map (tu/build-test-map ["~O~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [1 0 :shipyard]
             [{:type :destroyer :hits 1}
              {:type :battleship :hits 5}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in @atoms/game-map [1 0])]
        (should= [{:type :destroyer :hits 2}
                  {:type :battleship :hits 6}]
                 (:shipyard city)))))

  (it "does not repair ships in free cities"
    (let [game-map (tu/build-test-map ["~+~"])]  ; + = free city
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [1 0 :shipyard] [{:type :destroyer :hits 1}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in @atoms/game-map [1 0])]
        ;; Should not repair in free city
        (should= [{:type :destroyer :hits 1}] (:shipyard city)))))

  (it "repairs computer ships in computer cities"
    (let [game-map (tu/build-test-map ["~X~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [1 0 :shipyard] [{:type :destroyer :hits 1}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in @atoms/game-map [1 0])]
        ;; Computer ships should be repaired at computer cities
        (should= [{:type :destroyer :hits 2}] (:shipyard city)))))

  (it "launches repaired ship to adjacent sea when city is occupied"
    (let [game-map (tu/build-test-map ["~O~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [1 0 :shipyard]
             [{:type :destroyer :hits 2}   ; will be repaired to 3 (full)
              {:type :battleship :hits 5}]) ; will be repaired to 6
      ;; Put an existing ship on the city
      (swap! atoms/game-map assoc-in [1 0 :contents]
             {:type :submarine :owner :player :hits 2 :mode :sentry})
      (game-loop/repair-damaged-ships)
      (let [city (get-in @atoms/game-map [1 0])]
        ;; Destroyer should launch to adjacent sea since city is occupied
        (should= [{:type :battleship :hits 6}]
                 (:shipyard city))
        ;; Original ship still on city
        (should= :submarine (:type (:contents city)))
        ;; Destroyer should be on adjacent sea cell
        (let [sea0 (get-in @atoms/game-map [0 0])
              sea2 (get-in @atoms/game-map [2 0])]
          (should (or (= :destroyer (:type (:contents sea0)))
                      (= :destroyer (:type (:contents sea2))))))))))

(describe "launch-ship-from-shipyard"
  (before
    (tu/reset-all-atoms!))

  (it "removes ship from shipyard and places on map"
    (let [game-map (tu/build-test-map ["~O~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [1 0 :shipyard] [{:type :destroyer :hits 3}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [city (get-in @atoms/game-map [1 0])
            ship (:contents city)]
        (should= [] (:shipyard city))
        (should= :destroyer (:type ship))
        (should= :player (:owner ship))
        (should= :awake (:mode ship))
        (should= 3 (:hits ship))
        (should= 2 (:steps-remaining ship)))))

  (it "reconstructs ship with correct owner from city status"
    (let [game-map (tu/build-test-map ["~X~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [1 0 :shipyard] [{:type :battleship :hits 10}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in @atoms/game-map [1 0 :contents])]
        (should= :computer (:owner ship)))))

  (it "sets scaled speed for partially repaired ship"
    (let [game-map (tu/build-test-map ["~O~"])]
      (reset! atoms/game-map game-map)
      ;; Battleship at 5/10 hits, speed=2 -> effective speed 1
      (swap! atoms/game-map assoc-in [1 0 :shipyard] [{:type :battleship :hits 5}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in @atoms/game-map [1 0 :contents])]
        (should= 1 (:steps-remaining ship)))))

  (it "stamps carrier fields on computer carrier"
    (let [game-map (tu/build-test-map ["~X~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [1 0 :shipyard] [{:type :carrier :hits 8}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in @atoms/game-map [1 0 :contents])]
        (should= :carrier (:type ship))
        (should= :computer (:owner ship))
        (should= :positioning (:carrier-mode ship))
        (should (integer? (:carrier-id ship)))
        (should= nil (:group-battleship-id ship))
        (should= [] (:group-submarine-ids ship)))))

  (it "stamps destroyer fields on computer destroyer"
    (let [game-map (tu/build-test-map ["~X~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [1 0 :shipyard] [{:type :destroyer :hits 3}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in @atoms/game-map [1 0 :contents])]
        (should= :destroyer (:type ship))
        (should= :computer (:owner ship))
        (should (integer? (:destroyer-id ship)))
        (should= :seeking (:escort-mode ship)))))

  (it "stamps escort fields on computer battleship"
    (let [game-map (tu/build-test-map ["~X~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [1 0 :shipyard] [{:type :battleship :hits 10}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in @atoms/game-map [1 0 :contents])]
        (should= :battleship (:type ship))
        (should= :computer (:owner ship))
        (should (integer? (:escort-id ship)))
        (should= :seeking (:escort-mode ship)))))

  (it "stamps transport fields on computer transport"
    (let [game-map (tu/build-test-map ["~X~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [1 0 :shipyard] [{:type :transport :hits 3}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in @atoms/game-map [1 0 :contents])]
        (should= :transport (:type ship))
        (should= :computer (:owner ship))
        (should= :loading (:transport-mission ship))
        (should (integer? (:transport-id ship))))))

  (it "stamps patrol fields on computer patrol-boat from country city"
    (let [game-map (tu/build-test-map ["~X~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [1 0 :country-id] 5)
      (swap! atoms/game-map assoc-in [1 0 :shipyard] [{:type :patrol-boat :hits 2}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in @atoms/game-map [1 0 :contents])]
        (should= :patrol-boat (:type ship))
        (should= :computer (:owner ship))
        (should= 5 (:patrol-country-id ship))
        (should= :clockwise (:patrol-direction ship))
        (should= :homing (:patrol-mode ship)))))

  (it "does not stamp computer fields on player ships"
    (let [game-map (tu/build-test-map ["~O~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [1 0 :shipyard] [{:type :carrier :hits 8}])
      (container-ops/launch-ship-from-shipyard [1 0] 0)
      (let [ship (get-in @atoms/game-map [1 0 :contents])]
        (should= :carrier (:type ship))
        (should= :player (:owner ship))
        (should-be-nil (:carrier-mode ship))
        (should-be-nil (:carrier-id ship))))))

(run-specs)
