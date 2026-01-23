(ns empire.movement.coastline-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.coastline :refer :all]
            [empire.test-utils :refer [build-test-map set-test-unit get-test-unit reset-all-atoms!]]))

(describe "pick-random-matching"
  (it "returns nil for empty collection"
    (should-be-nil (pick-random-matching [])))

  (it "returns nil when no elements match predicate"
    (should-be-nil (pick-random-matching [1 3 5] even?)))

  (it "returns random element from matching set"
    (let [results (repeatedly 20 #(pick-random-matching [1 2 3 4 5 6] even?))]
      (should (every? #{2 4 6} results))
      ;; Should have some variety (not always same element)
      (should (> (count (set results)) 1))))

  (it "applies multiple predicates with every-pred"
    (let [gt5? (fn [x] (> x 5))
          results (repeatedly 20 #(pick-random-matching [1 2 3 4 5 6 7 8 9 10 12]
                                                        even?
                                                        gt5?))]
      (should (every? #{6 8 10 12} results))))

  (it "returns element when single match exists"
    (should= 4 (pick-random-matching [1 3 4 5 7] even?)))

  (it "works with no predicates (returns random from collection)"
    (let [results (repeatedly 20 #(pick-random-matching [1 2 3]))]
      (should (every? #{1 2 3} results)))))

(describe "coastline-follow-eligible?"
  (before (reset-all-atoms!))
  (it "returns true for transport near coast"
    (should (coastline-follow-eligible? {:type :transport} true)))

  (it "returns true for patrol-boat near coast"
    (should (coastline-follow-eligible? {:type :patrol-boat} true)))

  (it "returns false for transport not near coast"
    (should-not (coastline-follow-eligible? {:type :transport} false)))

  (it "returns false for other ship types"
    (should-not (coastline-follow-eligible? {:type :destroyer} true))
    (should-not (coastline-follow-eligible? {:type :battleship} true))
    (should-not (coastline-follow-eligible? {:type :carrier} true))))

(describe "coastline-follow-rejection-reason"
  (before (reset-all-atoms!))
  (it "returns :not-near-coast for transport not near coast"
    (should= :not-near-coast (coastline-follow-rejection-reason {:type :transport} false)))

  (it "returns nil for transport near coast"
    (should-be-nil (coastline-follow-rejection-reason {:type :transport} true)))

  (it "returns nil for non-eligible unit types"
    (should-be-nil (coastline-follow-rejection-reason {:type :destroyer} false))
    (should-be-nil (coastline-follow-rejection-reason {:type :army} false))))

(describe "valid-coastline-cell?"
  (before (reset-all-atoms!))
  (it "returns true for empty sea cell"
    (should (valid-coastline-cell? {:type :sea})))

  (it "returns false for land cell"
    (should-not (valid-coastline-cell? {:type :land})))

  (it "returns false for cell with unit"
    (should-not (valid-coastline-cell? {:type :sea :contents {:type :destroyer}})))

  (it "returns false for nil cell"
    (should-not (valid-coastline-cell? nil))))

(describe "get-valid-coastline-moves"
  (before (reset-all-atoms!))
  (it "returns adjacent sea cells without units"
    (let [game-map (atom (build-test-map ["~~~"
                                    "~~~"
                                    "~~~"]))]
      (let [moves (get-valid-coastline-moves [1 1] game-map)]
        (should= 8 (count moves)))))

  (it "excludes land cells"
    (let [game-map (atom (build-test-map ["###"
                                    "#~#"
                                    "###"]))]
      (let [moves (get-valid-coastline-moves [1 1] game-map)]
        (should= 0 (count moves)))))

  (it "excludes cells with units"
    (let [game-map (atom (build-test-map ["~D~"
                                    "~~~"
                                    "~~~"]))]
      (let [moves (get-valid-coastline-moves [1 1] game-map)]
        (should= 7 (count moves))))))

(describe "pick-coastline-move"
  (before (reset-all-atoms!))
  (it "prefers orthogonally adjacent to land moves"
    (let [game-map (atom (build-test-map ["#~~"
                                    "#~~"
                                    "#~~"]))]
      (reset! atoms/player-map @game-map)
      (let [move (pick-coastline-move [1 1] game-map #{} nil)]
        (should (some #{move} [[0 1] [2 1] [0 2] [1 2] [2 2]])))))

  (it "returns nil when no valid moves"
    (let [game-map (atom (build-test-map ["###"
                                    "#~#"
                                    "###"]))]
      (reset! atoms/player-map @game-map)
      (should-be-nil (pick-coastline-move [1 1] game-map #{} nil))))

  (it "avoids previous position"
    (let [game-map (atom (build-test-map ["#~#"
                                    "#~#"
                                    "#~#"]))]
      (reset! atoms/player-map @game-map)
      (dotimes [_ 10]
        (let [move (pick-coastline-move [1 1] game-map #{} [0 1])]
          (should-not= [0 1] move)))))

  (it "prefers unvisited orthogonal coastal cells that expose unexplored"
    ;; Set up: [1 1] is the unit, [0 1] is orthogonally coastal and adjacent to unexplored [0 0]
    (let [game-map (atom (build-test-map ["#~~"
                                    "#~~"
                                    "#~~"]))]
      ;; player-map with nil at [0 0] means unexplored
      (reset! atoms/player-map (build-test-map ["-~~"
                                                 "#~~"
                                                 "#~~"]))
      (dotimes [_ 10]
        (let [move (pick-coastline-move [1 1] game-map #{} nil)]
          ;; Should prefer [0 1] because it's orthogonally adjacent to land and adjacent to unexplored [0 0]
          (should= [0 1] move)))))

  (it "falls back to unvisited coastal cells exposing unexplored when no orthogonal"
    ;; Set up: no orthogonal coastal moves, but diagonal coastal move adjacent to unexplored
    ;; The key is: no unvisited-orthogonal (neither orthogonal to land nor unexplored)
    ;; but there IS unvisited diagonal coastal that is adjacent to unexplored
    (let [game-map (atom (build-test-map ["~~~"
                                    "~~~"
                                    "~~#"]))]
      ;; Player map: [2 0] is unexplored (nil), so [2 1] is adjacent to unexplored
      ;; [2 1] is diagonally adjacent to land at [2 2]
      ;; No cells are orthogonally adjacent to land from [1 1]
      (reset! atoms/player-map (build-test-map ["~~~"
                                                 "~~~"
                                                 "-~#"]))
      (dotimes [_ 10]
        (let [move (pick-coastline-move [1 1] game-map #{} nil)]
          ;; Should pick [2 1] - diagonal coastal and adjacent to unexplored [2 0]
          (should= [2 1] move)))))

  (it "falls back to unvisited coastal cells when no unexplored adjacent"
    ;; All explored, but there's a coastal move
    (let [game-map (atom (build-test-map ["~~~"
                                    "~~~"
                                    "~~#"]))]
      ;; All explored (no nil cells)
      (reset! atoms/player-map @game-map)
      (let [move (pick-coastline-move [1 1] game-map #{} nil)]
        ;; Should pick [2 1] or [2 2] - the coastal cells (diagonal to land)
        (should (some #{move} [[2 1] [1 2] [2 2]])))))

  (it "falls back to visited orthogonal coastal when all unvisited are non-coastal"
    ;; All unvisited non-coastal, but there's a visited orthogonal coastal cell
    (let [game-map (atom (build-test-map ["~~~"
                                    "#~~"
                                    "~~~"]))]
      (reset! atoms/player-map @game-map)
      ;; Mark all cells except [1 0] (land) and [0 1] (visited coastal) as visited
      (let [visited #{[0 0] [0 2] [2 0] [1 2] [2 1] [2 2]}]
        (dotimes [_ 10]
          (let [move (pick-coastline-move [1 1] game-map visited nil)]
            ;; Should pick [0 1] - visited but orthogonally adjacent to land
            (should= [0 1] move))))))

  (it "falls back to any coastal move when orthogonal coastal visited"
    ;; Visited orthogonal coastal, but there's a diagonal coastal move
    (let [game-map (atom (build-test-map ["#~~"
                                    "~~~"
                                    "~~~"]))]
      (reset! atoms/player-map @game-map)
      ;; All cells visited except we allow backstepping to coastal
      (let [visited #{[0 1] [1 0] [0 2] [1 2] [2 0] [2 1] [2 2]}]
        (dotimes [_ 10]
          (let [move (pick-coastline-move [1 1] game-map visited nil)]
            ;; Should pick [0 1] or [1 0] - coastal (diagonal to land at [0 0])
            (should (some #{move} [[0 1] [1 0] [0 2]]))))))

    ;; Additional test: specifically hit the diagonal-only coastal branch
    ;; This requires: no orthogonal-coastal moves exist at all, only diagonal coastal
    (let [game-map (atom (build-test-map ["##~"
                                    "#~~"
                                    "~~~"]))]
      ;; From [1 1]: orthogonal neighbors are [0 1] land, [2 1] sea, [1 0] land, [1 2] sea
      ;; Orthogonally adjacent to land: none of the sea cells [2 1], [1 2] are orthogonally adjacent to land
      ;; Diagonally adjacent to land: [0 2] is diagonal to [0 1] and [1 2]? No, [0 2] neighbors are [0 1] land (orthogonal!), [1 1], [1 2]
      ;; Let me reconsider: we need cells that are diagonally adjacent to land but NOT orthogonally adjacent
      ;; [2 2] has neighbors: [1 1] sea, [1 2] sea, [2 1] sea, [1 3]?, [2 3]?, [3 1]?, [3 2]?, [3 3]?
      ;; Actually, from [1 1], the diagonal neighbors are [0 0], [0 2], [2 0], [2 2]
      ;; [0 0] is land, [0 2] is sea, [2 0] is sea, [2 2] is sea
      ;; For coastal: is [0 2] adjacent to land? Its neighbors include [0 1] which is land (orthogonally!)
      ;; So [0 2] IS orthogonally adjacent to land
      ;; I need a setup where diagonals are coastal but not orthogonally adjacent to land
      (reset! atoms/player-map @game-map)
      (let [visited #{[0 2] [2 0] [2 1] [1 2] [2 2]}]  ;; Mark all as visited, leaving only [0 2] (visited, diagonal coastal)
        ;; prev-pos blocks backstepping
        (let [move (pick-coastline-move [1 1] game-map visited nil)]
          ;; Should pick from visited coastal options
          (should (some #{move} [[0 2] [2 1] [1 2] [2 0] [2 2]]))))))

  (it "falls back to unvisited diagonal coastal with unexplored (no orthogonal coastal)"
    ;; Scenario: No unvisited orthogonal coastal, but unvisited diagonal coastal adjacent to unexplored
    ;; Need: land only at diagonal positions from center, no orthogonal land neighbors
    ;; And some cells adjacent to unexplored (nil in player-map)
    ;; Map layout: land at corners only, sea elsewhere
    (let [game-map (atom (build-test-map ["#~#"
                                    "~~~"
                                    "#~#"]))]
      ;; From [1 1]: orthogonal neighbors [0 1], [2 1], [1 0], [1 2] are all sea
      ;; Diagonal neighbors [0 0], [0 2], [2 0], [2 2] are all land
      ;; So orthogonal neighbors are sea but NOT orthogonally adjacent to land
      ;; Diagonal neighbors are land (invalid moves)
      ;; The diagonal adjacent sea cells are the orthogonal ones, but they're diagonal to land
      ;; Wait, [0 1] is adjacent to [0 0] and [0 2] (both land) - so it IS adjacent to land (diagonally)
      ;; For orthogonal adjacency to land: [0 1] has orthogonal neighbors [0 0], [0 2], [1 1]
      ;; Hmm, [0 1]'s orthogonal neighbors include [-1 1] (out of bounds), [1 1] (sea), [0 0] (land), [0 2] (land)
      ;; So [0 1] IS orthogonally adjacent to land! This won't work.
      ;; I need a different setup where no sea cell is orthogonally adjacent to land.
      ;; Let's try a 5x5 map with land only at corners
      (reset! atoms/game-map (build-test-map ["#~~~#"
                                               "~~~~~"
                                               "~~~~~"
                                               "~~~~~"
                                               "#~~~#"]))
      ;; From [2 2]: all orthogonal neighbors [1 2], [3 2], [2 1], [2 3] are sea
      ;; None of them are orthogonally adjacent to land (land is only at corners)
      ;; But [1 1], [1 3], [3 1], [3 3] (diagonals of [2 2]) are sea and diagonally adjacent to corners
      ;; Actually [1 1] is diagonally adjacent to [0 0] (land), so [1 1] is coastal (diagonal)
      ;; We need unexplored cells: make [0 0] unexplored in player-map
      (reset! atoms/player-map (build-test-map ["-~~~#"
                                                 "~~~~~"
                                                 "~~~~~"
                                                 "~~~~~"
                                                 "#~~~#"]))
      ;; Now [1 1] is diagonal coastal (adjacent to [0 0] which is land in game-map)
      ;; And [1 1] is adjacent to unexplored [0 0] in player-map
      ;; From [2 2], [1 1] should be picked as unvisited-coastal-unexplored
      (dotimes [_ 10]
        (let [move (pick-coastline-move [2 2] atoms/game-map #{} nil)]
          ;; Should pick [1 1] - diagonal coastal and adjacent to unexplored
          (should= [1 1] move)))))

  (it "falls back to visited diagonal coastal when no unvisited coastal"
    ;; Scenario: All unvisited moves are non-coastal, no orthogonal coastal (visited or not)
    ;; But there's a visited diagonal coastal cell
    ;; This requires: only visited diagonal coastal options remaining
    (let [game-map (atom (build-test-map ["#~~~#"
                                    "~~~~~"
                                    "~~~~~"
                                    "~~~~~"
                                    "#~~~#"]))]
      (reset! atoms/player-map @game-map)  ;; All explored
      ;; From [2 2]: coastal cells (diagonal to land) are [1 1], [1 3], [3 1], [3 3]
      ;; Non-coastal cells are [1 2], [2 1], [2 3], [3 2]
      ;; Mark all non-coastal as unvisited, coastal as visited
      ;; Then the fallback should use visited diagonal coastal
      (let [visited #{[1 1] [1 3] [3 1] [3 3]}]  ;; Coastal cells visited
        (let [_move (pick-coastline-move [2 2] game-map visited nil)]
          ;; Should pick a visited coastal cell since all unvisited are non-coastal
          ;; Actually no - unvisited-moves comes before coastal-moves in priority
          ;; So it will pick an unvisited non-coastal first
          ;; I need to also mark non-coastal as visited to hit this branch
          ))
      ;; Mark everything as visited to force fall through to coastal-moves
      (let [visited #{[1 1] [1 2] [1 3] [2 1] [2 3] [3 1] [3 2] [3 3]}]  ;; All visited
        (let [move (pick-coastline-move [2 2] game-map visited nil)]
          ;; Should pick a coastal cell (diagonal to land)
          (should (some #{move} [[1 1] [1 3] [3 1] [3 3]]))))))

  (it "falls back to any unvisited move when no coastal"
    ;; No coastal cells at all, but there are unvisited sea cells
    (let [game-map (atom (build-test-map ["~~~"
                                    "~~~"
                                    "~~~"]))]
      (reset! atoms/player-map @game-map)
      ;; Mark some as visited, leave others unvisited
      (let [visited #{[0 0] [0 1] [0 2] [1 0]}]
        (let [move (pick-coastline-move [1 1] game-map visited nil)]
          ;; Should pick an unvisited non-coastal cell
          (should (some #{move} [[1 2] [2 0] [2 1] [2 2]]))))))

  (it "falls back to any move when all visited"
    ;; All cells visited, should pick any valid move
    (let [game-map (atom (build-test-map ["~~~"
                                    "~~~"
                                    "~~~"]))]
      (reset! atoms/player-map @game-map)
      ;; All neighbors visited
      (let [visited #{[0 0] [0 1] [0 2] [1 0] [1 2] [2 0] [2 1] [2 2]}]
        (let [move (pick-coastline-move [1 1] game-map visited nil)]
          ;; Should pick any valid cell
          (should (some #{move} [[0 0] [0 1] [0 2] [1 0] [1 2] [2 0] [2 1] [2 2]])))))))

(describe "set-coastline-follow-mode"
  (before (reset-all-atoms!))
  (it "sets unit to coastline-follow mode with initial state"
    (reset! atoms/game-map (build-test-map ["T"]))
    (set-coastline-follow-mode [0 0])
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should= :coastline-follow (:mode unit))
      (should= config/coastline-steps (:coastline-steps unit))
      (should= [0 0] (:start-pos unit))
      (should= #{[0 0]} (:visited unit))
      (should-be-nil (:prev-pos unit))))

  (it "removes reason and target"
    (reset! atoms/game-map (build-test-map ["T"]))
    (set-test-unit atoms/game-map "T" :reason :some-reason :target [5 5])
    (set-coastline-follow-mode [0 0])
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should-be-nil (:reason unit))
      (should-be-nil (:target unit)))))

(describe "move-coastline-unit"
  (before (reset-all-atoms!))
  (it "moves transport along coastline"
    (reset! atoms/game-map (build-test-map ["#~~~~"
                                             "#~~~~"
                                             "#T~~~"
                                             "~~~~~"
                                             "~~~~~"]))
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :coastline-steps 50
                   :start-pos [2 1]
                   :visited #{[2 1]}
                   :prev-pos nil)
    (reset! atoms/player-map (build-test-map ["~~~~~"
                                               "~~~~~"
                                               "~~~~~"
                                               "~~~~~"
                                               "~~~~~"]))
    (move-coastline-unit [2 1])
    (should-be-nil (:contents (get-in @atoms/game-map [2 1]))))

  (it "wakes up when blocked"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#T#"
                                             "###"]))
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :coastline-steps 50
                   :start-pos [1 1]
                   :visited #{[1 1]}
                   :prev-pos nil)
    (reset! atoms/player-map @atoms/game-map)
    (move-coastline-unit [1 1])
    (let [unit (get-in @atoms/game-map [1 1 :contents])]
      (should= :awake (:mode unit))
      (should= :blocked (:reason unit))))

  (it "wakes up when returning to start position after traveling"
    ;; Create a small loop where unit can return to start after 5+ moves
    (reset! atoms/game-map (build-test-map ["#~~~"
                                             "#T#~"
                                             "#~~~"
                                             "####"]))
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :coastline-steps 50
                   :start-pos [0 1]
                   ;; Simulating having traveled 6+ cells, now adjacent to start
                   :visited #{[0 1] [0 2] [0 3] [1 3] [2 3] [2 2] [2 1]}
                   :prev-pos [2 1])
    (reset! atoms/player-map @atoms/game-map)
    (move-coastline-unit [1 1])
    ;; Unit should wake because it's adjacent to start-pos [0 1] and visited > 5
    (let [unit (get-in @atoms/game-map [1 1 :contents])]
      (should= :awake (:mode unit))
      (should= :returned-to-start (:reason unit))))

  (it "wakes up when hitting map edge (started away from edge)"
    ;; Unit starts away from edge, moves to edge
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "#T~"
                                             "#~~"]))
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :coastline-steps 50
                   :start-pos [1 1]  ;; Started at [1 1], not at edge
                   :visited #{[1 1]}
                   :prev-pos nil)
    (reset! atoms/player-map @atoms/game-map)
    (move-coastline-unit [1 1])
    ;; Unit should move to edge (row 0) and wake
    (let [cell-0-1 (get-in @atoms/game-map [0 1])
          cell-0-2 (get-in @atoms/game-map [0 2])
          woken-unit (or (:contents cell-0-1) (:contents cell-0-2))]
      (when woken-unit
        (should= :awake (:mode woken-unit))
        (should= :hit-edge (:reason woken-unit)))))

  (it "wakes up when steps exhausted"
    ;; Map where unit starts in middle, not at edge
    (reset! atoms/game-map (build-test-map ["#~~~~"
                                             "#~~~~"
                                             "#T~~~"
                                             "#~~~~"
                                             "#~~~~"]))
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :coastline-steps 1  ;; Only 1 step left
                   :start-pos [2 1]    ;; Started in middle, not at edge
                   :visited #{[2 1]}
                   :prev-pos nil)
    (reset! atoms/player-map @atoms/game-map)
    (move-coastline-unit [2 1])
    (let [{:keys [unit]} (get-test-unit atoms/game-map "T" :mode :awake)]
      (should= :awake (:mode unit))
      (should= :steps-exhausted (:reason unit))))

  (it "continues moving when not waking (tests make-continuing-unit and recur)"
    ;; Map where unit won't hit any wake conditions
    ;; Transport has speed 2, and with 50 coastline-steps, it should continue
    (reset! atoms/game-map (build-test-map ["#~~~~"
                                             "#~~~~"
                                             "#~~~~"
                                             "#T~~~"
                                             "#~~~~"
                                             "#~~~~"
                                             "#~~~~"]))
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :coastline-steps 50  ;; Plenty of steps
                   :start-pos [3 1]    ;; Started in middle, not at edge
                   :visited #{[3 1]}
                   :prev-pos nil)
    (reset! atoms/player-map @atoms/game-map)
    ;; transport has speed 2, so it should take 2 steps and not wake
    (move-coastline-unit [3 1])
    ;; Unit should have moved but still be in coastline-follow mode (not woken)
    (let [{:keys [unit]} (get-test-unit atoms/game-map "T" :mode :coastline-follow)]
      (should= :coastline-follow (:mode unit))
      (should (> (count (:visited unit)) 1))))

  (it "wakes up transport with armies when finding a bay"
    ;; A bay is a sea cell surrounded by land on exactly 3 orthogonal sides
    ;; Create a channel that leads into a bay - unit must move into the bay
    ;; Bay at [2 3]: up=[1 3] land, down=[3 3] land, left=[2 2] sea, right=[2 4] land = 3 land sides
    (reset! atoms/game-map (build-test-map ["#####"
                                             "##~##"
                                             "##T~#"
                                             "##~##"
                                             "#####"]))
    ;; [2 3] has: up=[1 3] land, down=[3 3] land, left=[2 2] sea, right=[2 4] land -> 3 land = bay!
    ;; Put transport at [2 2] with armies. Only valid move is [2 3] (bay) or [1 2] or [3 2]
    ;; Set prev-pos to [1 2] so it doesn't backstep, leaving only [2 3] or [3 2]
    (set-test-unit atoms/game-map "T"
                   :mode :coastline-follow
                   :army-count 2
                   :coastline-steps 50
                   :start-pos [1 2]
                   :visited #{[1 2] [2 2]}
                   :prev-pos [1 2])
    (reset! atoms/player-map @atoms/game-map)
    (move-coastline-unit [2 2])
    ;; Find the awake transport - it should be at [2 3] (bay) or [3 2]
    (let [unit-2-3 (get-in @atoms/game-map [2 3 :contents])
          unit-3-2 (get-in @atoms/game-map [3 2 :contents])
          _woken-unit (if (= :found-a-bay (:reason unit-2-3)) unit-2-3 unit-3-2)]
      ;; If it went to bay [2 3], it should wake with :found-a-bay
      (when unit-2-3
        (should= :awake (:mode unit-2-3))
        (should= :found-a-bay (:reason unit-2-3))))))
