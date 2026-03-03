(ns empire.ui.util.input.dispatch-spec
  (:require [empire.test-utils :as test-utils]
            [speclj.core :refer :all]
            [clojure.string :as string]
            [empire.ui.util.input.dispatch :as dispatch]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.player.orders :as orders]
            [empire.save-load :as save-load]
            [empire.test-utils :refer [build-test-map get-test-city get-test-unit set-test-unit reset-all-atoms! set-test-player-map! set-test-computer-map! set-test-world! update-test-world!]]))

(describe "key-down :P"
  (before
    (reset-all-atoms!)
    (test-utils/set-test-state! :paused false)
    (test-utils/set-test-state! :pause-requested false)
    (test-utils/set-test-state! :backtick-pressed false))

  (it "toggles pause when P is pressed"
    (dispatch/dispatch-key :P nil)
    (should (test-utils/read-test-state :pause-requested))))

(describe "key-down :space when paused"
  (before
    (reset-all-atoms!)
    (set-test-world! (build-test-map ["O"]))  ;; Player city so not game over
    (set-test-player-map! (build-test-map ["#"]))
    (set-test-computer-map! (build-test-map ["#"]))
    (test-utils/set-test-state! :paused true)
    (test-utils/set-test-state! :pause-requested false)
    (test-utils/set-test-state! :backtick-pressed false)
    (test-utils/set-test-state! :player-items [])
    (test-utils/set-test-state! :computer-items [])
    (test-utils/set-test-state! :round-number 5))

  (it "starts new round when both item lists are empty"
    (dispatch/dispatch-key :space nil)
    (should= 6 (test-utils/read-test-state :round-number)))

  (it "sets pause-requested to pause after round"
    (dispatch/dispatch-key :space nil)
    (should= true (test-utils/read-test-state :pause-requested)))

  (it "unpauses to allow game loop to process"
    (dispatch/dispatch-key :space nil)
    (should= false (test-utils/read-test-state :paused))))

(describe "save/load key handling"
  (around [it]
    (reset-all-atoms!)
    (it))

  (it "! key calls save-game! and shows confirmation"
    (let [saved (atom false)]
      (with-redefs [save-load/save-game! (fn [] (reset! saved true) "test-file.edn")]
        (dispatch/dispatch-key (keyword "!") nil)
        (should @saved)
        (should (string/includes? (test-utils/read-test-state :turn-message) "test-file.edn")))))

  (it "^ key opens load menu"
    (dispatch/dispatch-key (keyword "^") nil)
    (should= true (test-utils/read-test-state :load-menu-open))))

(describe "Escape key with load menu"
  (around [it]
    (reset-all-atoms!)
    (it))

  (it "closes load menu when open"
    (test-utils/set-test-state! :load-menu-open true)
    (test-utils/set-test-state! :load-menu-files ["file.edn"])
    (dispatch/dispatch-key :escape nil)
    (should= false (test-utils/read-test-state :load-menu-open))
    (should= [] (test-utils/read-test-state :load-menu-files)))

  (it "does nothing when load menu is closed"
    (dispatch/dispatch-key :escape nil)
    (should= false (test-utils/read-test-state :load-menu-open))))

(describe "key blocking while load menu open"
  (around [it]
    (reset-all-atoms!)
    (it))

  (it "ignores non-escape keys when menu is open"
    (test-utils/set-test-state! :load-menu-open true)
    (test-utils/set-test-state! :pause-requested false)
    (dispatch/dispatch-key :P nil)
    (should= false (test-utils/read-test-state :pause-requested)))

  (it "processes normal keys when menu is closed"
    (test-utils/set-test-state! :load-menu-open false)
    (test-utils/set-test-state! :paused false)
    (dispatch/dispatch-key :P nil)
    (should= true (test-utils/read-test-state :pause-requested))))

(describe "load menu click handling"
  (around [it]
    (reset-all-atoms!)
    (it))

  (it "loads selected file when clicking on menu item"
    (let [loaded (atom nil)]
      (test-utils/set-test-state! :load-menu-open true)
      (test-utils/set-test-state! :load-menu-files ["file1.edn" "file2.edn"])
      (test-utils/set-test-state! :load-menu-hovered 1)
      (with-redefs [save-load/load-game! (fn [f] (reset! loaded f))]
        (dispatch/handle-load-menu-click)
        (should= "file2.edn" @loaded))))

  (it "does nothing when no file is hovered"
    (let [loaded (atom nil)]
      (test-utils/set-test-state! :load-menu-open true)
      (test-utils/set-test-state! :load-menu-files ["file1.edn"])
      (test-utils/set-test-state! :load-menu-hovered nil)
      (with-redefs [save-load/load-game! (fn [f] (reset! loaded f))]
        (dispatch/handle-load-menu-click)
        (should-be-nil @loaded)))))

(describe "dispatch-key backtick mode"
  (before
    (reset-all-atoms!)
    (set-test-world! (build-test-map ["#~O"
                                      "~~~"
                                      "~~~"]))
    (test-utils/set-test-state! :backtick-pressed true))

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
      (test-utils/set-test-state! :backtick-pressed true)
      (dispatch/dispatch-key k coords)
      (should= unit-type (:type (:contents (get-in (test-utils/read-test-state :game-map) coords))))
      (should= :player (:owner (:contents (get-in (test-utils/read-test-state :game-map) coords))))
      (update-test-world! assoc-in (conj coords :contents) nil)))

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
      (test-utils/set-test-state! :backtick-pressed true)
      (dispatch/dispatch-key k coords)
      (should= unit-type (:type (:contents (get-in (test-utils/read-test-state :game-map) coords))))
      (should= :computer (:owner (:contents (get-in (test-utils/read-test-state :game-map) coords))))
      (update-test-world! assoc-in (conj coords :contents) nil)))

  (it "claims city for player with :o"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (dispatch/dispatch-key :o city-coords)
      (should= :player (get-in (test-utils/read-test-state :game-map) (conj city-coords :city-status)))))

  (it "clears backtick-pressed"
    (dispatch/dispatch-key :A [0 0])
    (should= false (test-utils/read-test-state :backtick-pressed)))

  (it "does nothing with nil cell-coords"
    (dispatch/dispatch-key :A nil)
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "clears backtick-pressed even with nil cell-coords"
    (dispatch/dispatch-key :A nil)
    (should= false (test-utils/read-test-state :backtick-pressed))))

(describe "dispatch-key normal mode mouse commands"
  (before
    (reset-all-atoms!)
    (set-test-world! (build-test-map ["O~~"
                                      "~~~"
                                      "~~~"])))

  (it ". key sets destination"
    (dispatch/dispatch-key (keyword ".") [1 2])
    (should= [1 2] (test-utils/read-test-state :destination)))

  (it ". key does nothing with nil coords"
    (dispatch/dispatch-key (keyword ".") nil)
    (should-be-nil (test-utils/read-test-state :destination)))

  (it ":m key sets marching orders on player city"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (test-utils/set-test-state! :destination [2 2])
      (dispatch/dispatch-key :m city-coords)
      (should= [2 2] (get-in (test-utils/read-test-state :game-map) (conj city-coords :marching-orders)))))

  (it ":m key does nothing with nil coords"
    (test-utils/set-test-state! :destination [2 2])
    (dispatch/dispatch-key :m nil)
    (should= [2 2] (test-utils/read-test-state :destination)))

  (it ":f key sets flight path on player city"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (test-utils/set-test-state! :destination [2 2])
      (dispatch/dispatch-key :f city-coords)
      (should= [2 2] (get-in (test-utils/read-test-state :game-map) (conj city-coords :flight-path)))))

  (it ":f key does nothing with nil coords"
    (test-utils/set-test-state! :destination [2 2])
    (dispatch/dispatch-key :f nil)
    (should= [2 2] (test-utils/read-test-state :destination)))

  (it ":f key does nothing without destination"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (dispatch/dispatch-key :f city-coords)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) (conj city-coords :flight-path)))))

  (it ":u key wakes player city"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (test-utils/update-test-state! :production assoc city-coords :army)
      (dispatch/dispatch-key :u city-coords)
      (should-be-nil (get (test-utils/read-test-state :production) city-coords))))

  (it ":u key does nothing with nil coords"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (test-utils/update-test-state! :production assoc city-coords :army)
      (dispatch/dispatch-key :u nil)
      (should= :army (get (test-utils/read-test-state :production) city-coords))))

  (it ":l key sets lookaround on player city"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (dispatch/dispatch-key :l city-coords)
      (should= :lookaround (get-in (test-utils/read-test-state :game-map) (conj city-coords :marching-orders)))))

  (it ":l key does nothing with nil coords"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (dispatch/dispatch-key :l nil)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) (conj city-coords :marching-orders)))))

  (it "direction key sets city marching orders to edge"
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (dispatch/dispatch-key :d city-coords)
      (should= [2 0] (get-in (test-utils/read-test-state :game-map) (conj city-coords :marching-orders)))))

  (it "direction key does nothing with nil coords"
    (dispatch/dispatch-key :d nil)
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (should-be-nil (get-in (test-utils/read-test-state :game-map) (conj city-coords :marching-orders)))))

  (it "backtick key sets backtick-pressed"
    (dispatch/dispatch-key (keyword "`") nil)
    (should= true (test-utils/read-test-state :backtick-pressed)))

  (it "+ key cycles map display"
    (let [before (test-utils/read-test-state :map-to-display)]
      (dispatch/dispatch-key :+ nil)
      (should-not= before (test-utils/read-test-state :map-to-display))))

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
    (test-utils/set-test-state! :paused false)
    (let [round (test-utils/read-test-state :round-number)]
      (dispatch/dispatch-key :space nil)
      (should= round (test-utils/read-test-state :round-number))))

  (it "unrecognized key is ignored"
    (dispatch/dispatch-key :Q nil)
    (should= false (test-utils/read-test-state :backtick-pressed))))

(describe "dispatch-key coverage gaps"
  (around [it]
    (reset-all-atoms!)
    (set-test-world! (build-test-map ["O~~"
                                      "~~~"
                                      "~~~"]))
    (it))

  (it "unknown backtick key places no unit and clears backtick"
    (test-utils/set-test-state! :backtick-pressed true)
    (dispatch/dispatch-key :X [0 0])
    (should= false (test-utils/read-test-state :backtick-pressed))
    (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

  (it "backtick :o with nil coords clears backtick without claiming city"
    (test-utils/set-test-state! :backtick-pressed true)
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (dispatch/dispatch-key :o nil)
      (should= false (test-utils/read-test-state :backtick-pressed))
      (should= :player (get-in (test-utils/read-test-state :game-map) (conj city-coords :city-status)))))

  (it ":f without destination does not set flight path"
    (test-utils/set-test-state! :destination nil)
    (let [city-coords (:pos (get-test-city (test-utils/game-map-atom) "O"))]
      (dispatch/dispatch-key :f city-coords)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) (conj city-coords :flight-path)))))

  (it ":P with coords still toggles pause"
    (test-utils/set-test-state! :paused false)
    (dispatch/dispatch-key :P [1 1])
    (should (test-utils/read-test-state :pause-requested)))

  (it "! with coords still saves"
    (let [saved (atom false)]
      (with-redefs [save-load/save-game! (fn [] (reset! saved true) "test.edn")]
        (dispatch/dispatch-key (keyword "!") [1 1])
        (should @saved))))

  (it ":m on non-city cell falls through to handle-key"
    (test-utils/set-test-state! :destination [2 2])
    (dispatch/dispatch-key :m [0 1])
    (should= [2 2] (test-utils/read-test-state :destination))))

(describe "debug drag helpers"
  (before (reset-all-atoms!))

  (it "modifier-held? returns true when any modifier key is true"
    (should (dispatch/modifier-held? {:ctrl true}))
    (should (dispatch/modifier-held? {:meta true}))
    (should (dispatch/modifier-held? {:alt true})))

  (it "modifier-held? returns false when no modifier is true"
    (should-not (dispatch/modifier-held? {:ctrl false :meta false :alt false})))

  (it "debug-drag-start! sets start and current positions"
    (dispatch/debug-drag-start! 10 20)
    (should= [10 20] (test-utils/read-test-state :debug-drag-start))
    (should= [10 20] (test-utils/read-test-state :debug-drag-current)))

  (it "debug-drag-update! updates current position only when dragging"
    (test-utils/set-test-state! :debug-drag-start nil)
    (test-utils/set-test-state! :debug-drag-current nil)
    (dispatch/debug-drag-update! 1 2)
    (should-be-nil (test-utils/read-test-state :debug-drag-current))
    (dispatch/debug-drag-start! 3 4)
    (dispatch/debug-drag-update! 5 6)
    (should= [5 6] (test-utils/read-test-state :debug-drag-current)))

  (it "debug-drag-end! clears drag state even when no modifier held"
    (dispatch/debug-drag-start! 10 10)
    (dispatch/debug-drag-end! 20 20 {:ctrl false :meta false :alt false})
    (should-be-nil (test-utils/read-test-state :debug-drag-start))
    (should-be-nil (test-utils/read-test-state :debug-drag-current))
    (should= "" (test-utils/read-test-state :debug-message)))

  (it "debug-drag-end! does not write dump when selection has no area"
    (dispatch/debug-drag-start! 10 10)
    (with-redefs [empire.debug/screen-coords-to-cell-range (fn [_ _] [[1 1] [1 1]])
                  empire.debug/write-dump! (fn [_ _] (throw (ex-info "should not dump" {})))]
      (dispatch/debug-drag-end! 20 20 {:ctrl true})
      (should= "" (test-utils/read-test-state :debug-message)))
    (should-be-nil (test-utils/read-test-state :debug-drag-start))
    (should-be-nil (test-utils/read-test-state :debug-drag-current)))

  (it "debug-drag-end! writes dump and updates debug message for area selection"
    (dispatch/debug-drag-start! 10 10)
    (with-redefs [empire.debug/screen-coords-to-cell-range (fn [_ _] [[1 1] [2 3]])
                  empire.debug/write-dump! (fn [start end]
                                             (should= [1 1] start)
                                             (should= [2 3] end)
                                             "debug-dump.txt")]
      (dispatch/debug-drag-end! 20 30 {:ctrl true})
      (should= "Debug: debug-dump.txt" (test-utils/read-test-state :debug-message)))
    (should-be-nil (test-utils/read-test-state :debug-drag-start))
    (should-be-nil (test-utils/read-test-state :debug-drag-current)))

  (it "debug-drag-end! is no-op when no drag is active"
    (dispatch/debug-drag-end! 1 1 {:ctrl true})
    (should-be-nil (test-utils/read-test-state :debug-drag-start))
    (should-be-nil (test-utils/read-test-state :debug-drag-current))))
