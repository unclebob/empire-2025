;; mutation-tested: 2026-02-28
;; mutation-tested: 2026-02-28
(ns empire.game-mechanics.movement.movement-state
  (:require [empire.config.core :as config]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.state.api :as sa]
            [empire.game-mechanics.movement.visibility :as visibility]))

(defn- update-game-map!
  [f & args]
  (apply sa/update-world! f args))

(defn- current-world
  []
  (sa/current-world))

(defn- read-runtime-state
  [k]
  (sa/read-state k))

(defn- write-runtime-state!
  [k v]
  (sa/write-state! k v))

(defn- update-runtime-state!
  [k f & args]
  (let [current (read-runtime-state k)
        next-state (apply f current args)]
    (write-runtime-state! k next-state)))

(defn transport-with-awake-armies? [contents]
  (and (= (:type contents) :transport)
       (uc/has-awake? contents :awake-armies)))

(defn carrier-with-awake-fighters? [contents]
  (and (= (:type contents) :carrier)
       (uc/has-awake? contents :awake-fighters)))

(defn awake-unit? [contents]
  (and contents (= (:mode contents) :awake)))

(defn get-active-unit
  "Returns the unit currently needing attention: awake army aboard transport, awake fighter on carrier,
   then awake contents, then awake airport fighter.
   For armies aboard transport, returns a synthetic army map with :aboard-transport true.
   For fighters on carrier, returns a synthetic fighter map with :from-carrier true.
   For fighters in airport, returns a synthetic fighter map with :from-airport true."
  [cell]
  (let [contents (:contents cell)]
    (cond
      (transport-with-awake-armies? contents)
      {:type :army :mode :awake :owner (:owner contents) :aboard-transport true}

      (carrier-with-awake-fighters? contents)
      {:type :fighter :mode :awake :owner (:owner contents) :fuel config/fighter-fuel :from-carrier true}

      (awake-unit? contents) contents

      (uc/has-awake? cell :awake-fighters)
      {:type :fighter :mode :awake :owner :player :fuel config/fighter-fuel :from-airport true}

      :else nil)))

(defn is-army-aboard-transport?
  "Returns true if the active unit is an army aboard a transport."
  [active-unit]
  (and active-unit
       (:aboard-transport active-unit)))

(defn is-fighter-from-airport?
  "Returns true if the active unit is a fighter from the airport."
  [active-unit]
  (and active-unit
       (:from-airport active-unit)))

(defn is-fighter-from-carrier?
  "Returns true if the active unit is a fighter from a carrier."
  [active-unit]
  (and active-unit
       (:from-carrier active-unit)))

(defn movement-context
  "Determines the movement context for a cell and active unit.
   Returns :airport-fighter, :carrier-fighter, :army-aboard, or :standard-unit."
  [_cell active-unit]
  (cond
    (is-fighter-from-airport? active-unit) :airport-fighter
    (is-fighter-from-carrier? active-unit) :carrier-fighter
    (is-army-aboard-transport? active-unit) :army-aboard
    :else :standard-unit))

(defn set-unit-mode [coords mode]
  (let [cell (get-in (current-world) coords)
        unit (:contents cell)
        updated-unit (dissoc (assoc unit :mode mode) :reason)
        updated-cell (assoc cell :contents updated-unit)]
    (update-game-map! assoc-in coords updated-cell)))

(defn add-unit-at
  "Adds a unit of the given type at the specified cell coordinates.
   Only adds if the cell is empty. Owner defaults to :player.
   Updates visibility for the owner after adding."
  ([[cx cy] unit-type] (add-unit-at [cx cy] unit-type :player))
  ([[cx cy] unit-type owner]
   (let [cell (get-in (current-world) [cx cy])
         unit {:type unit-type
               :hits (config/item-hits unit-type)
               :mode :awake
               :owner owner}
         unit (if (= unit-type :fighter)
                (assoc unit :fuel config/fighter-fuel)
                unit)
         unit (if (= unit-type :satellite)
                (assoc unit :turns-remaining config/satellite-turns)
                unit)]
     (when-not (:contents cell)
       (update-game-map! assoc-in [cx cy :contents] unit)
       (visibility/update-cell-visibility [cx cy] owner)))))

(defn- player-city? [cell]
  (and (= (:type cell) :city) (= (:city-status cell) :player)))

(defn- player-transport-with-armies? [contents]
  (and contents
       (= (:owner contents) :player)
       (= (:type contents) :transport)
       (pos? (:army-count contents 0))))

(defn- sleeping-player-unit? [contents]
  (and contents
       (= (:owner contents) :player)
       (not= (:mode contents) :awake)))

(defn wake-at
  "Wakes a city (removes production so it needs attention) or a sleeping unit.
   For transports with armies, also wakes the armies aboard.
   Returns true if something was woken, nil otherwise."
  [[cx cy]]
  (let [cell (get-in (current-world) [cx cy])
        contents (:contents cell)]
    (cond
      (player-city? cell)
      (do (update-runtime-state! :production dissoc [cx cy])
          true)

      (player-transport-with-armies? contents)
      (do (update-game-map! update-in [cx cy :contents]
                 #(-> %
                      (assoc :mode :awake)
                      (uc/wake-all :army-count :awake-armies)))
          true)

      (sleeping-player-unit? contents)
      (do (update-game-map! assoc-in [cx cy :contents]
                 (-> contents
                     (assoc :mode :awake)
                     (dissoc :coastline-steps :visited :start-pos :prev-pos :target :reason)))
          true)

      :else nil)))
