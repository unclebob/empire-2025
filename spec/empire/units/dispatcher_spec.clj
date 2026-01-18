(ns empire.units.dispatcher-spec
  (:require [speclj.core :refer :all]
            [empire.units.dispatcher :as dispatcher]))

(describe "unit dispatcher module"
  (describe "speed"
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

  (describe "cost"
    (it "returns correct cost for each unit type"
      (should= 5 (dispatcher/cost :army))
      (should= 10 (dispatcher/cost :fighter))
      (should= 50 (dispatcher/cost :satellite))
      (should= 30 (dispatcher/cost :transport))
      (should= 30 (dispatcher/cost :carrier))
      (should= 15 (dispatcher/cost :patrol-boat))
      (should= 20 (dispatcher/cost :destroyer))
      (should= 20 (dispatcher/cost :submarine))
      (should= 40 (dispatcher/cost :battleship))))

  (describe "hits"
    (it "returns correct hits for each unit type"
      (should= 1 (dispatcher/hits :army))
      (should= 1 (dispatcher/hits :fighter))
      (should= 1 (dispatcher/hits :satellite))
      (should= 1 (dispatcher/hits :transport))
      (should= 8 (dispatcher/hits :carrier))
      (should= 1 (dispatcher/hits :patrol-boat))
      (should= 3 (dispatcher/hits :destroyer))
      (should= 2 (dispatcher/hits :submarine))
      (should= 10 (dispatcher/hits :battleship))))

  (describe "display-char"
    (it "returns correct character for each unit type"
      (should= "A" (dispatcher/display-char :army))
      (should= "F" (dispatcher/display-char :fighter))
      (should= "Z" (dispatcher/display-char :satellite))
      (should= "T" (dispatcher/display-char :transport))
      (should= "C" (dispatcher/display-char :carrier))
      (should= "P" (dispatcher/display-char :patrol-boat))
      (should= "D" (dispatcher/display-char :destroyer))
      (should= "S" (dispatcher/display-char :submarine))
      (should= "B" (dispatcher/display-char :battleship))))

  (describe "visibility-radius"
    (it "returns 1 for most units"
      (should= 1 (dispatcher/visibility-radius :army))
      (should= 1 (dispatcher/visibility-radius :fighter))
      (should= 1 (dispatcher/visibility-radius :transport)))

    (it "returns 2 for satellite"
      (should= 2 (dispatcher/visibility-radius :satellite))))

  (describe "initial-state"
    (it "returns empty map for simple units"
      (should= {} (dispatcher/initial-state :army))
      (should= {} (dispatcher/initial-state :patrol-boat)))

    (it "returns fuel state for fighter"
      (should= {:fuel 32} (dispatcher/initial-state :fighter)))

    (it "returns turns-remaining for satellite"
      (should= {:turns-remaining 50} (dispatcher/initial-state :satellite)))

    (it "returns container state for transport"
      (should= {:army-count 0 :awake-armies 0 :been-to-sea true} (dispatcher/initial-state :transport)))

    (it "returns container state for carrier"
      (should= {:fighter-count 0 :awake-fighters 0} (dispatcher/initial-state :carrier))))

  (describe "can-move-to?"
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
      (should (dispatcher/can-move-to? :destroyer {:type :sea}))
      (should-not (dispatcher/can-move-to? :destroyer {:type :land}))))

  (describe "needs-attention?"
    (it "delegates to appropriate module"
      (should (dispatcher/needs-attention? {:type :army :mode :awake}))
      (should-not (dispatcher/needs-attention? {:type :army :mode :sentry}))
      (should (dispatcher/needs-attention? {:type :satellite :target nil}))
      (should-not (dispatcher/needs-attention? {:type :satellite :target [5 5]}))
      (should (dispatcher/needs-attention? {:type :transport :mode :sentry :awake-armies 1}))
      (should-not (dispatcher/needs-attention? {:type :transport :mode :sentry :awake-armies 0}))))

  (describe "naval-unit?"
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
