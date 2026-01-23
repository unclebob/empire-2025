(ns empire.movement.visibility-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.movement.visibility :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit reset-all-atoms! make-initial-test-map]]))

(describe "update-cell-visibility"
  (before (reset-all-atoms!))
  (it "reveals cells near player-owned units"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----A#---"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (reset! atoms/player-map (make-initial-test-map 9 9 nil))
    (update-cell-visibility [4 4] :player)
    ;; Check that the unit's cell and neighbors are revealed
    (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake}} (get-in @atoms/player-map [4 4]))
    (should= {:type :land} (get-in @atoms/player-map [4 5]))
    (should= {:type :land} (get-in @atoms/player-map [5 4]))
    ;; Check that distant cells are not revealed
    (should= nil (get-in @atoms/player-map [0 0]))
    (should= nil (get-in @atoms/player-map [8 8])))

  (it "reveals cells near computer-owned units"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----a#---"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (reset! atoms/computer-map (make-initial-test-map 9 9 nil))
    (update-cell-visibility [4 4] :computer)
    ;; Check that the unit's cell and neighbors are revealed
    (should= {:type :land :contents {:type :army :owner :computer :hits 1}} (get-in @atoms/computer-map [4 4]))
    (should= {:type :land} (get-in @atoms/computer-map [4 5]))
    (should= {:type :land} (get-in @atoms/computer-map [5 4]))
    ;; Check that distant cells are not revealed
    (should= nil (get-in @atoms/computer-map [0 0])))

  (it "does nothing when visible-map-atom contains nil"
    (reset! atoms/game-map (build-test-map ["A#"]))
    (reset! atoms/player-map nil)
    (update-cell-visibility [0 0] :player)
    (should= nil @atoms/player-map))

  (it "clamps visibility at map edges"
    (reset! atoms/game-map (build-test-map ["A##"
                                             "###"
                                             "###"]))
    (reset! atoms/player-map (make-initial-test-map 3 3 nil))
    (update-cell-visibility [0 0] :player)
    ;; Should reveal 4 cells (2x2 in top-left corner)
    (should (get-in @atoms/player-map [0 0]))
    (should (get-in @atoms/player-map [0 1]))
    (should (get-in @atoms/player-map [1 0]))
    (should (get-in @atoms/player-map [1 1]))
    ;; Should not reveal far cells
    (should= nil (get-in @atoms/player-map [2 2])))

  (it "reveals two rectangular rings for satellites"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "##V##"
                                             "#####"
                                             "#####"]))
    (set-test-unit atoms/game-map "V" :target [4 4] :turns-remaining 50)
    (reset! atoms/player-map (make-initial-test-map 5 5 nil))
    (update-cell-visibility [2 2] :player)
    ;; All 25 cells in the 5x5 map should be visible (rings 1 and 2 plus center)
    (doseq [row (range 5)
            col (range 5)]
      (should (get-in @atoms/player-map [row col])))))

(describe "update-combatant-map"
  (before (reset-all-atoms!))
  (it "reveals all 9 cells around a player unit in center of map"
    (reset! atoms/game-map (build-test-map ["~~~~~"
                                             "~~~~~"
                                             "~~A~~"
                                             "~~~~~"
                                             "~~~~~"]))
    (reset! atoms/player-map (make-initial-test-map 5 5 nil))
    (update-combatant-map atoms/player-map :player)
    ;; All 9 cells around [2 2] should be revealed
    (should= {:type :sea} (get-in @atoms/player-map [1 1]))
    (should= {:type :sea} (get-in @atoms/player-map [1 2]))
    (should= {:type :sea} (get-in @atoms/player-map [1 3]))
    (should= {:type :sea} (get-in @atoms/player-map [2 1]))
    (should= {:type :land :contents {:type :army :owner :player :hits 1}} (get-in @atoms/player-map [2 2]))
    (should= {:type :sea} (get-in @atoms/player-map [2 3]))
    (should= {:type :sea} (get-in @atoms/player-map [3 1]))
    (should= {:type :sea} (get-in @atoms/player-map [3 2]))
    (should= {:type :sea} (get-in @atoms/player-map [3 3]))
    ;; Corners should not be revealed
    (should= nil (get-in @atoms/player-map [0 0]))
    (should= nil (get-in @atoms/player-map [0 4]))
    (should= nil (get-in @atoms/player-map [4 0]))
    (should= nil (get-in @atoms/player-map [4 4])))

  (it "clamps visibility at map edges for unit in corner"
    (reset! atoms/game-map (build-test-map ["A~~~~"
                                             "~~~~~"
                                             "~~~~~"
                                             "~~~~~"
                                             "~~~~~"]))
    (reset! atoms/player-map (make-initial-test-map 5 5 nil))
    (update-combatant-map atoms/player-map :player)
    ;; Cells at and adjacent to [0 0] should be revealed (clamped)
    (should= {:type :land :contents {:type :army :owner :player :hits 1}} (get-in @atoms/player-map [0 0]))
    (should= {:type :sea} (get-in @atoms/player-map [0 1]))
    (should= {:type :sea} (get-in @atoms/player-map [1 0]))
    (should= {:type :sea} (get-in @atoms/player-map [1 1]))
    ;; Far cells should not be revealed
    (should= nil (get-in @atoms/player-map [2 2])))

  (it "reveals cells around player city"
    (reset! atoms/game-map (build-test-map ["~~~~~"
                                             "~~~~~"
                                             "~~O~~"
                                             "~~~~~"
                                             "~~~~~"]))
    (reset! atoms/player-map (make-initial-test-map 5 5 nil))
    (update-combatant-map atoms/player-map :player)
    ;; All 9 cells around [2 2] should be revealed
    (should= {:type :city :city-status :player} (get-in @atoms/player-map [2 2]))
    (should= {:type :sea} (get-in @atoms/player-map [1 1]))
    (should= {:type :sea} (get-in @atoms/player-map [3 3])))

  (it "does nothing when visible-map-atom is nil"
    (reset! atoms/game-map (build-test-map ["~~~~~"
                                             "~~~~~"
                                             "~~A~~"
                                             "~~~~~"
                                             "~~~~~"]))
    (reset! atoms/player-map nil)
    (update-combatant-map atoms/player-map :player)
    (should= nil @atoms/player-map))

  (it "works for computer owner"
    (reset! atoms/game-map (build-test-map ["~~~~~"
                                             "~~~~~"
                                             "~~a~~"
                                             "~~~~~"
                                             "~~~~~"]))
    (reset! atoms/computer-map (make-initial-test-map 5 5 nil))
    (update-combatant-map atoms/computer-map :computer)
    ;; All 9 cells around [2 2] should be revealed in computer map
    (should= {:type :land :contents {:type :army :owner :computer :hits 1}} (get-in @atoms/computer-map [2 2]))
    (should= {:type :sea} (get-in @atoms/computer-map [1 1]))
    (should= {:type :sea} (get-in @atoms/computer-map [3 3])))

  (it "handles multiple units revealing overlapping areas"
    (reset! atoms/game-map (build-test-map ["~~~~~~~"
                                             "~~~~~~~"
                                             "~~A~~~~"
                                             "~~~~~~~"
                                             "~~~~A~~"
                                             "~~~~~~~"
                                             "~~~~~~~"]))
    (reset! atoms/player-map (make-initial-test-map 7 7 nil))
    (update-combatant-map atoms/player-map :player)
    ;; Both units and their surroundings should be visible
    (should= {:type :land :contents {:type :army :owner :player :hits 1}} (get-in @atoms/player-map [2 2]))
    (should= {:type :land :contents {:type :army :owner :player :hits 1}} (get-in @atoms/player-map [4 4]))
    ;; Overlapping cell [3 3] should be revealed by both
    (should= {:type :sea} (get-in @atoms/player-map [3 3]))
    ;; Far corner should not be revealed
    (should= nil (get-in @atoms/player-map [6 6]))))
