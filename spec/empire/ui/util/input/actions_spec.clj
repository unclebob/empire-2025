(ns empire.ui.util.input.actions-spec
  (:require [speclj.core :refer :all]
            [empire.ui.util.input.actions :as actions]
            [empire.ui.util.input.dispatch :as dispatch]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.player.orders :as orders]
            [empire.player.production :as production]
            [empire.save-load :as save-load]
            [empire.test-utils :refer [build-test-map get-test-city get-test-unit set-test-unit reset-all-atoms!]]))

(describe "set-city-lookaround"
  (around [it]
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["~O"
                                             "X#"]))
    (it))

  (it "sets marching orders to :lookaround on player city"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (orders/set-city-lookaround city-coords)
      (should= :lookaround (get-in @atoms/game-map (conj city-coords :marching-orders)))))

  (it "returns true when setting lookaround on player city"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (should (orders/set-city-lookaround city-coords))))

  (it "does not set marching orders on computer city"
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (orders/set-city-lookaround city-coords)
      (should-be-nil (get-in @atoms/game-map (conj city-coords :marching-orders)))))

  (it "returns nil when cell is not a player city"
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (should-be-nil (orders/set-city-lookaround city-coords))))

  (it "does not set marching orders on non-city cell"
    (orders/set-city-lookaround [0 0])
    (should-be-nil (get-in @atoms/game-map [0 0 :marching-orders])))

  (it "returns nil for non-city cell"
    (should-be-nil (orders/set-city-lookaround [0 0]))))

(describe "handle-key :space"
  (before (reset-all-atoms!))
  (it "sets reason to :skipping-this-round on the unit"
    (reset! atoms/game-map (build-test-map ["A"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/cells-needing-attention [unit-coords])
      (reset! atoms/player-items [unit-coords])
      (reset! atoms/waiting-for-input true)
      (actions/handle-key :space)
      (let [unit (:contents (get-in @atoms/game-map unit-coords))]
        (should= :skipping-this-round (:reason unit)))))

  (it "burns a full round of fuel for fighters when skipping"
    (let [initial-fuel 20
          fighter-speed (config/unit-speed :fighter)]
      (reset! atoms/game-map (build-test-map ["F"]))
      (set-test-unit atoms/game-map "F" :mode :awake :fuel initial-fuel)
      (let [unit-coords (:pos (get-test-unit atoms/game-map "F"))]
        (reset! atoms/cells-needing-attention [unit-coords])
        (reset! atoms/player-items [unit-coords])
        (reset! atoms/waiting-for-input true)
        (actions/handle-key :space)
        (let [unit (:contents (get-in @atoms/game-map unit-coords))]
          (should= (- initial-fuel fighter-speed) (:fuel unit))))))

  (it "fighter crashes when skipping with insufficient fuel"
    (let [_fighter-speed (config/unit-speed :fighter)]
      (reset! atoms/game-map (build-test-map ["F"]))
      (set-test-unit atoms/game-map "F" :mode :awake :fuel 3 :hits 1)
      (let [unit-coords (:pos (get-test-unit atoms/game-map "F"))]
        (reset! atoms/cells-needing-attention [unit-coords])
        (reset! atoms/player-items [unit-coords])
        (reset! atoms/waiting-for-input true)
        (actions/handle-key :space)
        (let [unit (:contents (get-in @atoms/game-map unit-coords))]
          (should= 0 (:hits unit))))))

  (it "includes fuel in reason when fighter skips"
    (reset! atoms/game-map (build-test-map ["F"]))
    (set-test-unit atoms/game-map "F" :mode :awake :fuel 20)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/cells-needing-attention [unit-coords])
      (reset! atoms/player-items [unit-coords])
      (reset! atoms/waiting-for-input true)
      (actions/handle-key :space)
      (let [unit (:contents (get-in @atoms/game-map unit-coords))]
        (should-contain "12" (:reason unit))))))

(describe "handle-key :space on city needing attention"
  (before (reset-all-atoms!))

  (it "clears attention when space is pressed on city without production"
    (reset! atoms/game-map (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/waiting-for-input true)
      (actions/handle-key :space)
      (should= [] @atoms/cells-needing-attention)
      (should= false @atoms/waiting-for-input)))

  (it "does not add production when space is pressed"
    (reset! atoms/game-map (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/waiting-for-input true)
      (actions/handle-key :space)
      (should-be-nil (get @atoms/production city-coords))))

  (it "removes city from player-items when space is pressed"
    (reset! atoms/game-map (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/player-items [city-coords])
      (reset! atoms/waiting-for-input true)
      (actions/handle-key :space)
      (should= [] @atoms/player-items)))

  (it "does nothing for computer cities"
    (reset! atoms/game-map (build-test-map ["X"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/waiting-for-input true)
      (actions/handle-key :space)
      ;; Should still be waiting - nothing happened
      (should= true @atoms/waiting-for-input))))

(describe "handle-key :l on army aboard transport"
  (before (reset-all-atoms!))

  (it "does not add disembarked army to player-items (no double move)"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 1 :awake-armies 1)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(first transport-coords) (inc (second transport-coords))]]
      (reset! atoms/cells-needing-attention [transport-coords])
      (reset! atoms/player-items [transport-coords])
      (reset! atoms/waiting-for-input true)
      (actions/handle-key :l)
      ;; Army should be on land
      (should= :army (:type (:contents (get-in @atoms/game-map land-coords))))
      ;; Army should NOT be in player-items (would cause double move)
      (should-not (some #{land-coords} @atoms/player-items))
      ;; Transport remains in player-items for normal processing
      (should (some #{transport-coords} @atoms/player-items))))

  (it "keeps transport in player-items when more awake armies remain"
    (reset! atoms/game-map (build-test-map ["---------"
                                             "---------"
                                             "---------"
                                             "---------"
                                             "----T----"
                                             "----#----"
                                             "---------"
                                             "---------"
                                             "---------"]))
    (set-test-unit atoms/game-map "T" :mode :sentry :hits 1 :army-count 3 :awake-armies 3)
    (let [transport-coords (:pos (get-test-unit atoms/game-map "T"))
          land-coords [(first transport-coords) (inc (second transport-coords))]]
      (reset! atoms/cells-needing-attention [transport-coords])
      (reset! atoms/player-items [transport-coords])
      (reset! atoms/waiting-for-input true)
      (actions/handle-key :l)
      ;; Transport should still be in player-items so remaining armies get attention
      (should (some #{transport-coords} @atoms/player-items))
      ;; Disembarked army should NOT be in player-items (no double move)
      (should-not (some #{land-coords} @atoms/player-items))
      ;; Transport should still have 2 awake armies
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= 2 (:awake-armies transport))))))

(describe "undamaged ship entering friendly city"
  (before (reset-all-atoms!))

  (it "rejects command and shows error message"
    (reset! atoms/game-map (build-test-map ["~D~"
                                             "~O~"
                                             "~~~"]))
    (set-test-unit atoms/game-map "D" :mode :awake :hits 3)  ; full health destroyer (max 3)
    (let [ship-coords (:pos (get-test-unit atoms/game-map "D"))]
      (reset! atoms/cells-needing-attention [ship-coords])
      (reset! atoms/player-items [ship-coords])
      (reset! atoms/waiting-for-input true)
      (reset! atoms/error-message "")
      ;; Press 'x' to move down toward city
      (actions/handle-key :x)
      ;; Should show error message
      (should= "Ship not damaged, entry denied." @atoms/error-message)
      ;; Ship should still be awake (command rejected, not processed)
      (let [ship (:contents (get-in @atoms/game-map ship-coords))]
        (should= :awake (:mode ship)))
      ;; Should still be waiting for input
      (should= true @atoms/waiting-for-input)))

  (it "allows damaged ship to set movement toward city"
    (reset! atoms/game-map (build-test-map ["~D~"
                                             "~O~"
                                             "~~~"]))
    (set-test-unit atoms/game-map "D" :mode :awake :hits 2)  ; damaged destroyer
    (let [ship-coords (:pos (get-test-unit atoms/game-map "D"))
          city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/cells-needing-attention [ship-coords])
      (reset! atoms/player-items [ship-coords])
      (reset! atoms/waiting-for-input true)
      (reset! atoms/error-message "")
      ;; Press 'x' to move down toward city
      (actions/handle-key :x)
      ;; Should NOT show error message
      (should= "" @atoms/error-message)
      ;; Ship should be in moving mode with city as target
      (let [ship (:contents (get-in @atoms/game-map ship-coords))]
        (should= :moving (:mode ship))
        (should= city-coords (:target ship))))))

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
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [4 0] (#'actions/calculate-extended-target [0 0] [1 0])))

  (it "calculates target at map edge going south"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [0 4] (#'actions/calculate-extended-target [0 0] [0 1])))

  (it "calculates target at map edge going southeast"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [4 4] (#'actions/calculate-extended-target [0 0] [1 1])))

  (it "calculates target at map edge going west"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [0 2] (#'actions/calculate-extended-target [4 2] [-1 0])))

  (it "calculates target at map edge going north"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [2 0] (#'actions/calculate-extended-target [2 4] [0 -1])))

  (it "returns starting position when already at edge"
    (reset! atoms/game-map (build-test-map ["#####"
                                             "#####"
                                             "#####"
                                             "#####"
                                             "#####"]))
    (should= [0 0] (#'actions/calculate-extended-target [0 0] [-1 0])))

  (it "works with non-square maps"
    (reset! atoms/game-map (build-test-map ["###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"
                                             "###"]))
    (should= [2 1] (#'actions/calculate-extended-target [0 1] [1 0]))))

(describe "handle-key :u (unload)"
  (before (reset-all-atoms!))

  (it "wakes armies on transport"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~T~"
                                             "~~~"]))
    (set-test-unit atoms/game-map "T" :mode :awake :hits 1 :army-count 2 :awake-armies 0)
    (let [coords (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/cells-needing-attention [coords])
      (reset! atoms/player-items [coords])
      (reset! atoms/waiting-for-input true)
      (actions/handle-key :u)
      (let [transport (:contents (get-in @atoms/game-map coords))]
        (should= 2 (:awake-armies transport)))))

  (it "wakes fighters on carrier"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~C~"
                                             "~~~"]))
    (set-test-unit atoms/game-map "C" :mode :awake :hits 1 :fighter-count 2 :awake-fighters 0)
    (let [coords (:pos (get-test-unit atoms/game-map "C"))]
      (reset! atoms/cells-needing-attention [coords])
      (reset! atoms/player-items [coords])
      (reset! atoms/waiting-for-input true)
      (actions/handle-key :u)
      (let [carrier (:contents (get-in @atoms/game-map coords))]
        (should= 2 (:awake-fighters carrier)))))

  (it "returns nil when unit is not a container"
    (reset! atoms/game-map (build-test-map ["~D~"]))
    (set-test-unit atoms/game-map "D" :mode :awake :hits 3)
    (let [coords (:pos (get-test-unit atoms/game-map "D"))]
      (reset! atoms/cells-needing-attention [coords])
      (reset! atoms/player-items [coords])
      (reset! atoms/waiting-for-input true)
      (should-be-nil (actions/handle-key :u)))))

(describe "handle-key production on city"
  (before (reset-all-atoms!))

  (it "sets army production on player city"
    (reset! atoms/game-map (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/player-items [city-coords])
      (reset! atoms/waiting-for-input true)
      (actions/handle-key :a)
      (should= :army (:item (get @atoms/production city-coords)))))

  (it "rejects naval production on non-coastal city"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#O#"
                                             "###"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/player-items [city-coords])
      (reset! atoms/waiting-for-input true)
      (reset! atoms/error-message "")
      (actions/handle-key :d)
      ;; Should show error message about coastal city
      (should-contain "coastal" @atoms/error-message)
      ;; Should NOT set production
      (should-be-nil (get @atoms/production city-coords))))

  (it "allows naval production on coastal city"
    (reset! atoms/game-map (build-test-map ["~O#"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/player-items [city-coords])
      (reset! atoms/waiting-for-input true)
      (actions/handle-key :d)
      (should= :destroyer (:item (get @atoms/production city-coords)))))

  (it "clears production with :x key"
    (reset! atoms/game-map (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      ;; Set initial production
      (production/set-city-production city-coords :army)
      (should= :army (:item (get @atoms/production city-coords)))
      ;; Now press :x to clear
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/player-items [city-coords])
      (reset! atoms/waiting-for-input true)
      (actions/handle-key :x)
      (should= :none (get @atoms/production city-coords)))))

(describe "handle-key :l (look-around)"
  (before (reset-all-atoms!))

  (it "sets army to explore mode"
    (reset! atoms/game-map (build-test-map ["###"
                                             "#A#"
                                             "###"]))
    (set-test-unit atoms/game-map "A" :mode :awake)
    (let [coords (:pos (get-test-unit atoms/game-map "A"))]
      (reset! atoms/cells-needing-attention [coords])
      (reset! atoms/player-items [coords])
      (reset! atoms/waiting-for-input true)
      (actions/handle-key :l)
      (let [unit (:contents (get-in @atoms/game-map coords))]
        (should= :explore (:mode unit)))))

  (it "sets transport to coastline-follow mode when near coast"
    (reset! atoms/game-map (build-test-map ["~#~"
                                             "~T~"
                                             "~~~"]))
    (set-test-unit atoms/game-map "T" :mode :awake :hits 1 :army-count 0)
    (let [coords (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/cells-needing-attention [coords])
      (reset! atoms/player-items [coords])
      (reset! atoms/waiting-for-input true)
      (actions/handle-key :l)
      (let [unit (:contents (get-in @atoms/game-map coords))]
        (should= :coastline-follow (:mode unit)))))

  (it "shows rejection reason for transport not near coast"
    (reset! atoms/game-map (build-test-map ["~~~~~"
                                             "~~~~~"
                                             "~~T~~"
                                             "~~~~~"
                                             "~~~~~"]))
    (set-test-unit atoms/game-map "T" :mode :awake :hits 1 :army-count 0)
    (let [coords (:pos (get-test-unit atoms/game-map "T"))]
      (reset! atoms/cells-needing-attention [coords])
      (reset! atoms/player-items [coords])
      (reset! atoms/waiting-for-input true)
      (reset! atoms/attention-message "")
      (actions/handle-key :l)
      (should-contain "coast" @atoms/attention-message))))

(describe "execute-unit-movement: airport-fighter launch"
  (before (reset-all-atoms!))

  (it "launches fighter from airport"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~O~"
                                             "~~~"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      ;; Set up airport with awake fighter
      (swap! atoms/game-map update-in city-coords
             merge {:fighter-count 1 :awake-fighters 1})
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/player-items [city-coords])
      (reset! atoms/waiting-for-input true)
      ;; Press 'q' to move up-left
      (actions/handle-key :q)
      ;; Fighter should have been launched - city should have 0 fighters now
      (let [city (get-in @atoms/game-map city-coords)]
        (should= 0 (:fighter-count city))))))

(describe "execute-unit-movement: carrier-fighter launch"
  (before (reset-all-atoms!))

  (it "launches fighter from carrier"
    (reset! atoms/game-map (build-test-map ["~~~"
                                             "~C~"
                                             "~~~"]))
    (set-test-unit atoms/game-map "C" :mode :sentry :hits 1 :fighter-count 1 :awake-fighters 1)
    (let [coords (:pos (get-test-unit atoms/game-map "C"))]
      (reset! atoms/cells-needing-attention [coords])
      (reset! atoms/player-items [coords])
      (reset! atoms/waiting-for-input true)
      ;; Press 'q' to move up-left - should launch fighter
      (actions/handle-key :q)
      (let [carrier (:contents (get-in @atoms/game-map coords))]
        (should= 0 (:fighter-count carrier))))))

(describe "handle-standard-unit-movement: fighter overfly hostile city"
  (before (reset-all-atoms!))

  (it "fighter gets shot down when flying over hostile city"
    (reset! atoms/game-map (build-test-map ["~F~"
                                             "~X~"
                                             "~~~"]))
    (set-test-unit atoms/game-map "F" :mode :awake :fuel 20 :hits 1)
    (let [fighter-coords (:pos (get-test-unit atoms/game-map "F"))
          city-coords (:pos (get-test-city atoms/game-map "X"))]
      (reset! atoms/cells-needing-attention [fighter-coords])
      (reset! atoms/player-items [fighter-coords])
      (reset! atoms/waiting-for-input true)
      (reset! atoms/error-message "")
      ;; Press 'x' to move down toward hostile city
      (actions/handle-key :x)
      ;; Fighter should be shot down (hits = 0) at city
      (let [city-cell (get-in @atoms/game-map city-coords)
            fighter (:contents city-cell)]
        (should= 0 (:hits fighter))))))
