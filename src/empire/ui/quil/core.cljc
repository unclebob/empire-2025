(ns empire.ui.quil.core
  (:require [empire.state.api :as sa]
            [empire.computer.threat-response.probe :as invasion-probe]
            [empire.config.core :as config]
            [empire.game.loop.core :as game-loop]
            [empire.game.initialization :as init]
            [empire.game-mechanics.debug.logging :as debug-logging]
            [empire.game-mechanics.debug.dump :as debug-dump]
            [empire.ui.quil.input :as quil-input]
            [empire.ui.quil.rendering.map :as render-map]
            [empire.ui.quil.rendering.messages :as render-messages]
            [empire.ui.quil.rendering.overlay :as render-overlay]
            [empire.ui.util.core :as util-core]
            [empire.ui.util.input.dispatch :as dispatch]
            [empire.ui.util.rendering.display :as display]
            [empire.game.loop.monitor :as monitor]
            [clojure.string :as str]
            [quil.core :as q]
            [quil.middleware :as m]))

(defonce ^:private debug-dump-hook-installed? (atom false))

(declare maybe-write-debug-dump-on-exit!)
(declare install-debug-dump-shutdown-hook!)

(defn create-fonts
  "Creates and caches font objects."
  []
  (sa/write-state! :text-font (q/create-font config/text-font-name config/text-font-size))
  (sa/write-state! :production-char-font (q/create-font config/cell-char-font-name config/cell-char-font-size)))

(defn- install-seeded-random!
  []
  (when-let [seed (sa/read-state :random-seed)]
    (let [rng (java.util.Random. seed)]
      (alter-var-root #'clojure.core/rand
                      (constantly (fn
                                   ([] (.nextDouble rng))
                                   ([n] (* n (.nextDouble rng))))))
      (alter-var-root #'clojure.core/rand-int
                      (constantly (fn [n] (.nextInt rng (int n))))))))

(defn- initialize-map!
  []
  (let [num-cities (:number-of-cities (sa/read-state :map-size-constants) config/number-of-cities)]
    (init/make-initial-map (sa/read-state :map-size)
                           config/smooth-count
                           config/land-fraction
                           num-cities
                           config/min-city-distance)))

(defn setup
  "Initial setup for the game state."
  []
  (create-fonts)
  (util-core/calculate-screen-dimensions)
  (install-seeded-random!)
  (initialize-map!)
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
  (maybe-write-debug-dump-on-exit!)
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

(defn- explored-percentage
  [computer-map]
  (let [cells (for [row computer-map
                    cell row]
                cell)
        total (count cells)
        explored (count (filter #(and % (not= :unexplored (:type %))) cells))]
    (if (pos? total)
      (* 100.0 (/ explored total))
      0.0)))

(defn- headless-progress-line
  [round-number computer-map major-invasion-state]
  (format "Round %d explored %.1f%% invasion %s"
          round-number
          (double (explored-percentage computer-map))
          (if (:active? major-invasion-state) "yes" "no")))

(defn- print-headless-progress!
  [round-number]
  (println (headless-progress-line round-number
                                   (sa/read-state :computer-map)
                                   (sa/read-state :major-invasion-state))))

(defn- maybe-print-final-headless-progress!
  [last-reported-round]
  (let [round-number (sa/read-state :round-number)]
    (when (> round-number last-reported-round)
      (print-headless-progress! round-number))))

(defn- finish-headless-run!
  [last-reported-round]
  (maybe-print-final-headless-progress! last-reported-round)
  (sa/write-state! :headless-mode? false)
  (sa/write-state! :headless-stop-on-major-invasion? false)
  (maybe-write-debug-dump-on-exit!))

(defn- run-headless-round!
  [mon]
  (let [recording (and mon (monitor/recording? mon))
        phase-sink (when recording (atom []))
        start-ms (monitor/now-ms)]
    (if phase-sink
      (do
        (swap! phase-sink conj (monitor/time-phase :update-player-map (game-loop/update-player-map)))
        (swap! phase-sink conj (monitor/time-phase :update-computer-map (game-loop/update-computer-map)))
        (binding [monitor/*phase-sink* phase-sink]
          (game-loop/advance-game-batch)))
      (do
        (game-loop/update-player-map)
        (game-loop/update-computer-map)
        (game-loop/advance-game-batch)))
    (let [elapsed (- (monitor/now-ms) start-ms)
          next-round (sa/read-state :round-number)]
      (cond
        recording
        (monitor/record-round mon next-round
                              {:phases @phase-sink
                               :total-ms elapsed})
        mon
        (monitor/check-round mon next-round elapsed)

        :else nil))))

(defn- run-headless-loop!
  [headless-rounds monitor-threshold]
  (loop [last-reported-round 0
         mon (when monitor-threshold (monitor/create-monitor monitor-threshold))]
    (let [round-number (sa/read-state :round-number)]
      (cond
        (and mon (monitor/done? mon))
        (do (println (monitor/format-report mon))
            (finish-headless-run! last-reported-round))

        (>= round-number headless-rounds)
        (finish-headless-run! last-reported-round)

        (sa/read-state :paused)
        (finish-headless-run! last-reported-round)

        :else
        (let [mon (run-headless-round! mon)
              next-round (sa/read-state :round-number)
              next-reported-round (if (and (> next-round last-reported-round)
                                           (zero? (mod next-round 20)))
                                    (do
                                      (print-headless-progress! next-round)
                                      next-round)
                                    last-reported-round)]
          (recur next-reported-round mon))))))

(defn- run-headless!
  [{:keys [headless-rounds monitor-threshold]}]
  (install-seeded-random!)
  (initialize-map!)
  (debug-logging/log-game-map! 0)
  (invasion-probe/clear-log!)
  (sa/write-state! :headless-mode? true)
  (sa/write-state! :headless-stop-on-major-invasion? false)
  (sa/write-state! :major-invasion-probe-hit? false)
  (run-headless-loop! headless-rounds monitor-threshold))

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
        (install-debug-dump-shutdown-hook!)
        (println (format "empire has begun. Map size: [%d %d], seed: %d" cols rows effective-seed))
        (if headless?
          (run-headless! startup)
          (start-sketch! startup))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T15:31:25.375489-05:00", :module-hash "820525299", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 14, :hash "-182943859"} {:id "defn/create-fonts", :kind "defn", :line 16, :end-line 20, :hash "1956119937"} {:id "defn/setup", :kind "defn", :line 22, :end-line 38, :hash "-470041702"} {:id "defn/update-state", :kind "defn", :line 40, :end-line 47, :hash "476676615"} {:id "defn/draw-state", :kind "defn", :line 49, :end-line 61, :hash "-52211456"} {:id "defn-/normalize-key", :kind "defn-", :line 63, :end-line 70, :hash "-1064835850"} {:id "defn-/remember-key!", :kind "defn-", :line 72, :end-line 76, :hash "-236905863"} {:id "defn/key-pressed", :kind "defn", :line 78, :end-line 84, :hash "-1203340287"} {:id "defn-/get-modifiers", :kind "defn-", :line 86, :end-line 92, :hash "808258936"} {:id "defn/mouse-pressed", :kind "defn", :line 94, :end-line 103, :hash "-624125753"} {:id "defn/mouse-dragged", :kind "defn", :line 105, :end-line 107, :hash "1314545681"} {:id "defn/mouse-released", :kind "defn", :line 109, :end-line 111, :hash "206586691"} {:id "defn/on-close", :kind "defn", :line 113, :end-line 117, :hash "1221335354"} {:id "defn-/screen-dimensions", :kind "defn-", :line 119, :end-line 121, :hash "1572516113"} {:id "form/14/declare", :kind "declare", :line 123, :end-line 123, :hash "2146718225"} {:id "defn-/print-map-size-error-and-exit!", :kind "defn-", :line 124, :end-line 131, :hash "1515084806"} {:id "defn-/parse-startup-config", :kind "defn-", :line 133, :end-line 138, :hash "-1190224892"} {:id "defn-/initialize-startup-state!", :kind "defn-", :line 140, :end-line 147, :hash "-419380522"} {:id "defn-/start-sketch!", :kind "defn-", :line 149, :end-line 165, :hash "-347673216"} {:id "defn/-main", :kind "defn", :line 167, :end-line 176, :hash "871102357"}]}
;; clj-mutate-manifest-end
(defn- maybe-write-debug-dump-on-exit!
  []
  (when (and (sa/read-state :debug-dump-on-exit?)
             (not (sa/read-state :debug-dump-written?))
             (seq (sa/read-state :map-size)))
    (let [filename (debug-dump/write-full-dump!)]
      (sa/write-state! :debug-dump-written? true)
      (println (str "Debug log written: " filename))
      filename)))

(defn- install-debug-dump-shutdown-hook!
  []
  (when (and (sa/read-state :debug-dump-on-exit?)
             (compare-and-set! debug-dump-hook-installed? false true))
    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread. ^Runnable (fn [] (maybe-write-debug-dump-on-exit!))))))
