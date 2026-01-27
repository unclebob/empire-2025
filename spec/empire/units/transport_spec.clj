(ns empire.units.transport-spec
  (:require [speclj.core :refer :all]
            [empire.units.transport :as transport]))

(describe "transport unit module"
  (describe "configuration constants"
    (it "has capacity of 6"
      (should= 6 transport/capacity)))

  (describe "can-move-to?"
    (it "returns true for sea"
      (should (transport/can-move-to? {:type :sea})))

    (it "returns false for land"
      (should-not (transport/can-move-to? {:type :land})))

    (it "returns false for city"
      (should-not (transport/can-move-to? {:type :city})))

    (it "returns false for nil"
      (should-not (transport/can-move-to? nil))))

  (describe "needs-attention?"
    (it "returns true when awake"
      (should (transport/needs-attention? {:type :transport :mode :awake})))

    (it "returns true when has awake armies"
      (should (transport/needs-attention? {:type :transport :mode :sentry :awake-armies 2})))

    (it "returns false when sentry with no awake armies"
      (should-not (transport/needs-attention? {:type :transport :mode :sentry :awake-armies 0})))

    (it "returns false when moving"
      (should-not (transport/needs-attention? {:type :transport :mode :moving :awake-armies 0}))))

  (describe "full?"
    (it "returns true at capacity"
      (should (transport/full? {:army-count 6})))

    (it "returns true above capacity"
      (should (transport/full? {:army-count 7})))

    (it "returns false below capacity"
      (should-not (transport/full? {:army-count 3})))

    (it "returns false for empty transport"
      (should-not (transport/full? {:army-count 0}))))

  (describe "has-armies?"
    (it "returns true when has armies"
      (should (transport/has-armies? {:army-count 3})))

    (it "returns false when empty"
      (should-not (transport/has-armies? {:army-count 0}))))

  (describe "has-awake-armies?"
    (it "returns true when has awake armies"
      (should (transport/has-awake-armies? {:awake-armies 2})))

    (it "returns false when no awake armies"
      (should-not (transport/has-awake-armies? {:awake-armies 0}))))

  (describe "add-army"
    (it "increments army count"
      (should= 4 (:army-count (transport/add-army {:army-count 3}))))

    (it "handles nil army count"
      (should= 1 (:army-count (transport/add-army {})))))

  (describe "remove-army"
    (it "decrements army count"
      (should= 2 (:army-count (transport/remove-army {:army-count 3})))))

  (describe "wake-armies"
    (it "sets awake-armies to army-count"
      (let [result (transport/wake-armies {:army-count 4 :awake-armies 0})]
        (should= 4 (:awake-armies result)))))

  (describe "sleep-armies"
    (it "sets awake-armies to 0"
      (let [result (transport/sleep-armies {:army-count 4 :awake-armies 3})]
        (should= 0 (:awake-armies result)))))

  (describe "remove-awake-army"
    (it "decrements both army-count and awake-armies"
      (let [result (transport/remove-awake-army {:army-count 3 :awake-armies 2})]
        (should= 2 (:army-count result))
        (should= 1 (:awake-armies result))))))
