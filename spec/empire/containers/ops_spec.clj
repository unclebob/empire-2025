(ns empire.containers.ops-spec
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.containers.ops :refer :all]
            [empire.containers.helpers :as uc]
            [empire.test-utils :refer [build-test-map get-test-unit reset-all-atoms! set-test-unit]]
            [speclj.core :refer :all]))

(describe "load-adjacent-sentry-armies"
  (before (reset-all-atoms!))

  (it "loads adjacent sentry armies onto transport"
    (reset! atoms/game-map (build-test-map ["--#--"
                                            "--AT-"
                                            "---A-"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1)
    (set-test-unit atoms/game-map "A1" :mode :sentry :hits 1)
    (set-test-unit atoms/game-map "A2" :mode :sentry :hits 1)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          army1-coords (:pos (get-test-unit atoms/game-map "A1"))
          army2-coords (:pos (get-test-unit atoms/game-map "A2"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= 2 (:army-count transport)))
      (should= nil (:contents (get-in @atoms/game-map army1-coords)))
      (should= nil (:contents (get-in @atoms/game-map army2-coords)))))

  (it "does not load awake armies onto transport"
    (reset! atoms/game-map (build-test-map ["-#--"
                                            "-AT-"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1)
    (set-test-unit atoms/game-map "A" :mode :awake :hits 1)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          army-coords (:pos (get-test-unit atoms/game-map "A"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= 0 (:army-count transport 0)))
      (should-not= nil (:contents (get-in @atoms/game-map army-coords)))))

  (it "does not load non-army units onto transport"
    (reset! atoms/game-map (build-test-map ["-#--"
                                            "-DT-"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1)
    (set-test-unit atoms/game-map "D" :mode :sentry :hits 1)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= 0 (:army-count transport 0)))))

  (it "loads army at map edge onto transport at edge"
    ;; Transport at [0 0] — army at [1 0] or [0 1]
    ;; This kills mutations on bounds-check arithmetic (+ -> -)
    (reset! atoms/game-map (build-test-map ["TA-"
                                            "~#-"]))
    ;; After transpose: T at [0 0], A at [1 0]
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1)
    (set-test-unit atoms/game-map "A" :mode :sentry :hits 1)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= 1 (:army-count transport)))))

  (it "wakes transport after loading armies if at beach"
    (reset! atoms/game-map (build-test-map ["-#---"
                                            "-AT--"
                                            "--#--"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1)
    (set-test-unit atoms/game-map "A" :mode :sentry :hits 1)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= :awake (:mode transport))
        (should= :transport-at-beach (:reason transport))
        (should= 1 (:army-count transport)))))

  (it "does nothing when transport is already full"
    (reset! atoms/game-map (build-test-map ["-#--"
                                            "-AT-"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 6)
    (set-test-unit atoms/game-map "A" :mode :sentry :hits 1)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          army-coords (:pos (get-test-unit atoms/game-map "A"))]
      (load-adjacent-sentry-armies transport-coords)
      (should-not-be-nil (:contents (get-in @atoms/game-map army-coords)))))

  (it "does not load enemy sentry armies"
    (reset! atoms/game-map (build-test-map ["-#--"
                                            "-At-"]))
    (set-test-unit atoms/game-map "t" :mode :sentry :hits 1)
    (set-test-unit atoms/game-map "A" :mode :sentry :hits 1)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "t"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= 0 (:army-count transport 0)))))

  (it "does not wake transport in open sea with pre-loaded armies"
    (reset! atoms/game-map (build-test-map ["~~~"
                                            "~T~"
                                            "~~~"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 1)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= 1 (:army-count transport))
        (should= :sentry (:mode transport))))))

(describe "wake-armies-on-transport"
  (before (reset-all-atoms!))

  (it "wakes all armies and sets transport to sentry"
    (reset! atoms/game-map (build-test-map ["-T-"
                                            "-#-"]))
    (set-test-unit atoms/game-map "T" :mode :awake :hits 1 :army-count 2 :reason :transport-at-beach)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (wake-armies-on-transport transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= :sentry (:mode transport))
        (should= nil (:reason transport))
        (should= 2 (:army-count transport))
        (should= 2 (:awake-armies transport)))))

  (it "clears steps-remaining to end transport's turn"
    (reset! atoms/game-map (build-test-map ["-T-"
                                            "-#-"]))
    (set-test-unit atoms/game-map "T" :mode :awake :hits 1 :army-count 2 :reason :transport-at-beach :steps-remaining 2)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (wake-armies-on-transport transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= 0 (:steps-remaining transport))))))

(describe "sleep-armies-on-transport"
  (before (reset-all-atoms!))

  (it "puts armies to sleep and wakes transport"
    (reset! atoms/game-map (build-test-map ["-T-"
                                            "-#-"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (sleep-armies-on-transport transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= :awake (:mode transport))
        (should= nil (:reason transport))
        (should= 2 (:army-count transport))
        (should= 0 (:awake-armies transport))))))

(describe "disembark-army-from-transport"
  (before (reset-all-atoms!))

  (it "removes one army and decrements counts"
    (reset! atoms/game-map (build-test-map ["-T-"
                                            "-#-"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 3 :awake-armies 3)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(first transport-coords) (inc (second transport-coords))]]
      (disembark-army-from-transport transport-coords land-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))
            disembarked (:contents (get-in @atoms/game-map land-coords))]
        (should= 2 (:army-count transport))
        (should= 2 (:awake-armies transport))
        (should= :army (:type disembarked))
        (should= :awake (:mode disembarked))
        (should= 1 (:hits disembarked))
        (should= (config/unit-speed :army) (:steps-remaining disembarked)))))

  (it "wakes transport when last army disembarks"
    (reset! atoms/game-map (build-test-map ["-T-"
                                            "-#-"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 1 :awake-armies 1)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(first transport-coords) (inc (second transport-coords))]]
      (disembark-army-from-transport transport-coords land-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= :awake (:mode transport))
        (should= 0 (:army-count transport)))))

  (it "wakes transport when no more awake armies remain"
    (reset! atoms/game-map (build-test-map ["-T-"
                                            "-#-"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 1)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(first transport-coords) (inc (second transport-coords))]]
      (disembark-army-from-transport transport-coords land-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= :awake (:mode transport))
        (should= 1 (:army-count transport))
        (should= 0 (:awake-armies transport))))))

(describe "disembark-army-with-target"
  (before (reset-all-atoms!))

  (it "disembarks army and sets it moving toward extended target"
    (reset! atoms/game-map (build-test-map ["-T---"
                                            "-#---"
                                            "-----"
                                            "-----"
                                            "-#---"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(second transport-coords) (inc (first transport-coords))]
          target-coords [(second transport-coords) (+ 4 (first transport-coords))]]
      (disembark-army-with-target transport-coords land-coords target-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))
            army (:contents (get-in @atoms/game-map land-coords))]
        (should= 1 (:army-count transport))
        (should= 1 (:awake-armies transport))
        (should= :army (:type army))
        (should= :moving (:mode army))
        (should= 1 (:hits army))
        (should= target-coords (:target army))
        (should= 0 (:steps-remaining army))))))

(describe "disembark-army-to-explore"
  (before (reset-all-atoms!))

  (it "disembarks army in explore mode"
    (reset! atoms/game-map (build-test-map ["-T-"
                                            "-#-"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(second transport-coords) (inc (first transport-coords))]
          result (disembark-army-to-explore transport-coords land-coords)]
      (should= land-coords result)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))
            army (:contents (get-in @atoms/game-map land-coords))]
        (should= 1 (:army-count transport))
        (should= 1 (:awake-armies transport))
        (should= :army (:type army))
        (should= :explore (:mode army))
        (should= 1 (:hits army))
        (should= (config/unit-speed :army) (:steps-remaining army))
        (should= config/explore-steps (:explore-steps army))
        (should= #{land-coords} (:visited army))))))

(describe "wake-fighters-on-carrier"
  (before (reset-all-atoms!))

  (it "wakes all fighters and sets carrier to sentry"
    (reset! atoms/game-map (build-test-map ["-C-"]))
    (set-test-unit atoms/game-map "C" :mode :awake :hits 8 :fighter-count 2)
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))]
      (wake-fighters-on-carrier carrier-coords)
      (let [carrier (:contents (get-in @atoms/game-map carrier-coords))]
        (should= :sentry (:mode carrier))
        (should= 2 (:fighter-count carrier))
        (should= 2 (:awake-fighters carrier))))))

(describe "sleep-fighters-on-carrier"
  (before (reset-all-atoms!))

  (it "puts fighters to sleep and wakes carrier"
    (reset! atoms/game-map (build-test-map ["-C-"]))
    (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 2 :awake-fighters 2)
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))]
      (sleep-fighters-on-carrier carrier-coords)
      (let [carrier (:contents (get-in @atoms/game-map carrier-coords))]
        (should= :awake (:mode carrier))
        (should= 2 (:fighter-count carrier))
        (should= 0 (:awake-fighters carrier))))))

(describe "launch-fighter-from-carrier"
  (before (reset-all-atoms!))

  (it "removes fighter and places it at adjacent cell"
    (reset! atoms/game-map (build-test-map ["-C~-"]))
    (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 2 :awake-fighters 2)
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          adjacent-cell [(inc (first carrier-coords)) (second carrier-coords)]
          target-coords [(+ 2 (first carrier-coords)) (second carrier-coords)]]
      (launch-fighter-from-carrier carrier-coords target-coords)
      (let [carrier (:contents (get-in @atoms/game-map carrier-coords))
            launched-fighter (:contents (get-in @atoms/game-map adjacent-cell))]
        (should= 1 (:fighter-count carrier))
        (should= 1 (:awake-fighters carrier))
        (should= :fighter (:type launched-fighter))
        (should= :moving (:mode launched-fighter))
        (should= target-coords (:target launched-fighter)))))

  (it "keeps carrier in sentry mode after last fighter launches"
    (reset! atoms/game-map (build-test-map ["-C~-"]))
    (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          target-coords [(+ 2 (first carrier-coords)) (second carrier-coords)]]
      (launch-fighter-from-carrier carrier-coords target-coords)
      (let [carrier (:contents (get-in @atoms/game-map carrier-coords))]
        (should= :sentry (:mode carrier))
        (should= 0 (:fighter-count carrier)))))

  (it "launches fighter toward target in negative x direction"
    (reset! atoms/game-map (build-test-map ["-~C-"]))
    ;; After transpose: C at [2 0], ~ at [1 0]
    (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          target-coords [(- (first carrier-coords) 2) (second carrier-coords)]
          expected-step [(dec (first carrier-coords)) (second carrier-coords)]
          result (launch-fighter-from-carrier carrier-coords target-coords)]
      (should= expected-step result)
      (let [fighter (:contents (get-in @atoms/game-map expected-step))]
        (should= :fighter (:type fighter))
        (should= target-coords (:target fighter)))))

  (it "launches fighter toward target in y direction"
    (reset! atoms/game-map (build-test-map ["--"
                                             "-C"
                                             "-~"
                                             "--"]))
    ;; After transpose: C at [1 1], ~ at [1 2]
    (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          target-coords [(first carrier-coords) (+ 2 (second carrier-coords))]
          expected-step [(first carrier-coords) (inc (second carrier-coords))]
          result (launch-fighter-from-carrier carrier-coords target-coords)]
      (should= expected-step result)
      (let [fighter (:contents (get-in @atoms/game-map expected-step))]
        (should= :fighter (:type fighter))
        (should= 1 (:hits fighter)))))

  (it "launches fighter toward target in negative y direction"
    ;; Carrier at y=3, target at y=1, so dy should be -1
    ;; This kills the mutant (+ ty cy) because ty=1, cy=3, (+ 1 3)=4>0 gives dy=1 (wrong)
    (reset! atoms/game-map (build-test-map ["----"
                                             "----"
                                             "----"
                                             "-~C-"
                                             "----"]))
    ;; After transpose: C at [2 3], ~ at [1 3]
    (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          ;; Target at same x, y=1 (negative y direction)
          target-coords [(first carrier-coords) 1]
          expected-step [(first carrier-coords) (dec (second carrier-coords))]
          result (launch-fighter-from-carrier carrier-coords target-coords)]
      (should= expected-step result)
      (let [fighter (:contents (get-in @atoms/game-map expected-step))]
        (should= :fighter (:type fighter))
        (should= 1 (:hits fighter))
        (should= target-coords (:target fighter)))))

  (it "launches fighter along x-axis when target at same y"
    ;; Kills M27: (- ty cy) → (+ ty cy) in zero? check
    ;; When carrier at y=1 and target at y=1, (+ 1 1) = 2 ≠ 0 makes mutant give wrong dy
    (reset! atoms/game-map (build-test-map ["---"
                                             "-C~"
                                             "---"]))
    ;; After transpose: C at [1 1], ~ at [2 1]
    (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          target-coords [(+ 2 (first carrier-coords)) (second carrier-coords)]
          expected-step [(inc (first carrier-coords)) (second carrier-coords)]
          result (launch-fighter-from-carrier carrier-coords target-coords)]
      (should= expected-step result)
      (let [fighter (:contents (get-in @atoms/game-map expected-step))]
        (should= :fighter (:type fighter))
        (should= (second carrier-coords) (second (:target fighter))))))

  (it "sets steps-remaining to speed minus one"
    (reset! atoms/game-map (build-test-map ["-C~-"]))
    (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          adjacent-cell [(inc (first carrier-coords)) (second carrier-coords)]
          target-coords [(+ 2 (first carrier-coords)) (second carrier-coords)]]
      (launch-fighter-from-carrier carrier-coords target-coords)
      (let [fighter (:contents (get-in @atoms/game-map adjacent-cell))]
        (should= 7 (:steps-remaining fighter))))))

(describe "launch-fighter-from-airport"
  (before (reset-all-atoms!))

  (it "removes awake fighter from airport and places it moving"
    (reset! atoms/game-map (build-test-map ["-O#-"]))
    (swap! atoms/game-map assoc-in [1 0 :fighter-count] 2)
    (swap! atoms/game-map assoc-in [1 0 :awake-fighters] 2)
    (launch-fighter-from-airport [1 0] [3 0])
    (let [city (get-in @atoms/game-map [1 0])
          fighter (:contents city)]
      (should= 1 (:fighter-count city))
      (should= 1 (:awake-fighters city))
      (should= :fighter (:type fighter))
      (should= :moving (:mode fighter))
      (should= [3 0] (:target fighter))
      (should= 1 (:hits fighter)))))

(describe "remove-army-from-transport"
  (before (reset-all-atoms!))

  (it "decrements army-count and awake-armies"
    (reset! atoms/game-map (build-test-map ["-T-"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 3 :awake-armies 2)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (remove-army-from-transport transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= 2 (:army-count transport))
        (should= 1 (:awake-armies transport)))))

  (it "wakes transport when no more awake armies remain"
    (reset! atoms/game-map (build-test-map ["-T-"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 1 :reason :transport-at-beach)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (remove-army-from-transport transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= :awake (:mode transport))
        (should-be-nil (:reason transport))
        (should= 1 (:army-count transport))
        (should= 0 (:awake-armies transport)))))

  (it "does not wake transport when awake armies remain"
    (reset! atoms/game-map (build-test-map ["-T-"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 3 :awake-armies 2 :reason :transport-at-beach)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (remove-army-from-transport transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= :sentry (:mode transport))
        (should= :transport-at-beach (:reason transport))
        (should= 2 (:army-count transport))
        (should= 1 (:awake-armies transport))))))

(describe "launch-ship-from-shipyard"
  (before (reset-all-atoms!))

  (it "launches ship at city coords when no launch-pos given"
    (reset! atoms/game-map (build-test-map ["-O-"]))
    (swap! atoms/game-map assoc-in [1 0 :shipyard]
           [{:type :destroyer :hits 3}])
    (launch-ship-from-shipyard [1 0] 0)
    (let [city (get-in @atoms/game-map [1 0])
          ship (:contents city)]
      (should= [] (:shipyard city))
      (should-not-be-nil ship)
      (should= :destroyer (:type ship))
      (should= :player (:owner ship))
      (should= 3 (:hits ship))
      (should= :awake (:mode ship))))

  (it "launches ship at separate launch-pos when provided"
    (reset! atoms/game-map (build-test-map ["-O~-"]))
    (swap! atoms/game-map assoc-in [1 0 :shipyard]
           [{:type :destroyer :hits 3}])
    (launch-ship-from-shipyard [1 0] 0 [2 0])
    (let [city (get-in @atoms/game-map [1 0])
          ship (:contents (get-in @atoms/game-map [2 0]))]
      (should= [] (:shipyard city))
      (should-be-nil (:contents city))
      (should-not-be-nil ship)
      (should= :destroyer (:type ship))
      (should= :player (:owner ship)))))
