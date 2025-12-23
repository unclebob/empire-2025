(ns empire.core
  (:require [empire.config :as config]
            [empire.init :as init]
            [empire.map :as map]
            [quil.core :as q]
            [quil.middleware :as m]))

(defn setup
  "Initial setup for the game state."
  []
  (init/make-initial-map config/map-size config/smooth-count config/land-fraction config/number-of-cities config/min-city-distance)
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
    (let [screen-w (q/width)
          screen-h (q/height)
          the-map (if @config/test-mode @map/game-map @map/visible-map)]
      (map/draw-map screen-w screen-h the-map))
    (q/fill 255)
    (q/text "Empire: Global Conquest" 10 20)
    (let [end-time (System/currentTimeMillis)]
      (println "Draw time:" (- end-time start-time) "ms"))))

(defn key-down [k]
  ;; Handle key down events
  (cond
    (= k :t) (swap! config/test-mode not)
    :else (println "Key down:" k)))

(defn key-pressed [state _]
  (let [k (q/key-as-keyword)]
    (when (nil? @config/last-key)
      (key-down k))
    (reset! config/last-key k))
  state)

(defn key-released [_ _]
  (reset! config/last-key nil))

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
               :features [:keep-on-top]
               :middleware [m/fun-mode]
               :on-close on-close
               :host "empire"))
