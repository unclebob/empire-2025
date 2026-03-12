(ns empire.ui.quil.core
  (:require [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.game.loop.core :as game-loop]
            [empire.game.initialization :as init]
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
    (render-overlay/draw-load-menu)
    (render-overlay/draw-save-menu)))

(defn key-pressed [state _]
  (let [raw-k (q/key-as-keyword)
        key-code (q/key-code)
        k (cond
            (= key-code java.awt.event.KeyEvent/VK_DELETE) :delete
            (= key-code java.awt.event.KeyEvent/VK_BACK_SPACE) :backspace
            (= key-code java.awt.event.KeyEvent/VK_ENTER) :enter
            (= key-code java.awt.event.KeyEvent/VK_ESCAPE) :escape
            :else raw-k)]
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:02:59.65257-05:00", :module-hash "133900371", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 14, :hash "-182943859"} {:id "defn/create-fonts", :kind "defn", :line 16, :end-line 20, :hash "1956119937"} {:id "defn/setup", :kind "defn", :line 22, :end-line 38, :hash "-470041702"} {:id "defn/update-state", :kind "defn", :line 40, :end-line 47, :hash "476676615"} {:id "defn/draw-state", :kind "defn", :line 49, :end-line 61, :hash "-52211456"} {:id "defn/key-pressed", :kind "defn", :line 63, :end-line 76, :hash "1427502277"} {:id "defn-/get-modifiers", :kind "defn-", :line 78, :end-line 84, :hash "808258936"} {:id "defn/mouse-pressed", :kind "defn", :line 86, :end-line 95, :hash "-624125753"} {:id "defn/mouse-dragged", :kind "defn", :line 97, :end-line 99, :hash "1314545681"} {:id "defn/mouse-released", :kind "defn", :line 101, :end-line 103, :hash "206586691"} {:id "defn/on-close", :kind "defn", :line 105, :end-line 109, :hash "1221335354"} {:id "defn-/screen-dimensions", :kind "defn-", :line 111, :end-line 113, :hash "1572516113"} {:id "form/12/declare", :kind "declare", :line 115, :end-line 115, :hash "2146718225"} {:id "defn/-main", :kind "defn", :line 116, :end-line 146, :hash "-258464563"}]}
;; clj-mutate-manifest-end
