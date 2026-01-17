(ns empire.coastline-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.coastline :refer :all]))

(describe "coastline-follow-eligible?"
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
  (it "returns :not-near-coast for transport not near coast"
    (should= :not-near-coast (coastline-follow-rejection-reason {:type :transport} false)))

  (it "returns nil for transport near coast"
    (should-be-nil (coastline-follow-rejection-reason {:type :transport} true)))

  (it "returns nil for non-eligible unit types"
    (should-be-nil (coastline-follow-rejection-reason {:type :destroyer} false))
    (should-be-nil (coastline-follow-rejection-reason {:type :army} false))))

(describe "valid-coastline-cell?"
  (it "returns true for empty sea cell"
    (should (valid-coastline-cell? {:type :sea})))

  (it "returns false for land cell"
    (should-not (valid-coastline-cell? {:type :land})))

  (it "returns false for cell with unit"
    (should-not (valid-coastline-cell? {:type :sea :contents {:type :destroyer}})))

  (it "returns false for nil cell"
    (should-not (valid-coastline-cell? nil))))

(describe "get-valid-coastline-moves"
  (it "returns adjacent sea cells without units"
    (let [game-map (atom [[{:type :sea} {:type :sea} {:type :sea}]
                          [{:type :sea} {:type :sea} {:type :sea}]
                          [{:type :sea} {:type :sea} {:type :sea}]])]
      (let [moves (get-valid-coastline-moves [1 1] game-map)]
        (should= 8 (count moves)))))

  (it "excludes land cells"
    (let [game-map (atom [[{:type :land} {:type :land} {:type :land}]
                          [{:type :land} {:type :sea} {:type :land}]
                          [{:type :land} {:type :land} {:type :land}]])]
      (let [moves (get-valid-coastline-moves [1 1] game-map)]
        (should= 0 (count moves)))))

  (it "excludes cells with units"
    (let [game-map (atom [[{:type :sea} {:type :sea :contents {:type :destroyer}} {:type :sea}]
                          [{:type :sea} {:type :sea} {:type :sea}]
                          [{:type :sea} {:type :sea} {:type :sea}]])]
      (let [moves (get-valid-coastline-moves [1 1] game-map)]
        (should= 7 (count moves))))))

(describe "pick-coastline-move"
  (it "prefers orthogonally adjacent to land moves"
    (let [game-map (atom [[{:type :land} {:type :sea} {:type :sea}]
                          [{:type :land} {:type :sea} {:type :sea}]
                          [{:type :land} {:type :sea} {:type :sea}]])]
      (reset! atoms/player-map @game-map)
      (let [move (pick-coastline-move [1 1] game-map #{} nil)]
        (should (some #{move} [[0 1] [2 1] [0 2] [1 2] [2 2]])))))

  (it "returns nil when no valid moves"
    (let [game-map (atom [[{:type :land} {:type :land} {:type :land}]
                          [{:type :land} {:type :sea} {:type :land}]
                          [{:type :land} {:type :land} {:type :land}]])]
      (reset! atoms/player-map @game-map)
      (should-be-nil (pick-coastline-move [1 1] game-map #{} nil))))

  (it "avoids previous position"
    (let [game-map (atom [[{:type :land} {:type :sea} {:type :land}]
                          [{:type :land} {:type :sea} {:type :land}]
                          [{:type :land} {:type :sea} {:type :land}]])]
      (reset! atoms/player-map @game-map)
      (dotimes [_ 10]
        (let [move (pick-coastline-move [1 1] game-map #{} [0 1])]
          (should-not= [0 1] move))))))

(describe "set-coastline-follow-mode"
  (it "sets unit to coastline-follow mode with initial state"
    (reset! atoms/game-map [[{:type :sea :contents {:type :transport :mode :awake :owner :player}}]])
    (set-coastline-follow-mode [0 0])
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should= :coastline-follow (:mode unit))
      (should= config/coastline-steps (:coastline-steps unit))
      (should= [0 0] (:start-pos unit))
      (should= #{[0 0]} (:visited unit))
      (should-be-nil (:prev-pos unit))))

  (it "removes reason and target"
    (reset! atoms/game-map [[{:type :sea :contents {:type :transport :mode :awake :owner :player :reason :some-reason :target [5 5]}}]])
    (set-coastline-follow-mode [0 0])
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should-be-nil (:reason unit))
      (should-be-nil (:target unit)))))

(describe "move-coastline-unit"
  (it "moves transport along coastline"
    (let [game-map (-> (vec (repeat 5 (vec (repeat 5 {:type :sea}))))
                       (assoc-in [0 0] {:type :land})
                       (assoc-in [1 0] {:type :land})
                       (assoc-in [2 0] {:type :land})
                       (assoc-in [2 1] {:type :sea :contents {:type :transport :mode :coastline-follow :owner :player
                                                              :coastline-steps 50 :start-pos [2 1] :visited #{[2 1]} :prev-pos nil}}))]
      (reset! atoms/game-map game-map)
      (reset! atoms/player-map (vec (repeat 5 (vec (repeat 5 {:type :sea})))))
      (move-coastline-unit [2 1])
      (should-be-nil (:contents (get-in @atoms/game-map [2 1])))))

  (it "wakes up when blocked"
    (let [game-map [[{:type :land} {:type :land} {:type :land}]
                    [{:type :land} {:type :sea :contents {:type :transport :mode :coastline-follow :owner :player
                                                          :coastline-steps 50 :start-pos [1 1] :visited #{[1 1]} :prev-pos nil}} {:type :land}]
                    [{:type :land} {:type :land} {:type :land}]]]
      (reset! atoms/game-map game-map)
      (reset! atoms/player-map game-map)
      (move-coastline-unit [1 1])
      (let [unit (get-in @atoms/game-map [1 1 :contents])]
        (should= :awake (:mode unit))
        (should= :blocked (:reason unit))))))
