(ns empire.ui.core
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.init :as init]
            [empire.ui.input :as input]
            [empire.ui.rendering :as rendering]
            [quil.core :as q]
            [quil.middleware :as m]))

(defn create-fonts
  "Creates and caches font objects."
  []
  (reset! atoms/text-font (q/create-font config/text-font-name config/text-font-size))
  (reset! atoms/production-char-font (q/create-font config/cell-char-font-name config/cell-char-font-size)))

(defn compute-screen-dimensions
  "Computes pixel rendering dimensions from known map-size and fixed cell-size.
   Returns a map with :map-screen-dimensions and :text-area-dimensions."
  [cols rows cell-w cell-h]
  (let [map-display-w (* cols cell-w)
        map-display-h (* rows cell-h)
        text-h (* config/text-area-rows cell-h)
        text-x 0
        text-y (+ map-display-h config/text-area-gap)
        text-w map-display-w]
    {:map-screen-dimensions [map-display-w map-display-h]
     :text-area-dimensions [text-x text-y text-w text-h]}))

(defn calculate-screen-dimensions
  "Sets pixel rendering dimensions from known map-size and fixed cell-size."
  []
  (let [[cols rows] @atoms/map-size
        [cell-w cell-h] config/cell-size
        dims (compute-screen-dimensions cols rows cell-w cell-h)]
    (reset! atoms/map-screen-dimensions (:map-screen-dimensions dims))
    (reset! atoms/text-area-dimensions (:text-area-dimensions dims))))

(defn setup
  "Initial setup for the game state."
  []
  (create-fonts)
  (calculate-screen-dimensions)
  (when-let [seed @atoms/random-seed]
    (let [rng (java.util.Random. seed)]
      (alter-var-root #'clojure.core/rand
                      (constantly (fn
                                   ([] (.nextDouble rng))
                                   ([n] (* n (.nextDouble rng))))))
      (alter-var-root #'clojure.core/rand-int
                      (constantly (fn [n] (.nextInt rng (int n)))))))
  (let [num-cities (:number-of-cities @atoms/map-size-constants config/number-of-cities)]
    (init/make-initial-map @atoms/map-size config/smooth-count config/land-fraction num-cities config/min-city-distance))
  (q/frame-rate 30)
  {})

(defn update-state
  "Update the game state."
  [state]
  (game-loop/update-player-map)
  (game-loop/update-computer-map)
  (game-loop/advance-game-batch)
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
    (rendering/draw-message-area)
    (rendering/draw-load-menu)))

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

(defn- screen-dimensions []
  (let [screen (.getScreenSize (java.awt.Toolkit/getDefaultToolkit))]
    [(.width screen) (.height screen)]))

(declare empire)
(defn -main [& args]
  (let [seed-arg (some #(when (.startsWith ^String % "--seed=")
                          (Long/parseLong (subs % 7)))
                       args)
        non-seed-args (remove #(.startsWith ^String % "--seed=") args)
        [cols rows] (if (>= (count non-seed-args) 2)
                      [(Integer/parseInt (first non-seed-args))
                       (Integer/parseInt (second non-seed-args))]
                      config/default-map-size)
        [cell-w cell-h] config/cell-size
        text-area-h (* config/text-area-rows cell-h)
        window-w (* cols cell-w)
        window-h (+ (* rows cell-h) text-area-h config/text-area-gap)
        [screen-w screen-h] (screen-dimensions)
        max-cols (quot screen-w cell-w)
        max-rows (quot (- screen-h text-area-h config/text-area-gap) cell-h)]
    (when seed-arg
      (reset! atoms/random-seed seed-arg))
    (when (or (> window-w screen-w) (> window-h screen-h))
      (println (format "Map size [%d %d] exceeds monitor bounds (%dx%d pixels)."
                       cols rows screen-w screen-h))
      (println (format "Maximum map size for this monitor: [%d %d]"
                       max-cols max-rows))
      (System/exit 1))
    (reset! atoms/map-size [cols rows])
    (reset! atoms/map-size-constants (config/compute-size-constants cols rows))
    (if seed-arg
      (println (format "empire has begun. Map size: [%d %d], seed: %d" cols rows seed-arg))
      (println (format "empire has begun. Map size: [%d %d]" cols rows)))
    (q/defsketch empire
                 :title "Empire: Global Conquest"
                 :size [window-w window-h]
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
                 :host "empire")))
