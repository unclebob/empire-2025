;; mutation-tested: 2026-02-28
(ns empire.game-mechanics.movement.movement-resolution
  (:require [clojure.string]
            [empire.game-mechanics.services.combat :as combat]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.state.api :as sa]
            [empire.game-mechanics.movement.map-utils :as map-utils]
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
  (if (and (vector? target-coords)
           (= 2 (count target-coords))
           (every? number? target-coords))
    (clamp-to-map-bounds target-coords)
    from-coords))

(defn- blocked-by-friendly?
  "Returns true if the next cell contains a friendly unit (same owner)."
  [unit next-cell]
  (let [blocker (:contents next-cell)]
    (and blocker
         (= (:owner blocker) (:owner unit)))))

(defn- blocked-by-enemy?
  "Returns true if the next cell contains an enemy unit (different owner)."
  [unit next-cell]
  (let [blocker (:contents next-cell)]
    (and blocker
         (not= (:owner blocker) (:owner unit)))))

(defn- can-attack-enemy?
  "Returns true if unit can attack the enemy in next-cell.
   The unit must be able to occupy the terrain (ignoring the enemy)."
  [unit next-cell]
  (and (blocked-by-enemy? unit next-cell)
       (not= :satellite (:type unit))
       (not= :satellite (get-in next-cell [:contents :type]))
       (dispatcher/can-move-to? (:type unit) (dissoc next-cell :contents))))

(defn- handle-combat
  "Handles combat between unit at from-coords and enemy at next-pos.
   Returns {:result :combat :pos final-pos}."
  [from-coords next-pos cell]
  (let [unit (:contents cell)
        result (combat/attempt-attack (current-world) from-coords next-pos)]
    (when result
      (combat/apply-combat-result! result)
      (visibility/update-cell-visibility next-pos (:owner unit)))
    {:result :combat :pos next-pos}))

(defn- should-sidestep-city?
  "Returns true if unit should sidestep around the city in next-cell.
   Armies sidestep friendly cities. Fighters sidestep all cities except their target."
  [unit next-cell next-pos]
  (when (= :city (:type next-cell))
    (cond
      (and (= :army (:type unit))
           (= :player (:city-status next-cell)))
      true

      (and (= :fighter (:type unit))
           (not= next-pos (:target unit)))
      true

      :else false)))

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
        {:result :sidestep :pos sidestep-pos})
      (let [updated-cell (assoc cell :contents woken-unit)]
        (update-game-map! assoc-in from-coords updated-cell)
        (visibility/update-cell-visibility from-coords (:owner unit))
        {:result :woke :pos from-coords}))))

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
    {:result :docked :pos city-coords}))

(defn- woke-and-blocked? [woke? woken-unit]
  (and woke? (= (:reason woken-unit) :somethings-in-the-way)))

(defn- handle-movement-result
  "Handles movement after wake-before-move check. Determines whether to
   sidestep, engage in combat, wake up, or proceed with normal movement."
  [from-coords next-pos target-coords cell unit woken-unit woke? next-cell current-map]
  (let [blocked? (woke-and-blocked? woke? woken-unit)]
    (cond
      (should-sidestep-city? unit next-cell next-pos)
      (try-sidestep from-coords next-pos target-coords cell (wake-unit-for-city unit) current-map)

      (and blocked? (blocked-by-friendly? unit next-cell))
      (try-sidestep from-coords next-pos target-coords cell woken-unit current-map)

      (and blocked? (can-attack-enemy? unit next-cell))
      (handle-combat from-coords next-pos cell)

      woke?
      (let [updated-cell (assoc cell :contents woken-unit)]
        (update-game-map! assoc-in from-coords updated-cell)
        (visibility/update-cell-visibility from-coords (:owner unit))
        {:result :woke :pos from-coords})

      :else
      (let [final-unit (wake/wake-after-move unit from-coords next-pos current-map)]
        (execution/do-move from-coords next-pos cell final-unit)
        {:result :normal :pos next-pos}))))

(defn move-unit
  "Moves a unit one step toward target. Returns a map with:
   :result - :normal, :sidestep, :woke, :combat, or :docked
   :pos - the new position (or original if woke)"
  [from-coords target-coords cell current-map]
  (let [unit (:contents cell)
        safe-target (normalize-target from-coords target-coords)
        safe-unit (if (= (:target unit) safe-target)
                    unit
                    (assoc unit :target safe-target))
        safe-cell (if (= safe-unit unit)
                    cell
                    (assoc cell :contents safe-unit))
        next-pos (pathing/next-step-pos from-coords safe-target)
        next-cell (get-in (map-utils/resolve-map-source current-map) next-pos)]
    (if (uc/ship-can-dock? safe-unit next-cell)
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
