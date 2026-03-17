(ns empire.computer.ship-major-invasion-spec
  (:require [empire.computer.ship :as ship]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world!]]
            [speclj.core :refer :all]))

(describe "major invasion ship attacks"
  (before (reset-all-atoms!))

  (it "attacks a directly adjacent enemy ship before checking the wider neighborhood"
    (set-test-world! (build-test-map ["dP"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [empire.computer.ship-core/attack-enemy (fn [_ enemy] enemy)]
      (should= [1 0]
               (@#'ship/try-major-invasion-attack [0 0]))))

  (it "attacks a nearby non-transport player target when no direct enemy ship exists"
    (set-test-world! (build-test-map ["d#"
                                      "A~"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                  empire.computer.ship-core/attack-enemy (fn [_ enemy] enemy)]
      (should= [0 1]
               (@#'ship/try-major-invasion-attack [0 0]))))

  (it "ignores player transports and satellites in the major invasion neighborhood search"
    (set-test-world! (build-test-map ["dT"
                                      "V~"]))
    (set-test-computer-map! (test-utils/read-test-state :game-map))
    (with-redefs [empire.computer.ship-core/find-adjacent-enemy-ship (constantly nil)
                  empire.computer.ship-core/attack-enemy (fn [& _] :attacked)]
      (should= nil
               (@#'ship/try-major-invasion-attack [0 0])))))
