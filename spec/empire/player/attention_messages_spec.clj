(ns empire.player.attention-messages-spec
  (:require [empire.player.attention :as attention]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map get-test-city get-test-unit reset-all-atoms! set-test-unit set-test-world! update-test-world!]]
            [speclj.core :refer :all]))

(describe "set-attention-message"
  (before (reset-all-atoms!))

  (it "sets message for airport fighter"
    (set-test-world! (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (update-test-world! assoc-in (conj city-coords :awake-fighters) 1)
      (update-test-world! assoc-in (conj city-coords :fighter-count) 1)
      (test-utils/set-test-state! :production {city-coords {:item :fighter :remaining-rounds 10}})
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message city-coords)
      (should-contain "Fighter" (test-utils/read-test-state :attention-message))
      (should-contain "in airport" (test-utils/read-test-state :attention-message))))

  (it "sets message for carrier fighter"
    (set-test-world! (build-test-map ["C"]))
    (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :awake-fighters 1 :fighter-count 2)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))]
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message unit-coords)
      (should-contain "Fighter" (test-utils/read-test-state :attention-message))
      (should-contain "carrier" (test-utils/read-test-state :attention-message))
      (should-contain "2 fighters" (test-utils/read-test-state :attention-message))))

  (it "sets message for army aboard transport"
    (set-test-world! (build-test-map ["T"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :awake-armies 1 :army-count 3)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message unit-coords)
      (should-contain "Army" (test-utils/read-test-state :attention-message))
      (should-contain "transport" (test-utils/read-test-state :attention-message))
      (should-contain "3 armies" (test-utils/read-test-state :attention-message))))

  (it "sets message for regular awake army"
    (set-test-world! (build-test-map ["A"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake :hits 1)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))]
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message unit-coords)
      (should-contain "army" (test-utils/read-test-state :attention-message))))

  (it "sets message for transport with cargo count"
    (set-test-world! (build-test-map ["T"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :awake :hits 1 :army-count 4)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message unit-coords)
      (should-contain "transport" (test-utils/read-test-state :attention-message))
      (should-contain "4 armies" (test-utils/read-test-state :attention-message))))

  (it "sets message for carrier with cargo count"
    (set-test-world! (build-test-map ["C"]))
    (set-test-unit (test-utils/game-map-atom) "C" :mode :awake :hits 8 :fighter-count 3)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))]
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message unit-coords)
      (should-contain "carrier" (test-utils/read-test-state :attention-message))
      (should-contain "3 fighters" (test-utils/read-test-state :attention-message))))

  (it "sets reason as warning for unit with reason"
    (set-test-world! (build-test-map ["A"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake :hits 1 :reason :somethings-in-the-way)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))]
      (test-utils/set-test-state! :attention-message "")
      (test-utils/set-test-state! :warning-message "")
      (attention/set-attention-message unit-coords)
      (should-contain "army" (test-utils/read-test-state :attention-message))
      (should= "Something's in the way." (test-utils/read-test-state :warning-message))))

  (it "sets reason as warning for army adjacent to enemy city"
    (set-test-world! (build-test-map ["AX"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake :hits 1)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))]
      (test-utils/set-test-state! :attention-message "")
      (test-utils/set-test-state! :warning-message "")
      (attention/set-attention-message unit-coords)
      (should-contain "army" (test-utils/read-test-state :attention-message))
      (should= "Army found a city!" (test-utils/read-test-state :warning-message))))

  (it "sets message for player city without production"
    (set-test-world! (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message city-coords)
      (should= "City" (test-utils/read-test-state :attention-message))))

  (it "includes Damaged for damaged carrier"
    (set-test-world! (build-test-map ["C"]))
    (set-test-unit (test-utils/game-map-atom) "C" :mode :awake :hits 5)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))]
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message unit-coords)
      (should-contain "Damaged" (test-utils/read-test-state :attention-message))
      (should-contain "carrier" (test-utils/read-test-state :attention-message))
      (should-contain "hits:5/8" (test-utils/read-test-state :attention-message))))

  (it "includes Damaged for damaged destroyer"
    (set-test-world! (build-test-map ["D"]))
    (set-test-unit (test-utils/game-map-atom) "D" :mode :awake :hits 2)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "D"))]
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message unit-coords)
      (should-contain "Damaged" (test-utils/read-test-state :attention-message))
      (should-contain "destroyer" (test-utils/read-test-state :attention-message))
      (should-contain "hits:2/3" (test-utils/read-test-state :attention-message))))

  (it "does not include Damaged for undamaged carrier"
    (set-test-world! (build-test-map ["C"]))
    (set-test-unit (test-utils/game-map-atom) "C" :mode :awake :hits 8)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))]
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message unit-coords)
      (should-not-contain "Damaged" (test-utils/read-test-state :attention-message))
      (should-contain "carrier" (test-utils/read-test-state :attention-message))
      (should-contain "hits:8/8" (test-utils/read-test-state :attention-message))))

  (it "does not include Damaged for army (1 hit max)"
    (set-test-world! (build-test-map ["A"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake :hits 1)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))]
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message unit-coords)
      (should-not-contain "Damaged" (test-utils/read-test-state :attention-message))))

  (it "includes fuel in regular fighter message"
    (set-test-world! (build-test-map ["F"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :fuel 20)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))]
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message unit-coords)
      (should-contain "fuel:20" (test-utils/read-test-state :attention-message))))

  (it "includes fuel in airport fighter message"
    (set-test-world! (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (update-test-world! assoc-in (conj city-coords :awake-fighters) 1)
      (update-test-world! assoc-in (conj city-coords :fighter-count) 1)
      (test-utils/set-test-state! :production {city-coords {:item :fighter :remaining-rounds 10}})
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message city-coords)
      (should-contain "fuel:32" (test-utils/read-test-state :attention-message))))

  (it "includes fuel in carrier fighter message"
    (set-test-world! (build-test-map ["C"]))
    (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :awake-fighters 1 :fighter-count 2)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))]
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message unit-coords)
      (should-contain "fuel:32" (test-utils/read-test-state :attention-message))))

  (it "does not include fuel in army message"
    (set-test-world! (build-test-map ["A"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake :hits 1)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))]
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message unit-coords)
      (should-not-contain "fuel:" (test-utils/read-test-state :attention-message))))

  (it "does not include reason suffix when no reason"
    (set-test-world! (build-test-map ["D"]))
    (set-test-unit (test-utils/game-map-atom) "D" :mode :awake :hits 3)
    (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "D"))]
      (test-utils/set-test-state! :attention-message "")
      (attention/set-attention-message unit-coords)
      (should-not-contain " - " (test-utils/read-test-state :attention-message)))))
