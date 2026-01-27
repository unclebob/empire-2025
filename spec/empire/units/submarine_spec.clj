(ns empire.units.submarine-spec
  (:require [speclj.core :refer :all]
            [empire.units.submarine :as submarine]))

(describe "submarine unit module"
  (describe "can-move-to?"
    (it "returns true for sea"
      (should (submarine/can-move-to? {:type :sea})))

    (it "returns false for land"
      (should-not (submarine/can-move-to? {:type :land})))

    (it "returns false for city"
      (should-not (submarine/can-move-to? {:type :city})))

    (it "returns false for nil"
      (should-not (submarine/can-move-to? nil))))

  (describe "needs-attention?"
    (it "returns true when awake"
      (should (submarine/needs-attention? {:type :submarine :mode :awake})))

    (it "returns false when sentry"
      (should-not (submarine/needs-attention? {:type :submarine :mode :sentry})))

    (it "returns false when moving"
      (should-not (submarine/needs-attention? {:type :submarine :mode :moving})))

    (it "returns false when exploring"
      (should-not (submarine/needs-attention? {:type :submarine :mode :explore})))))
