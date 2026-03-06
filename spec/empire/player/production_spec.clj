(ns empire.player.production-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.player.production :as production]
            [empire.computer.production :as computer-production]
            [empire.config.core :as config]
            [empire.test.utils :refer [build-test-map get-test-city reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "update-production"
  (around [it]
    (reset-all-atoms!)
    (test-utils/set-test-state! :production {})
    (set-test-world! (build-test-map ["~O"
                                      "O#"]))
    (it))

  (it "decrements remaining-rounds when not complete"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O2"))]
      (test-utils/update-test-state! :production assoc city-coords {:item :army :remaining-rounds 3})
      (production/update-production)
      (should= {:item :army :remaining-rounds 2} ((test-utils/read-test-state :production) city-coords))
      (should= {:type :sea} (get-in (test-utils/read-test-state :game-map) [0 0]))
      (should= {:type :city :city-status :player} (get-in (test-utils/read-test-state :game-map) [0 1]))))

  (it "places item on map and resets production when remaining-rounds reaches 0"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O2"))]
      (test-utils/update-test-state! :production assoc city-coords {:item :army :remaining-rounds 1})
      (production/update-production)
      (should= {:item :army :remaining-rounds 5} ((test-utils/read-test-state :production) city-coords)) ; item-cost :army = 5
      (should= {:type :army :hits 1 :mode :awake :owner :player} (:contents (get-in (test-utils/read-test-state :game-map) city-coords))) ; item-hits :army = 1
      (should= {:type :city :city-status :player :contents {:type :army :hits 1 :mode :awake :owner :player}} (get-in (test-utils/read-test-state :game-map) city-coords))))

  (it "handles multiple cities correctly"
    (let [city1-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))
          city2-coords (:pos (get-test-city (test-utils/game-map-atom) "O2"))]
      (test-utils/update-test-state! :production assoc city2-coords {:item :army :remaining-rounds 2})
      (test-utils/update-test-state! :production assoc city1-coords {:item :fighter :remaining-rounds 1})
      (production/update-production)
      (should= {:item :army :remaining-rounds 1} ((test-utils/read-test-state :production) city2-coords))
      (should= {:item :fighter :remaining-rounds 10} ((test-utils/read-test-state :production) city1-coords)) ; item-cost :fighter = 10
      (should= {:type :fighter :hits 1 :mode :awake :owner :player :fuel 32} (:contents (get-in (test-utils/read-test-state :game-map) city1-coords)))))

  (it "ignores cities with :no-production"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O2"))]
      (test-utils/update-test-state! :production assoc city-coords :no-production)
      (production/update-production)
      (should= :no-production ((test-utils/read-test-state :production) city-coords))))

  (it "does nothing when no production"
    (production/update-production)
    (should= {} (test-utils/read-test-state :production))
    (should= [[{:type :sea} {:type :city :city-status :player}]
              [{:type :city :city-status :player} {:type :land}]] (test-utils/read-test-state :game-map)))

  (it "does not decrement remaining-rounds if city has a unit"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O2"))]
      (test-utils/update-test-state! :production assoc city-coords {:item :army :remaining-rounds 3})
      (update-test-world! assoc-in (conj city-coords :contents) {:type :fighter :hits 1}) ; Put a unit in the city
      (production/update-production)
      (should= {:item :army :remaining-rounds 3} ((test-utils/read-test-state :production) city-coords)) ; Should not decrement
      (should= {:type :fighter :hits 1} (:contents (get-in (test-utils/read-test-state :game-map) city-coords)))))

  (it "creates army with marching orders when city has them"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O2"))]
      (update-test-world! assoc-in (conj city-coords :marching-orders) [5 5])
      (test-utils/update-test-state! :production assoc city-coords {:item :army :remaining-rounds 1})
      (production/update-production)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) city-coords))]
        (should= :army (:type unit))
        (should= :moving (:mode unit))
        (should= [5 5] (:target unit)))))

  (it "creates army without marching orders when city has none"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O2"))]
      (test-utils/update-test-state! :production assoc city-coords {:item :army :remaining-rounds 1})
      (production/update-production)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) city-coords))]
        (should= :army (:type unit))
        (should= :awake (:mode unit))
        (should-be-nil (:target unit)))))

  (it "creates fighter with flight path when city has one"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (update-test-world! assoc-in (conj city-coords :flight-path) [10 10])
      (test-utils/update-test-state! :production assoc city-coords {:item :fighter :remaining-rounds 1})
      (production/update-production)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) city-coords))]
        (should= :fighter (:type unit))
        (should= :moving (:mode unit))
        (should= [10 10] (:target unit))
        (should= config/fighter-fuel (:fuel unit)))))

  (it "creates fighter without flight path when city has none"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (test-utils/update-test-state! :production assoc city-coords {:item :fighter :remaining-rounds 1})
      (production/update-production)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) city-coords))]
        (should= :fighter (:type unit))
        (should= :awake (:mode unit))
        (should-be-nil (:target unit))
        (should= config/fighter-fuel (:fuel unit)))))

  (it "ignores marching orders for non-army units"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O2"))]
      (update-test-world! assoc-in (conj city-coords :marching-orders) [5 5])
      (test-utils/update-test-state! :production assoc city-coords {:item :transport :remaining-rounds 1})
      (production/update-production)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) city-coords))]
        (should= :transport (:type unit))
        (should= :awake (:mode unit))
        (should-be-nil (:target unit)))))

  (it "ignores flight path for non-fighter units"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O2"))]
      (update-test-world! assoc-in (conj city-coords :flight-path) [10 10])
      (test-utils/update-test-state! :production assoc city-coords {:item :destroyer :remaining-rounds 1})
      (production/update-production)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) city-coords))]
        (should= :destroyer (:type unit))
        (should= :awake (:mode unit))
        (should-be-nil (:target unit)))))

  (it "creates unit owned by computer for computer city"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O2"))]
      (update-test-world! assoc-in (conj city-coords :city-status) :computer)
      (test-utils/update-test-state! :production assoc city-coords {:item :army :remaining-rounds 1})
      (production/update-production)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) city-coords))]
        (should= :army (:type unit))
        (should= :computer (:owner unit)))))

  (it "creates army in explore mode when city has lookaround marching orders"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O2"))]
      (update-test-world! assoc-in (conj city-coords :marching-orders) :lookaround)
      (test-utils/update-test-state! :production assoc city-coords {:item :army :remaining-rounds 1})
      (production/update-production)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) city-coords))]
        (should= :army (:type unit))
        (should= :explore (:mode unit))
        (should= 50 (:explore-steps unit))
        (should-be-nil (:target unit)))))

  (it "ignores lookaround marching orders for non-army units"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O2"))]
      (update-test-world! assoc-in (conj city-coords :marching-orders) :lookaround)
      (test-utils/update-test-state! :production assoc city-coords {:item :transport :remaining-rounds 1})
      (production/update-production)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) city-coords))]
        (should= :transport (:type unit))
        (should= :awake (:mode unit))
        (should-be-nil (:explore-steps unit)))))

  (it "assigns country-id to fighter when city has country-id"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (update-test-world! assoc-in (conj city-coords :city-status) :computer)
      (update-test-world! assoc-in (conj city-coords :country-id) 7)
      (test-utils/update-test-state! :production assoc city-coords {:item :fighter :remaining-rounds 1})
      (production/update-production)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) city-coords))]
        (should= :fighter (:type unit))
        (should= 7 (:country-id unit)))))

  (it "stamps country-id on adjacent land when computer army spawns"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O2"))]
      (update-test-world! assoc-in (conj city-coords :city-status) :computer)
      (update-test-world! assoc-in (conj city-coords :country-id) 3)
      (test-utils/update-test-state! :production assoc city-coords {:item :army :remaining-rounds 1})
      (with-redefs [rand (constantly 0.9)]
        (production/update-production))
      (let [land-cell (get-in (test-utils/read-test-state :game-map) [1 1])]
        (should= 3 (:country-id land-cell)))))

  (it "does not assign country-id to fighter when city lacks country-id"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (update-test-world! assoc-in (conj city-coords :city-status) :computer)
      (test-utils/update-test-state! :production assoc city-coords {:item :fighter :remaining-rounds 1})
      (production/update-production)
      (let [unit (:contents (get-in (test-utils/read-test-state :game-map) city-coords))]
        (should= :fighter (:type unit))
        (should-be-nil (:country-id unit))))))

(describe "coast-walk stamping"
  (before
    (reset-all-atoms!)
    (test-utils/set-test-state! :production {}))

  (it "first computer army gets clockwise coast-walk"
    ;; Need coastal land cells unexplored on computer-map for coast-walk to trigger
    (set-test-world! [[{:type :city :city-status :computer :country-id 1}
                       {:type :sea}]
                      [{:type :land :country-id 1}
                       {:type :sea}]])
    ;; computer-map is {} so land at [1 0] is unexplored → coastal cells not explored
    (computer-production/rebuild-country-stats!)
    (test-utils/update-test-state! :production assoc [0 0] {:item :army :remaining-rounds 1})
    (production/update-production)
    (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (should= :coast-walk (:mode unit))
      (should= :clockwise (:coast-direction unit))
      (should= [0 0] (:coast-start unit))
      (should= [[0 0]] (:coast-visited unit))))

  (it "second computer army gets counter-clockwise coast-walk"
    (test-utils/set-test-state! :coast-walkers-produced {1 1})
    (set-test-world! [[{:type :city :city-status :computer :country-id 1}
                       {:type :sea}]
                      [{:type :land :country-id 1}
                       {:type :sea}]])
    (computer-production/rebuild-country-stats!)
    (test-utils/update-test-state! :production assoc [0 0] {:item :army :remaining-rounds 1})
    (production/update-production)
    (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (should= :coast-walk (:mode unit))
      (should= :counter-clockwise (:coast-direction unit))))

  (it "no coast-walk when all coastal cells explored"
    (set-test-world! [[{:type :city :city-status :computer :country-id 1}
                       {:type :sea}]
                      [{:type :land :country-id 1}
                       {:type :sea}]])
    ;; Make all coastal cells visible on computer-map
    (set-test-computer-map! [[{:type :city} {:type :sea}]
                                 [{:type :land} {:type :sea}]])
    (test-utils/update-test-state! :production assoc [0 0] {:item :army :remaining-rounds 1})
    (with-redefs [rand (constantly 0.9)]
      (production/update-production))
    (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (should= :awake (:mode unit))
      (should-be-nil (:coast-direction unit))))

  (it "player army does not get coast-walk"
    (set-test-world! [[{:type :city :city-status :player :country-id 1}
                       {:type :sea}]
                      [{:type :land :country-id 1}
                       {:type :sea}]])
    (test-utils/update-test-state! :production assoc [0 0] {:item :army :remaining-rounds 1})
    (production/update-production)
    (let [unit (get-in (test-utils/read-test-state :game-map) [0 0 :contents])]
      (should= :awake (:mode unit))
      (should-be-nil (:coast-direction unit)))))

(describe "set-city-production"
  (before
    (reset-all-atoms!)
    (test-utils/set-test-state! :production {}))

  (it "sets production for a city"
    (production/set-city-production [1 2] :army)
    (should= {:item :army :remaining-rounds (config/item-cost :army)} ((test-utils/read-test-state :production) [1 2])))

  (it "sets production for fighter with correct cost"
    (production/set-city-production [3 4] :fighter)
    (should= {:item :fighter :remaining-rounds (config/item-cost :fighter)} ((test-utils/read-test-state :production) [3 4]))))
