;; mutation-tested: 2026-02-26
(ns empire.player.commands
  "Pure command dispatch for player attention items.
   Handles key input when units/cities need attention. No Quil dependency."
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.player.attention :as attention]
            [empire.combat :as combat]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.game-loop :as game-loop]
            [empire.movement.coastline :as coastline]
            [empire.movement.explore :as explore]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.movement :as movement]
            [empire.player.production :as production]
            [empire.units.dispatcher :as dispatcher]))

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
    (when valid-land?
      (if extended?
        (container-ops/disembark-army-with-target coords adjacent-target target)
        (container-ops/disembark-army-from-transport coords adjacent-target))
      (game-loop/item-processed))
    true))

(defn- undamaged-ship-entering-friendly-city? [active-unit adjacent-target]
  (let [target-cell (get-in @atoms/game-map adjacent-target)
        unit-type (:type active-unit)
        max-hits (dispatcher/hits unit-type)]
    (and (dispatcher/naval-unit? unit-type)
         (= :city (:type target-cell))
         (= :player (:city-status target-cell))
         (= (:hits active-unit) max-hits))))

(defn- immediate-hostile-city? [extended? adjacent-target]
  (and (not extended?) (combat/hostile-city? adjacent-target)))

(defn- handle-standard-unit-movement [coords adjacent-target target extended? active-unit]
  (let [hostile? (immediate-hostile-city? extended? adjacent-target)]
    (cond
      (and hostile? (= :army (:type active-unit)))
      (do (combat/attempt-conquest coords adjacent-target)
          (game-loop/item-processed)
          true)

      (and hostile? (= :fighter (:type active-unit)))
      (do (combat/attempt-fighter-overfly coords adjacent-target)
          (game-loop/item-processed)
          true)

      (and (not extended?) (undamaged-ship-entering-friendly-city? active-unit adjacent-target))
      (do (atoms/set-error-message "Ship not damaged, entry denied." config/error-message-duration)
          true)

      :else
      (do (movement/set-unit-movement coords target)
          (game-loop/item-processed)
          true))))

(defn- resolve-direction [k]
  (when-let [direction (or (config/key->direction k)
                           (config/key->extended-direction k))]
    {:direction direction
     :extended? (boolean (config/key->extended-direction k))}))

(defn- player-unit? [unit]
  (and unit (= :player (:owner unit))))

(defn- dispatch-movement [context coords adjacent-target target extended? target-cell active-unit _cell]
  (case context
    :airport-fighter (launch-fighter-and-update container-ops/launch-fighter-from-airport coords target)
    :carrier-fighter (launch-fighter-and-update container-ops/launch-fighter-from-carrier coords target)
    :army-aboard (handle-army-aboard-movement coords adjacent-target target extended? target-cell)
    :standard-unit (handle-standard-unit-movement coords adjacent-target target extended? active-unit)))

(defn- handle-unit-movement-key [k coords cell]
  (when-let [{:keys [direction extended?]} (resolve-direction k)]
    (let [active-unit (movement/get-active-unit cell)]
      (when (player-unit? active-unit)
        (let [[x y] coords
              [dx dy] direction
              adjacent-target [(+ x dx) (+ y dy)]
              target-cell (get-in @atoms/game-map adjacent-target)
              target (if extended?
                       (calculate-extended-target coords direction)
                       adjacent-target)]
          (dispatch-movement (movement/movement-context cell active-unit)
                             coords adjacent-target target extended? target-cell active-unit cell))))))

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

(defn- free-army? [active-unit is-army-aboard?]
  (and (= :army (:type active-unit)) (not is-army-aboard?)))

(defn- handle-look-around-key [coords cell active-unit]
  (let [is-army-aboard? (movement/is-army-aboard-transport? active-unit)
        near-coast? (map-utils/adjacent-to-land? coords atoms/game-map)
        rejection-reason (coastline/coastline-follow-rejection-reason active-unit near-coast?)]
    (cond
      (free-army? active-unit is-army-aboard?)
      (do (explore/set-explore-mode coords)
          (game-loop/item-processed)
          true)

      is-army-aboard?
      (do (when-let [valid-target (find-adjacent-land coords)]
            (container-ops/disembark-army-to-explore coords valid-target)
            (game-loop/item-processed))
          true)

      (coastline/coastline-follow-eligible? active-unit near-coast?)
      (do (coastline/set-coastline-follow-mode coords)
          (game-loop/item-processed)
          true)

      rejection-reason
      (do (reset! atoms/attention-message (rejection-reason config/messages))
          true)

      :else nil)))

(defn- adjacent-coords? [c1 c2]
  (let [[ax ay] c1 [cx cy] c2]
    (and (<= (abs (- ax cx)) 1) (<= (abs (- ay cy)) 1))))

(defn- click-army-aboard [attn-coords clicked-coords target-cell]
  (when (and (adjacent-coords? attn-coords clicked-coords)
             (= (:type target-cell) :land)
             (not (:contents target-cell)))
    (container-ops/disembark-army-from-transport attn-coords clicked-coords)
    (game-loop/item-processed)))

(defn- click-standard-unit [attn-coords clicked-coords unit-type]
  (if (and (adjacent-coords? attn-coords clicked-coords)
           (combat/hostile-city? clicked-coords))
    (case unit-type
      :army (combat/attempt-conquest attn-coords clicked-coords)
      :fighter (combat/attempt-fighter-overfly attn-coords clicked-coords)
      (movement/set-unit-movement attn-coords clicked-coords))
    (movement/set-unit-movement attn-coords clicked-coords)))

(defn handle-unit-click
  "Handles interaction with an attention-needing unit."
  [clicked-coords attention-coords]
  (let [attn-coords (first attention-coords)
        attn-cell (get-in @atoms/game-map attn-coords)
        active-unit (movement/get-active-unit attn-cell)
        target-cell (get-in @atoms/game-map clicked-coords)
        context (movement/movement-context attn-cell active-unit)]
    (case context
      :airport-fighter (launch-fighter-and-update
                         container-ops/launch-fighter-from-airport attn-coords clicked-coords)
      :army-aboard (click-army-aboard attn-coords clicked-coords target-cell)
      (click-standard-unit attn-coords clicked-coords (:type active-unit)))
    (game-loop/item-processed)))

(defn handle-cell-click
  "Handles clicking on a map cell, prioritizing attention-needing items."
  [cell-x cell-y]
  (let [attention-coords @atoms/cells-needing-attention
        clicked-coords [cell-x cell-y]]
    (when (attention/is-unit-needing-attention? attention-coords)
      (handle-unit-click clicked-coords attention-coords))))

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
