(ns empire.game-mechanics.containers.helpers-spec
  (:require [speclj.core :refer :all]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.config.units.dispatcher :as dispatcher]))

(describe "count and query functions"
  (context "get-count"
    (it "returns count when key exists"
      (should= 5 (uc/get-count {:fighter-count 5} :fighter-count)))

    (it "returns 0 when key is missing"
      (should= 0 (uc/get-count {} :fighter-count)))

    (it "returns 0 for nil entity"
      (should= 0 (uc/get-count nil :fighter-count))))

  (context "get-awake-count"
    (it "returns awake count when key exists"
      (should= 3 (uc/get-awake-count {:awake-fighters 3} :awake-fighters)))

    (it "returns 0 when key is missing"
      (should= 0 (uc/get-awake-count {} :awake-fighters))))

  (context "has-awake?"
    (it "returns true when awake count is positive"
      (should (uc/has-awake? {:awake-fighters 1} :awake-fighters)))

    (it "returns false when awake count is zero"
      (should-not (uc/has-awake? {:awake-fighters 0} :awake-fighters)))

    (it "returns false when key is missing"
      (should-not (uc/has-awake? {} :awake-fighters))))

  (context "full?"
    (it "returns true when at capacity"
      (should (uc/full? {:army-count 6} :army-count 6)))

    (it "returns true when over capacity"
      (should (uc/full? {:army-count 7} :army-count 6)))

    (it "returns false when under capacity"
      (should-not (uc/full? {:army-count 5} :army-count 6)))

    (it "returns false when count is missing"
      (should-not (uc/full? {} :army-count 6)))

    (it "returns false when count is missing and capacity is 1"
      (should-not (uc/full? {} :army-count 1)))))

(describe "unit add and remove"
  (context "add-unit"
    (it "increments existing count"
      (should= {:fighter-count 3} (uc/add-unit {:fighter-count 2} :fighter-count)))

    (it "initializes count to 1 when missing"
      (should= {:fighter-count 1} (uc/add-unit {} :fighter-count)))

    (it "initializes count to 1 when nil"
      (should= {:fighter-count 1} (uc/add-unit {:fighter-count nil} :fighter-count))))

  (context "add-awake-unit"
    (it "increments both count and awake count"
      (let [result (uc/add-awake-unit {:fighter-count 2 :awake-fighters 1} :fighter-count :awake-fighters)]
        (should= 3 (:fighter-count result))
        (should= 2 (:awake-fighters result))))

    (it "initializes both counts when missing"
      (let [result (uc/add-awake-unit {} :fighter-count :awake-fighters)]
        (should= 1 (:fighter-count result))
        (should= 1 (:awake-fighters result)))))

  (context "remove-awake-unit"
    (it "decrements both count and awake count"
      (let [result (uc/remove-awake-unit {:fighter-count 3 :awake-fighters 2} :fighter-count :awake-fighters)]
        (should= 2 (:fighter-count result))
        (should= 1 (:awake-fighters result))))

    (it "handles nil count-key gracefully"
      (let [result (uc/remove-awake-unit {:awake-fighters 1} :fighter-count :awake-fighters)]
        (should= -1 (:fighter-count result))
        (should= 0 (:awake-fighters result))))

    (it "handles nil awake-key gracefully"
      (let [result (uc/remove-awake-unit {:fighter-count 1} :fighter-count :awake-fighters)]
        (should= 0 (:fighter-count result))
        (should= -1 (:awake-fighters result))))))

(describe "wake and sleep"
  (context "wake-all"
    (it "sets awake count equal to total count"
      (let [result (uc/wake-all {:fighter-count 5 :awake-fighters 0} :fighter-count :awake-fighters)]
        (should= 5 (:awake-fighters result))))

    (it "handles missing awake key"
      (let [result (uc/wake-all {:fighter-count 3} :fighter-count :awake-fighters)]
        (should= 3 (:awake-fighters result))))

    (it "sets awake to zero when count key is missing"
      (let [result (uc/wake-all {} :fighter-count :awake-fighters)]
        (should= 0 (:awake-fighters result)))))

  (context "sleep-all"
    (it "sets awake count to zero"
      (let [result (uc/sleep-all {:awake-fighters 5} :awake-fighters)]
        (should= 0 (:awake-fighters result))))))

(describe "type predicates"
  (context "transport-with-armies?"
    (it "returns true for transport with armies"
      (should (uc/transport-with-armies? {:type :transport :army-count 3})))

    (it "returns false for transport with no armies"
      (should-not (uc/transport-with-armies? {:type :transport :army-count 0})))

    (it "returns false for transport with nil army-count"
      (should-not (uc/transport-with-armies? {:type :transport})))

    (it "returns false for non-transport"
      (should-not (uc/transport-with-armies? {:type :carrier :army-count 3}))))

  (context "transport-at-beach?"
    (it "returns true for transport at beach with armies"
      (should (uc/transport-at-beach? {:type :transport :reason :transport-at-beach :army-count 2})))

    (it "returns true for transport that found a bay with armies"
      (should (uc/transport-at-beach? {:type :transport :reason :found-a-bay :army-count 2})))

    (it "returns true for awake player transport with armies and no reason"
      (should (uc/transport-at-beach? {:type :transport :owner :player :mode :awake :army-count 2})))

    (it "returns false for awake computer transport with armies and no reason"
      (should-not (uc/transport-at-beach? {:type :transport :owner :computer :mode :awake :army-count 2})))

    (it "returns false for transport at beach with no armies"
      (should-not (uc/transport-at-beach? {:type :transport :reason :transport-at-beach :army-count 0})))

    (it "returns false for sentry player transport with no reason"
      (should-not (uc/transport-at-beach? {:type :transport :owner :player :mode :sentry :army-count 2})))

    (it "returns false for awake player transport with non-beach reason"
      (should-not (uc/transport-at-beach? {:type :transport :owner :player :mode :awake
                                           :army-count 2 :reason :some-other})))

    (it "returns false for transport at beach with missing army-count"
      (should-not (uc/transport-at-beach? {:type :transport :reason :transport-at-beach})))

    (it "returns false for non-transport"
      (should-not (uc/transport-at-beach? {:type :carrier :reason :transport-at-beach :army-count 2}))))

  (context "carrier-with-fighters?"
    (it "returns true for carrier with fighters"
      (should (uc/carrier-with-fighters? {:type :carrier :fighter-count 3})))

    (it "returns false for carrier with no fighters"
      (should-not (uc/carrier-with-fighters? {:type :carrier :fighter-count 0})))

    (it "returns false for non-carrier"
      (should-not (uc/carrier-with-fighters? {:type :transport :fighter-count 3}))))

  (context "has-awake-carrier-fighter?"
    (it "returns true for carrier with awake fighters"
      (should (uc/has-awake-carrier-fighter? {:type :carrier :awake-fighters 1})))

    (it "returns false for carrier with no awake fighters"
      (should-not (uc/has-awake-carrier-fighter? {:type :carrier :awake-fighters 0})))

    (it "returns false for non-carrier"
      (should-not (uc/has-awake-carrier-fighter? {:type :transport :awake-fighters 1}))))

  (context "has-awake-army-aboard?"
    (it "returns true for transport with awake armies"
      (should (uc/has-awake-army-aboard? {:type :transport :awake-armies 1})))

    (it "returns false for transport with no awake armies"
      (should-not (uc/has-awake-army-aboard? {:type :transport :awake-armies 0})))

    (it "returns false for non-transport"
      (should-not (uc/has-awake-army-aboard? {:type :carrier :awake-armies 1})))))

(describe "has-unit-contents?"
  (it "returns truthy for valid unit map"
    (should (@#'uc/has-unit-contents? {:type :army :mode :awake})))

  (it "returns falsy for nil"
    (should-not (@#'uc/has-unit-contents? nil)))

  (it "returns falsy for empty map"
    (should-not (@#'uc/has-unit-contents? {})))

  (it "returns falsy for map without :type"
    (should-not (@#'uc/has-unit-contents? {:mode :awake}))))

(describe "awake-contents?"
  (it "returns true for awake unit with type"
    (should (@#'uc/awake-contents? {:type :army :mode :awake})))

  (it "returns false for nil"
    (should-not (@#'uc/awake-contents? nil)))

  (it "returns false for non-awake unit"
    (should-not (@#'uc/awake-contents? {:type :army :mode :sentry})))

  (it "returns false for awake without type"
    (should-not (@#'uc/awake-contents? {:mode :awake}))))

(describe "display helpers"
  (context "blinking-contained-unit"
    (it "returns fighter for awake airport"
      (should= {:type :fighter :mode :awake}
               (uc/blinking-contained-unit true false false)))

    (it "returns fighter for awake carrier"
      (should= {:type :fighter :mode :awake}
               (uc/blinking-contained-unit false true false)))

    (it "returns army for awake army aboard"
      (should= {:type :army :mode :awake}
               (uc/blinking-contained-unit false false true)))

    (it "returns nil when nothing awake"
      (should-not (uc/blinking-contained-unit false false false)))

    (it "prioritizes airport over carrier"
      (should= {:type :fighter :mode :awake}
               (uc/blinking-contained-unit true true false))))

  (context "normal-display-unit"
    (it "returns awake contents first"
      (let [unit {:type :army :mode :awake}]
        (should= unit (uc/normal-display-unit {} unit false false))))

    (it "returns awake airport fighter over non-awake contents"
      (let [unit {:type :army :mode :sentry}]
        (should= {:type :fighter :mode :awake}
                 (uc/normal-display-unit {} unit true false))))

    (it "returns non-awake contents when no awake airport"
      (let [unit {:type :army :mode :sentry}]
        (should= unit (uc/normal-display-unit {} unit false false))))

    (it "returns sentry fighter for airport with no awake fighters"
      (should= {:type :fighter :mode :sentry}
               (uc/normal-display-unit {} nil false true)))

    (it "ignores contents without type even if mode is awake"
      (should-not (uc/normal-display-unit {} {:mode :awake} false false)))

    (it "ignores non-awake contents without type"
      (should-not (uc/normal-display-unit {} {:mode :sentry} false false)))

    (it "returns nil when nothing to display"
      (should-not (uc/normal-display-unit {} nil false false)))))

(describe "shipyard operations"
  (context "add-ship-to-shipyard"
    (it "adds a ship to an empty shipyard"
      (should= {:shipyard [{:type :destroyer :hits 1}]}
               (uc/add-ship-to-shipyard {} :destroyer 1)))

    (it "appends to an existing shipyard"
      (let [city {:shipyard [{:type :submarine :hits 2}]}
            result (uc/add-ship-to-shipyard city :destroyer 1)]
        (should= [{:type :submarine :hits 2} {:type :destroyer :hits 1}]
                 (:shipyard result)))))

  (context "remove-ship-from-shipyard"
    (it "removes first ship"
      (let [city {:shipyard [{:type :destroyer :hits 1} {:type :submarine :hits 2}]}]
        (should= [{:type :submarine :hits 2}]
                 (:shipyard (uc/remove-ship-from-shipyard city 0)))))

    (it "removes middle ship"
      (let [city {:shipyard [{:type :destroyer :hits 1}
                             {:type :submarine :hits 2}
                             {:type :battleship :hits 3}]}]
        (should= [{:type :destroyer :hits 1} {:type :battleship :hits 3}]
                 (:shipyard (uc/remove-ship-from-shipyard city 1)))))

    (it "removes last ship"
      (let [city {:shipyard [{:type :destroyer :hits 1} {:type :submarine :hits 2}]}]
        (should= [{:type :destroyer :hits 1}]
                 (:shipyard (uc/remove-ship-from-shipyard city 1))))))

  (context "get-shipyard-ships"
    (it "returns ships when present"
      (should= [{:type :destroyer :hits 1}]
               (uc/get-shipyard-ships {:shipyard [{:type :destroyer :hits 1}]})))

    (it "returns empty vector when no shipyard"
      (should= [] (uc/get-shipyard-ships {}))))

  (context "repair-ship"
    (it "increments hits by 1"
      (should= {:type :destroyer :hits 2}
               (uc/repair-ship {:type :destroyer :hits 1})))

    (it "caps hits at max for unit type"
      (let [max-hits (dispatcher/hits :destroyer)]
        (should= {:type :destroyer :hits max-hits}
                 (uc/repair-ship {:type :destroyer :hits max-hits}))))

    (it "increments to max without exceeding"
      (let [max-hits (dispatcher/hits :destroyer)]
        (should= {:type :destroyer :hits max-hits}
                 (uc/repair-ship {:type :destroyer :hits (dec max-hits)})))))

  (context "ship-fully-repaired?"
    (it "returns true when hits equal max"
      (let [max-hits (dispatcher/hits :destroyer)]
        (should (uc/ship-fully-repaired? {:type :destroyer :hits max-hits}))))

    (it "returns false when hits below max"
      (should-not (uc/ship-fully-repaired? {:type :destroyer :hits 1}))))

  (context "ship-can-dock?"
    (it "returns true for damaged player ship at player city"
      (should (uc/ship-can-dock? {:type :destroyer :hits 1 :owner :player}
                                 {:type :city :city-status :player})))

    (it "returns true for damaged computer ship at computer city"
      (should (uc/ship-can-dock? {:type :destroyer :hits 1 :owner :computer}
                                 {:type :city :city-status :computer})))

    (it "returns false for fully repaired ship"
      (let [max-hits (dispatcher/hits :destroyer)]
        (should-not (uc/ship-can-dock? {:type :destroyer :hits max-hits :owner :player}
                                       {:type :city :city-status :player}))))

    (it "returns false for non-naval unit"
      (should-not (uc/ship-can-dock? {:type :army :hits 1 :owner :player}
                                     {:type :city :city-status :player})))

    (it "returns false at enemy city"
      (should-not (uc/ship-can-dock? {:type :destroyer :hits 1 :owner :player}
                                     {:type :city :city-status :computer})))

    (it "returns false at free city"
      (should-not (uc/ship-can-dock? {:type :destroyer :hits 1 :owner :player}
                                     {:type :city :city-status :free})))

    (it "returns false at non-city"
      (should-not (uc/ship-can-dock? {:type :destroyer :hits 1 :owner :player}
                                     {:type :sea})))))

(run-specs)
