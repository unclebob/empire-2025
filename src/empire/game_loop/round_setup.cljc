(ns empire.game-loop.round-setup
  "Round initialization: satellite moves, fuel consumption, sentry waking,
   dead unit removal, repair, step resets."
  (:require [empire.application.state-access :as sa]
            [empire.movement.services :as movement-services]
            [empire.config :as config]
            [empire.domain.services.round-setup :as domain-round-setup]
            [empire.game-loop.round-setup.fuel :as fuel]
            [empire.game-loop.round-setup.lakes :as lakes]
            [empire.game-loop.round-setup.repair :as repair]
            [empire.game-loop.round-setup.satellites :as satellites]
            [empire.game-loop.round-setup.waking :as waking]
            [empire.units.dispatcher :as dispatcher]))

(defn dead-unit? [contents]
  (domain-round-setup/dead-unit? contents))

(defn computer-carrier? [contents]
  (domain-round-setup/computer-carrier? contents))

(defn remove-dead-units
  "Removes units with hits at or below zero."
  []
  (let [world (sa/current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                contents (:contents cell)]
            :when (dead-unit? contents)]
      (when (computer-carrier? contents)
        (sa/update-state! :computer-carrier-positions disj [i j]))
      (sa/update-world! assoc-in [i j] (dissoc cell :contents))
      (movement-services/update-cell-visibility [i j] (:owner contents)))))

(defn reset-steps-remaining
  "Resets steps-remaining for all player units at start of round."
  []
  (let [world (sa/current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                unit (:contents cell)]
            :when (and unit (= (:owner unit) :player))]
      (let [steps (or (dispatcher/effective-speed (:type unit) (:hits unit)) 1)]
        (sa/update-world! assoc-in [i j :contents :steps-remaining] steps)))))

(defn move-satellites
  "Moves all satellites according to their speed.
   Removes satellites with turns-remaining at or below zero."
  []
  (satellites/move-satellites!
   {:current-world sa/current-world
    :update-game-map! sa/update-world!
    :update-visibility! movement-services/update-cell-visibility
    :move-satellite movement-services/move-satellite
    :satellite-speed (config/unit-speed :satellite)}))

;; Delegated to sub-modules
(def wake-airport-fighters waking/wake-airport-fighters)
(def wake-carrier-fighters waking/wake-carrier-fighters)
(def wake-sentries-seeing-enemy waking/wake-sentries-seeing-enemy)
(def consume-sentry-fighter-fuel fuel/consume-sentry-fighter-fuel)
(def evacuate-lake-patrol-boats lakes/evacuate-lake-patrol-boats)
(def mark-lake-locked-ships lakes/mark-lake-locked-ships)
(def repair-damaged-ships repair/repair-damaged-ships)
