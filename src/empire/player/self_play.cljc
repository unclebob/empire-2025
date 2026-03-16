(ns empire.player.self-play
  (:require [empire.computer.army :as army]
            [empire.computer.production :as computer-production]
            [empire.game.loop.item-processing.computer-items :as computer-items]
            [empire.state.api :as sa]
            [empire.state.computer :as computer-state]
            [empire.state.world :as world-state]))

(def ^:private computer-scratch-keys
  [:computer-items
   :claimed-objectives
   :claimed-transport-targets
   :claimed-patrol-targets
   :last-transport-city
   :fighter-leg-records
   :computer-city-positions
   :computer-carrier-positions
   :country-stats
   :coastal-cells-by-country
   :coast-walkers-produced
   :opening-satellite-produced?
   :patrol-boats-produced
   :seen-coast
   :land-ho-targets
   :major-invasion-state
   :transport-fully-loaded?
   :early-patrol-boat-produced?
   :early-satellite-produced?
   :computer-event-log
   :distant-city-pairs
   :known-lake-cells])

(defn- swap-side
  [side]
  (case side
    :player :computer
    :computer :player
    side))

(defn- mirror-unit
  [unit]
  (cond-> unit
    (map? unit) (update :owner swap-side)))

(defn- mirror-cell
  [cell]
  (cond-> cell
    (map? cell) (-> (update :city-status swap-side)
                    (update :contents mirror-unit))))

(defn- mirror-grid
  [grid]
  (mapv (fn [row]
          (mapv mirror-cell row))
        grid))

(defn- reset-computer-scratch-state!
  [player-items]
  (let [defaults computer-state/defaults]
    (doseq [k computer-scratch-keys]
      (sa/write-state! k (get defaults k)))
    (sa/write-state! :computer-items (vec player-items))))

(defn- mirrored-player-batch!
  []
  (let [world-before (:game-map @world-state/state)
        player-map-before (:player-map @world-state/state)
        computer-map-before (:computer-map @world-state/state)
        scratch-before (select-keys @computer-state/state computer-scratch-keys)
        player-items-before (vec (sa/read-state :player-items))]
    (try
      (swap! world-state/state assoc
             :game-map (mirror-grid world-before)
             :computer-map (mirror-grid player-map-before)
             :player-map computer-map-before)
      (reset-computer-scratch-state! player-items-before)
      (computer-production/rebuild-country-stats!)
      (army/assign-city-attacks)
      (computer-items/process-computer-items)
      (let [world-after (mirror-grid (:game-map @world-state/state))
            player-items-after (vec (sa/read-state :computer-items))]
        (swap! world-state/state assoc
               :game-map world-after
               :player-map player-map-before
               :computer-map computer-map-before)
        (swap! computer-state/state merge scratch-before)
        (sa/write-state! :player-items player-items-after)
        (sa/write-state! :waiting-for-input false)
        (sa/write-state! :cells-needing-attention []))
      (catch Throwable t
        (swap! world-state/state assoc
               :game-map world-before
               :player-map player-map-before
               :computer-map computer-map-before)
        (swap! computer-state/state merge scratch-before)
        (throw t)))))

(defn process-player-items-batch!
  []
  (when (seq (sa/read-state :player-items))
    (mirrored-player-batch!)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T15:47:23.925634-05:00", :module-hash "-859891549", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-2108896833"} {:id "def/computer-scratch-keys", :kind "def", :line 9, :end-line 31, :hash "1406422622"} {:id "defn-/swap-side", :kind "defn-", :line 33, :end-line 38, :hash "666256868"} {:id "defn-/mirror-unit", :kind "defn-", :line 40, :end-line 43, :hash "955181488"} {:id "defn-/mirror-cell", :kind "defn-", :line 45, :end-line 49, :hash "960528632"} {:id "defn-/mirror-grid", :kind "defn-", :line 51, :end-line 55, :hash "-2101051239"} {:id "defn-/reset-computer-scratch-state!", :kind "defn-", :line 57, :end-line 62, :hash "13225222"} {:id "defn-/mirrored-player-batch!", :kind "defn-", :line 64, :end-line 96, :hash "1494247153"} {:id "defn/process-player-items-batch!", :kind "defn", :line 98, :end-line 101, :hash "-61627655"}]}
;; clj-mutate-manifest-end
