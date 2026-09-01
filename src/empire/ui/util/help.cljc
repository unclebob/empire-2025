(ns empire.ui.util.help
  (:require [clojure.string :as string]
            [empire.state.api :as sa]))

(def help-width 720)
(def help-padding 16)
(def title-height 28)
(def line-height 16)
(def button-width 110)
(def button-height 24)
(def button-margin 12)
(def dismiss-label "Dismiss")
(def explanation-wrap 42)

(defn wrap-words
  [s max-chars]
  (let [words (string/split (or s "") #"\s+")]
    (reduce (fn [lines word]
              (let [current (peek lines)]
                (if (or (empty? current)
                        (<= (+ (count current) 1 (count word)) max-chars))
                  (conj (pop lines) (if (empty? current) word (str current " " word)))
                  (conj lines word))))
            [""]
            words)))

(def keystroke-sections
  [{:title "Help"
    :entries [{:keys "?"
               :explanation "Show this list of keystrokes"}]}
   {:title "Movement"
    :entries [{:keys "q w e a d z x c"
               :explanation "Move one cell in that compass direction"}
              {:keys "Q W E A D Z X C"
               :explanation "Move toward the map edge in that direction"}]}
   {:title "Unit commands"
    :entries [{:keys "space"
               :explanation "Skip this unit for the rest of the round"}
              {:keys "s"
               :explanation "Sentry: sleep until something nearby happens"}
              {:keys "u"
               :explanation "Unload or wake armies and fighters on this unit"}
              {:keys "l"
               :explanation "Explore land, or follow the coast in a ship"}]}
   {:title "Standing orders"
    :entries [{:keys "."
               :explanation "Set destination at the mouse, or clear it off-map"}
              {:keys "*"
               :explanation "Place or remove a waypoint at the mouse cell"}
              {:keys "m"
               :explanation "March toward the remembered destination"}
              {:keys "f"
               :explanation "Set a fighter flight path toward the destination"}
              {:keys "l"
               :explanation "Give the city under the mouse look-around orders"}
              {:keys "p"
               :explanation "Clear production at the city under the mouse"}
              {:keys "u"
               :explanation "Wake the sleeping unit under the mouse"}
              {:keys "q w e a d z x c"
               :explanation "March a city or waypoint toward that map edge"}]}
   {:title "City production"
    :entries [{:keys "a f z t p d s c b"
               :explanation "Produce army, fighter, satellite, transport, patrol boat, destroyer, submarine, carrier, or battleship"}
              {:keys "x"
               :explanation "Clear this city's production so it will ask again"}
              {:keys "space"
               :explanation "Skip this city for the rest of the round"}]}
   {:title "Game control"
    :entries [{:keys "P"
               :explanation "Pause or resume the game"}
              {:keys "space"
               :explanation "While paused, play exactly one more round"}
              {:keys "+"
               :explanation "Cycle player, computer, and true map views"}]}
   {:title "Save and load"
    :entries [{:keys "!"
               :explanation "Open the save-game dialog"}
              {:keys "^"
               :explanation "Open the load-game dialog"}
              {:keys "Enter"
               :explanation "Save using the name typed in the dialog"}
              {:keys "Escape"
               :explanation "Cancel the save or load dialog"}
              {:keys "Backspace / Delete"
               :explanation "Remove the last character of the save name"}]}
   {:title "Backtick commands"
    :entries [{:keys "`"
               :explanation "Prefix: press ` then a key with the mouse on a cell"}
              {:keys "` A F Z T P D S C B"
               :explanation "Place a player army, fighter, satellite, transport, patrol boat, destroyer, submarine, carrier, or battleship"}
              {:keys "` a f z t p d s c b"
               :explanation "Place the matching computer unit at the mouse"}
              {:keys "` o"
               :explanation "Claim the city under the mouse for the player"}]}])

(defn open-help!
  []
  (sa/write-state! :help-open true))

(defn close-help!
  []
  (sa/write-state! :help-open false)
  (sa/write-state! :help-dismiss-hovered false))

(defn entry-line-count
  [entry]
  (inc (count (wrap-words (:explanation entry) explanation-wrap))))

(defn- section-height
  [section]
  (* line-height (+ 2 (reduce + 0 (map entry-line-count (:entries section))))))

(defn- column-height
  [sections]
  (reduce + 0 (map section-height sections)))

(defn column-sections
  []
  (let [sections (vec keystroke-sections)
        mid (long (Math/ceil (/ (count sections) 2.0)))]
    [(subvec sections 0 mid)
     (subvec sections mid)]))

(defn help-geometry
  [screen-w screen-h]
  (let [[left-col right-col] (column-sections)
        content-h (max (column-height left-col) (column-height right-col))
        height (+ help-padding title-height content-h button-margin button-height help-padding)
        left (/ (- screen-w help-width) 2)
        top (/ (- screen-h height) 2)
        button-x (+ left (/ (- help-width button-width) 2))
        button-y (+ top height (- help-padding) (- button-height))]
    {:left left
     :top top
     :width help-width
     :height height
     :right (+ left help-width)
     :bottom (+ top height)
     :content-top (+ top help-padding title-height)
     :columns [left-col right-col]
     :dismiss-button {:x button-x :y button-y :w button-width :h button-height}}))

(defn dismiss-button-hit?
  [px py geom]
  (let [{:keys [x y w h]} (:dismiss-button geom)]
    (and (>= px x) (< px (+ x w))
         (>= py y) (< py (+ y h)))))

(defn- window-size
  []
  (let [[map-w map-h] (or (sa/read-state :map-screen-dimensions) [0 0])
        [_ text-y _ text-h] (or (sa/read-state :text-area-dimensions) [0 0 0 0])]
    [map-w (max map-h (+ (or text-y 0) (or text-h 0)))]))

(defn current-geometry
  []
  (or (sa/read-state :help-geometry)
      (let [[w h] (window-size)]
        (help-geometry w h))))

(defn handle-help-click
  [x y]
  (when (dismiss-button-hit? x y (current-geometry))
    (close-help!)))
