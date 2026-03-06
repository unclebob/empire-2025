;; mutation-tested: 2026-02-26
(ns empire.containers.ops
  (:require [empire.state.api :as sa]
            [empire.application.unit-stamping :as unit-stamping]
            [empire.config.core :as config]
            [empire.containers.helpers :as uc]
            [empire.domain.model.containers :as domain-containers]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.visibility :as visibility]
            [empire.units.dispatcher :as dispatcher]))

(defn- neighbor-offsets
  []
  map-utils/neighbor-offsets)

(defn- any-neighbor-matches?
  [coords world offsets pred]
  (map-utils/any-neighbor-matches? coords world offsets pred))

(defn- get-matching-neighbors
  [coords world offsets pred]
  (map-utils/get-matching-neighbors coords world offsets pred))

(defn- update-cell-visibility!
  [coords owner]
  (visibility/update-cell-visibility coords owner))

(defn- stamp-unit-fields
  [unit city]
  (unit-stamping/stamp-computer-fields unit city))

;; Transport operations

(defn- loadable-army?
  "Returns true if adj-unit is a sentry army owned by the same player as transport,
   and the transport is not full."
  [adj-unit transport]
  (and adj-unit
       (= (:type adj-unit) :army)
       (= (:mode adj-unit) :sentry)
       (= (:owner adj-unit) (:owner transport))
       (not (uc/full? transport :army-count
                      (dispatcher/effective-capacity :transport (:hits transport))))))

(defn- wake-transport-if-needed
  "Wakes a sentry transport at beach that has armies loaded."
  [transport-coords]
  (let [world (sa/current-world)
        transport (get-in world (conj transport-coords :contents))
        has-armies? (pos? (uc/get-count transport :army-count))
        at-beach? (any-neighbor-matches? transport-coords world (neighbor-offsets)
                                         #(= :land (:type %)))]
    (when (and has-armies? at-beach? (= (:mode transport) :sentry))
      (sa/update-world! update-in (conj transport-coords :contents)
                        #(assoc % :mode :awake :reason :transport-at-beach)))))

(defn non-full-transport? [unit]
  (and (= (:type unit) :transport)
       (not (uc/full? unit :army-count (dispatcher/effective-capacity :transport (:hits unit))))))

(defn- try-load-from-neighbor [transport-coords [nx ny]]
  (let [world (sa/current-world)
        adj-cell (get-in world [nx ny])
        adj-unit (:contents adj-cell)
        transport (get-in world (conj transport-coords :contents))]
    (when (loadable-army? adj-unit transport)
      (sa/update-world! assoc-in [nx ny] (dissoc adj-cell :contents))
      (sa/update-world! update-in (conj transport-coords :contents) uc/add-unit :army-count))))

(defn load-adjacent-sentry-armies
  [transport-coords]
  (let [unit (:contents (get-in (sa/current-world) transport-coords))]
    (when (non-full-transport? unit)
      (let [neighbors (get-matching-neighbors transport-coords
                                              (sa/current-world)
                                              (neighbor-offsets)
                                              (constantly true))]
        (doseq [n neighbors]
          (try-load-from-neighbor transport-coords n))
        (wake-transport-if-needed transport-coords)))))

(defn wake-armies-on-transport
  [transport-coords]
  (let [cell (get-in (sa/current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/wake-transport-armies transport)
        updated-cell (assoc cell :contents updated-transport)]
    (sa/update-world! assoc-in transport-coords updated-cell)))

(defn sleep-armies-on-transport
  [transport-coords]
  (let [cell (get-in (sa/current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/sleep-transport-armies transport)
        updated-cell (assoc cell :contents updated-transport)]
    (sa/update-world! assoc-in transport-coords updated-cell)))

(defn remove-army-from-transport
  [transport-coords]
  (let [cell (get-in (sa/current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/remove-awake-transport-army transport)
        updated-cell (assoc cell :contents updated-transport)]
    (sa/update-world! assoc-in transport-coords updated-cell)))

(defn disembark-army-from-transport
  [transport-coords target-coords]
  (let [cell (get-in (sa/current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/remove-awake-transport-army transport)
        disembarked-army (domain-containers/disembarked-army (:owner transport))
        updated-cell (assoc cell :contents updated-transport)]
    (sa/update-world! assoc-in transport-coords updated-cell)
    (sa/update-world! assoc-in (conj target-coords :contents) disembarked-army)
    (update-cell-visibility! target-coords (:owner transport))
    target-coords))

(defn disembark-army-with-target
  [transport-coords adjacent-coords extended-target]
  (let [cell (get-in (sa/current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/remove-awake-transport-army transport)
        moving-army (domain-containers/moving-disembarked-army (:owner transport) extended-target)
        updated-cell (assoc cell :contents updated-transport)]
    (sa/update-world! assoc-in transport-coords updated-cell)
    (sa/update-world! assoc-in (conj adjacent-coords :contents) moving-army)
    (update-cell-visibility! adjacent-coords (:owner transport))))

(defn disembark-army-to-explore
  [transport-coords target-coords]
  (let [cell (get-in (sa/current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/remove-awake-transport-army transport)
        exploring-army (domain-containers/exploring-disembarked-army (:owner transport) target-coords)
        updated-cell (assoc cell :contents updated-transport)]
    (sa/update-world! assoc-in transport-coords updated-cell)
    (sa/update-world! assoc-in (conj target-coords :contents) exploring-army)
    (update-cell-visibility! target-coords (:owner transport))
    target-coords))

;; Carrier operations

(defn wake-fighters-on-carrier
  [carrier-coords]
  (let [cell (get-in (sa/current-world) carrier-coords)
        carrier (:contents cell)
        updated-carrier (domain-containers/wake-carrier-fighters carrier)
        updated-cell (assoc cell :contents updated-carrier)]
    (sa/update-world! assoc-in carrier-coords updated-cell)))

(defn sleep-fighters-on-carrier
  [carrier-coords]
  (let [cell (get-in (sa/current-world) carrier-coords)
        carrier (:contents cell)
        updated-carrier (domain-containers/sleep-carrier-fighters carrier)
        updated-cell (assoc cell :contents updated-carrier)]
    (sa/update-world! assoc-in carrier-coords updated-cell)))

(defn launch-fighter-from-carrier
  [carrier-coords target-coords]
  (let [world (sa/current-world)
        cell (get-in world carrier-coords)
        carrier (:contents cell)
        after-remove (uc/remove-awake-unit carrier :fighter-count :awake-fighters)
        first-step (domain-containers/first-step-toward carrier-coords target-coords)
        moving-fighter (domain-containers/launched-fighter
                        (:owner carrier)
                        target-coords
                        (dec (config/unit-speed :fighter)))
        updated-cell (assoc cell :contents after-remove)
        target-cell (get-in world first-step)]
    ;; Update carrier
    (sa/update-world! assoc-in carrier-coords updated-cell)
    ;; Place fighter at first step position
    (sa/update-world! assoc-in first-step (assoc target-cell :contents moving-fighter))
    (update-cell-visibility! first-step (:owner carrier))
    first-step))

;; Airport operations

(defn launch-fighter-from-airport
  [city-coords target-coords]
  (let [cell (get-in (sa/current-world) city-coords)
        after-remove (uc/remove-awake-unit cell :fighter-count :awake-fighters)
        moving-fighter (domain-containers/launched-fighter
                        :player
                        target-coords
                        (config/unit-speed :fighter))
        updated-cell (assoc after-remove :contents moving-fighter)]
    (sa/update-world! assoc-in city-coords updated-cell)
    city-coords))

;; Shipyard operations

(defn launch-ship-from-shipyard
  ([city-coords ship-index]
   (launch-ship-from-shipyard city-coords ship-index city-coords))
  ([city-coords ship-index launch-pos]
   (let [cell (get-in (sa/current-world) city-coords)
         ship-data (get-in cell [:shipyard ship-index])
         owner (case (:city-status cell)
                 :player :player
                 :computer :computer
                 :player)  ; default to player for free cities
         ship (-> {:type (:type ship-data)
                   :owner owner
                   :hits (:hits ship-data)
                   :mode :awake
                   :steps-remaining (dispatcher/effective-speed (:type ship-data) (:hits ship-data))}
                  (stamp-unit-fields cell))
         updated-city (uc/remove-ship-from-shipyard cell ship-index)]
     (sa/update-world! assoc-in city-coords updated-city)
     (if (= launch-pos city-coords)
       (sa/update-world! assoc-in city-coords (assoc updated-city :contents ship))
       (sa/update-world! assoc-in (conj launch-pos :contents) ship))
     (update-cell-visibility! launch-pos owner))))
