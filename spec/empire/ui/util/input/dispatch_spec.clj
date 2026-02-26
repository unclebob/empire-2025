(ns empire.ui.util.input.dispatch-spec
  (:require [speclj.core :refer :all]
            [clojure.string :as string]
            [empire.ui.util.input.dispatch :as dispatch]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.player.orders :as orders]
            [empire.save-load :as save-load]
            [empire.test-utils :refer [build-test-map get-test-city get-test-unit set-test-unit reset-all-atoms!]]))

(describe "key-down :P"
  (before
    (reset-all-atoms!)
    (reset! atoms/paused false)
    (reset! atoms/pause-requested false)
    (reset! atoms/backtick-pressed false))

  (it "toggles pause when P is pressed"
    (dispatch/dispatch-key :P nil)
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
    (dispatch/dispatch-key :space nil)
    (should= 6 @atoms/round-number))

  (it "sets pause-requested to pause after round"
    (dispatch/dispatch-key :space nil)
    (should= true @atoms/pause-requested))

  (it "unpauses to allow game loop to process"
    (dispatch/dispatch-key :space nil)
    (should= false @atoms/paused)))

(describe "save/load key handling"
  (around [it]
    (reset-all-atoms!)
    (it))

  (it "! key calls save-game! and shows confirmation"
    (let [saved (atom false)]
      (with-redefs [save-load/save-game! (fn [] (reset! saved true) "test-file.edn")]
        (dispatch/dispatch-key (keyword "!") nil)
        (should @saved)
        (should (string/includes? @atoms/turn-message "test-file.edn")))))

  (it "^ key opens load menu"
    (dispatch/dispatch-key (keyword "^") nil)
    (should= true @atoms/load-menu-open)))

(describe "Escape key with load menu"
  (around [it]
    (reset-all-atoms!)
    (it))

  (it "closes load menu when open"
    (reset! atoms/load-menu-open true)
    (reset! atoms/load-menu-files ["file.edn"])
    (dispatch/dispatch-key :escape nil)
    (should= false @atoms/load-menu-open)
    (should= [] @atoms/load-menu-files))

  (it "does nothing when load menu is closed"
    (dispatch/dispatch-key :escape nil)
    (should= false @atoms/load-menu-open)))

(describe "key blocking while load menu open"
  (around [it]
    (reset-all-atoms!)
    (it))

  (it "ignores non-escape keys when menu is open"
    (reset! atoms/load-menu-open true)
    (reset! atoms/pause-requested false)
    (dispatch/dispatch-key :P nil)
    (should= false @atoms/pause-requested))

  (it "processes normal keys when menu is closed"
    (reset! atoms/load-menu-open false)
    (reset! atoms/paused false)
    (dispatch/dispatch-key :P nil)
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
        (dispatch/handle-load-menu-click)
        (should= "file2.edn" @loaded))))

  (it "does nothing when no file is hovered"
    (let [loaded (atom nil)]
      (reset! atoms/load-menu-open true)
      (reset! atoms/load-menu-files ["file1.edn"])
      (reset! atoms/load-menu-hovered nil)
      (with-redefs [save-load/load-game! (fn [f] (reset! loaded f))]
        (dispatch/handle-load-menu-click)
        (should-be-nil @loaded)))))

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
      (dispatch/dispatch-key k coords)
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
      (dispatch/dispatch-key k coords)
      (should= unit-type (:type (:contents (get-in @atoms/game-map coords))))
      (should= :computer (:owner (:contents (get-in @atoms/game-map coords))))
      (swap! atoms/game-map assoc-in (conj coords :contents) nil)))

  (it "claims city for player with :o"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (dispatch/dispatch-key :o city-coords)
      (should= :player (get-in @atoms/game-map (conj city-coords :city-status)))))

  (it "clears backtick-pressed"
    (dispatch/dispatch-key :A [0 0])
    (should= false @atoms/backtick-pressed))

  (it "does nothing with nil cell-coords"
    (dispatch/dispatch-key :A nil)
    (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))

  (it "clears backtick-pressed even with nil cell-coords"
    (dispatch/dispatch-key :A nil)
    (should= false @atoms/backtick-pressed)))

(describe "dispatch-key normal mode mouse commands"
  (before
    (reset-all-atoms!)
    (reset! atoms/game-map (build-test-map ["O~~"
                                             "~~~"
                                             "~~~"])))

  (it ". key sets destination"
    (dispatch/dispatch-key (keyword ".") [1 2])
    (should= [1 2] @atoms/destination))

  (it ". key does nothing with nil coords"
    (dispatch/dispatch-key (keyword ".") nil)
    (should-be-nil @atoms/destination))

  (it ":m key sets marching orders on player city"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/destination [2 2])
      (dispatch/dispatch-key :m city-coords)
      (should= [2 2] (get-in @atoms/game-map (conj city-coords :marching-orders)))))

  (it ":m key does nothing with nil coords"
    (reset! atoms/destination [2 2])
    (dispatch/dispatch-key :m nil)
    (should= [2 2] @atoms/destination))

  (it ":f key sets flight path on player city"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (reset! atoms/destination [2 2])
      (dispatch/dispatch-key :f city-coords)
      (should= [2 2] (get-in @atoms/game-map (conj city-coords :flight-path)))))

  (it ":f key does nothing with nil coords"
    (reset! atoms/destination [2 2])
    (dispatch/dispatch-key :f nil)
    (should= [2 2] @atoms/destination))

  (it ":f key does nothing without destination"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (dispatch/dispatch-key :f city-coords)
      (should-be-nil (get-in @atoms/game-map (conj city-coords :flight-path)))))

  (it ":u key wakes player city"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords :army)
      (dispatch/dispatch-key :u city-coords)
      (should-be-nil (get @atoms/production city-coords))))

  (it ":u key does nothing with nil coords"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (swap! atoms/production assoc city-coords :army)
      (dispatch/dispatch-key :u nil)
      (should= :army (get @atoms/production city-coords))))

  (it ":l key sets lookaround on player city"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (dispatch/dispatch-key :l city-coords)
      (should= :lookaround (get-in @atoms/game-map (conj city-coords :marching-orders)))))

  (it ":l key does nothing with nil coords"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (dispatch/dispatch-key :l nil)
      (should-be-nil (get-in @atoms/game-map (conj city-coords :marching-orders)))))

  (it "direction key sets city marching orders to edge"
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (dispatch/dispatch-key :d city-coords)
      (should= [2 0] (get-in @atoms/game-map (conj city-coords :marching-orders)))))

  (it "direction key does nothing with nil coords"
    (dispatch/dispatch-key :d nil)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (should-be-nil (get-in @atoms/game-map (conj city-coords :marching-orders)))))

  (it "backtick key sets backtick-pressed"
    (dispatch/dispatch-key (keyword "`") nil)
    (should= true @atoms/backtick-pressed))

  (it "+ key cycles map display"
    (let [before @atoms/map-to-display]
      (dispatch/dispatch-key :+ nil)
      (should-not= before @atoms/map-to-display)))

  (it "* key sets waypoint at coords"
    (let [called (atom nil)]
      (with-redefs [orders/set-waypoint-at (fn [c] (reset! called c) true)]
        (dispatch/dispatch-key (keyword "*") [1 1])
        (should= [1 1] @called))))

  (it "* key does nothing with nil coords"
    (let [called (atom false)]
      (with-redefs [orders/set-waypoint-at (fn [_] (reset! called true))]
        (dispatch/dispatch-key (keyword "*") nil)
        (should= false @called))))

  (it "space key does nothing when not paused"
    (reset! atoms/paused false)
    (let [round @atoms/round-number]
      (dispatch/dispatch-key :space nil)
      (should= round @atoms/round-number)))

  (it "unrecognized key is ignored"
    (dispatch/dispatch-key :Q nil)
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
    (dispatch/dispatch-key :X [0 0])
    (should= false @atoms/backtick-pressed)
    (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))

  (it "backtick :o with nil coords clears backtick without claiming city"
    (reset! atoms/backtick-pressed true)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (dispatch/dispatch-key :o nil)
      (should= false @atoms/backtick-pressed)
      (should= :player (get-in @atoms/game-map (conj city-coords :city-status)))))

  (it ":f without destination does not set flight path"
    (reset! atoms/destination nil)
    (let [city-coords (:pos (get-test-city atoms/game-map "O"))]
      (dispatch/dispatch-key :f city-coords)
      (should-be-nil (get-in @atoms/game-map (conj city-coords :flight-path)))))

  (it ":P with coords still toggles pause"
    (reset! atoms/paused false)
    (dispatch/dispatch-key :P [1 1])
    (should @atoms/pause-requested))

  (it "! with coords still saves"
    (let [saved (atom false)]
      (with-redefs [save-load/save-game! (fn [] (reset! saved true) "test.edn")]
        (dispatch/dispatch-key (keyword "!") [1 1])
        (should @saved))))

  (it ":m on non-city cell falls through to handle-key"
    (reset! atoms/destination [2 2])
    (dispatch/dispatch-key :m [0 1])
    (should= [2 2] @atoms/destination)))
