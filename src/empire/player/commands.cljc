;; mutation-tested: 2026-02-26
(ns empire.player.commands
  "Pure command dispatch for player attention items.
   Handles key input when units/cities need attention. No Quil dependency."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.config :as config]
            [empire.player.attention :as attention]
            [empire.player.commands.actions :as actions]
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

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

(defn- update-runtime-state!
  [k f & args]
  (let [current (read-runtime-state k)
        next-state (apply f current args)]
    (write-runtime-state! k next-state)))

(defn- set-error-message!
  [msg ms]
  (write-runtime-state! :error-message msg)
  (write-runtime-state! :error-until (+ (System/currentTimeMillis) ms)))

(defn- coastal-cell?
  [coords]
  (map-utils/any-neighbor-matches? coords (current-world) map-utils/neighbor-offsets
                                  #(= :sea (:type %))))

(defn- try-set-production [coords item]
  (let [coastal? (coastal-cell? coords)
        naval? (dispatcher/naval-units item)]
    (if (and naval? (not coastal?))
      (set-error-message! (format "Must be coastal city to produce %s." (name item)) config/error-message-duration)
      (do
        (production/set-city-production coords item)
        (game-loop/item-processed)))
    true))

(defn- handle-city-production-key [k coords cell]
  (when (and (= (:type cell) :city)
             (= (:city-status cell) :player)
             (not (movement/get-active-unit cell)))
    (cond
      (= k :space) (do (update-runtime-state! :player-items rest)
                       (game-loop/item-processed)
                       true)
      (= k :x) (do (update-runtime-state! :production assoc coords :none)
                   (game-loop/item-processed)
                   true)
      (config/key->production-item k) (try-set-production coords (config/key->production-item k)))))

(defn- calculate-extended-target [coords [dx dy]]
  (let [world (current-world)
        height (count world)
        width (count (first world))
        [x y] coords]
    (loop [tx x ty y]
      (let [nx (+ tx dx)
            ny (+ ty dy)]
        (if (and (>= nx 0) (< nx height) (>= ny 0) (< ny width))
          (recur nx ny)
          [tx ty])))))

(defn- launch-fighter-and-update [launch-fn coords target]
  (let [fighter-pos (launch-fn coords target)]
    (write-runtime-state! :waiting-for-input false)
    (write-runtime-state! :attention-message "")
    (write-runtime-state! :cells-needing-attention [])
    (update-runtime-state! :player-items #(cons fighter-pos (rest %)))
    true))

(defn- actions-ctx []
  {:current-world current-world
   :update-game-map! update-game-map!
   :read-runtime-state read-runtime-state
   :write-runtime-state! write-runtime-state!
   :update-runtime-state! update-runtime-state!
   :launch-fighter-and-update launch-fighter-and-update})

(defn army-aboard-action
  [extended? target-cell hostile-city?]
  (let [valid-land? (and (= (:type target-cell) :land) (not (:contents target-cell)))]
    (cond
      valid-land? (if extended? :disembark-with-target :disembark)
      (and (not extended?) hostile-city?) :conquest
      :else :ignore)))

(defn- handle-army-aboard-movement [coords adjacent-target target extended? target-cell]
  (case (army-aboard-action extended? target-cell (combat/hostile-city? adjacent-target))
    :disembark
    (do
      (container-ops/disembark-army-from-transport coords adjacent-target)
      (game-loop/item-processed))
    :disembark-with-target
    (do
      (container-ops/disembark-army-with-target coords adjacent-target target)
      (game-loop/item-processed))
    :conquest
    (do
      (container-ops/remove-army-from-transport coords)
      (combat/attempt-city-conquest adjacent-target)
      (game-loop/item-processed))
    nil)
  true)

(defn- undamaged-ship-entering-friendly-city? [active-unit adjacent-target]
  (let [target-cell (get-in (current-world) adjacent-target)
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
      (do (set-error-message! "Ship not damaged, entry denied." config/error-message-duration)
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
              target-cell (get-in (current-world) adjacent-target)
              target (if extended?
                       (calculate-extended-target coords direction)
                       adjacent-target)]
          (dispatch-movement (movement/movement-context cell active-unit)
                             coords adjacent-target target extended? target-cell active-unit cell))))))

(defn handle-unit-click
  "Handles interaction with an attention-needing unit."
  [clicked-coords attention-coords]
  (actions/handle-unit-click (actions-ctx) clicked-coords attention-coords))

(defn handle-cell-click
  "Handles clicking on a map cell, prioritizing attention-needing items."
  [cell-x cell-y]
  (actions/handle-cell-click (actions-ctx) cell-x cell-y))

(defn handle-key [k]
  (when-let [coords (first (read-runtime-state :cells-needing-attention))]
    (let [cell (get-in (current-world) coords)
          active-unit (movement/get-active-unit cell)]
      (if active-unit
        (case k
          :space (actions/handle-space-key (actions-ctx) coords)
          :u (actions/handle-unload-key (actions-ctx) coords cell)
          :s (actions/handle-sentry-key (actions-ctx) coords cell active-unit)
          :l (actions/handle-look-around-key (actions-ctx) coords cell active-unit)
          (handle-unit-movement-key k coords cell))
        (handle-city-production-key k coords cell)))))
