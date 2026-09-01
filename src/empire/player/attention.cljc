(ns empire.player.attention
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.player.attention-decisions :as decisions]
            [empire.notifications :as notifications]))

(defn is-unit-needing-attention?
  "Returns true if there is an attention-needing unit."
  [attention-coords]
  (decisions/unit-needs-attention? (sa/current-world) attention-coords))

(defn is-city-needing-attention?
  "Returns true if the cell needs city handling as the first attention item."
  [cell clicked-coords attention-coords]
  (decisions/city-needs-attention? cell clicked-coords attention-coords))

(defn needs-attention?
  "Returns true if the cell at [i j] needs attention (awake unit, city with no production, awake airport fighter, carrier with awake fighters, or transport with awake armies).
   Satellites only need attention when they have no target."
  [i j]
  (let [player-map (sa/read-state :player-map)
        production (sa/read-state :production)
        cell (get-in player-map [i j])]
    (decisions/player-map-cell-needs-attention? cell (production [i j]))))

(defn cells-needing-attention
  "Returns coordinates of player's units and cities with no production."
  []
  (let [player-map (sa/read-state :player-map)]
    (decisions/attention-coords player-map (sa/read-state :production))))

(defn item-needs-attention?
  "Returns true if the item at coords needs user input.
   Satellites only need attention when they have no target."
  [coords]
  (let [cell (get-in (sa/current-world) coords)
        production (sa/read-state :production)]
    (decisions/world-item-needs-attention? cell (production coords))))

(defn set-attention-message
  "Sets the message for the current item needing attention.
   If the unit has a reason (e.g. enemy spotted, bingo fuel), writes it as a warning."
  [coords]
  (let [world (sa/current-world)
        cell (get-in world coords)
        unit (:contents cell)
        active-unit (movement-state/get-active-unit cell coords)
        context {:world world
                 :coords coords
                 :cell cell
                 :unit unit
                 :active-unit active-unit
                 :airport-fighter? (movement-state/is-fighter-from-airport? active-unit)
                 :carrier-fighter? (movement-state/is-fighter-from-carrier? active-unit)
                 :transport-army? (movement-state/is-army-aboard-transport? active-unit)}]
    (sa/write-state! :attention-message (decisions/attention-message context))
    (when-let [reason (decisions/attention-reason context)]
      (when (not= reason (sa/read-state :warning-message))
        (notifications/warn! reason)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:03:41.16606-05:00", :module-hash "1224647119", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-1403348952"} {:id "defn/is-unit-needing-attention?", :kind "defn", :line 7, :end-line nil, :hash "120189784"} {:id "defn/is-city-needing-attention?", :kind "defn", :line 12, :end-line nil, :hash "1790114880"} {:id "defn/needs-attention?", :kind "defn", :line 17, :end-line nil, :hash "-2039746276"} {:id "defn/cells-needing-attention", :kind "defn", :line 26, :end-line nil, :hash "-561589124"} {:id "defn/item-needs-attention?", :kind "defn", :line 32, :end-line nil, :hash "-779795582"} {:id "defn/set-attention-message", :kind "defn", :line 40, :end-line nil, :hash "1792886963"}]}
;; clj-mutate-manifest-end
