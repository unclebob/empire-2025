(ns empire.player.commands
  "Pure command dispatch for player attention items.
   Handles key input when units/cities need attention. No Quil dependency."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.game-mechanics.movement.api :as movement-api]
            [empire.config.core :as config]
            [empire.player.attention :as attention]
            [empire.player.commands-actions :as actions]
            [empire.game-mechanics.services.combat :as combat]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.player.production :as production]
            [empire.config.units.dispatcher :as dispatcher]))

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
             (not (movement-state/get-active-unit cell)))
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
      (do (movement-api/set-unit-movement coords target)
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
    (let [active-unit (movement-state/get-active-unit cell)]
      (when (player-unit? active-unit)
        (let [[x y] coords
              [dx dy] direction
              adjacent-target [(+ x dx) (+ y dy)]
              target-cell (get-in (sa/current-world) adjacent-target)
              target (if extended?
                       (calculate-extended-target coords direction)
                       adjacent-target)]
          (dispatch-movement (movement-state/movement-context cell active-unit)
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
          active-unit (movement-state/get-active-unit cell)]
      (if active-unit
        (case k
          :space (actions/handle-space-key (actions-ctx) coords)
          :u (actions/handle-unload-key (actions-ctx) coords cell)
          :s (actions/handle-sentry-key (actions-ctx) coords cell active-unit)
          :l (actions/handle-look-around-key (actions-ctx) coords cell active-unit)
          (handle-unit-movement-key k coords cell))
        (handle-city-production-key k coords cell)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:02:34.542996-05:00", :module-hash "-1328021731", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 15, :hash "1063006811"} {:id "defn-/set-error-message!", :kind "defn-", :line 17, :end-line 20, :hash "-369960802"} {:id "defn-/item-processed!", :kind "defn-", :line 22, :end-line 25, :hash "-1288155210"} {:id "defn-/coastal-cell?", :kind "defn-", :line 27, :end-line 30, :hash "-153204213"} {:id "defn-/try-set-production", :kind "defn-", :line 32, :end-line 40, :hash "434942381"} {:id "defn-/handle-city-production-key", :kind "defn-", :line 42, :end-line 53, :hash "157331080"} {:id "defn-/calculate-extended-target", :kind "defn-", :line 55, :end-line 65, :hash "-1811725595"} {:id "defn-/launch-fighter-and-update", :kind "defn-", :line 67, :end-line 73, :hash "-1225767676"} {:id "defn-/actions-ctx", :kind "defn-", :line 75, :end-line 81, :hash "-2112578534"} {:id "defn/army-aboard-action", :kind "defn", :line 83, :end-line 89, :hash "1657616300"} {:id "defn-/handle-army-aboard-movement", :kind "defn-", :line 91, :end-line 107, :hash "56145651"} {:id "defn-/undamaged-ship-entering-friendly-city?", :kind "defn-", :line 109, :end-line 116, :hash "429312611"} {:id "defn-/immediate-hostile-city?", :kind "defn-", :line 118, :end-line 119, :hash "-372544577"} {:id "defn-/handle-standard-unit-movement", :kind "defn-", :line 121, :end-line 141, :hash "-1838426353"} {:id "defn-/resolve-direction", :kind "defn-", :line 143, :end-line 147, :hash "-106956789"} {:id "defn-/player-unit?", :kind "defn-", :line 149, :end-line 150, :hash "-530785046"} {:id "defn-/dispatch-movement", :kind "defn-", :line 152, :end-line 157, :hash "385065776"} {:id "defn-/handle-unit-movement-key", :kind "defn-", :line 159, :end-line 171, :hash "1481622101"} {:id "defn/handle-unit-click", :kind "defn", :line 173, :end-line 176, :hash "806630779"} {:id "defn/handle-cell-click", :kind "defn", :line 178, :end-line 181, :hash "1339262717"} {:id "defn/handle-key", :kind "defn", :line 183, :end-line 194, :hash "-517773544"}]}
;; clj-mutate-manifest-end
