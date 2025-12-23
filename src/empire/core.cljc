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
  {})

(defn update-state
  "Update the game state."
  [state]
  state)

(defn draw-state
  "Draw the current game state."
  [_state]
  (q/background 0)
  (let [screen-w (q/width)
        screen-h (q/height)
        the-map @map/game-map
        height (count the-map)
        width (count (first the-map))
        cell-w (/ screen-w width)
        cell-h (/ screen-h height)]
    (doseq [i (range height)
            j (range width)]
      (let [[terrain-type contents] (get-in the-map [i j])
            color (cond
                    (= contents :my-city) [0 255 0]         ; green for player's city
                    (= contents :his-city) [255 0 0]        ; red for opponent's city
                    (= contents :free-city) [255 255 255]   ; white for free cities
                    (= terrain-type :land) [139 69 19]      ; brown for land
                    (= terrain-type :sea) [25 25 112])]     ; midnight blue for water
        (apply q/fill color)
        (q/rect (* j cell-w) (* i cell-h) cell-w cell-h)))
    (q/fill 255)
    (q/text "Empire: Global Conquest" 10 20)))

(defn key-down [k]
  ;; Placeholder for key down handling
  (println "Key down:" k))

(defn key-pressed [state _]
  (let [k (q/key-as-keyword)]
    (when (nil? @config/key)
      (key-down k))
    (reset! config/key k))
  state)

(defn key-released [_ _]
  (reset! config/key nil))

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
