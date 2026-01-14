(ns empire.rendering
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.map-utils :as map-utils]
            [empire.unit-container :as uc]
            [quil.core :as q]))

(defn- blink? [period-ms]
  (even? (quot (System/currentTimeMillis) period-ms)))

(defn color-of [cell]
  (let
    [terrain-type (:type cell)
     cell-color (if (= terrain-type :city)
                  (case (:city-status cell)
                    :player :player-city
                    :computer :computer-city
                    :free :free-city)
                  terrain-type)]
    (config/cell-colors cell-color)))

(defn draw-production-indicators
  "Draws production indicator for a city cell."
  [i j cell cell-w cell-h]
  (when (= :city (:type cell))
    (when-let [prod (@atoms/production [j i])]
      (when (and (map? prod) (:item prod))
        ;; Draw production progress thermometer
        (let [total (config/item-cost (:item prod))
              remaining (:remaining-rounds prod)
              progress (/ (- total remaining) (double total))
              base-color (color-of cell)
              dark-color (mapv #(* % 0.5) base-color)]
          (when (and (> progress 0) (> remaining 0))
            (let [[r g b] dark-color]
              (q/fill r g b 128))                           ; semi-transparent darker version
            (let [bar-height (* cell-h progress)]
              (q/rect (* j cell-w) (+ (* i cell-h) (- cell-h bar-height)) cell-w bar-height))))
        ;; Draw production character
        (let [[r g b] config/production-color]
          (q/fill r g b))
        (q/text-font @atoms/production-char-font)
        (q/text (config/item-chars (:item prod)) (+ (* j cell-w) 2) (+ (* i cell-h) 12))))))

(defn- draw-unit
  "Draws a unit on the map cell, handling attention blinking for contained units."
  [col row cell cell-w cell-h]
  (let [contents (:contents cell)
        has-awake-airport-fighter? (uc/has-awake? cell :awake-fighters)
        has-any-airport-fighter? (pos? (uc/get-count cell :fighter-count))
        has-awake-carrier-fighter? (and (= (:type contents) :carrier)
                                        (uc/has-awake? contents :awake-fighters))
        has-awake-army-aboard? (and (= (:type contents) :transport)
                                    (uc/has-awake? contents :awake-armies))
        attention-coords @atoms/cells-needing-attention
        is-attention-cell? (and (seq attention-coords) (= [col row] (first attention-coords)))
        show-contained-unit? (and is-attention-cell?
                                  (or has-awake-airport-fighter?
                                      has-awake-carrier-fighter?
                                      has-awake-army-aboard?)
                                  (blink? 250))
        display-unit (cond
                       ;; Alternate: show contained unit (even frames)
                       (and show-contained-unit? has-awake-airport-fighter?)
                       {:type :fighter :mode :awake}
                       (and show-contained-unit? has-awake-carrier-fighter?)
                       {:type :fighter :mode :awake}
                       (and show-contained-unit? has-awake-army-aboard?)
                       {:type :army :mode :awake}
                       ;; Alternate: show container (odd frames) - airport shows nothing
                       (and is-attention-cell? has-awake-airport-fighter? (not show-contained-unit?))
                       nil
                       ;; Normal display logic
                       (and contents (= (:mode contents) :awake)) contents
                       has-awake-airport-fighter? {:type :fighter :mode :awake}
                       contents contents
                       has-any-airport-fighter? {:type :fighter :mode :sentry}
                       :else nil)]
    (when display-unit
      (let [item (:type display-unit)
            [r g b] (case (:mode display-unit)
                      :awake config/awake-unit-color
                      :sentry config/sentry-unit-color
                      :explore config/explore-unit-color
                      config/sleeping-unit-color)]
        (q/fill r g b)
        (q/text-font @atoms/production-char-font)
        (q/text (config/item-chars item) (+ (* col cell-w) 2) (+ (* row cell-h) 12))))))

(defn draw-map
  "Draws the map on the screen."
  [the-map]
  (let [[map-w map-h] @atoms/map-screen-dimensions
        cols (count the-map)
        rows (count (first the-map))
        cell-w (/ map-w cols)
        cell-h (/ map-h rows)
        attention-coords @atoms/cells-needing-attention
        production @atoms/production
        ;; First pass: collect cells grouped by color
        cells-by-color (reduce
                        (fn [acc [col row]]
                          (let [cell (get-in the-map [col row])]
                            (if (= :unexplored (:type cell))
                              acc
                              (let [color (color-of cell)
                                    current [col row]
                                    should-flash-black (and (seq attention-coords) (= current (first attention-coords)))
                                    completed? (and (= (:type cell) :city) (not= :free (:city-status cell))
                                                    (let [prod (production [col row])]
                                                      (and (map? prod) (= (:remaining-rounds prod) 0))))
                                    blink-white? (and completed? (blink? 500))
                                    blink-black? (and should-flash-black (blink? 125))
                                    final-color (cond blink-black? [0 0 0]
                                                      blink-white? [255 255 255]
                                                      :else color)]
                                (update acc final-color conj {:col col :row row :cell cell})))))
                        {}
                        (for [col (range cols) row (range rows)] [col row]))]
    (q/no-stroke)
    ;; Draw all rects batched by color
    (doseq [[color cells] cells-by-color]
      (let [[r g b] color]
        (q/fill r g b)
        (doseq [{:keys [col row]} cells]
          (q/rect (* col cell-w) (* row cell-h) cell-w cell-h))))
    ;; Draw grid lines
    (q/stroke 0)
    (doseq [col (range (inc cols))]
      (q/line (* col cell-w) 0 (* col cell-w) map-h))
    (doseq [row (range (inc rows))]
      (q/line 0 (* row cell-h) map-w (* row cell-h)))
    ;; Draw production indicators and units
    (doseq [[_ cells] cells-by-color]
      (doseq [{:keys [col row cell]} cells]
        (draw-production-indicators row col cell cell-w cell-h)
        (draw-unit col row cell cell-w cell-h)))))

(defn- format-unit-status
  "Formats status string for a unit."
  [unit]
  (let [type-name (name (:type unit))
        hits (:hits unit)
        max-hits (config/item-hits (:type unit))
        fuel (when (= (:type unit) :fighter) (:fuel unit))
        cargo (case (:type unit)
                :transport (:army-count unit 0)
                :carrier (:fighter-count unit 0)
                nil)
        orders (cond
                 (:marching-orders unit) "march"
                 (:flight-path unit) "flight"
                 :else nil)]
    (str type-name
         " [" hits "/" max-hits "]"
         (when fuel (str " fuel:" fuel))
         (when cargo (str " cargo:" cargo))
         (when orders (str " " orders))
         " " (name (:mode unit)))))

(defn- format-city-status
  "Formats status string for a city."
  [cell coords]
  (let [status (:city-status cell)
        production (get @atoms/production coords)
        fighters (:fighter-count cell 0)
        sleeping (:sleeping-fighters cell 0)]
    (str "city:" (name status)
         (when (and (= status :player) production)
           (str " producing:" (if (= production :none) "none" (name (:item production)))))
         (when (pos? fighters) (str " fighters:" fighters))
         (when (pos? sleeping) (str " sleeping:" sleeping))
         (when (:marching-orders cell) " march")
         (when (:flight-path cell) " flight"))))

(defn format-hover-status
  "Formats a status string for the cell under the mouse."
  [cell coords]
  (cond
    (:contents cell) (format-unit-status (:contents cell))
    (= (:type cell) :city) (format-city-status cell coords)
    :else nil))

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

(defn- draw-line-1
  "Draws the main message on line 1."
  [text-x text-y]
  (when (seq @atoms/message)
    (q/text @atoms/message (+ text-x 10) (+ text-y 10))))

(defn- draw-line-2
  "Draws content on line 2."
  [text-x text-y]
  (when (seq @atoms/line2-message)
    (q/text @atoms/line2-message (+ text-x 10) (+ text-y 30))))

(defn- draw-line-3
  "Draws the flashing red message on line 3."
  [text-x text-y]
  (if (>= (System/currentTimeMillis) @atoms/line3-until)
    (reset! atoms/line3-message "")
    (when (and (seq @atoms/line3-message)
               (blink? 500))
      (q/fill 255 0 0)
      (q/text @atoms/line3-message (+ text-x 10) (+ text-y 50))
      (q/fill 255))))

(defn- draw-status
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
