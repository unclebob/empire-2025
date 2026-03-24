(ns empire.computer.fighter.action-resolution
  (:require [empire.computer.fighter.movement-decisions :as decisions]
            [empire.computer.shared.grid :as grid]
            [empire.config.core :as config]
            [empire.game-mechanics.debug.logging :as debug]
            [empire.game-mechanics.visibility :as visibility]
            [empire.game-mechanics.services.combat :as combat]
            [empire.state.api :as sa]))

(defn attack-enemy
  [fighter-pos enemy-pos attackable-enemy-cell?]
  (let [world (sa/read-state :computer-map)
        {:keys [attacker defender attackable?]} (decisions/attack-context world
                                                                          fighter-pos
                                                                          enemy-pos
                                                                          attackable-enemy-cell?)]
    (when attackable?
      (let [result (combat/resolve-combat attacker defender)]
        (sa/update-world! update-in fighter-pos dissoc :contents)
        (case (decisions/attack-result-action (:winner result))
          :occupy-target
          (do
            (sa/update-world! assoc-in (conj enemy-pos :contents) (:survivor result))
            (visibility/update-cell-visibility fighter-pos :computer)
            (visibility/update-cell-visibility enemy-pos :computer)
            enemy-pos)
          (do
            (visibility/update-cell-visibility fighter-pos :computer)
            nil))))))

(defn land-at-city
  [pos city-pos]
  (let [fighter (get-in (sa/read-state :computer-map) (conj pos :contents))]
    (sa/update-world! update-in pos dissoc :contents)
    (sa/update-world! update-in (conj city-pos :fighter-count) (fnil inc 0))
    (when (:kamikazee fighter)
      (sa/update-world! update-in (conj city-pos :kamikazee-fighter-count) (fnil inc 0))
      (sa/update-world! update-in (conj city-pos :awake-kamikazee-fighters) (fnil inc 0)))
    (sa/update-world! update-in (conj city-pos :awake-fighters) (fnil inc 0))
    (visibility/update-cell-visibility pos :computer)
    :landed))

(defn consume-fighter-fuel
  [pos]
  (let [unit (get-in (sa/read-state :computer-map) (conj pos :contents))
        target-site (:flight-target-site unit)
        landing-site (:explore-landing-site unit)
        target-cell (when target-site (get-in (sa/current-world) target-site))
        landing-cell (when landing-site (get-in (sa/current-world) landing-site))
        crash-details {:target-site target-site
                       :target-cell-type (:type target-cell)
                       :target-city-status (:city-status target-cell)
                       :target-distance (when target-site (grid/distance pos target-site))
                       :landing-site landing-site
                       :landing-cell-type (:type landing-cell)
                       :landing-city-status (:city-status landing-cell)
                       :landing-distance (when landing-site (grid/distance pos landing-site))}
        action (decisions/fuel-action unit config/fighter-fuel)]
    (case (:action action)
      :invalid false
      :destroy (do (when (debug/computer-unit-logging-enabled?)
                     (debug/log-computer-event! :fighter-crash pos (merge {:reason :fuel
                                                                           :computer-unit-id (:computer-unit-id unit)
                                                                           :flight-mode (:flight-mode unit)
                                                                           :flight-target-site (:flight-target-site unit)}
                                                                          crash-details))
                     (debug/log-computer-unit-crash! pos unit :fuel crash-details))
                   (sa/update-world! update-in pos dissoc :contents)
                   (visibility/update-cell-visibility pos :computer)
                   false)
      :update-fuel (do
                     (sa/update-world! assoc-in (conj pos :contents :fuel) (:fuel action))
                     (visibility/sync-ai-unit-to-computer-map! pos)
                     true)
      false)))
