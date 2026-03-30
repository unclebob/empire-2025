(ns empire.ui.quil.core
  (:require [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.game.loop.core :as game-loop]
            [empire.ui.quil.headless :as headless]
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
  (headless/install-seeded-random!)
  (headless/initialize-map!)
  (q/frame-rate 30)
  {})

(declare sync-window-focus!)

(defn update-state
  "Update the game state."
  [state]
  (sync-window-focus!)
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

(defn- normalize-key
  [raw-k key-code]
  (cond
    (= key-code java.awt.event.KeyEvent/VK_DELETE) :delete
    (= key-code java.awt.event.KeyEvent/VK_BACK_SPACE) :backspace
    (= key-code java.awt.event.KeyEvent/VK_ENTER) :enter
    (= key-code java.awt.event.KeyEvent/VK_ESCAPE) :escape
    :else raw-k))

(defn- remember-key!
  [k]
  (when (nil? (sa/read-state :last-key))
    (quil-input/key-down k))
  (sa/write-state! :last-key k))

(defn key-pressed [state _]
  (let [raw-k (q/key-as-keyword)
        key-code (q/key-code)
        k (normalize-key raw-k key-code)]
    (when (not= k :shift)
      (remember-key! k)))
  state)

(defn- get-modifiers
  "Returns a map of modifier key states."
  []
  (let [mods (q/key-modifiers)]
    {:ctrl (:control mods)
     :meta (:meta mods)
     :alt (:alt mods)}))

(defn- sync-window-focus!
  []
  (let [focused? (boolean (q/focused))
        previous-focus? (boolean (sa/read-state :window-focused?))]
    (when (not= focused? previous-focus?)
      (sa/write-state! :window-focused? focused?)
      (sa/write-state! :last-key nil)
      (sa/write-state! :backtick-pressed false)
      (if focused?
        (do
          (sa/write-state! :refocus-click-armed? true)
          (sa/write-state! :refocus-click-saw-press? false))
        (do
          (sa/write-state! :refocus-click-armed? false)
          (sa/write-state! :refocus-click-saw-press? false))))))

(defn mouse-pressed [state _]
  (let [x (q/mouse-x)
        y (q/mouse-y)
        button (q/mouse-button)
        mods (get-modifiers)]
    (if (dispatch/modifier-held? mods)
      (dispatch/debug-drag-start! x y)
      (do
        (when (sa/read-state :refocus-click-armed?)
          (sa/write-state! :refocus-click-saw-press? true)
          (sa/write-state! :refocus-click-armed? false))
        (dispatch/mouse-down x y button))))
  state)

(defn mouse-dragged [state _]
  (dispatch/debug-drag-update! (q/mouse-x) (q/mouse-y))
  state)

(defn mouse-released [state _]
  (let [x (q/mouse-x)
        y (q/mouse-y)
        button (q/mouse-button)
        mods (get-modifiers)]
    (when (and (sa/read-state :refocus-click-armed?)
               (not (sa/read-state :refocus-click-saw-press?))
               (not (dispatch/modifier-held? mods))
               (= button :left))
      (dispatch/mouse-down x y button))
    (sa/write-state! :refocus-click-armed? false)
    (sa/write-state! :refocus-click-saw-press? false))
  (dispatch/debug-drag-end! (q/mouse-x) (q/mouse-y) (get-modifiers))
  state)

(defn on-close [_]
  (headless/maybe-write-debug-dump-on-exit!)
  (q/no-loop)
  (q/exit)
  (println "Empire closed.")
  (System/exit 0))

(defn- screen-dimensions []
  (let [screen (.getScreenSize (java.awt.Toolkit/getDefaultToolkit))]
    [(.width screen) (.height screen)]))

(declare empire)
(defn- print-map-size-error-and-exit!
  [e]
  (let [{:keys [cols rows screen-w screen-h max-cols max-rows]} (ex-data e)]
    (println (format "Map size [%d %d] exceeds monitor bounds (%dx%d pixels)."
                     cols rows screen-w screen-h))
    (println (format "Maximum map size for this monitor: [%d %d]"
                     max-cols max-rows))
    (System/exit 1)))

(defn- parse-startup-config
  [args screen-w screen-h]
  (try
    (util-core/parse-args args screen-w screen-h)
    (catch clojure.lang.ExceptionInfo e
      (print-map-size-error-and-exit! e))))

(defn- initialize-startup-state!
  [{:keys [cols rows handicap log-enabled debug-dump-enabled production-limits]} effective-seed]
  (sa/write-state! :random-seed effective-seed)
  (sa/write-state! :map-size [cols rows])
  (sa/write-state! :map-size-constants (config/compute-size-constants cols rows))
  (sa/write-state! :handicap-rounds-remaining handicap)
  (sa/write-state! :handicap-display-rounds (when (pos? handicap) handicap))
  (sa/write-state! :computer-production-limits production-limits)
  (sa/write-state! :computer-unit-log-file
                   (when log-enabled
                     (util-core/unit-log-filename)))
  (sa/write-state! :debug-dump-on-exit? (boolean debug-dump-enabled))
  (sa/write-state! :debug-dump-written? false)
  (sa/write-state! :headless-mode? false)
  (sa/write-state! :headless-stop-on-major-invasion? false)
  (sa/write-state! :major-invasion-probe-hit? false))

(defn- start-sketch!
  [{:keys [window-w window-h]}]
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
               :host "empire"))

(defn -main [& args]
  (if (util-core/help-requested? args)
    (println (util-core/usage-text))
    (let [headless? (util-core/headless-requested? args)
          {:keys [cols rows seed] :as startup}
          (if headless?
            (parse-startup-config args nil nil)
            (let [[screen-w screen-h] (screen-dimensions)]
              (parse-startup-config args screen-w screen-h)))]
      (let [effective-seed (or seed (System/currentTimeMillis))]
        (initialize-startup-state! startup effective-seed)
        (headless/install-debug-dump-shutdown-hook!)
        (println (format "empire has begun. Map size: [%d %d], seed: %d" cols rows effective-seed))
        (if headless?
          (headless/run-headless! startup)
          (start-sketch! startup))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T11:56:37.540884-05:00", :module-hash "-42289857", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 14, :hash "494466759"} {:id "defn/create-fonts", :kind "defn", :line 16, :end-line 20, :hash "1956119937"} {:id "defn/setup", :kind "defn", :line 22, :end-line 30, :hash "-578848240"} {:id "defn/update-state", :kind "defn", :line 32, :end-line 39, :hash "476676615"} {:id "defn/draw-state", :kind "defn", :line 41, :end-line 53, :hash "-52211456"} {:id "defn-/normalize-key", :kind "defn-", :line 55, :end-line 62, :hash "-1064835850"} {:id "defn-/remember-key!", :kind "defn-", :line 64, :end-line 68, :hash "-236905863"} {:id "defn/key-pressed", :kind "defn", :line 70, :end-line 76, :hash "-1203340287"} {:id "defn-/get-modifiers", :kind "defn-", :line 78, :end-line 84, :hash "808258936"} {:id "defn/mouse-pressed", :kind "defn", :line 86, :end-line 94, :hash "-624125753"} {:id "defn/mouse-dragged", :kind "defn", :line 96, :end-line 98, :hash "1314545681"} {:id "defn/mouse-released", :kind "defn", :line 100, :end-line 102, :hash "206586691"} {:id "defn/on-close", :kind "defn", :line 104, :end-line 109, :hash "852166893"} {:id "defn-/screen-dimensions", :kind "defn-", :line 111, :end-line 113, :hash "1572516113"} {:id "form/14/declare", :kind "declare", :line 115, :end-line 115, :hash "2146718225"} {:id "defn-/print-map-size-error-and-exit!", :kind "defn-", :line 116, :end-line 123, :hash "1515084806"} {:id "defn-/parse-startup-config", :kind "defn-", :line 125, :end-line 130, :hash "-1190224892"} {:id "defn-/initialize-startup-state!", :kind "defn-", :line 132, :end-line 147, :hash "1103297715"} {:id "defn-/start-sketch!", :kind "defn-", :line 149, :end-line 165, :hash "-347673216"} {:id "defn/-main", :kind "defn", :line 167, :end-line 182, :hash "-1946920868"}]}
;; clj-mutate-manifest-end
