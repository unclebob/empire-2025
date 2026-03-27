(ns empire.game-mechanics.movement.visibility-predicates-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.visibility :refer :all]
            [empire.game-mechanics.visibility.core :as vis-core]
            [empire.test.utils :refer [build-test-map set-test-unit reset-all-atoms! set-test-player-map! set-test-computer-map! make-initial-test-map set-test-world!]]))
(describe "in-bounds?"
  (it "returns true for coordinates within bounds"
    (should= true (vis-core/in-bounds? 0 0 5 5))
    (should= true (vis-core/in-bounds? 4 4 5 5))
    (should= true (vis-core/in-bounds? 2 3 5 5)))

  (it "returns false for negative row"
    (should= false (vis-core/in-bounds? -1 0 5 5)))

  (it "returns false for negative col"
    (should= false (vis-core/in-bounds? 0 -1 5 5)))

  (it "returns false for row at height"
    (should= false (vis-core/in-bounds? 5 0 5 5)))

  (it "returns false for col at width"
    (should= false (vis-core/in-bounds? 0 5 5 5))))

(describe "should-stamp-country?"
  (it "returns truthy for computer army with country-id"
    (should (vis-core/should-stamp-country?
              {:type :army :owner :computer :country-id 3})))

  (it "returns falsy for nil unit"
    (should-not (vis-core/should-stamp-country? nil)))

  (it "returns falsy for player army"
    (should-not (vis-core/should-stamp-country?
                  {:type :army :owner :player :country-id 3})))

  (it "returns falsy for computer fighter"
    (should-not (vis-core/should-stamp-country?
                  {:type :fighter :owner :computer :country-id 3})))

  (it "returns falsy for computer army without country-id"
    (should-not (vis-core/should-stamp-country?
                  {:type :army :owner :computer}))))

(describe "was-unexplored?"
  (it "returns true for nil cell in visible map"
    (let [visible-map [[nil nil] [nil nil]]]
      (should= true (vis-core/was-unexplored? visible-map 0 0))))

  (it "returns true for unexplored cell"
    (let [visible-map [[{:type :unexplored} nil] [nil nil]]]
      (should= true (vis-core/was-unexplored? visible-map 0 0))))

  (it "returns false for revealed land cell"
    (let [visible-map [[{:type :land} nil] [nil nil]]]
      (should= false (vis-core/was-unexplored? visible-map 0 0))))

  (it "returns false for revealed sea cell"
    (let [visible-map [[{:type :sea} nil] [nil nil]]]
      (should= false (vis-core/was-unexplored? visible-map 0 0)))))
