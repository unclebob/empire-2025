(ns empire.units.battleship-spec
  (:require [speclj.core :refer :all]
            [empire.units.battleship :as battleship]))

(describe "battleship unit module"
  (describe "can-move-to?"
    (it "returns true for sea"
      (should (battleship/can-move-to? {:type :sea})))

    (it "returns false for land"
      (should-not (battleship/can-move-to? {:type :land})))

    (it "returns false for city"
      (should-not (battleship/can-move-to? {:type :city})))

    (it "returns false for nil"
      (should-not (battleship/can-move-to? nil))))

  (describe "needs-attention?"
    (it "returns true when awake"
      (should (battleship/needs-attention? {:type :battleship :mode :awake})))

    (it "returns false when sentry"
      (should-not (battleship/needs-attention? {:type :battleship :mode :sentry})))

    (it "returns false when moving"
      (should-not (battleship/needs-attention? {:type :battleship :mode :moving})))

    (it "returns false when exploring"
      (should-not (battleship/needs-attention? {:type :battleship :mode :explore})))))
