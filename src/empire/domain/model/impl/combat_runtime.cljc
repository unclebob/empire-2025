;; mutation-tested: no
(ns empire.domain.model.impl.combat-runtime
  (:require [empire.application.runtime :as app-runtime]
            [empire.combat :as combat]
            [empire.combat.escorts :as escorts]
            [empire.config :as config]
            [empire.domain.model.combat :as domain-combat]
            [empire.movement.visibility :as visibility]
            [empire.units.dispatcher :as dispatcher]))

(defonce ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(def ^:private flippable-types
  "Unit types that flip ownership on city conquest (ships and fighters)."
  #{:fighter :transport :patrol-boat :destroyer :submarine :carrier :battleship})

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- set-game-map!
  [world]
  ((:save-world! @state-ctx) world))

(defn- update-game-map!
  [f & args]
  (let [world (current-world)]
    (set-game-map! (apply f world args))))

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
  (visibility/update-cell-visibility pos owner))

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

(defmethod combat/conquer-city-contents :default
  [city-coords new-owner]
  (set-game-map! (conquer-city-contents-world (current-world) city-coords new-owner))
  (update-runtime-state! :production dissoc city-coords)
  nil)

(defmethod combat/hostile-city? :default
  [target-coords]
  (let [target-cell (get-in (current-world) target-coords)]
    (and (= (:type target-cell) :city)
         (config/hostile-city? (:city-status target-cell)))))

(defmethod combat/attempt-city-conquest :default
  [city-coords]
  (let [city-cell (get-in (current-world) city-coords)]
    (if (< (rand) 0.5)
      (do
        (when (= :computer (:city-status city-cell))
          (update-runtime-state! :computer-city-positions disj city-coords))
        (update-game-map! assoc-in city-coords (assoc city-cell :city-status :player))
        (combat/conquer-city-contents city-coords :player)
        (update-runtime-state! :computer-carrier-positions disj city-coords)
        (update-cell-visibility! city-coords :player)
        (update-runtime-state! :computer-map assoc-in (conj city-coords :city-status) :player))
      (set-error-message! (:conquest-failed config/messages) config/error-message-duration))
    true))

(defmethod combat/attempt-conquest :default
  [army-coords city-coords]
  (let [army-cell (get-in (current-world) army-coords)]
    (update-game-map! assoc-in army-coords (dissoc army-cell :contents))
    (update-cell-visibility! army-coords :player)
    (combat/attempt-city-conquest city-coords)))

(defn- apply-fighter-overfly-world
  [game-map fighter-coords city-coords shot-down-fighter]
  (let [fighter-cell (get-in game-map fighter-coords)
        city-cell (get-in game-map city-coords)]
    (-> game-map
        (assoc-in fighter-coords (dissoc fighter-cell :contents))
        (assoc-in city-coords (assoc city-cell :contents shot-down-fighter)))))

(defmethod combat/attempt-fighter-overfly :default
  [fighter-coords city-coords]
  (let [fighter-cell (get-in (current-world) fighter-coords)
        fighter (:contents fighter-cell)
        shot-down-fighter (assoc fighter :mode :awake :hits 0 :steps-remaining 0 :reason :fighter-shot-down)]
    (set-game-map! (apply-fighter-overfly-world (current-world) fighter-coords city-coords shot-down-fighter))
    (set-error-message! (:fighter-destroyed-by-city config/messages) config/error-message-duration)
    true))

(defmethod combat/hostile-unit? :default
  [unit owner]
  (and unit (not= (:owner unit) owner)))

(defmethod combat/format-combat-log :default
  [log attacker-type defender-type winner]
  (domain-combat/format-combat-log log attacker-type defender-type winner))

(defmethod combat/format-combat-status :default
  [log attacker-type defender-type winner]
  (domain-combat/format-combat-status log attacker-type defender-type winner))

(defmethod combat/fight-round :default
  [attacker defender]
  (domain-combat/fight-round attacker defender))

(defmethod combat/resolve-combat :default
  [attacker defender]
  (domain-combat/resolve-combat attacker defender))

(defmethod combat/dead-escort-destroyer? :default
  [dead-unit]
  (escorts/dead-escort-destroyer? dead-unit))

(defmethod combat/dead-escort-transport? :default
  [dead-unit]
  (escorts/dead-escort-transport? dead-unit))

(defmethod combat/clear-escort-on-death :default
  [dead-unit]
  (escorts/clear-escort-on-death!
   {:current-world current-world
    :update-game-map! update-game-map!}
   dead-unit))

(defn- drown-excess-cargo
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

(defmethod combat/attempt-attack :default
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
            (not (combat/hostile-unit? defender (:owner attacker))))
      false
      (let [result (combat/resolve-combat attacker defender)
            message (combat/format-combat-status (:log result)
                                                 (:type attacker)
                                                 (:type defender)
                                                 (:winner result))
            dead-unit (if (= :attacker (:winner result)) defender attacker)]
        (set-game-map! (apply-attack-world world attacker-coords target-coords (:survivor result)))
        (drown-excess-cargo target-coords (:survivor result))
        (combat/clear-escort-on-death dead-unit)
        (set-turn-message! message Long/MAX_VALUE)
        true))))

(defn load-methods!
  []
  true)
