(ns empire.ui.util.input.actions-movement-spec
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.ui.util.input.actions :as actions]
            [empire.ui.util.input.actions.movement :as actions-movement]
            [empire.ui.util.input.dispatch :as dispatch]
            [empire.config.core :as config]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.services.combat :as combat]
            [empire.player.orders :as orders]
            [empire.player.production :as production]
            [empire.test.utils :refer [build-test-map get-test-city get-test-unit set-test-unit reset-all-atoms!
                                       set-test-world! update-test-world!]]))
(describe "handle-key :l on army aboard transport"
  (before (reset-all-atoms!))

  (it "does not add disembarked army to player-items (no double move)"
    (set-test-world! (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 1 :awake-armies 1)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          land-coords [(first transport-coords) (inc (second transport-coords))]]
      (test-utils/set-test-state! :cells-needing-attention [transport-coords])
      (test-utils/set-test-state! :player-items [transport-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (actions/handle-key :l)
      ;; Army should be on land
      (should= :army (:type (:contents (get-in (test-utils/read-test-state :game-map) land-coords))))
      ;; Army should NOT be in player-items (would cause double move)
      (should-not (some #{land-coords} (test-utils/read-test-state :player-items)))
      ;; Transport remains in player-items for normal processing
      (should (some #{transport-coords} (test-utils/read-test-state :player-items)))))

  (it "keeps transport in player-items when more awake armies remain"
    (set-test-world! (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :sentry :hits 1 :army-count 3 :awake-armies 3)
    (let [transport-coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))
          land-coords [(first transport-coords) (inc (second transport-coords))]]
      (test-utils/set-test-state! :cells-needing-attention [transport-coords])
      (test-utils/set-test-state! :player-items [transport-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (actions/handle-key :l)
      ;; Transport should still be in player-items so remaining armies get attention
      (should (some #{transport-coords} (test-utils/read-test-state :player-items)))
      ;; Disembarked army should NOT be in player-items (no double move)
      (should-not (some #{land-coords} (test-utils/read-test-state :player-items)))
      ;; Transport should still have 2 awake armies
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) transport-coords))]
        (should= 2 (:awake-armies transport))))))

(describe "undamaged ship entering friendly city"
  (before (reset-all-atoms!))

  (it "rejects command and shows error message"
    (set-test-world! (build-test-map ["~D~"
                                             "~O~"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "D" :mode :awake :hits 3)  ; full health destroyer (max 3)
    (let [ship-coords (:pos (get-test-unit (test-utils/game-map-atom) "D"))]
      (test-utils/set-test-state! :cells-needing-attention [ship-coords])
      (test-utils/set-test-state! :player-items [ship-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (test-utils/set-test-state! :warning-message "")
      ;; Press 'x' to move down toward city
      (actions/handle-key :x)
      ;; Should show error message
      (should= "Ship not damaged, entry denied." (test-utils/read-test-state :warning-message))
      ;; Ship should still be awake (command rejected, not processed)
      (let [ship (:contents (get-in (test-utils/read-test-state :game-map) ship-coords))]
        (should= :awake (:mode ship)))
      ;; Should still be waiting for input
      (should= true (test-utils/read-test-state :waiting-for-input))))

  (it "allows damaged ship to set movement toward city"
    (set-test-world! (build-test-map ["~D~"
                                             "~O~"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "D" :mode :awake :hits 2)  ; damaged destroyer
    (let [ship-coords (:pos (get-test-unit (test-utils/game-map-atom) "D"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (test-utils/set-test-state! :cells-needing-attention [ship-coords])
      (test-utils/set-test-state! :player-items [ship-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (test-utils/set-test-state! :warning-message "")
      ;; Press 'x' to move down toward city
      (actions/handle-key :x)
      ;; Should NOT show error message
      (should= "" (test-utils/read-test-state :warning-message))
      ;; Ship should be in moving mode with city as target
      (let [ship (:contents (get-in (test-utils/read-test-state :game-map) ship-coords))]
        (should= :moving (:mode ship))
        (should= city-coords (:target ship))))))

(describe "coastal army attacks ship"
  (before (reset-all-atoms!))

  (it "uses coastal army attack when moving into an adjacent hostile ship"
    (set-test-world! (build-test-map ["Ad"]))
    (set-test-unit (test-utils/game-map-atom) "A" :mode :awake)
    (set-test-unit (test-utils/game-map-atom) "d" :owner :computer :hits 3)
    (let [army-coords (:pos (get-test-unit (test-utils/game-map-atom) "A"))]
      (test-utils/set-test-state! :cells-needing-attention [army-coords])
      (test-utils/set-test-state! :player-items [army-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (let [attack-called (atom false)]
        (with-redefs [combat/attempt-coastal-army-attack (fn [_ _ _] (reset! attack-called true) {})
                      combat/apply-combat-result! (fn [_] nil)]
          (actions/handle-key :d)
          (should @attack-called)
          (should= false (test-utils/read-test-state :waiting-for-input)))))))

(describe "army-aboard-action"
  (it "disembarks to empty land when not extended"
    (should= :disembark
             (actions/army-aboard-action false {:type :land} false)))

  (it "disembarks with target to empty land when extended"
    (should= :disembark-with-target
             (actions/army-aboard-action true {:type :land} false)))

  (it "conquers hostile city when not extended"
    (should= :conquest
             (actions/army-aboard-action false {:type :city :city-status :computer} true)))

  (it "ignores hostile city when extended"
    (should= :ignore
             (actions/army-aboard-action true {:type :city :city-status :computer} true)))

  (it "ignores occupied land"
    (should= :ignore
             (actions/army-aboard-action false {:type :land :contents {:type :army}} false)))

  (it "ignores sea target"
    (should= :ignore
             (actions/army-aboard-action false {:type :sea} false)))

  (it "prefers land disembark over hostile city"
    (should= :disembark
             (actions/army-aboard-action false {:type :land} true))))

(describe "calculate-extended-target"
  (before (reset-all-atoms!))
  (it "calculates target at map edge going east"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [4 0] (#'actions-movement/calculate-extended-target [0 0] [1 0])))

  (it "calculates target at map edge going south"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [0 4] (#'actions-movement/calculate-extended-target [0 0] [0 1])))

  (it "calculates target at map edge going southeast"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [4 4] (#'actions-movement/calculate-extended-target [0 0] [1 1])))

  (it "calculates target at map edge going west"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [0 2] (#'actions-movement/calculate-extended-target [4 2] [-1 0])))

  (it "calculates target at map edge going north"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [2 0] (#'actions-movement/calculate-extended-target [2 4] [0 -1])))

  (it "returns starting position when already at edge"
    (set-test-world! (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [0 0] (#'actions-movement/calculate-extended-target [0 0] [-1 0])))

  (it "works with non-square maps"
    (set-test-world! (build-test-map ["###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"]))
    (should= [2 1] (#'actions-movement/calculate-extended-target [0 1] [1 0]))))

(describe "handle-key :u (unload)"
  (before (reset-all-atoms!))

  (it "wakes armies on transport"
    (set-test-world! (build-test-map ["~~~"
                                             "~T~"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "T" :mode :awake :hits 1 :army-count 2 :awake-armies 0)
    (let [coords (:pos (get-test-unit (test-utils/game-map-atom) "T"))]
      (test-utils/set-test-state! :cells-needing-attention [coords])
      (test-utils/set-test-state! :player-items [coords])
      (orders/wake-at coords)
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) coords))]
        (should= 2 (:awake-armies transport)))))

  (it "wakes fighters on carrier"
    (set-test-world! (build-test-map ["~~~"
                                             "~C~"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "C" :mode :awake :hits 1 :fighter-count 2 :awake-fighters 0)
    (let [coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))]
      (test-utils/set-test-state! :cells-needing-attention [coords])
      (test-utils/set-test-state! :player-items [coords])
      (orders/wake-at coords)
      (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) coords))]
        (should= 2 (:awake-fighters carrier)))))

  (it "returns nil when unit is not a container"
    (set-test-world! (build-test-map ["~D~"]))
    (set-test-unit (test-utils/game-map-atom) "D" :mode :awake :hits 3)
    (let [coords (:pos (get-test-unit (test-utils/game-map-atom) "D"))]
      (test-utils/set-test-state! :cells-needing-attention [coords])
      (test-utils/set-test-state! :player-items [coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (should-be-nil (actions/handle-key :u)))))

(describe "execute-unit-movement: airport-fighter launch"
  (before (reset-all-atoms!))

  (it "launches fighter from airport"
    (set-test-world! (build-test-map ["~~~"
                                             "~O~"
                                             "~~~"]))
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      ;; Set up airport with awake fighter
      (update-test-world! update-in city-coords
             merge {:fighter-count 1 :awake-fighters 1})
      (test-utils/set-test-state! :production {city-coords {:item :fighter :remaining-rounds 10}})
      (test-utils/set-test-state! :cells-needing-attention [city-coords])
      (test-utils/set-test-state! :player-items [city-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      ;; Press 'q' to move up-left
      (actions/handle-key :q)
      ;; Fighter should have been launched - city should have 0 fighters now
      (let [city (get-in (test-utils/read-test-state :game-map) city-coords)]
        (should= 0 (:fighter-count city))))))

(describe "execute-unit-movement: carrier-fighter launch"
  (before (reset-all-atoms!))

  (it "launches fighter from carrier"
    (set-test-world! (build-test-map ["~~~"
                                             "~C~"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "C" :mode :sentry :hits 1 :fighter-count 1 :awake-fighters 1)
    (let [coords (:pos (get-test-unit (test-utils/game-map-atom) "C"))]
      (test-utils/set-test-state! :cells-needing-attention [coords])
      (test-utils/set-test-state! :player-items [coords])
      (test-utils/set-test-state! :waiting-for-input true)
      ;; Press 'q' to move up-left - should launch fighter
      (actions/handle-key :q)
      (let [carrier (:contents (get-in (test-utils/read-test-state :game-map) coords))]
        (should= 0 (:fighter-count carrier))))))

(describe "handle-standard-unit-movement: fighter overfly hostile city"
  (before (reset-all-atoms!))

  (it "fighter gets shot down when flying over hostile city"
    (set-test-world! (build-test-map ["~F~"
                                             "~X~"
                                             "~~~"]))
    (set-test-unit (test-utils/game-map-atom) "F" :mode :awake :fuel 20 :hits 1)
    (let [fighter-coords (:pos (get-test-unit (test-utils/game-map-atom) "F"))
          city-coords (:pos (get-test-city (test-utils/game-map-atom) "X"))]
      (test-utils/set-test-state! :cells-needing-attention [fighter-coords])
      (test-utils/set-test-state! :player-items [fighter-coords])
      (test-utils/set-test-state! :waiting-for-input true)
      (test-utils/set-test-state! :warning-message "")
      ;; Press 'x' to move down toward hostile city
      (actions/handle-key :x)
      ;; Fighter should be shot down (hits = 0) at city
      (let [city-cell (get-in (test-utils/read-test-state :game-map) city-coords)
            fighter (:contents city-cell)]
        (should= 0 (:hits fighter))))))
