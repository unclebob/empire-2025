(ns empire.game-mechanics.movement.movement-resolution
  (:require [clojure.string]
            [empire.game-mechanics.services.combat :as combat]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.state.api :as sa]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.movement.movement-resolution-decisions :as decisions]
            [empire.game-mechanics.movement.movement-execution :as execution]
            [empire.game-mechanics.movement.movement-pathing :as pathing]
            [empire.game-mechanics.movement.satellite :as satellite]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.game-mechanics.movement.wake-conditions :as wake]
            [empire.config.units.dispatcher :as dispatcher]))

(defn- update-game-map!
  [f & args]
  (apply sa/update-world! f args))

(defn- current-world
  []
  (sa/current-world))

(defn- write-runtime-state!
  [k v]
  (sa/write-state! k v))

(defn- clamp-to-map-bounds
  "Clamps [x y] to the current map bounds."
  [[x y]]
  (let [world (current-world)
        cols (count world)
        rows (count (first world))
        max-x (dec cols)
        max-y (dec rows)]
    [(-> x (max 0) (min max-x))
     (-> y (max 0) (min max-y))]))

(defn- normalize-target
  "Returns a safe in-bounds target. Falls back to from-coords when target is missing/malformed."
  [from-coords target-coords]
  (decisions/normalize-target clamp-to-map-bounds from-coords target-coords))

(defn- blocked-by-friendly?
  "Returns true if the next cell contains a friendly unit (same owner)."
  [unit next-cell]
  (decisions/blocked-by-friendly? unit next-cell))

(defn- blocked-by-enemy?
  "Returns true if the next cell contains an enemy unit (different owner)."
  [unit next-cell]
  (decisions/blocked-by-enemy? unit next-cell))

(defn- can-attack-enemy?
  "Returns true if unit can attack the enemy in next-cell.
   The unit must be able to occupy the terrain (ignoring the enemy)."
  [unit next-cell]
  (decisions/can-attack-enemy? blocked-by-enemy? dispatcher/can-move-to? unit next-cell))

(defn- handle-combat
  "Handles combat between unit at from-coords and enemy at next-pos.
   Returns {:result :combat :pos final-pos}."
  [from-coords next-pos cell]
  (let [unit (:contents cell)
        result (combat/attempt-attack (current-world) from-coords next-pos)]
    (when result
      (combat/apply-combat-result! result)
      (when-let [{:keys [pos owner]} (decisions/combat-visibility-pos unit next-pos result)]
        (visibility/update-cell-visibility pos owner)))
    (decisions/movement-result :combat next-pos)))

(defn- should-sidestep-city?
  "Returns true if unit should sidestep around the city in next-cell.
   Armies sidestep friendly cities. Fighters sidestep all cities except their target."
  [unit next-cell next-pos]
  (decisions/should-sidestep-city? unit next-cell next-pos))

(defn- get-blocked-direction
  "Returns the direction [dx dy] from pos to next-pos."
  [[x y] [nx ny]]
  [(- nx x) (- ny y)])

(defn- try-sidestep
  "Attempts to sidestep around a blocked cell. Returns {:result :sidestep :pos new-pos}
   if successful, or {:result :woke :pos from-coords} if no valid sidestep exists."
  [from-coords next-pos target-coords cell woken-unit current-map]
  (let [unit (:contents cell)
        blocked-dir (get-blocked-direction from-coords next-pos)
        sidestep-pos (pathing/find-best-sidestep from-coords target-coords (:type unit) blocked-dir current-map)]
    (if sidestep-pos
      (let [final-unit (wake/wake-after-move unit from-coords sidestep-pos current-map)]
        (execution/do-move from-coords sidestep-pos cell final-unit)
        (decisions/movement-result :sidestep sidestep-pos))
      (let [updated-cell (assoc cell :contents woken-unit)]
        (update-game-map! assoc-in from-coords updated-cell)
        (visibility/update-cell-visibility from-coords (:owner unit))
        (decisions/movement-result :woke from-coords)))))

(defn- wake-unit-for-city [unit]
  "Creates a woken unit with appropriate reason for city blocking."
  (let [reason (if (= :army (:type unit)) :cant-move-into-city :fighter-over-defended-city)]
    (assoc (dissoc (assoc unit :mode :awake) :target) :reason reason)))

(defn- dock-ship-for-repair
  "Docks a damaged ship into a friendly city's shipyard.
   Removes ship from origin cell and adds to city's shipyard."
  [from-coords city-coords cell]
  (let [unit (:contents cell)
        unit-type (:type unit)
        current-hits (:hits unit)
        max-hits (dispatcher/hits unit-type)
        city-cell (get-in (current-world) city-coords)
        updated-city (uc/add-ship-to-shipyard city-cell unit-type (:hits unit))
        updated-origin (dissoc cell :contents)
        type-name (clojure.string/capitalize (name unit-type))]
    (update-game-map! assoc-in from-coords updated-origin)
    (update-game-map! assoc-in city-coords updated-city)
    (visibility/update-cell-visibility city-coords (:owner unit))
    (write-runtime-state! :turn-message (format (:docked-for-repair config/messages)
                                                type-name
                                                current-hits
                                                max-hits))
    (decisions/movement-result :docked city-coords)))

(defn- woke-and-blocked? [woke? woken-unit]
  (and woke? (= (:reason woken-unit) :somethings-in-the-way)))

(defn- handle-movement-result
  "Handles movement after wake-before-move check. Determines whether to
   sidestep, engage in combat, wake up, or proceed with normal movement."
  [from-coords next-pos target-coords cell unit woken-unit woke? next-cell current-map]
  (let [blocked? (woke-and-blocked? woke? woken-unit)]
    (case (decisions/movement-action
           {:sidestep-city? (should-sidestep-city? unit next-cell next-pos)
            :blocked? blocked?
            :blocked-by-friendly? (blocked-by-friendly? unit next-cell)
            :can-attack-enemy? (can-attack-enemy? unit next-cell)
            :woke? woke?})
      :sidestep-city
      (try-sidestep from-coords next-pos target-coords cell (wake-unit-for-city unit) current-map)

      :sidestep-friendly
      (try-sidestep from-coords next-pos target-coords cell woken-unit current-map)

      :combat
      (handle-combat from-coords next-pos cell)

      :woke
      (let [updated-cell (assoc cell :contents woken-unit)]
        (update-game-map! assoc-in from-coords updated-cell)
        (visibility/update-cell-visibility from-coords (:owner unit))
        (decisions/movement-result :woke from-coords))

      :normal
      (let [final-unit (wake/wake-after-move unit from-coords next-pos current-map)]
        (execution/do-move from-coords next-pos cell final-unit)
        (decisions/movement-result :normal next-pos)))))

(defn move-unit
  "Moves a unit one step toward target. Returns a map with:
   :result - :normal, :sidestep, :woke, :combat, or :docked
   :pos - the new position (or original if woke)"
  [from-coords target-coords cell current-map]
  (let [unit (:contents cell)
        safe-target (normalize-target from-coords target-coords)
        safe-unit (decisions/target-unit-state unit safe-target)
        safe-cell (decisions/target-cell-state cell safe-unit)
        next-pos (pathing/next-step-pos from-coords safe-target)
        next-cell (get-in (map-utils/resolve-map-source current-map) next-pos)]
    (case (decisions/move-unit-phase
           {:ship-can-dock? (uc/ship-can-dock? safe-unit next-cell)})
      :dock
      (dock-ship-for-repair from-coords next-pos safe-cell)
      (let [[woken-unit woke?] (wake/wake-before-move safe-unit next-cell)]
        (handle-movement-result from-coords next-pos safe-target safe-cell safe-unit woken-unit woke? next-cell current-map)))))

(defn set-unit-movement
  ([unit-coords target-coords] (set-unit-movement unit-coords target-coords false))
  ([unit-coords target-coords extended?]
   (let [first-cell (get-in (current-world) unit-coords)
         unit (:contents first-cell)
         actual-target (-> (if (= :satellite (:type unit))
                             (satellite/calculate-satellite-target unit-coords target-coords)
                             target-coords)
                           clamp-to-map-bounds)
         updated-contents (-> unit
                              (assoc :mode :moving :target actual-target)
                              (dissoc :reason :extended)
                              (cond-> extended? (assoc :extended true)))]
     (update-game-map! assoc-in unit-coords (assoc first-cell :contents updated-contents)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T14:46:12.042993-05:00", :module-hash "474524223", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 14, :hash "1565855507"} {:id "defn-/update-game-map!", :kind "defn-", :line 16, :end-line 18, :hash "1805137569"} {:id "defn-/current-world", :kind "defn-", :line 20, :end-line 22, :hash "-640438772"} {:id "defn-/write-runtime-state!", :kind "defn-", :line 24, :end-line 26, :hash "1105581680"} {:id "defn-/clamp-to-map-bounds", :kind "defn-", :line 28, :end-line 37, :hash "1663727491"} {:id "defn-/normalize-target", :kind "defn-", :line 39, :end-line 42, :hash "1079930249"} {:id "defn-/blocked-by-friendly?", :kind "defn-", :line 44, :end-line 47, :hash "-1760303407"} {:id "defn-/blocked-by-enemy?", :kind "defn-", :line 49, :end-line 52, :hash "-1822856815"} {:id "defn-/can-attack-enemy?", :kind "defn-", :line 54, :end-line 58, :hash "-966012541"} {:id "defn-/handle-combat", :kind "defn-", :line 60, :end-line 70, :hash "1209332123"} {:id "defn-/should-sidestep-city?", :kind "defn-", :line 72, :end-line 76, :hash "522749880"} {:id "defn-/get-blocked-direction", :kind "defn-", :line 78, :end-line 81, :hash "-997753313"} {:id "defn-/try-sidestep", :kind "defn-", :line 83, :end-line 97, :hash "-1907502546"} {:id "defn-/wake-unit-for-city", :kind "defn-", :line 99, :end-line 102, :hash "-828913488"} {:id "defn-/dock-ship-for-repair", :kind "defn-", :line 104, :end-line 123, :hash "-1783696714"} {:id "defn-/woke-and-blocked?", :kind "defn-", :line 125, :end-line 126, :hash "1414423656"} {:id "defn-/handle-movement-result", :kind "defn-", :line 128, :end-line 157, :hash "-963629566"} {:id "defn/move-unit", :kind "defn", :line 159, :end-line 175, :hash "-862461006"} {:id "defn/set-unit-movement", :kind "defn", :line 177, :end-line 190, :hash "-1991903948"}]}
;; clj-mutate-manifest-end
