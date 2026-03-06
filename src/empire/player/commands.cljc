;; mutation-tested: 2026-02-26
(ns empire.player.commands
  "Pure command dispatch for player attention items.
   Handles key input when units/cities need attention. No Quil dependency."
  (:require [empire.state.api :as sa]
            [empire.movement.map-utils :as map-utils]
            [empire.application.ports.unit-state :as ports]
            [empire.application.ports.movement-execution :as exec-ports]
            [empire.config.core :as config]
            [empire.player.attention :as attention]
            [empire.player.commands-actions :as actions]
            [empire.application.combat :as combat]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.player.production :as production]
            [empire.units.dispatcher :as dispatcher]))

(defn- unit-state-port []
  (or (:unit-state-port (sa/state-ctx))
      (throw (ex-info "Unit state port not configured in runtime state context" {}))))

(defn- execution-port []
  (or (:execution-port (sa/state-ctx))
      (throw (ex-info "Execution port not configured in runtime state context" {}))))

(defn- set-error-message!
  [msg ms]
  (sa/write-state! :error-message msg)
  (sa/write-state! :error-until (+ (System/currentTimeMillis) ms)))

(defn- item-processed!
  []
  (sa/write-state! :waiting-for-input false)
  (sa/write-state! :cells-needing-attention []))

(defn- coastal-cell?
  [coords]
  (map-utils/any-neighbor-matches? coords (sa/current-world) map-utils/neighbor-offsets
                                           #(= :sea (:type %))))

(defn- try-set-production [coords item]
  (let [coastal? (coastal-cell? coords)
        naval? (dispatcher/naval-units item)]
    (if (and naval? (not coastal?))
      (set-error-message! (format "Must be coastal city to produce %s." (name item)) config/error-message-duration)
      (do
        (production/set-city-production coords item)
        (item-processed!)))
    true))

(defn- handle-city-production-key [k coords cell]
  (when (and (= (:type cell) :city)
             (= (:city-status cell) :player)
             (not (ports/movement-get-active-unit (unit-state-port) cell)))
    (cond
      (= k :space) (do (sa/update-state! :player-items rest)
                       (item-processed!)
                       true)
      (= k :x) (do (sa/update-state! :production assoc coords :none)
                   (item-processed!)
                   true)
      (config/key->production-item k) (try-set-production coords (config/key->production-item k)))))

(defn- calculate-extended-target [coords [dx dy]]
  (let [world (sa/current-world)
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
    (sa/write-state! :waiting-for-input false)
    (sa/write-state! :attention-message "")
    (sa/write-state! :cells-needing-attention [])
    (sa/update-state! :player-items #(cons fighter-pos (rest %)))
    true))

(defn- actions-ctx []
  {:current-world sa/current-world
   :update-game-map! sa/update-world!
   :read-runtime-state sa/read-state
   :write-runtime-state! sa/write-state!
   :update-runtime-state! sa/update-state!
   :unit-state-port (unit-state-port)
   :execution-port (execution-port)
   :launch-fighter-and-update launch-fighter-and-update})

(defn army-aboard-action
  [extended? target-cell hostile-city?]
  (let [valid-land? (and (= (:type target-cell) :land) (not (:contents target-cell)))]
    (cond
      valid-land? (if extended? :disembark-with-target :disembark)
      (and (not extended?) hostile-city?) :conquest
      :else :ignore)))

(defn- handle-army-aboard-movement [coords adjacent-target target extended? target-cell]
  (case (army-aboard-action extended? target-cell (combat/hostile-city? (sa/current-world) adjacent-target))
    :disembark
    (do
      (container-ops/disembark-army-from-transport coords adjacent-target)
      (item-processed!))
    :disembark-with-target
    (do
      (container-ops/disembark-army-with-target coords adjacent-target target)
      (item-processed!))
    :conquest
    (do
      (container-ops/remove-army-from-transport coords)
      (combat/apply-combat-result! (combat/attempt-city-conquest (sa/current-world) adjacent-target))
      (item-processed!))
    nil)
  true)

(defn- undamaged-ship-entering-friendly-city? [active-unit adjacent-target]
  (let [target-cell (get-in (sa/current-world) adjacent-target)
        unit-type (:type active-unit)
        max-hits (dispatcher/hits unit-type)]
    (and (dispatcher/naval-unit? unit-type)
         (= :city (:type target-cell))
         (= :player (:city-status target-cell))
         (= (:hits active-unit) max-hits))))

(defn- immediate-hostile-city? [extended? adjacent-target]
  (and (not extended?) (combat/hostile-city? (sa/current-world) adjacent-target)))

(defn- handle-standard-unit-movement [coords adjacent-target target extended? active-unit]
  (let [hostile? (immediate-hostile-city? extended? adjacent-target)]
    (cond
      (and hostile? (= :army (:type active-unit)))
      (do (combat/apply-combat-result! (combat/attempt-conquest (sa/current-world) coords adjacent-target))
          (item-processed!)
          true)

      (and hostile? (= :fighter (:type active-unit)))
      (do (combat/apply-combat-result! (combat/attempt-fighter-overfly (sa/current-world) coords adjacent-target))
          (item-processed!)
          true)

      (and (not extended?) (undamaged-ship-entering-friendly-city? active-unit adjacent-target))
      (do (set-error-message! "Ship not damaged, entry denied." config/error-message-duration)
          true)

      :else
      (do (exec-ports/movement-set-unit-movement (execution-port) coords target false)
          (item-processed!)
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
    (let [active-unit (ports/movement-get-active-unit (unit-state-port) cell)]
      (when (player-unit? active-unit)
        (let [[x y] coords
              [dx dy] direction
              adjacent-target [(+ x dx) (+ y dy)]
              target-cell (get-in (sa/current-world) adjacent-target)
              target (if extended?
                       (calculate-extended-target coords direction)
                       adjacent-target)]
          (dispatch-movement (ports/movement-context (unit-state-port) cell active-unit)
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
  (when-let [coords (first (sa/read-state :cells-needing-attention))]
    (let [cell (get-in (sa/current-world) coords)
          active-unit (ports/movement-get-active-unit (unit-state-port) cell)]
      (if active-unit
        (case k
          :space (actions/handle-space-key (actions-ctx) coords)
          :u (actions/handle-unload-key (actions-ctx) coords cell)
          :s (actions/handle-sentry-key (actions-ctx) coords cell active-unit)
          :l (actions/handle-look-around-key (actions-ctx) coords cell active-unit)
          (handle-unit-movement-key k coords cell))
        (handle-city-production-key k coords cell)))))
