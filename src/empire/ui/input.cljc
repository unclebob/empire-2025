(ns empire.ui.input
  (:require [empire.atoms :as atoms]
            [empire.debug :as debug]
            [empire.player.attention :as attention]
            [empire.player.combat :as combat]
            [empire.config :as config]
            [empire.containers.ops :as container-ops]
            [empire.game-loop :as game-loop]
            [empire.movement.coastline :as coastline]
            [empire.movement.explore :as explore]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.movement :as movement]
            [empire.player.production :as production]
            [empire.containers.helpers :as uc]
            [empire.units.dispatcher :as dispatcher]
            [empire.movement.waypoint :as waypoint]
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
      (let [fighter-pos (container-ops/launch-fighter-from-airport attn-coords clicked-coords)]
        (reset! atoms/waiting-for-input false)
        (reset! atoms/message "")
        (reset! atoms/cells-needing-attention [])
        (swap! atoms/player-items #(cons fighter-pos (rest %))))

      (and is-army-aboard? adjacent? (= (:type target-cell) :land) (not (:contents target-cell)))
      (do
        (container-ops/disembark-army-from-transport attn-coords clicked-coords)
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

(defn- try-set-production [coords item]
  (let [[x y] coords
        coastal? (map-utils/on-coast? x y)
        naval? (dispatcher/naval-units item)]
    (if (and naval? (not coastal?))
      (atoms/set-line3-message (format "Must be coastal city to produce %s." (name item)) 3000)
      (do
        (production/set-city-production coords item)
        (game-loop/item-processed)))
    true))

(defn- handle-city-production-key [k coords cell]
  (when (and (= (:type cell) :city)
             (= (:city-status cell) :player)
             (not (movement/get-active-unit cell)))
    (cond
      (= k :space) (do (swap! atoms/player-items rest)
                       (game-loop/item-processed)
                       true)
      (= k :x) (do (swap! atoms/production assoc coords :none)
                   (game-loop/item-processed)
                   true)
      (config/key->production-item k) (try-set-production coords (config/key->production-item k)))))

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
      (do (container-ops/disembark-army-from-transport coords adjacent-target)
          (game-loop/item-processed)
          true)

      (and extended? valid-land?)
      (do (container-ops/disembark-army-with-target coords adjacent-target target)
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
              :airport-fighter (launch-fighter-and-update container-ops/launch-fighter-from-airport coords target)
              :carrier-fighter (launch-fighter-and-update container-ops/launch-fighter-from-carrier coords target)
              :army-aboard (handle-army-aboard-movement coords adjacent-target target extended? target-cell)
              :standard-unit (handle-standard-unit-movement coords adjacent-target target extended? active-unit))))))))


(defn- handle-space-key [coords]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)]
    (when unit
      (if (= :fighter (:type unit))
        (let [current-fuel (:fuel unit config/fighter-fuel)
              fuel-cost (config/unit-speed :fighter)
              new-fuel (- current-fuel fuel-cost)]
          (if (<= new-fuel 0)
            (do
              (swap! atoms/game-map assoc-in (conj coords :contents :hits) 0)
              (swap! atoms/game-map assoc-in (conj coords :contents :reason) :skipping-this-round))
            (do
              (swap! atoms/game-map assoc-in (conj coords :contents :fuel) new-fuel)
              (swap! atoms/game-map assoc-in (conj coords :contents :reason) (str "Skipping this round. Fuel: " new-fuel)))))
        (swap! atoms/game-map assoc-in (conj coords :contents :reason) :skipping-this-round))))
  (swap! atoms/player-items rest)
  (game-loop/item-processed)
  true)

(defn- handle-unload-key [coords cell]
  (let [contents (:contents cell)]
    (cond
      (uc/transport-at-beach? contents)
      (do (container-ops/wake-armies-on-transport coords)
          (game-loop/item-processed)
          true)

      (uc/carrier-with-fighters? contents)
      (do (container-ops/wake-fighters-on-carrier coords)
          (game-loop/item-processed)
          true)

      :else nil)))

(defn- handle-sentry-key [coords cell active-unit]
  (let [is-army-aboard? (movement/is-army-aboard-transport? active-unit)
        is-carrier-fighter? (movement/is-fighter-from-carrier? active-unit)
        is-airport-fighter? (movement/is-fighter-from-airport? active-unit)]
    (cond
      is-army-aboard?
      (do (container-ops/sleep-armies-on-transport coords)
          (game-loop/item-processed)
          true)

      is-carrier-fighter?
      (do (container-ops/sleep-fighters-on-carrier coords)
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
        near-coast? (map-utils/adjacent-to-land? coords atoms/game-map)
        rejection-reason (coastline/coastline-follow-rejection-reason active-unit near-coast?)]
    (cond
      ;; Army (not aboard) - explore mode
      (and (= :army (:type active-unit)) (not is-army-aboard?))
      (do (explore/set-explore-mode coords)
          (game-loop/item-processed)
          true)

      ;; Army aboard transport - disembark to explore
      is-army-aboard?
      (do (when-let [valid-target (find-adjacent-land coords)]
            (let [army-pos (container-ops/disembark-army-to-explore coords valid-target)]
              ;; Add army to front but keep transport in list for remaining awake armies
              (swap! atoms/player-items #(cons army-pos %))
              (game-loop/item-processed)))
          true)

      ;; Transport or patrol-boat near coast - coastline follow
      (coastline/coastline-follow-eligible? active-unit near-coast?)
      (do (coastline/set-coastline-follow-mode coords)
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

(defn add-unit-at-mouse
  ([unit-type] (add-unit-at-mouse unit-type :player))
  ([unit-type owner]
   (let [x (q/mouse-x)
         y (q/mouse-y)]
     (when (map-utils/on-map? x y)
       (movement/add-unit-at (map-utils/determine-cell-coordinates x y) unit-type owner)))))

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

(defn set-city-lookaround
  "Sets marching orders to :lookaround on a player city at the given coordinates."
  [[cx cy]]
  (let [cell (get-in @atoms/game-map [cx cy])]
    (when (and (= (:type cell) :city)
               (= (:city-status cell) :player))
      (swap! atoms/game-map assoc-in [cx cy :marching-orders] :lookaround)
      (atoms/set-confirmation-message "Marching orders set to lookaround" 2000)
      true)))

(defn set-lookaround-at-mouse []
  "Sets lookaround marching orders on a player city under the mouse cursor."
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (set-city-lookaround (map-utils/determine-cell-coordinates x y)))))

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
  "Sets marching orders on a player city or waypoint under the mouse to the map edge in the given direction."
  (when-let [direction (config/key->direction k)]
    (let [x (q/mouse-x)
          y (q/mouse-y)]
      (when (map-utils/on-map? x y)
        (let [[cx cy] (map-utils/determine-cell-coordinates x y)
              cell (get-in @atoms/game-map [cx cy])]
          (cond
            (and (= (:type cell) :city)
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
              true)

            (:waypoint cell)
            (waypoint/set-waypoint-orders-by-direction [cx cy] direction)))))))

;; Debug drag functions

(defn modifier-held?
  "Returns true if a modifier key (ctrl, meta, alt) is held."
  [modifiers]
  (or (:ctrl modifiers) (:meta modifiers) (:alt modifiers)))

(defn debug-drag-start!
  "Starts a debug drag operation at the given screen coordinates."
  [x y]
  (reset! atoms/debug-drag-start [x y])
  (reset! atoms/debug-drag-current [x y]))

(defn debug-drag-update!
  "Updates the current drag position. Only updates if a drag is active."
  [x y]
  (when @atoms/debug-drag-start
    (reset! atoms/debug-drag-current [x y])))

(defn- has-area?
  "Returns true if the cell range covers more than one cell."
  [[[start-row start-col] [end-row end-col]]]
  (or (not= start-row end-row)
      (not= start-col end-col)))

(defn debug-drag-cancel!
  "Cancels a debug drag operation without writing a dump."
  []
  (reset! atoms/debug-drag-start nil)
  (reset! atoms/debug-drag-current nil))

(defn debug-drag-end!
  "Ends a debug drag operation and triggers the dump if ctrl is held and selection has area.
   Converts screen coordinates to cell range and writes the dump file."
  [x y modifiers]
  (when @atoms/debug-drag-start
    (when (modifier-held? modifiers)
      (let [start @atoms/debug-drag-start
            end [x y]
            cell-range (debug/screen-coords-to-cell-range start end)]
        (when (has-area? cell-range)
          (let [filename (debug/write-dump! (first cell-range) (second cell-range))]
            (reset! atoms/debug-message (str "Debug: " filename))))))
    (reset! atoms/debug-drag-start nil)
    (reset! atoms/debug-drag-current nil)))

(defn key-down [k]
  (debug/log-action! [:key-pressed k])
  ;; Handle key down events
  (if @atoms/backtick-pressed
    (do
      (reset! atoms/backtick-pressed false)
      (case k
        ;; Uppercase = player units
        :A (add-unit-at-mouse :army :player)
        :F (add-unit-at-mouse :fighter :player)
        :Z (add-unit-at-mouse :satellite :player)
        :T (add-unit-at-mouse :transport :player)
        :P (add-unit-at-mouse :patrol-boat :player)
        :D (add-unit-at-mouse :destroyer :player)
        :S (add-unit-at-mouse :submarine :player)
        :C (add-unit-at-mouse :carrier :player)
        :B (add-unit-at-mouse :battleship :player)
        ;; Lowercase = enemy/computer units
        :a (add-unit-at-mouse :army :computer)
        :f (add-unit-at-mouse :fighter :computer)
        :z (add-unit-at-mouse :satellite :computer)
        :t (add-unit-at-mouse :transport :computer)
        :p (add-unit-at-mouse :patrol-boat :computer)
        :d (add-unit-at-mouse :destroyer :computer)
        :s (add-unit-at-mouse :submarine :computer)
        :c (add-unit-at-mouse :carrier :computer)
        :b (add-unit-at-mouse :battleship :computer)
        ;; Other commands
        :o (own-city-at-mouse)
        nil))
    (cond
      (= k (keyword "`")) (reset! atoms/backtick-pressed true)
      (= k :P) (game-loop/toggle-pause)
      (and (= k :space) @atoms/paused) (game-loop/step-one-round)
      (= k :+) (swap! atoms/map-to-display {:player-map :computer-map
                                            :computer-map :actual-map
                                            :actual-map :player-map})
      (= k (keyword ".")) (set-destination-at-mouse)
      (and (= k :m) (set-marching-orders-at-mouse)) nil
      (and (= k :f) @atoms/destination (set-flight-path-at-mouse)) nil
      (and (= k :u) (wake-at-mouse)) nil
      (and (= k :l) (set-lookaround-at-mouse)) nil
      (set-city-marching-orders-by-direction k) nil
      (handle-key k) nil
      (and (= k (keyword "*")) (set-waypoint-at-mouse)) nil
      :else nil)))
