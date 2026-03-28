(ns empire.ui.util.input.dispatch-keys
  (:require [clojure.string :as string]
            [empire.game.save-load :as save-load]
            [empire.state.api :as sa]
            [empire.game.loop.core :as game-loop]
            [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.player.orders :as player-orders]
            [empire.ui.util.input.actions :as actions]))

(def backtick-unit-map
  {:A [:army :player] :F [:fighter :player] :Z [:satellite :player]
   :T [:transport :player] :P [:patrol-boat :player] :D [:destroyer :player]
   :S [:submarine :player] :C [:carrier :player] :B [:battleship :player]
   :a [:army :computer] :f [:fighter :computer] :z [:satellite :computer]
   :t [:transport :computer] :p [:patrol-boat :computer] :d [:destroyer :computer]
   :s [:submarine :computer] :c [:carrier :computer] :b [:battleship :computer]})

(def standing-order-handlers
  {(keyword ".") (fn [coords] (player-orders/set-destination-at coords))
   (keyword "*") (fn [coords] (player-orders/set-waypoint-at coords))
   :l (fn [coords] (player-orders/set-city-lookaround coords))
   :u (fn [coords] (player-orders/wake-at coords))
   :m (fn [coords] (player-orders/set-marching-orders-at coords))
   :f (fn [coords] (player-orders/set-flight-path-at coords))})

(defn dispatch-load-menu-key [k]
  (when (= k :escape) (save-load/close-load-menu!)))

(defn save-char-key
  [k]
  (let [s (name k)]
    (when (and (= 1 (count s))
               (re-matches #"[A-Za-z0-9._-]" s))
      s)))

(defn clear-default-save-input!
  []
  (when (sa/read-state :save-menu-default-active)
    (sa/write-state! :save-menu-input "")
    (sa/write-state! :save-menu-default-active false)))

(defn delete-key?
  [k]
  (let [name-lc (some-> k name string/lower-case)]
    (or (= k :backspace)
        (= k :delete)
        (= k :del)
        (= k (keyword (str (char 127))))
        (= name-lc "forward-delete")
        (= name-lc "kp-delete")
        (string/includes? (or name-lc "") "delete"))))

(defn enter-key?
  [k]
  (let [name-lc (some-> k name string/lower-case)]
    (or (= k :enter)
        (= k :return)
        (= k :newline)
        (= k (keyword (str \newline)))
        (= name-lc "kp-enter")
        (string/includes? (or name-lc "") "enter"))))

(defn dispatch-save-menu-key
  [k]
  (cond
    (= k :escape) (do (save-load/close-save-menu!) true)
    (enter-key? k) (do (sa/write-state! :turn-message
                                        (str "Saved to " (save-load/save-from-menu!)))
                       (sa/write-state! :turn-message-until (+ (System/currentTimeMillis) 3000))
                       true)
    :else (do
            (clear-default-save-input!)
            (cond
              (delete-key? k) (do (save-load/backspace-save-menu-input!) true)
              :else (when-let [ch (save-char-key k)]
                      (save-load/append-save-menu-char! ch)
                      true)))))

(defn dispatch-backtick-key
  [k cell-coords]
  (sa/write-state! :backtick-pressed false)
  (when cell-coords
    (if-let [[unit-type owner] (backtick-unit-map k)]
      (player-orders/add-unit-at cell-coords unit-type owner)
      (when (= k :o) (player-orders/own-city-at cell-coords)))))

(def backtick-key (keyword "`"))
(def bang-key (keyword "!"))
(def caret-key (keyword "^"))

(defn save-dialog-available?
  []
  (let [[w h] (sa/read-state :map-screen-dimensions)]
    (and (pos? w) (pos? h))))

(defn cycle-map-display
  [current]
  ({:player-map :computer-map
    :computer-map :actual-map
    :actual-map :player-map}
   current))

(defn dispatch-game-control-key [k]
  (cond
    (= k backtick-key) (do (sa/write-state! :backtick-pressed true) true)
    (= k :P) (do (game-loop/toggle-pause) true)
    (= k :+) (do (sa/update-state! :map-to-display cycle-map-display) true)
    (and (= k :space) (sa/read-state :paused)) (do (game-loop/step-one-round) true)))

(defn dispatch-save-load-key [k]
  (cond
    (= k bang-key) (do
                     (if (save-dialog-available?)
                       (save-load/open-save-menu!)
                       (sa/write-state! :turn-message
                                        (str "Saved to " (save-load/save-game! "saves"))))
                     (sa/write-state! :turn-message-until (+ (System/currentTimeMillis) 3000))
                     true)
    (= k caret-key) (do (save-load/open-load-menu!) true)))

(defn dispatch-standing-order-key [k cell-coords]
  (if (and (= k (keyword ".")) (nil? cell-coords))
    (player-orders/clear-destination!)
    (when-let [f (standing-order-handlers k)]
      (when cell-coords (f cell-coords)))))

(defn dispatch-coord-key [k cell-coords]
  (or (dispatch-standing-order-key k cell-coords)
      (when cell-coords
        (player-orders/set-city-marching-orders-by-direction-at cell-coords k))))

(defn- unit-has-attention? []
  (when-let [coords (first (sa/read-state :cells-needing-attention))]
    (let [cell (get-in (sa/current-world) coords)]
      (movement-state/get-active-unit cell))))

(defn dispatch-normal-key [k cell-coords]
  (or (dispatch-game-control-key k)
      (dispatch-save-load-key k)
      (when (and (sa/read-state :waiting-for-input) (unit-has-attention?))
        (actions/handle-key k))
      (dispatch-coord-key k cell-coords)
      (actions/handle-key k)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:53:27.605582-05:00", :module-hash "-740707103", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "215924935"} {:id "def/backtick-unit-map", :kind "def", :line 9, :end-line 15, :hash "-249117167"} {:id "def/standing-order-handlers", :kind "def", :line 17, :end-line 23, :hash "1315951812"} {:id "defn/dispatch-load-menu-key", :kind "defn", :line 25, :end-line 26, :hash "-2009929628"} {:id "defn/save-char-key", :kind "defn", :line 28, :end-line 33, :hash "-1609212192"} {:id "defn/clear-default-save-input!", :kind "defn", :line 35, :end-line 39, :hash "-1555783017"} {:id "defn/delete-key?", :kind "defn", :line 41, :end-line 50, :hash "-1902152722"} {:id "defn/enter-key?", :kind "defn", :line 52, :end-line 60, :hash "1126374611"} {:id "defn/dispatch-save-menu-key", :kind "defn", :line 62, :end-line 76, :hash "-1210264817"} {:id "defn/dispatch-backtick-key", :kind "defn", :line 78, :end-line 84, :hash "171420772"} {:id "def/backtick-key", :kind "def", :line 86, :end-line 86, :hash "-102465311"} {:id "def/bang-key", :kind "def", :line 87, :end-line 87, :hash "-645220389"} {:id "def/caret-key", :kind "def", :line 88, :end-line 88, :hash "-282349008"} {:id "defn/save-dialog-available?", :kind "defn", :line 90, :end-line 93, :hash "2009159762"} {:id "defn/cycle-map-display", :kind "defn", :line 95, :end-line 100, :hash "-1723051264"} {:id "defn/dispatch-game-control-key", :kind "defn", :line 102, :end-line 107, :hash "-1608682103"} {:id "defn/dispatch-save-load-key", :kind "defn", :line 109, :end-line 118, :hash "1380158179"} {:id "defn/dispatch-standing-order-key", :kind "defn", :line 120, :end-line 122, :hash "-851749556"} {:id "defn/dispatch-coord-key", :kind "defn", :line 124, :end-line 127, :hash "-1787070461"} {:id "defn/dispatch-normal-key", :kind "defn", :line 129, :end-line 133, :hash "-1065966531"}]}
;; clj-mutate-manifest-end
