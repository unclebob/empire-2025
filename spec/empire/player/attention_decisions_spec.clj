(ns empire.player.attention-decisions-spec
  (:require [empire.player.attention-decisions :as decisions]
            [speclj.core :refer :all]))

(describe "player-map-cell-needs-attention?"
  (it "returns true for awake player unit"
    (should (decisions/player-map-cell-needs-attention?
             {:contents {:type :army :owner :player :mode :awake}}
             nil)))

  (it "returns true for player city without production"
    (should (decisions/player-map-cell-needs-attention?
             {:type :city :city-status :player}
             nil)))

  (it "returns false for targeted satellite"
    (should-not (decisions/player-map-cell-needs-attention?
                 {:contents {:type :satellite :owner :player :mode :awake :target [1 1]}}
                 nil))))

(describe "world-item-needs-attention?"
  (it "city with fighter-count > 0 and awake-fighters > 0 needs attention"
    (let [cell {:type :city :city-status :player :fighter-count 2 :awake-fighters 2}]
      (should (decisions/player-map-cell-needs-attention? cell {:item :army}))))

  (it "city with stored fighters but no awake fighters does not need airport attention"
    (let [cell {:type :city :city-status :player :fighter-count 27 :awake-fighters 0}]
      (should-not (decisions/player-map-cell-needs-attention? cell {:item :army}))
      (should-not (decisions/world-item-needs-attention? cell {:item :army}))))

  (it "city with stale awake-fighters but no stored fighters does not need airport attention"
    (let [cell {:type :city :city-status :player :fighter-count 0 :awake-fighters 1}]
      (should-not (decisions/player-map-cell-needs-attention? cell {:item :army}))
      (should-not (decisions/world-item-needs-attention? cell {:item :army}))))

  (it "returns false for awake computer unit in player city"
    (should-not (decisions/world-item-needs-attention?
                 {:type :city :city-status :player :contents {:type :fighter :owner :computer :mode :awake}}
                 :army))))

(describe "attention-coords"
  (it "collects matching coordinates"
    (should= [[0 0] [1 0]]
             (vec (decisions/attention-coords
                   [[{:contents {:type :army :owner :player :mode :awake}}
                     {:type :land}]
                    [{:type :city :city-status :player}
                     {:type :city :city-status :player}]]
                   {[1 1] {:item :army}})))))

(describe "additional attention decisions"
  (it "detects when the clicked city is the next attention item"
    (should (decisions/city-needs-attention?
             {:type :city :city-status :player}
             [1 1]
             [[1 1] [2 2]])))

  (it "builds attention messages for active units"
    (should-contain "destroyer needs attention"
                    (decisions/attention-message
                     {:world [[{:type :sea
                                :contents {:type :destroyer :owner :player :mode :awake :hits 3}}]]
                      :coords [0 0]
                      :unit {:type :destroyer :owner :player :mode :awake :hits 3}
                      :active-unit {:type :destroyer :owner :player :mode :awake :hits 3}}))))
