(ns empire.player.attention-decisions
  (:require [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.config.core :as config]
            [empire.config.domain.core.unit-metrics :as unit-metrics]))

(defn satellite-with-target?
  [unit]
  (and (= (:type unit) :satellite) (:target unit)))

(defn player-map-cell-needs-attention?
  [cell production-entry]
  (let [unit (:contents cell)
        has-airport-fighter? (and (pos? (:fighter-count cell 0))
                                  (pos? (:awake-fighters cell 0)))
        has-awake-army-aboard? (pos? (:awake-armies unit 0))
        has-awake-carrier-fighter? (and (= (:type unit) :carrier)
                                        (uc/has-awake? unit :awake-fighters))]
    (and (not (satellite-with-target? unit))
         (or (= (:city-status cell) :player)
             (= (:owner unit) :player)
             has-airport-fighter?
             has-awake-carrier-fighter?)
         (or (= (:mode unit) :awake)
             has-airport-fighter?
             has-awake-army-aboard?
             has-awake-carrier-fighter?
             (and (= (:type cell) :city)
                  (not production-entry))))))

(defn world-item-needs-attention?
  [cell production-entry]
  (let [unit (:contents cell)
        player-owned-unit? (= (:owner unit) :player)
        has-airport-fighter? (and (pos? (:fighter-count cell 0))
                                  (pos? (:awake-fighters cell 0)))
        has-awake-army-aboard? (pos? (:awake-armies unit 0))
        has-awake-carrier-fighter? (and (= (:type unit) :carrier)
                                        player-owned-unit?
                                        (uc/has-awake? unit :awake-fighters))]
    (and (not (satellite-with-target? unit))
         (or (and player-owned-unit? (= (:mode unit) :awake))
             has-airport-fighter?
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

(defn city-needs-attention?
  [cell clicked-coords attention-coords]
  (and (= (:city-status cell) :player)
       (= (:type cell) :city)
       (= clicked-coords (first attention-coords))))

(defn unit-needs-attention?
  [world attention-coords]
  (and (seq attention-coords)
       (let [first-cell (get-in world (first attention-coords))
             unit (:contents first-cell)]
         (or unit
             (and (pos? (:fighter-count first-cell 0))
                  (pos? (:awake-fighters first-cell 0)))
             (pos? (:awake-armies unit 0))))))

(defn- cargo-string
  [unit-type unit]
  (case unit-type
    :transport (str " (" (:army-count unit 0) " armies)")
    :carrier (str " (" (:fighter-count unit 0) " fighters)")
    nil))

(defn- reason-string
  [reason-key]
  (when reason-key
    (if (string? reason-key)
      reason-key
      (reason-key config/messages))))

(defn- fuel-string
  [active-unit]
  (when (= :fighter (:type active-unit))
    (str " (fuel:" (:fuel active-unit) ")")))

(defn- ship-hits-string
  [active-unit]
  (let [unit-type (:type active-unit)]
    (when (unit-metrics/naval-unit? unit-type)
      (let [max-hits (config/item-hits unit-type)
            current-hits (:hits active-unit max-hits)]
        (str " (hits:" current-hits "/" max-hits ")")))))

(defn- army-adjacent-to-enemy-city?
  [world coords active-unit]
  (and (= :army (:type active-unit))
       (let [[ax ay] coords]
         (some (fn [[di dj]]
                 (let [adj-cell (get-in world [(+ ax di) (+ ay dj)])]
                   (and adj-cell
                        (= (:type adj-cell) :city)
                        (config/hostile-city? (:city-status adj-cell)))))
               map-utils/neighbor-offsets))))

(defn active-unit-attention-message
  [world coords active-unit]
  (let [unit-type (:type active-unit)
        unit-name (name unit-type)
        max-hits (config/item-hits unit-type)
        current-hits (:hits active-unit max-hits)
        damage-prefix (if (< current-hits max-hits) "Damaged " "")
        cargo-str (cargo-string unit-type active-unit)
        reason-key (or (:reason active-unit)
                       (when (army-adjacent-to-enemy-city? world coords active-unit)
                         :army-found-city))
        reason-str (reason-string reason-key)]
    (str damage-prefix unit-name (:unit-needs-attention config/messages)
         (or cargo-str "")
         (or (ship-hits-string active-unit) "")
         (if reason-str (str " - " reason-str) "")
         (or (fuel-string active-unit) ""))))

(defn attention-message
  [{:keys [world coords cell unit active-unit airport-fighter? carrier-fighter? transport-army?]}]
  (cond
    airport-fighter?
    (str "Fighter" (:unit-needs-attention config/messages)
         " (" (:fighter-count cell 0) " in airport)"
         (fuel-string active-unit))

    carrier-fighter?
    (str "Fighter" (:unit-needs-attention config/messages)
         " - aboard carrier (" (:fighter-count unit 0) " fighters)"
         (fuel-string active-unit))

    transport-army?
    (str "Army" (:unit-needs-attention config/messages)
         " - aboard transport (" (:army-count unit 0) " armies) - "
         (:transport-at-beach config/messages))

    active-unit
    (active-unit-attention-message world coords active-unit)

    :else
    (:city-needs-attention config/messages)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:16:54.702614-05:00", :module-hash "-281489083", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "1909230051"} {:id "defn/satellite-with-target?", :kind "defn", :line 7, :end-line 9, :hash "1476000773"} {:id "defn/player-map-cell-needs-attention?", :kind "defn", :line 11, :end-line 28, :hash "-803454248"} {:id "defn/world-item-needs-attention?", :kind "defn", :line 30, :end-line 46, :hash "2023565201"} {:id "defn/attention-coords", :kind "defn", :line 48, :end-line 54, :hash "50561017"} {:id "defn/city-needs-attention?", :kind "defn", :line 56, :end-line 60, :hash "18197332"} {:id "defn/unit-needs-attention?", :kind "defn", :line 62, :end-line 69, :hash "263615096"} {:id "defn-/cargo-string", :kind "defn-", :line 71, :end-line 76, :hash "-531062870"} {:id "defn-/reason-string", :kind "defn-", :line 78, :end-line 83, :hash "-270678909"} {:id "defn-/fuel-string", :kind "defn-", :line 85, :end-line 88, :hash "-835329319"} {:id "defn-/ship-hits-string", :kind "defn-", :line 90, :end-line 96, :hash "67812064"} {:id "defn-/army-adjacent-to-enemy-city?", :kind "defn-", :line 98, :end-line 107, :hash "1478541614"} {:id "defn/active-unit-attention-message", :kind "defn", :line 109, :end-line 125, :hash "-488882600"} {:id "defn/attention-message", :kind "defn", :line 127, :end-line 149, :hash "-490587600"}]}
;; clj-mutate-manifest-end
