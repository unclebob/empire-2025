(ns empire.units.destroyer-spec
  (:require [speclj.core :refer :all]
            [empire.units.destroyer :as destroyer]))

(describe "destroyer unit module"
  (describe "can-move-to?"
    (it "returns true for sea"
      (should (destroyer/can-move-to? {:type :sea})))

    (it "returns false for land"
      (should-not (destroyer/can-move-to? {:type :land})))

    (it "returns false for city"
      (should-not (destroyer/can-move-to? {:type :city})))

    (it "returns false for nil"
      (should-not (destroyer/can-move-to? nil))))

  (describe "needs-attention?"
    (it "returns true when awake"
      (should (destroyer/needs-attention? {:type :destroyer :mode :awake})))

    (it "returns false when sentry"
      (should-not (destroyer/needs-attention? {:type :destroyer :mode :sentry})))

    (it "returns false when moving"
      (should-not (destroyer/needs-attention? {:type :destroyer :mode :moving})))

    (it "returns false when exploring"
      (should-not (destroyer/needs-attention? {:type :destroyer :mode :explore})))))
