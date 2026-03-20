(ns empire.game-mechanics.movement.visibility-reveal-helpers-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.visibility :refer :all]
            [empire.test.utils :refer [build-test-map set-test-unit reset-all-atoms! set-test-player-map! set-test-computer-map! make-initial-test-map set-test-world!]]))
(describe "reveal-cell!"
  (before (reset-all-atoms!))

  (it "reveals a game cell in the visible map"
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (let [game-cell {:type :land}
          visible-map (test-utils/read-test-state :player-map)]
      (#'empire.game-mechanics.visibility/reveal-cell!
        (test-utils/player-map-atom) 1 1 game-cell nil visible-map)
      (should= {:type :land} (get-in (test-utils/read-test-state :player-map) [1 1]))))

  (it "stamps country-id on newly-revealed land cell"
    (set-test-world! (build-test-map ["###"
                                             "###"
                                             "###"]))
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (let [game-cell {:type :land}
          visible-map (test-utils/read-test-state :player-map)]
      (#'empire.game-mechanics.visibility/reveal-cell!
        (test-utils/player-map-atom) 1 1 game-cell 5 visible-map)
      (should= 5 (get-in (test-utils/read-test-state :game-map) [1 1 :country-id]))))

  (it "does not stamp country-id on sea cell"
    (set-test-world! (build-test-map ["~~~"
                                             "~~~"
                                             "~~~"]))
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (let [game-cell {:type :sea}
          visible-map (test-utils/read-test-state :player-map)]
      (#'empire.game-mechanics.visibility/reveal-cell!
        (test-utils/player-map-atom) 1 1 game-cell 5 visible-map)
      (should-not (get-in (test-utils/read-test-state :game-map) [1 1 :country-id]))))

  (it "does not stamp country-id on already-revealed cell"
    (set-test-world! (build-test-map ["###"
                                             "###"
                                             "###"]))
    (let [pre-revealed [[{:type :land} {:type :land} {:type :land}]
                        [{:type :land} {:type :land} {:type :land}]
                        [{:type :land} {:type :land} {:type :land}]]]
      (set-test-player-map! pre-revealed)
      (let [game-cell {:type :land}
            visible-map (test-utils/read-test-state :player-map)]
        (#'empire.game-mechanics.visibility/reveal-cell!
          (test-utils/player-map-atom) 1 1 game-cell 5 visible-map)
        (should-not (get-in (test-utils/read-test-state :game-map) [1 1 :country-id]))))))

(describe "is-players?"
  (it "returns true for player city"
    (should (#'empire.game-mechanics.visibility/is-players? {:type :city :city-status :player})))

  (it "returns true for cell with player unit"
    (should (#'empire.game-mechanics.visibility/is-players? {:type :land :contents {:owner :player}})))

  (it "returns false for computer city"
    (should-not (#'empire.game-mechanics.visibility/is-players? {:type :city :city-status :computer})))

  (it "returns false for cell with computer unit"
    (should-not (#'empire.game-mechanics.visibility/is-players? {:type :land :contents {:owner :computer}})))

  (it "returns false for empty cell"
    (should-not (#'empire.game-mechanics.visibility/is-players? {:type :land})))

  (it "returns false for free city"
    (should-not (#'empire.game-mechanics.visibility/is-players? {:type :city :city-status :free}))))

(describe "is-computers?"
  (it "returns true for computer city"
    (should (#'empire.game-mechanics.visibility/is-computers? {:type :city :city-status :computer})))

  (it "returns true for cell with computer unit"
    (should (#'empire.game-mechanics.visibility/is-computers? {:type :land :contents {:owner :computer}})))

  (it "returns false for player city"
    (should-not (#'empire.game-mechanics.visibility/is-computers? {:type :city :city-status :player})))

  (it "returns false for cell with player unit"
    (should-not (#'empire.game-mechanics.visibility/is-computers? {:type :land :contents {:owner :player}})))

  (it "returns false for empty cell"
    (should-not (#'empire.game-mechanics.visibility/is-computers? {:type :land}))))

(describe "cell-visibility-radius"
  (it "returns 1 for cell without unit"
    (should= 1 (#'empire.game-mechanics.visibility/cell-visibility-radius {:type :land})))

  (it "returns 1 for cell with army"
    (should= 1 (#'empire.game-mechanics.visibility/cell-visibility-radius
                  {:type :land :contents {:type :army}})))

  (it "returns 2 for cell with satellite"
    (should= 2 (#'empire.game-mechanics.visibility/cell-visibility-radius
                  {:type :land :contents {:type :satellite}})))

  (it "returns 1 for cell with fighter"
    (should= 1 (#'empire.game-mechanics.visibility/cell-visibility-radius
                  {:type :land :contents {:type :fighter}})))

  (it "returns 1 for city cell without unit"
    (should= 1 (#'empire.game-mechanics.visibility/cell-visibility-radius
                  {:type :city :city-status :player}))))

(describe "reveal-surrounding-cells!"
  (it "reveals cells within radius 1"
    (let [game-map [[{:type :land} {:type :sea} {:type :land}]
                     [{:type :sea} {:type :land} {:type :sea}]
                     [{:type :land} {:type :sea} {:type :land}]]
          result (transient (mapv transient (vec (repeat 3 (vec (repeat 3 nil))))))]
      (#'empire.game-mechanics.visibility/reveal-surrounding-cells! result game-map 1 1 3 3 1)
      (let [final (mapv persistent! (persistent! result))]
        (should= {:type :land} (get-in final [0 0]))
        (should= {:type :sea} (get-in final [1 0]))
        (should= {:type :land} (get-in final [2 0]))
        (should= {:type :sea} (get-in final [0 1]))
        (should= {:type :land} (get-in final [1 1]))
        (should= {:type :sea} (get-in final [2 1]))
        (should= {:type :land} (get-in final [0 2]))
        (should= {:type :sea} (get-in final [1 2]))
        (should= {:type :land} (get-in final [2 2])))))

  (it "clamps at map edges"
    (let [game-map [[{:type :land} {:type :sea}]
                     [{:type :sea} {:type :land}]]
          result (transient (mapv transient (vec (repeat 2 (vec (repeat 2 nil))))))]
      (#'empire.game-mechanics.visibility/reveal-surrounding-cells! result game-map 0 0 2 2 1)
      (let [final (mapv persistent! (persistent! result))]
        (should= {:type :land} (get-in final [0 0]))
        (should= {:type :sea} (get-in final [1 0]))
        (should= {:type :sea} (get-in final [0 1]))
        (should= {:type :land} (get-in final [1 1])))))

  (it "reveals larger area with radius 2"
    (let [game-map (vec (repeat 5 (vec (repeat 5 {:type :land}))))
          result (transient (mapv transient (vec (repeat 5 (vec (repeat 5 nil))))))]
      (#'empire.game-mechanics.visibility/reveal-surrounding-cells! result game-map 2 2 5 5 2)
      (let [final (mapv persistent! (persistent! result))]
        ;; All 25 cells should be revealed (radius 2 from center of 5x5)
        (doseq [r (range 5) c (range 5)]
          (should= {:type :land} (get-in final [r c])))))))
