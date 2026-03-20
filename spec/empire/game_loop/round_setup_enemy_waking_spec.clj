(ns empire.game-loop.round-setup-enemy-waking-spec
  (:require [empire.game-mechanics.movement.satellite :as satellite]
            [empire.game-mechanics.visibility :as visibility]
            [empire.game-mechanics.movement.wake-conditions :as wake]
            [empire.game.loop.round-setup :as setup]
            [empire.game.loop.round-setup.lakes :as lakes]
            [empire.game.loop.round-setup.waking :as waking]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-unit set-test-world! update-test-world!]]
            [speclj.core :refer :all]))
(describe "wake-sentries-seeing-enemy"
  (before (reset-all-atoms!))

  (it "wakes player sentry that sees enemy"
    (let [game-map (build-test-map ["Da"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "D" :mode :sentry)
      (with-redefs [wake/enemy-unit-visible? (fn [_ _ _] true)]
        (waking/wake-sentries-seeing-enemy))
      (should= :awake (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode]))
      (should= :enemy-spotted (get-in (test-utils/read-test-state :game-map) [0 0 :contents :reason]))))

  (it "does NOT wake sentry that sees no enemy"
    (let [game-map (build-test-map ["D~"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "D" :mode :sentry)
      (with-redefs [wake/enemy-unit-visible? (fn [_ _ _] false)]
        (waking/wake-sentries-seeing-enemy))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode]))))

  (it "does NOT wake computer sentries"
    (let [game-map (build-test-map ["dA"])]
      (set-test-world! game-map)
      (set-test-unit (test-utils/game-map-atom) "d" :mode :sentry)
      (with-redefs [wake/enemy-unit-visible? (fn [_ _ _] true)]
        (waking/wake-sentries-seeing-enemy))
      (should= :sentry (get-in (test-utils/read-test-state :game-map) [0 0 :contents :mode])))))
