(ns empire.player.commands
  "Pure command dispatch for player attention items.
   Handles key input when units/cities need attention. No Quil dependency."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.game-mechanics.movement.api :as movement-api]
            [empire.config.core :as config]
            [empire.player.attention :as attention]
            [empire.player.command-decisions :as decisions]
            [empire.player.commands-action-decisions :as action-decisions]
            [empire.player.movement-decisions :as movement-decisions]
            [empire.player.commands-actions :as actions]
            [empire.game-mechanics.services.combat :as combat]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.player.production :as production]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.sound :as sound]))

(defn- set-warning-message!
  [msg]
  (sa/write-state! :warning-message msg)
  (sound/play-bonk!))

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
        naval? (dispatcher/naval-units item)
        action (action-decisions/city-production-action {:naval? naval?
                                                         :coastal? coastal?
                                                         :item item})]
    (case (:action action)
      :reject-production
      (set-warning-message! (format "Must be coastal city to produce %s." (name item)))
      :set-production
      (do
        (production/set-city-production coords item)
        (item-processed!)))
    true))

(defn- handle-city-production-decision [decision coords]
  (case (:action decision)
    :skip (do (sa/update-state! :player-items rest)
              (item-processed!)
              true)
    :clear-production (do (sa/update-state! :production assoc coords :none)
                          (item-processed!)
                          true)
    :set-production (try-set-production coords (:item decision))
    nil))

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

(defn- requeue-airport? [coords]
  (let [cell (get-in (sa/current-world) coords)]
    (and (= :city (:type cell))
         (uc/has-awake? cell :awake-fighters))))

(defn- launch-fighter-and-update [launch-fn coords target]
  (let [fighter-pos (launch-fn coords target)]
    (sa/write-state! :waiting-for-input false)
    (sa/write-state! :attention-message "")
    (sa/write-state! :cells-needing-attention [])
    (sa/update-state! :player-items
                      #(let [remaining (rest %)]
                         (if (requeue-airport? coords)
                           (cons fighter-pos (cons coords remaining))
                           (cons fighter-pos remaining))))
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

(defn- coastal-army-attack? [extended? coords adjacent-target active-unit]
  (let [target-cell (get-in (sa/current-world) adjacent-target)
        target-unit (:contents target-cell)]
    (and (not extended?)
         (= :army (:type active-unit))
         (= :sea (:type target-cell))
         (coastal-cell? coords)
         (combat/hostile-unit? target-unit (:owner active-unit)))))

(defn- standard-movement-action [coords adjacent-target extended? active-unit]
  (movement-decisions/standard-movement-action
   (:type active-unit)
   extended?
   (immediate-hostile-city? extended? adjacent-target)
   (coastal-army-attack? extended? coords adjacent-target active-unit)
   (undamaged-ship-entering-friendly-city? active-unit adjacent-target)))

(defn- handle-standard-unit-movement [coords adjacent-target target extended? active-unit]
  (case (standard-movement-action coords adjacent-target extended? active-unit)
    :coastal-army-attack (do (combat/apply-combat-result! (combat/attempt-coastal-army-attack (sa/current-world) coords adjacent-target))
                             (item-processed!)
                             true)
    :army-conquest (do (combat/apply-combat-result! (combat/attempt-conquest (sa/current-world) coords adjacent-target))
                       (item-processed!)
                       true)
    :fighter-overfly (do (combat/apply-combat-result! (combat/attempt-fighter-overfly (sa/current-world) coords adjacent-target))
                         (item-processed!)
                         true)
    :reject-undamaged-ship (do (set-warning-message! "Ship not damaged, entry denied.")
                               true)
    (do (movement-api/set-unit-movement coords target)
        (item-processed!)
        true)))

(defn- dispatch-movement [context coords adjacent-target target extended? target-cell active-unit _cell]
  (case (action-decisions/movement-context-action context)
    :launch-airport-fighter (launch-fighter-and-update container-ops/launch-fighter-from-airport coords target)
    :launch-carrier-fighter (launch-fighter-and-update container-ops/launch-fighter-from-carrier coords target)
    :army-aboard-movement (handle-army-aboard-movement coords adjacent-target target extended? target-cell)
    :standard-unit-movement (handle-standard-unit-movement coords adjacent-target target extended? active-unit)))

(defn- handle-unit-movement-decision [decision coords cell active-unit]
  (let [{:keys [direction extended?]} decision
        [x y] coords
        [dx dy] direction
        adjacent-target [(+ x dx) (+ y dy)]
        target-cell (get-in (sa/current-world) adjacent-target)
        target (if extended?
                 (calculate-extended-target coords direction)
                 adjacent-target)]
    (dispatch-movement (movement-state/movement-context cell active-unit)
                       coords adjacent-target target extended? target-cell active-unit cell)))

(defn- apply-unit-decision [decision coords cell active-unit]
  (case (:action decision)
    :skip (actions/handle-space-key (actions-ctx) coords)
    :unload (actions/handle-unload-key (actions-ctx) coords cell active-unit)
    :sentry (actions/handle-sentry-key (actions-ctx) coords cell active-unit)
    :look-around (actions/handle-look-around-key (actions-ctx) coords cell active-unit)
    :move (handle-unit-movement-decision decision coords cell active-unit)
    nil))

(defn- apply-city-decision [decision coords]
  (case (:action decision)
    :skip (handle-city-production-decision decision coords)
    :clear-production (handle-city-production-decision decision coords)
    :set-production (handle-city-production-decision decision coords)
    nil))

(defn- apply-key-decision [decision coords cell active-unit]
  (if active-unit
    (apply-unit-decision decision coords cell active-unit)
    (apply-city-decision decision coords)))

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
          active-unit (movement-state/get-active-unit cell coords)
          decision (decisions/attention-key-action k cell active-unit)]
      (apply-key-decision decision coords cell active-unit))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-07T19:29:40.637292-05:00", :module-hash "-1340822174", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 19, :hash "-246558338"} {:id "defn-/set-warning-message!", :kind "defn-", :line 21, :end-line 24, :hash "409180286"} {:id "defn-/item-processed!", :kind "defn-", :line 26, :end-line 29, :hash "-1288155210"} {:id "defn-/coastal-cell?", :kind "defn-", :line 31, :end-line 34, :hash "1443832154"} {:id "defn-/try-set-production", :kind "defn-", :line 36, :end-line 49, :hash "26758432"} {:id "defn-/handle-city-production-decision", :kind "defn-", :line 51, :end-line 60, :hash "358021842"} {:id "defn-/calculate-extended-target", :kind "defn-", :line 62, :end-line 72, :hash "-1811725595"} {:id "defn-/requeue-airport?", :kind "defn-", :line 74, :end-line 77, :hash "-2106957752"} {:id "defn-/launch-fighter-and-update", :kind "defn-", :line 79, :end-line 89, :hash "-1832701050"} {:id "defn-/actions-ctx", :kind "defn-", :line 91, :end-line 97, :hash "-2112578534"} {:id "defn/army-aboard-action", :kind "defn", :line 99, :end-line 105, :hash "1657616300"} {:id "defn-/handle-army-aboard-movement", :kind "defn-", :line 107, :end-line 123, :hash "56145651"} {:id "defn-/undamaged-ship-entering-friendly-city?", :kind "defn-", :line 125, :end-line 132, :hash "429312611"} {:id "defn-/immediate-hostile-city?", :kind "defn-", :line 134, :end-line 135, :hash "-372544577"} {:id "defn-/coastal-army-attack?", :kind "defn-", :line 137, :end-line 144, :hash "-663531133"} {:id "defn-/standard-movement-action", :kind "defn-", :line 146, :end-line 152, :hash "-1506381564"} {:id "defn-/handle-standard-unit-movement", :kind "defn-", :line 154, :end-line 169, :hash "-973285127"} {:id "defn-/dispatch-movement", :kind "defn-", :line 171, :end-line 176, :hash "-1399098674"} {:id "defn-/handle-unit-movement-decision", :kind "defn-", :line 178, :end-line 188, :hash "-1410438608"} {:id "defn-/apply-unit-decision", :kind "defn-", :line 190, :end-line 197, :hash "-1662391742"} {:id "defn-/apply-city-decision", :kind "defn-", :line 199, :end-line 204, :hash "-248221435"} {:id "defn-/apply-key-decision", :kind "defn-", :line 206, :end-line 209, :hash "-343068248"} {:id "defn/handle-unit-click", :kind "defn", :line 211, :end-line 214, :hash "806630779"} {:id "defn/handle-cell-click", :kind "defn", :line 216, :end-line 219, :hash "1339262717"} {:id "defn/handle-key", :kind "defn", :line 221, :end-line 226, :hash "-1396998960"}]}
;; clj-mutate-manifest-end
