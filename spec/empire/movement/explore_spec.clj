(ns empire.movement.explore-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.explore :refer :all]))

(describe "valid-explore-cell?"
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
  (it "returns adjacent land cells without units"
    (let [game-map (atom [[{:type :land} {:type :land} {:type :land}]
                          [{:type :land} {:type :land} {:type :land}]
                          [{:type :land} {:type :land} {:type :land}]])]
      (let [moves (get-valid-explore-moves [1 1] game-map)]
        (should= 8 (count moves)))))

  (it "excludes sea cells"
    (let [game-map (atom [[{:type :sea} {:type :sea} {:type :sea}]
                          [{:type :sea} {:type :land} {:type :sea}]
                          [{:type :sea} {:type :sea} {:type :sea}]])]
      (let [moves (get-valid-explore-moves [1 1] game-map)]
        (should= 0 (count moves)))))

  (it "excludes cells with units"
    (let [game-map (atom [[{:type :land} {:type :land :contents {:type :army}} {:type :land}]
                          [{:type :land} {:type :land} {:type :land}]
                          [{:type :land} {:type :land} {:type :land}]])]
      (let [moves (get-valid-explore-moves [1 1] game-map)]
        (should= 7 (count moves)))))

  (it "handles corner positions"
    (let [game-map (atom [[{:type :land} {:type :land}]
                          [{:type :land} {:type :land}]])]
      (let [moves (get-valid-explore-moves [0 0] game-map)]
        (should= 3 (count moves))))))

(describe "adjacent-to-unexplored?"
  (it "returns true when adjacent to unexplored cell"
    (reset! atoms/player-map [[{:type :land} nil]
                              [{:type :land} {:type :land}]])
    (should (adjacent-to-unexplored? [0 0])))

  (it "returns false when all adjacent cells are explored"
    (reset! atoms/player-map [[{:type :land} {:type :land} {:type :land}]
                              [{:type :land} {:type :land} {:type :land}]
                              [{:type :land} {:type :land} {:type :land}]])
    (should-not (adjacent-to-unexplored? [1 1]))))

(describe "get-unexplored-explore-moves"
  (it "returns moves adjacent to unexplored cells"
    (let [game-map (atom [[{:type :land} {:type :land} {:type :land}]
                          [{:type :land} {:type :land} {:type :land}]
                          [{:type :land} {:type :land} {:type :land}]])
          player-map [[{:type :land} {:type :land} nil]
                      [{:type :land} {:type :land} nil]
                      [{:type :land} {:type :land} nil]]]
      (reset! atoms/player-map player-map)
      ;; From [1 1], moves to column 2 should be adjacent to unexplored
      (let [moves (get-unexplored-explore-moves [1 1] game-map)]
        (should (some #{[0 2]} moves))
        (should (some #{[1 2]} moves))
        (should (some #{[2 2]} moves))))))

(describe "pick-explore-move"
  (it "prefers unexplored moves"
    (let [game-map (atom [[{:type :land} {:type :land} {:type :land}]
                          [{:type :land} {:type :land} {:type :land}]
                          [{:type :land} {:type :land} {:type :land}]])
          player-map [[{:type :land} {:type :land} nil]
                      [{:type :land} {:type :land} {:type :land}]
                      [{:type :land} {:type :land} {:type :land}]]]
      (reset! atoms/player-map player-map)
      (let [move (pick-explore-move [1 1] game-map #{})]
        ;; Should pick a move adjacent to unexplored [0 2]
        (should (some #{move} [[0 1] [0 2] [1 2]])))))

  (it "returns visited cell when all cells visited"
    (let [game-map (atom [[{:type :sea} {:type :sea} {:type :sea}]
                          [{:type :sea} {:type :land} {:type :sea}]
                          [{:type :land} {:type :land} {:type :land}]])
          player-map [[{:type :sea} {:type :sea} {:type :sea}]
                      [{:type :sea} {:type :land} {:type :sea}]
                      [{:type :land} {:type :land} {:type :land}]]]
      (reset! atoms/player-map player-map)
      ;; All valid moves are visited
      (let [visited #{[2 0] [2 1] [2 2]}
            move (pick-explore-move [1 1] game-map visited)]
        ;; Should still return a move even though all are visited
        (should (some #{move} [[2 0] [2 1] [2 2]])))))

  (it "returns nil when no valid moves"
    (let [game-map (atom [[{:type :sea} {:type :sea} {:type :sea}]
                          [{:type :sea} {:type :land} {:type :sea}]
                          [{:type :sea} {:type :sea} {:type :sea}]])]
      (reset! atoms/player-map @game-map)
      (should-be-nil (pick-explore-move [1 1] game-map #{})))))

(describe "set-explore-mode"
  (it "sets unit to explore mode with initial state"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :awake :owner :player}}]])
    (set-explore-mode [0 0])
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should= :explore (:mode unit))
      (should= config/explore-steps (:explore-steps unit))
      (should= #{[0 0]} (:visited unit))))

  (it "removes reason and target when setting explore mode"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :awake :owner :player :reason :some-reason :target [5 5]}}]])
    (set-explore-mode [0 0])
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should= :explore (:mode unit))
      (should-be-nil (:reason unit))
      (should-be-nil (:target unit)))))

(describe "move-explore-unit"
  (it "wakes up after explore-steps exhausted"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :explore :owner :player :explore-steps 1 :visited #{}}}
                             {:type :land}]])
    (reset! atoms/player-map [[{:type :land} {:type :land}]])
    (move-explore-unit [0 0])
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should= :awake (:mode unit))
      (should-be-nil (:explore-steps unit))
      (should-be-nil (:visited unit))))

  (it "wakes up when stuck with no valid moves"
    (reset! atoms/game-map [[{:type :sea} {:type :sea} {:type :sea}]
                            [{:type :sea} {:type :land :contents {:type :army :mode :explore :owner :player :explore-steps 10 :visited #{}}} {:type :sea}]
                            [{:type :sea} {:type :sea} {:type :sea}]])
    (reset! atoms/player-map [[{:type :sea} {:type :sea} {:type :sea}]
                              [{:type :sea} {:type :land} {:type :sea}]
                              [{:type :sea} {:type :sea} {:type :sea}]])
    (move-explore-unit [1 1])
    (let [unit (get-in @atoms/game-map [1 1 :contents])]
      (should= :awake (:mode unit))))

  (it "wakes up when finding hostile city"
    (reset! atoms/game-map [[{:type :land :contents {:type :army :mode :explore :owner :player :explore-steps 10 :visited #{}}}
                             {:type :land}
                             {:type :city :city-status :computer}]])
    (reset! atoms/player-map [[{:type :land} {:type :land} {:type :city}]])
    (move-explore-unit [0 0])
    ;; After moving to [0 1] which is adjacent to hostile city at [0 2], unit should wake
    (let [unit (get-in @atoms/game-map [0 1 :contents])]
      (should= :awake (:mode unit))
      (should= :army-found-city (:reason unit)))))
