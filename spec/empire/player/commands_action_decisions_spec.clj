(ns empire.player.commands-action-decisions-spec
  (:require [empire.player.commands-action-decisions :as sut]
            [speclj.core :refer :all]))

(describe "player command action decisions"
  (it "classifies city production and movement context actions"
    (should= {:action :reject-production :item :destroyer}
             (sut/city-production-action {:naval? true :coastal? false :item :destroyer}))
    (should= {:action :set-production :item :army}
             (sut/city-production-action {:naval? false :coastal? false :item :army}))
    (should= :launch-airport-fighter (sut/movement-context-action :airport-fighter))
    (should= :standard-unit-movement (sut/movement-context-action :standard-unit)))

  (it "returns a fighter skip fuel action"
    (should= {:action :skip-and-burn-fuel
              :fuel 24
              :reason "Skipping this round. Fuel: 24"}
             (sut/space-key-action {:type :fighter :fuel 32})))

  (it "returns a reject action for an ineligible coastline unit"
    (should= :reject
             (:action (sut/look-around-action [[{:type :sea}]]
                                              [0 0]
                                              {:type :transport}))))

  (it "returns a conquest click action for an adjacent hostile city"
    (should= {:action :attempt-conquest
              :target [1 1]}
             (sut/click-action [[{:type :land} {:type :land}]
                                [{:type :land} {:type :city :city-status :computer}]]
                               [0 0]
                               [1 1]
                               :normal
                               {:type :army}))))
