;; mutation-tested: 2026-02-26
(ns empire.combat
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.combat.escorts :as escorts]
            [empire.config :as config]
            [empire.domain.world.combat :as domain-combat]
            [empire.units.dispatcher :as dispatcher]))

(def ^:private flippable-types
  "Unit types that flip ownership on city conquest (ships and fighters)."
  #{:fighter :transport :patrol-boat :destroyer :submarine :carrier :battleship})

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(def ^:private update-cell-visibility-fn
  (delay
    (try
      (requiring-resolve 'empire.movement.visibility/update-cell-visibility)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- set-game-map!
  [world]
  (app-state/set-world! @state-ctx world))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

(defn- update-runtime-state!
  [k f & args]
  (let [current (read-runtime-state k)
        next-state (apply f current args)]
    (write-runtime-state! k next-state)))

(defn- set-error-message!
  [msg ms]
  (write-runtime-state! :error-message msg)
  (write-runtime-state! :error-until (+ (System/currentTimeMillis) ms)))

(defn- set-turn-message!
  [msg ms]
  (write-runtime-state! :turn-message msg)
  (write-runtime-state! :turn-message-until (if (= ms Long/MAX_VALUE)
                                               Long/MAX_VALUE
                                               (+ (System/currentTimeMillis) ms))))

(defn- update-cell-visibility!
  [pos owner]
  (when-let [f @update-cell-visibility-fn]
    (f pos owner)))

(defn- conquer-city-contents-world
  [game-map city-coords new-owner]
  (let [cell (get-in game-map city-coords)
        contents (:contents cell)
        with-updated-contents
        (cond
          (nil? contents) game-map
          (= :satellite (:type contents)) game-map
          (= :army (:type contents))
          (update-in game-map city-coords dissoc :contents)
          (flippable-types (:type contents))
          (let [flipped (-> contents
                            (assoc :owner new-owner :mode :awake)
                            (dissoc :target :reason))
                flipped (cond-> flipped
                          (= :transport (:type flipped))
                          (assoc :army-count 0 :awake-armies 0)
                          (= :carrier (:type flipped))
                          (assoc :fighter-count 0 :awake-fighters 0))]
            (assoc-in game-map (conj city-coords :contents) flipped))
          :else game-map)]
    (update-in with-updated-contents city-coords dissoc :marching-orders :flight-path)))

(defn conquer-city-contents
  "Handles units at a conquered city per VMS Empire rules:
   - Armies are killed
   - Satellites are left unchanged
   - Ships and fighters flip ownership, wake up, clear orders
  - Transported armies and carried fighters are killed
  - City production and standing orders are cleared"
  [city-coords new-owner]
  (set-game-map! (conquer-city-contents-world (current-world) city-coords new-owner))
  ;; Clear production
  (update-runtime-state! :production dissoc city-coords)
  nil)

(defn hostile-city? [target-coords]
  (let [target-cell (get-in (current-world) target-coords)]
    (and (= (:type target-cell) :city)
         (config/hostile-city? (:city-status target-cell)))))

(defn attempt-city-conquest
  "Rolls for city conquest. On success, converts city to player and conquers contents.
   On failure, shows failure message. Returns true regardless of outcome."
  [city-coords]
  (let [city-cell (get-in (current-world) city-coords)]
    (if (< (rand) 0.5)
      (do
        (when (= :computer (:city-status city-cell))
          (update-runtime-state! :computer-city-positions disj city-coords))
        (update-game-map! assoc-in city-coords (assoc city-cell :city-status :player))
        (conquer-city-contents city-coords :player)
        (update-runtime-state! :computer-carrier-positions disj city-coords)
        (update-cell-visibility! city-coords :player)
        (update-runtime-state! :computer-map assoc-in (conj city-coords :city-status) :player))
      (set-error-message! (:conquest-failed config/messages) config/error-message-duration))
    true))

(defn attempt-conquest
  "Attempts to conquer a city with an army. Returns true if conquest was attempted."
  [army-coords city-coords]
  (let [army-cell (get-in (current-world) army-coords)]
    (update-game-map! assoc-in army-coords (dissoc army-cell :contents))
    (update-cell-visibility! army-coords :player)
    (attempt-city-conquest city-coords)))

(defn- apply-fighter-overfly-world
  [game-map fighter-coords city-coords shot-down-fighter]
  (let [fighter-cell (get-in game-map fighter-coords)
        city-cell (get-in game-map city-coords)]
    (-> game-map
        (assoc-in fighter-coords (dissoc fighter-cell :contents))
        (assoc-in city-coords (assoc city-cell :contents shot-down-fighter)))))

(defn attempt-fighter-overfly
  "Fighter flies over hostile city and gets shot down."
  [fighter-coords city-coords]
  (let [fighter-cell (get-in (current-world) fighter-coords)
        fighter (:contents fighter-cell)
        shot-down-fighter (assoc fighter :mode :awake :hits 0 :steps-remaining 0 :reason :fighter-shot-down)]
    (set-game-map! (apply-fighter-overfly-world (current-world) fighter-coords city-coords shot-down-fighter))
    (set-error-message! (:fighter-destroyed-by-city config/messages) config/error-message-duration)
    true))

(defn hostile-unit?
  "Returns true if the unit is hostile to the given owner."
  [unit owner]
  (and unit (not= (:owner unit) owner)))

(defn format-combat-log
  "Formats a combat log for display.
   Format: c-3,S-1,S-1. Submarine destroyed."
  [log attacker-type defender-type winner]
  (domain-combat/format-combat-log log attacker-type defender-type winner))

(defn format-combat-status
  "Formats combat status for the game status line."
  [log attacker-type defender-type winner]
  (domain-combat/format-combat-status log attacker-type defender-type winner))

(defn fight-round
  "Executes one round of combat. 50% chance attacker hits, 50% chance defender hits.
   Returns [updated-attacker updated-defender log-entry]."
  [attacker defender]
  (domain-combat/fight-round attacker defender))

(defn resolve-combat
  "Fights combat rounds until one unit dies.
   Returns {:winner :attacker|:defender :survivor unit-map :log [log-entries]}."
  [attacker defender]
  (domain-combat/resolve-combat attacker defender))

(defn dead-escort-destroyer?
  [dead-unit]
  (escorts/dead-escort-destroyer? dead-unit))

(defn dead-escort-transport?
  [dead-unit]
  (escorts/dead-escort-transport? dead-unit))

(defn clear-escort-on-death
  "When a unit with escort pairing is destroyed, clear the partner's reference."
  [dead-unit]
  (escorts/clear-escort-on-death!
   {:current-world current-world
    :update-game-map! update-game-map!}
   dead-unit))

(defn- drown-excess-cargo
  "After combat, if a container's cargo exceeds its effective capacity, kill excess."
  [coords survivor]
  (when (#{:transport :carrier} (:type survivor))
    (let [cap (dispatcher/effective-capacity (:type survivor) (:hits survivor))
          [count-key awake-key] (if (= :transport (:type survivor))
                                  [:army-count :awake-armies]
                                  [:fighter-count :awake-fighters])
          current-count (get survivor count-key 0)
          excess (- current-count cap)]
      (when (pos? excess)
        (let [current-awake (get survivor awake-key 0)
              new-awake (min current-awake cap)]
          (update-game-map! update-in (conj coords :contents)
                            assoc count-key cap awake-key new-awake))))))

(defn- apply-attack-world
  [game-map attacker-coords target-coords survivor]
  (-> game-map
      (assoc-in (conj attacker-coords :contents) nil)
      (assoc-in (conj target-coords :contents) survivor)))

(defn attempt-attack
  "Attempts to attack an enemy unit at target-coords from attacker-coords.
   Returns true if attack was attempted, false otherwise."
  [attacker-coords target-coords]
  (let [world (current-world)
        attacker-cell (get-in world attacker-coords)
        target-cell (get-in world target-coords)
        attacker (:contents attacker-cell)
        defender (:contents target-cell)]
    (if (or (nil? attacker)
            (nil? defender)
            (= :satellite (:type attacker))
            (= :satellite (:type defender))
            (not (hostile-unit? defender (:owner attacker))))
      false
      (let [result (resolve-combat attacker defender)
            message (format-combat-status (:log result)
                                          (:type attacker)
                                          (:type defender)
                                          (:winner result))
            dead-unit (if (= :attacker (:winner result)) defender attacker)]
        (set-game-map! (apply-attack-world world attacker-coords target-coords (:survivor result)))
        ;; Drown excess cargo if surviving container took damage
        (drown-excess-cargo target-coords (:survivor result))
        ;; Clear escort pairing if destroyer or transport died
        (clear-escort-on-death dead-unit)
        (set-turn-message! message Long/MAX_VALUE)
        true))))
