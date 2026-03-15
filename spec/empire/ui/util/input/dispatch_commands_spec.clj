(ns empire.ui.util.input.dispatch-commands-spec
  (:require [empire.player.orders :as orders]
            [empire.test.utils :as test-utils]
            [empire.ui.util.input.dispatch :as dispatch]
            [speclj.core :refer :all]
            [empire.test.utils :refer [build-test-map get-test-city reset-all-atoms! set-test-world! update-test-world!]]))

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
    (test-utils/set-test-state! :map-screen-dimensions [300 300])
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
    (dispatch/dispatch-key (keyword "!") [1 1])
    (should= true (test-utils/read-test-state :save-menu-open)))

  (it ":m on non-city cell falls through to handle-key"
    (test-utils/set-test-state! :destination [2 2])
    (dispatch/dispatch-key :m [0 1])
    (should= [2 2] (test-utils/read-test-state :destination))))
