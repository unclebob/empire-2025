(ns empire.coastline-spec
  (:require
    [empire.atoms :as atoms]
    [empire.config :as config]
    [empire.game-loop :as game-loop]
    [empire.movement :as movement]
    [speclj.core :refer :all]))

(describe "coastline-follow mode"
  (before
    (reset! atoms/game-map nil)
    (reset! atoms/player-map nil))

  (describe "set-coastline-follow-mode"
    (it "sets mode to :coastline-follow with start-pos and visited"
      (let [initial-map (-> (vec (repeat 9 (vec (repeat 9 {:type :sea}))))
                            (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :awake :owner :player :hits 1}})
                            (assoc-in [4 5] {:type :land}))]
        (reset! atoms/game-map initial-map)
        (movement/set-coastline-follow-mode [4 4])
        (let [unit (get-in @atoms/game-map [4 4 :contents])]
          (should= :coastline-follow (:mode unit))
          (should= [4 4] (:start-pos unit))
          (should= #{[4 4]} (:visited unit))
          (should= config/coastline-steps (:coastline-steps unit))))))

  (describe "valid-coastline-cell?"
    (it "returns true for empty sea cell"
      (let [cell {:type :sea}]
        (should (movement/valid-coastline-cell? cell))))

    (it "returns false for land cell"
      (let [cell {:type :land}]
        (should-not (movement/valid-coastline-cell? cell))))

    (it "returns false for cell with contents"
      (let [cell {:type :sea :contents {:type :destroyer}}]
        (should-not (movement/valid-coastline-cell? cell))))

    (it "returns false for nil cell"
      (should-not (movement/valid-coastline-cell? nil))))

  (describe "get-valid-coastline-moves"
    (it "returns adjacent sea cells"
      (let [game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :land}))))
                         (assoc-in [2 2] {:type :sea})
                         (assoc-in [2 3] {:type :sea})
                         (assoc-in [3 2] {:type :sea}))]
        (reset! atoms/game-map game-map)
        (let [moves (movement/get-valid-coastline-moves [2 2] atoms/game-map)]
          (should (some #{[2 3]} moves))
          (should (some #{[3 2]} moves))
          (should= 2 (count moves))))))

  (describe "pick-coastline-move"
    (it "prefers cells adjacent to land"
      (let [;; Create a map where only [2 3] is a valid coastal move
            game-map (-> (vec (repeat 6 (vec (repeat 6 {:type :land}))))
                         (assoc-in [2 2] {:type :sea})  ; current position
                         (assoc-in [2 3] {:type :sea}))] ; only valid sea move, adjacent to land
        (reset! atoms/game-map game-map)
        (let [move (movement/pick-coastline-move [2 2] atoms/game-map #{} nil)]
          (should= [2 3] move))))

    (it "avoids visited cells when possible"
      (let [;; Two sea cells available: [2 3] and [3 2], both coastal
            game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :land}))))
                         (assoc-in [2 2] {:type :sea})
                         (assoc-in [2 3] {:type :sea})
                         (assoc-in [3 2] {:type :sea}))
            visited #{[2 3]}]
        (reset! atoms/game-map game-map)
        (let [move (movement/pick-coastline-move [2 2] atoms/game-map visited nil)]
          ;; Should pick [3 2] since [2 3] is visited
          (should= [3 2] move))))

    (it "avoids backsteps"
      (let [;; Two sea cells available: [2 3] and [3 2], both coastal
            game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :land}))))
                         (assoc-in [2 2] {:type :sea})
                         (assoc-in [2 3] {:type :sea})
                         (assoc-in [3 2] {:type :sea}))]
        (reset! atoms/game-map game-map)
        ;; prev-pos is [2 3], so should avoid it and pick [3 2]
        (let [move (movement/pick-coastline-move [2 2] atoms/game-map #{} [2 3])]
          (should= [3 2] move))))

    (it "randomizes each move"
      (let [;; Three sea cells available, all coastal
            game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :land}))))
                         (assoc-in [2 2] {:type :sea})
                         (assoc-in [2 3] {:type :sea})
                         (assoc-in [3 2] {:type :sea})
                         (assoc-in [1 2] {:type :sea}))]
        (reset! atoms/game-map game-map)
        ;; All moves should be randomized
        (let [moves (set (repeatedly 20 #(movement/pick-coastline-move [2 2] atoms/game-map #{} nil)))
              valid-moves #{[2 3] [3 2] [1 2]}]
          (should (every? valid-moves moves))
          (should (> (count moves) 1)))))

    (it "returns visited cell when all cells visited"
      (let [game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :land}))))
                         (assoc-in [2 2] {:type :sea})
                         (assoc-in [2 3] {:type :sea}))
            visited #{[2 3]}]
        (reset! atoms/game-map game-map)
        (let [move (movement/pick-coastline-move [2 2] atoms/game-map visited nil)]
          (should= [2 3] move))))

    (it "returns nil when no valid moves"
      (let [game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :land}))))
                         (assoc-in [2 2] {:type :sea}))]
        (reset! atoms/game-map game-map)
        (should-be-nil (movement/pick-coastline-move [2 2] atoms/game-map #{} nil)))))

  (describe "move-coastline-unit"
    (before
      (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil))))))

    (it "moves unit along coastline based on speed"
      (let [;; Create a long coastline for transport (speed 2) to move along
            initial-map (-> (vec (repeat 9 (vec (repeat 9 {:type :land}))))
                            (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :coastline-follow
                                                                   :owner :player :hits 1
                                                                   :start-pos [0 0]
                                                                   :coastline-steps 100
                                                                   :visited #{[4 4]}}})
                            (assoc-in [4 5] {:type :sea})
                            (assoc-in [4 6] {:type :sea})
                            (assoc-in [4 7] {:type :sea}))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
        (movement/move-coastline-unit [4 4])
        ;; Transport has speed 2, so should move 2 cells in one round
        (should-be-nil (get-in @atoms/game-map [4 4 :contents]))
        (should-be-nil (get-in @atoms/game-map [4 5 :contents]))
        (let [unit (get-in @atoms/game-map [4 6 :contents])]
          (should= :coastline-follow (:mode unit))
          (should= 98 (:coastline-steps unit)))))

    (it "patrol-boat moves 4 steps per round"
      (let [;; Create a long coastline for patrol-boat (speed 4) to move along
            initial-map (-> (vec (repeat 9 (vec (repeat 9 {:type :land}))))
                            (assoc-in [4 2] {:type :sea :contents {:type :patrol-boat :mode :coastline-follow
                                                                   :owner :player :hits 1
                                                                   :start-pos [0 0]
                                                                   :coastline-steps 100
                                                                   :visited #{[4 2]}}})
                            (assoc-in [4 3] {:type :sea})
                            (assoc-in [4 4] {:type :sea})
                            (assoc-in [4 5] {:type :sea})
                            (assoc-in [4 6] {:type :sea})
                            (assoc-in [4 7] {:type :sea}))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
        (movement/move-coastline-unit [4 2])
        ;; Patrol-boat has speed 4, so should move 4 cells in one round
        (let [unit (get-in @atoms/game-map [4 6 :contents])]
          (should= :coastline-follow (:mode unit))
          (should= 96 (:coastline-steps unit)))))

    (it "wakes unit when adjacent to start position after traveling"
      (let [;; Unit at [4 4], start-pos is [4 5] which is adjacent
            ;; visited has > 5 cells to indicate vessel has traveled around
            initial-map (-> (vec (repeat 9 (vec (repeat 9 {:type :land}))))
                            (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :coastline-follow
                                                                   :owner :player :hits 1
                                                                   :start-pos [4 5]
                                                                   :coastline-steps 50
                                                                   :visited #{[4 5] [4 6] [4 7] [3 7] [3 6] [4 4]}}})
                            (assoc-in [4 5] {:type :sea}))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
        (movement/move-coastline-unit [4 4])
        ;; Unit should wake up at current position [4 4], not move to [4 5]
        (let [unit (get-in @atoms/game-map [4 4 :contents])]
          (should= :awake (:mode unit))
          (should= :returned-to-start (:reason unit))
          (should-be-nil (:coastline-steps unit))
          (should-be-nil (:visited unit))
          (should-be-nil (:start-pos unit)))))

    (it "wakes unit when steps exhausted"
      (let [;; Only one valid move: [4 5], start-pos far away so not adjacent
            initial-map (-> (vec (repeat 9 (vec (repeat 9 {:type :land}))))
                            (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :coastline-follow
                                                                   :owner :player :hits 1
                                                                   :start-pos [0 0]
                                                                   :coastline-steps 1
                                                                   :visited #{[4 4]}}})
                            (assoc-in [4 5] {:type :sea}))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
        (movement/move-coastline-unit [4 4])
        (let [unit (get-in @atoms/game-map [4 5 :contents])]
          (should= :awake (:mode unit))
          (should= :steps-exhausted (:reason unit)))))

    (it "wakes unit when stuck with blocked reason"
      (let [;; Unit at [4 4] surrounded by land - not at edge
            initial-map (-> (vec (repeat 9 (vec (repeat 9 {:type :land}))))
                            (assoc-in [4 4] {:type :sea :contents {:type :transport :mode :coastline-follow
                                                                   :owner :player :hits 1
                                                                   :start-pos [4 4]
                                                                   :coastline-steps 100
                                                                   :visited #{[4 4]}}}))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
        (movement/move-coastline-unit [4 4])
        (let [unit (get-in @atoms/game-map [4 4 :contents])]
          (should= :awake (:mode unit))
          (should= :blocked (:reason unit)))))

    (it "wakes unit when hitting map edge"
      (let [;; Unit at [0 4] - at the top edge of map, surrounded by land
            initial-map (-> (vec (repeat 9 (vec (repeat 9 {:type :land}))))
                            (assoc-in [0 4] {:type :sea :contents {:type :transport :mode :coastline-follow
                                                                   :owner :player :hits 1
                                                                   :start-pos [0 4]
                                                                   :coastline-steps 100
                                                                   :visited #{[0 4]}}}))]
        (reset! atoms/game-map initial-map)
        (reset! atoms/player-map (vec (repeat 9 (vec (repeat 9 nil)))))
        (movement/move-coastline-unit [0 4])
        (let [unit (get-in @atoms/game-map [0 4 :contents])]
          (should= :awake (:mode unit))
          (should= :hit-edge (:reason unit))))))

  (describe "coastline-follow eligibility"
    (it "transport near coast is eligible"
      (let [unit {:type :transport :mode :awake}]
        (should (movement/coastline-follow-eligible? unit true))))

    (it "patrol-boat near coast is eligible"
      (let [unit {:type :patrol-boat :mode :awake}]
        (should (movement/coastline-follow-eligible? unit true))))

    (it "transport not near coast is not eligible"
      (let [unit {:type :transport :mode :awake}]
        (should-not (movement/coastline-follow-eligible? unit false))))

    (it "destroyer near coast is not eligible"
      (let [unit {:type :destroyer :mode :awake}]
        (should-not (movement/coastline-follow-eligible? unit true))))

    (it "army is not eligible"
      (let [unit {:type :army :mode :awake}]
        (should-not (movement/coastline-follow-eligible? unit true)))))

  (describe "mode->color for coastline-follow"
    (it "returns green color"
      (should= config/explore-unit-color (config/mode->color :coastline-follow))))

  (describe "orthogonally-adjacent-to-land?"
    (it "returns true when land is directly north"
      (let [game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :sea}))))
                          (assoc-in [1 2] {:type :land}))]  ; land north of [2 2]
        (reset! atoms/game-map game-map)
        (should (movement/orthogonally-adjacent-to-land? [2 2] atoms/game-map))))

    (it "returns true when land is directly south"
      (let [game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :sea}))))
                          (assoc-in [3 2] {:type :land}))]  ; land south of [2 2]
        (reset! atoms/game-map game-map)
        (should (movement/orthogonally-adjacent-to-land? [2 2] atoms/game-map))))

    (it "returns true when land is directly east"
      (let [game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :sea}))))
                          (assoc-in [2 3] {:type :land}))]  ; land east of [2 2]
        (reset! atoms/game-map game-map)
        (should (movement/orthogonally-adjacent-to-land? [2 2] atoms/game-map))))

    (it "returns true when land is directly west"
      (let [game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :sea}))))
                          (assoc-in [2 1] {:type :land}))]  ; land west of [2 2]
        (reset! atoms/game-map game-map)
        (should (movement/orthogonally-adjacent-to-land? [2 2] atoms/game-map))))

    (it "returns false when land is only diagonally adjacent"
      (let [game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :sea}))))
                          (assoc-in [1 1] {:type :land}))]  ; land NW of [2 2]
        (reset! atoms/game-map game-map)
        (should-not (movement/orthogonally-adjacent-to-land? [2 2] atoms/game-map))))

    (it "returns false when no land is adjacent"
      (let [game-map (vec (repeat 5 (vec (repeat 5 {:type :sea}))))]
        (reset! atoms/game-map game-map)
        (should-not (movement/orthogonally-adjacent-to-land? [2 2] atoms/game-map)))))

  (describe "pick-coastline-move prefers orthogonal shore hugging"
    (it "prefers move orthogonally adjacent to land over diagonally adjacent"
      (let [;; Create a sea map with specific land placement:
            ;; Current: [3 3], two valid moves: [3 4] and [4 4]
            ;; [3 4] has land orthogonally at [3 5]
            ;; [4 4] has land only diagonally at [5 5]
            game-map (-> (vec (repeat 7 (vec (repeat 7 {:type :sea}))))
                          (assoc-in [3 5] {:type :land})   ; orthogonal to [3 4]
                          (assoc-in [5 5] {:type :land}))]  ; diagonal to [4 4]
        (reset! atoms/game-map game-map)
        ;; From [3 3], both [3 4] and [4 4] are valid sea moves
        ;; [3 4] is orthogonally adjacent to land at [3 5]
        ;; [4 4] is only diagonally adjacent to land at [5 5]
        ;; Should prefer [3 4]
        (let [moves (set (repeatedly 20 #(movement/pick-coastline-move [3 3] atoms/game-map #{} nil)))]
          ;; All picked moves should be orthogonally adjacent to land
          (should (every? #(movement/orthogonally-adjacent-to-land? % atoms/game-map) moves)))))

    (it "randomizes among equally good orthogonal moves"
      (let [;; Create constrained area with exactly two valid moves from [2 2]:
            ;; Both [2 1] and [2 3] are orthogonally adjacent to land
            game-map (-> (vec (repeat 6 (vec (repeat 6 {:type :land}))))
                          (assoc-in [2 2] {:type :sea})   ; current position
                          (assoc-in [2 1] {:type :sea})   ; move option 1
                          (assoc-in [2 3] {:type :sea}))]  ; move option 2
        ;; Both [2 1] and [2 3] have land orthogonally adjacent (from the land base map)
        (reset! atoms/game-map game-map)
        ;; Should pick from both since both are orthogonally adjacent
        (let [moves (set (repeatedly 20 #(movement/pick-coastline-move [2 2] atoms/game-map #{} nil)))]
          (should (= #{[2 1] [2 3]} moves)))))

    (it "falls back to diagonal coastal when no orthogonal coastal moves"
      (let [;; Create a scenario where only diagonal coastal is available
            ;; [2 2] -> [3 3] with land only at [4 4] (diagonal to [3 3])
            ;; Surround with land except for a diagonal channel
            game-map (-> (vec (repeat 7 (vec (repeat 7 {:type :land}))))
                          (assoc-in [2 2] {:type :sea})   ; current position
                          (assoc-in [3 3] {:type :sea})   ; only valid move (diagonal)
                          (assoc-in [4 4] {:type :sea}))]  ; extends channel so [3 3] has diagonal adj land
        (reset! atoms/game-map game-map)
        ;; [3 3] is diagonally adjacent to land (at [2 2] borders, [2 3], [3 2], [4 2], [2 4], etc)
        ;; but not orthogonally adjacent since [2 3], [3 2], [4 3], [3 4] need to be land
        (should= [3 3] (movement/pick-coastline-move [2 2] atoms/game-map #{} nil)))))

  (describe "pick-coastline-move prefers unexplored territory"
    (it "prefers moves adjacent to unexplored over explored"
      (let [;; Sea map with land for shore hugging, two valid moves
            ;; [3 3] current, can move to [3 4] or [4 3]
            ;; [3 4] has unexplored neighbors, [4 3] does not
            game-map (-> (vec (repeat 7 (vec (repeat 7 {:type :sea}))))
                          (assoc-in [3 5] {:type :land})   ; makes [3 4] coastal
                          (assoc-in [5 3] {:type :land}))  ; makes [4 3] coastal
            ;; Player map: most explored, but [3 6] and neighbors of [3 4] unexplored
            player-map (-> (vec (repeat 7 (vec (repeat 7 {:type :sea}))))
                            (assoc-in [3 5] {:type :land})
                            (assoc-in [5 3] {:type :land})
                            ;; Leave [2 5], [3 6], [4 5] as nil (unexplored) - adjacent to [3 4]
                            (assoc-in [2 5] nil)
                            (assoc-in [3 6] nil)
                            (assoc-in [4 5] nil))]
        (reset! atoms/game-map game-map)
        (reset! atoms/player-map player-map)
        ;; Both moves are orthogonally coastal, but [3 4] is adjacent to unexplored
        (let [moves (set (repeatedly 20 #(movement/pick-coastline-move [3 3] atoms/game-map #{} nil)))]
          ;; Should prefer [3 4] since it's adjacent to unexplored territory
          (should (every? #(movement/adjacent-to-unexplored? %) moves)))))

    (it "falls back to explored when no unexplored available"
      (let [;; Fully explored sea with coastal moves
            game-map (-> (vec (repeat 6 (vec (repeat 6 {:type :land}))))
                          (assoc-in [2 2] {:type :sea})
                          (assoc-in [2 3] {:type :sea}))
            ;; Fully explored player map
            player-map (-> (vec (repeat 6 (vec (repeat 6 {:type :land}))))
                            (assoc-in [2 2] {:type :sea})
                            (assoc-in [2 3] {:type :sea}))]
        (reset! atoms/game-map game-map)
        (reset! atoms/player-map player-map)
        ;; Should still return a valid move even though nothing is unexplored
        (should= [2 3] (movement/pick-coastline-move [2 2] atoms/game-map #{} nil)))))

  (describe "not near coast message"
    (it "returns :not-near-coast reason when transport not adjacent to land"
      (let [unit {:type :transport :mode :awake}]
        (should= :not-near-coast (movement/coastline-follow-rejection-reason unit false))))

    (it "returns :not-near-coast reason when patrol-boat not adjacent to land"
      (let [unit {:type :patrol-boat :mode :awake}]
        (should= :not-near-coast (movement/coastline-follow-rejection-reason unit false))))

    (it "returns nil when transport is near coast (eligible)"
      (let [unit {:type :transport :mode :awake}]
        (should-be-nil (movement/coastline-follow-rejection-reason unit true))))

    (it "returns nil for non-ship units"
      (let [unit {:type :destroyer :mode :awake}]
        (should-be-nil (movement/coastline-follow-rejection-reason unit false))))))
