(ns empire.unit-container
  (:require [empire.units.dispatcher :as dispatcher]))

(defn get-count
  "Gets the total count of units in a container."
  [entity count-key]
  (get entity count-key 0))

(defn get-awake-count
  "Gets the count of awake units in a container."
  [entity awake-key]
  (get entity awake-key 0))

(defn has-awake?
  "Returns true if the container has any awake units."
  [entity awake-key]
  (pos? (get entity awake-key 0)))

(defn add-unit
  "Adds a sleeping unit to a container (for transport/carrier)."
  [entity count-key]
  (update entity count-key (fnil inc 0)))

(defn add-awake-unit
  "Adds an awake unit to a container (for city airports)."
  [entity count-key awake-key]
  (-> entity
      (update count-key (fnil inc 0))
      (update awake-key (fnil inc 0))))

(defn remove-awake-unit
  "Removes one awake unit from a container."
  [entity count-key awake-key]
  (-> entity
      (update count-key dec)
      (update awake-key dec)))

(defn wake-all
  "Wakes all units in a container."
  [entity count-key awake-key]
  (assoc entity awake-key (get entity count-key 0)))

(defn sleep-all
  "Puts all units in a container to sleep."
  [entity awake-key]
  (assoc entity awake-key 0))

(defn full?
  "Returns true if the container is at capacity."
  [entity count-key capacity]
  (>= (get entity count-key 0) capacity))

(defn transport-at-beach?
  "Returns true if the unit is a transport at a beach with armies aboard."
  [contents]
  (and (= (:type contents) :transport)
       (#{:transport-at-beach :found-a-bay} (:reason contents))
       (pos? (:army-count contents 0))))

(defn carrier-with-fighters?
  "Returns true if the unit is a carrier with fighters aboard."
  [contents]
  (and (= (:type contents) :carrier)
       (pos? (get-count contents :fighter-count))))

(defn has-awake-carrier-fighter?
  "Returns true if the unit is a carrier with awake fighters aboard."
  [contents]
  (and (= (:type contents) :carrier)
       (has-awake? contents :awake-fighters)))

(defn has-awake-army-aboard?
  "Returns true if the unit is a transport with awake armies aboard."
  [contents]
  (and (= (:type contents) :transport)
       (has-awake? contents :awake-armies)))

(defn blinking-contained-unit
  "Returns the contained unit to display during attention blink, or nil."
  [has-awake-airport? has-awake-carrier? has-awake-army?]
  (cond
    has-awake-airport? {:type :fighter :mode :awake}
    has-awake-carrier? {:type :fighter :mode :awake}
    has-awake-army? {:type :army :mode :awake}
    :else nil))

(defn normal-display-unit
  "Returns the unit to display during normal (non-blink) rendering."
  [cell contents has-awake-airport? has-any-airport?]
  (cond
    (and contents (= (:mode contents) :awake)) contents
    has-awake-airport? {:type :fighter :mode :awake}
    contents contents
    has-any-airport? {:type :fighter :mode :sentry}
    :else nil))

;; Shipyard helpers

(defn add-ship-to-shipyard
  "Adds a ship to the city's shipyard. Ship is stored as {:type t :hits h}."
  [city ship-type hits]
  (update city :shipyard (fnil conj []) {:type ship-type :hits hits}))

(defn remove-ship-from-shipyard
  "Removes the ship at the given index from the shipyard."
  [city index]
  (let [shipyard (:shipyard city [])
        new-shipyard (vec (concat (subvec shipyard 0 index)
                                  (subvec shipyard (inc index))))]
    (assoc city :shipyard new-shipyard)))

(defn get-shipyard-ships
  "Returns the list of ships in the shipyard, or empty vector if none."
  [city]
  (get city :shipyard []))

(defn repair-ship
  "Repairs a ship by incrementing hits by 1, capped at max for unit type."
  [ship]
  (let [max-hits (dispatcher/hits (:type ship))
        new-hits (min (inc (:hits ship)) max-hits)]
    (assoc ship :hits new-hits)))

(defn ship-fully-repaired?
  "Returns true if ship's hits equal max hits for its type."
  [ship]
  (= (:hits ship) (dispatcher/hits (:type ship))))

(defn ship-can-dock?
  "Returns true if a damaged naval unit can dock at a friendly city for repair."
  [unit cell]
  (and (= :city (:type cell))
       (dispatcher/naval-unit? (:type unit))
       (< (:hits unit) (dispatcher/hits (:type unit)))
       (= (:owner unit)
          (case (:city-status cell)
            :player :player
            :computer :computer
            nil))))
