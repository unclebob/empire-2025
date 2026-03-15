(ns empire.game-mechanics.movement.visibility-combatant-map-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.movement.visibility :refer :all]
            [empire.test.utils :refer [build-test-map set-test-unit reset-all-atoms! set-test-player-map! set-test-computer-map! make-initial-test-map set-test-world!]]))
(describe "update-combatant-map"
  (before (reset-all-atoms!))
  (it "reveals all 9 cells around a player unit in center of map"
    (set-test-world! (build-test-map ["~~~~~"
                                             "~~~~~"
                                             "~~A~~"
                                             "~~~~~"
                                             "~~~~~"]))
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (update-combatant-map (test-utils/player-map-atom) :player)
    ;; All 9 cells around [2 2] should be revealed
    (should= {:type :sea} (get-in (test-utils/read-test-state :player-map) [1 1]))
    (should= {:type :sea} (get-in (test-utils/read-test-state :player-map) [2 1]))
    (should= {:type :sea} (get-in (test-utils/read-test-state :player-map) [3 1]))
    (should= {:type :sea} (get-in (test-utils/read-test-state :player-map) [1 2]))
    (should= {:type :land :contents {:type :army :owner :player :hits 1}} (get-in (test-utils/read-test-state :player-map) [2 2]))
    (should= {:type :sea} (get-in (test-utils/read-test-state :player-map) [3 2]))
    (should= {:type :sea} (get-in (test-utils/read-test-state :player-map) [1 3]))
    (should= {:type :sea} (get-in (test-utils/read-test-state :player-map) [2 3]))
    (should= {:type :sea} (get-in (test-utils/read-test-state :player-map) [3 3]))
    ;; Corners should not be revealed
    (should= nil (get-in (test-utils/read-test-state :player-map) [0 0]))
    (should= nil (get-in (test-utils/read-test-state :player-map) [4 0]))
    (should= nil (get-in (test-utils/read-test-state :player-map) [0 4]))
    (should= nil (get-in (test-utils/read-test-state :player-map) [4 4])))

  (it "clamps visibility at map edges for unit in corner"
    (set-test-world! (build-test-map ["A~~~~"
                                             "~~~~~"
                                             "~~~~~"
                                             "~~~~~"
                                             "~~~~~"]))
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (update-combatant-map (test-utils/player-map-atom) :player)
    ;; Cells at and adjacent to [0 0] should be revealed (clamped)
    (should= {:type :land :contents {:type :army :owner :player :hits 1}} (get-in (test-utils/read-test-state :player-map) [0 0]))
    (should= {:type :sea} (get-in (test-utils/read-test-state :player-map) [1 0]))
    (should= {:type :sea} (get-in (test-utils/read-test-state :player-map) [0 1]))
    (should= {:type :sea} (get-in (test-utils/read-test-state :player-map) [1 1]))
    ;; Far cells should not be revealed
    (should= nil (get-in (test-utils/read-test-state :player-map) [2 2])))

  (it "reveals cells around player city"
    (set-test-world! (build-test-map ["~~~~~"
                                             "~~~~~"
                                             "~~O~~"
                                             "~~~~~"
                                             "~~~~~"]))
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (update-combatant-map (test-utils/player-map-atom) :player)
    ;; All 9 cells around [2 2] should be revealed
    (should= {:type :city :city-status :player} (get-in (test-utils/read-test-state :player-map) [2 2]))
    (should= {:type :sea} (get-in (test-utils/read-test-state :player-map) [1 1]))
    (should= {:type :sea} (get-in (test-utils/read-test-state :player-map) [3 3])))

  (it "does nothing when visible-map-atom is nil"
    (set-test-world! (build-test-map ["~~~~~"
                                             "~~~~~"
                                             "~~A~~"
                                             "~~~~~"
                                             "~~~~~"]))
    (set-test-player-map! nil)
    (update-combatant-map (test-utils/player-map-atom) :player)
    (should= nil (test-utils/read-test-state :player-map)))

  (it "works for computer owner"
    (set-test-world! (build-test-map ["~~~~~"
                                             "~~~~~"
                                             "~~a~~"
                                             "~~~~~"
                                             "~~~~~"]))
    (set-test-computer-map! (make-initial-test-map 5 5 nil))
    (update-combatant-map (test-utils/computer-map-atom) :computer)
    ;; All 9 cells around [2 2] should be revealed in computer map
    (should= {:type :land :contents {:type :army :owner :computer :hits 1}} (get-in (test-utils/read-test-state :computer-map) [2 2]))
    (should= {:type :sea} (get-in (test-utils/read-test-state :computer-map) [1 1]))
    (should= {:type :sea} (get-in (test-utils/read-test-state :computer-map) [3 3])))

  (it "reveals 5x5 area for satellite in update-combatant-map"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "##V##"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [4 4] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (update-combatant-map (test-utils/player-map-atom) :player)
    ;; All 25 cells should be visible (satellite radius = 2)
    (doseq [row (range 5)
            col (range 5)]
      (should-not-be-nil (get-in (test-utils/read-test-state :player-map) [row col]))))

  (it "handles multiple units revealing overlapping areas"
    (set-test-world! (build-test-map ["~~~~~~~"
                                             "~~~~~~~"
                                             "~~A~~~~"
                                             "~~~~~~~"
                                             "~~~~A~~"
                                             "~~~~~~~"
                                             "~~~~~~~"]))
    (set-test-player-map! (make-initial-test-map 7 7 nil))
    (update-combatant-map (test-utils/player-map-atom) :player)
    ;; Both units and their surroundings should be visible
    (should= {:type :land :contents {:type :army :owner :player :hits 1}} (get-in (test-utils/read-test-state :player-map) [2 2]))
    (should= {:type :land :contents {:type :army :owner :player :hits 1}} (get-in (test-utils/read-test-state :player-map) [4 4]))
    ;; Overlapping cell [3 3] should be revealed by both
    (should= {:type :sea} (get-in (test-utils/read-test-state :player-map) [3 3]))
    ;; Far corner should not be revealed
    (should= nil (get-in (test-utils/read-test-state :player-map) [6 6]))))

