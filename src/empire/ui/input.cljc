(ns empire.ui.input
  (:require [empire.atoms :as atoms]
            [empire.debug :as debug]
            [empire.player.attention :as attention]
            [empire.combat :as combat]
            [empire.config :as config]
            [empire.containers.ops :as container-ops]
            [empire.game-loop :as game-loop]
            [empire.movement.coastline :as coastline]
            [empire.movement.explore :as explore]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.movement :as movement]
            [empire.player.orders :as orders]
            [empire.player.production :as production]
            [empire.containers.helpers :as uc]
            [empire.save-load :as save-load]
            [empire.units.dispatcher :as dispatcher]
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
        (reset! atoms/attention-message "")
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

(defn handle-load-menu-click
  "Handles a mouse click when the load menu is open.
   Loads the hovered file if one is selected."
  []
  (when-let [idx @atoms/load-menu-hovered]
    (let [files @atoms/load-menu-files]
      (when (< idx (count files))
        (save-load/load-game! (nth files idx))))))

(defn mouse-down
  "Handles mouse click events."
  [x y button]
  (cond
    ;; Load menu is open - handle menu click
    @atoms/load-menu-open
    (when (= button :left)
      (handle-load-menu-click))

    ;; Normal map click
    (and (= button :left) (map-utils/on-map? x y))
    (let [[cell-x cell-y] (map-utils/determine-cell-coordinates x y)]
      (reset! atoms/last-clicked-cell [cell-x cell-y])
      (handle-cell-click cell-x cell-y))))

(defn- try-set-production [coords item]
  (let [[x y] coords
        coastal? (map-utils/on-coast? x y)
        naval? (dispatcher/naval-units item)]
    (if (and naval? (not coastal?))
      (atoms/set-error-message (format "Must be coastal city to produce %s." (name item)) config/error-message-duration)
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
    (reset! atoms/attention-message "")
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

      (and (not extended?) (combat/hostile-city? adjacent-target))
      (do (container-ops/remove-army-from-transport coords)
          (combat/attempt-city-conquest adjacent-target)
          (game-loop/item-processed)
          true)

      :else true))) ;; Ignore invalid disembark targets

(defn- undamaged-ship-entering-friendly-city? [active-unit adjacent-target]
  (let [target-cell (get-in @atoms/game-map adjacent-target)
        unit-type (:type active-unit)
        max-hits (dispatcher/hits unit-type)]
    (and (dispatcher/naval-unit? unit-type)
         (= :city (:type target-cell))
         (= :player (:city-status target-cell))
         (= (:hits active-unit) max-hits))))

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

    (and (not extended?) (undamaged-ship-entering-friendly-city? active-unit adjacent-target))
    (do (atoms/set-error-message "Ship not damaged, entry denied." config/error-message-duration)
        true)

    :else
    (do (movement/set-unit-movement coords target extended?)
        (game-loop/item-processed)
        true)))

(defn- execute-unit-movement [coords direction extended? active-unit cell]
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
      :standard-unit (handle-standard-unit-movement coords adjacent-target target extended? active-unit))))

(defn- handle-unit-movement-key [k coords cell]
  (let [direction (or (config/key->direction k)
                      (config/key->extended-direction k))
        extended? (boolean (config/key->extended-direction k))]
    (when direction
      (let [active-unit (movement/get-active-unit cell)]
        (when (and active-unit (= (:owner active-unit) :player))
          (execute-unit-movement coords direction extended? active-unit cell))))))


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
      (uc/transport-with-armies? contents)
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
            (container-ops/disembark-army-to-explore coords valid-target)
            (game-loop/item-processed))
          true)

      ;; Transport or patrol-boat near coast - coastline follow
      (coastline/coastline-follow-eligible? active-unit near-coast?)
      (do (coastline/set-coastline-follow-mode coords)
          (game-loop/item-processed)
          true)

      ;; Transport or patrol-boat not near coast - show reason
      rejection-reason
      (do (reset! atoms/attention-message (rejection-reason config/messages))
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

(defn- mouse->cell []
  (let [x (q/mouse-x) y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (map-utils/determine-cell-coordinates x y))))

(defn dispatch-key [k cell-coords]
  (debug/log-action! [:key-pressed k])
  (cond
    @atoms/load-menu-open
    (when (= k :escape) (save-load/close-load-menu!))

    @atoms/backtick-pressed
    (do (reset! atoms/backtick-pressed false)
        (when cell-coords
          (case k
            :A (orders/add-unit-at cell-coords :army :player)
            :F (orders/add-unit-at cell-coords :fighter :player)
            :Z (orders/add-unit-at cell-coords :satellite :player)
            :T (orders/add-unit-at cell-coords :transport :player)
            :P (orders/add-unit-at cell-coords :patrol-boat :player)
            :D (orders/add-unit-at cell-coords :destroyer :player)
            :S (orders/add-unit-at cell-coords :submarine :player)
            :C (orders/add-unit-at cell-coords :carrier :player)
            :B (orders/add-unit-at cell-coords :battleship :player)
            :a (orders/add-unit-at cell-coords :army :computer)
            :f (orders/add-unit-at cell-coords :fighter :computer)
            :z (orders/add-unit-at cell-coords :satellite :computer)
            :t (orders/add-unit-at cell-coords :transport :computer)
            :p (orders/add-unit-at cell-coords :patrol-boat :computer)
            :d (orders/add-unit-at cell-coords :destroyer :computer)
            :s (orders/add-unit-at cell-coords :submarine :computer)
            :c (orders/add-unit-at cell-coords :carrier :computer)
            :b (orders/add-unit-at cell-coords :battleship :computer)
            :o (orders/own-city-at cell-coords)
            nil)))

    :else
    (cond
      (= k (keyword "`")) (reset! atoms/backtick-pressed true)
      (= k :P) (game-loop/toggle-pause)
      (and (= k :space) @atoms/paused) (game-loop/step-one-round)
      (= k :+) (swap! atoms/map-to-display {:player-map :computer-map
                                            :computer-map :actual-map
                                            :actual-map :player-map})
      (and (= k (keyword ".")) cell-coords) (orders/set-destination-at cell-coords)
      (and (= k :m) cell-coords (orders/set-marching-orders-at cell-coords)) nil
      (and (= k :f) @atoms/destination cell-coords (orders/set-flight-path-at cell-coords)) nil
      (and (= k :u) cell-coords (orders/wake-at cell-coords)) nil
      (and (= k :l) cell-coords (orders/set-city-lookaround cell-coords)) nil
      (and (= k (keyword "*")) cell-coords (orders/set-waypoint-at cell-coords)) nil
      (= k (keyword "!")) (let [filename (save-load/save-game!)]
                            (atoms/set-turn-message (str "Saved to " filename) 3000))
      (= k (keyword "^")) (save-load/open-load-menu!)
      (and cell-coords (orders/set-city-marching-orders-by-direction-at cell-coords k)) nil
      (handle-key k) nil
      :else nil)))

(defn key-down [k]
  (dispatch-key k (mouse->cell)))
