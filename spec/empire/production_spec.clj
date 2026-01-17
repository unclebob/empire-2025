(ns empire.production-spec
  (:require [speclj.core :refer :all]
            [empire.production :as production]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.test-utils :refer [build-test-map]]))

(describe "update-production"
  (around [it]
    (reset! atoms/production {})
    (reset! atoms/game-map @(build-test-map ["sO"
                                             "OL"]))
    (it))

  (it "decrements remaining-rounds when not complete"
    (swap! atoms/production assoc [1 0] {:item :army :remaining-rounds 3})
    (production/update-production)
    (should= {:item :army :remaining-rounds 2} (@atoms/production [1 0]))
    (should= {:type :sea} (get-in @atoms/game-map [0 0]))
    (should= {:type :city :city-status :player} (get-in @atoms/game-map [0 1])))

  (it "places item on map and resets production when remaining-rounds reaches 0"
    (swap! atoms/production assoc [1 0] {:item :army :remaining-rounds 1})
    (production/update-production)
    (should= {:item :army :remaining-rounds 5} (@atoms/production [1 0])) ; item-cost :army = 5
    (should= {:type :army :hits 1 :mode :awake :owner :player} (:contents (get-in @atoms/game-map [1 0]))) ; item-hits :army = 1
    (should= {:type :city :city-status :player :contents {:type :army :hits 1 :mode :awake :owner :player}} (get-in @atoms/game-map [1 0])))

  (it "handles multiple cities correctly"
    (swap! atoms/production assoc [1 0] {:item :army :remaining-rounds 2})
    (swap! atoms/production assoc [0 1] {:item :fighter :remaining-rounds 1})
    (production/update-production)
    (should= {:item :army :remaining-rounds 1} (@atoms/production [1 0]))
    (should= {:item :fighter :remaining-rounds 10} (@atoms/production [0 1])) ; item-cost :fighter = 10
    (should= {:type :fighter :hits 1 :mode :awake :owner :player :fuel 32} (:contents (get-in @atoms/game-map [0 1]))))

  (it "ignores cities with :no-production"
    (swap! atoms/production assoc [1 0] :no-production)
    (production/update-production)
    (should= :no-production (@atoms/production [1 0])))

  (it "does nothing when no production"
    (production/update-production)
    (should= {} @atoms/production)
    (should= [[{:type :sea} {:type :city :city-status :player}]
              [{:type :city :city-status :player} {:type :land}]] @atoms/game-map))

  (it "does not decrement remaining-rounds if city has a unit"
    (swap! atoms/production assoc [1 0] {:item :army :remaining-rounds 3})
    (swap! atoms/game-map assoc-in [1 0 :contents] {:type :fighter :hits 1}) ; Put a unit in the city
    (production/update-production)
    (should= {:item :army :remaining-rounds 3} (@atoms/production [1 0])) ; Should not decrement
    (should= {:type :fighter :hits 1} (:contents (get-in @atoms/game-map [1 0]))))

  (it "creates army with marching orders when city has them"
    (swap! atoms/game-map assoc-in [1 0 :marching-orders] [5 5])
    (swap! atoms/production assoc [1 0] {:item :army :remaining-rounds 1})
    (production/update-production)
    (let [unit (:contents (get-in @atoms/game-map [1 0]))]
      (should= :army (:type unit))
      (should= :moving (:mode unit))
      (should= [5 5] (:target unit))))

  (it "creates army without marching orders when city has none"
    (swap! atoms/production assoc [1 0] {:item :army :remaining-rounds 1})
    (production/update-production)
    (let [unit (:contents (get-in @atoms/game-map [1 0]))]
      (should= :army (:type unit))
      (should= :awake (:mode unit))
      (should-be-nil (:target unit))))

  (it "creates fighter with flight path when city has one"
    (swap! atoms/game-map assoc-in [0 1 :flight-path] [10 10])
    (swap! atoms/production assoc [0 1] {:item :fighter :remaining-rounds 1})
    (production/update-production)
    (let [unit (:contents (get-in @atoms/game-map [0 1]))]
      (should= :fighter (:type unit))
      (should= :moving (:mode unit))
      (should= [10 10] (:target unit))
      (should= config/fighter-fuel (:fuel unit))))

  (it "creates fighter without flight path when city has none"
    (swap! atoms/production assoc [0 1] {:item :fighter :remaining-rounds 1})
    (production/update-production)
    (let [unit (:contents (get-in @atoms/game-map [0 1]))]
      (should= :fighter (:type unit))
      (should= :awake (:mode unit))
      (should-be-nil (:target unit))
      (should= config/fighter-fuel (:fuel unit))))

  (it "ignores marching orders for non-army units"
    (swap! atoms/game-map assoc-in [1 0 :marching-orders] [5 5])
    (swap! atoms/production assoc [1 0] {:item :transport :remaining-rounds 1})
    (production/update-production)
    (let [unit (:contents (get-in @atoms/game-map [1 0]))]
      (should= :transport (:type unit))
      (should= :awake (:mode unit))
      (should-be-nil (:target unit))))

  (it "ignores flight path for non-fighter units"
    (swap! atoms/game-map assoc-in [1 0 :flight-path] [10 10])
    (swap! atoms/production assoc [1 0] {:item :destroyer :remaining-rounds 1})
    (production/update-production)
    (let [unit (:contents (get-in @atoms/game-map [1 0]))]
      (should= :destroyer (:type unit))
      (should= :awake (:mode unit))
      (should-be-nil (:target unit))))

  (it "creates unit owned by computer for computer city"
    (swap! atoms/game-map assoc-in [1 0 :city-status] :computer)
    (swap! atoms/production assoc [1 0] {:item :army :remaining-rounds 1})
    (production/update-production)
    (let [unit (:contents (get-in @atoms/game-map [1 0]))]
      (should= :army (:type unit))
      (should= :computer (:owner unit))))

  (it "creates army in explore mode when city has lookaround marching orders"
    (swap! atoms/game-map assoc-in [1 0 :marching-orders] :lookaround)
    (swap! atoms/production assoc [1 0] {:item :army :remaining-rounds 1})
    (production/update-production)
    (let [unit (:contents (get-in @atoms/game-map [1 0]))]
      (should= :army (:type unit))
      (should= :explore (:mode unit))
      (should= 50 (:explore-steps unit))
      (should-be-nil (:target unit))))

  (it "ignores lookaround marching orders for non-army units"
    (swap! atoms/game-map assoc-in [1 0 :marching-orders] :lookaround)
    (swap! atoms/production assoc [1 0] {:item :transport :remaining-rounds 1})
    (production/update-production)
    (let [unit (:contents (get-in @atoms/game-map [1 0]))]
      (should= :transport (:type unit))
      (should= :awake (:mode unit))
      (should-be-nil (:explore-steps unit)))))

(describe "set-city-production"
  (before
    (reset! atoms/production {}))

  (it "sets production for a city"
    (production/set-city-production [1 2] :army)
    (should= {:item :army :remaining-rounds (config/item-cost :army)} (@atoms/production [1 2])))

  (it "sets production for fighter with correct cost"
    (production/set-city-production [3 4] :fighter)
    (should= {:item :fighter :remaining-rounds (config/item-cost :fighter)} (@atoms/production [3 4]))))