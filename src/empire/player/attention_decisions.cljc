(ns empire.player.attention-decisions
  (:require [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.config.core :as config]
            [empire.config.domain.core.unit-metrics :as unit-metrics]))

(defn satellite-with-target?
  [unit]
  (and (= (:type unit) :satellite) (:target unit)))

(defn- has-airport-fighter?
  [cell]
  (and (pos? (:fighter-count cell 0))
       (pos? (:awake-fighters cell 0))))

(defn- has-awake-carrier-fighter?
  [unit]
  (and (= (:type unit) :carrier)
       (uc/has-awake? unit :awake-fighters)))

(defn- player-owned-attention?
  [cell unit has-airport-fighter? has-awake-carrier-fighter?]
  (or (= (:city-status cell) :player)
      (= (:owner unit) :player)
      has-airport-fighter?
      has-awake-carrier-fighter?))

(defn- awake-or-pending-attention?
  [cell unit production-entry has-airport-fighter? has-awake-army-aboard? has-awake-carrier-fighter?]
  (or (= (:mode unit) :awake)
      has-airport-fighter?
      has-awake-army-aboard?
      has-awake-carrier-fighter?
      (and (= (:type cell) :city)
           (not production-entry))))

(defn player-map-cell-needs-attention?
  [cell production-entry]
  (let [unit (:contents cell)
        airport? (has-airport-fighter? cell)
        army-aboard? (pos? (:awake-armies unit 0))
        carrier-fighter? (has-awake-carrier-fighter? unit)]
    (and (not (satellite-with-target? unit))
         (player-owned-attention? cell unit airport? carrier-fighter?)
         (awake-or-pending-attention? cell unit production-entry airport? army-aboard? carrier-fighter?))))

(defn- world-city-needs-production?
  [cell production-entry]
  (and (= (:type cell) :city)
       (= (:city-status cell) :player)
       (not production-entry)))

(defn world-item-needs-attention?
  [cell production-entry]
  (let [unit (:contents cell)
        player-owned-unit? (= (:owner unit) :player)
        airport? (has-airport-fighter? cell)
        army-aboard? (pos? (:awake-armies unit 0))
        carrier-fighter? (and (has-awake-carrier-fighter? unit) player-owned-unit?)]
    (and (not (satellite-with-target? unit))
         (or (and player-owned-unit? (= (:mode unit) :awake))
             airport?
             army-aboard?
             carrier-fighter?
             (world-city-needs-production? cell production-entry)))))

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

(defn active-unit-reason
  [world coords active-unit]
  (let [reason-key (or (:reason active-unit)
                       (when (army-adjacent-to-enemy-city? world coords active-unit)
                         :army-found-city))]
    (reason-string reason-key)))

(defn active-unit-attention-message
  [world coords active-unit]
  (let [unit-type (:type active-unit)
        unit-name (name unit-type)
        max-hits (config/item-hits unit-type)
        current-hits (:hits active-unit max-hits)
        damage-prefix (if (< current-hits max-hits) "Damaged " "")]
    (str damage-prefix unit-name
         (or (cargo-string unit-type active-unit) "")
         (or (ship-hits-string active-unit) "")
         (or (fuel-string active-unit) ""))))

(defn attention-message
  [{:keys [world coords cell unit active-unit airport-fighter? carrier-fighter? transport-army?]}]
  (cond
    airport-fighter?
    (str "Fighter (" (:fighter-count cell 0) " in airport)"
         (fuel-string active-unit))

    carrier-fighter?
    (str "Fighter - aboard carrier (" (:fighter-count unit 0) " fighters)"
         (fuel-string active-unit))

    transport-army?
    (str "Army - aboard transport (" (:army-count unit 0) " armies)")

    active-unit
    (active-unit-attention-message world coords active-unit)

    :else
    "City"))

(defn attention-reason
  "Returns the reason string for the current attention item, or nil."
  [{:keys [world coords active-unit transport-army?]}]
  (cond
    transport-army?
    (:transport-at-beach config/messages)

    active-unit
    (active-unit-reason world coords active-unit)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:31:43.974857-05:00", :module-hash "2065819894", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "1909230051"} {:id "defn/satellite-with-target?", :kind "defn", :line 7, :end-line nil, :hash "1476000773"} {:id "defn-/has-airport-fighter?", :kind "defn-", :line 11, :end-line nil, :hash "-1430395428"} {:id "defn-/has-awake-carrier-fighter?", :kind "defn-", :line 16, :end-line nil, :hash "-1135860689"} {:id "defn-/player-owned-attention?", :kind "defn-", :line 21, :end-line nil, :hash "1463956352"} {:id "defn-/awake-or-pending-attention?", :kind "defn-", :line 28, :end-line nil, :hash "-102095167"} {:id "defn/player-map-cell-needs-attention?", :kind "defn", :line 37, :end-line nil, :hash "2019532858"} {:id "defn-/world-city-needs-production?", :kind "defn-", :line 47, :end-line nil, :hash "-1476304166"} {:id "defn/world-item-needs-attention?", :kind "defn", :line 53, :end-line nil, :hash "18489983"} {:id "defn/attention-coords", :kind "defn", :line 67, :end-line nil, :hash "50561017"} {:id "defn/city-needs-attention?", :kind "defn", :line 75, :end-line nil, :hash "18197332"} {:id "defn/unit-needs-attention?", :kind "defn", :line 81, :end-line nil, :hash "-1818493375"} {:id "defn-/cargo-string", :kind "defn-", :line 91, :end-line nil, :hash "-531062870"} {:id "defn-/reason-string", :kind "defn-", :line 98, :end-line nil, :hash "-270678909"} {:id "defn-/fuel-string", :kind "defn-", :line 105, :end-line nil, :hash "-835329319"} {:id "defn-/ship-hits-string", :kind "defn-", :line 110, :end-line nil, :hash "67812064"} {:id "defn-/army-adjacent-to-enemy-city?", :kind "defn-", :line 118, :end-line nil, :hash "1478541614"} {:id "defn/active-unit-reason", :kind "defn", :line 129, :end-line nil, :hash "-2126377059"} {:id "defn/active-unit-attention-message", :kind "defn", :line 136, :end-line nil, :hash "1645431333"} {:id "defn/attention-message", :kind "defn", :line 148, :end-line nil, :hash "1166901536"} {:id "defn/attention-reason", :kind "defn", :line 168, :end-line nil, :hash "1598836539"}]}
;; clj-mutate-manifest-end
