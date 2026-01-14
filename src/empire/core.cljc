(ns empire.core
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.init :as init]
            [empire.input :as input]
            [empire.rendering :as rendering]
            [quil.core :as q]
            [quil.middleware :as m]))

(defn create-fonts
  "Creates and caches font objects."
  []
  (reset! atoms/text-font (q/create-font "Courier New" 18))
  (reset! atoms/production-char-font (q/create-font "CourierNewPS-BoldMT" 12)))

(defn calculate-screen-dimensions
  "Calculates map size and display dimensions based on screen and sets config values."
  []
  (q/text-font @atoms/text-font)
  (let [char-width (q/text-width "M")
        char-height (+ (q/text-ascent) (q/text-descent))
        screen-w (q/width)
        screen-h (q/height)
        cols (quot screen-w char-width)
        text-rows 4
        text-gap 7
        text-h (* text-rows char-height)
        rows (quot (+ (- screen-h text-h) text-gap) char-height)
        map-display-w (* cols char-width)
        map-display-h (* rows char-height)
        text-x 0
        text-y (+ map-display-h text-gap)
        text-w screen-w]
    (reset! atoms/map-size [cols rows])
    (reset! atoms/map-screen-dimensions [map-display-w map-display-h])
    (reset! atoms/text-area-dimensions [text-x text-y text-w text-h])))

(defn setup
  "Initial setup for the game state."
  []
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

(defn mouse-pressed [_ _]
  (input/mouse-down (q/mouse-x) (q/mouse-y) (q/mouse-button)))

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
               :features []
               :middleware [m/fun-mode]
               :on-close on-close
               :host "empire"))
