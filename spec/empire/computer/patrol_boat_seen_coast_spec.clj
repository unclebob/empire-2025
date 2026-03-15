(ns empire.computer.patrol-boat-seen-coast-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.ship :as ship]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.test.utils :as tu]))
(describe "seen-coast atom"
  (before (tu/reset-all-atoms!))

  (it "starts as an empty set"
    (should= #{} (test-utils/read-test-state :seen-coast)))

  (it "is reset to empty set by reset-all-atoms!"
    (test-utils/set-test-state! :seen-coast #{[3 4] [5 6]})
    (tu/reset-all-atoms!)
    (should= #{} (test-utils/read-test-state :seen-coast))))
