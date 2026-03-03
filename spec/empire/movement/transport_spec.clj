(ns empire.movement.transport-spec
  (:require [empire.test-utils :as test-utils]
    [empire.containers.ops :as container-ops]
    [empire.game-loop :as game-loop]
    [empire.movement.map-utils :as map-utils]
    [empire.movement.movement :refer :all]
    [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms! set-test-player-map! make-initial-test-map set-test-world!]]
    [speclj.core :refer :all]))

(describe "transport with armies"
  (before (reset-all-atoms!))
  (it "loads adjacent sentry armies onto transport"
    (set-test-world! (build-test-map ["#--"
                                             "AT-"
                                             "-A-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1)
    (set-test-unit (test-utils/game-map-atom) "A1" :mode :sentry :hits 1)
    (set-test-unit (test-utils/game-map-atom) "A2" :mode :sentry :hits 1)
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          army1-coords (:pos (get-test-unit (test-utils/game-map-atom) "A1"))
          army2-coords (:pos (get-test-unit (test-utils/game-map-atom) "A2"))]
      (container-ops/load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= 2 (:army-count transport)))
      (should= nil (:contents (get-in (test-utils/read-test-state :game-map) army1-coords)))
      (should= nil (:contents (get-in (test-utils/read-test-state :game-map) army2-coords)))))

  (it "does not load awake armies onto transport"
    (set-test-world! (build-test-map ["#--"
                                             "AT-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1)
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake :hits 1)
    (set-test-player-map! (make-initial-test-map 2 3 nil))
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          army-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))]
      (container-ops/load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= 0 (:army-count transport 0)))
      (should-not= nil (:contents (get-in (test-utils/read-test-state :game-map) army-coords)))))

  (it "wakes transport after loading armies if at beach"
    (set-test-world! (build-test-map ["#--"
                                             "AT-"
                                             "-#-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1)
    (set-test-unit (test-utils/game-map-atom) "A" :mode :sentry :hits 1)
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (container-ops/load-adjacent-sentry-armies transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= :awake (:mode transport))
        (should= :transport-at-beach (:reason transport))
        (should= 1 (:army-count transport)))))

  (it "wake-armies-on-transport wakes all armies and sets transport to sentry"
    (set-test-world! (build-test-map ["-T-"
                                             "-#-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :awake :hits 1 :army-count 2 :reason :transport-at-beach)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (container-ops/wake-armies-on-transport transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= :sentry (:mode transport))
        (should= nil (:reason transport))
        (should= 2 (:army-count transport))
        (should= 2 (:awake-armies transport)))))

  (it "sleep-armies-on-transport puts armies to sleep and wakes transport"
    (set-test-world! (build-test-map ["-T-"
                                             "-#-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (container-ops/sleep-armies-on-transport transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= :awake (:mode transport))
        (should= nil (:reason transport))
        (should= 2 (:army-count transport))
        (should= 0 (:awake-armies transport)))))

  (it "disembark-army-from-transport removes one army and decrements counts"
    (set-test-world! (build-test-map ["-T-"
                                             "-#-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 3 :awake-armies 3)
    (set-test-player-map! (make-initial-test-map 2 3 nil))
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          land-coords [(first transport-coords) (inc (second transport-coords))]]
      (container-ops/disembark-army-from-transport transport-coords land-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))
            disembarked (:contents (get-in (test-utils/read-test-state :game-map) land-coords))]
        (should= 2 (:army-count transport))
        (should= 2 (:awake-armies transport))
        (should= :army (:type disembarked))
        (should= :awake (:mode disembarked)))))

  (it "disembark-army-from-transport wakes transport when last army disembarks"
    (set-test-world! (build-test-map ["-T-"
                                             "-#-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 1 :awake-armies 1)
    (set-test-player-map! (make-initial-test-map 2 3 nil))
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          land-coords [(first transport-coords) (inc (second transport-coords))]]
      (container-ops/disembark-army-from-transport transport-coords land-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= :awake (:mode transport))
        (should= 0 (:army-count transport)))))

  (it "disembark-army-from-transport wakes transport when no more awake armies remain"
    (set-test-world! (build-test-map ["-T-"
                                             "-#-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 1)
    (set-test-player-map! (make-initial-test-map 2 3 nil))
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          land-coords [(first transport-coords) (inc (second transport-coords))]]
      (container-ops/disembark-army-from-transport transport-coords land-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= :awake (:mode transport))
        (should= 1 (:army-count transport))
        (should= 0 (:awake-armies transport)))))

  (it "transport wakes up when reaching beach with armies"
    (set-test-world! (build-test-map ["-T~-"
                                             "--#-"]))
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          target-coords [(inc (first transport-coords)) (second transport-coords)]]
      (set-test-unit (test-utils/game-map-atom) "T" :mode :moving :hits 1 :army-count 1 :target target-coords :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 2 4 nil))
      (game-loop/move-current-unit transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) target-coords))]
        (should= :awake (:mode transport))
        (should= :transport-at-beach (:reason transport)))))

  (it "transport does not wake when reaching beach without armies"
    (set-test-world! (build-test-map ["-T~-"
                                             "--#-"]))
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          target-coords [(inc (first transport-coords)) (second transport-coords)]]
      (set-test-unit (test-utils/game-map-atom) "T" :mode :moving :hits 1 :target target-coords :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 2 4 nil))
      (game-loop/move-current-unit transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) target-coords))]
        (should= :awake (:mode transport))
        (should= nil (:reason transport)))))

  (it "map-utils/completely-surrounded-by-sea? returns true when no adjacent land"
    (set-test-world! (build-test-map ["~~~"
                                             "~T~"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :moving)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (should (map-utils/completely-surrounded-by-sea? transport-coords (test-utils/game-map-atom)))))

  (it "map-utils/completely-surrounded-by-sea? returns false when adjacent to land"
    (set-test-world! (build-test-map ["~~~"
                                             "~T#"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :moving)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (should-not (map-utils/completely-surrounded-by-sea? transport-coords (test-utils/game-map-atom)))))

  (it "transport wakes with found-land when moving from open sea to land visible"
    (set-test-world! (build-test-map ["~~~#"
                                             "~T~~"
                                             "~~~~"]))
    ;; Transport at T completely surrounded by sea
    ;; Target at ~ (one right of T) is sea but has land at # (adjacent to target but not to T)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          target-coords [(inc (first transport-coords)) (second transport-coords)]]
      (set-test-unit (test-utils/game-map-atom) "T" :mode :moving :hits 1 :army-count 1 :target target-coords :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 3 4 nil))
      (game-loop/move-current-unit transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) target-coords))]
        (should= :awake (:mode transport))
        (should= :transport-found-land (:reason transport)))))

  (it "transport does not wake with found-land when already near land before move"
    (set-test-world! (build-test-map ["#~~~"
                                             "~T~~"
                                             "~~#~"]))
    ;; Transport at T already has land at # above-left
    ;; Target (one right of T) also near land at # below-right
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          target-coords [(inc (first transport-coords)) (second transport-coords)]]
      (set-test-unit (test-utils/game-map-atom) "T" :mode :moving :hits 1 :army-count 1 :target target-coords :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 3 4 nil))
      (game-loop/move-current-unit transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) target-coords))]
        ;; Still wakes because it's at beach with armies, but reason should be :transport-at-beach
        (should= :awake (:mode transport))
        (should= :transport-at-beach (:reason transport)))))

  (it "transport wakes with found-land even without armies"
    (set-test-world! (build-test-map ["~~~#"
                                             "~T~~"
                                             "~~~~"]))
    ;; Transport at T completely surrounded by sea, no armies
    ;; Target (one right of T) is sea but has land at # (adjacent to target but not to T)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          target-coords [(inc (first transport-coords)) (second transport-coords)]]
      (set-test-unit (test-utils/game-map-atom) "T" :mode :moving :hits 1 :target target-coords :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 3 4 nil))
      (game-loop/move-current-unit transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) target-coords))]
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
  (before (reset-all-atoms!))
  (it "disembarks army and sets it moving toward extended target"
    (set-test-world! (build-test-map ["-T-"
                                             "-#-"
                                             "-#-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          land-coords [(first transport-coords) (inc (second transport-coords))]
          extended-target [(first transport-coords) 2]]
      (container-ops/disembark-army-with-target transport-coords land-coords extended-target)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))
            army (:contents (get-in (test-utils/read-test-state :game-map) land-coords))]
        (should= 1 (:army-count transport))
        (should= 1 (:awake-armies transport))
        (should= :army (:type army))
        (should= :moving (:mode army))
        (should= extended-target (:target army))
        (should= 0 (:steps-remaining army))))))

(describe "disembark-army-to-explore"
  (before (reset-all-atoms!))
  (it "disembarks army in explore mode"
    (set-test-world! (build-test-map ["-T-"
                                             "-#-"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 2 :awake-armies 2)
    (set-test-player-map! (make-initial-test-map 2 3 nil))
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          land-coords [(first transport-coords) (inc (second transport-coords))]]
      (let [result (container-ops/disembark-army-to-explore transport-coords land-coords)]
        (should= land-coords result)
        (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))
              army (:contents (get-in (test-utils/read-test-state :game-map) land-coords))]
          (should= 1 (:army-count transport))
          (should= 1 (:awake-armies transport))
          (should= :army (:type army))
          (should= :explore (:mode army))
          (should= #{land-coords} (:visited army)))))))

(describe "transport been-to-sea behavior"
  (before (reset-all-atoms!))

  (it "new transport has :been-to-sea true"
    (set-test-world! (build-test-map ["~T~"
                                             "~#~"]))
    ;; Note: set-test-unit doesn't use initial-state, so we set :been-to-sea explicitly
    ;; In production, new transports are created with initial-state which includes :been-to-sea true
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 1 :been-to-sea true)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
      (should= true (:been-to-sea transport))))

  (it "transport does not wake at subsequent beaches after first beach wake"
    ;; Transport starts at beach, moves along coast to another beach
    (set-test-world! (build-test-map ["~#~~#~"
                                             "~~T~~~"]))
    ;; Transport at beach with :been-to-sea false (already woke at beach before)
    ;; Target is further away so transport doesn't wake at target
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          next-coords [(inc (first transport-coords)) (second transport-coords)]
          far-target [5 (second transport-coords)]]
      (set-test-unit (test-utils/game-map-atom) "T" :mode :moving :hits 1 :army-count 1
                     :been-to-sea false :target far-target :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 2 6 nil))
      (game-loop/move-current-unit transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) next-coords))]
        ;; Should NOT wake - still moving (target not reached, been-to-sea is false)
        (should= :moving (:mode transport))
        (should= false (:been-to-sea transport)))))

  (it "transport sets :been-to-sea true when completely surrounded by sea"
    (set-test-world! (build-test-map ["#~~~"
                                             "~T~~"
                                             "~~~~"]))
    ;; Transport at beach (adjacent to land at [0,0]) moving to open sea
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          target-coords [(inc (first transport-coords)) (second transport-coords)]]
      (set-test-unit (test-utils/game-map-atom) "T" :mode :moving :hits 1 :army-count 1
                     :been-to-sea false :target target-coords :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 3 4 nil))
      (game-loop/move-current-unit transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) target-coords))]
        ;; Should set :been-to-sea true since now completely surrounded by sea
        (should= true (:been-to-sea transport)))))

  (it "transport wakes at beach after going to open sea"
    ;; Transport NOT in open sea (adjacent to land at [0,0]) but :been-to-sea is true
    ;; Moving to beach adjacent to land at [3,1]
    (set-test-world! (build-test-map ["#~~~"
                                             "~T~#"]))
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          target-coords [(inc (first transport-coords)) (second transport-coords)]]
      (set-test-unit (test-utils/game-map-atom) "T" :mode :moving :hits 1 :army-count 1
                     :been-to-sea true :target target-coords :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 2 4 nil))
      (game-loop/move-current-unit transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) target-coords))]
        ;; Should wake with :transport-at-beach and set :been-to-sea false
        (should= :awake (:mode transport))
        (should= :transport-at-beach (:reason transport))
        (should= false (:been-to-sea transport)))))

  (it "transport wakes at first beach when :been-to-sea defaults to true"
    ;; Transport NOT in open sea (adjacent to land at [0,0]) but :been-to-sea defaults to true
    ;; Moving to beach adjacent to land at [3,1]
    (set-test-world! (build-test-map ["#~~~"
                                             "~T~#"]))
    ;; New transport (default :been-to-sea true) moving to beach
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          target-coords [(inc (first transport-coords)) (second transport-coords)]]
      (set-test-unit (test-utils/game-map-atom) "T" :mode :moving :hits 1 :army-count 1
                     :target target-coords :steps-remaining 1)
      (set-test-player-map! (make-initial-test-map 2 4 nil))
      (game-loop/move-current-unit transport-coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) target-coords))]
        ;; Should wake since :been-to-sea defaults to true
        (should= :awake (:mode transport))
        (should= :transport-at-beach (:reason transport))
        (should= false (:been-to-sea transport))))))
