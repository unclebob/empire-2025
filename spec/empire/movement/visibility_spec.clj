(ns empire.game-mechanics.movement.visibility-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.game-mechanics.movement.visibility :refer :all]
            [empire.test.utils :refer [build-test-map set-test-unit reset-all-atoms! set-test-player-map! set-test-computer-map! make-initial-test-map set-test-world!]]))

(describe "update-cell-visibility"
  (before (reset-all-atoms!))
  (it "reveals cells near player-owned units"
    (set-test-world! (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----A#---"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (set-test-player-map! (make-initial-test-map 9 9 nil))
    (update-cell-visibility [4 4] :player)
    ;; Check that the unit's cell and neighbors are revealed
    (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake}} (get-in (test-utils/read-test-state :player-map) [4 4]))
    (should= {:type :land} (get-in (test-utils/read-test-state :player-map) [5 4]))
    (should= {:type :land} (get-in (test-utils/read-test-state :player-map) [4 5]))
    ;; Check that distant cells are not revealed
    (should= nil (get-in (test-utils/read-test-state :player-map) [0 0]))
    (should= nil (get-in (test-utils/read-test-state :player-map) [8 8])))

  (it "computer non-army discovers free city — added to land-ho-targets"
    (set-test-world! (build-test-map ["~~~"
                                             "~t+"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "t" :mode :moving :target [2 1])
    (set-test-computer-map! (make-initial-test-map 3 3 nil))
    (test-utils/set-test-state! :land-ho-targets #{})
    (update-cell-visibility [1 1] :computer)
    (should-contain [2 1] (test-utils/read-test-state :land-ho-targets)))

  (it "computer army discovers free city — NOT added to land-ho-targets"
    (set-test-world! (build-test-map ["###"
                                             "#a+"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "a" :mode :moving :target [2 1])
    (set-test-computer-map! (make-initial-test-map 3 3 nil))
    (test-utils/set-test-state! :land-ho-targets #{})
    (update-cell-visibility [1 1] :computer)
    (should= #{} (test-utils/read-test-state :land-ho-targets)))

  (it "player discovers free city — NOT added to land-ho-targets"
    (set-test-world! (build-test-map ["~~~"
                                             "~T+"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :moving :target [2 1])
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (test-utils/set-test-state! :land-ho-targets #{})
    (update-cell-visibility [1 1] :player)
    (should= #{} (test-utils/read-test-state :land-ho-targets)))

  (it "computer non-army discovers non-free city — NOT added to land-ho-targets"
    (set-test-world! (build-test-map ["~~~"
                                             "~t~"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "t" :mode :moving :target [2 1])
    (set-test-computer-map! (make-initial-test-map 3 3 nil))
    (test-utils/set-test-state! :land-ho-targets #{})
    (update-cell-visibility [1 1] :computer)
    (should= #{} (test-utils/read-test-state :land-ho-targets)))

  (it "stamps country-id when 3-arity called with computer army unit"
    (set-test-world! (build-test-map ["###"
                                             "#a#"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "a" :mode :moving :target [2 1] :country-id 7)
    (set-test-computer-map! (make-initial-test-map 3 3 nil))
    (let [unit {:type :army :owner :computer :country-id 7}]
      (update-cell-visibility [1 1] :computer unit))
    (should= 7 (get-in (test-utils/read-test-state :game-map) [0 0 :country-id])))

  (it "does not stamp when visible-map is nil"
    (set-test-world! (build-test-map ["###"
                                             "#a#"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "a" :mode :moving :target [2 1])
    (set-test-computer-map! nil)
    (update-cell-visibility [1 1] :computer)
    (should-not (get-in (test-utils/read-test-state :game-map) [0 0 :country-id])))

  (it "3-arity with player army does not stamp and does not track"
    (set-test-world! (build-test-map ["###"
                                             "#A#"
                                             "##+"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [2 2])
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (let [unit {:type :army :owner :player}]
      (update-cell-visibility [1 1] :player unit))
    (should= {:type :land} (get-in (test-utils/read-test-state :player-map) [0 0]))
    (should-not (get-in (test-utils/read-test-state :game-map) [0 0 :country-id])))

  (it "reveals corner cell with edge clamping"
    (set-test-world! (build-test-map ["A~~"
                                             "~~~"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (update-cell-visibility [0 0] :player)
    (should (get-in (test-utils/read-test-state :player-map) [0 0]))
    (should (get-in (test-utils/read-test-state :player-map) [0 1]))
    (should (get-in (test-utils/read-test-state :player-map) [1 0]))
    (should (get-in (test-utils/read-test-state :player-map) [1 1]))
    (should-not (get-in (test-utils/read-test-state :player-map) [2 2])))

  (it "computer transport discovers free city — added to land-ho-targets (3-arity)"
    (set-test-world! (build-test-map ["~~~"
                                             "~t+"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "t" :mode :moving :target [2 1])
    (set-test-computer-map! (make-initial-test-map 3 3 nil))
    (test-utils/set-test-state! :land-ho-targets #{})
    (let [unit {:type :transport :owner :computer}]
      (update-cell-visibility [1 1] :computer unit))
    (should-contain [2 1] (test-utils/read-test-state :land-ho-targets)))

  (it "computer army 3-arity stamps land but does not track free city"
    (set-test-world! (build-test-map ["##+"
                                             "#a#"
                                             "###"]))
    (set-test-unit (test-utils/game-map-atom) "a" :mode :moving :target [0 2])
    (set-test-computer-map! (make-initial-test-map 3 3 nil))
    (test-utils/set-test-state! :land-ho-targets #{})
    (let [unit {:type :army :owner :computer :country-id 5}]
      (update-cell-visibility [1 1] :computer unit))
    (should= 5 (get-in (test-utils/read-test-state :game-map) [0 0 :country-id]))
    (should= #{} (test-utils/read-test-state :land-ho-targets)))

  (it "computer satellite discovers free city in outer ring — added to land-ho-targets"
    (set-test-world! (build-test-map ["~~~~~"
                                             "~~~~~"
                                             "~~v~~"
                                             "~~~~~"
                                             "~~~~+"]))
    (set-test-unit (test-utils/game-map-atom) "v" :target [4 4] :turns-remaining 50)
    (set-test-computer-map! (make-initial-test-map 5 5 nil))
    (test-utils/set-test-state! :land-ho-targets #{})
    (update-cell-visibility [2 2] :computer)
    (should-contain [4 4] (test-utils/read-test-state :land-ho-targets)))

  (it "3-arity with computer transport and nil visible-map — no-op"
    (set-test-world! (build-test-map ["~~~"
                                             "~t~"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "t" :mode :moving :target [2 1])
    (set-test-computer-map! nil)
    (test-utils/set-test-state! :land-ho-targets #{})
    (let [unit {:type :transport :owner :computer}]
      (update-cell-visibility [1 1] :computer unit))
    (should= #{} (test-utils/read-test-state :land-ho-targets)))

  (it "reveals two rectangular rings for satellites"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "##V##"
                                             "#####"
                                             "#####"]))
    (set-test-unit (test-utils/game-map-atom) "V" :target [4 4] :turns-remaining 50)
    (set-test-player-map! (make-initial-test-map 5 5 nil))
    (update-cell-visibility [2 2] :player)
    ;; All 25 cells in the 5x5 map should be visible (rings 1 and 2 plus center)
    (doseq [row (range 5)
            col (range 5)]
      (should (get-in (test-utils/read-test-state :player-map) [row col])))))

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

(describe "in-bounds?"
  (it "returns true for coordinates within bounds"
    (should= true (#'empire.game-mechanics.movement.visibility/in-bounds? 0 0 5 5))
    (should= true (#'empire.game-mechanics.movement.visibility/in-bounds? 4 4 5 5))
    (should= true (#'empire.game-mechanics.movement.visibility/in-bounds? 2 3 5 5)))

  (it "returns false for negative row"
    (should= false (#'empire.game-mechanics.movement.visibility/in-bounds? -1 0 5 5)))

  (it "returns false for negative col"
    (should= false (#'empire.game-mechanics.movement.visibility/in-bounds? 0 -1 5 5)))

  (it "returns false for row at height"
    (should= false (#'empire.game-mechanics.movement.visibility/in-bounds? 5 0 5 5)))

  (it "returns false for col at width"
    (should= false (#'empire.game-mechanics.movement.visibility/in-bounds? 0 5 5 5))))

(describe "should-stamp-country?"
  (it "returns truthy for computer army with country-id"
    (should (#'empire.game-mechanics.movement.visibility/should-stamp-country?
              {:type :army :owner :computer :country-id 3})))

  (it "returns falsy for nil unit"
    (should-not (#'empire.game-mechanics.movement.visibility/should-stamp-country? nil)))

  (it "returns falsy for player army"
    (should-not (#'empire.game-mechanics.movement.visibility/should-stamp-country?
                  {:type :army :owner :player :country-id 3})))

  (it "returns falsy for computer fighter"
    (should-not (#'empire.game-mechanics.movement.visibility/should-stamp-country?
                  {:type :fighter :owner :computer :country-id 3})))

  (it "returns falsy for computer army without country-id"
    (should-not (#'empire.game-mechanics.movement.visibility/should-stamp-country?
                  {:type :army :owner :computer}))))

(describe "should-track-free-city?"
  (it "returns true for computer non-army"
    (should (#'empire.game-mechanics.movement.visibility/should-track-free-city? :computer :transport)))

  (it "returns false for computer army"
    (should-not (#'empire.game-mechanics.movement.visibility/should-track-free-city? :computer :army)))

  (it "returns false for player non-army"
    (should-not (#'empire.game-mechanics.movement.visibility/should-track-free-city? :player :transport)))

  (it "returns false for player army"
    (should-not (#'empire.game-mechanics.movement.visibility/should-track-free-city? :player :army)))

  (it "returns true for computer fighter"
    (should (#'empire.game-mechanics.movement.visibility/should-track-free-city? :computer :fighter)))

  (it "returns true for computer with nil unit-type"
    (should (#'empire.game-mechanics.movement.visibility/should-track-free-city? :computer nil))))

(describe "was-unexplored?"
  (it "returns true for nil cell in visible map"
    (let [visible-map [[nil nil] [nil nil]]]
      (should= true (#'empire.game-mechanics.movement.visibility/was-unexplored? visible-map 0 0))))

  (it "returns true for unexplored cell"
    (let [visible-map [[{:type :unexplored} nil] [nil nil]]]
      (should= true (#'empire.game-mechanics.movement.visibility/was-unexplored? visible-map 0 0))))

  (it "returns false for revealed land cell"
    (let [visible-map [[{:type :land} nil] [nil nil]]]
      (should= false (#'empire.game-mechanics.movement.visibility/was-unexplored? visible-map 0 0))))

  (it "returns false for revealed sea cell"
    (let [visible-map [[{:type :sea} nil] [nil nil]]]
      (should= false (#'empire.game-mechanics.movement.visibility/was-unexplored? visible-map 0 0)))))

(describe "reveal-cell!"
  (before (reset-all-atoms!))

  (it "reveals a game cell in the visible map"
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (let [game-cell {:type :land}
          visible-map (test-utils/read-test-state :player-map)]
      (#'empire.game-mechanics.movement.visibility/reveal-cell!
        (test-utils/player-map-atom) 1 1 game-cell nil visible-map)
      (should= {:type :land} (get-in (test-utils/read-test-state :player-map) [1 1]))))

  (it "stamps country-id on newly-revealed land cell"
    (set-test-world! (build-test-map ["###"
                                             "###"
                                             "###"]))
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (let [game-cell {:type :land}
          visible-map (test-utils/read-test-state :player-map)]
      (#'empire.game-mechanics.movement.visibility/reveal-cell!
        (test-utils/player-map-atom) 1 1 game-cell 5 visible-map)
      (should= 5 (get-in (test-utils/read-test-state :game-map) [1 1 :country-id]))))

  (it "does not stamp country-id on sea cell"
    (set-test-world! (build-test-map ["~~~"
                                             "~~~"
                                             "~~~"]))
    (set-test-player-map! (make-initial-test-map 3 3 nil))
    (let [game-cell {:type :sea}
          visible-map (test-utils/read-test-state :player-map)]
      (#'empire.game-mechanics.movement.visibility/reveal-cell!
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
        (#'empire.game-mechanics.movement.visibility/reveal-cell!
          (test-utils/player-map-atom) 1 1 game-cell 5 visible-map)
        (should-not (get-in (test-utils/read-test-state :game-map) [1 1 :country-id]))))))

(describe "is-players?"
  (it "returns true for player city"
    (should (#'empire.game-mechanics.movement.visibility/is-players? {:type :city :city-status :player})))

  (it "returns true for cell with player unit"
    (should (#'empire.game-mechanics.movement.visibility/is-players? {:type :land :contents {:owner :player}})))

  (it "returns false for computer city"
    (should-not (#'empire.game-mechanics.movement.visibility/is-players? {:type :city :city-status :computer})))

  (it "returns false for cell with computer unit"
    (should-not (#'empire.game-mechanics.movement.visibility/is-players? {:type :land :contents {:owner :computer}})))

  (it "returns false for empty cell"
    (should-not (#'empire.game-mechanics.movement.visibility/is-players? {:type :land})))

  (it "returns false for free city"
    (should-not (#'empire.game-mechanics.movement.visibility/is-players? {:type :city :city-status :free}))))

(describe "is-computers?"
  (it "returns true for computer city"
    (should (#'empire.game-mechanics.movement.visibility/is-computers? {:type :city :city-status :computer})))

  (it "returns true for cell with computer unit"
    (should (#'empire.game-mechanics.movement.visibility/is-computers? {:type :land :contents {:owner :computer}})))

  (it "returns false for player city"
    (should-not (#'empire.game-mechanics.movement.visibility/is-computers? {:type :city :city-status :player})))

  (it "returns false for cell with player unit"
    (should-not (#'empire.game-mechanics.movement.visibility/is-computers? {:type :land :contents {:owner :player}})))

  (it "returns false for empty cell"
    (should-not (#'empire.game-mechanics.movement.visibility/is-computers? {:type :land}))))

(describe "cell-visibility-radius"
  (it "returns 1 for cell without unit"
    (should= 1 (#'empire.game-mechanics.movement.visibility/cell-visibility-radius {:type :land})))

  (it "returns 1 for cell with army"
    (should= 1 (#'empire.game-mechanics.movement.visibility/cell-visibility-radius
                  {:type :land :contents {:type :army}})))

  (it "returns 2 for cell with satellite"
    (should= 2 (#'empire.game-mechanics.movement.visibility/cell-visibility-radius
                  {:type :land :contents {:type :satellite}})))

  (it "returns 1 for cell with fighter"
    (should= 1 (#'empire.game-mechanics.movement.visibility/cell-visibility-radius
                  {:type :land :contents {:type :fighter}})))

  (it "returns 1 for city cell without unit"
    (should= 1 (#'empire.game-mechanics.movement.visibility/cell-visibility-radius
                  {:type :city :city-status :player}))))

(describe "reveal-surrounding-cells!"
  (it "reveals cells within radius 1"
    (let [game-map [[{:type :land} {:type :sea} {:type :land}]
                     [{:type :sea} {:type :land} {:type :sea}]
                     [{:type :land} {:type :sea} {:type :land}]]
          result (transient (mapv transient (vec (repeat 3 (vec (repeat 3 nil))))))]
      (#'empire.game-mechanics.movement.visibility/reveal-surrounding-cells! result game-map 1 1 3 3 1)
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
      (#'empire.game-mechanics.movement.visibility/reveal-surrounding-cells! result game-map 0 0 2 2 1)
      (let [final (mapv persistent! (persistent! result))]
        (should= {:type :land} (get-in final [0 0]))
        (should= {:type :sea} (get-in final [1 0]))
        (should= {:type :sea} (get-in final [0 1]))
        (should= {:type :land} (get-in final [1 1])))))

  (it "reveals larger area with radius 2"
    (let [game-map (vec (repeat 5 (vec (repeat 5 {:type :land}))))
          result (transient (mapv transient (vec (repeat 5 (vec (repeat 5 nil))))))]
      (#'empire.game-mechanics.movement.visibility/reveal-surrounding-cells! result game-map 2 2 5 5 2)
      (let [final (mapv persistent! (persistent! result))]
        ;; All 25 cells should be revealed (radius 2 from center of 5x5)
        (doseq [r (range 5) c (range 5)]
          (should= {:type :land} (get-in final [r c])))))))
