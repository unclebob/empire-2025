(ns empire.movement.explore-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.explore :refer :all]
            [empire.test-utils :refer [build-test-map get-test-unit set-test-unit reset-all-atoms!]]))

(describe "valid-explore-cell?"
  (before (reset-all-atoms!))
  (it "returns true for land cell without unit"
    (should (valid-explore-cell? {:type :land})))

  (it "returns false for sea cell"
    (should-not (valid-explore-cell? {:type :sea})))

  (it "returns false for city cell"
    (should-not (valid-explore-cell? {:type :city :city-status :player})))

  (it "returns false for cell with unit"
    (should-not (valid-explore-cell? {:type :land :contents {:type :army}})))

  (it "returns false for nil cell"
    (should-not (valid-explore-cell? nil))))

(describe "get-valid-explore-moves"
  (before (reset-all-atoms!))
  (it "returns adjacent land cells without units"
    (let [game-map (build-test-map ["###"
                                    "###"
                                    "###"])]
      (let [moves (get-valid-explore-moves [1 1] game-map)]
        (should= 8 (count moves)))))

  (it "excludes sea cells"
    (let [game-map (build-test-map ["~~~"
                                    "~#~"
                                    "~~~"])]
      (let [moves (get-valid-explore-moves [1 1] game-map)]
        (should= 0 (count moves)))))

  (it "excludes cells with units"
    (let [game-map (build-test-map ["#A#"
                                    "###"
                                    "###"])]
      (let [moves (get-valid-explore-moves [1 1] game-map)]
        (should= 7 (count moves)))))

  (it "handles corner positions"
    (let [game-map (build-test-map ["##"
                                    "##"])]
      (let [moves (get-valid-explore-moves [0 0] game-map)]
        (should= 3 (count moves))))))

(describe "adjacent-to-unexplored?"
  (before (reset-all-atoms!))
  (it "returns true when adjacent to unexplored cell"
    (reset! atoms/player-map @(build-test-map ["#-"
                                               "##"]))
    (should (adjacent-to-unexplored? [0 0])))

  (it "returns false when all adjacent cells are explored"
    (reset! atoms/player-map @(build-test-map ["###"
                                               "###"
                                               "###"]))
    (should-not (adjacent-to-unexplored? [1 1]))))

(describe "get-unexplored-explore-moves"
  (before (reset-all-atoms!))
  (it "returns moves adjacent to unexplored cells"
    (let [game-map (build-test-map ["###"
                                    "###"
                                    "###"])]
      (reset! atoms/player-map @(build-test-map ["##-"
                                                 "##-"
                                                 "##-"]))
      ;; From [1 1], moves to column 2 should be adjacent to unexplored
      (let [moves (get-unexplored-explore-moves [1 1] game-map)]
        (should (some #{[0 2]} moves))
        (should (some #{[1 2]} moves))
        (should (some #{[2 2]} moves))))))

(describe "pick-explore-move"
  (before (reset-all-atoms!))
  (it "prefers unexplored moves"
    (let [game-map (build-test-map ["###"
                                    "###"
                                    "###"])]
      (reset! atoms/player-map @(build-test-map ["##-"
                                                 "###"
                                                 "###"]))
      (let [move (pick-explore-move [1 1] game-map #{})]
        ;; Should pick a move adjacent to unexplored [0 2]
        (should (some #{move} [[0 1] [0 2] [1 2]])))))

  (it "returns visited cell when all cells visited"
    (let [game-map (build-test-map ["~~~"
                                    "~#~"
                                    "###"])]
      (reset! atoms/player-map @game-map)
      ;; All valid moves are visited
      (let [visited #{[2 0] [2 1] [2 2]}
            move (pick-explore-move [1 1] game-map visited)]
        ;; Should still return a move even though all are visited
        (should (some #{move} [[2 0] [2 1] [2 2]])))))

  (it "returns nil when no valid moves"
    (let [game-map (build-test-map ["~~~"
                                    "~#~"
                                    "~~~"])]
      (reset! atoms/player-map @game-map)
      (should-be-nil (pick-explore-move [1 1] game-map #{})))))

(describe "set-explore-mode"
  (before (reset-all-atoms!))
  (it "sets unit to explore mode with initial state"
    (reset! atoms/game-map @(build-test-map ["A"]))
    (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))]
      (set-explore-mode unit-coords)
      (let [unit (get-in @atoms/game-map (conj unit-coords :contents))]
        (should= :explore (:mode unit))
        (should= config/explore-steps (:explore-steps unit))
        (should= #{unit-coords} (:visited unit)))))

  (it "removes reason and target when setting explore mode"
    (reset! atoms/game-map @(build-test-map ["A"]))
    (set-test-unit atoms/game-map "A" :reason :some-reason :target [5 5])
    (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))]
      (set-explore-mode unit-coords)
      (let [unit (get-in @atoms/game-map (conj unit-coords :contents))]
        (should= :explore (:mode unit))
        (should-be-nil (:reason unit))
        (should-be-nil (:target unit))))))

(describe "move-explore-unit"
  (before (reset-all-atoms!))
  (it "wakes up after explore-steps exhausted"
    (reset! atoms/game-map @(build-test-map ["A#"]))
    (set-test-unit atoms/game-map "A" :mode :explore :explore-steps 1 :visited #{})
    (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map @(build-test-map ["##"]))
      (move-explore-unit unit-coords)
      (let [unit (get-in @atoms/game-map (conj unit-coords :contents))]
        (should= :awake (:mode unit))
        (should-be-nil (:explore-steps unit))
        (should-be-nil (:visited unit)))))

  (it "wakes up when stuck with no valid moves"
    (reset! atoms/game-map @(build-test-map ["~~~"
                                             "~A~"
                                             "~~~"]))
    (set-test-unit atoms/game-map "A" :mode :explore :explore-steps 10 :visited #{})
    (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/player-map @(build-test-map ["~~~"
                                                 "~#~"
                                                 "~~~"]))
      (move-explore-unit unit-coords)
      (let [unit (get-in @atoms/game-map (conj unit-coords :contents))]
        (should= :awake (:mode unit)))))

  (it "wakes up when finding hostile city"
    (reset! atoms/game-map @(build-test-map ["A#X"]))
    (set-test-unit atoms/game-map "A" :mode :explore :explore-steps 10 :visited #{})
    (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))
          dest-coords [(first unit-coords) (inc (second unit-coords))]]
      (reset! atoms/player-map @(build-test-map ["##X"]))
      (move-explore-unit unit-coords)
      ;; After moving to dest-coords which is adjacent to hostile city, unit should wake
      (let [unit (get-in @atoms/game-map (conj dest-coords :contents))]
        (should= :awake (:mode unit))
        (should= :army-found-city (:reason unit))))))
