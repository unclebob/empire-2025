(ns empire.config.units.dispatcher-spec
  (:require [speclj.core :refer :all]
            [empire.config.units.dispatcher :as dispatcher]))

(describe "unit dispatcher module"
  (context "speed"
    (it "returns correct speed for each unit type"
      (should= 1 (dispatcher/speed :army))
      (should= 8 (dispatcher/speed :fighter))
      (should= 10 (dispatcher/speed :satellite))
      (should= 2 (dispatcher/speed :transport))
      (should= 2 (dispatcher/speed :carrier))
      (should= 4 (dispatcher/speed :patrol-boat))
      (should= 2 (dispatcher/speed :destroyer))
      (should= 2 (dispatcher/speed :submarine))
      (should= 2 (dispatcher/speed :battleship)))

    (it "returns nil for unknown type"
      (should-be-nil (dispatcher/speed :unknown))))

  (context "cost"
    (it "returns correct cost for each unit type"
      (should= 5 (dispatcher/cost :army))
      (should= 10 (dispatcher/cost :fighter))
      (should= 50 (dispatcher/cost :satellite))
      (should= 30 (dispatcher/cost :transport))
      (should= 30 (dispatcher/cost :carrier))
      (should= 15 (dispatcher/cost :patrol-boat))
      (should= 20 (dispatcher/cost :destroyer))
      (should= 20 (dispatcher/cost :submarine))
      (should= 40 (dispatcher/cost :battleship)))

    (it "returns nil for unknown type"
      (should-be-nil (dispatcher/cost :unknown))))

  (context "hits"
    (it "returns correct hits for each unit type"
      (should= 1 (dispatcher/hits :army))
      (should= 1 (dispatcher/hits :fighter))
      (should= 1 (dispatcher/hits :satellite))
      (should= 1 (dispatcher/hits :transport))
      (should= 8 (dispatcher/hits :carrier))
      (should= 1 (dispatcher/hits :patrol-boat))
      (should= 3 (dispatcher/hits :destroyer))
      (should= 2 (dispatcher/hits :submarine))
      (should= 10 (dispatcher/hits :battleship)))

    (it "returns nil for unknown type"
      (should-be-nil (dispatcher/hits :unknown))))

  (context "display-char"
    (it "returns correct character for each unit type"
      (should= "A" (dispatcher/display-char :army))
      (should= "F" (dispatcher/display-char :fighter))
      (should= "Z" (dispatcher/display-char :satellite))
      (should= "T" (dispatcher/display-char :transport))
      (should= "C" (dispatcher/display-char :carrier))
      (should= "P" (dispatcher/display-char :patrol-boat))
      (should= "D" (dispatcher/display-char :destroyer))
      (should= "S" (dispatcher/display-char :submarine))
      (should= "B" (dispatcher/display-char :battleship)))

    (it "returns nil for unknown type"
      (should-be-nil (dispatcher/display-char :unknown))))

  (context "visibility-radius"
    (it "returns 1 for most units"
      (should= 1 (dispatcher/visibility-radius :army))
      (should= 1 (dispatcher/visibility-radius :fighter))
      (should= 1 (dispatcher/visibility-radius :transport))
      (should= 1 (dispatcher/visibility-radius :carrier))
      (should= 1 (dispatcher/visibility-radius :patrol-boat))
      (should= 1 (dispatcher/visibility-radius :destroyer))
      (should= 1 (dispatcher/visibility-radius :submarine))
      (should= 1 (dispatcher/visibility-radius :battleship)))

    (it "returns 2 for satellite"
      (should= 2 (dispatcher/visibility-radius :satellite)))

    (it "returns nil for unknown type"
      (should-be-nil (dispatcher/visibility-radius :unknown))))

  (context "strength"
    (it "returns 1 for most units"
      (should= 1 (dispatcher/strength :army))
      (should= 1 (dispatcher/strength :fighter))
      (should= 1 (dispatcher/strength :satellite))
      (should= 1 (dispatcher/strength :transport))
      (should= 1 (dispatcher/strength :carrier))
      (should= 1 (dispatcher/strength :patrol-boat))
      (should= 1 (dispatcher/strength :destroyer)))

    (it "returns 3 for submarine"
      (should= 3 (dispatcher/strength :submarine)))

    (it "returns 2 for battleship"
      (should= 2 (dispatcher/strength :battleship)))

    (it "returns nil for unknown type"
      (should-be-nil (dispatcher/strength :unknown))))

  (context "initial-state"
    (it "returns empty map for simple units"
      (should= {} (dispatcher/initial-state :army))
      (should= {} (dispatcher/initial-state :patrol-boat))
      (should= {} (dispatcher/initial-state :destroyer))
      (should= {} (dispatcher/initial-state :submarine))
      (should= {} (dispatcher/initial-state :battleship)))

    (it "returns fuel state for fighter"
      (should= {:fuel 32} (dispatcher/initial-state :fighter)))

    (it "returns turns-remaining for satellite"
      (should= {:turns-remaining 50} (dispatcher/initial-state :satellite)))

    (it "returns container state for transport"
      (should= {:army-count 0 :awake-armies 0 :been-to-sea true} (dispatcher/initial-state :transport)))

    (it "returns container state for carrier"
      (should= {:fighter-count 0 :awake-fighters 0} (dispatcher/initial-state :carrier)))

    (it "returns empty map for unknown type"
      (should= {} (dispatcher/initial-state :unknown))))

  (context "can-move-to?"
    (it "delegates to army module"
      (should (dispatcher/can-move-to? :army {:type :land}))
      (should-not (dispatcher/can-move-to? :army {:type :sea})))

    (it "delegates to fighter module"
      (should (dispatcher/can-move-to? :fighter {:type :land}))
      (should (dispatcher/can-move-to? :fighter {:type :sea})))

    (it "delegates to satellite module"
      (should (dispatcher/can-move-to? :satellite {:type :land}))
      (should (dispatcher/can-move-to? :satellite {:type :sea})))

    (it "delegates to naval units"
      (should (dispatcher/can-move-to? :transport {:type :sea}))
      (should-not (dispatcher/can-move-to? :transport {:type :land}))
      (should (dispatcher/can-move-to? :carrier {:type :sea}))
      (should-not (dispatcher/can-move-to? :carrier {:type :land}))
      (should (dispatcher/can-move-to? :patrol-boat {:type :sea}))
      (should-not (dispatcher/can-move-to? :patrol-boat {:type :land}))
      (should (dispatcher/can-move-to? :destroyer {:type :sea}))
      (should-not (dispatcher/can-move-to? :destroyer {:type :land}))
      (should (dispatcher/can-move-to? :submarine {:type :sea}))
      (should-not (dispatcher/can-move-to? :submarine {:type :land}))
      (should (dispatcher/can-move-to? :battleship {:type :sea}))
      (should-not (dispatcher/can-move-to? :battleship {:type :land})))

    (it "throws for unknown type"
      (should-throw (dispatcher/can-move-to? :unknown {:type :land}))))

  (context "needs-attention?"
    (it "delegates to army module"
      (should (dispatcher/needs-attention? {:type :army :mode :awake}))
      (should-not (dispatcher/needs-attention? {:type :army :mode :sentry})))

    (it "delegates to fighter module"
      (should (dispatcher/needs-attention? {:type :fighter :mode :awake}))
      (should-not (dispatcher/needs-attention? {:type :fighter :mode :sentry})))

    (it "delegates to satellite module"
      (should (dispatcher/needs-attention? {:type :satellite :target nil}))
      (should-not (dispatcher/needs-attention? {:type :satellite :target [5 5]})))

    (it "delegates to transport module"
      (should (dispatcher/needs-attention? {:type :transport :mode :awake :awake-armies 0}))
      (should (dispatcher/needs-attention? {:type :transport :mode :sentry :awake-armies 1}))
      (should-not (dispatcher/needs-attention? {:type :transport :mode :sentry :awake-armies 0})))

    (it "delegates to carrier module"
      (should (dispatcher/needs-attention? {:type :carrier :mode :awake :awake-fighters 0}))
      (should (dispatcher/needs-attention? {:type :carrier :mode :sentry :awake-fighters 1}))
      (should-not (dispatcher/needs-attention? {:type :carrier :mode :sentry :awake-fighters 0})))

    (it "delegates to ship modules"
      (should (dispatcher/needs-attention? {:type :patrol-boat :mode :awake}))
      (should-not (dispatcher/needs-attention? {:type :patrol-boat :mode :sentry}))
      (should (dispatcher/needs-attention? {:type :destroyer :mode :awake}))
      (should-not (dispatcher/needs-attention? {:type :destroyer :mode :sentry}))
      (should (dispatcher/needs-attention? {:type :submarine :mode :awake}))
      (should-not (dispatcher/needs-attention? {:type :submarine :mode :sentry}))
      (should (dispatcher/needs-attention? {:type :battleship :mode :awake}))
      (should-not (dispatcher/needs-attention? {:type :battleship :mode :sentry})))

    (it "throws for unknown type"
      (should-throw (dispatcher/needs-attention? {:type :unknown :mode :awake}))))

  (context "effective-speed"
    (it "returns base speed at full health for all unit types"
      (should= 1 (dispatcher/effective-speed :army 1))
      (should= 8 (dispatcher/effective-speed :fighter 1))
      (should= 10 (dispatcher/effective-speed :satellite 1))
      (should= 2 (dispatcher/effective-speed :transport 1))
      (should= 2 (dispatcher/effective-speed :carrier 8))
      (should= 4 (dispatcher/effective-speed :patrol-boat 1))
      (should= 2 (dispatcher/effective-speed :destroyer 3))
      (should= 2 (dispatcher/effective-speed :submarine 2))
      (should= 2 (dispatcher/effective-speed :battleship 10)))

    (it "returns 1 for destroyer at 1/3 hits"
      (should= 1 (dispatcher/effective-speed :destroyer 1)))

    (it "returns 2 for destroyer at 2/3 hits"
      (should= 2 (dispatcher/effective-speed :destroyer 2)))

    (it "returns 1 for submarine at 1/2 hits"
      (should= 1 (dispatcher/effective-speed :submarine 1)))

    (it "returns 1 for battleship at 5/10 hits"
      (should= 1 (dispatcher/effective-speed :battleship 5)))

    (it "returns 1 for carrier at 4/8 hits"
      (should= 1 (dispatcher/effective-speed :carrier 4)))

    (it "returns 1 for carrier at 1/8 hits"
      (should= 1 (dispatcher/effective-speed :carrier 1))))

  (context "capacity"
    (it "returns correct capacity for container units"
      (should= 6 (dispatcher/capacity :transport))
      (should= 8 (dispatcher/capacity :carrier)))

    (it "returns nil for non-container units"
      (should-be-nil (dispatcher/capacity :army))
      (should-be-nil (dispatcher/capacity :fighter))
      (should-be-nil (dispatcher/capacity :destroyer))))

  (context "effective-capacity"
    (it "returns base capacity at full health"
      (should= 6 (dispatcher/effective-capacity :transport 1))
      (should= 8 (dispatcher/effective-capacity :carrier 8)))

    (it "returns 4 for carrier at 4/8 hits"
      (should= 4 (dispatcher/effective-capacity :carrier 4)))

    (it "returns 1 for carrier at 1/8 hits"
      (should= 1 (dispatcher/effective-capacity :carrier 1)))

    (it "scales carrier capacity linearly with hits"
      (should= 7 (dispatcher/effective-capacity :carrier 7))
      (should= 6 (dispatcher/effective-capacity :carrier 6))
      (should= 5 (dispatcher/effective-capacity :carrier 5))
      (should= 3 (dispatcher/effective-capacity :carrier 3))
      (should= 2 (dispatcher/effective-capacity :carrier 2)))

    (it "defaults to max hits when current-hits is nil"
      (should= 6 (dispatcher/effective-capacity :transport nil))
      (should= 8 (dispatcher/effective-capacity :carrier nil))))

  (context "naval-unit?"
    (it "returns true for naval units"
      (should (dispatcher/naval-unit? :transport))
      (should (dispatcher/naval-unit? :carrier))
      (should (dispatcher/naval-unit? :patrol-boat))
      (should (dispatcher/naval-unit? :destroyer))
      (should (dispatcher/naval-unit? :submarine))
      (should (dispatcher/naval-unit? :battleship)))

    (it "returns false for non-naval units"
      (should-not (dispatcher/naval-unit? :army))
      (should-not (dispatcher/naval-unit? :fighter))
      (should-not (dispatcher/naval-unit? :satellite)))))
