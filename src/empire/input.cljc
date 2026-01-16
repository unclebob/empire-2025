(ns empire.input
  (:require [empire.atoms :as atoms]
            [empire.attention :as attention]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.map-utils :as map-utils]
            [empire.movement :as movement]
            [empire.production :as production]
            [empire.unit-container :as uc]
            [empire.waypoint :as waypoint]
            [quil.core :as q]))

(defn handle-unit-click
  "Handles interaction with an attention-needing unit."
  [clicked-coords attention-coords]
  (let [attn-coords (first attention-coords)
        attn-cell (get-in @atoms/game-map attn-coords)
        active-unit (movement/get-active-unit attn-cell)
        unit-type (:type active-unit)
        is-airport-fighter? (movement/is-fighter-from-airport? active-unit)
        is-army-aboard? (movement/is-army-aboard-transport? active-unit)
        target-cell (get-in @atoms/game-map clicked-coords)
        [ax ay] attn-coords
        [cx cy] clicked-coords
        adjacent? (and (<= (abs (- ax cx)) 1) (<= (abs (- ay cy)) 1))]
    (cond
      is-airport-fighter?
      (let [fighter-pos (movement/launch-fighter-from-airport attn-coords clicked-coords)]
        (reset! atoms/waiting-for-input false)
        (reset! atoms/message "")
        (reset! atoms/cells-needing-attention [])
        (swap! atoms/player-items #(cons fighter-pos (rest %))))

      (and is-army-aboard? adjacent? (= (:type target-cell) :land) (not (:contents target-cell)))
      (do
        (movement/disembark-army-from-transport attn-coords clicked-coords)
        (game-loop/item-processed))

      is-army-aboard?
      nil ;; Awake army aboard - ignore invalid disembark targets

      (and (= :army unit-type) adjacent? (combat/hostile-city? clicked-coords))
      (combat/attempt-conquest attn-coords clicked-coords)

      (and (= :fighter unit-type) adjacent? (combat/hostile-city? clicked-coords))
      (combat/attempt-fighter-overfly attn-coords clicked-coords)

      :else
      (movement/set-unit-movement attn-coords clicked-coords))
    (game-loop/item-processed)))

(defn handle-cell-click
  "Handles clicking on a map cell, prioritizing attention-needing items."
  [cell-x cell-y]
  (let [attention-coords @atoms/cells-needing-attention
        clicked-coords [cell-x cell-y]]
    (when (attention/is-unit-needing-attention? attention-coords)
      (handle-unit-click clicked-coords attention-coords))))

(defn mouse-down
  "Handles mouse click events."
  [x y button]
  (when (and (= button :left) (map-utils/on-map? x y))
    (let [[cell-x cell-y] (map-utils/determine-cell-coordinates x y)]
      (reset! atoms/last-clicked-cell [cell-x cell-y])
      (handle-cell-click cell-x cell-y))))

(defn- handle-city-production-key [k coords cell]
  (when (and (= (:type cell) :city)
             (= (:city-status cell) :player)
             (not (movement/get-active-unit cell)))
    (cond
      ;; 'x' sets production to nothing
      (= k :x)
      (do
        (swap! atoms/production assoc coords :none)
        (game-loop/item-processed)
        true)

      ;; Standard production keys
      (config/key->production-item k)
      (let [item (config/key->production-item k)
            [x y] coords
            coastal? (map-utils/on-coast? x y)
            naval? (config/naval-unit? item)]
        (if (and naval? (not coastal?))
          (do
            (atoms/set-line3-message (format "Must be coastal city to produce %s." (name item)) 3000)
            true)
          (do
            (production/set-city-production coords item)
            (game-loop/item-processed)
            true))))))

(defn- calculate-extended-target [coords [dx dy]]
  (let [height (count @atoms/game-map)
        width (count (first @atoms/game-map))
        [x y] coords]
    (loop [tx x ty y]
      (let [nx (+ tx dx)
            ny (+ ty dy)]
        (if (and (>= nx 0) (< nx height) (>= ny 0) (< ny width))
          (recur nx ny)
          [tx ty])))))


(defn- launch-fighter-and-update [launch-fn coords target]
  (let [fighter-pos (launch-fn coords target)]
    (reset! atoms/waiting-for-input false)
    (reset! atoms/message "")
    (reset! atoms/cells-needing-attention [])
    (swap! atoms/player-items #(cons fighter-pos (rest %)))
    true))

(defn- handle-army-aboard-movement [coords adjacent-target target extended? target-cell]
  (let [valid-land? (and (= (:type target-cell) :land) (not (:contents target-cell)))]
    (cond
      (and (not extended?) valid-land?)
      (do (movement/disembark-army-from-transport coords adjacent-target)
          (game-loop/item-processed)
          true)

      (and extended? valid-land?)
      (do (movement/disembark-army-with-target coords adjacent-target target)
          (game-loop/item-processed)
          true)

      :else true))) ;; Ignore invalid disembark targets

(defn- handle-standard-unit-movement [coords adjacent-target target extended? active-unit]
  (cond
    (and (= :army (:type active-unit)) (not extended?) (combat/hostile-city? adjacent-target))
    (do (combat/attempt-conquest coords adjacent-target)
        (game-loop/item-processed)
        true)

    (and (= :fighter (:type active-unit)) (not extended?) (combat/hostile-city? adjacent-target))
    (do (combat/attempt-fighter-overfly coords adjacent-target)
        (game-loop/item-processed)
        true)

    :else
    (do (movement/set-unit-movement coords target)
        (game-loop/item-processed)
        true)))

(defn- handle-unit-movement-key [k coords cell]
  (let [direction (or (config/key->direction k)
                      (config/key->extended-direction k))
        extended? (boolean (config/key->extended-direction k))]
    (when direction
      (let [active-unit (movement/get-active-unit cell)]
        (when (and active-unit (= (:owner active-unit) :player))
          (let [[x y] coords
                [dx dy] direction
                adjacent-target [(+ x dx) (+ y dy)]
                target-cell (get-in @atoms/game-map adjacent-target)
                target (if extended?
                         (calculate-extended-target coords direction)
                         adjacent-target)
                context (movement/movement-context cell active-unit)]
            (case context
              :airport-fighter (launch-fighter-and-update movement/launch-fighter-from-airport coords target)
              :carrier-fighter (launch-fighter-and-update movement/launch-fighter-from-carrier coords target)
              :army-aboard (handle-army-aboard-movement coords adjacent-target target extended? target-cell)
              :standard-unit (handle-standard-unit-movement coords adjacent-target target extended? active-unit))))))))


(defn- handle-space-key [coords]
  (swap! atoms/player-items rest)
  (game-loop/item-processed)
  true)

(defn- handle-unload-key [coords cell]
  (let [contents (:contents cell)]
    (cond
      (uc/transport-at-beach? contents)
      (do (movement/wake-armies-on-transport coords)
          (game-loop/item-processed)
          true)

      (uc/carrier-with-fighters? contents)
      (do (movement/wake-fighters-on-carrier coords)
          (game-loop/item-processed)
          true)

      :else nil)))

(defn- handle-sentry-key [coords cell active-unit]
  (let [is-army-aboard? (movement/is-army-aboard-transport? active-unit)
        is-carrier-fighter? (movement/is-fighter-from-carrier? active-unit)
        is-airport-fighter? (movement/is-fighter-from-airport? active-unit)]
    (cond
      is-army-aboard?
      (do (movement/sleep-armies-on-transport coords)
          (game-loop/item-processed)
          true)

      is-carrier-fighter?
      (do (movement/sleep-fighters-on-carrier coords)
          (game-loop/item-processed)
          true)

      (and (not= :city (:type cell)) (not is-airport-fighter?) (not is-carrier-fighter?))
      (do (movement/set-unit-mode coords :sentry)
          (game-loop/item-processed)
          true)

      :else nil)))

(defn- find-adjacent-land [coords]
  (let [[x y] coords]
    (first (for [dx [-1 0 1] dy [-1 0 1]
                 :when (not (and (zero? dx) (zero? dy)))
                 :let [target [(+ x dx) (+ y dy)]
                       tcell (get-in @atoms/game-map target)]
                 :when (and tcell (= :land (:type tcell)) (not (:contents tcell)))]
             target))))

(defn- handle-look-around-key [coords cell active-unit]
  (let [is-army-aboard? (movement/is-army-aboard-transport? active-unit)
        near-coast? (movement/adjacent-to-land? coords atoms/game-map)
        rejection-reason (movement/coastline-follow-rejection-reason active-unit near-coast?)]
    (cond
      ;; Army (not aboard) - explore mode
      (and (= :army (:type active-unit)) (not is-army-aboard?))
      (do (movement/set-explore-mode coords)
          (game-loop/item-processed)
          true)

      ;; Army aboard transport - disembark to explore
      is-army-aboard?
      (do (when-let [valid-target (find-adjacent-land coords)]
            (let [army-pos (movement/disembark-army-to-explore coords valid-target)]
              (reset! atoms/waiting-for-input false)
              (reset! atoms/message "")
              (reset! atoms/cells-needing-attention [])
              (swap! atoms/player-items #(cons army-pos (rest %)))))
          true)

      ;; Transport or patrol-boat near coast - coastline follow
      (movement/coastline-follow-eligible? active-unit near-coast?)
      (do (movement/set-coastline-follow-mode coords)
          (game-loop/item-processed)
          true)

      ;; Transport or patrol-boat not near coast - show reason
      rejection-reason
      (do (reset! atoms/message (rejection-reason config/messages))
          true)

      :else nil)))

(defn handle-key [k]
  (when-let [coords (first @atoms/cells-needing-attention)]
    (let [cell (get-in @atoms/game-map coords)
          active-unit (movement/get-active-unit cell)]
      (if active-unit
        (case k
          :space (handle-space-key coords)
          :u (handle-unload-key coords cell)
          :s (handle-sentry-key coords cell active-unit)
          :l (handle-look-around-key coords cell active-unit)
          (handle-unit-movement-key k coords cell))
        (handle-city-production-key k coords cell)))))

(defn add-unit-at-mouse [unit-type]
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (movement/add-unit-at (map-utils/determine-cell-coordinates x y) unit-type))))

(defn wake-at-mouse []
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (movement/wake-at (map-utils/determine-cell-coordinates x y)))))

(defn own-city-at-mouse []
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (let [[cx cy] (map-utils/determine-cell-coordinates x y)
            cell (get-in @atoms/game-map [cx cy])]
        (when (= (:type cell) :city)
          (swap! atoms/game-map assoc-in [cx cy :city-status] :player)
          true)))))

(defn set-destination-at-mouse []
  "Sets the destination to the cell under the mouse cursor."
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (let [[cx cy] (map-utils/determine-cell-coordinates x y)]
        (reset! atoms/destination [cx cy])
        true))))

(defn set-marching-orders-at-mouse []
  "Sets marching orders on a player city, transport, or waypoint under the mouse to the current destination."
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
                (atoms/set-confirmation-message (str "Marching orders set to " (first dest) "," (second dest)) 2000)
                true)

            (and (= (:type contents) :transport)
                 (= (:owner contents) :player))
            (do (swap! atoms/game-map assoc-in [cx cy :contents :marching-orders] dest)
                (reset! atoms/destination nil)
                (atoms/set-confirmation-message (str "Marching orders set to " (first dest) "," (second dest)) 2000)
                true)

            (:waypoint cell)
            (do (waypoint/set-waypoint-orders [cx cy])
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
                (atoms/set-confirmation-message (str "Flight path set to " (first dest) "," (second dest)) 2000)
                true)

            (and (= (:type contents) :carrier)
                 (= (:owner contents) :player))
            (do (swap! atoms/game-map assoc-in [cx cy :contents :flight-path] dest)
                (reset! atoms/destination nil)
                (atoms/set-confirmation-message (str "Flight path set to " (first dest) "," (second dest)) 2000)
                true)

            :else nil))))))

(defn set-waypoint-at-mouse []
  "Creates or removes a waypoint at the cell under the mouse cursor."
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (let [[cx cy] (map-utils/determine-cell-coordinates x y)]
        (when (waypoint/create-waypoint [cx cy])
          (let [cell (get-in @atoms/game-map [cx cy])]
            (if (:waypoint cell)
              (atoms/set-confirmation-message (str "Waypoint placed at " cx "," cy) 2000)
              (atoms/set-confirmation-message (str "Waypoint removed from " cx "," cy) 2000)))
          true)))))

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
              (atoms/set-confirmation-message (str "Marching orders set to " (first target) "," (second target)) 2000)
              true)))))))

(defn key-down [k]
  ;; Handle key down events
  (if @atoms/backtick-pressed
    (do
      (reset! atoms/backtick-pressed false)
      (case k
        :a (add-unit-at-mouse :army)
        :f (add-unit-at-mouse :fighter)
        :z (add-unit-at-mouse :satellite)
        :t (add-unit-at-mouse :transport)
        :p (add-unit-at-mouse :patrol-boat)
        :d (add-unit-at-mouse :destroyer)
        :s (add-unit-at-mouse :submarine)
        :c (add-unit-at-mouse :carrier)
        :b (add-unit-at-mouse :battleship)
        :o (own-city-at-mouse)
        nil))
    (cond
      (= k (keyword "`")) (reset! atoms/backtick-pressed true)
      (= k :p) (game-loop/toggle-pause)
      (= k :+) (swap! atoms/map-to-display {:player-map :computer-map
                                            :computer-map :actual-map
                                            :actual-map :player-map})
      (= k (keyword ".")) (set-destination-at-mouse)
      (and (= k :m) (set-marching-orders-at-mouse)) nil
      (and (= k :f) @atoms/destination (set-flight-path-at-mouse)) nil
      (and (= k :u) (wake-at-mouse)) nil
      (set-city-marching-orders-by-direction k) nil
      (handle-key k) nil
      (and (= k (keyword "*")) (set-waypoint-at-mouse)) nil
      :else nil)))
