(ns empire.production-spec
  (:require [speclj.core :refer :all]
            [empire.production :as production]
            [empire.atoms :as atoms]))

(describe "update-production"
  (around [it]
    (reset! atoms/production {})
    (reset! atoms/game-map [[{:type :sea} {:type :city :owner :player}]
                            [{:type :land} {:type :land}]])
    (it))

  (it "decrements remaining-rounds when not complete"
    (swap! atoms/production assoc [1 0] {:item :army :remaining-rounds 3})
    (production/update-production)
    (should= {:item :army :remaining-rounds 2} (@atoms/production [1 0]))
    (should= {:type :sea} (get-in @atoms/game-map [0 0]))
    (should= {:type :city :owner :player} (get-in @atoms/game-map [0 1])))

  (it "places item on map and resets production when remaining-rounds reaches 0"
    (swap! atoms/production assoc [1 0] {:item :army :remaining-rounds 1})
    (production/update-production)
    (should= {:item :army :remaining-rounds 5} (@atoms/production [1 0])) ; item-cost :army = 5
    (should= {:type :city :owner :player :contents {:type :army :hits 1}} (get-in @atoms/game-map [0 1])))

  (it "handles multiple cities correctly"
    (swap! atoms/production assoc [1 0] {:item :army :remaining-rounds 2})
    (swap! atoms/production assoc [0 1] {:item :fighter :remaining-rounds 1})
    (production/update-production)
    (should= {:item :army :remaining-rounds 1} (@atoms/production [1 0]))
    (should= {:item :fighter :remaining-rounds 10} (@atoms/production [0 1])) ; item-cost :fighter = 10
    (should= {:type :fighter :hits 1} (:contents (get-in @atoms/game-map [1 0]))))

  (it "does nothing when no production"
    (production/update-production)
    (should= {} @atoms/production)
    (should= [[{:type :sea} {:type :city :owner :player}]
              [{:type :land} {:type :land}]] @atoms/game-map)))