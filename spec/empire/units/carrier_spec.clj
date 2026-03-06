(ns empire.config.units.carrier-spec
  (:require [speclj.core :refer :all]
            [empire.config.units.config :as units-config]
            [empire.config.units.carrier :as carrier]))

(describe "carrier unit module"
  (context "configuration"
    (it "has speed of 2"
      (should= 2 units-config/carrier-speed))

    (it "has cost of 30"
      (should= 30 units-config/carrier-cost))

    (it "has 8 hit points"
      (should= 8 units-config/carrier-hits))

    (it "displays as C"
      (should= "C" units-config/carrier-display-char))

    (it "has capacity of 8"
      (should= 8 units-config/carrier-capacity))

    (it "has visibility radius of 1"
      (should= 1 units-config/carrier-visibility-radius))

    (it "has strength of 1"
      (should= 1 units-config/carrier-strength)))

  (context "initial-state"
    (it "starts with no fighters"
      (should= {:fighter-count 0 :awake-fighters 0} (carrier/initial-state))))

  (context "can-move-to?"
    (it "returns true for sea"
      (should (carrier/can-move-to? {:type :sea})))

    (it "returns false for land"
      (should-not (carrier/can-move-to? {:type :land})))

    (it "returns false for city"
      (should-not (carrier/can-move-to? {:type :city})))

    (it "returns false for nil"
      (should-not (carrier/can-move-to? nil))))

  (context "needs-attention?"
    (it "returns true when awake"
      (should (carrier/needs-attention? {:type :carrier :mode :awake})))

    (it "returns true when has awake fighters"
      (should (carrier/needs-attention? {:type :carrier :mode :sentry :awake-fighters 2})))

    (it "returns false when sentry with no awake fighters"
      (should-not (carrier/needs-attention? {:type :carrier :mode :sentry :awake-fighters 0})))

    (it "returns false when moving"
      (should-not (carrier/needs-attention? {:type :carrier :mode :moving :awake-fighters 0})))

    (it "returns false when sentry with no awake-fighters key"
      (should-not (carrier/needs-attention? {:type :carrier :mode :sentry}))))

  (context "full?"
    (it "returns true at capacity"
      (should (carrier/full? {:fighter-count 8})))

    (it "returns true above capacity"
      (should (carrier/full? {:fighter-count 9})))

    (it "returns false below capacity"
      (should-not (carrier/full? {:fighter-count 5})))

    (it "returns false for empty carrier"
      (should-not (carrier/full? {:fighter-count 0})))

    (it "returns false when fighter-count key missing"
      (should-not (carrier/full? {}))))

  (context "has-fighters?"
    (it "returns true when has fighters"
      (should (carrier/has-fighters? {:fighter-count 4})))

    (it "returns false when empty"
      (should-not (carrier/has-fighters? {:fighter-count 0})))

    (it "returns false when fighter-count key missing"
      (should-not (carrier/has-fighters? {}))))

  (context "has-awake-fighters?"
    (it "returns true when has awake fighters"
      (should (carrier/has-awake-fighters? {:awake-fighters 3})))

    (it "returns false when no awake fighters"
      (should-not (carrier/has-awake-fighters? {:awake-fighters 0})))

    (it "returns false when awake-fighters key missing"
      (should-not (carrier/has-awake-fighters? {}))))

  (context "add-fighter"
    (it "increments fighter count"
      (should= 5 (:fighter-count (carrier/add-fighter {:fighter-count 4}))))

    (it "handles nil fighter count"
      (should= 1 (:fighter-count (carrier/add-fighter {})))))

  (context "remove-fighter"
    (it "decrements fighter count"
      (should= 3 (:fighter-count (carrier/remove-fighter {:fighter-count 4}))))

    (it "decrements from default 0 when key missing"
      (should= -1 (:fighter-count (carrier/remove-fighter {})))))

  (context "wake-fighters"
    (it "sets awake-fighters to fighter-count"
      (let [result (carrier/wake-fighters {:fighter-count 6 :awake-fighters 0})]
        (should= 6 (:awake-fighters result))))

    (it "defaults to 0 when fighter-count key missing"
      (should= 0 (:awake-fighters (carrier/wake-fighters {})))))

  (context "sleep-fighters"
    (it "sets awake-fighters to 0"
      (let [result (carrier/sleep-fighters {:fighter-count 6 :awake-fighters 4})]
        (should= 0 (:awake-fighters result)))))

  (context "remove-awake-fighter"
    (it "decrements both fighter-count and awake-fighters"
      (let [result (carrier/remove-awake-fighter {:fighter-count 5 :awake-fighters 3})]
        (should= 4 (:fighter-count result))
        (should= 2 (:awake-fighters result))))

    (it "decrements from default 0 when keys missing"
      (let [result (carrier/remove-awake-fighter {})]
        (should= -1 (:fighter-count result))
        (should= -1 (:awake-fighters result))))))
