(ns empire.movement.transport-spec
  (:require
    [empire.atoms :as atoms]
    [empire.game-loop :as game-loop]
    [empire.movement.movement :refer :all]
    [empire.test-utils :refer [build-test-map set-test-unit get-test-unit]]
    [speclj.core :refer :all]))

(describe "transport with armies"
  (it "loads adjacent sentry armies onto transport"
    (reset! atoms/game-map @(build-test-map ["---------"
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
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          army1-coords (:pos (get-test-unit atoms/game-map "A1"))
          army2-coords (:pos (get-test-unit atoms/game-map "A2"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= 2 (:army-count transport)))
      (should= nil (:contents (get-in @atoms/game-map army1-coords)))
      (should= nil (:contents (get-in @atoms/game-map army2-coords)))))

  (it "does not load awake armies onto transport"
    (reset! atoms/game-map @(build-test-map ["---------"
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
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          army-coords (:pos (get-test-unit atoms/game-map "A"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= 0 (:army-count transport 0)))
      (should-not= nil (:contents (get-in @atoms/game-map army-coords)))))

  (it "wakes transport after loading armies if at beach"
    (reset! atoms/game-map @(build-test-map ["---------"
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
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= :awake (:mode transport))
        (should= :transport-at-beach (:reason transport))
        (should= 1 (:army-count transport)))))

  (it "wake-armies-on-transport wakes all armies and sets transport to sentry"
    (reset! atoms/game-map @(build-test-map ["---------"
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

  (it "sleep-armies-on-transport puts armies to sleep and wakes transport"
    (reset! atoms/game-map @(build-test-map ["---------"
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
        (should= 0 (:awake-armies transport)))))

  (it "disembark-army-from-transport removes one army and decrements counts"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 3 :awake-armies 3)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(inc (first transport-coords)) (second transport-coords)]]
      (disembark-army-from-transport transport-coords land-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))
            disembarked (:contents (get-in @atoms/game-map land-coords))]
        (should= 2 (:army-count transport))
        (should= 2 (:awake-armies transport))
        (should= :army (:type disembarked))
        (should= :awake (:mode disembarked)))))

  (it "disembark-army-from-transport wakes transport when last army disembarks"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 1 :awake-armies 1)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(inc (first transport-coords)) (second transport-coords)]]
      (disembark-army-from-transport transport-coords land-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= :awake (:mode transport))
        (should= 0 (:army-count transport)))))

  (it "disembark-army-from-transport wakes transport when no more awake armies remain"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 1)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(inc (first transport-coords)) (second transport-coords)]]
      (disembark-army-from-transport transport-coords land-coords)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= :awake (:mode transport))
        (should= 1 (:army-count transport))
        (should= 0 (:awake-armies transport)))))

  (it "transport wakes up when reaching beach with armies"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T~---"
                                             "-----#---"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          target-coords [(first transport-coords) (inc (second transport-coords))]]
      (set-test-unit atoms/game-map "T" :mode :moving :hits 1 :army-count 1 :target target-coords :steps-remaining 1)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit transport-coords)
      (let [transport (:contents (get-in @atoms/game-map target-coords))]
        (should= :awake (:mode transport))
        (should= :transport-at-beach (:reason transport)))))

  (it "transport does not wake when reaching beach without armies"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T~---"
                                             "-----#---"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          target-coords [(first transport-coords) (inc (second transport-coords))]]
      (set-test-unit atoms/game-map "T" :mode :moving :hits 1 :target target-coords :steps-remaining 1)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit transport-coords)
      (let [transport (:contents (get-in @atoms/game-map target-coords))]
        (should= :awake (:mode transport))
        (should= nil (:reason transport)))))

  (it "completely-surrounded-by-sea? returns true when no adjacent land"
    (reset! atoms/game-map @(build-test-map ["~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~T~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"]))
    (set-test-unit atoms/game-map "T" :mode :moving)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (should (completely-surrounded-by-sea? transport-coords atoms/game-map))))

  (it "completely-surrounded-by-sea? returns false when adjacent to land"
    (reset! atoms/game-map @(build-test-map ["~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~T#~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"]))
    (set-test-unit atoms/game-map "T" :mode :moving)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))]
      (should-not (completely-surrounded-by-sea? transport-coords atoms/game-map))))

  (it "transport wakes with found-land when moving from open sea to land visible"
    (reset! atoms/game-map @(build-test-map ["~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~T~#~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"]))
    ;; Transport at T completely surrounded by sea
    ;; Target at ~ (one right of T) is sea but has land at # (adjacent to target but not to T)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          target-coords [(first transport-coords) (inc (second transport-coords))]]
      (set-test-unit atoms/game-map "T" :mode :moving :hits 1 :army-count 1 :target target-coords :steps-remaining 1)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit transport-coords)
      (let [transport (:contents (get-in @atoms/game-map target-coords))]
        (should= :awake (:mode transport))
        (should= :transport-found-land (:reason transport)))))

  (it "transport does not wake with found-land when already near land before move"
    (reset! atoms/game-map @(build-test-map ["~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~#~~~~~~"
                                             "~~~~T~~~~"
                                             "~~~~~#~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"]))
    ;; Transport at T already has land at # above-left
    ;; Target (one right of T) also near land at # below-right
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          target-coords [(first transport-coords) (inc (second transport-coords))]]
      (set-test-unit atoms/game-map "T" :mode :moving :hits 1 :army-count 1 :target target-coords :steps-remaining 1)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit transport-coords)
      (let [transport (:contents (get-in @atoms/game-map target-coords))]
        ;; Still wakes because it's at beach with armies, but reason should be :transport-at-beach
        (should= :awake (:mode transport))
        (should= :transport-at-beach (:reason transport)))))

  (it "transport wakes with found-land even without armies"
    (reset! atoms/game-map @(build-test-map ["~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~T~#~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"
                                             "~~~~~~~~~"]))
    ;; Transport at T completely surrounded by sea, no armies
    ;; Target (one right of T) is sea but has land at # (adjacent to target but not to T)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          target-coords [(first transport-coords) (inc (second transport-coords))]]
      (set-test-unit atoms/game-map "T" :mode :moving :hits 1 :target target-coords :steps-remaining 1)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (game-loop/move-current-unit transport-coords)
      (let [transport (:contents (get-in @atoms/game-map target-coords))]
        (should= :awake (:mode transport))
        (should= :transport-found-land (:reason transport)))))

  (it "get-active-unit returns synthetic army when transport has awake armies"
    (let [cell {:type :sea :contents {:type :transport :mode :sentry :owner :player :army-count 3 :awake-armies 2}}]
      (let [active (get-active-unit cell)]
        (should= :army (:type active))
        (should= :awake (:mode active))
        (should= true (:aboard-transport active)))))

  (it "get-active-unit returns transport when no awake armies"
    (let [cell {:type :sea :contents {:type :transport :mode :awake :owner :player :army-count 1 :awake-armies 0}}]
      (let [active (get-active-unit cell)]
        (should= :transport (:type active))
        (should= :awake (:mode active)))))

  (it "is-army-aboard-transport? returns true for synthetic army with :aboard-transport"
    (let [army {:type :army :mode :awake :owner :player :aboard-transport true}]
      (should= true (is-army-aboard-transport? army))))

  (it "is-army-aboard-transport? returns falsy for army without :aboard-transport"
    (let [army {:type :army :mode :awake :owner :player :hits 1}]
      (should-not (is-army-aboard-transport? army)))))

(describe "disembark-army-with-target"
  (it "disembarks army and sets it moving toward extended target"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "----#----"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(inc (first transport-coords)) (second transport-coords)]
          extended-target [8 (second transport-coords)]]
      (disembark-army-with-target transport-coords land-coords extended-target)
      (let [transport (:contents (get-in @atoms/game-map transport-coords))
            army (:contents (get-in @atoms/game-map land-coords))]
        (should= 1 (:army-count transport))
        (should= 1 (:awake-armies transport))
        (should= :army (:type army))
        (should= :moving (:mode army))
        (should= extended-target (:target army))
        (should= 0 (:steps-remaining army))))))

(describe "disembark-army-to-explore"
  (it "disembarks army in explore mode"
    (reset! atoms/game-map @(build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(inc (first transport-coords)) (second transport-coords)]]
      (let [result (disembark-army-to-explore transport-coords land-coords)]
        (should= land-coords result)
        (let [transport (:contents (get-in @atoms/game-map transport-coords))
              army (:contents (get-in @atoms/game-map land-coords))]
          (should= 1 (:army-count transport))
          (should= 1 (:awake-armies transport))
          (should= :army (:type army))
          (should= :explore (:mode army))
          (should= #{land-coords} (:visited army)))))))
