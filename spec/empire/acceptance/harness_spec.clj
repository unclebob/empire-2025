(ns empire.acceptance.harness-spec
  (:require [speclj.core :refer :all]
            [empire.acceptance.harness :as h]))

(describe "acceptance harness characterization"
  (before
    (h/reset-all-atoms!))

  (it "reads values written via set-state!"
    (h/set-state! :round-number 7)
    (should= 7 (h/read-state :round-number)))

  (it "returns nil for unsupported read-state key"
    (should-be-nil (h/read-state :unsupported-key)))

  (it "updates production via update-state!"
    (h/set-state! :production {1 {:item :army :remaining-rounds 3}})
    (h/update-state! :production assoc 2 {:item :fighter :remaining-rounds 5})
    (should= {:item :fighter :remaining-rounds 5}
             (get (h/read-state :production) 2)))

  (it "sets and queries unit state through harness helpers"
    (h/set-test-world! (h/build-test-map ["A#"]))
    (h/set-unit! "A" :mode :sentry :fuel 11)
    (let [u (h/get-unit "A")]
      (should= :sentry (get-in u [:unit :mode]))
      (should= 11 (get-in u [:unit :fuel]))))

  (it "queries city and labeled cell helpers"
    (h/set-test-world! (h/build-test-map ["O=%"]))
    (let [city (h/get-city "O")
          sea-label (h/get-cell "=")
          land-label (h/get-cell "%")]
      (should= [0 0] (:pos city))
      (should= [1 0] (:pos sea-label))
      (should= [2 0] (:pos land-label))))

  (it "supports variadic update-test-world! operations"
    (h/set-test-world! (h/build-test-map ["##" "##"]))
    (h/update-test-world! assoc-in [1 0 :waypoint] true)
    (should (true? (:waypoint (h/cell-at [1 0])))))

  (it "throws on unsupported set-state! key"
    (should-throw clojure.lang.ExceptionInfo
                  (h/set-state! :unsupported-key 1)))

  (it "throws on unsupported update-state! key"
    (should-throw clojure.lang.ExceptionInfo
                  (h/update-state! :unsupported-key assoc :x 1))))
