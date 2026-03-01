(ns empire.ui.util.input.actions
  (:require [empire.atoms :as atoms]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.config :as config]
            [empire.combat :as combat]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.game-loop :as game-loop]
            [empire.movement.coastline :as coastline]
            [empire.movement.explore :as explore]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.movement :as movement]
            [empire.player.attention :as attention]
            [empire.player.orders :as orders]
            [empire.player.production :as production]
            [empire.player.commands :as commands]
            [empire.units.dispatcher :as dispatcher]))

(defonce ^:private state-ctx (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

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

(defn army-aboard-action [extended? target-cell hostile-city?]
  (let [valid-land? (and (= (:type target-cell) :land) (not (:contents target-cell)))]
    (cond
      valid-land? (if extended? :disembark-with-target :disembark)
      (and (not extended?) hostile-city?) :conquest
      :else :ignore)))

(defn- handle-army-aboard-movement [coords adjacent-target target extended? target-cell]
  (case (army-aboard-action extended? target-cell (combat/hostile-city? adjacent-target))
    :disembark (do (container-ops/disembark-army-from-transport coords adjacent-target)
                   (game-loop/item-processed))
    :disembark-with-target (do (container-ops/disembark-army-with-target coords adjacent-target target)
                               (game-loop/item-processed))
    :conquest (do (container-ops/remove-army-from-transport coords)
                  (combat/attempt-city-conquest adjacent-target)
                  (game-loop/item-processed))
    nil)
  true)

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
              (update-game-map! assoc-in (conj coords :contents :hits) 0)
              (update-game-map! assoc-in (conj coords :contents :reason) :skipping-this-round))
            (do
              (update-game-map! assoc-in (conj coords :contents :fuel) new-fuel)
              (update-game-map! assoc-in (conj coords :contents :reason) (str "Skipping this round. Fuel: " new-fuel)))))
        (update-game-map! assoc-in (conj coords :contents :reason) :skipping-this-round))))
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
