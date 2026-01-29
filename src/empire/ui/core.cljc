(ns empire.ui.core
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.init :as init]
            [empire.move-log :as move-log]
            [empire.ui.input :as input]
            [empire.ui.rendering :as rendering]
            [quil.core :as q]
            [quil.middleware :as m]))

(defn create-fonts
  "Creates and caches font objects."
  []
  (reset! atoms/text-font (q/create-font "Courier New" 18))
  (reset! atoms/production-char-font (q/create-font "CourierNewPS-BoldMT" 12)))

(defn compute-screen-dimensions
  "Pure calculation of screen dimensions. Returns a map with :map-size, :map-screen-dimensions, and :text-area-dimensions."
  [char-width char-height screen-w screen-h]
  (let [cols (quot screen-w char-width)
        text-rows 4
        text-gap 7
        text-h (* text-rows char-height)
        rows (quot (+ (- screen-h text-h) text-gap) char-height)
        map-display-w (* cols char-width)
        map-display-h (* rows char-height)
        text-x 0
        text-y (+ map-display-h text-gap)
        text-w screen-w]
    {:map-size [cols rows]
     :map-screen-dimensions [map-display-w map-display-h]
     :text-area-dimensions [text-x text-y text-w text-h]}))

(defn calculate-screen-dimensions
  "Calculates map size and display dimensions based on screen and sets config values."
  []
  (q/text-font @atoms/text-font)
  (let [char-width (q/text-width "M")
        char-height (+ (q/text-ascent) (q/text-descent))
        screen-w (q/width)
        screen-h (q/height)
        dims (compute-screen-dimensions char-width char-height screen-w screen-h)]
    (reset! atoms/map-size (:map-size dims))
    (reset! atoms/map-screen-dimensions (:map-screen-dimensions dims))
    (reset! atoms/text-area-dimensions (:text-area-dimensions dims))))

(defn setup
  "Initial setup for the game state."
  []
  (move-log/init-log!)
  (create-fonts)
  (calculate-screen-dimensions)
  (init/make-initial-map @atoms/map-size config/smooth-count config/land-fraction config/number-of-cities config/min-city-distance)
  (q/frame-rate 30)
  {})

(defn update-state
  "Update the game state."
  [state]
  (game-loop/update-map)
  (rendering/update-hover-status)
  state)

(defn draw-state
  "Draw the current game state."
  [_state]
  (q/background 0)
  (let [the-map (case @atoms/map-to-display
                  :player-map @atoms/player-map
                  :computer-map @atoms/computer-map
                  :actual-map @atoms/game-map)]
    (rendering/draw-map the-map)
    (rendering/draw-debug-selection-rectangle)
    (rendering/draw-message-area)))

(defn key-pressed [state _]
  (let [k (q/key-as-keyword)]
    (when (not= k :shift)
      (when (nil? @atoms/last-key)
        (input/key-down k))
      (reset! atoms/last-key k)))
  state)

(defn key-released [_ _]
  (reset! atoms/last-key nil))

(defn- get-modifiers
  "Returns a map of modifier key states."
  []
  (let [mods (q/key-modifiers)]
    {:ctrl (:control mods)
     :meta (:meta mods)
     :alt (:alt mods)}))

(defn mouse-pressed [state _]
  (let [x (q/mouse-x)
        y (q/mouse-y)
        button (q/mouse-button)
        mods (get-modifiers)]
    ;; On macOS, Ctrl+Click becomes right-click, so accept any button with modifier
    (if (input/modifier-held? mods)
      (input/debug-drag-start! x y)
      (input/mouse-down x y button)))
  state)

(defn mouse-dragged [state _]
  (input/debug-drag-update! (q/mouse-x) (q/mouse-y))
  state)

(defn mouse-released [state _]
  (input/debug-drag-end! (q/mouse-x) (q/mouse-y) (get-modifiers))
  state)

(defn on-close [_]
  (q/no-loop)
  (q/exit)
  (println "Empire closed.")
  (System/exit 0))

(declare empire)
(defn -main [& _args]
  (println "empire has begun.")
  (q/defsketch empire
               :title "Empire: Global Conquest"
               :size :fullscreen
               :setup setup
               :update update-state
               :draw draw-state
               :key-pressed key-pressed
               :key-released key-released
               :mouse-pressed mouse-pressed
               :mouse-dragged mouse-dragged
               :mouse-released mouse-released
               :features []
               :middleware [m/fun-mode]
               :on-close on-close
               :host "empire"))
