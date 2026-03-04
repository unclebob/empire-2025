;; mutation-tested: 2026-02-25
(ns empire.player.production
  (:require [empire.application.movement-services :as movement-services]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.config :as config]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))

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

(defn- computer-call
  [sym & args]
  (let [f (or (try
                (requiring-resolve (symbol "empire.computer.stamping" (name sym)))
                (catch #?(:clj Throwable :cljs :default) _
                  nil))
              (throw (ex-info (str "Unable to resolve computer stamping function: " (name sym))
                              {:symbol sym})))]
    (apply f args)))

(defn- stamp-computer-fields
  [unit cell]
  (computer-call 'stamp-computer-fields unit cell))

(defn- apply-coast-walk-fields
  [unit item cell coords]
  (computer-call 'apply-coast-walk-fields unit item cell coords))

(defn- apply-random-explore-fields
  [unit item cell]
  (computer-call 'apply-random-explore-fields unit item cell))

(defn set-city-production
  "Sets the production for a city at given coordinates to the specified item."
  [coords item]
  (update-runtime-state! :production assoc coords {:item item :remaining-rounds (config/item-cost item)}))

(defn- create-base-unit
  "Creates a base unit with type, hits, mode, and owner."
  [item owner]
  {:type item :hits (config/item-hits item) :mode :awake :owner owner})

(defn- apply-unit-type-attributes
  "Adds type-specific attributes (fuel, turns)."
  [unit item]
  (cond-> unit
    (= item :fighter)
    (assoc :fuel config/fighter-fuel)

    (= item :satellite)
    (assoc :turns-remaining config/satellite-turns)))

(defn- army-with-lookaround? [item marching-orders]
  (and (= item :army) (= marching-orders :lookaround)))

(defn- army-with-marching-orders? [item marching-orders]
  (and (= item :army) marching-orders))

(defn- fighter-with-flight-path? [item flight-path]
  (and (= item :fighter) flight-path))

(defn- apply-movement-orders
  "Applies marching orders or flight path to unit."
  [unit item marching-orders flight-path]
  (cond
    (army-with-lookaround? item marching-orders)
    (assoc unit :mode :explore :explore-steps 50)

    (army-with-marching-orders? item marching-orders)
    (assoc unit :mode :moving :target marching-orders)

    (fighter-with-flight-path? item flight-path)
    (assoc unit :mode :moving :target flight-path)

    :else unit))

(defn stamp-unit-fields
  "Applies all type-specific fields to a unit based on its :type and :owner.
   Used by spawn-unit and launch-ship-from-shipyard to ensure computer ships
   get their required fields (carrier-mode, escort-id, etc.).
   cell is the city cell (needed for country-id / patrol fields); may be nil."
  [unit cell]
  (-> unit
      (apply-unit-type-attributes (:type unit))
      (stamp-computer-fields cell)))

(defn- stamp-adjacent-land
  "Stamps country-id on land cells adjacent to a city when a computer army spawns."
  [coords country-id]
  (let [game-map (current-world)
        neighbors (movement-services/get-matching-neighbors coords game-map
                                                            movement-services/neighbor-offsets some?)]
    (doseq [n neighbors]
      (let [cell (get-in game-map n)]
        (when (and (= :land (:type cell)) (nil? (:country-id cell)))
          (update-game-map! assoc-in (conj n :country-id) country-id))))))

(defn- spawn-unit
  "Creates and places a unit at the given city coordinates."
  [coords cell item]
  (let [owner (:city-status cell)
        marching-orders (:marching-orders cell)
        flight-path (:flight-path cell)
        unit (-> (create-base-unit item owner)
                 (stamp-unit-fields cell)
                 (apply-coast-walk-fields item cell coords)
                 (apply-random-explore-fields item cell)
                 (apply-movement-orders item marching-orders flight-path)
                 (cond-> (= item :transport) (assoc :produced-at coords)))]
    (update-game-map! assoc-in (conj coords :contents) unit)
    (when (and (= owner :computer) (= item :army) (:country-id cell))
      (stamp-adjacent-land coords (:country-id cell)))
    (when (and (= owner :computer) (= item :carrier))
      (update-runtime-state! :computer-carrier-positions conj coords))
    owner))

(defn- handle-production-complete
  "Handles production completion: spawns unit and updates production state."
  [coords prod item]
  (let [cell (get-in (current-world) coords)
        owner (spawn-unit coords cell item)]
    (if (= owner :computer)
      (update-runtime-state! :production dissoc coords)
      (update-runtime-state! :production assoc coords
                             (assoc prod :remaining-rounds (config/item-cost item))))))

(defn- update-city-production
  "Updates production for a single city."
  [coords prod]
  (let [cell (get-in (current-world) coords)]
    (when-not (:contents cell)
      (let [item (:item prod)
            remaining (dec (:remaining-rounds prod))]
        (if (zero? remaining)
          (handle-production-complete coords prod item)
          (update-runtime-state! :production assoc coords
                                 (assoc prod :remaining-rounds remaining)))))))

(defn update-production
  "Updates production for all cities by decrementing remaining rounds."
  []
  (doseq [[coords prod] (read-runtime-state :production)]
    (when (map? prod)
      (update-city-production coords prod))))
