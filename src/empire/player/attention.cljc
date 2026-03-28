(ns empire.player.attention
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.player.attention-decisions :as decisions]))

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
  "Sets the message for the current item needing attention."
  [coords]
  (let [world (sa/current-world)
        cell (get-in world coords)
        unit (:contents cell)
        active-unit (movement-state/get-active-unit cell)]
    (sa/write-state! :attention-message
                    (decisions/attention-message
                     {:world world
                      :coords coords
                      :cell cell
                      :unit unit
                      :active-unit active-unit
                      :airport-fighter? (movement-state/is-fighter-from-airport? active-unit)
                      :carrier-fighter? (movement-state/is-fighter-from-carrier? active-unit)
                      :transport-army? (movement-state/is-army-aboard-transport? active-unit)}))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:18:14.085667-05:00", :module-hash "454227381", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "1226809292"} {:id "defn/is-unit-needing-attention?", :kind "defn", :line 6, :end-line 9, :hash "120189784"} {:id "defn/is-city-needing-attention?", :kind "defn", :line 11, :end-line 14, :hash "1790114880"} {:id "defn/needs-attention?", :kind "defn", :line 16, :end-line 23, :hash "-2039746276"} {:id "defn/cells-needing-attention", :kind "defn", :line 25, :end-line 29, :hash "-561589124"} {:id "defn/item-needs-attention?", :kind "defn", :line 31, :end-line 37, :hash "-779795582"} {:id "defn/set-attention-message", :kind "defn", :line 39, :end-line 54, :hash "20172865"}]}
;; clj-mutate-manifest-end
