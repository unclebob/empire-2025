(ns empire.core
  (:require [empire.config :as config]
            [empire.init :as init]
            [empire.map :as map]
            [empire.atoms :as atoms]
            [quil.core :as q]
            [quil.middleware :as m]))

(defn calculate-screen-dimensions
  "Calculates map size and display dimensions based on screen and sets config values."
  []
  (q/text-font (q/create-font "Courier New" 18))
  (let [char-width (q/text-width "M")
        char-height (+ (q/text-ascent) (q/text-descent))
        screen-w (q/width)
        screen-h (q/height)
        cols (quot screen-w char-width)
        text-rows 3
        text-gap 5
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
  (calculate-screen-dimensions)
  (init/make-initial-map @atoms/map-size config/smooth-count config/land-fraction config/number-of-cities config/min-city-distance)
  (q/frame-rate 10)
  {})

(defn update-state
  "Update the game state."
  [state]
  (map/update-map)
  state)

(defn draw-state
  "Draw the current game state."
  [_state]
  (let [start-time (System/currentTimeMillis)]
    (q/background 0)
    (let [the-map (if @atoms/test-mode @map/game-map @map/visible-map)]
      (map/draw-map the-map)
      (let [end-time (System/currentTimeMillis)
            draw-time (- end-time start-time)
            [text-x text-y _ _] @atoms/text-area-dimensions]
        (q/text-font (q/create-font "Courier New" 18))
        (q/fill 255)
        (q/text (str "Map size: " @atoms/map-size " Draw time: " draw-time "ms") (+ text-x 10) (+ text-y 10))))))

  (defn key-down [k]
    ;; Handle key down events
    (cond
      (= k :t) (swap! atoms/test-mode not)
      :else (println "Key down:" k)))

  (defn key-pressed [state _]
    (let [k (q/key-as-keyword)]
      (when (nil? @atoms/last-key)
        (key-down k))
      (reset! atoms/last-key k))
    state)

  (defn key-released [_ _]
    (reset! atoms/last-key nil))

  (defn on-close [_]
    (q/no-loop)
    (q/exit)                                                ; Exit the sketch
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
                 :features [:keep-on-top]
                 :middleware [m/fun-mode]
                 :on-close on-close
                 :host "empire"))
