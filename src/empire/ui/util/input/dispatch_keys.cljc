(ns empire.ui.util.input.dispatch-keys
  (:require [clojure.string :as string]
            [empire.game.save-load :as save-load]
            [empire.state.api :as sa]
            [empire.game.loop.core :as game-loop]
            [empire.player.orders :as player-orders]
            [empire.ui.util.help :as help]
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
   :p (fn [coords] (player-orders/clear-city-production-at coords))
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

(defn- dispatch-save-menu-edit
  [k]
  (clear-default-save-input!)
  (if (delete-key? k)
    (do (save-load/backspace-save-menu-input!) true)
    (when-let [ch (save-char-key k)]
      (save-load/append-save-menu-char! ch)
      true)))

(defn dispatch-save-menu-key
  [k]
  (cond
    (= k :escape) (do (save-load/close-save-menu!) true)
    (enter-key? k) (do (sa/write-state! :command-message
                                        (str "Saved to " (save-load/save-from-menu!)))
                       true)
    :else (dispatch-save-menu-edit k)))

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
(def help-key (keyword "?"))

(defn dispatch-help-key
  [k]
  (help/handle-help-key k))

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

(def ^:private game-control-handlers
  {help-key (fn [] (help/open-help!))
   backtick-key (fn [] (sa/write-state! :backtick-pressed true))
   :P (fn [] (game-loop/toggle-pause))
   :+ (fn [] (sa/update-state! :map-to-display cycle-map-display))})

(defn- paused-space?
  [k]
  (and (= k :space) (sa/read-state :paused)))

(defn dispatch-game-control-key [k]
  (if-let [handle (game-control-handlers k)]
    (do (handle) true)
    (when (paused-space? k)
      (game-loop/step-one-round)
      true)))

(defn dispatch-save-load-key [k]
  (cond
    (= k bang-key) (do
                     (if (save-dialog-available?)
                       (save-load/open-save-menu!)
                       (sa/write-state! :command-message
                                        (str "Saved to " (save-load/save-game! "saves"))))
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

(defn- dispatch-waiting-input-key
  [k cell-coords]
  (when (and (sa/read-state :waiting-for-input)
             (not (and cell-coords (standing-order-handlers k))))
    (actions/handle-key k)))

(defn- dispatch-production-key
  [k cell-coords]
  (when (and (= k :p) cell-coords)
    (dispatch-standing-order-key k cell-coords)))

(defn dispatch-normal-key [k cell-coords]
  (or (dispatch-game-control-key k)
      (dispatch-save-load-key k)
      (dispatch-production-key k cell-coords)
      (dispatch-waiting-input-key k cell-coords)
      (dispatch-coord-key k cell-coords)
      (actions/handle-key k)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:41:12.744771-05:00", :module-hash "802697805", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-554791877"} {:id "def/backtick-unit-map", :kind "def", :line 10, :end-line nil, :hash "-249117167"} {:id "def/standing-order-handlers", :kind "def", :line 18, :end-line nil, :hash "1289175283"} {:id "defn/dispatch-load-menu-key", :kind "defn", :line 27, :end-line nil, :hash "-2009929628"} {:id "defn/save-char-key", :kind "defn", :line 30, :end-line nil, :hash "-1609212192"} {:id "defn/clear-default-save-input!", :kind "defn", :line 37, :end-line nil, :hash "-1555783017"} {:id "defn/delete-key?", :kind "defn", :line 43, :end-line nil, :hash "-1902152722"} {:id "defn/enter-key?", :kind "defn", :line 54, :end-line nil, :hash "1126374611"} {:id "defn-/dispatch-save-menu-edit", :kind "defn-", :line 64, :end-line nil, :hash "-2141894208"} {:id "defn/dispatch-save-menu-key", :kind "defn", :line 73, :end-line nil, :hash "-1962368209"} {:id "defn/dispatch-backtick-key", :kind "defn", :line 82, :end-line nil, :hash "171420772"} {:id "def/backtick-key", :kind "def", :line 90, :end-line nil, :hash "-102465311"} {:id "def/bang-key", :kind "def", :line 91, :end-line nil, :hash "-645220389"} {:id "def/caret-key", :kind "def", :line 92, :end-line nil, :hash "-282349008"} {:id "defn/save-dialog-available?", :kind "defn", :line 94, :end-line nil, :hash "2009159762"} {:id "defn/cycle-map-display", :kind "defn", :line 99, :end-line nil, :hash "-1723051264"} {:id "defn/dispatch-game-control-key", :kind "defn", :line 106, :end-line nil, :hash "-1608682103"} {:id "defn/dispatch-save-load-key", :kind "defn", :line 113, :end-line nil, :hash "-2106685747"} {:id "defn/dispatch-standing-order-key", :kind "defn", :line 123, :end-line nil, :hash "561409852"} {:id "defn/dispatch-coord-key", :kind "defn", :line 129, :end-line nil, :hash "1474393818"} {:id "defn-/unit-has-attention?", :kind "defn-", :line 134, :end-line nil, :hash "919285969"} {:id "defn-/dispatch-waiting-input-key", :kind "defn-", :line 139, :end-line nil, :hash "1908896677"} {:id "defn-/dispatch-production-key", :kind "defn-", :line 145, :end-line nil, :hash "-991454570"} {:id "defn/dispatch-normal-key", :kind "defn", :line 150, :end-line nil, :hash "122973513"}]}
;; clj-mutate-manifest-end
