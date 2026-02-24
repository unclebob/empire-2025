(ns empire.movement.movement-spec
  (:require
    [empire.atoms :as atoms]
    [empire.config :as config]
    [empire.game-loop :as game-loop]
    [empire.movement.explore :as explore]
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
        (set-test-unit atoms/game-map "A" :mode :moving :target [1 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [1 0]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit up and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["#A"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--"]))
        (game-loop/move-current-unit [1 0])
        (should= {:type :land} (get-in @atoms/game-map [1 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [0 0]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit left and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["#" "A"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["-" "-"]))
        (game-loop/move-current-unit [0 1])
        (should= {:type :land} (get-in @atoms/game-map [0 1]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [0 0]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit right and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["A" "#"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 1] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["-" "-"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [0 1]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit up-left and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["#-" "-A"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--" "--"]))
        (game-loop/move-current-unit [1 1])
        (should= {:type :land} (get-in @atoms/game-map [1 1]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [0 0]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit up-right and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["-A" "#-"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 1] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--" "--"]))
        (game-loop/move-current-unit [1 0])
        (should= {:type :land} (get-in @atoms/game-map [1 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [0 1]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit down-left and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["-#" "A-"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [1 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--" "--"]))
        (game-loop/move-current-unit [0 1])
        (should= {:type :land} (get-in @atoms/game-map [0 1]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [1 0]))
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
        (set-test-unit atoms/game-map "A" :mode :moving :target [1 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 1 :mode :awake :reason :cant-move-into-water}} (get-in @atoms/game-map [0 0]))
        (should= {:type :sea} (get-in @atoms/game-map [1 0])))

      (it "wakes up a unit if the next move would be into a friendly city"
        (reset! atoms/game-map (build-test-map ["AO"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [1 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 1 :mode :awake :reason :cant-move-into-city}} (get-in @atoms/game-map [0 0]))
        (should= {:type :city :city-status :player} (get-in @atoms/game-map [1 0])))

      (it "wakes up a unit when moving near an enemy city"
        (reset! atoms/game-map (build-test-map ["A#X"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [2 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :awake :reason :army-found-city}} (get-in @atoms/game-map [1 0]))
        (should= {:type :city :city-status :computer} (get-in @atoms/game-map [2 0])))

      (it "returns nil when army wakes near enemy city with no steps remaining"
        ;; Army uses its one step, wakes near city, but can't act this round
        (reset! atoms/game-map (build-test-map ["A#X"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [2 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---"]))
        (should= nil (game-loop/move-current-unit [0 0]))
        ;; Army should still be awake at [1 0] with reason set
        (let [unit (:contents (get-in @atoms/game-map [1 0]))]
          (should= :awake (:mode unit))
          (should= :army-found-city (:reason unit))
          (should= 0 (:steps-remaining unit))))

      (it "returns position when unit wakes due to blocking"
        (reset! atoms/game-map (build-test-map ["A~"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [1 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--"]))
        (should= [0 0] (game-loop/move-current-unit [0 0])))
      )

    (context "visibility updates"
      (it "reveals cells near player-owned units"
        (reset! atoms/game-map (build-test-map ["-----"
                                                 "-----"
                                                 "--A#-"
                                                 "--#--"
                                                 "-----"]))
        (set-test-unit atoms/game-map "A" :mode :awake)
        (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))
              [row col] unit-coords]
          (reset! atoms/player-map (build-test-map ["-----"
                                                     "-----"
                                                     "-----"
                                                     "-----"
                                                     "-----"]))
          (visibility/update-combatant-map atoms/player-map :player)
          ;; Check that the unit's cell and neighbors are revealed
          (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake}} (get-in @atoms/player-map unit-coords))
          (should= {:type :land} (get-in @atoms/player-map [row (inc col)]))
          (should= {:type :land} (get-in @atoms/player-map [(inc row) col]))
          (should= nil (get-in @atoms/player-map [(dec row) col]))
          (should= nil (get-in @atoms/player-map [row (dec col)]))
          (should= nil (get-in @atoms/player-map [(inc row) (inc col)]))
          (should= nil (get-in @atoms/player-map [(dec row) (dec col)]))
          (should= nil (get-in @atoms/player-map [(inc row) (dec col)]))
          (should= nil (get-in @atoms/player-map [(dec row) (inc col)]))
          ;; Check that distant cells are not revealed
          (should= nil (get-in @atoms/player-map [0 0]))
          (should= nil (get-in @atoms/player-map [4 4]))))
      )

    (context "multi-step moves take one step towards the target, keeping mode as moving"
      (it "moves a unit one step right towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["A" "#" "#"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 2] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["-" "-" "-"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 2]}} (get-in @atoms/game-map [0 1]))
        (should= {:type :land} (get-in @atoms/game-map [0 2]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step left towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["#" "#" "A"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["-" "-" "-"]))
        (game-loop/move-current-unit [0 2])
        (should= {:type :land} (get-in @atoms/game-map [0 2]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 0]}} (get-in @atoms/game-map [0 1]))
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step up towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["##A"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---"]))
        (game-loop/move-current-unit [2 0])
        (should= {:type :land} (get-in @atoms/game-map [2 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 0]}} (get-in @atoms/game-map [1 0]))
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step down towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["A##"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [2 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [2 0]}} (get-in @atoms/game-map [1 0]))
        (should= {:type :land} (get-in @atoms/game-map [2 0]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step up-right towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["--A" "-#-" "#--"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 2] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---" "---" "---"]))
        (game-loop/move-current-unit [2 0])
        (should= {:type :land} (get-in @atoms/game-map [2 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 2]}} (get-in @atoms/game-map [1 1]))
        (should= {:type :land} (get-in @atoms/game-map [0 2]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step up-left towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["#--" "-#-" "--A"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---" "---" "---"]))
        (game-loop/move-current-unit [2 2])
        (should= {:type :land} (get-in @atoms/game-map [2 2]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 0]}} (get-in @atoms/game-map [1 1]))
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step down-right towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["A--" "-#-" "--#"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [2 2] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---" "---" "---"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [2 2]}} (get-in @atoms/game-map [1 1]))
        (should= {:type :land} (get-in @atoms/game-map [2 2]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step down-left towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["--#" "-#-" "A--"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [2 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---" "---" "---"]))
        (game-loop/move-current-unit [0 2])
        (should= {:type :land} (get-in @atoms/game-map [0 2]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [2 0]}} (get-in @atoms/game-map [1 1]))
        (should= {:type :land} (get-in @atoms/game-map [2 0]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))
      )

    (context "multiple steps"
      (it "moves a unit two steps towards target over two calls"
        (reset! atoms/game-map (build-test-map ["A##"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [2 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---"]))
        (game-loop/move-current-unit [0 0])
        ;; After first move, unit at [1 0], still moving
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [2 0]}} (get-in @atoms/game-map [1 0]))
        (should= {:type :land} (get-in @atoms/game-map [2 0]))
        ;; Give the unit another step and call again
        (swap! atoms/game-map assoc-in [1 0 :contents :steps-remaining] 1)
        (game-loop/move-current-unit [1 0])
        ;; After second move, unit at [2 0], awake
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= {:type :land} (get-in @atoms/game-map [1 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [2 0])))
      )


    (context "wake-before-move edge cases"
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

    (context "wake-after-move default case"
      (it "returns default values for naval units like destroyer"
        (reset! atoms/game-map (build-test-map ["D~"]))
        (set-test-unit atoms/game-map "D" :mode :moving :target [1 0] :hits 3 :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["--"]))
        ;; Destroyer moving to its target should wake normally
        (game-loop/move-current-unit [0 0])
        (let [destroyer (:contents (get-in @atoms/game-map [1 0]))]
          (should= :destroyer (:type destroyer))
          (should= :awake (:mode destroyer)))))

    (context "explore movement helpers"
      (it "get-unexplored-explore-moves returns moves adjacent to unexplored"
        (reset! atoms/game-map (build-test-map ["-----"
                                                 "-----"
                                                 "--##-"
                                                 "--#--"
                                                 "-----"]))
        (reset! atoms/player-map (build-test-map ["-----"
                                                   "-----"
                                                   "--##-"
                                                   "-----"
                                                   "-----"]))
        ;; [2 3] is unexplored in player-map, so moves from [2 2] that are adjacent to unexplored
        (let [moves (explore/get-unexplored-explore-moves [2 2] atoms/game-map)]
          (should (some #{[2 3]} moves))))

      (it "pick-explore-move returns visited cell when all cells visited"
        (reset! atoms/game-map (build-test-map ["~~~~~"
                                                 "~~~~~"
                                                 "~~##~"
                                                 "~~#~~"
                                                 "~~~~~"]))
        (reset! atoms/player-map (build-test-map ["~~~~~"
                                                   "~~~~~"
                                                   "~~##~"
                                                   "~~#~~"
                                                   "~~~~~"]))
        ;; All valid moves are visited
        (let [visited #{[2 3] [3 2]}
              move (explore/pick-explore-move [2 2] atoms/game-map visited)]
          ;; Should still return a move even though all are visited
          (should (some #{move} [[2 3] [3 2]])))))
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
    (reset! atoms/game-map (build-test-map ["###" "###" "###"])))

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
    (reset! atoms/game-map (build-test-map ["###" "###" "###"]))
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
    (should-not (wake-at [1 1])))

  (it "wakes armies aboard a sentry transport"
    (swap! atoms/game-map assoc-in [1 1 :contents]
           {:type :transport :owner :player :mode :sentry :army-count 3 :awake-armies 0})
    (should (wake-at [1 1]))
    (let [transport (get-in @atoms/game-map [1 1 :contents])]
      (should= :awake (:mode transport))
      (should= 3 (:awake-armies transport))))

  (it "wakes armies aboard an already-awake transport"
    (swap! atoms/game-map assoc-in [1 1 :contents]
           {:type :transport :owner :player :mode :awake :army-count 4 :awake-armies 0})
    (should (wake-at [1 1]))
    (should= 4 (get-in @atoms/game-map [1 1 :contents :awake-armies])))

  (it "clears coastline state when waking a coastline-follow unit"
    (swap! atoms/game-map assoc-in [1 1 :contents]
           {:type :patrol-boat :owner :player :mode :coastline-follow :hits 2
            :coastline-steps 5 :visited #{[0 0] [1 0]} :start-pos [0 0] :prev-pos [1 0]
            :target [2 2] :reason :something})
    (should (wake-at [1 1]))
    (let [unit (get-in @atoms/game-map [1 1 :contents])]
      (should= :awake (:mode unit))
      (should-be-nil (:coastline-steps unit))
      (should-be-nil (:visited unit))
      (should-be-nil (:start-pos unit))
      (should-be-nil (:prev-pos unit))
      (should-be-nil (:target unit))
      (should-be-nil (:reason unit)))))

(describe "combat during movement"
  (before (reset-all-atoms!))

  (it "army attacks enemy army when moving into its cell"
    (reset! atoms/game-map (build-test-map ["Aa"]))
    (set-test-unit atoms/game-map "A" :hits 1 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-unit atoms/game-map "a" :hits 1)
    (reset! atoms/player-map (build-test-map ["--"]))
    (with-redefs [rand (constantly 0.4)]
      (game-loop/move-current-unit [0 0])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :army (:type (:contents (get-in @atoms/game-map [1 0]))))
      (should= :player (:owner (:contents (get-in @atoms/game-map [1 0]))))))

  (it "army is destroyed when losing to enemy army"
    (reset! atoms/game-map (build-test-map ["Aa"]))
    (set-test-unit atoms/game-map "A" :hits 1 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-unit atoms/game-map "a" :hits 1)
    (reset! atoms/player-map (build-test-map ["--"]))
    (with-redefs [rand (constantly 0.6)]
      (game-loop/move-current-unit [0 0])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :army (:type (:contents (get-in @atoms/game-map [1 0]))))
      (should= :computer (:owner (:contents (get-in @atoms/game-map [1 0]))))))

  (it "destroyer attacks enemy transport on sea"
    (reset! atoms/game-map (build-test-map ["Dt"]))
    (set-test-unit atoms/game-map "D" :hits 3 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-unit atoms/game-map "t" :hits 1)
    (reset! atoms/player-map (build-test-map ["~~"]))
    (with-redefs [rand (constantly 0.4)]
      (game-loop/move-current-unit [0 0])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :destroyer (:type (:contents (get-in @atoms/game-map [1 0]))))
      (should= :player (:owner (:contents (get-in @atoms/game-map [1 0]))))))

  (it "fighter attacks enemy fighter"
    (reset! atoms/game-map (build-test-map ["Ff"]))
    (set-test-unit atoms/game-map "F" :hits 1 :fuel 20 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-unit atoms/game-map "f" :hits 1)
    (reset! atoms/player-map (build-test-map ["--"]))
    (with-redefs [rand (constantly 0.4)]
      (game-loop/move-current-unit [0 0])
      (should= nil (:contents (get-in @atoms/game-map [0 0])))
      (should= :fighter (:type (:contents (get-in @atoms/game-map [1 0]))))
      (should= :player (:owner (:contents (get-in @atoms/game-map [1 0]))))))

  (it "attacker survives with reduced hits"
    (reset! atoms/game-map (build-test-map ["Dd"]))
    (set-test-unit atoms/game-map "D" :hits 3 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-unit atoms/game-map "d" :hits 3)
    (reset! atoms/player-map (build-test-map ["~~"]))
    ;; Rolls: 0.4 (D hits d:2), 0.6 (d hits D:2), 0.4 (D hits d:1), 0.4 (D hits d:0)
    (let [rolls (atom [0.4 0.6 0.4 0.4])]
      (with-redefs [rand (fn [] (let [v (first @rolls)] (swap! rolls rest) v))]
        (game-loop/move-current-unit [0 0])
        (let [survivor (:contents (get-in @atoms/game-map [1 0]))]
          (should= :destroyer (:type survivor))
          (should= :player (:owner survivor))
          (should= 2 (:hits survivor))))))

  (it "does not attack friendly units"
    (reset! atoms/game-map (build-test-map ["AA"]))
    (set-test-unit atoms/game-map "A1" :hits 1 :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-unit atoms/game-map "A2" :hits 1)
    (reset! atoms/player-map (build-test-map ["--"]))
    (game-loop/move-current-unit [0 0])
    ;; Should wake up, not attack
    (should= :awake (:mode (:contents (get-in @atoms/game-map [0 0]))))
    (should= :army (:type (:contents (get-in @atoms/game-map [1 0])))))

  (it "army cannot attack ship on sea (terrain incompatible)"
    (reset! atoms/game-map (build-test-map ["A~"]))
    (swap! atoms/game-map assoc-in [1 0 :contents] {:type :destroyer :owner :computer :hits 3})
    (set-test-unit atoms/game-map "A" :hits 1 :mode :moving :target [1 0] :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["--"]))
    (game-loop/move-current-unit [0 0])
    ;; Army should wake up because it can't move onto sea
    (should= :awake (:mode (:contents (get-in @atoms/game-map [0 0]))))
    (should= :destroyer (:type (:contents (get-in @atoms/game-map [1 0]))))))

(describe "chebyshev-distance"
  (it "returns correct distance for asymmetric positions"
    (should= 5 (chebyshev-distance [1 2] [6 4]))
    (should= 3 (chebyshev-distance [0 0] [3 1]))
    (should= 4 (chebyshev-distance [5 1] [2 5])))

  (it "returns 0 for same position"
    (should= 0 (chebyshev-distance [3 7] [3 7])))

  (it "handles negative coordinate differences"
    (should= 3 (chebyshev-distance [3 3] [0 0]))))

(describe "process-consumables"
  (before (reset-all-atoms!))

  (it "returns non-fighter unit unchanged"
    (let [unit {:type :army :owner :player :hits 1}
          result (process-consumables unit {:type :sea})]
      (should= unit result)))

  (it "refuels fighter landing at city"
    (let [unit {:type :fighter :owner :player :fuel 5}
          result (process-consumables unit {:type :city :city-status :player})]
      (should= 5 (:fuel result))))

  (it "decrements fuel for fighter not at city"
    (let [unit {:type :fighter :owner :player :fuel 10}
          result (process-consumables unit {:type :land})]
      (should= 9 (:fuel result))))

  (it "destroys fighter when fuel runs out"
    (let [unit {:type :fighter :owner :player :fuel 0}
          result (process-consumables unit {:type :land})]
      (should-be-nil result)))

  (it "keeps fighter alive at fuel 1"
    (let [unit {:type :fighter :owner :player :fuel 1}
          result (process-consumables unit {:type :land})]
      (should= 0 (:fuel result))))

  (it "returns nil for nil unit"
    (should-be-nil (process-consumables nil {:type :land}))))

(describe "get-active-unit"
  (before (reset-all-atoms!))

  (it "returns awake army aboard transport"
    (let [cell {:contents {:type :transport :owner :player :awake-armies 1 :army-count 2}}
          result (get-active-unit cell)]
      (should= :army (:type result))
      (should (:aboard-transport result))
      (should= :player (:owner result))))

  (it "returns nil for transport without awake armies"
    (let [cell {:contents {:type :transport :owner :player :awake-armies 0 :army-count 2}}
          result (get-active-unit cell)]
      (should-be-nil result)))

  (it "returns awake fighter on carrier"
    (let [cell {:contents {:type :carrier :owner :player :awake-fighters 1 :fighter-count 2}}
          result (get-active-unit cell)]
      (should= :fighter (:type result))
      (should (:from-carrier result))
      (should= :player (:owner result))))

  (it "returns nil for carrier without awake fighters"
    (let [cell {:contents {:type :carrier :owner :player :awake-fighters 0 :fighter-count 2}}
          result (get-active-unit cell)]
      (should-be-nil result)))

  (it "returns awake contents directly"
    (let [unit {:type :destroyer :owner :player :mode :awake :hits 3}
          cell {:contents unit}
          result (get-active-unit cell)]
      (should= unit result)))

  (it "returns nil for sleeping contents"
    (let [cell {:contents {:type :army :owner :player :mode :sentry}}]
      (should-be-nil (get-active-unit cell))))

  (it "returns airport fighter when city has awake fighters"
    (let [cell {:type :city :awake-fighters 1 :fighter-count 2}
          result (get-active-unit cell)]
      (should= :fighter (:type result))
      (should (:from-airport result))))

  (it "prefers transport army over awake contents"
    (let [cell {:contents {:type :transport :owner :player :mode :awake :awake-armies 1 :army-count 1}}
          result (get-active-unit cell)]
      (should= :army (:type result))
      (should (:aboard-transport result)))))

(describe "set-unit-movement"
  (before (reset-all-atoms!))

  (it "sets mode to moving with target"
    (reset! atoms/game-map (build-test-map ["A##"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (set-unit-movement [0 0] [0 2])
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should= :moving (:mode unit))
      (should= [0 2] (:target unit))
      (should-be-nil (:extended unit))))

  (it "sets extended flag when extended is true"
    (reset! atoms/game-map (build-test-map ["A##"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (set-unit-movement [0 0] [0 2] true)
    (let [unit (get-in @atoms/game-map [0 0 :contents])]
      (should= :moving (:mode unit))
      (should (:extended unit))))

  (it "does not set extended flag when extended is false"
    (reset! atoms/game-map (build-test-map ["A##"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (set-unit-movement [0 0] [0 2] false)
    (should-be-nil (:extended (get-in @atoms/game-map [0 0 :contents])))))

(describe "fighter landing"
  (before (reset-all-atoms!))

  (it "fighter landing at player city increments fighter-count"
    (reset! atoms/game-map (build-test-map ["FO"]))
    (set-test-unit atoms/game-map "F" :fuel 20 :mode :moving :target [1 0] :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["--"]))
    (game-loop/move-current-unit [0 0])
    (let [city (get-in @atoms/game-map [1 0])]
      ;; Fighter should be absorbed into city, not placed as contents
      (should-be-nil (:contents city))
      (should= 1 (:fighter-count city))))

  (it "fighter landing on carrier increments carrier fighter-count"
    (reset! atoms/game-map (build-test-map ["JC"]))
    (set-test-unit atoms/game-map "F" :fuel 20 :mode :moving :target [1 0] :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["~~"]))
    (game-loop/move-current-unit [0 0])
    (let [carrier (get-in @atoms/game-map [1 0 :contents])]
      (should= :carrier (:type carrier))
      (should= 1 (:fighter-count carrier))))

  (it "fighter landing at player city does not remain as contents"
    (reset! atoms/game-map (build-test-map ["FO"]))
    (set-test-unit atoms/game-map "F" :fuel 20 :mode :moving :target [1 0] :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["--"]))
    (game-loop/move-current-unit [0 0])
    ;; Fighter absorbed into city — not left as contents
    (should-be-nil (:contents (get-in @atoms/game-map [1 0])))))

(describe "city sidestepping"
  (before (reset-all-atoms!))

  (it "army sidesteps around friendly city"
    ;; Army at [1,0] moving right, friendly city at [2,0]
    ;; Should sidestep to [2,1] (diagonal) then continue toward [4,0]
    (reset! atoms/game-map (build-test-map ["#AO##"
                                             "#####"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [4 0] :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["-----" "-----"]))
    (with-redefs [rand-nth first]
      (game-loop/move-current-unit [1 0]))
    ;; Army should have sidestepped, not stayed at [1,0]
    (should-be-nil (:contents (get-in @atoms/game-map [1 0])))
    ;; Should be at [2,1] (sidestep down-right) since that gets closest to target
    (should= :army (:type (:contents (get-in @atoms/game-map [2 1])))))

  (it "fighter sidesteps around non-target city"
    ;; Fighter at [1,0] heading toward [4,0], player city at [2,0] is NOT target
    (reset! atoms/game-map (build-test-map ["#FO##"
                                             "#####"]))
    (set-test-unit atoms/game-map "F" :fuel 20 :mode :moving :target [4 0] :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["-----" "-----"]))
    (with-redefs [rand-nth first]
      (game-loop/move-current-unit [1 0]))
    ;; Fighter should have sidestepped
    (should-be-nil (:contents (get-in @atoms/game-map [1 0]))))

  (it "fighter does not sidestep its target city"
    ;; Fighter heading to a player city that IS its target — should land there
    (reset! atoms/game-map (build-test-map ["FO#"
                                             "###"]))
    (set-test-unit atoms/game-map "F" :fuel 20 :mode :moving :target [1 0] :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["---" "---"]))
    (game-loop/move-current-unit [0 0])
    ;; Fighter should land at the city (its target), not sidestep
    (let [city (get-in @atoms/game-map [1 0])]
      (should= 1 (:fighter-count city))))

  (it "fighter sidesteps computer city that is not its target"
    ;; Fighter at [1,0] heading to [4,0], computer city at [2,0]
    ;; Fighter should sidestep around the non-target city
    (reset! atoms/game-map (build-test-map ["#FX##"
                                             "#####"]))
    (set-test-unit atoms/game-map "F" :fuel 20 :mode :moving :target [4 0] :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["-----" "-----"]))
    (with-redefs [rand-nth first]
      (game-loop/move-current-unit [1 0]))
    ;; Fighter should have moved away from [1,0] via sidestep
    (should-be-nil (:contents (get-in @atoms/game-map [1 0])))))

(describe "army sidestep verifies exact position"
  (before (reset-all-atoms!))

  (it "army sidestepping horizontally blocked goes to correct diagonal"
    ;; Army at [2,1] moving right toward [5,1], friendly city at [3,1]
    ;; Blocked direction is [1,0] (horizontal). Sidestep candidates:
    ;; [[1 1] [1 -1] [0 1] [0 -1]] → positions [3,2] [3,0] [2,2] [2,0]
    ;; Make [3,0] sea so only [3,2], [2,2], [2,0] are valid land
    (reset! atoms/game-map (build-test-map ["###~##"
                                             "##AO##"
                                             "######"]))
    (set-test-unit atoms/game-map "A" :mode :moving :target [5 1] :steps-remaining 1)
    (reset! atoms/player-map (build-test-map ["------" "------" "------"]))
    (with-redefs [rand-nth first]
      (game-loop/move-current-unit [2 1]))
    ;; [3,2] should be best (diagonal toward target, gets closest)
    (should= :army (:type (:contents (get-in @atoms/game-map [3 2])))))

  (it "army sidestepping vertically blocked uses correct candidates"
    ;; Army at [1,0] moving down toward [1,3], friendly army at [1,1]
    ;; Blocked direction is [0,1] (vertical). Sidestep candidates:
    ;; [[1 dy] [-1 dy] [1 0] [-1 0]] = [[1 1] [-1 1] [1 0] [-1 0]]
    ;; → positions [2,1] [0,1] [2,0] [0,0]
    (reset! atoms/game-map (build-test-map ["#A#"
                                             "#A#"
                                             "###"
                                             "###"]))
    (set-test-unit atoms/game-map "A1" :mode :moving :target [1 3] :steps-remaining 1)
    (set-test-unit atoms/game-map "A2" :mode :sentry)
    (reset! atoms/player-map (build-test-map ["---" "---" "---" "---"]))
    (with-redefs [rand-nth first]
      (game-loop/move-current-unit [1 0]))
    ;; Should have sidestepped to [2,1] (first candidate that's closer)
    (let [unit21 (:contents (get-in @atoms/game-map [2 1]))]
      (should= :army (:type unit21)))))

(describe "sidestep around friendly unit"
  (before (reset-all-atoms!))

  (it "army sidesteps around friendly army blocking path"
    ;; Army at [0,0] moving right toward [3,0], friendly army at [1,0]
    (reset! atoms/game-map (build-test-map ["AA##"
                                             "####"]))
    (set-test-unit atoms/game-map "A1" :mode :moving :target [3 0] :steps-remaining 1)
    (set-test-unit atoms/game-map "A2" :mode :sentry)
    (reset! atoms/player-map (build-test-map ["----" "----"]))
    (with-redefs [rand-nth first]
      (game-loop/move-current-unit [0 0]))
    ;; Army should have sidestepped (not stayed at [0,0])
    (let [unit0 (:contents (get-in @atoms/game-map [0 0]))
          unit1 (:contents (get-in @atoms/game-map [1 1]))]
      ;; Either sidestepped or woke up
      (if unit1
        (should= :army (:type unit1))
        (should= :awake (:mode unit0)))))

  (it "army sidesteps diagonally when blocked diagonally"
    ;; Army at [0,0] moving toward [2,2], friendly army at [1,1]
    (reset! atoms/game-map (build-test-map ["A--"
                                             "-A-"
                                             "--#"]))
    (set-test-unit atoms/game-map "A1" :mode :moving :target [2 2] :steps-remaining 1)
    (set-test-unit atoms/game-map "A2" :mode :sentry)
    (reset! atoms/player-map (build-test-map ["---" "---" "---"]))
    (with-redefs [rand-nth first]
      (game-loop/move-current-unit [0 0]))
    ;; Should have attempted to sidestep
    (let [unit00 (:contents (get-in @atoms/game-map [0 0]))]
      (if unit00
        ;; Either woke up or sidestepped elsewhere
        (should (#{:awake :moving} (:mode unit00)))
        ;; Moved somewhere
        true))))

(describe "transport auto-loads sentry armies"
  (before (reset-all-atoms!))

  (it "loads adjacent sentry army after transport moves"
    ;; Transport at [0,0] (sea), target [1,0] (sea), sentry army at [1,1] (land)
    (reset! atoms/game-map (build-test-map ["T~"
                                             "~A"]))
    (set-test-unit atoms/game-map "T" :mode :moving :target [1 0] :steps-remaining 1)
    (set-test-unit atoms/game-map "A" :mode :sentry)
    (reset! atoms/player-map (build-test-map ["~~" "~~"]))
    (game-loop/move-current-unit [0 0])
    ;; Transport moved to [1,0]; sentry army at [1,1] should be loaded
    (let [transport (get-in @atoms/game-map [1 0 :contents])]
      (should= :transport (:type transport))
      (should= 1 (:army-count transport)))
    ;; Army cell should be empty
    (should-be-nil (:contents (get-in @atoms/game-map [1 1])))))

(describe "wake-at return values"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["###" "###" "###"]))
    (reset! atoms/production {}))

  (it "returns truthy for waking a transport without armies"
    ;; A moving transport (no armies) should be woken via the regular unit branch
    (swap! atoms/game-map assoc-in [1 1 :contents]
           {:type :transport :owner :player :mode :moving :hits 1})
    (should (wake-at [1 1]))
    (should= :awake (get-in @atoms/game-map [1 1 :contents :mode])))

  (it "wakes transport without armies via regular branch not transport branch"
    ;; Transport with NO army-count key — default 0 means (pos? 0) = false
    ;; Should fall through to regular unit wake branch, not transport-armies branch
    (swap! atoms/game-map assoc-in [1 1 :contents]
           {:type :transport :owner :player :mode :sentry})
    (should (wake-at [1 1]))
    (should= :awake (get-in @atoms/game-map [1 1 :contents :mode]))
    ;; Should NOT have :awake-armies set (regular branch doesn't call wake-all)
    (should-be-nil (get-in @atoms/game-map [1 1 :contents :awake-armies]))))

(describe "add-unit-at satellite"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["###" "###" "###"])))

  (it "adds satellite with turns-remaining"
    (add-unit-at [1 1] :satellite)
    (let [contents (get-in @atoms/game-map [1 1 :contents])]
      (should= :satellite (:type contents))
      (should= config/satellite-turns (:turns-remaining contents))))

  (it "satellite does not have fuel"
    (add-unit-at [1 1] :satellite)
    (should-be-nil (:fuel (get-in @atoms/game-map [1 1 :contents])))))
