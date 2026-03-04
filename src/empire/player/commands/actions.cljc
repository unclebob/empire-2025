;; mutation-tested: no
(ns empire.player.commands.actions
  "Extracted unit action handlers for player command processing."
  (:require [empire.application.movement-services :as movement-services]
            [empire.application.player-movement-services :as player-movement]
            [empire.application.ports.movement :as ports]
            [empire.player.attention :as attention]
            [empire.combat :as combat]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.config :as config]
            [empire.units.dispatcher :as dispatcher]))

(defn- current-world [ctx]
  ((:current-world ctx)))

(defn- update-game-map! [ctx f & args]
  (apply (:update-game-map! ctx) f args))

(defn- read-runtime-state [ctx k]
  ((:read-runtime-state ctx) k))

(defn- write-runtime-state! [ctx k v]
  ((:write-runtime-state! ctx) k v))

(defn- update-runtime-state! [ctx k f & args]
  (apply (:update-runtime-state! ctx) k f args))

(defn- movement-port [ctx]
  (or (:movement-port ctx)
      (throw (ex-info "Movement port missing from commands actions ctx" {}))))

(defn- item-processed! [ctx]
  (write-runtime-state! ctx :waiting-for-input false)
  (write-runtime-state! ctx :cells-needing-attention []))

(defn handle-space-key [ctx coords]
  (let [cell (get-in (current-world ctx) coords)
        unit (:contents cell)]
    (when unit
      (if (= :fighter (:type unit))
        (let [current-fuel (:fuel unit config/fighter-fuel)
              fuel-cost (config/unit-speed :fighter)
              new-fuel (- current-fuel fuel-cost)]
          (if (<= new-fuel 0)
            (do
              (update-game-map! ctx assoc-in (conj coords :contents :hits) 0)
              (update-game-map! ctx assoc-in (conj coords :contents :reason) :skipping-this-round))
            (do
              (update-game-map! ctx assoc-in (conj coords :contents :fuel) new-fuel)
              (update-game-map! ctx assoc-in (conj coords :contents :reason) (str "Skipping this round. Fuel: " new-fuel)))))
        (update-game-map! ctx assoc-in (conj coords :contents :reason) :skipping-this-round))))
  (update-runtime-state! ctx :player-items rest)
  (item-processed! ctx)
  true)

(defn handle-unload-key [ctx coords cell]
  (let [contents (:contents cell)]
    (cond
      (uc/transport-with-armies? contents)
      (do (container-ops/wake-armies-on-transport coords)
          (item-processed! ctx)
          true)

      (uc/carrier-with-fighters? contents)
      (do (container-ops/wake-fighters-on-carrier coords)
          (item-processed! ctx)
          true)

      :else nil)))

(defn handle-sentry-key [ctx coords cell active-unit]
  (let [is-army-aboard? (ports/movement-is-army-aboard-transport? (movement-port ctx) active-unit)
        is-carrier-fighter? (ports/movement-is-fighter-from-carrier? (movement-port ctx) active-unit)
        is-airport-fighter? (ports/movement-is-fighter-from-airport? (movement-port ctx) active-unit)]
    (cond
      is-army-aboard?
      (do (container-ops/sleep-armies-on-transport coords)
          (item-processed! ctx)
          true)

      is-carrier-fighter?
      (do (container-ops/sleep-fighters-on-carrier coords)
          (item-processed! ctx)
          true)

      (and (not= :city (:type cell)) (not is-airport-fighter?) (not is-carrier-fighter?))
      (do (ports/movement-set-unit-mode (movement-port ctx) coords :sentry)
          (item-processed! ctx)
          true)

      :else nil)))

(defn- find-adjacent-land [ctx coords]
  (let [[x y] coords]
    (first (for [dx [-1 0 1] dy [-1 0 1]
                 :when (not (and (zero? dx) (zero? dy)))
                 :let [target [(+ x dx) (+ y dy)]
                       tcell (get-in (current-world ctx) target)]
                 :when (and tcell (= :land (:type tcell)) (not (:contents tcell)))]
             target))))

(defn- free-army? [active-unit is-army-aboard?]
  (and (= :army (:type active-unit)) (not is-army-aboard?)))

(defn handle-look-around-key [ctx coords _cell active-unit]
  (let [is-army-aboard? (ports/movement-is-army-aboard-transport? (movement-port ctx) active-unit)
        near-coast? (movement-services/any-neighbor-matches?
                     coords (current-world ctx) movement-services/neighbor-offsets
                     #(= :land (:type %)))
        rejection-reason (player-movement/coastline-follow-rejection-reason active-unit near-coast?)]
    (cond
      (free-army? active-unit is-army-aboard?)
      (do (player-movement/set-explore-mode coords)
          (item-processed! ctx)
          true)

      is-army-aboard?
      (do (when-let [valid-target (find-adjacent-land ctx coords)]
            (container-ops/disembark-army-to-explore coords valid-target)
            (item-processed! ctx))
          true)

      (player-movement/coastline-follow-eligible? active-unit near-coast?)
      (do (player-movement/set-coastline-follow-mode coords)
          (item-processed! ctx)
          true)

      rejection-reason
      (do (write-runtime-state! ctx :attention-message (rejection-reason config/messages))
          true)

      :else nil)))

(defn- adjacent-coords? [c1 c2]
  (let [[ax ay] c1 [cx cy] c2]
    (and (<= (abs (- ax cx)) 1) (<= (abs (- ay cy)) 1))))

(defn- click-army-aboard [ctx attn-coords clicked-coords target-cell]
  (when (and (adjacent-coords? attn-coords clicked-coords)
             (= (:type target-cell) :land)
             (not (:contents target-cell)))
    (container-ops/disembark-army-from-transport attn-coords clicked-coords)
    (item-processed! ctx)))

(defn- click-standard-unit [ctx attn-coords clicked-coords unit-type]
  (if (and (adjacent-coords? attn-coords clicked-coords)
           (combat/hostile-city? clicked-coords))
    (case unit-type
      :army (combat/attempt-conquest attn-coords clicked-coords)
      :fighter (combat/attempt-fighter-overfly attn-coords clicked-coords)
      (ports/movement-set-unit-movement (movement-port ctx) attn-coords clicked-coords false))
    (ports/movement-set-unit-movement (movement-port ctx) attn-coords clicked-coords false)))

(defn handle-unit-click
  "Handles interaction with an attention-needing unit."
  [ctx clicked-coords attention-coords]
  (let [attn-coords (first attention-coords)
        attn-cell (get-in (current-world ctx) attn-coords)
        active-unit (ports/movement-get-active-unit (movement-port ctx) attn-cell)
        target-cell (get-in (current-world ctx) clicked-coords)
        context (ports/movement-context (movement-port ctx) attn-cell active-unit)]
    (case context
      :airport-fighter ((:launch-fighter-and-update ctx)
                        container-ops/launch-fighter-from-airport attn-coords clicked-coords)
      :army-aboard (click-army-aboard ctx attn-coords clicked-coords target-cell)
      (click-standard-unit ctx attn-coords clicked-coords (:type active-unit)))
    (item-processed! ctx)))

(defn handle-cell-click
  "Handles clicking on a map cell, prioritizing attention-needing items."
  [ctx cell-x cell-y]
  (let [attention-coords (read-runtime-state ctx :cells-needing-attention)
        clicked-coords [cell-x cell-y]]
    (when (attention/is-unit-needing-attention? attention-coords)
      (handle-unit-click ctx clicked-coords attention-coords))))
