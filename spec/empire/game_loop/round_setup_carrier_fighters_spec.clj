(ns empire.game-loop.round-setup-carrier-fighters-spec
  (:require [empire.game-mechanics.movement.satellite :as satellite]
            [empire.game-mechanics.visibility :as visibility]
            [empire.game-mechanics.movement.wake-conditions :as wake]
            [empire.game.loop.round-setup :as setup]
            [empire.game.loop.round-setup.lakes :as lakes]
            [empire.game.loop.round-setup.waking :as waking]
            [empire.test.utils :as test-utils]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-unit set-test-world! update-test-world!]]
            [speclj.core :refer :all]))
(describe "wake-carrier-fighters"
  (before (reset-all-atoms!))

  (it "wakes fighters on player carrier"
    (let [game-map (build-test-map ["C"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :contents :fighter-count] 4)
      (waking/wake-carrier-fighters)
      (should= 4 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :awake-fighters]))))

  (it "does NOT wake fighters on computer carrier"
    (let [game-map (build-test-map ["c"])]
      (set-test-world! game-map)
      (update-test-world! assoc-in [0 0 :contents :fighter-count] 3)
      (waking/wake-carrier-fighters)
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :awake-fighters] 0))))

  (it "does nothing when carrier has no fighters"
    (let [game-map (build-test-map ["C"])]
      (set-test-world! game-map)
      (waking/wake-carrier-fighters)
      (should= 0 (get-in (test-utils/read-test-state :game-map) [0 0 :contents :awake-fighters] 0)))))
