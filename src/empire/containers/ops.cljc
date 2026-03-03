;; mutation-tested: 2026-02-26
(ns empire.containers.ops
  (:require [empire.application.ports.world-store :as world-ports]
            [empire.config :as config]
            [empire.containers.helpers :as uc]
            [empire.domain.model.containers :as domain-containers]
            [empire.units.dispatcher :as dispatcher]))

(def ^:private world-store-fn
  (delay
    (try
      (requiring-resolve 'empire.adapters.state.atoms/world-store)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(def ^:private map-neighbor-offsets-var
  (delay
    (try
      (requiring-resolve 'empire.movement.map-utils/neighbor-offsets)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(def ^:private map-any-neighbor-matches-fn
  (delay
    (try
      (requiring-resolve 'empire.movement.map-utils/any-neighbor-matches?)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(def ^:private map-get-matching-neighbors-fn
  (delay
    (try
      (requiring-resolve 'empire.movement.map-utils/get-matching-neighbors)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(def ^:private update-cell-visibility-fn
  (delay
    (try
      (requiring-resolve 'empire.movement.visibility/update-cell-visibility)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(def ^:private stamp-unit-fields-fn
  (delay
    (try
      (requiring-resolve 'empire.player.production/stamp-unit-fields)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(defn- current-world
  []
  (if-let [resolver @world-store-fn]
    (world-ports/load-world (resolver))
    []))

(defn- update-game-map!
  [f & args]
  (when-let [resolver @world-store-fn]
    (let [store (resolver)
          world (world-ports/load-world store)]
      (world-ports/save-world! store (apply f world args)))))

(defn- neighbor-offsets
  []
  (if-let [v @map-neighbor-offsets-var] @v []))

(defn- any-neighbor-matches?
  [coords world offsets pred]
  (if-let [f @map-any-neighbor-matches-fn]
    (f coords world offsets pred)
    false))

(defn- get-matching-neighbors
  [coords world offsets pred]
  (if-let [f @map-get-matching-neighbors-fn]
    (f coords world offsets pred)
    []))

(defn- update-cell-visibility!
  [coords owner]
  (when-let [f @update-cell-visibility-fn]
    (f coords owner)))

(defn- stamp-unit-fields
  [city unit]
  (if-let [f @stamp-unit-fields-fn]
    (f city unit)
    unit))

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
  (let [world (current-world)
        transport (get-in world (conj transport-coords :contents))
        has-armies? (pos? (uc/get-count transport :army-count))
        at-beach? (any-neighbor-matches? transport-coords world (neighbor-offsets)
                                         #(= :land (:type %)))]
    (when (and has-armies? at-beach? (= (:mode transport) :sentry))
      (update-game-map! update-in (conj transport-coords :contents)
                        #(assoc % :mode :awake :reason :transport-at-beach)))))

(defn non-full-transport? [unit]
  (and (= (:type unit) :transport)
       (not (uc/full? unit :army-count (dispatcher/effective-capacity :transport (:hits unit))))))

(defn- try-load-from-neighbor [transport-coords [nx ny]]
  (let [world (current-world)
        adj-cell (get-in world [nx ny])
        adj-unit (:contents adj-cell)
        transport (get-in world (conj transport-coords :contents))]
    (when (loadable-army? adj-unit transport)
      (update-game-map! assoc-in [nx ny] (dissoc adj-cell :contents))
      (update-game-map! update-in (conj transport-coords :contents) uc/add-unit :army-count))))

(defn load-adjacent-sentry-armies
  "Loads adjacent sentry armies onto a transport at the given coords.
   Wakes up the transport if it has armies and is at a beach."
  [transport-coords]
  (let [unit (:contents (get-in (current-world) transport-coords))]
    (when (non-full-transport? unit)
      (let [neighbors (get-matching-neighbors transport-coords
                                              (current-world)
                                              (neighbor-offsets)
                                              (constantly true))]
        (doseq [n neighbors]
          (try-load-from-neighbor transport-coords n))
        (wake-transport-if-needed transport-coords)))))

(defn wake-armies-on-transport
  "Wakes up all armies aboard the transport at the given coords.
   Sets steps-remaining to 0 to end the transport's turn."
  [transport-coords]
  (let [cell (get-in (current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/wake-transport-armies transport)
        updated-cell (assoc cell :contents updated-transport)]
    (update-game-map! assoc-in transport-coords updated-cell)))

(defn sleep-armies-on-transport
  "Puts all armies aboard the transport back to sleep (sentry mode).
   Wakes up the transport so it can receive orders."
  [transport-coords]
  (let [cell (get-in (current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/sleep-transport-armies transport)
        updated-cell (assoc cell :contents updated-transport)]
    (update-game-map! assoc-in transport-coords updated-cell)))

(defn remove-army-from-transport
  "Removes one awake army from transport without placing it anywhere.
   Wakes the transport when no more awake armies remain."
  [transport-coords]
  (let [cell (get-in (current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/remove-awake-transport-army transport)
        updated-cell (assoc cell :contents updated-transport)]
    (update-game-map! assoc-in transport-coords updated-cell)))

(defn disembark-army-from-transport
  "Removes first awake army from transport and places it on target land cell.
   Army remains awake and ready for orders. Other armies remain on transport.
   Wakes the transport when no more awake armies remain.
  Returns the coordinates where the army was placed."
  [transport-coords target-coords]
  (let [cell (get-in (current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/remove-awake-transport-army transport)
        disembarked-army (domain-containers/disembarked-army (:owner transport))
        updated-cell (assoc cell :contents updated-transport)]
    (update-game-map! assoc-in transport-coords updated-cell)
    (update-game-map! assoc-in (conj target-coords :contents) disembarked-army)
    (update-cell-visibility! target-coords (:owner transport))
    target-coords))

(defn disembark-army-with-target
  "Removes first awake army from transport and places it on adjacent cell in moving mode.
   Army will continue moving toward the extended target on subsequent turns.
  Steps-remaining is 0 because the disembark used the army's one step."
  [transport-coords adjacent-coords extended-target]
  (let [cell (get-in (current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/remove-awake-transport-army transport)
        moving-army (domain-containers/moving-disembarked-army (:owner transport) extended-target)
        updated-cell (assoc cell :contents updated-transport)]
    (update-game-map! assoc-in transport-coords updated-cell)
    (update-game-map! assoc-in (conj adjacent-coords :contents) moving-army)
    (update-cell-visibility! adjacent-coords (:owner transport))))

(defn disembark-army-to-explore
  "Removes first awake army from transport and places it on target land cell in explore mode.
   Returns the coordinates where the army was placed."
  [transport-coords target-coords]
  (let [cell (get-in (current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/remove-awake-transport-army transport)
        exploring-army (domain-containers/exploring-disembarked-army (:owner transport) target-coords)
        updated-cell (assoc cell :contents updated-transport)]
    (update-game-map! assoc-in transport-coords updated-cell)
    (update-game-map! assoc-in (conj target-coords :contents) exploring-army)
    (update-cell-visibility! target-coords (:owner transport))
    target-coords))

;; Carrier operations

(defn wake-fighters-on-carrier
  "Wakes up all fighters aboard the carrier at the given coords."
  [carrier-coords]
  (let [cell (get-in (current-world) carrier-coords)
        carrier (:contents cell)
        updated-carrier (domain-containers/wake-carrier-fighters carrier)
        updated-cell (assoc cell :contents updated-carrier)]
    (update-game-map! assoc-in carrier-coords updated-cell)))

(defn sleep-fighters-on-carrier
  "Puts all fighters aboard the carrier back to sleep.
   Wakes up the carrier so it can receive orders."
  [carrier-coords]
  (let [cell (get-in (current-world) carrier-coords)
        carrier (:contents cell)
        updated-carrier (domain-containers/sleep-carrier-fighters carrier)
        updated-cell (assoc cell :contents updated-carrier)]
    (update-game-map! assoc-in carrier-coords updated-cell)))

(defn launch-fighter-from-carrier
  "Removes first awake fighter from carrier and sets it moving to target.
   Fighter is placed at the adjacent cell toward target.
  Carrier stays in its current mode (sentry carriers remain sentry).
   Returns the coordinates where the fighter was placed."
  [carrier-coords target-coords]
  (let [world (current-world)
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
    (update-game-map! assoc-in carrier-coords updated-cell)
    ;; Place fighter at first step position
    (update-game-map! assoc-in first-step (assoc target-cell :contents moving-fighter))
    (update-cell-visibility! first-step (:owner carrier))
    first-step))

;; Airport operations

(defn launch-fighter-from-airport
  "Removes first awake fighter from airport and sets it moving to target.
   Returns the coordinates where the fighter was placed."
  [city-coords target-coords]
  (let [cell (get-in (current-world) city-coords)
        after-remove (uc/remove-awake-unit cell :fighter-count :awake-fighters)
        moving-fighter (domain-containers/launched-fighter
                        :player
                        target-coords
                        (config/unit-speed :fighter))
        updated-cell (assoc after-remove :contents moving-fighter)]
    (update-game-map! assoc-in city-coords updated-cell)
    city-coords))

;; Shipyard operations

(defn launch-ship-from-shipyard
  "Removes ship at given index from city's shipyard and places on map.
   Reconstructs full unit from minimal shipyard data.
   When launch-pos is provided, places ship there instead of at city."
  ([city-coords ship-index]
   (launch-ship-from-shipyard city-coords ship-index city-coords))
  ([city-coords ship-index launch-pos]
   (let [cell (get-in (current-world) city-coords)
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
     (update-game-map! assoc-in city-coords updated-city)
     (if (= launch-pos city-coords)
       (update-game-map! assoc-in city-coords (assoc updated-city :contents ship))
       (update-game-map! assoc-in (conj launch-pos :contents) ship))
     (update-cell-visibility! launch-pos owner))))
