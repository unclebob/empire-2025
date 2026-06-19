(ns empire.ui.util.input.actions.movement
  (:require [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.game-mechanics.movement.api :as movement-api]
            [empire.state.api :as sa]
            [empire.game-mechanics.services.combat :as combat]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.player.command-decisions :as decisions]
            [empire.player.movement-decisions :as movement-decisions]
            [empire.player.movement-support :as movement-support]
            [empire.player.commands :as player-commands]
            [empire.ui.util.input.actions.helpers :as helpers]
            [empire.game-mechanics.movement.map-utils :as map-utils]))

(defn- calculate-extended-target [coords [dx dy]]
  (movement-support/calculate-extended-target (sa/current-world) coords [dx dy]))

(defn- launch-fighter-and-update [launch-fn coords target]
  (movement-support/launch-fighter-and-update!
   sa/current-world
   sa/write-state!
   sa/update-state!
   launch-fn
   coords
   target))

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
   (movement-support/undamaged-ship-entering-friendly-city?
    (sa/current-world)
    active-unit
    adjacent-target)))

(defn- apply-combat-action!
  [combat-action coords adjacent-target]
  (combat/apply-combat-result! (combat-action (sa/current-world) coords adjacent-target))
  (helpers/item-processed!))

(defn- reject-undamaged-ship!
  [_coords _adjacent-target _target _extended?]
  (helpers/set-warning-message! "Ship not damaged, entry denied."))

(defn- normal-move!
  [coords _adjacent-target target extended?]
  (movement-api/set-unit-movement coords target extended?)
  (helpers/item-processed!))

(def ^:private standard-movement-handlers
  {:coastal-army-attack (fn [coords adjacent-target _target _extended?]
                          (apply-combat-action! combat/attempt-coastal-army-attack coords adjacent-target))
   :army-conquest (fn [coords adjacent-target _target _extended?]
                    (apply-combat-action! combat/attempt-conquest coords adjacent-target))
   :fighter-overfly (fn [coords adjacent-target _target _extended?]
                      (apply-combat-action! combat/attempt-fighter-overfly coords adjacent-target))
   :reject-undamaged-ship reject-undamaged-ship!
   :normal-move normal-move!})

(defn- perform-standard-movement! [action coords adjacent-target target extended?]
  ((standard-movement-handlers action) coords adjacent-target target extended?)
  true)

(defn- handle-standard-unit-movement [coords adjacent-target target extended? active-unit]
  (-> (standard-movement-action coords active-unit adjacent-target extended?)
      (perform-standard-movement! coords adjacent-target target extended?)))

(defn- movement-targets
  [coords direction extended?]
  (let [[x y] coords
        [dx dy] direction
        adjacent-target [(+ x dx) (+ y dy)]
        target (if extended?
                 (calculate-extended-target coords direction)
                 adjacent-target)]
    {:adjacent-target adjacent-target
     :target target
     :target-cell (get-in (sa/current-world) adjacent-target)}))

(defn- launch-airport-fighter
  [coords _adjacent-target target _extended? _target-cell _active-unit]
  (launch-fighter-and-update container-ops/launch-fighter-from-airport coords target))

(defn- launch-carrier-fighter
  [coords _adjacent-target target _extended? _target-cell _active-unit]
  (launch-fighter-and-update container-ops/launch-fighter-from-carrier coords target))

(defn- move-army-aboard
  [coords adjacent-target target extended? target-cell _active-unit]
  (handle-army-aboard-movement coords adjacent-target target extended? target-cell))

(defn- move-standard-unit
  [coords adjacent-target target extended? _target-cell active-unit]
  (handle-standard-unit-movement coords adjacent-target target extended? active-unit))

(def ^:private movement-context-handlers
  {:airport-fighter launch-airport-fighter
   :carrier-fighter launch-carrier-fighter
   :army-aboard move-army-aboard
   :standard-unit move-standard-unit})

(defn- execute-unit-movement [coords direction extended? active-unit cell]
  (let [{:keys [adjacent-target target target-cell]} (movement-targets coords direction extended?)
        context (movement-state/movement-context cell active-unit)]
    ((movement-context-handlers context)
     coords adjacent-target target extended? target-cell active-unit)))

(defn handle-unit-movement-decision [decision coords cell]
  (let [active-unit (movement-state/get-active-unit cell)]
    (when (and active-unit
               (= (:owner active-unit) :player))
      (execute-unit-movement coords (:direction decision) (:extended? decision) active-unit cell))))

(defn handle-unit-movement-key [k coords cell]
  (when-let [decision (decisions/movement-decision k)]
    (handle-unit-movement-decision decision coords cell)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-06-19T12:42:30.127137-05:00", :module-hash "1492965923", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 12, :hash "-853472811"} {:id "defn-/calculate-extended-target", :kind "defn-", :line 14, :end-line 15, :hash "-1478819494"} {:id "defn-/launch-fighter-and-update", :kind "defn-", :line 17, :end-line 24, :hash "-1791763626"} {:id "defn/army-aboard-action", :kind "defn", :line 26, :end-line 27, :hash "1245189332"} {:id "defn-/handle-army-aboard-movement", :kind "defn-", :line 29, :end-line 39, :hash "-1114819059"} {:id "defn-/coastal-army-attack-action", :kind "defn-", :line 41, :end-line 48, :hash "-1772006669"} {:id "defn-/standard-movement-action", :kind "defn-", :line 50, :end-line 59, :hash "-633111157"} {:id "defn-/apply-combat-action!", :kind "defn-", :line 61, :end-line 64, :hash "789834498"} {:id "defn-/reject-undamaged-ship!", :kind "defn-", :line 66, :end-line 68, :hash "-1580919542"} {:id "defn-/normal-move!", :kind "defn-", :line 70, :end-line 73, :hash "-714581933"} {:id "def/standard-movement-handlers", :kind "def", :line 75, :end-line 83, :hash "-56003273"} {:id "defn-/perform-standard-movement!", :kind "defn-", :line 85, :end-line 87, :hash "-2032959916"} {:id "defn-/handle-standard-unit-movement", :kind "defn-", :line 89, :end-line 91, :hash "2064052240"} {:id "defn-/movement-targets", :kind "defn-", :line 93, :end-line 103, :hash "-442073001"} {:id "defn-/launch-airport-fighter", :kind "defn-", :line 105, :end-line 107, :hash "73404825"} {:id "defn-/launch-carrier-fighter", :kind "defn-", :line 109, :end-line 111, :hash "-867935410"} {:id "defn-/move-army-aboard", :kind "defn-", :line 113, :end-line 115, :hash "-618392245"} {:id "defn-/move-standard-unit", :kind "defn-", :line 117, :end-line 119, :hash "-1720312155"} {:id "def/movement-context-handlers", :kind "def", :line 121, :end-line 125, :hash "1776958680"} {:id "defn-/execute-unit-movement", :kind "defn-", :line 127, :end-line 131, :hash "-218448543"} {:id "defn/handle-unit-movement-decision", :kind "defn", :line 133, :end-line 137, :hash "-68039860"} {:id "defn/handle-unit-movement-key", :kind "defn", :line 139, :end-line 141, :hash "-365692720"}]}
;; clj-mutate-manifest-end
