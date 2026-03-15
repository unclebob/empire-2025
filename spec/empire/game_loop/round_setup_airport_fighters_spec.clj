(ns empire.game-loop.round-setup-airport-fighters-spec
  (:require [empire.game-mechanics.movement.satellite :as satellite]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.game-mechanics.movement.wake-conditions :as wake]
            [empire.game.loop.round-setup :as setup]
            [empire.game.loop.round-setup.lakes :as lakes]
            [empire.game.loop.round-setup.waking :as waking]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-unit set-test-world! update-test-world!]]
            [speclj.core :refer :all]))
(describe "wake-airport-fighters"
  (before (reset-all-atoms!))

  (it "wakes fighters in player city airport"
    (let [game-map (build-test-map ["O"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :fighter-count] 3)
      (waking/wake-airport-fighters)
      (should= 3 (get-in (test-utils/read-test-state :game-map) [0 0 :awake-fighters]))))

  (it "does NOT wake fighters in computer city"
    (let [game-map (build-test-map ["X"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :fighter-count] 2)
      (waking/wake-airport-fighters)
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :awake-fighters] 0))))

  (it "does nothing when city has no fighters"
    (let [game-map (build-test-map ["O"])]
      (set-test-world! game-map)
      (waking/wake-airport-fighters)
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :awake-fighters] 0)))))

