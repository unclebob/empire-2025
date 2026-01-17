(ns empire.movement.visibility-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.movement.visibility :refer :all]
            [empire.test-utils :refer [build-test-map]]))

(describe "update-cell-visibility"
  (it "reveals cells near player-owned units"
    (let [game-map (-> (vec (repeat 9 (vec (repeat 9 nil))))
                       (assoc-in [4 4] {:type :land :contents {:type :army :mode :awake :owner :player}})
                       (assoc-in [4 5] {:type :land})
                       (assoc-in [5 4] {:type :land}))]
      (reset! atoms/game-map game-map)
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
      (update-cell-visibility [4 4] :player)
      ;; Check that the unit's cell and neighbors are revealed
      (should= {:type :land :contents {:type :army :mode :awake :owner :player}} (get-in @atoms/player-map [4 4]))
      (should= {:type :land} (get-in @atoms/player-map [4 5]))
      (should= {:type :land} (get-in @atoms/player-map [5 4]))
      ;; Check that distant cells are not revealed
      (should= nil (get-in @atoms/player-map [0 0]))
      (should= nil (get-in @atoms/player-map [8 8]))))

  (it "reveals two rectangular rings for satellites"
    (let [initial-map (vec (repeat 15 (vec (repeat 15 {:type :land}))))
          initial-map (assoc-in initial-map [7 7] {:type :land :contents {:type :satellite :owner :player :target [14 14] :turns-remaining 50}})]
      (reset! atoms/game-map initial-map)
      (reset! atoms/player-map (vec (repeat 15 (vec (repeat 15 nil)))))
      (update-cell-visibility [7 7] :player)
      ;; Ring 1 (distance 1) - all 8 cells should be visible
      (should (get-in @atoms/player-map [6 6]))
      (should (get-in @atoms/player-map [6 7]))
      (should (get-in @atoms/player-map [6 8]))
      (should (get-in @atoms/player-map [7 6]))
      (should (get-in @atoms/player-map [7 8]))
      (should (get-in @atoms/player-map [8 6]))
      (should (get-in @atoms/player-map [8 7]))
      (should (get-in @atoms/player-map [8 8]))
      ;; Ring 2 (distance 2) - all 16 cells should be visible
      (should (get-in @atoms/player-map [5 5]))
      (should (get-in @atoms/player-map [5 6]))
      (should (get-in @atoms/player-map [5 7]))
      (should (get-in @atoms/player-map [5 8]))
      (should (get-in @atoms/player-map [5 9]))
      (should (get-in @atoms/player-map [6 5]))
      (should (get-in @atoms/player-map [6 9]))
      (should (get-in @atoms/player-map [7 5]))
      (should (get-in @atoms/player-map [7 9]))
      (should (get-in @atoms/player-map [8 5]))
      (should (get-in @atoms/player-map [8 9]))
      (should (get-in @atoms/player-map [9 5]))
      (should (get-in @atoms/player-map [9 6]))
      (should (get-in @atoms/player-map [9 7]))
      (should (get-in @atoms/player-map [9 8]))
      (should (get-in @atoms/player-map [9 9]))
      ;; Center cell (the satellite's position) should also be visible
      (should (get-in @atoms/player-map [7 7])))))

(describe "update-combatant-map"
  (it "reveals all 9 cells around a player unit in center of map"
    (let [game-map (assoc-in @(build-test-map ["sssss"
                                               "sssss"
                                               "ssAss"
                                               "sssss"
                                               "sssss"])
                             [2 2] {:type :land :contents {:type :army :owner :player}})]
      (reset! atoms/game-map game-map)
      (reset! atoms/player-map @(build-test-map ["....."
                                                 "....."
                                                 "....."
                                                 "....."
                                                 "....."]))
      (update-combatant-map atoms/player-map :player)
      ;; All 9 cells around [2 2] should be revealed
      (should= {:type :sea} (get-in @atoms/player-map [1 1]))
      (should= {:type :sea} (get-in @atoms/player-map [1 2]))
      (should= {:type :sea} (get-in @atoms/player-map [1 3]))
      (should= {:type :sea} (get-in @atoms/player-map [2 1]))
      (should= {:type :land :contents {:type :army :owner :player}} (get-in @atoms/player-map [2 2]))
      (should= {:type :sea} (get-in @atoms/player-map [2 3]))
      (should= {:type :sea} (get-in @atoms/player-map [3 1]))
      (should= {:type :sea} (get-in @atoms/player-map [3 2]))
      (should= {:type :sea} (get-in @atoms/player-map [3 3]))
      ;; Corners should not be revealed
      (should= nil (get-in @atoms/player-map [0 0]))
      (should= nil (get-in @atoms/player-map [0 4]))
      (should= nil (get-in @atoms/player-map [4 0]))
      (should= nil (get-in @atoms/player-map [4 4]))))

  (it "clamps visibility at map edges for unit in corner"
    (let [game-map (assoc-in @(build-test-map ["sssss"
                                               "sssss"
                                               "sssss"
                                               "sssss"
                                               "sssss"])
                             [0 0] {:type :land :contents {:type :army :owner :player}})]
      (reset! atoms/game-map game-map)
      (reset! atoms/player-map @(build-test-map ["....."
                                                 "....."
                                                 "....."
                                                 "....."
                                                 "....."]))
      (update-combatant-map atoms/player-map :player)
      ;; Cells at and adjacent to [0 0] should be revealed (clamped)
      (should= {:type :land :contents {:type :army :owner :player}} (get-in @atoms/player-map [0 0]))
      (should= {:type :sea} (get-in @atoms/player-map [0 1]))
      (should= {:type :sea} (get-in @atoms/player-map [1 0]))
      (should= {:type :sea} (get-in @atoms/player-map [1 1]))
      ;; Far cells should not be revealed
      (should= nil (get-in @atoms/player-map [2 2]))))

  (it "reveals cells around player city"
    (let [game-map (assoc-in @(build-test-map ["sssss"
                                               "sssss"
                                               "sssss"
                                               "sssss"
                                               "sssss"])
                             [2 2] {:type :city :city-status :player})]
      (reset! atoms/game-map game-map)
      (reset! atoms/player-map @(build-test-map ["....."
                                                 "....."
                                                 "....."
                                                 "....."
                                                 "....."]))
      (update-combatant-map atoms/player-map :player)
      ;; All 9 cells around [2 2] should be revealed
      (should= {:type :city :city-status :player} (get-in @atoms/player-map [2 2]))
      (should= {:type :sea} (get-in @atoms/player-map [1 1]))
      (should= {:type :sea} (get-in @atoms/player-map [3 3]))))

  (it "does nothing when visible-map-atom is nil"
    (let [game-map (assoc-in @(build-test-map ["sssss"
                                               "sssss"
                                               "ssAss"
                                               "sssss"
                                               "sssss"])
                             [2 2] {:type :land :contents {:type :army :owner :player}})]
      (reset! atoms/game-map game-map)
      (reset! atoms/player-map nil)
      (update-combatant-map atoms/player-map :player)
      (should= nil @atoms/player-map)))

  (it "works for computer owner"
    (let [game-map (assoc-in @(build-test-map ["sssss"
                                               "sssss"
                                               "sssss"
                                               "sssss"
                                               "sssss"])
                             [2 2] {:type :land :contents {:type :army :owner :computer}})]
      (reset! atoms/game-map game-map)
      (reset! atoms/computer-map @(build-test-map ["....."
                                                   "....."
                                                   "....."
                                                   "....."
                                                   "....."]))
      (update-combatant-map atoms/computer-map :computer)
      ;; All 9 cells around [2 2] should be revealed in computer map
      (should= {:type :land :contents {:type :army :owner :computer}} (get-in @atoms/computer-map [2 2]))
      (should= {:type :sea} (get-in @atoms/computer-map [1 1]))
      (should= {:type :sea} (get-in @atoms/computer-map [3 3]))))

  (it "handles multiple units revealing overlapping areas"
    (let [game-map (-> @(build-test-map ["sssssss"
                                         "sssssss"
                                         "sssssss"
                                         "sssssss"
                                         "sssssss"
                                         "sssssss"
                                         "sssssss"])
                       (assoc-in [2 2] {:type :land :contents {:type :army :owner :player}})
                       (assoc-in [4 4] {:type :land :contents {:type :army :owner :player}}))]
      (reset! atoms/game-map game-map)
      (reset! atoms/player-map @(build-test-map ["......."
                                                 "......."
                                                 "......."
                                                 "......."
                                                 "......."
                                                 "......."
                                                 "......."]))
      (update-combatant-map atoms/player-map :player)
      ;; Both units and their surroundings should be visible
      (should= {:type :land :contents {:type :army :owner :player}} (get-in @atoms/player-map [2 2]))
      (should= {:type :land :contents {:type :army :owner :player}} (get-in @atoms/player-map [4 4]))
      ;; Overlapping cell [3 3] should be revealed by both
      (should= {:type :sea} (get-in @atoms/player-map [3 3]))
      ;; Far corner should not be revealed
      (should= nil (get-in @atoms/player-map [6 6])))))
