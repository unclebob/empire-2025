(ns empire.ui.input-spec
  (:require [speclj.core :refer :all]
            [clojure.string :as string]
            [empire.ui.input :as input]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.player.orders :as orders]
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
      (input/handle-key :space)
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
        (input/handle-key :space)
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
        (input/handle-key :space)
        (let [unit (:contents (get-in @atoms/game-map unit-coords))]
          (should= 0 (:hits unit))))))

  (it "includes fuel in reason when fighter skips"
    (reset! atoms/game-map (build-test-map ["F"]))
    (set-test-unit atoms/game-map "F" :mode :awake :fuel 20)
    (let [unit-coords (:pos (get-test-unit atoms/game-map "F"))]
      (reset! atoms/cells-needing-attention [unit-coords])
      (reset! atoms/player-items [unit-coords])
      (reset! atoms/waiting-for-input true)
      (input/handle-key :space)
      (let [unit (:contents (get-in @atoms/game-map unit-coords))]
        (should-contain "12" (:reason unit))))))

(describe "handle-key :space on city needing attention"
  (before (reset-all-atoms!))

  (it "clears attention when space is pressed on city without production"
    (reset! atoms/game-map (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/waiting-for-input true)
      (input/handle-key :space)
      (should= [] @atoms/cells-needing-attention)
      (should= false @atoms/waiting-for-input)))

  (it "does not add production when space is pressed"
    (reset! atoms/game-map (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/waiting-for-input true)
      (input/handle-key :space)
      (should-be-nil (get @atoms/production city-coords))))

  (it "removes city from player-items when space is pressed"
    (reset! atoms/game-map (build-test-map ["O"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/player-items [city-coords])
      (reset! atoms/waiting-for-input true)
      (input/handle-key :space)
      (should= [] @atoms/player-items)))

  (it "does nothing for computer cities"
    (reset! atoms/game-map (build-test-map ["X"]))
    (let [city-coords (:pos (get-test-city atoms/game-map "X"))]
      (reset! atoms/cells-needing-attention [city-coords])
      (reset! atoms/waiting-for-input true)
      (input/handle-key :space)
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
      (input/handle-key :l)
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
      (input/handle-key :l)
      ;; Transport should still be in player-items so remaining armies get attention
      (should (some #{transport-coords} @atoms/player-items))
      ;; Disembarked army should NOT be in player-items (no double move)
      (should-not (some #{land-coords} @atoms/player-items))
      ;; Transport should still have 2 awake armies
      (let [transport (:contents (get-in @atoms/game-map transport-coords))]
        (should= 2 (:awake-armies transport))))))

(describe "key-down :P"
  (before
    (reset-all-atoms!)
    (reset! atoms/paused false)
    (reset! atoms/pause-requested false)
    (reset! atoms/backtick-pressed false))

  (it "toggles pause when P is pressed"
    (input/dispatch-key :P nil)
    (should @atoms/pause-requested)))

(describe "key-down :space when paused"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O"]))  ;; Player city so not game over
    (reset! atoms/player-map (build-test-map ["#"]))
    (reset! atoms/computer-map (build-test-map ["#"]))
    (reset! atoms/paused true)
    (reset! atoms/pause-requested false)
    (reset! atoms/backtick-pressed false)
    (reset! atoms/player-items [])
    (reset! atoms/computer-items [])
    (reset! atoms/round-number 5))

  (it "starts new round when both item lists are empty"
    (input/dispatch-key :space nil)
    (should= 6 @atoms/round-number))

  (it "sets pause-requested to pause after round"
    (input/dispatch-key :space nil)
    (should= true @atoms/pause-requested))

  (it "unpauses to allow game loop to process"
    (input/dispatch-key :space nil)
    (should= false @atoms/paused)))

(describe "save/load key handling"
  (around [it]
    (reset-all-atoms!)
    (it))

  (it "! key calls save-game! and shows confirmation"
    (let [saved (atom false)]
      (with-redefs [save-load/save-game! (fn [] (reset! saved true) "test-file.edn")]
        (input/dispatch-key (keyword "!") nil)
        (should @saved)
        (should (string/includes? @atoms/turn-message "test-file.edn")))))

  (it "^ key opens load menu"
    (input/dispatch-key (keyword "^") nil)
    (should= true @atoms/load-menu-open)))

(describe "Escape key with load menu"
  (around [it]
    (reset-all-atoms!)
    (it))

  (it "closes load menu when open"
    (reset! atoms/load-menu-open true)
    (reset! atoms/load-menu-files ["file.edn"])
    (input/dispatch-key :escape nil)
    (should= false @atoms/load-menu-open)
    (should= [] @atoms/load-menu-files))

  (it "does nothing when load menu is closed"
    (input/dispatch-key :escape nil)
    (should= false @atoms/load-menu-open)))

(describe "key blocking while load menu open"
  (around [it]
    (reset-all-atoms!)
    (it))

  (it "ignores non-escape keys when menu is open"
    (reset! atoms/load-menu-open true)
    (reset! atoms/pause-requested false)
    (input/dispatch-key :P nil)
    (should= false @atoms/pause-requested))

  (it "processes normal keys when menu is closed"
    (reset! atoms/load-menu-open false)
    (reset! atoms/paused false)
    (input/dispatch-key :P nil)
    (should= true @atoms/pause-requested)))

(describe "load menu click handling"
  (around [it]
    (reset-all-atoms!)
    (it))

  (it "loads selected file when clicking on menu item"
    (let [loaded (atom nil)]
      (reset! atoms/load-menu-open true)
      (reset! atoms/load-menu-files ["file1.edn" "file2.edn"])
      (reset! atoms/load-menu-hovered 1)
      (with-redefs [save-load/load-game! (fn [f] (reset! loaded f))]
        (input/handle-load-menu-click)
        (should= "file2.edn" @loaded))))

  (it "does nothing when no file is hovered"
    (let [loaded (atom nil)]
      (reset! atoms/load-menu-open true)
      (reset! atoms/load-menu-files ["file1.edn"])
      (reset! atoms/load-menu-hovered nil)
      (with-redefs [save-load/load-game! (fn [f] (reset! loaded f))]
        (input/handle-load-menu-click)
        (should-be-nil @loaded)))))

(describe "undamaged ship entering friendly city"
  (before (reset-all-atoms!))

  (it "rejects command and shows error message"
    (reset! atoms/game-map (build-test-map ["~D~"
                                             "~O~"
                                             "~~~"]))
    (set-test-unit atoms/game-map "D" :mode :awake :hits 3)  ; full health destroyer (max 3)
    (let [ship-coords (:pos (get-test-unit atoms/game-map "D"))
          city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/cells-needing-attention [ship-coords])
      (reset! atoms/player-items [ship-coords])
      (reset! atoms/waiting-for-input true)
      (reset! atoms/error-message "")
      ;; Press 'x' to move down toward city
      (input/handle-key :x)
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
      (input/handle-key :x)
      ;; Should NOT show error message
      (should= "" @atoms/error-message)
      ;; Ship should be in moving mode with city as target
      (let [ship (:contents (get-in @atoms/game-map ship-coords))]
        (should= :moving (:mode ship))
        (should= city-coords (:target ship))))))

(describe "dispatch-key backtick mode"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["#~O"
                                             "~~~"
                                             "~~~"]))
    (reset! atoms/backtick-pressed true))

  (it "places all player unit types"
    (doseq [[k unit-type coords] [[:A :army [0 0]]
                                   [:F :fighter [0 0]]
                                   [:Z :satellite [0 0]]
                                   [:T :transport [0 1]]
                                   [:P :patrol-boat [0 1]]
                                   [:D :destroyer [0 1]]
                                   [:S :submarine [0 1]]
                                   [:C :carrier [0 1]]
                                   [:B :battleship [0 1]]]]
      (reset! atoms/backtick-pressed true)
      (input/dispatch-key k coords)
      (should= unit-type (:type (:contents (get-in @atoms/game-map coords))))
      (should= :player (:owner (:contents (get-in @atoms/game-map coords))))
      (swap! atoms/game-map assoc-in (conj coords :contents) nil)))

  (it "places all computer unit types"
    (doseq [[k unit-type coords] [[:a :army [0 0]]
                                   [:f :fighter [0 0]]
                                   [:z :satellite [0 0]]
                                   [:t :transport [0 1]]
                                   [:p :patrol-boat [0 1]]
                                   [:d :destroyer [0 1]]
                                   [:s :submarine [0 1]]
                                   [:c :carrier [0 1]]
                                   [:b :battleship [0 1]]]]
      (reset! atoms/backtick-pressed true)
      (input/dispatch-key k coords)
      (should= unit-type (:type (:contents (get-in @atoms/game-map coords))))
      (should= :computer (:owner (:contents (get-in @atoms/game-map coords))))
      (swap! atoms/game-map assoc-in (conj coords :contents) nil)))

  (it "claims city for player with :o"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (input/dispatch-key :o city-coords)
      (should= :player (get-in @atoms/game-map (conj city-coords :city-status)))))

  (it "clears backtick-pressed"
    (input/dispatch-key :A [0 0])
    (should= false @atoms/backtick-pressed))

  (it "does nothing with nil cell-coords"
    (input/dispatch-key :A nil)
    (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))

  (it "clears backtick-pressed even with nil cell-coords"
    (input/dispatch-key :A nil)
    (should= false @atoms/backtick-pressed)))

(describe "dispatch-key normal mode mouse commands"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O~~"
                                             "~~~"
                                             "~~~"])))

  (it ". key sets destination"
    (input/dispatch-key (keyword ".") [1 2])
    (should= [1 2] @atoms/destination))

  (it ". key does nothing with nil coords"
    (input/dispatch-key (keyword ".") nil)
    (should-be-nil @atoms/destination))

  (it ":m key sets marching orders on player city"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/destination [2 2])
      (input/dispatch-key :m city-coords)
      (should= [2 2] (get-in @atoms/game-map (conj city-coords :marching-orders)))))

  (it ":m key does nothing with nil coords"
    (reset! atoms/destination [2 2])
    (input/dispatch-key :m nil)
    (should= [2 2] @atoms/destination))

  (it ":f key sets flight path on player city"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/destination [2 2])
      (input/dispatch-key :f city-coords)
      (should= [2 2] (get-in @atoms/game-map (conj city-coords :flight-path)))))

  (it ":f key does nothing with nil coords"
    (reset! atoms/destination [2 2])
    (input/dispatch-key :f nil)
    (should= [2 2] @atoms/destination))

  (it ":f key does nothing without destination"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (input/dispatch-key :f city-coords)
      (should-be-nil (get-in @atoms/game-map (conj city-coords :flight-path)))))

  (it ":u key wakes player city"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords :army)
      (input/dispatch-key :u city-coords)
      (should-be-nil (get @atoms/production city-coords))))

  (it ":u key does nothing with nil coords"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords :army)
      (input/dispatch-key :u nil)
      (should= :army (get @atoms/production city-coords))))

  (it ":l key sets lookaround on player city"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (input/dispatch-key :l city-coords)
      (should= :lookaround (get-in @atoms/game-map (conj city-coords :marching-orders)))))

  (it ":l key does nothing with nil coords"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (input/dispatch-key :l nil)
      (should-be-nil (get-in @atoms/game-map (conj city-coords :marching-orders)))))

  (it "direction key sets city marching orders to edge"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (input/dispatch-key :d city-coords)
      (should= [2 0] (get-in @atoms/game-map (conj city-coords :marching-orders)))))

  (it "direction key does nothing with nil coords"
    (input/dispatch-key :d nil)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (should-be-nil (get-in @atoms/game-map (conj city-coords :marching-orders)))))

  (it "backtick key sets backtick-pressed"
    (input/dispatch-key (keyword "`") nil)
    (should= true @atoms/backtick-pressed))

  (it "+ key cycles map display"
    (let [before @atoms/map-to-display]
      (input/dispatch-key :+ nil)
      (should-not= before @atoms/map-to-display)))

  (it "* key sets waypoint at coords"
    (let [called (atom nil)]
      (with-redefs [orders/set-waypoint-at (fn [c] (reset! called c) true)]
        (input/dispatch-key (keyword "*") [1 1])
        (should= [1 1] @called))))

  (it "* key does nothing with nil coords"
    (let [called (atom false)]
      (with-redefs [orders/set-waypoint-at (fn [_] (reset! called true))]
        (input/dispatch-key (keyword "*") nil)
        (should= false @called))))

  (it "space key does nothing when not paused"
    (reset! atoms/paused false)
    (let [round @atoms/round-number]
      (input/dispatch-key :space nil)
      (should= round @atoms/round-number)))

  (it "unrecognized key is ignored"
    (input/dispatch-key :Q nil)
    (should= false @atoms/backtick-pressed)))

(describe "dispatch-key coverage gaps"
  (around [it]
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O~~"
                                             "~~~"
                                             "~~~"]))
    (it))

  (it "unknown backtick key places no unit and clears backtick"
    (reset! atoms/backtick-pressed true)
    (input/dispatch-key :X [0 0])
    (should= false @atoms/backtick-pressed)
    (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))

  (it "backtick :o with nil coords clears backtick without claiming city"
    (reset! atoms/backtick-pressed true)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (input/dispatch-key :o nil)
      (should= false @atoms/backtick-pressed)
      (should= :player (get-in @atoms/game-map (conj city-coords :city-status)))))

  (it ":f without destination does not set flight path"
    (reset! atoms/destination nil)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (input/dispatch-key :f city-coords)
      (should-be-nil (get-in @atoms/game-map (conj city-coords :flight-path)))))

  (it ":P with coords still toggles pause"
    (reset! atoms/paused false)
    (input/dispatch-key :P [1 1])
    (should @atoms/pause-requested))

  (it "! with coords still saves"
    (let [saved (atom false)]
      (with-redefs [save-load/save-game! (fn [] (reset! saved true) "test.edn")]
        (input/dispatch-key (keyword "!") [1 1])
        (should @saved))))

  (it ":m on non-city cell falls through to handle-key"
    (reset! atoms/destination [2 2])
    (input/dispatch-key :m [0 1])
    (should= [2 2] @atoms/destination)))

(describe "army-aboard-action"
  (it "disembarks to empty land when not extended"
    (should= :disembark
             (input/army-aboard-action false {:type :land} false)))

  (it "disembarks with target to empty land when extended"
    (should= :disembark-with-target
             (input/army-aboard-action true {:type :land} false)))

  (it "conquers hostile city when not extended"
    (should= :conquest
             (input/army-aboard-action false {:type :city :city-status :computer} true)))

  (it "ignores hostile city when extended"
    (should= :ignore
             (input/army-aboard-action true {:type :city :city-status :computer} true)))

  (it "ignores occupied land"
    (should= :ignore
             (input/army-aboard-action false {:type :land :contents {:type :army}} false)))

  (it "ignores sea target"
    (should= :ignore
             (input/army-aboard-action false {:type :sea} false)))

  (it "prefers land disembark over hostile city"
    (should= :disembark
             (input/army-aboard-action false {:type :land} true))))
