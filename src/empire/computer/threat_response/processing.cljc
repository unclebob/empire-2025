;; mutation-tested: no
(ns empire.computer.threat-response.processing
  "Threat mission execution helpers extracted from threat-response coordinator."
  (:require [empire.computer.core :as core]
            [empire.computer.fighter-movement :as fm]
            [empire.computer.ship-core :as ship-core]
            [empire.config :as config]))

(defn- move-hop-consume
  [pos target]
  (when-let [hop (fm/hop-over-friendly pos target)]
    (when-let [{:keys [pos hops]} (fm/execute-hop pos hop)]
      (when (fm/consume-fighter-fuel pos)
        {:pos pos :steps-used hops}))))

(defn- attack-threat-step
  [pos enemy]
  (when-let [new-pos (fm/attack-enemy pos enemy)]
    (when (fm/consume-fighter-fuel new-pos)
      {:pos new-pos :steps-used 1})))

(defn- refuel-at-adjacent-site
  [ctx pos site]
  (if (= :city (:type (get-in ((:current-world ctx)) site)))
    (do (fm/land-at-city pos site) nil)
    (do ((:update-game-map! ctx) assoc-in (conj pos :contents :fuel) config/fighter-fuel)
        {:pos pos :steps-used 1})))

(defn- refuel-threat-step
  [ctx pos]
  (when-let [site (fm/find-nearest-refueling-site pos)]
    (if (<= (fm/distance-to pos site) 1)
      (refuel-at-adjacent-site ctx pos site)
      (move-hop-consume pos site))))

(defn- out-of-threat-radius?
  [pos center radius]
  (and center (> (core/distance pos center) radius)))

(defn- patrol-threat-step
  [pos center radius]
  (when-let [{:keys [pos hops]} (fm/do-patrol pos)]
    (when (fm/consume-fighter-fuel pos)
      (if (out-of-threat-radius? pos center radius)
        (move-hop-consume pos center)
        {:pos pos :steps-used hops}))))

(defn fighter-step-threat
  [ctx pos unit]
  (let [center (:threat-center unit)
        radius (:threat-radius unit (:threat-radius ctx))
        fuel (:fuel unit config/fighter-fuel)
        enemy (fm/find-adjacent-enemy pos)]
    (cond
      enemy
      (attack-threat-step pos enemy)

      (fm/should-return-to-refuel? pos fuel)
      (refuel-threat-step ctx pos)

      (out-of-threat-radius? pos center radius)
      (move-hop-consume pos center)

      :else
      (patrol-threat-step pos center radius))))

(defn process-fighter-threat
  "Overrides regular fighter logic while fighter-sweep threat mission is active.
   Returns true when handled."
  [ctx pos unit]
  (when (= :fighter-sweep (:threat-mission unit))
    (loop [current pos
           remaining fm/fighter-speed]
      (when (pos? remaining)
        (when-let [{:keys [pos steps-used]}
                   (fighter-step-threat ctx current (get-in ((:current-world ctx)) (conj current :contents)))]
          (recur pos (- remaining steps-used)))))
    true))

(defn- ship-threat-action
  [pos ship-type move-target]
  (or (when-let [enemy-pos (ship-core/find-adjacent-enemy-ship pos)]
        (ship-core/attack-enemy pos enemy-pos))
      (when move-target
        (ship-core/move-toward pos move-target))
      (ship-core/explore-sea pos ship-type)))

(defn- sea-scout-target
  [pos center radius]
  (when (and center (> (core/distance pos center) radius))
    center))

(defn- major-invasion-target
  [nearest-major-target pos center]
  (or center (nearest-major-target pos)))

(defn- handle-sea-scout-ship-threat
  [pos ship-type center radius]
  (ship-threat-action pos ship-type (sea-scout-target pos center radius))
  true)

(defn- handle-major-invasion-ship-threat
  [nearest-major-target pos ship-type center]
  (ship-threat-action pos ship-type (major-invasion-target nearest-major-target pos center))
  true)

(defn process-ship-threat
  "Overrides regular ship logic for sea-scout and major-invasion missions.
   Returns true when handled."
  [ctx pos ship-type unit]
  (let [center (or (:threat-center unit) (:major-invasion-target unit))
        radius (:threat-radius unit (:threat-radius ctx))
        nearest-major-target (:nearest-major-target ctx)]
    (cond
      (= :sea-scout (:threat-mission unit))
      (handle-sea-scout-ship-threat pos ship-type center radius)

      (:major-invasion unit)
      (handle-major-invasion-ship-threat nearest-major-target pos ship-type center)

      :else false)))
