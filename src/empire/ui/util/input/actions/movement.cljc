(ns empire.ui.util.input.actions.movement
  (:require [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.game-mechanics.movement.api :as movement-api]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.game-mechanics.services.combat :as combat]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.player.command-decisions :as decisions]
            [empire.player.movement-decisions :as movement-decisions]
            [empire.player.commands :as player-commands]
            [empire.ui.util.input.actions.helpers :as helpers]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.config.units.dispatcher :as dispatcher]))

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

(defn- coastal-army-attack-action [coords active-unit adjacent-target extended?]
  (let [target-cell (get-in (sa/current-world) adjacent-target)]
    (when (and (not extended?)
               (= :army (:type active-unit))
               (= :sea (:type target-cell))
               (map-utils/on-coast? (first coords) (second coords))
               (combat/hostile-unit? (:contents target-cell) (:owner active-unit)))
      :coastal-army-attack)))

(defn- standard-movement-action [coords active-unit adjacent-target extended?]
  (movement-decisions/standard-movement-action
   (:type active-unit)
   extended?
   (and (not extended?) (combat/hostile-city? (sa/current-world) adjacent-target))
   (boolean (coastal-army-attack-action coords active-unit adjacent-target extended?))
   (undamaged-ship-entering-friendly-city? active-unit adjacent-target)))

(defn- perform-standard-movement! [action coords adjacent-target target extended?]
  (case action
    :coastal-army-attack (combat/apply-combat-result! (combat/attempt-coastal-army-attack (sa/current-world) coords adjacent-target))
    :army-conquest (combat/apply-combat-result! (combat/attempt-conquest (sa/current-world) coords adjacent-target))
    :fighter-overfly (combat/apply-combat-result! (combat/attempt-fighter-overfly (sa/current-world) coords adjacent-target))
    :reject-undamaged-ship (helpers/set-error-message! "Ship not damaged, entry denied." config/error-message-duration)
    :normal-move (movement-api/set-unit-movement coords target extended?))
  (when (not= :reject-undamaged-ship action)
    (helpers/item-processed!))
  true)

(defn- handle-standard-unit-movement [coords adjacent-target target extended? active-unit]
  (-> (standard-movement-action coords active-unit adjacent-target extended?)
      (perform-standard-movement! coords adjacent-target target extended?)))

(defn- execute-unit-movement [coords direction extended? active-unit cell]
  (let [[x y] coords
        [dx dy] direction
        adjacent-target [(+ x dx) (+ y dy)]
        target-cell (get-in (sa/current-world) adjacent-target)
        target (if extended?
                 (calculate-extended-target coords direction)
                 adjacent-target)
        context (movement-state/movement-context cell active-unit)]
    (case context
      :airport-fighter (launch-fighter-and-update container-ops/launch-fighter-from-airport coords target)
      :carrier-fighter (launch-fighter-and-update container-ops/launch-fighter-from-carrier coords target)
      :army-aboard (handle-army-aboard-movement coords adjacent-target target extended? target-cell)
      :standard-unit (handle-standard-unit-movement coords adjacent-target target extended? active-unit))))

(defn handle-unit-movement-decision [decision coords cell]
  (let [active-unit (movement-state/get-active-unit cell)]
    (when (and active-unit
               (= (:owner active-unit) :player))
      (execute-unit-movement coords (:direction decision) (:extended? decision) active-unit cell))))

(defn handle-unit-movement-key [k coords cell]
  (when-let [decision (decisions/movement-decision k)]
    (handle-unit-movement-decision decision coords cell)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:50:58.182839-05:00", :module-hash "1715491489", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 13, :hash "-261330217"} {:id "defn-/calculate-extended-target", :kind "defn-", :line 15, :end-line 25, :hash "-1811725595"} {:id "defn-/launch-fighter-and-update", :kind "defn-", :line 27, :end-line 33, :hash "-19380899"} {:id "defn/army-aboard-action", :kind "defn", :line 35, :end-line 36, :hash "1245189332"} {:id "defn-/handle-army-aboard-movement", :kind "defn-", :line 38, :end-line 48, :hash "-1114819059"} {:id "defn-/undamaged-ship-entering-friendly-city?", :kind "defn-", :line 50, :end-line 57, :hash "429312611"} {:id "defn-/coastal-army-attack-action", :kind "defn-", :line 59, :end-line 66, :hash "-1772006669"} {:id "defn-/standard-movement-action", :kind "defn-", :line 68, :end-line 74, :hash "-1196078700"} {:id "defn-/perform-standard-movement!", :kind "defn-", :line 76, :end-line 85, :hash "-1373878146"} {:id "defn-/handle-standard-unit-movement", :kind "defn-", :line 87, :end-line 89, :hash "2064052240"} {:id "defn-/execute-unit-movement", :kind "defn-", :line 91, :end-line 104, :hash "-1610568678"} {:id "defn/handle-unit-movement-decision", :kind "defn", :line 106, :end-line 110, :hash "-68039860"} {:id "defn/handle-unit-movement-key", :kind "defn", :line 112, :end-line 114, :hash "-365692720"}]}
;; clj-mutate-manifest-end
