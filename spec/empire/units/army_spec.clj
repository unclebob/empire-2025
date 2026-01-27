(ns empire.units.army-spec
  (:require [speclj.core :refer :all]
            [empire.units.army :as army]))

(describe "army unit module"
  (describe "can-move-to?"
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

  (describe "needs-attention?"
    (it "returns true when awake"
      (should (army/needs-attention? {:type :army :mode :awake})))

    (it "returns false when sentry"
      (should-not (army/needs-attention? {:type :army :mode :sentry})))

    (it "returns false when moving"
      (should-not (army/needs-attention? {:type :army :mode :moving})))

    (it "returns false when exploring"
      (should-not (army/needs-attention? {:type :army :mode :explore})))))
