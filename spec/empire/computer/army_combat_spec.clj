(ns empire.computer.army-combat-spec
  (:require [empire.computer.army.combat :as army-combat]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world!]]
            [speclj.core :refer :all]))

(describe "army combat city conquest side effects"
  (before (reset-all-atoms!))

  (it "rebuilds kamikazee routing after a successful city conquest"
    (set-test-world! (build-test-map ["a+"]))
    (set-test-computer-map! (build-test-map ["a+"]))
    (let [calls (atom [])]
      (with-redefs [rand (constantly 0.1)
                    empire.computer.threat-response/rebuild-kamikazee-routing!
                    (fn [] (swap! calls conj :rebuilt))]
        (army-combat/attack-enemy [0 0] [1 0]))
      (should= [:rebuilt] @calls)))

  (it "does not rebuild kamikazee routing after a failed city conquest"
    (set-test-world! (build-test-map ["a+"]))
    (set-test-computer-map! (build-test-map ["a+"]))
    (let [calls (atom [])]
      (with-redefs [rand (constantly 0.9)
                    empire.computer.threat-response/rebuild-kamikazee-routing!
                    (fn [] (swap! calls conj :rebuilt))]
        (army-combat/attack-enemy [0 0] [1 0]))
      (should= [] @calls))))
