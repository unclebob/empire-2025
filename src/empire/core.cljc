(ns empire.core
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.init :as init]
            [empire.map :as map]
            [quil.core :as q]
            [quil.middleware :as m]))

(defn draw-menu
  "Draws the menu if it's visible."
  []
  (when (:visible @atoms/menu-state)
    (let [{:keys [x y header items]} @atoms/menu-state
          item-height 20
          menu-width 150
          menu-height (+ 30 (* (count items) item-height))  ;; Extra space for header
          mouse-x (q/mouse-x)
          mouse-y (q/mouse-y)
          highlighted-idx (when (and (>= mouse-x x) (< mouse-x (+ x menu-width)))
                            (first (filter #(let [item-y (+ y 35 (* % item-height))]  ;; Start detection 10px above text
                                              (and (>= mouse-y item-y) (< mouse-y (+ item-y item-height))))
                                           (range (count items)))))]
      (q/fill 200 200 200 200)
      (q/rect x y menu-width menu-height)
      ;; Header
      (q/fill 0)
      (q/text-font (q/create-font "CourierNewPS-BoldMT" 16 true)) ;; Bold
      (q/text header (+ x 10) (+ y 20))
      ;; Line
      (q/stroke 0)
      (q/line (+ x 5) (+ y 25) (- (+ x menu-width) 5) (+ y 25))
      ;; Items
      (q/text-font (q/create-font "Courier New" 14))
      (doseq [[idx item] (map-indexed vector items)]
        (if (= idx highlighted-idx)
          ;; Highlighted item
          (q/fill 255)
          (q/fill 0))
        (q/text item (+ x 10) (+ y 45 (* idx item-height)))))))

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
    (let [the-map (case @atoms/map-to-display
                    :player-map @map/player-map
                    :computer-map @map/computer-map
                    :actual-map @map/game-map)]
      (map/draw-map the-map)
      (draw-menu)
      (let [end-time (System/currentTimeMillis)
            draw-time (- end-time start-time)
            [text-x text-y text-w _] @atoms/text-area-dimensions]
        (q/text-font (q/create-font "Courier New" 18))
        (q/fill 255)
        (q/text (str "Map size: " @atoms/map-size " Draw time: " draw-time "ms") (+ text-x 10) (+ text-y 10))
        (when @atoms/last-clicked-cell
          (let [[cx cy] @atoms/last-clicked-cell
                [terrain-type contents] (get-in @map/game-map [cy cx])]
            (q/text (str "Last clicked: " cx ", " cy " - " terrain-type " " contents) (+ text-x 10) (+ text-y 30))))
        (q/text (str "Round: " @atoms/round-number) (- (+ text-x text-w) 100) (+ text-y 10))))))

(defn key-down [k]
  ;; Handle key down events
  (cond
    (= k :t) (swap! atoms/test-mode not)
    (= k :m) (swap! atoms/map-to-display {:player-map :computer-map
                                          :computer-map :actual-map
                                          :actual-map :player-map})
    (= k :space) (map/do-a-round)
    :else (println "Key down:" k)))

(defn key-pressed [state _]
  (let [k (q/key-as-keyword)]
    (when (nil? @atoms/last-key)
      (key-down k))
    (reset! atoms/last-key k))
  state)

(defn key-released [_ _]
  (reset! atoms/last-key nil))

(defn mouse-pressed [_ _]
  (map/mouse-down (q/mouse-x) (q/mouse-y)))

(defn on-close [_]
  (q/no-loop)
  (q/exit)                                                  ; Exit the sketch
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
               :features [:keep-on-top]
               :middleware [m/fun-mode]
               :on-close on-close
               :host "empire"))
