(ns empire.units.patrol-boat-spec
  (:require [speclj.core :refer :all]
            [empire.units.patrol-boat :as patrol-boat]))

(describe "patrol boat unit module"
  (describe "can-move-to?"
    (it "returns true for sea"
      (should (patrol-boat/can-move-to? {:type :sea})))

    (it "returns false for land"
      (should-not (patrol-boat/can-move-to? {:type :land})))

    (it "returns false for city"
      (should-not (patrol-boat/can-move-to? {:type :city})))

    (it "returns false for nil"
      (should-not (patrol-boat/can-move-to? nil))))

  (describe "needs-attention?"
    (it "returns true when awake"
      (should (patrol-boat/needs-attention? {:type :patrol-boat :mode :awake})))

    (it "returns false when sentry"
      (should-not (patrol-boat/needs-attention? {:type :patrol-boat :mode :sentry})))

    (it "returns false when moving"
      (should-not (patrol-boat/needs-attention? {:type :patrol-boat :mode :moving})))

    (it "returns false when exploring"
      (should-not (patrol-boat/needs-attention? {:type :patrol-boat :mode :explore})))))
