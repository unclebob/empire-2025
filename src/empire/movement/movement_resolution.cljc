;; mutation-tested: 2026-02-28
(ns empire.movement.movement-resolution
  (:require [clojure.string]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.combat :as combat]
            [empire.containers.helpers :as uc]
            [empire.movement.movement-execution :as execution]
            [empire.movement.movement-pathing :as pathing]
            [empire.movement.satellite :as satellite]
            [empire.movement.visibility :as visibility]
            [empire.movement.wake-conditions :as wake]
            [empire.units.dispatcher :as dispatcher]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

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
       (dispatcher/can-move-to? (:type unit) (dissoc next-cell :contents))))

(defn- handle-combat
  "Handles combat between unit at from-coords and enemy at next-pos.
   Returns {:result :combat :pos final-pos}."
  [from-coords next-pos cell]
  (let [unit (:contents cell)
        result (combat/attempt-attack from-coords next-pos)]
    (when result
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
        city-cell (get-in (current-world) city-coords)
        updated-city (uc/add-ship-to-shipyard city-cell unit-type (:hits unit))
        updated-origin (dissoc cell :contents)
        type-name (clojure.string/capitalize (name unit-type))]
    (update-game-map! assoc-in from-coords updated-origin)
    (update-game-map! assoc-in city-coords updated-city)
    (visibility/update-cell-visibility city-coords (:owner unit))
    (write-runtime-state! :turn-message (str type-name " docked for repair."))
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
        next-pos (pathing/next-step-pos from-coords target-coords)
        next-cell (get-in @current-map next-pos)]
    (if (uc/ship-can-dock? unit next-cell)
      (dock-ship-for-repair from-coords next-pos cell)
      (let [[woken-unit woke?] (wake/wake-before-move unit next-cell)]
        (handle-movement-result from-coords next-pos target-coords cell unit woken-unit woke? next-cell current-map)))))

(defn set-unit-movement
  ([unit-coords target-coords] (set-unit-movement unit-coords target-coords false))
  ([unit-coords target-coords extended?]
   (let [first-cell (get-in (current-world) unit-coords)
         unit (:contents first-cell)
         actual-target (if (= :satellite (:type unit))
                         (satellite/calculate-satellite-target unit-coords target-coords)
                         target-coords)
         updated-contents (-> unit
                              (assoc :mode :moving :target actual-target)
                              (dissoc :reason :extended)
                              (cond-> extended? (assoc :extended true)))]
     (update-game-map! assoc-in unit-coords (assoc first-cell :contents updated-contents)))))
