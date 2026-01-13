(ns empire.core
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.init :as init]
            [empire.input :as input]
            [empire.map-utils :as map-utils]
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

(defn format-hover-status
  "Formats a status string for the cell under the mouse."
  [cell coords]
  (let [unit (:contents cell)
        city? (= (:type cell) :city)]
    (cond
      unit
      (let [type-name (name (:type unit))
            hits (:hits unit)
            max-hits (config/item-hits (:type unit))
            mode (:mode unit)
            fuel (when (= (:type unit) :fighter) (:fuel unit))
            cargo (cond
                    (= (:type unit) :transport) (:army-count unit 0)
                    (= (:type unit) :carrier) (:fighter-count unit 0)
                    :else nil)
            orders (cond
                     (:marching-orders unit) "march"
                     (:flight-path unit) "flight"
                     :else nil)]
        (str type-name
             " [" hits "/" max-hits "]"
             (when fuel (str " fuel:" fuel))
             (when cargo (str " cargo:" cargo))
             (when orders (str " " orders))
             " " (name mode)))

      city?
      (let [status (:city-status cell)
            production (get @atoms/production coords)
            fighters (:fighter-count cell 0)
            sleeping (:sleeping-fighters cell 0)
            marching (:marching-orders cell)
            flight (:flight-path cell)]
        (str "city:" (name status)
             (when (and (= status :player) production)
               (str " producing:" (if (= production :none) "none" (name (:item production)))))
             (when (pos? fighters) (str " fighters:" fighters))
             (when (pos? sleeping) (str " sleeping:" sleeping))
             (when marching " march")
             (when flight " flight")))

      :else nil)))

(defn set-confirmation-message
  "Sets a confirmation message on line 2 that persists for the specified milliseconds."
  [msg ms]
  (reset! atoms/line2-message msg)
  (reset! atoms/confirmation-until (+ (System/currentTimeMillis) ms)))

(defn update-hover-status
  "Updates line2-message based on mouse position, unless a confirmation message is active."
  []
  (when (>= (System/currentTimeMillis) @atoms/confirmation-until)
    (let [x (q/mouse-x)
          y (q/mouse-y)]
      (if (map-utils/on-map? x y)
        (let [[cx cy] (map-utils/determine-cell-coordinates x y)
              coords [cx cy]
              cell (get-in @atoms/player-map coords)
              status (format-hover-status cell coords)]
          (reset! atoms/line2-message (or status "")))
        (reset! atoms/line2-message "")))))

(defn update-state
  "Update the game state."
  [state]
  (game-loop/update-map)
  (update-hover-status)
  state)

(defn draw-line-1
  "Draws the main message on line 1."
  [text-x text-y]
  (when (seq @atoms/message)
    (q/text @atoms/message (+ text-x 10) (+ text-y 10))))

(defn draw-line-2
  "Draws content on line 2."
  [text-x text-y]
  (when (seq @atoms/line2-message)
    (q/text @atoms/line2-message (+ text-x 10) (+ text-y 30))))

(defn draw-line-3
  "Draws the flashing red message on line 3."
  [text-x text-y]
  (if (>= (System/currentTimeMillis) @atoms/line3-until)
    (reset! atoms/line3-message "")
    (when (and (seq @atoms/line3-message)
               (even? (quot (System/currentTimeMillis) 500)))
      (q/fill 255 0 0)
      (q/text @atoms/line3-message (+ text-x 10) (+ text-y 50))
      (q/fill 255))))

(defn draw-status
  "Draws the status area on the right (3 lines, 20 chars wide)."
  [text-x text-y text-w]
  (let [char-width (q/text-width "M")
        status-width (* 20 char-width)
        status-x (- (+ text-x text-w) status-width)
        dest @atoms/destination
        dest-str (if dest (str "Dest: " (first dest) "," (second dest)) "")]
    (q/text (str "Round: " @atoms/round-number) status-x (+ text-y 10))
    (q/text dest-str status-x (+ text-y 30))))

(defn draw-message-area
  "Draws the message area including separator line and messages."
  []
  (let [[text-x text-y text-w _] @atoms/text-area-dimensions]
    (q/stroke 255)
    (q/line text-x (- text-y 4) (+ text-x text-w) (- text-y 4))
    (q/text-font @atoms/text-font)
    (q/fill 255)
    (draw-line-1 text-x text-y)
    (draw-line-2 text-x text-y)
    (draw-line-3 text-x text-y)
    (draw-status text-x text-y text-w)))

(defn draw-state
  "Draw the current game state."
  [_state]
  (q/background 0)
  (let [the-map (case @atoms/map-to-display
                  :player-map @atoms/player-map
                  :computer-map @atoms/computer-map
                  :actual-map @atoms/game-map)]
    (rendering/draw-map the-map)
    (draw-message-area)))

(defn add-unit-at-mouse [unit-type]
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (let [[cx cy] (map-utils/determine-cell-coordinates x y)
            cell (get-in @atoms/game-map [cx cy])
            unit {:type unit-type
                  :hits (config/item-hits unit-type)
                  :mode :awake
                  :owner :player}
            unit (if (= unit-type :fighter)
                   (assoc unit :fuel config/fighter-fuel)
                   unit)]
        (when-not (:contents cell)
          (swap! atoms/game-map assoc-in [cx cy :contents] unit))))))

(defn wake-at-mouse []
  "Wakes a city (removes production so it needs attention) or a sleeping unit.
   Also wakes sleeping fighters in city airports.
   Returns true if something was woken, nil otherwise."
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (let [[cx cy] (map-utils/determine-cell-coordinates x y)
            cell (get-in @atoms/game-map [cx cy])
            contents (:contents cell)]
        (cond
          ;; Wake a friendly city - remove production and wake sleeping fighters
          ;; Also skip any unit at this location from attention queue
          (and (= (:type cell) :city)
               (= (:city-status cell) :player))
          (let [sleeping (get cell :sleeping-fighters 0)
                awake (get cell :awake-fighters 0)
                coords [cx cy]]
            (swap! atoms/production dissoc coords)
            (when (pos? sleeping)
              (swap! atoms/game-map update-in coords assoc
                     :sleeping-fighters 0
                     :awake-fighters (+ awake sleeping)))
            ;; Skip unit at this city if it's currently needing attention
            (when (= coords (first @atoms/cells-needing-attention))
              (swap! atoms/player-items rest)
              (game-loop/item-processed))
            true)

          ;; Wake a sleeping/sentry/explore friendly unit (not already awake)
          (and contents
               (= (:owner contents) :player)
               (not= (:mode contents) :awake))
          (do (swap! atoms/game-map assoc-in [cx cy :contents :mode] :awake)
              true)

          :else nil)))))

(defn set-destination-at-mouse []
  "Sets the destination to the cell under the mouse cursor."
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (let [[cx cy] (map-utils/determine-cell-coordinates x y)]
        (reset! atoms/destination [cx cy])
        true))))

(defn set-marching-orders-at-mouse []
  "Sets marching orders on a player city or transport under the mouse to the current destination."
  (when-let [dest @atoms/destination]
    (let [x (q/mouse-x)
          y (q/mouse-y)]
      (when (map-utils/on-map? x y)
        (let [[cx cy] (map-utils/determine-cell-coordinates x y)
              cell (get-in @atoms/game-map [cx cy])
              contents (:contents cell)]
          (cond
            (and (= (:type cell) :city)
                 (= (:city-status cell) :player))
            (do (swap! atoms/game-map assoc-in [cx cy :marching-orders] dest)
                (reset! atoms/destination nil)
                (set-confirmation-message (str "Marching orders set to " (first dest) "," (second dest)) 2000)
                true)

            (and (= (:type contents) :transport)
                 (= (:owner contents) :player))
            (do (swap! atoms/game-map assoc-in [cx cy :contents :marching-orders] dest)
                (reset! atoms/destination nil)
                (set-confirmation-message (str "Marching orders set to " (first dest) "," (second dest)) 2000)
                true)

            :else nil))))))

(defn set-flight-path-at-mouse []
  "Sets flight path on a player city or carrier under the mouse to the current destination."
  (when-let [dest @atoms/destination]
    (let [x (q/mouse-x)
          y (q/mouse-y)]
      (when (map-utils/on-map? x y)
        (let [[cx cy] (map-utils/determine-cell-coordinates x y)
              cell (get-in @atoms/game-map [cx cy])
              contents (:contents cell)]
          (cond
            (and (= (:type cell) :city)
                 (= (:city-status cell) :player))
            (do (swap! atoms/game-map assoc-in [cx cy :flight-path] dest)
                (reset! atoms/destination nil)
                (set-confirmation-message (str "Flight path set to " (first dest) "," (second dest)) 2000)
                true)

            (and (= (:type contents) :carrier)
                 (= (:owner contents) :player))
            (do (swap! atoms/game-map assoc-in [cx cy :contents :flight-path] dest)
                (reset! atoms/destination nil)
                (set-confirmation-message (str "Flight path set to " (first dest) "," (second dest)) 2000)
                true)

            :else nil))))))

(defn set-city-marching-orders-by-direction [k]
  "Sets marching orders on a player city under the mouse to the map edge in the given direction."
  (when-let [direction (config/key->direction k)]
    (let [x (q/mouse-x)
          y (q/mouse-y)]
      (when (map-utils/on-map? x y)
        (let [[cx cy] (map-utils/determine-cell-coordinates x y)
              cell (get-in @atoms/game-map [cx cy])]
          (when (and (= (:type cell) :city)
                     (= (:city-status cell) :player))
            (let [[dx dy] direction
                  cols (count @atoms/game-map)
                  rows (count (first @atoms/game-map))
                  target (loop [tx cx ty cy]
                           (let [nx (+ tx dx)
                                 ny (+ ty dy)]
                             (if (and (>= nx 0) (< nx cols) (>= ny 0) (< ny rows))
                               (recur nx ny)
                               [tx ty])))]
              (swap! atoms/game-map assoc-in [cx cy :marching-orders] target)
              (set-confirmation-message (str "Marching orders set to " (first target) "," (second target)) 2000)
              true)))))))

(defn key-down [k]
  ;; Handle key down events
  (if @atoms/backtick-pressed
    (do
      (reset! atoms/backtick-pressed false)
      (case k
        :c (add-unit-at-mouse :carrier)
        :t (add-unit-at-mouse :transport)
        :a (add-unit-at-mouse :army)
        :f (add-unit-at-mouse :fighter)
        nil))
    (cond
      (= k (keyword "`")) (reset! atoms/backtick-pressed true)
      (= k :+) (swap! atoms/map-to-display {:player-map :computer-map
                                            :computer-map :actual-map
                                            :actual-map :player-map})
      (= k (keyword ".")) (set-destination-at-mouse)
      (and (= k :m) (set-marching-orders-at-mouse)) nil
      (and (= k :f) @atoms/destination (set-flight-path-at-mouse)) nil
      (and (= k :w) (wake-at-mouse)) nil
      (set-city-marching-orders-by-direction k) nil
      (input/handle-key k) nil
      :else nil)))

(defn key-pressed [state _]
  (let [k (q/key-as-keyword)]
    (when (not= k :shift)
      (when (nil? @atoms/last-key)
        (key-down k))
      (reset! atoms/last-key k)))
  state)

(defn key-released [_ _]
  (reset! atoms/last-key nil))

(defn mouse-pressed [_ _]
  (input/mouse-down (q/mouse-x) (q/mouse-y) (q/mouse-button)))

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
               :features []
               :middleware [m/fun-mode]
               :on-close on-close
               :host "empire"))
