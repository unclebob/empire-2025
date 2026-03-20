(ns empire.game-mechanics.movement.move-current-spec
  (:require [empire.test.utils :as test-utils]
    [empire.config.core :as config]
    [empire.game.loop.core :as game-loop]
    [empire.game-mechanics.movement.explore :as explore]
    [empire.game-mechanics.movement.api :refer :all]
    [empire.game-mechanics.visibility :as visibility]
    [empire.game-mechanics.movement.wake-conditions :as wake]
    [empire.test.utils :refer [build-test-map get-test-unit set-test-unit reset-all-atoms! set-test-player-map! set-test-world! update-test-world!]]
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
      (set-test-world! (build-test-map ["##"]))
      (set-test-player-map! (build-test-map ["--"]))
      (game-loop/move-current-unit [0 0])
      (should= 2 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

    (context "single moves that awaken the unit"
      (it "moves a unit to its target and sets mode to awake"
        (set-test-world! (build-test-map ["A#"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [1 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["--"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in (test-utils/read-test-state :game-map) [1 0]))
        (should= 2 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

      (it "moves a unit up and sets mode to awake"
        (set-test-world! (build-test-map ["#A"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [0 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["--"]))
        (game-loop/move-current-unit [1 0])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [1 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= 2 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

      (it "moves a unit left and sets mode to awake"
        (set-test-world! (build-test-map ["#" "A"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [0 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["-" "-"]))
        (game-loop/move-current-unit [0 1])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 1]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= 2 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

      (it "moves a unit right and sets mode to awake"
        (set-test-world! (build-test-map ["A" "#"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [0 1] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["-" "-"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in (test-utils/read-test-state :game-map) [0 1]))
        (should= 2 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

      (it "moves a unit up-left and sets mode to awake"
        (set-test-world! (build-test-map ["#-" "-A"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [0 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["--" "--"]))
        (game-loop/move-current-unit [1 1])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [1 1]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= 2 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

      (it "moves a unit up-right and sets mode to awake"
        (set-test-world! (build-test-map ["-A" "#-"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [0 1] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["--" "--"]))
        (game-loop/move-current-unit [1 0])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [1 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in (test-utils/read-test-state :game-map) [0 1]))
        (should= 2 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

      (it "moves a unit down-left and sets mode to awake"
        (set-test-world! (build-test-map ["-#" "A-"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [1 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["--" "--"]))
        (game-loop/move-current-unit [0 1])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 1]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in (test-utils/read-test-state :game-map) [1 0]))
        (should= 2 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

      (it "moves a unit down-right and sets mode to awake"
        (set-test-world! (build-test-map ["A-" "-#"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [1 1] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["--" "--"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in (test-utils/read-test-state :game-map) [1 1]))
        (should= 2 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

      (it "wakes up a unit if the next move would be into sea"
        (set-test-world! (build-test-map ["A~"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [1 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["--"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 1 :mode :awake :reason :cant-move-into-water}} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= {:type :sea} (get-in (test-utils/read-test-state :game-map) [1 0])))

      (it "wakes up a unit if the next move would be into a friendly city"
        (set-test-world! (build-test-map ["AO"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [1 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["--"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 1 :mode :awake :reason :cant-move-into-city}} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= {:type :city :city-status :player} (get-in (test-utils/read-test-state :game-map) [1 0])))

      (it "wakes up a unit when moving near an enemy city"
        (set-test-world! (build-test-map ["A#X"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [2 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["---"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :awake :reason :army-found-city}} (get-in (test-utils/read-test-state :game-map) [1 0]))
        (should= {:type :city :city-status :computer} (get-in (test-utils/read-test-state :game-map) [2 0])))

      (it "returns nil when army wakes near enemy city with no steps remaining"
        ;; Army uses its one step, wakes near city, but can't act this round
        (set-test-world! (build-test-map ["A#X"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [2 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["---"]))
        (should= nil (game-loop/move-current-unit [0 0]))
        ;; Army should still be awake at [1 0] with reason set
        (let [unit (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
          (should= :awake (:mode unit))
          (should= :army-found-city (:reason unit))
          (should= 0 (:steps-remaining unit))))

      (it "returns position when unit wakes due to blocking"
        (set-test-world! (build-test-map ["A~"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [1 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["--"]))
        (should= [0 0] (game-loop/move-current-unit [0 0])))
      )

    (context "visibility updates"
      (it "reveals cells near player-owned units"
        (set-test-world! (build-test-map ["-----"
                                                 "-----"
                                                 "--A#-"
                                                 "--#--"
                                                 "-----"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
        (let [unit-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))
              [row col] unit-coords]
          (set-test-player-map! (build-test-map ["-----"
                                                     "-----"
                                                     "-----"
                                                     "-----"
                                                     "-----"]))
          (visibility/update-combatant-map (test-utils/player-map-atom) :player)
          ;; Check that the unit's cell and neighbors are revealed
          (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake}} (get-in (test-utils/read-test-state :player-map) unit-coords))
          (should= {:type :land} (get-in (test-utils/read-test-state :player-map) [row (inc col)]))
          (should= {:type :land} (get-in (test-utils/read-test-state :player-map) [(inc row) col]))
          (should= nil (get-in (test-utils/read-test-state :player-map) [(dec row) col]))
          (should= nil (get-in (test-utils/read-test-state :player-map) [row (dec col)]))
          (should= nil (get-in (test-utils/read-test-state :player-map) [(inc row) (inc col)]))
          (should= nil (get-in (test-utils/read-test-state :player-map) [(dec row) (dec col)]))
          (should= nil (get-in (test-utils/read-test-state :player-map) [(inc row) (dec col)]))
          (should= nil (get-in (test-utils/read-test-state :player-map) [(dec row) (inc col)]))
          ;; Check that distant cells are not revealed
          (should= nil (get-in (test-utils/read-test-state :player-map) [0 0]))
          (should= nil (get-in (test-utils/read-test-state :player-map) [4 4]))))
      )

    (context "multi-step moves take one step towards the target, keeping mode as moving"
      (it "moves a unit one step right towards target at radius 4"
        (set-test-world! (build-test-map ["A" "#" "#"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [0 2] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["-" "-" "-"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 2]}} (get-in (test-utils/read-test-state :game-map) [0 1]))
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 2]))
        (should= 3 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

      (it "moves a unit one step left towards target at radius 4"
        (set-test-world! (build-test-map ["#" "#" "A"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [0 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["-" "-" "-"]))
        (game-loop/move-current-unit [0 2])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 2]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 0]}} (get-in (test-utils/read-test-state :game-map) [0 1]))
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= 3 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

      (it "moves a unit one step up towards target at radius 4"
        (set-test-world! (build-test-map ["##A"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [0 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["---"]))
        (game-loop/move-current-unit [2 0])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [2 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 0]}} (get-in (test-utils/read-test-state :game-map) [1 0]))
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= 3 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

      (it "moves a unit one step down towards target at radius 4"
        (set-test-world! (build-test-map ["A##"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [2 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["---"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [2 0]}} (get-in (test-utils/read-test-state :game-map) [1 0]))
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [2 0]))
        (should= 3 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

      (it "moves a unit one step up-right towards target at radius 4"
        (set-test-world! (build-test-map ["--A" "-#-" "#--"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [0 2] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["---" "---" "---"]))
        (game-loop/move-current-unit [2 0])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [2 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 2]}} (get-in (test-utils/read-test-state :game-map) [1 1]))
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 2]))
        (should= 3 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

      (it "moves a unit one step up-left towards target at radius 4"
        (set-test-world! (build-test-map ["#--" "-#-" "--A"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [0 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["---" "---" "---"]))
        (game-loop/move-current-unit [2 2])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [2 2]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 0]}} (get-in (test-utils/read-test-state :game-map) [1 1]))
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= 3 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

      (it "moves a unit one step down-right towards target at radius 4"
        (set-test-world! (build-test-map ["A--" "-#-" "--#"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [2 2] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["---" "---" "---"]))
        (game-loop/move-current-unit [0 0])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [2 2]}} (get-in (test-utils/read-test-state :game-map) [1 1]))
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [2 2]))
        (should= 3 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))

      (it "moves a unit one step down-left towards target at radius 4"
        (set-test-world! (build-test-map ["--#" "-#-" "A--"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [2 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["---" "---" "---"]))
        (game-loop/move-current-unit [0 2])
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 2]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [2 0]}} (get-in (test-utils/read-test-state :game-map) [1 1]))
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [2 0]))
        (should= 3 (count (filter (complement nil?) (flatten (test-utils/read-test-state :game-map))))))
      )

    (context "multiple steps"
      (it "moves a unit two steps towards target over two calls"
        (set-test-world! (build-test-map ["A##"]))
        (set-test-unit (test-utils/game-map-atom) "A" :mode :moving :target [2 0] :steps-remaining 1)
        (set-test-player-map! (build-test-map ["---"]))
        (game-loop/move-current-unit [0 0])
        ;; After first move, unit at [1 0], still moving
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [2 0]}} (get-in (test-utils/read-test-state :game-map) [1 0]))
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [2 0]))
        ;; Give the unit another step and call again
        (update-test-world! assoc-in [1 0 :contents :steps-remaining] 1)
        (game-loop/move-current-unit [1 0])
        ;; After second move, unit at [2 0], awake
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [0 0]))
        (should= {:type :land} (get-in (test-utils/read-test-state :game-map) [1 0]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in (test-utils/read-test-state :game-map) [2 0])))
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
        (set-test-world! (build-test-map ["D~"]))
        (set-test-unit (test-utils/game-map-atom) "D" :mode :moving :target [1 0] :hits 3 :steps-remaining 1)
        (set-test-player-map! (build-test-map ["--"]))
        ;; Destroyer moving to its target should wake normally
        (game-loop/move-current-unit [0 0])
        (let [destroyer (:contents (get-in (test-utils/read-test-state :game-map) [1 0]))]
          (should= :destroyer (:type destroyer))
          (should= :awake (:mode destroyer))))

      (it "damaged ship docks at city via move-current-unit"
        (set-test-world! (build-test-map ["~D~"
                                                 "~O~"
                                                 "~~~"]))
        (set-test-unit (test-utils/game-map-atom) "D" :mode :moving :target [1 1] :hits 2 :steps-remaining 1)
        (set-test-player-map! (build-test-map ["---"
                                                   "---"
                                                   "---"]))
        (should-be-nil (game-loop/move-current-unit [1 0]))
        (let [city (get-in (test-utils/read-test-state :game-map) [1 1])]
          (should= [{:type :destroyer :hits 2}] (:shipyard city)))
        (should-not (:contents (get-in (test-utils/read-test-state :game-map) [1 0])))))

    (context "explore movement helpers"
      (it "get-unexplored-explore-moves returns moves adjacent to unexplored"
        (set-test-world! (build-test-map ["-----"
                                                 "-----"
                                                 "--##-"
                                                 "--#--"
                                                 "-----"]))
        (set-test-player-map! (build-test-map ["-----"
                                                   "-----"
                                                   "--##-"
                                                   "-----"
                                                   "-----"]))
        ;; [2 3] is unexplored in player-map, so moves from [2 2] that are adjacent to unexplored
        (let [moves (explore/get-unexplored-explore-moves [2 2] (test-utils/game-map-atom))]
          (should (some #{[2 3]} moves))))

      (it "pick-explore-move returns visited cell when all cells visited"
        (set-test-world! (build-test-map ["~~~~~"
                                                 "~~~~~"
                                                 "~~##~"
                                                 "~~#~~"
                                                 "~~~~~"]))
        (set-test-player-map! (build-test-map ["~~~~~"
                                                   "~~~~~"
                                                   "~~##~"
                                                   "~~#~~"
                                                   "~~~~~"]))
        ;; All valid moves are visited
        (let [visited #{[2 3] [3 2]}
              move (explore/pick-explore-move [2 2] (test-utils/game-map-atom) visited)]
          ;; Should still return a move even though all are visited
          (should (some #{move} [[2 3] [3 2]])))))
    )
  )
