(ns empire.movement.movement-execution-spec
  (:require [empire.atoms :as atoms]
            [empire.movement.movement-execution :as execution]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-world!]]
            [speclj.core :refer :all]))

(describe "movement-execution"
  (before (reset-all-atoms!))

  (it "destroys fighter when fuel would go below zero"
    (should-be-nil
      (execution/process-consumables {:type :fighter :owner :player :fuel 0}
                                     {:type :land})))

  (it "does normal move when fighter enters non-player city"
    (set-test-world! (build-test-map ["FX"]))
    (let [cell {:type :land
                :contents {:type :fighter :owner :player :fuel 10 :mode :moving :target [1 0]}}
          final-unit (:contents cell)]
      (execution/do-move [0 0] [1 0] cell final-unit)
      (let [city (get-in @atoms/game-map [1 0])]
        (should= :fighter (get-in city [:contents :type]))
        (should= :player (get-in city [:contents :owner]))))))
