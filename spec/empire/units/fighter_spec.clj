(ns empire.units.fighter-spec
  (:require [speclj.core :refer :all]
            [empire.units.fighter :as fighter]))

(describe "fighter unit module"
  (describe "configuration constants"
    (it "has fuel of 32"
      (should= 32 fighter/fuel))

    (it "has bingo threshold of 8 (quarter of fuel)"
      (should= 8 fighter/bingo-threshold)))

  (describe "can-move-to?"
    (it "returns true for any cell type"
      (should (fighter/can-move-to? {:type :land}))
      (should (fighter/can-move-to? {:type :sea}))
      (should (fighter/can-move-to? {:type :city}))))

  (describe "needs-attention?"
    (it "returns true when awake"
      (should (fighter/needs-attention? {:type :fighter :mode :awake})))

    (it "returns false when sentry"
      (should-not (fighter/needs-attention? {:type :fighter :mode :sentry})))

    (it "returns false when moving"
      (should-not (fighter/needs-attention? {:type :fighter :mode :moving})))

    (it "returns false when exploring"
      (should-not (fighter/needs-attention? {:type :fighter :mode :explore}))))

  (describe "consume-fuel"
    (it "decrements fuel by 1"
      (let [unit {:type :fighter :fuel 20}
            result (fighter/consume-fuel unit)]
        (should= 19 (:fuel result))))

    (it "returns nil when fuel would go negative"
      (let [unit {:type :fighter :fuel 0}]
        (should-be-nil (fighter/consume-fuel unit))))

    (it "uses default fuel when not specified"
      (let [unit {:type :fighter}
            result (fighter/consume-fuel unit)]
        (should= 31 (:fuel result)))))

  (describe "refuel"
    (it "sets fuel to full capacity"
      (let [unit {:type :fighter :fuel 5}
            result (fighter/refuel unit)]
        (should= 32 (:fuel result)))))

  (describe "bingo?"
    (it "returns true when fuel is at threshold"
      (should (fighter/bingo? {:type :fighter :fuel 8})))

    (it "returns true when fuel is below threshold"
      (should (fighter/bingo? {:type :fighter :fuel 5})))

    (it "returns false when fuel is above threshold"
      (should-not (fighter/bingo? {:type :fighter :fuel 20}))))

  (describe "out-of-fuel?"
    (it "returns true when fuel is 1"
      (should (fighter/out-of-fuel? {:type :fighter :fuel 1})))

    (it "returns true when fuel is 0"
      (should (fighter/out-of-fuel? {:type :fighter :fuel 0})))

    (it "returns false when fuel is above 1"
      (should-not (fighter/out-of-fuel? {:type :fighter :fuel 2}))))

  (describe "can-land-at-city?"
    (it "returns true for player city"
      (should (fighter/can-land-at-city? {:type :city :city-status :player})))

    (it "returns false for enemy city"
      (should-not (fighter/can-land-at-city? {:type :city :city-status :computer})))

    (it "returns false for free city"
      (should-not (fighter/can-land-at-city? {:type :city :city-status :free})))

    (it "returns false for non-city"
      (should-not (fighter/can-land-at-city? {:type :land}))))

  (describe "can-land-on-carrier?"
    (it "returns true for friendly carrier with space"
      (let [cell {:contents {:type :carrier :owner :player :fighter-count 3}}]
        (should (fighter/can-land-on-carrier? cell :player 8))))

    (it "returns false for full carrier"
      (let [cell {:contents {:type :carrier :owner :player :fighter-count 8}}]
        (should-not (fighter/can-land-on-carrier? cell :player 8))))

    (it "returns false for enemy carrier"
      (let [cell {:contents {:type :carrier :owner :computer :fighter-count 3}}]
        (should-not (fighter/can-land-on-carrier? cell :player 8))))

    (it "returns false for non-carrier"
      (let [cell {:contents {:type :transport :owner :player}}]
        (should-not (fighter/can-land-on-carrier? cell :player 8))))

    (it "returns false for empty cell"
      (should-not (fighter/can-land-on-carrier? {:type :sea} :player 8)))))
