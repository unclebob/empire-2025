(ns empire.ui.quil.core
  (:require [empire.application.bootstrap :as app-bootstrap]
            [empire.application.state-access :as sa]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.init :as init]
            [empire.movement.bootstrap :as movement-bootstrap]
            [empire.ui.quil.input :as quil-input]
            [empire.ui.quil.rendering.map :as render-map]
            [empire.ui.quil.rendering.messages :as render-messages]
            [empire.ui.quil.rendering.overlay :as render-overlay]
            [empire.ui.util.core :as util-core]
            [empire.ui.util.input.dispatch :as dispatch]
            [empire.ui.util.rendering.display :as display]
            [quil.core :as q]
            [quil.middleware :as m]))

(defn create-fonts
  "Creates and caches font objects."
  []
  (sa/write-state! :text-font (q/create-font config/text-font-name config/text-font-size))
  (sa/write-state! :production-char-font (q/create-font config/cell-char-font-name config/cell-char-font-size)))

(defn setup
  "Initial setup for the game state."
  []
  (app-bootstrap/initialize-default-services!)
  (movement-bootstrap/initialize-default-services!)
  (create-fonts)
  (util-core/calculate-screen-dimensions)
  (when-let [seed (sa/read-state :random-seed)]
    (let [rng (java.util.Random. seed)]
      (alter-var-root #'clojure.core/rand
                      (constantly (fn
                                   ([] (.nextDouble rng))
                                   ([n] (* n (.nextDouble rng))))))
      (alter-var-root #'clojure.core/rand-int
                      (constantly (fn [n] (.nextInt rng (int n)))))))
  (let [num-cities (:number-of-cities (sa/read-state :map-size-constants) config/number-of-cities)]
    (init/make-initial-map (sa/read-state :map-size) config/smooth-count config/land-fraction num-cities config/min-city-distance))
  (q/frame-rate 30)
  {})

(defn update-state
  "Update the game state."
  [state]
  (game-loop/update-player-map)
  (game-loop/update-computer-map)
  (game-loop/advance-game-batch)
  (render-overlay/update-hover-status)
  state)

(defn draw-state
  "Draw the current game state."
  [_state]
  (q/background 0)
  (let [the-map (display/resolve-display-map (sa/read-state :map-to-display)
                  (sa/read-state :player-map)
                  (sa/read-state :computer-map)
                  (sa/read-state :game-map))]
    (render-map/draw-map the-map)
    (render-map/draw-debug-selection-rectangle)
    (render-messages/draw-message-area)
    (render-overlay/draw-load-menu)))

(defn key-pressed [state _]
  (let [k (q/key-as-keyword)]
    (when (not= k :shift)
      (when (nil? (sa/read-state :last-key))
        (quil-input/key-down k))
      (sa/write-state! :last-key k)))
  state)

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
    (if (dispatch/modifier-held? mods)
      (dispatch/debug-drag-start! x y)
      (dispatch/mouse-down x y button)))
  state)

(defn mouse-dragged [state _]
  (dispatch/debug-drag-update! (q/mouse-x) (q/mouse-y))
  state)

(defn mouse-released [state _]
  (dispatch/debug-drag-end! (q/mouse-x) (q/mouse-y) (get-modifiers))
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
  (let [[screen-w screen-h] (screen-dimensions)
        {:keys [cols rows seed window-w window-h]}
        (try (util-core/parse-args args screen-w screen-h)
             (catch clojure.lang.ExceptionInfo e
               (let [{:keys [cols rows screen-w screen-h max-cols max-rows]} (ex-data e)]
                 (println (format "Map size [%d %d] exceeds monitor bounds (%dx%d pixels)."
                                  cols rows screen-w screen-h))
                 (println (format "Maximum map size for this monitor: [%d %d]"
                                  max-cols max-rows))
                 (System/exit 1))))]
    (let [effective-seed (or seed (System/currentTimeMillis))]
      (sa/write-state! :random-seed effective-seed)
      (sa/write-state! :map-size [cols rows])
      (sa/write-state! :map-size-constants (config/compute-size-constants cols rows))
      (println (format "empire has begun. Map size: [%d %d], seed: %d" cols rows effective-seed))
      (q/defsketch empire
                   :title "Empire: Global Conquest"
                   :size [window-w window-h]
                   :setup setup
                   :update update-state
                   :draw draw-state
                   :key-pressed key-pressed
                   :key-released util-core/key-released
                   :mouse-pressed mouse-pressed
                   :mouse-dragged mouse-dragged
                   :mouse-released mouse-released
                   :features []
                   :middleware [m/fun-mode]
                   :on-close on-close
                   :host "empire"))))
