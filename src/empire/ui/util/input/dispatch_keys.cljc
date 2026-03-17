(ns empire.ui.util.input.dispatch-keys
  (:require [clojure.string :as string]
            [empire.game.save-load :as save-load]
            [empire.state.api :as sa]
            [empire.game.loop.core :as game-loop]
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
  (when-let [f (standing-order-handlers k)]
    (when cell-coords (f cell-coords))))

(defn dispatch-coord-key [k cell-coords]
  (when cell-coords
    (or (dispatch-standing-order-key k cell-coords)
        (player-orders/set-city-marching-orders-by-direction-at cell-coords k))))

(defn dispatch-normal-key [k cell-coords]
  (or (dispatch-game-control-key k)
      (dispatch-save-load-key k)
      (dispatch-coord-key k cell-coords)
      (actions/handle-key k)))
