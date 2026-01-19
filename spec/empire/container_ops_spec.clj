(ns empire.container-ops-spec
  (:require [empire.atoms :as atoms]
            [empire.container-ops :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms!]]
            [speclj.core :refer :all]))

(describe "load-adjacent-sentry-armies"
  (before (reset-all-atoms!))

  (it "loads adjacent sentry armies onto transport"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---#-----"
                                             "---AT----"
                                             "----A----"
                                             "---------"
                                             "---------"
                                             "---------"]))
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
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---#-----"
                                             "---AT----"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1)
    (set-test-unit atoms/game-map "A" :mode :awake :hits 1)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          army-coords (:pos (get-test-unit atoms/game-map "A"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= 0 (:army-count transport 0)))
      (should-not= nil (:contents (get-in @atoms/game-map army-coords)))))

  (it "wakes transport after loading armies if at beach"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---#-----"
                                             "---AT----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1)
    (set-test-unit atoms/game-map "A" :mode :sentry :hits 1)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= :awake (:mode transport))
        (should= :transport-at-beach (:reason transport))
        (should= 1 (:army-count transport))))))

(describe "wake-armies-on-transport"
  (before (reset-all-atoms!))

  (it "wakes all armies and sets transport to sentry"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :awake :hits 1 :army-count 2 :reason :transport-at-beach)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (wake-armies-on-transport transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= :sentry (:mode transport))
        (should= nil (:reason transport))
        (should= 2 (:army-count transport))
        (should= 2 (:awake-armies transport)))))

  (it "clears steps-remaining to end transport's turn"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :awake :hits 1 :army-count 2 :reason :transport-at-beach :steps-remaining 2)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (wake-armies-on-transport transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= 0 (:steps-remaining transport))))))

(describe "sleep-armies-on-transport"
  (before (reset-all-atoms!))

  (it "puts armies to sleep and wakes transport"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
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
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 3 :awake-armies 3)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(inc (first transport-coords)) (second transport-coords)]]
      (disembark-army-from-transport transport-coords land-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))
            disembarked (:contents (get-in @atoms/game-map land-coords))]
        (should= 2 (:army-count transport))
        (should= 2 (:awake-armies transport))
        (should= :army (:type disembarked))
        (should= :awake (:mode disembarked)))))

  (it "wakes transport when last army disembarks"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 1 :awake-armies 1)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(inc (first transport-coords)) (second transport-coords)]]
      (disembark-army-from-transport transport-coords land-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= :awake (:mode transport))
        (should= 0 (:army-count transport)))))

  (it "wakes transport when no more awake armies remain"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 1)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(inc (first transport-coords)) (second transport-coords)]]
      (disembark-army-from-transport transport-coords land-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= :awake (:mode transport))
        (should= 1 (:army-count transport))
        (should= 0 (:awake-armies transport))))))

(describe "disembark-army-with-target"
  (before (reset-all-atoms!))

  (it "disembarks army and sets it moving toward extended target"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "----#----"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(inc (first transport-coords)) (second transport-coords)]
          target-coords [(+ 4 (first transport-coords)) (second transport-coords)]]
      (disembark-army-with-target transport-coords land-coords target-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))
            army (:contents (get-in @atoms/game-map land-coords))]
        (should= 1 (:army-count transport))
        (should= 1 (:awake-armies transport))
        (should= :army (:type army))
        (should= :moving (:mode army))
        (should= target-coords (:target army))
        (should= 0 (:steps-remaining army))))))

(describe "disembark-army-to-explore"
  (before (reset-all-atoms!))

  (it "disembarks army in explore mode"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(inc (first transport-coords)) (second transport-coords)]
          result (disembark-army-to-explore transport-coords land-coords)]
      (should= land-coords result)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))
            army (:contents (get-in @atoms/game-map land-coords))]
        (should= 1 (:army-count transport))
        (should= 1 (:awake-armies transport))
        (should= :army (:type army))
        (should= :explore (:mode army))
        (should= #{land-coords} (:visited army))))))

(describe "wake-fighters-on-carrier"
  (before (reset-all-atoms!))

  (it "wakes all fighters and sets carrier to sentry"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----C----"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
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
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----C----"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
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
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----C~---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 2 :awake-fighters 2)
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          adjacent-cell [(first carrier-coords) (inc (second carrier-coords))]
          target-coords [(first carrier-coords) (+ 2 (second carrier-coords))]]
      (launch-fighter-from-carrier carrier-coords target-coords)
      (let [carrier (:contents (get-in @atoms/game-map carrier-coords))
            launched-fighter (:contents (get-in @atoms/game-map adjacent-cell))]
        (should= 1 (:fighter-count carrier))
        (should= 1 (:awake-fighters carrier))
        (should= :fighter (:type launched-fighter))
        (should= :moving (:mode launched-fighter))
        (should= target-coords (:target launched-fighter)))))

  (it "keeps carrier in sentry mode after last fighter launches"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----C~---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          target-coords [(first carrier-coords) (+ 2 (second carrier-coords))]]
      (launch-fighter-from-carrier carrier-coords target-coords)
      (let [carrier (:contents (get-in @atoms/game-map carrier-coords))]
        (should= :sentry (:mode carrier))
        (should= 0 (:fighter-count carrier)))))

  (it "sets steps-remaining to speed minus one"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----C~---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "C" :mode :sentry :hits 8 :fighter-count 1 :awake-fighters 1)
    (let [carrier-coords (:pos (get-test-unit atoms/game-map "C"))
          adjacent-cell [(first carrier-coords) (inc (second carrier-coords))]
          target-coords [(first carrier-coords) (+ 2 (second carrier-coords))]]
      (launch-fighter-from-carrier carrier-coords target-coords)
      (let [fighter (:contents (get-in @atoms/game-map adjacent-cell))]
        (should= 7 (:steps-remaining fighter))))))

(describe "launch-fighter-from-airport"
  (before (reset-all-atoms!))

  (it "removes awake fighter from airport and places it moving"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----O#---"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (swap! atoms/game-map assoc-in [4 4 :fighter-count] 2)
    (swap! atoms/game-map assoc-in [4 4 :awake-fighters] 2)
    (launch-fighter-from-airport [4 4] [4 6])
    (let [city (get-in @atoms/game-map [4 4])
          fighter (:contents city)]
      (should= 1 (:fighter-count city))
      (should= 1 (:awake-fighters city))
      (should= :fighter (:type fighter))
      (should= :moving (:mode fighter))
      (should= [4 6] (:target fighter)))))
