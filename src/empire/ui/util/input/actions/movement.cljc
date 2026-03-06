(ns empire.ui.util.input.actions.movement
  (:require [empire.application.ports.unit-state :as ports]
            [empire.application.ports.movement-execution :as exec-ports]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.application.combat :as combat]
            [empire.containers.ops :as container-ops]
            [empire.player.commands :as player-commands]
            [empire.ui.util.input.actions.helpers :as helpers]
            [empire.units.dispatcher :as dispatcher]))

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

(defn army-aboard-action [extended? target-cell hostile-city?]
  (player-commands/army-aboard-action extended? target-cell hostile-city?))

(defn- handle-army-aboard-movement [coords adjacent-target target extended? target-cell]
  (case (army-aboard-action extended? target-cell (combat/hostile-city? (sa/current-world) adjacent-target))
    :disembark (do (container-ops/disembark-army-from-transport coords adjacent-target)
                   (helpers/item-processed!))
    :disembark-with-target (do (container-ops/disembark-army-with-target coords adjacent-target target)
                               (helpers/item-processed!))
    :conquest (do (container-ops/remove-army-from-transport coords)
                  (combat/apply-combat-result! (combat/attempt-city-conquest (sa/current-world) adjacent-target))
                  (helpers/item-processed!))
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

(defn- hostile-city-action [unit-type adjacent-target extended?]
  (when (and (not extended?) (combat/hostile-city? (sa/current-world) adjacent-target))
    ({:army :army-conquest
      :fighter :fighter-overfly} unit-type)))

(defn- standard-movement-action [active-unit adjacent-target extended?]
  (or (hostile-city-action (:type active-unit) adjacent-target extended?)
      (when (and (not extended?)
                 (undamaged-ship-entering-friendly-city? active-unit adjacent-target))
        :reject-undamaged-ship)
      :normal-move))

(defn- perform-standard-movement! [action coords adjacent-target target extended?]
  (case action
    :army-conquest (combat/apply-combat-result! (combat/attempt-conquest (sa/current-world) coords adjacent-target))
    :fighter-overfly (combat/apply-combat-result! (combat/attempt-fighter-overfly (sa/current-world) coords adjacent-target))
    :reject-undamaged-ship (helpers/set-error-message! "Ship not damaged, entry denied." config/error-message-duration)
    :normal-move (exec-ports/movement-set-unit-movement (helpers/execution-port) coords target extended?))
  (when (not= :reject-undamaged-ship action)
    (helpers/item-processed!))
  true)

(defn- handle-standard-unit-movement [coords adjacent-target target extended? active-unit]
  (-> (standard-movement-action active-unit adjacent-target extended?)
      (perform-standard-movement! coords adjacent-target target extended?)))

(defn- execute-unit-movement [coords direction extended? active-unit cell]
  (let [[x y] coords
        [dx dy] direction
        adjacent-target [(+ x dx) (+ y dy)]
        target-cell (get-in (sa/current-world) adjacent-target)
        target (if extended?
                 (calculate-extended-target coords direction)
                 adjacent-target)
        context (ports/movement-context (helpers/unit-state-port) cell active-unit)]
    (case context
      :airport-fighter (launch-fighter-and-update container-ops/launch-fighter-from-airport coords target)
      :carrier-fighter (launch-fighter-and-update container-ops/launch-fighter-from-carrier coords target)
      :army-aboard (handle-army-aboard-movement coords adjacent-target target extended? target-cell)
      :standard-unit (handle-standard-unit-movement coords adjacent-target target extended? active-unit))))

(defn handle-unit-movement-key [k coords cell]
  (let [direction (or (config/key->direction k)
                      (config/key->extended-direction k))
        extended? (boolean (config/key->extended-direction k))]
    (when direction
      (let [active-unit (ports/movement-get-active-unit (helpers/unit-state-port) cell)]
        (when (and active-unit (= (:owner active-unit) :player))
          (execute-unit-movement coords direction extended? active-unit cell))))))
