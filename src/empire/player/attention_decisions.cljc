(ns empire.player.attention-decisions
  (:require [empire.game-mechanics.containers.helpers :as uc]))

(defn satellite-with-target?
  [unit]
  (and (= (:type unit) :satellite) (:target unit)))

(defn player-map-cell-needs-attention?
  [cell production-entry]
  (let [unit (:contents cell)
        has-awake-airport-fighter? (uc/has-awake? cell :awake-fighters)
        has-awake-army-aboard? (pos? (:awake-armies unit 0))
        has-awake-carrier-fighter? (and (= (:type unit) :carrier)
                                        (uc/has-awake? unit :awake-fighters))]
    (and (not (satellite-with-target? unit))
         (or (= (:city-status cell) :player)
             (= (:owner unit) :player)
             has-awake-airport-fighter?
             has-awake-carrier-fighter?)
         (or (= (:mode unit) :awake)
             has-awake-airport-fighter?
             has-awake-army-aboard?
             has-awake-carrier-fighter?
             (and (= (:type cell) :city)
                  (not production-entry))))))

(defn world-item-needs-attention?
  [cell production-entry]
  (let [unit (:contents cell)
        player-owned-unit? (= (:owner unit) :player)
        has-awake-airport-fighter? (uc/has-awake? cell :awake-fighters)
        has-awake-army-aboard? (pos? (:awake-armies unit 0))
        has-awake-carrier-fighter? (and (= (:type unit) :carrier)
                                        player-owned-unit?
                                        (uc/has-awake? unit :awake-fighters))]
    (and (not (satellite-with-target? unit))
         (or (and player-owned-unit? (= (:mode unit) :awake))
             has-awake-airport-fighter?
             has-awake-army-aboard?
             has-awake-carrier-fighter?
             (and (= (:type cell) :city)
                  (= (:city-status cell) :player)
                  (not production-entry))))))

(defn attention-coords
  [player-map production]
  (for [i (range (count player-map))
        j (range (count (first player-map)))
        :let [cell (get-in player-map [i j])]
        :when (player-map-cell-needs-attention? cell (production [i j]))]
    [i j]))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T15:49:47.934744-05:00", :module-hash "674983992", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-991786860"} {:id "defn/satellite-with-target?", :kind "defn", :line 4, :end-line 6, :hash "1476000773"} {:id "defn/player-map-cell-needs-attention?", :kind "defn", :line 8, :end-line 25, :hash "-803454248"} {:id "defn/world-item-needs-attention?", :kind "defn", :line 27, :end-line 43, :hash "2023565201"} {:id "defn/attention-coords", :kind "defn", :line 45, :end-line 51, :hash "50561017"}]}
;; clj-mutate-manifest-end
