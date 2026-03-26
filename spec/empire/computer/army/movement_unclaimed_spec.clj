(ns empire.computer.army.movement-unclaimed-spec
  (:require [empire.computer.army.movement :as movement]
            [empire.state.api :as sa]
            [empire.test.utils :refer [reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "find-nearest-unclaimed"
  (before (reset-all-atoms!))

  (it "returns nearest candidate not in claimed-objectives"
    (sa/write-state! :claimed-objectives #{[1 0]})
    (should= [2 0] (movement/find-nearest-unclaimed [[1 0] [2 0] [3 0]] [0 0])))

  (it "returns nil when all candidates are claimed"
    (sa/write-state! :claimed-objectives #{[1 0] [2 0]})
    (should-be-nil (movement/find-nearest-unclaimed [[1 0] [2 0]] [0 0])))

  (it "returns nearest by distance when none claimed"
    (should= [1 0] (movement/find-nearest-unclaimed [[3 0] [1 0] [2 0]] [0 0]))))
