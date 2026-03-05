(ns empire.combat
  (:require [empire.application.state-access :as sa]
            [empire.combat.escorts :as escorts]
            [empire.config :as config]
            [empire.domain.model.combat :as domain-combat]
            [empire.movement.visibility :as visibility]
            [empire.units.dispatcher :as dispatcher]))

(defn- set-error-message!
  [msg ms]
  (sa/write-state! :error-message msg)
  (sa/write-state! :error-until (+ (System/currentTimeMillis) ms)))

(defn- set-turn-message!
  [msg ms]
  (sa/write-state! :turn-message msg)
  (sa/write-state! :turn-message-until (if (= ms Long/MAX_VALUE)
                                          Long/MAX_VALUE
                                          (+ (System/currentTimeMillis) ms))))

(defn- update-cell-visibility!
  [pos owner]
  (visibility/update-cell-visibility pos owner))

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
          (sa/update-world! update-in (conj coords :contents)
                            assoc count-key cap awake-key new-awake))))))


(defn format-combat-log
  [log attacker-type defender-type winner]
  (domain-combat/format-combat-log log attacker-type defender-type winner))

(defn format-combat-status
  [log attacker-type defender-type winner]
  (domain-combat/format-combat-status log attacker-type defender-type winner))

(defn fight-round
  [attacker defender]
  (domain-combat/fight-round attacker defender))

(defn resolve-combat
  [attacker defender]
  (domain-combat/resolve-combat attacker defender))

(defn dead-escort-destroyer?
  [dead-unit]
  (escorts/dead-escort-destroyer? dead-unit))

(defn dead-escort-transport?
  [dead-unit]
  (escorts/dead-escort-transport? dead-unit))

(defn clear-escort-on-death
  [dead-unit]
  (escorts/clear-escort-on-death!
    {:current-world sa/current-world
     :update-game-map! sa/update-world!}
    dead-unit))

(defn conquer-city-contents
  [city-coords new-owner]
  (sa/update-world! (fn [w] (domain-combat/conquer-city-contents-world w city-coords new-owner)))
  (sa/update-state! :production dissoc city-coords)
  nil)

(defn hostile-city?
  [world target-coords]
  (domain-combat/hostile-city? world target-coords))

(defn attempt-city-conquest
  [world city-coords]
  (let [city-cell (get-in world city-coords)]
    (if (< (rand) 0.5)
      (do
        (when (= :computer (:city-status city-cell))
          (sa/update-state! :computer-city-positions disj city-coords))
        (sa/update-world! assoc-in city-coords (assoc city-cell :city-status :player))
        (conquer-city-contents city-coords :player)
        (sa/update-state! :computer-carrier-positions disj city-coords)
        (update-cell-visibility! city-coords :player)
        (sa/update-state! :computer-map assoc-in (conj city-coords :city-status) :player))
      (set-error-message! (:conquest-failed config/messages) config/error-message-duration))
    true))

(defn attempt-conquest
  [world army-coords city-coords]
  (let [army-cell (get-in world army-coords)]
    (sa/update-world! assoc-in army-coords (dissoc army-cell :contents))
    (update-cell-visibility! army-coords :player)
    (attempt-city-conquest (sa/current-world) city-coords)))

(defn attempt-fighter-overfly
  [world fighter-coords city-coords]
  (let [fighter-cell (get-in world fighter-coords)
        fighter (:contents fighter-cell)
        shot-down-fighter (assoc fighter :mode :awake :hits 0 :steps-remaining 0 :reason :fighter-shot-down)]
    (sa/update-world! (fn [w] (domain-combat/apply-fighter-overfly-world w fighter-coords city-coords shot-down-fighter)))
    (set-error-message! (:fighter-destroyed-by-city config/messages) config/error-message-duration)
    true))

(defn hostile-unit?
  [unit owner]
  (domain-combat/hostile-unit? unit owner))

(defn attempt-attack
  [world attacker-coords target-coords]
  (let [attacker-cell (get-in world attacker-coords)
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
        (sa/update-world! (fn [w] (domain-combat/apply-attack-world w attacker-coords target-coords (:survivor result))))
        (drown-excess-cargo target-coords (:survivor result))
        (clear-escort-on-death dead-unit)
        (set-turn-message! message Long/MAX_VALUE)
        true))))
