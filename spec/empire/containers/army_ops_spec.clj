(ns empire.containers.army-ops-spec
  (:require [empire.test.utils :as test-utils]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.ops :refer :all]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.test.utils :refer [build-test-map get-test-unit reset-all-atoms! set-test-unit set-test-world! update-test-world!]]
            [speclj.core :refer :all]))
(describe "non-full-transport?"
  (it "true for transport with space"
    (should (non-full-transport? {:type :transport :hits 1 :army-count 0})))
  (it "false for full transport"
    (should-not (non-full-transport? {:type :transport :hits 1 :army-count 6})))
  (it "false for non-transport"
    (should-not (non-full-transport? {:type :destroyer :hits 3})))
  (it "false for nil"
    (should-not (non-full-transport? nil))))

(describe "load-adjacent-sentry-armies"
  (before (reset-all-atoms!))

  (it "loads adjacent sentry armies onto transport"
    (set-test-world! (build-test-map ["--#--"
                                            "--AT-"
                                            "---A-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1)
    (set-test-unit (test-utils/game-map-atom) "A1" :mode :sentry :hits 1)
    (set-test-unit (test-utils/game-map-atom) "A2" :mode :sentry :hits 1)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          army1-coords (:pos (get-test-unit (test-utils/game-map-atom) "A1"))
          army2-coords (:pos (get-test-unit (test-utils/game-map-atom) "A2"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= 2 (:army-count transport)))
      (should= nil (:contents (get-in (test-utils/read-test-state :game-map) army1-coords)))
      (should= nil (:contents (get-in (test-utils/read-test-state :game-map) army2-coords)))))

  (it "does not load awake armies onto transport"
    (set-test-world! (build-test-map ["-#--"
                                            "-AT-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1)
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake :hits 1)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          army-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= 0 (:army-count transport 0)))
      (should-not= nil (:contents (get-in (test-utils/read-test-state :game-map) army-coords)))))

  (it "does not load non-army units onto transport"
    (set-test-world! (build-test-map ["-#--"
                                            "-DT-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1)
    (set-test-unit (test-utils/game-map-atom) "D" :mode :sentry :hits 1)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= 0 (:army-count transport 0)))))

  (it "loads army at map edge onto transport at edge"
    ;; Transport at [0 0] — army at [1 0] or [0 1]
    ;; This kills mutations on bounds-check arithmetic (+ -> -)
    (set-test-world! (build-test-map ["TA-"
                                            "~#-"]))
    ;; After transpose: T at [0 0], A at [1 0]
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1)
    (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry :hits 1)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= 1 (:army-count transport)))))

  (it "loads army at row-zero boundary (L27)"
    ;; Transport at [0,1], army at [0,0] — nx=0 kills >= -> > and 0 -> 1
    (set-test-world! (build-test-map ["A"
                                            "T"
                                            "#"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1)
    (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry :hits 1)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= 1 (:army-count transport)))))

  (it "wakes transport after loading armies if at beach"
    (set-test-world! (build-test-map ["-#---"
                                            "-AT--"
                                            "--#--"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1)
    (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry :hits 1)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= :awake (:mode transport))
        (should= :transport-at-beach (:reason transport))
        (should= 1 (:army-count transport)))))

  (it "does nothing when transport is already full"
    (set-test-world! (build-test-map ["-#--"
                                            "-AT-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 6)
    (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry :hits 1)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          army-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))]
      (load-adjacent-sentry-armies transport-coords)
      (should-not-be-nil (:contents (get-in (test-utils/read-test-state :game-map) army-coords)))))

  (it "does not load enemy sentry armies"
    (set-test-world! (build-test-map ["-#--"
                                            "-At-"]))
    (set-test-unit (test-utils/game-map-atom) "t" :mode :sentry :hits 1)
    (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry :hits 1)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "t"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= 0 (:army-count transport 0)))))

  (it "does not wake transport in open sea with pre-loaded armies"
    (set-test-world! (build-test-map ["~~~"
                                            "~T~"
                                            "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 1)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= 1 (:army-count transport))
        (should= :sentry (:mode transport))))))

(describe "wake-armies-on-transport"
  (before (reset-all-atoms!))

  (it "wakes all armies and sets transport to sentry"
    (set-test-world! (build-test-map ["-T-"
                                            "-#-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :awake :hits 1 :army-count 2 :reason :transport-at-beach)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (wake-armies-on-transport transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= :sentry (:mode transport))
        (should= nil (:reason transport))
        (should= 2 (:army-count transport))
        (should= 2 (:awake-armies transport)))))

  (it "clears steps-remaining to end transport's turn"
    (set-test-world! (build-test-map ["-T-"
                                            "-#-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :awake :hits 1 :army-count 2 :reason :transport-at-beach :steps-remaining 2)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (wake-armies-on-transport transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= 0 (:steps-remaining transport))))))

(describe "sleep-armies-on-transport"
  (before (reset-all-atoms!))

  (it "puts armies to sleep and wakes transport"
    (set-test-world! (build-test-map ["-T-"
                                            "-#-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (sleep-armies-on-transport transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= :awake (:mode transport))
        (should= nil (:reason transport))
        (should= 2 (:army-count transport))
        (should= 0 (:awake-armies transport))))))

(describe "disembark-army-from-transport"
  (before (reset-all-atoms!))

  (it "removes one army and decrements counts"
    (set-test-world! (build-test-map ["-T-"
                                            "-#-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 3 :awake-armies 3)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          land-coords [(first transport-coords) (inc (second transport-coords))]]
      (disembark-army-from-transport transport-coords land-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))
            disembarked (:contents (get-in (test-utils/read-test-state :game-map) land-coords))]
        (should= 2 (:army-count transport))
        (should= 2 (:awake-armies transport))
        (should= :army (:type disembarked))
        (should= :awake (:mode disembarked))
        (should= 1 (:hits disembarked))
        (should= (config/unit-speed :army) (:steps-remaining disembarked)))))

  (it "wakes transport when last army disembarks"
    (set-test-world! (build-test-map ["-T-"
                                            "-#-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 1 :awake-armies 1)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          land-coords [(first transport-coords) (inc (second transport-coords))]]
      (disembark-army-from-transport transport-coords land-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= :awake (:mode transport))
        (should= 0 (:army-count transport)))))

  (it "wakes transport when no more awake armies remain"
    (set-test-world! (build-test-map ["-T-"
                                            "-#-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 1)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          land-coords [(first transport-coords) (inc (second transport-coords))]]
      (disembark-army-from-transport transport-coords land-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= :awake (:mode transport))
        (should= 1 (:army-count transport))
        (should= 0 (:awake-armies transport))))))

(describe "disembark-army-with-target"
  (before (reset-all-atoms!))

  (it "disembarks army and sets it moving toward extended target"
    (set-test-world! (build-test-map ["-T---"
                                            "-#---"
                                            "-----"
                                            "-----"
                                            "-#---"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          land-coords [(second transport-coords) (inc (first transport-coords))]
          target-coords [(second transport-coords) (+ 4 (first transport-coords))]]
      (disembark-army-with-target transport-coords land-coords target-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))
            army (:contents (get-in (test-utils/read-test-state :game-map) land-coords))]
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
    (set-test-world! (build-test-map ["-T-"
                                            "-#-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          land-coords [(second transport-coords) (inc (first transport-coords))]
          result (disembark-army-to-explore transport-coords land-coords)]
      (should= land-coords result)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))
            army (:contents (get-in (test-utils/read-test-state :game-map) land-coords))]
        (should= 1 (:army-count transport))
        (should= 1 (:awake-armies transport))
        (should= :army (:type army))
        (should= :explore (:mode army))
        (should= 1 (:hits army))
        (should= (config/unit-speed :army) (:steps-remaining army))
        (should= config/explore-steps (:explore-steps army))
        (should= #{land-coords} (:visited army))))))

(describe "remove-army-from-transport"
  (before (reset-all-atoms!))

  (it "decrements army-count and awake-armies"
    (set-test-world! (build-test-map ["-T-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 3 :awake-armies 2)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (remove-army-from-transport transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= 2 (:army-count transport))
        (should= 1 (:awake-armies transport)))))

  (it "wakes transport when no more awake armies remain"
    (set-test-world! (build-test-map ["-T-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 1 :reason :transport-at-beach)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (remove-army-from-transport transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= :awake (:mode transport))
        (should-be-nil (:reason transport))
        (should= 1 (:army-count transport))
        (should= 0 (:awake-armies transport)))))

  (it "does not wake transport when awake armies remain"
    (set-test-world! (build-test-map ["-T-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 3 :awake-armies 2 :reason :transport-at-beach)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (remove-army-from-transport transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= :sentry (:mode transport))
        (should= :transport-at-beach (:reason transport))
        (should= 2 (:army-count transport))
        (should= 1 (:awake-armies transport))))))
