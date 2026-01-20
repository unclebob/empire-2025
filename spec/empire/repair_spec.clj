(ns empire.repair-spec
  (:require [speclj.core :refer :all]
            [empire.unit-container :as uc]
            [empire.units.dispatcher :as dispatcher]
            [empire.atoms :as atoms]
            [empire.test-utils :as tu]
            [empire.movement.movement :as movement]
            [empire.game-loop :as game-loop]
            [empire.container-ops :as container-ops]))

(describe "Shipyard helpers"
  (describe "add-ship-to-shipyard"
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

  (describe "remove-ship-from-shipyard"
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

  (describe "get-shipyard-ships"
    (it "returns empty vector when no shipyard"
      (let [city {:type :city :city-status :player}]
        (should= [] (uc/get-shipyard-ships city))))

    (it "returns ships when shipyard exists"
      (let [city {:type :city :shipyard [{:type :destroyer :hits 2}]}]
        (should= [{:type :destroyer :hits 2}] (uc/get-shipyard-ships city)))))

  (describe "repair-ship"
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

  (describe "ship-fully-repaired?"
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

  (describe "ship-can-dock?"
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
      (let [result (movement/move-unit [0 1] [1 1]
                                       (get-in @atoms/game-map [0 1])
                                       atoms/game-map)
            city (get-in @atoms/game-map [1 1])
            origin (get-in @atoms/game-map [0 1])]
        (should= :docked (:result result))
        (should= [1 1] (:pos result))
        (should= [{:type :destroyer :hits 2}] (:shipyard city))
        (should-not (:contents city))
        (should-not (:contents origin)))))

  (it "undamaged ship cannot enter city"
    (let [game-map (tu/build-test-map ["~D~"
                                       "~O~"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (tu/set-test-unit atoms/game-map "D" :hits 3 :mode :moving :target [1 1])
      (let [result (movement/move-unit [0 1] [1 1]
                                       (get-in @atoms/game-map [0 1])
                                       atoms/game-map)]
        ;; Should wake up, not dock
        (should= :woke (:result result)))))

  (it "displays dock message on line 2"
    (let [game-map (tu/build-test-map ["~D~"
                                       "~O~"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (reset! atoms/line2-message "")
      (tu/set-test-unit atoms/game-map "D" :hits 2 :mode :moving :target [1 1])
      (movement/move-unit [0 1] [1 1]
                          (get-in @atoms/game-map [0 1])
                          atoms/game-map)
      (should= "Destroyer docked for repair." @atoms/line2-message)))

  (it "displays dock message for battleship"
    (let [game-map (tu/build-test-map ["~B~"
                                       "~O~"
                                       "~~~"])]
      (reset! atoms/game-map game-map)
      (reset! atoms/line2-message "")
      (tu/set-test-unit atoms/game-map "B" :hits 5 :mode :moving :target [1 1])
      (movement/move-unit [0 1] [1 1]
                          (get-in @atoms/game-map [0 1])
                          atoms/game-map)
      (should= "Battleship docked for repair." @atoms/line2-message))))

(describe "repair-damaged-ships"
  (before
    (tu/reset-all-atoms!))

  (it "repairs ship by 1 hit"
    (let [game-map (tu/build-test-map ["~O~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [0 1 :shipyard] [{:type :destroyer :hits 1}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in @atoms/game-map [0 1])]
        (should= [{:type :destroyer :hits 2}] (:shipyard city)))))

  (it "caps repair at max hits"
    (let [game-map (tu/build-test-map ["~O~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [0 1 :shipyard] [{:type :destroyer :hits 3}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in @atoms/game-map [0 1])]
        ;; Ship should be launched when fully repaired
        (should= [] (:shipyard city)))))

  (it "launches fully repaired ship onto map"
    (let [game-map (tu/build-test-map ["~O~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [0 1 :shipyard] [{:type :destroyer :hits 3}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in @atoms/game-map [0 1])
            ship (:contents city)]
        (should= [] (:shipyard city))
        (should= :destroyer (:type ship))
        (should= :player (:owner ship))
        (should= :awake (:mode ship))
        (should= 3 (:hits ship)))))

  (it "repairs multiple ships in same city"
    (let [game-map (tu/build-test-map ["~O~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [0 1 :shipyard]
             [{:type :destroyer :hits 1}
              {:type :battleship :hits 5}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in @atoms/game-map [0 1])]
        (should= [{:type :destroyer :hits 2}
                  {:type :battleship :hits 6}]
                 (:shipyard city)))))

  (it "does not repair ships in free cities"
    (let [game-map (tu/build-test-map ["~+~"])]  ; + = free city
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [0 1 :shipyard] [{:type :destroyer :hits 1}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in @atoms/game-map [0 1])]
        ;; Should not repair in free city
        (should= [{:type :destroyer :hits 1}] (:shipyard city)))))

  (it "repairs computer ships in computer cities"
    (let [game-map (tu/build-test-map ["~X~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [0 1 :shipyard] [{:type :destroyer :hits 1}])
      (game-loop/repair-damaged-ships)
      (let [city (get-in @atoms/game-map [0 1])]
        ;; Computer ships should be repaired at computer cities
        (should= [{:type :destroyer :hits 2}] (:shipyard city)))))

  (it "does not launch ship if city already has contents"
    (let [game-map (tu/build-test-map ["~O~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [0 1 :shipyard]
             [{:type :destroyer :hits 2}   ; will be repaired to 3 (full)
              {:type :battleship :hits 5}]) ; will be repaired to 6
      ;; Put an existing ship on the city
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :submarine :owner :player :hits 2 :mode :sentry})
      (game-loop/repair-damaged-ships)
      (let [city (get-in @atoms/game-map [0 1])]
        ;; Destroyer should stay in shipyard since city is occupied
        ;; Battleship stays too since not fully repaired
        (should= [{:type :destroyer :hits 3}
                  {:type :battleship :hits 6}]
                 (:shipyard city))
        ;; Original ship still there
        (should= :submarine (:type (:contents city)))))))

(describe "launch-ship-from-shipyard"
  (before
    (tu/reset-all-atoms!))

  (it "removes ship from shipyard and places on map"
    (let [game-map (tu/build-test-map ["~O~"])]
      (reset! atoms/game-map game-map)
      (swap! atoms/game-map assoc-in [0 1 :shipyard] [{:type :destroyer :hits 3}])
      (container-ops/launch-ship-from-shipyard [0 1] 0)
      (let [city (get-in @atoms/game-map [0 1])
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
      (swap! atoms/game-map assoc-in [0 1 :shipyard] [{:type :battleship :hits 10}])
      (container-ops/launch-ship-from-shipyard [0 1] 0)
      (let [ship (get-in @atoms/game-map [0 1 :contents])]
        (should= :computer (:owner ship))))))

(run-specs)
