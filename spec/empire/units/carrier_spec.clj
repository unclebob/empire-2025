(ns empire.units.carrier-spec
  (:require [speclj.core :refer :all]
            [empire.units.carrier :as carrier]))

(describe "carrier unit module"
  (describe "configuration constants"
    (it "has capacity of 8"
      (should= 8 carrier/capacity)))

  (describe "can-move-to?"
    (it "returns true for sea"
      (should (carrier/can-move-to? {:type :sea})))

    (it "returns false for land"
      (should-not (carrier/can-move-to? {:type :land})))

    (it "returns false for city"
      (should-not (carrier/can-move-to? {:type :city})))

    (it "returns false for nil"
      (should-not (carrier/can-move-to? nil))))

  (describe "needs-attention?"
    (it "returns true when awake"
      (should (carrier/needs-attention? {:type :carrier :mode :awake})))

    (it "returns true when has awake fighters"
      (should (carrier/needs-attention? {:type :carrier :mode :sentry :awake-fighters 2})))

    (it "returns false when sentry with no awake fighters"
      (should-not (carrier/needs-attention? {:type :carrier :mode :sentry :awake-fighters 0})))

    (it "returns false when moving"
      (should-not (carrier/needs-attention? {:type :carrier :mode :moving :awake-fighters 0}))))

  (describe "full?"
    (it "returns true at capacity"
      (should (carrier/full? {:fighter-count 8})))

    (it "returns true above capacity"
      (should (carrier/full? {:fighter-count 9})))

    (it "returns false below capacity"
      (should-not (carrier/full? {:fighter-count 5})))

    (it "returns false for empty carrier"
      (should-not (carrier/full? {:fighter-count 0}))))

  (describe "has-fighters?"
    (it "returns true when has fighters"
      (should (carrier/has-fighters? {:fighter-count 4})))

    (it "returns false when empty"
      (should-not (carrier/has-fighters? {:fighter-count 0}))))

  (describe "has-awake-fighters?"
    (it "returns true when has awake fighters"
      (should (carrier/has-awake-fighters? {:awake-fighters 3})))

    (it "returns false when no awake fighters"
      (should-not (carrier/has-awake-fighters? {:awake-fighters 0}))))

  (describe "add-fighter"
    (it "increments fighter count"
      (should= 5 (:fighter-count (carrier/add-fighter {:fighter-count 4}))))

    (it "handles nil fighter count"
      (should= 1 (:fighter-count (carrier/add-fighter {})))))

  (describe "remove-fighter"
    (it "decrements fighter count"
      (should= 3 (:fighter-count (carrier/remove-fighter {:fighter-count 4})))))

  (describe "wake-fighters"
    (it "sets awake-fighters to fighter-count"
      (let [result (carrier/wake-fighters {:fighter-count 6 :awake-fighters 0})]
        (should= 6 (:awake-fighters result)))))

  (describe "sleep-fighters"
    (it "sets awake-fighters to 0"
      (let [result (carrier/sleep-fighters {:fighter-count 6 :awake-fighters 4})]
        (should= 0 (:awake-fighters result)))))

  (describe "remove-awake-fighter"
    (it "decrements both fighter-count and awake-fighters"
      (let [result (carrier/remove-awake-fighter {:fighter-count 5 :awake-fighters 3})]
        (should= 4 (:fighter-count result))
        (should= 2 (:awake-fighters result))))))
