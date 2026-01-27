(ns empire.movement.movement-spec
  (:require
    [empire.atoms :as atoms]
    [empire.config :as config]
    [empire.game-loop :as game-loop]
    [empire.movement.explore :as explore]
    [empire.movement.map-utils :as map-utils]
    [empire.movement.movement :refer :all]
    [empire.movement.visibility :as visibility]
    [empire.movement.wake-conditions :as wake]
    [empire.test-utils :refer [build-test-map get-test-unit set-test-unit reset-all-atoms!]]
    [speclj.core :refer :all]))

(defn move-until-done
  "Helper to move a unit until it stops (returns nil)."
  [coords]
  (loop [current coords]
    (when-let [next-coords (game-loop/move-current-unit current)]
      (recur next-coords))))

(describe "movement"
  (before (reset-all-atoms!))
  (context "move-current-unit"
    (it "does nothing if no unit"
      (reset! atoms/game-map (build-test-map ["##"]))
      (reset! atoms/player-map (build-test-map ["--"]))
      (game-loop/move-current-unit [0 0])
      (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

    (context "single moves that awaken the unit"
      (it "moves a unit to its target and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["A#"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 1] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [0 1]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit up and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["#A"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--"]))
        (game-loop/move-current-unit [0 1])
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [0 0]))
        (should= {:type :land} (get-in @atoms/game-map [0 1]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit left and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["#" "A"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["-" "-"]))
        (game-loop/move-current-unit [1 0])
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [0 0]))
        (should= {:type :land} (get-in @atoms/game-map [1 0]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit right and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["A" "#"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [1 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["-" "-"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [1 0]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit up-left and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["#-" "-A"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--" "--"]))
        (game-loop/move-current-unit [1 1])
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [0 0]))
        (should= {:type :land} (get-in @atoms/game-map [1 1]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit up-right and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["-A" "#-"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [1 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--" "--"]))
        (game-loop/move-current-unit [0 1])
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [1 0]))
        (should= {:type :land} (get-in @atoms/game-map [0 1]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit down-left and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["-#" "A-"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 1] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--" "--"]))
        (game-loop/move-current-unit [1 0])
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [0 1]))
        (should= {:type :land} (get-in @atoms/game-map [1 0]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit down-right and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["A-" "-#"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [1 1] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--" "--"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [1 1]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "wakes up a unit if the next move would be into sea"
        (reset! atoms/game-map (build-test-map ["A~"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 1] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 1 :mode :awake :reason :cant-move-into-water}} (get-in @atoms/game-map [0 0]))
        (should= {:type :sea} (get-in @atoms/game-map [0 1])))

      (it "wakes up a unit if the next move would be into a friendly city"
        (reset! atoms/game-map (build-test-map ["AO"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 1] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 1 :mode :awake :reason :cant-move-into-city}} (get-in @atoms/game-map [0 0]))
        (should= {:type :city :city-status :player} (get-in @atoms/game-map [0 1])))

      (it "wakes up a unit when moving near an enemy city"
        (reset! atoms/game-map (build-test-map ["A#X"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 2] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :awake :reason :army-found-city}} (get-in @atoms/game-map [0 1]))
        (should= {:type :city :city-status :computer} (get-in @atoms/game-map [0 2])))
      )

    (context "visibility updates"
      (it "reveals cells near player-owned units"
        (reset! atoms/game-map (build-test-map ["---"
                                                 "-A#"
                                                 "-#-"]))
        (set-test-unit atoms/game-map "A" :mode :awake)
        (reset! atoms/player-map (build-test-map ["---"
                                                   "---"
                                                   "---"]))
        (visibility/update-combatant-map atoms/player-map :player)
        ;; Check that the unit's cell and neighbors are revealed
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake}} (get-in @atoms/player-map [1 1]))
        (should= {:type :land} (get-in @atoms/player-map [1 2]))
        (should= {:type :land} (get-in @atoms/player-map [2 1]))
        (should= nil (get-in @atoms/player-map [0 1]))
        (should= nil (get-in @atoms/player-map [1 0]))
        ;; Check that distant cells are not revealed
        (should= nil (get-in @atoms/player-map [0 0]))
        (should= nil (get-in @atoms/player-map [2 2])))
      )

    (context "multi-step moves take one step towards the target, keeping mode as moving"
      (it "moves a unit one step right towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["A###"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 3] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["----"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 3]}} (get-in @atoms/game-map [0 1]))
        (should= {:type :land} (get-in @atoms/game-map [0 3]))
        (should= 4 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step left towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["###A"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["----"]))
        (game-loop/move-current-unit [0 3])
        (should= {:type :land} (get-in @atoms/game-map [0 3]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 0]}} (get-in @atoms/game-map [0 2]))
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= 4 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step up towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["#" "#" "#" "A"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["-" "-" "-" "-"]))
        (game-loop/move-current-unit [3 0])
        (should= {:type :land} (get-in @atoms/game-map [3 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 0]}} (get-in @atoms/game-map [2 0]))
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= 4 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step down towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["A" "#" "#" "#"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [3 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["-" "-" "-" "-"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [3 0]}} (get-in @atoms/game-map [1 0]))
        (should= {:type :land} (get-in @atoms/game-map [3 0]))
        (should= 4 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step diagonally towards distant target"
        (reset! atoms/game-map (build-test-map ["--A-"
                                                 "-#--"
                                                 "#---"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [2 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["----"
                                                   "----"
                                                   "----"]))
        (game-loop/move-current-unit [0 2])
        (should= {:type :land} (get-in @atoms/game-map [0 2]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [2 0]}} (get-in @atoms/game-map [1 1]))
        (should= {:type :land} (get-in @atoms/game-map [2 0]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step diagonally up-left towards distant target"
        (reset! atoms/game-map (build-test-map ["#---"
                                                 "-#--"
                                                 "--A-"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["----"
                                                   "----"
                                                   "----"]))
        (game-loop/move-current-unit [2 2])
        (should= {:type :land} (get-in @atoms/game-map [2 2]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 0]}} (get-in @atoms/game-map [1 1]))
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step diagonally down-right towards distant target"
        (reset! atoms/game-map (build-test-map ["A---"
                                                 "-#--"
                                                 "--#-"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [2 2] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["----"
                                                   "----"
                                                   "----"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [2 2]}} (get-in @atoms/game-map [1 1]))
        (should= {:type :land} (get-in @atoms/game-map [2 2]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step diagonally down-left towards distant target"
        (reset! atoms/game-map (build-test-map ["---A"
                                                 "--#-"
                                                 "-#--"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [2 1] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["----"
                                                   "----"
                                                   "----"]))
        (game-loop/move-current-unit [0 3])
        (should= {:type :land} (get-in @atoms/game-map [0 3]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [2 1]}} (get-in @atoms/game-map [1 2]))
        (should= {:type :land} (get-in @atoms/game-map [2 1]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))
      )

    (context "multiple steps"
      (it "moves a unit two steps towards target over two calls"
        (reset! atoms/game-map (build-test-map ["A##"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 2] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---"]))
        (game-loop/move-current-unit [0 0])
        ;; After first move, unit at [0 1], still moving
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 2]}} (get-in @atoms/game-map [0 1]))
        (should= {:type :land} (get-in @atoms/game-map [0 2]))
        ;; Give the unit another step and call again
        (swap! atoms/game-map assoc-in [0 1 :contents :steps-remaining] 1)
        (game-loop/move-current-unit [0 1])
        ;; After second move, unit at [0 2], awake
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land} (get-in @atoms/game-map [0 1]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [0 2])))
      )

    (describe "is-computers?"
      (it "returns true for computer city"
        (let [cell {:type :city :city-status :computer}]
          (should (map-utils/is-computers? cell))))

      (it "returns true for cell with computer unit"
        (let [cell {:type :land :contents {:type :army :owner :computer}}]
          (should (map-utils/is-computers? cell))))

      (it "returns false for player city"
        (let [cell {:type :city :city-status :player}]
          (should-not (map-utils/is-computers? cell))))

      (it "returns false for cell with player unit"
        (let [cell {:type :land :contents {:type :army :owner :player}}]
          (should-not (map-utils/is-computers? cell))))

      (it "returns false for empty cell"
        (let [cell {:type :land}]
          (should-not (map-utils/is-computers? cell)))))

    (describe "wake-before-move edge cases"
      (it "wakes unit when something is in the way"
        (let [unit {:type :army :mode :moving :owner :player :target [4 5] :steps-remaining 1}
              next-cell {:type :land :contents {:type :army :owner :player}}
              [result should-wake?] (wake/wake-before-move unit next-cell)]
          (should= :awake (:mode result))
          (should= :somethings-in-the-way (:reason result))
          (should should-wake?)))

      (it "wakes naval unit when trying to move on land"
        (let [unit {:type :destroyer :mode :moving :owner :player :target [4 5] :steps-remaining 1}
              next-cell {:type :land}
              [result should-wake?] (wake/wake-before-move unit next-cell)]
          (should= :awake (:mode result))
          (should= :ships-cant-drive-on-land (:reason result))
          (should should-wake?))))

    (describe "wake-after-move default case"
      (it "returns default values for naval units like destroyer"
        (reset! atoms/game-map (build-test-map ["D~"]))
        (set-test-unit atoms/game-map "D" :mode :moving :target [0 1] :hits 3 :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["~~"]))
        ;; Destroyer moving to its target should wake normally
        (game-loop/move-current-unit [0 0])
        (let [destroyer (:contents (get-in @atoms/game-map [0 1]))]
          (should= :destroyer (:type destroyer))
          (should= :awake (:mode destroyer)))))

    (describe "explore movement helpers"
      (it "get-unexplored-explore-moves returns moves adjacent to unexplored"
        (reset! atoms/game-map (build-test-map ["##-"
                                                 "#--"]))
        (reset! atoms/player-map (build-test-map ["##-"
                                                   "---"]))
        ;; [1 0] is unexplored in player-map, so moves from [0 0] that are adjacent to unexplored
        (let [moves (explore/get-unexplored-explore-moves [0 0] atoms/game-map)]
          (should (some #{[1 0]} moves))))

      (it "pick-explore-move returns visited cell when all cells visited"
        (reset! atoms/game-map (build-test-map ["~~#"
                                                 "~##"]))
        (reset! atoms/player-map (build-test-map ["~~#"
                                                   "~##"]))
        ;; All valid moves are visited
        (let [visited #{[0 2] [1 1] [1 2]}
              move (explore/pick-explore-move [0 2] atoms/game-map visited)]
          ;; Should still return a move even though all are visited
          (should (some #{move} [[1 1] [1 2]])))))
    )
  )

(describe "movement-context"
  (before (reset-all-atoms!))
  (it "returns :airport-fighter for fighter from airport"
    (let [cell {:type :city :awake-fighters 1}
          unit {:type :fighter :from-airport true}]
      (should= :airport-fighter (movement-context cell unit))))

  (it "returns :carrier-fighter for fighter from carrier"
    (let [cell {:contents {:type :carrier}}
          unit {:type :fighter :from-carrier true}]
      (should= :carrier-fighter (movement-context cell unit))))

  (it "returns :army-aboard for army aboard transport"
    (let [cell {:contents {:type :transport}}
          unit {:type :army :aboard-transport true}]
      (should= :army-aboard (movement-context cell unit))))

  (it "returns :standard-unit for regular unit"
    (let [cell {:contents {:type :army}}
          unit {:type :army :mode :awake}]
      (should= :standard-unit (movement-context cell unit))))

  (it "returns :standard-unit for nil unit"
    (should= :standard-unit (movement-context {} nil))))

(describe "add-unit-at"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["###"
                                             "###"
                                             "###"])))

  (it "adds army unit at empty cell"
    (add-unit-at [1 1] :army)
    (let [contents (get-in @atoms/game-map [1 1 :contents])]
      (should= :army (:type contents))
      (should= :player (:owner contents))
      (should= :awake (:mode contents))
      (should= (config/item-hits :army) (:hits contents))))

  (it "adds fighter with fuel"
    (add-unit-at [1 1] :fighter)
    (let [contents (get-in @atoms/game-map [1 1 :contents])]
      (should= :fighter (:type contents))
      (should= config/fighter-fuel (:fuel contents))))

  (it "does not add unit if cell has contents"
    (swap! atoms/game-map assoc-in [1 1 :contents] {:type :army :owner :computer})
    (add-unit-at [1 1] :carrier)
    (should= :army (get-in @atoms/game-map [1 1 :contents :type])))

  (it "adds computer-owned army when owner is :computer"
    (add-unit-at [1 1] :army :computer)
    (let [contents (get-in @atoms/game-map [1 1 :contents])]
      (should= :army (:type contents))
      (should= :computer (:owner contents))
      (should= :awake (:mode contents))))

  (it "adds computer-owned destroyer when owner is :computer"
    (add-unit-at [2 2] :destroyer :computer)
    (let [contents (get-in @atoms/game-map [2 2 :contents])]
      (should= :destroyer (:type contents))
      (should= :computer (:owner contents))))

  (it "defaults to player owner when not specified"
    (add-unit-at [0 0] :transport)
    (should= :player (get-in @atoms/game-map [0 0 :contents :owner]))))

(describe "wake-at"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["###"
                                             "###"
                                             "###"]))
    (reset! atoms/production {}))

  (it "wakes a sleeping unit"
    (swap! atoms/game-map assoc-in [1 1 :contents]
           {:type :army :owner :player :mode :sentry})
    (should (wake-at [1 1]))
    (should= :awake (get-in @atoms/game-map [1 1 :contents :mode])))

  (it "wakes unit in explore mode"
    (swap! atoms/game-map assoc-in [1 1 :contents]
           {:type :army :owner :player :mode :explore})
    (should (wake-at [1 1]))
    (should= :awake (get-in @atoms/game-map [1 1 :contents :mode])))

  (it "returns nil for already awake unit"
    (swap! atoms/game-map assoc-in [1 1 :contents]
           {:type :army :owner :player :mode :awake})
    (should-not (wake-at [1 1])))

  (it "returns nil for enemy unit"
    (swap! atoms/game-map assoc-in [1 1 :contents]
           {:type :army :owner :computer :mode :sentry})
    (should-not (wake-at [1 1])))

  (it "wakes player city and removes production"
    (swap! atoms/game-map assoc-in [1 1]
           {:type :city :city-status :player :sleeping-fighters 0 :awake-fighters 0})
    (reset! atoms/production {[1 1] {:item :army :remaining-rounds 5}})
    (should (wake-at [1 1]))
    (should-not (get @atoms/production [1 1])))

  (it "returns nil for empty cell"
    (should-not (wake-at [1 1])))

  (it "returns nil for enemy city"
    (swap! atoms/game-map assoc-in [1 1]
           {:type :city :city-status :computer})
    (should-not (wake-at [1 1]))))

(describe "combat during movement"
  (before (reset-all-atoms!))

  (it "army attacks enemy army when moving into its cell"
    (reset! atoms/game-map (build-test-map ["Aa"]))
    (set-test-unit atoms/game-map "A" :hits 1 :mode :moving :target [0 1] :steps-remaining 1)
    (set-test-unit atoms/game-map "a" :hits 1)
    (reset! atoms/player-map (build-test-map ["--"]))
    (with-redefs [rand (constantly 0.4)]
      (game-loop/move-current-unit [0 0])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :army (:type (:contents (get-in @atoms/game-map [0 1]))))
      (should= :player (:owner (:contents (get-in @atoms/game-map [0 1]))))))

  (it "army is destroyed when losing to enemy army"
    (reset! atoms/game-map (build-test-map ["Aa"]))
    (set-test-unit atoms/game-map "A" :hits 1 :mode :moving :target [0 1] :steps-remaining 1)
    (set-test-unit atoms/game-map "a" :hits 1)
    (reset! atoms/player-map (build-test-map ["--"]))
    (with-redefs [rand (constantly 0.6)]
      (game-loop/move-current-unit [0 0])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :army (:type (:contents (get-in @atoms/game-map [0 1]))))
      (should= :computer (:owner (:contents (get-in @atoms/game-map [0 1]))))))

  (it "destroyer attacks enemy transport on sea"
    (reset! atoms/game-map (build-test-map ["Dt"]))
    (set-test-unit atoms/game-map "D" :hits 3 :mode :moving :target [0 1] :steps-remaining 1)
    (set-test-unit atoms/game-map "t" :hits 1)
    (reset! atoms/player-map (build-test-map ["~~"]))
    (with-redefs [rand (constantly 0.4)]
      (game-loop/move-current-unit [0 0])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :destroyer (:type (:contents (get-in @atoms/game-map [0 1]))))
      (should= :player (:owner (:contents (get-in @atoms/game-map [0 1]))))))

  (it "fighter attacks enemy fighter"
    (reset! atoms/game-map (build-test-map ["Ff"]))
    (set-test-unit atoms/game-map "F" :hits 1 :fuel 20 :mode :moving :target [0 1] :steps-remaining 1)
    (set-test-unit atoms/game-map "f" :hits 1)
    (reset! atoms/player-map (build-test-map ["--"]))
    (with-redefs [rand (constantly 0.4)]
      (game-loop/move-current-unit [0 0])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :fighter (:type (:contents (get-in @atoms/game-map [0 1]))))
      (should= :player (:owner (:contents (get-in @atoms/game-map [0 1]))))))

  (it "attacker survives with reduced hits"
    (reset! atoms/game-map (build-test-map ["Dd"]))
    (set-test-unit atoms/game-map "D" :hits 3 :mode :moving :target [0 1] :steps-remaining 1)
    (set-test-unit atoms/game-map "d" :hits 3)
    (reset! atoms/player-map (build-test-map ["~~"]))
    ;; Rolls: 0.4 (D hits d:2), 0.6 (d hits D:2), 0.4 (D hits d:1), 0.4 (D hits d:0)
    (let [rolls (atom [0.4 0.6 0.4 0.4])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (game-loop/move-current-unit [0 0])
        (let [survivor (:contents (get-in @atoms/game-map [0 1]))]
          (should= :destroyer (:type survivor))
          (should= :player (:owner survivor))
          (should= 2 (:hits survivor))))))

  (it "does not attack friendly units"
    (reset! atoms/game-map (build-test-map ["AA"]))
    (set-test-unit atoms/game-map "A1" :hits 1 :mode :moving :target [0 1] :steps-remaining 1)
    (set-test-unit atoms/game-map "A2" :hits 1)
    (reset! atoms/player-map (build-test-map ["--"]))
    (game-loop/move-current-unit [0 0])
    ;; Should wake up, not attack
    (should= :awake (:mode (:contents (get-in @atoms/game-map [0 0]))))
    (should= :army (:type (:contents (get-in @atoms/game-map [0 1])))))

  (it "army cannot attack ship on sea (terrain incompatible)"
    (reset! atoms/game-map (build-test-map ["A~"]))
    (swap! atoms/game-map assoc-in [0 1 :contents] {:type :destroyer :owner :computer :hits 3})
    (set-test-unit atoms/game-map "A" :hits 1 :mode :moving :target [0 1] :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["--"]))
    (game-loop/move-current-unit [0 0])
    ;; Army should wake up because it can't move onto sea
    (should= :awake (:mode (:contents (get-in @atoms/game-map [0 0]))))
    (should= :destroyer (:type (:contents (get-in @atoms/game-map [0 1]))))))
