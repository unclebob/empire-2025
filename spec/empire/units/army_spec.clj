(ns empire.units.army-spec
  (:require [speclj.core :refer :all]
            [empire.units.army :as army]))

(describe "army unit module"
  (context "configuration"
    (it "has speed of 1"
      (should= 1 army/speed))

    (it "has cost of 5"
      (should= 5 army/cost))

    (it "has 1 hit point"
      (should= 1 army/hits))

    (it "has strength of 1"
      (should= 1 army/strength))

    (it "displays as A"
      (should= "A" army/display-char))

    (it "has visibility radius of 1"
      (should= 1 army/visibility-radius)))

  (context "initial-state"
    (it "returns empty map"
      (should= {} (army/initial-state))))

  (context "can-move-to?"
    (it "returns true for land"
      (should (army/can-move-to? {:type :land})))

    (it "returns false for sea"
      (should-not (army/can-move-to? {:type :sea})))

    (it "returns true for enemy city"
      (should (army/can-move-to? {:type :city :city-status :computer})))

    (it "returns true for free city"
      (should (army/can-move-to? {:type :city :city-status :free})))

    (it "returns false for player city"
      (should-not (army/can-move-to? {:type :city :city-status :player}))))

  (context "needs-attention?"
    (it "returns true when awake"
      (should (army/needs-attention? {:type :army :mode :awake})))

    (it "returns false when sentry"
      (should-not (army/needs-attention? {:type :army :mode :sentry})))

    (it "returns false when moving"
      (should-not (army/needs-attention? {:type :army :mode :moving})))

    (it "returns false when exploring"
      (should-not (army/needs-attention? {:type :army :mode :explore})))))
