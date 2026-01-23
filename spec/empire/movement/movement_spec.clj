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
      (reset! atoms/game-map (build-test-map ["---------"
                                               "---------"
                                               "---------"
                                               "---------"
                                               "----##---"
                                               "---------"
                                               "---------"
                                               "---------"
                                               "---------"]))
      (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
      (game-loop/move-current-unit [4 4])
      (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

    (context "single moves that awaken the unit"
      (it "moves a unit to its target and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "----A#---"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))
              target-coords [(first unit-coords) (inc (second unit-coords))]]
          (set-test-unit atoms/game-map "A" :mode :moving :target target-coords :steps-remaining 1)
          (reset! atoms/player-map (build-test-map ["---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"]))
          (game-loop/move-current-unit unit-coords)
          (should= {:type :land} (get-in @atoms/game-map unit-coords))
          (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map target-coords))
          (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map))))))

      (it "moves a unit up and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---#A----"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [4 3] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [4 3]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit left and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "----#----"
                                                 "----A----"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [3 4] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [3 4]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit right and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "----A----"
                                                 "----#----"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [5 4] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [5 4]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit up-left and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---#-----"
                                                 "----A----"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [3 3] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [3 3]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit up-right and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "----A----"
                                                 "---#-----"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [5 3] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [5 3]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit down-left and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "-----#---"
                                                 "----A----"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [3 5] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [3 5]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit down-right and sets mode to awake"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "----A----"
                                                 "-----#---"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [5 5] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [5 5]))
        (should= 2 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "wakes up a unit if the next move would be into sea"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "----A~---"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))
              target-coords [(first unit-coords) (inc (second unit-coords))]]
          (set-test-unit atoms/game-map "A" :mode :moving :target target-coords :steps-remaining 1)
          (reset! atoms/player-map (build-test-map ["---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"]))
          (game-loop/move-current-unit unit-coords)
          (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 1 :mode :awake :reason :cant-move-into-water}} (get-in @atoms/game-map unit-coords))
          (should= {:type :sea} (get-in @atoms/game-map target-coords))))

      (it "wakes up a unit if the next move would be into a friendly city"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "----AO---"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [4 5] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 1 :mode :awake :reason :cant-move-into-city}} (get-in @atoms/game-map [4 4]))
        (should= {:type :city :city-status :player} (get-in @atoms/game-map [4 5])))

      (it "wakes up a unit when moving near an enemy city"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "----A#X--"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [4 6] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :awake :reason :army-found-city}} (get-in @atoms/game-map [4 5]))
        (should= {:type :city :city-status :computer} (get-in @atoms/game-map [4 6])))
      )

    (context "visibility updates"
      (it "reveals cells near player-owned units"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "----A#---"
                                                 "----#----"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :awake)
        (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))
              [row col] unit-coords]
          (reset! atoms/player-map (build-test-map ["---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"
                                                     "---------"]))
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
          (should= nil (get-in @atoms/player-map [8 8]))))
      )

    (context "multi-step moves take one step towards the target, keeping mode as moving"
      (it "moves a unit one step right towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "----A----"
                                                 "----#----"
                                                 "---------"
                                                 "---------"
                                                 "----#----"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [8 4] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [8 4]}} (get-in @atoms/game-map [5 4]))
        (should= {:type :land} (get-in @atoms/game-map [8 4]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step left towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["----#----"
                                                 "---------"
                                                 "---------"
                                                 "----#----"
                                                 "----A----"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 4] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 4]}} (get-in @atoms/game-map [3 4]))
        (should= {:type :land} (get-in @atoms/game-map [0 4]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step up towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "#--#A----"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [4 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [4 0]}} (get-in @atoms/game-map [4 3]))
        (should= {:type :land} (get-in @atoms/game-map [4 0]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step down towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "----A#--#"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [4 8] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [4 8]}} (get-in @atoms/game-map [4 5]))
        (should= {:type :land} (get-in @atoms/game-map [4 8]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step up-right towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "----A----"
                                                 "---#-----"
                                                 "---------"
                                                 "---------"
                                                 "#--------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [8 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [8 0]}} (get-in @atoms/game-map [5 3]))
        (should= {:type :land} (get-in @atoms/game-map [8 0]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step up-left towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["#--------"
                                                 "---------"
                                                 "---------"
                                                 "---#-----"
                                                 "----A----"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 0] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 0]}} (get-in @atoms/game-map [3 3]))
        (should= {:type :land} (get-in @atoms/game-map [0 0]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step down-right towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "----A----"
                                                 "-----#---"
                                                 "---------"
                                                 "---------"
                                                 "--------#"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [8 8] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [8 8]}} (get-in @atoms/game-map [5 5]))
        (should= {:type :land} (get-in @atoms/game-map [8 8]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))

      (it "moves a unit one step down-left towards target at radius 4"
        (reset! atoms/game-map (build-test-map ["--------#"
                                                 "---------"
                                                 "---------"
                                                 "-----#---"
                                                 "----A----"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [0 8] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [0 8]}} (get-in @atoms/game-map [3 5]))
        (should= {:type :land} (get-in @atoms/game-map [0 8]))
        (should= 3 (count (filter (complement nil?) (flatten @atoms/game-map)))))
      )

    (context "multiple steps"
      (it "moves a unit two steps towards target over two calls"
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "----A##--"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "A" :mode :moving :target [4 6] :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        (game-loop/move-current-unit [4 4])
        ;; After first move, unit at [4 5], still moving
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :steps-remaining 0 :mode :moving :target [4 6]}} (get-in @atoms/game-map [4 5]))
        (should= {:type :land} (get-in @atoms/game-map [4 6]))
        ;; Give the unit another step and call again
        (swap! atoms/game-map assoc-in [4 5 :contents :steps-remaining] 1)
        (game-loop/move-current-unit [4 5])
        ;; After second move, unit at [4 6], awake
        (should= {:type :land} (get-in @atoms/game-map [4 4]))
        (should= {:type :land} (get-in @atoms/game-map [4 5]))
        (should= {:type :land :contents {:type :army :owner :player :hits 1 :mode :awake :steps-remaining 0}} (get-in @atoms/game-map [4 6])))
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
        (reset! atoms/game-map (build-test-map ["---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "----D~---"
                                                 "---------"
                                                 "---------"
                                                 "---------"
                                                 "---------"]))
        (set-test-unit atoms/game-map "D" :mode :moving :target [4 5] :hits 3 :steps-remaining 1)
        (reset! atoms/player-map (build-test-map ["---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"
                                                   "---------"]))
        ;; Destroyer moving to its target should wake normally
        (game-loop/move-current-unit [4 4])
        (let [destroyer (:contents (get-in @atoms/game-map [4 5]))]
          (should= :destroyer (:type destroyer))
          (should= :awake (:mode destroyer)))))

    (describe "explore movement helpers"
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
        ;; [3 2] is unexplored in player-map, so moves from [2 2] that are adjacent to unexplored
        (let [moves (explore/get-unexplored-explore-moves [2 2] atoms/game-map)]
          (should (some #{[3 2]} moves))))

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
    (reset! atoms/game-map (build-test-map ["#########"
                                             "#########"
                                             "#########"
                                             "#########"
                                             "#########"
                                             "#########"
                                             "#########"
                                             "#########"
                                             "#########"])))

  (it "adds army unit at empty cell"
    (add-unit-at [3 4] :army)
    (let [contents (get-in @atoms/game-map [3 4 :contents])]
      (should= :army (:type contents))
      (should= :player (:owner contents))
      (should= :awake (:mode contents))
      (should= (config/item-hits :army) (:hits contents))))

  (it "adds fighter with fuel"
    (add-unit-at [3 4] :fighter)
    (let [contents (get-in @atoms/game-map [3 4 :contents])]
      (should= :fighter (:type contents))
      (should= config/fighter-fuel (:fuel contents))))

  (it "does not add unit if cell has contents"
    (swap! atoms/game-map assoc-in [3 4 :contents] {:type :army :owner :computer})
    (add-unit-at [3 4] :carrier)
    (should= :army (get-in @atoms/game-map [3 4 :contents :type])))

  (it "adds computer-owned army when owner is :computer"
    (add-unit-at [3 4] :army :computer)
    (let [contents (get-in @atoms/game-map [3 4 :contents])]
      (should= :army (:type contents))
      (should= :computer (:owner contents))
      (should= :awake (:mode contents))))

  (it "adds computer-owned destroyer when owner is :computer"
    (add-unit-at [5 5] :destroyer :computer)
    (let [contents (get-in @atoms/game-map [5 5 :contents])]
      (should= :destroyer (:type contents))
      (should= :computer (:owner contents))))

  (it "defaults to player owner when not specified"
    (add-unit-at [2 2] :transport)
    (should= :player (get-in @atoms/game-map [2 2 :contents :owner]))))

(describe "wake-at"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#########"
                                             "#########"
                                             "#########"
                                             "#########"
                                             "#########"
                                             "#########"
                                             "#########"
                                             "#########"
                                             "#########"]))
    (reset! atoms/production {}))

  (it "wakes a sleeping unit"
    (swap! atoms/game-map assoc-in [3 4 :contents]
           {:type :army :owner :player :mode :sentry})
    (should (wake-at [3 4]))
    (should= :awake (get-in @atoms/game-map [3 4 :contents :mode])))

  (it "wakes unit in explore mode"
    (swap! atoms/game-map assoc-in [3 4 :contents]
           {:type :army :owner :player :mode :explore})
    (should (wake-at [3 4]))
    (should= :awake (get-in @atoms/game-map [3 4 :contents :mode])))

  (it "returns nil for already awake unit"
    (swap! atoms/game-map assoc-in [3 4 :contents]
           {:type :army :owner :player :mode :awake})
    (should-not (wake-at [3 4])))

  (it "returns nil for enemy unit"
    (swap! atoms/game-map assoc-in [3 4 :contents]
           {:type :army :owner :computer :mode :sentry})
    (should-not (wake-at [3 4])))

  (it "wakes player city and removes production"
    (swap! atoms/game-map assoc-in [3 4]
           {:type :city :city-status :player :sleeping-fighters 0 :awake-fighters 0})
    (reset! atoms/production {[3 4] {:item :army :remaining-rounds 5}})
    (should (wake-at [3 4]))
    (should-not (get @atoms/production [3 4])))

  (it "returns nil for empty cell"
    (should-not (wake-at [3 4])))

  (it "returns nil for enemy city"
    (swap! atoms/game-map assoc-in [3 4]
           {:type :city :city-status :computer})
    (should-not (wake-at [3 4]))))

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
