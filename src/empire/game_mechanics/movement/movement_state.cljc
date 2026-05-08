(ns empire.game-mechanics.movement.movement-state
  (:require [empire.config.core :as config]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.state.api :as sa]
            [empire.game-mechanics.visibility :as visibility]))

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

(defn- active-transport-army
  [contents]
  (when (and (= :player (:owner contents))
             (transport-with-awake-armies? contents))
    {:type :army :mode :awake :owner (:owner contents) :aboard-transport true}))

(defn- active-carrier-fighter
  [contents]
  (when (and (= :player (:owner contents))
             (carrier-with-awake-fighters? contents))
    {:type :fighter :mode :awake :owner (:owner contents) :fuel config/fighter-fuel :from-carrier true}))

(defn- active-awake-player-unit
  [contents]
  (when (and (= :player (:owner contents))
             (awake-unit? contents))
    contents))

(defn- active-airport-fighter
  [cell]
  (when (and (pos? (:fighter-count cell 0))
             (pos? (:awake-fighters cell 0)))
    {:type :fighter :mode :awake :owner :player :fuel config/fighter-fuel :from-airport true}))

(defn get-active-unit
  "Returns the unit currently needing attention: awake army aboard transport, awake fighter on carrier,
   then awake contents, then awake airport fighter.
   For armies aboard transport, returns a synthetic army map with :aboard-transport true.
   For fighters on carrier, returns a synthetic fighter map with :from-carrier true.
   For fighters in airport, returns a synthetic fighter map with :from-airport true.
   When coords are provided, skips airport fighter if city needs production."
  ([cell] (get-active-unit cell nil))
  ([cell coords]
   (let [contents (:contents cell)]
     (or (active-transport-army contents)
         (active-carrier-fighter contents)
         (active-awake-player-unit contents)
         (when-not (and coords
                        (= :city (:type cell))
                        (= :player (:city-status cell))
                        (not ((sa/read-state :production) coords)))
           (active-airport-fighter cell))))))

(defn- has-active-unit-flag?
  [active-unit flag]
  (and active-unit
       (flag active-unit)))

(defn is-army-aboard-transport?
  "Returns true if the active unit is an army aboard a transport."
  [active-unit]
  (has-active-unit-flag? active-unit :aboard-transport))

(defn is-fighter-from-airport?
  "Returns true if the active unit is a fighter from the airport."
  [active-unit]
  (has-active-unit-flag? active-unit :from-airport))

(defn is-fighter-from-carrier?
  "Returns true if the active unit is a fighter from a carrier."
  [active-unit]
  (has-active-unit-flag? active-unit :from-carrier))

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
  "Wakes a sleeping unit.
   For transports with armies, also wakes the armies aboard.
   For cities with fighters, wakes the airport fighters.
   Returns true if something was woken, nil otherwise."
  [[cx cy]]
  (let [cell (get-in (current-world) [cx cy])
        contents (:contents cell)]
    (cond
      (player-transport-with-armies? contents)
      (do (update-game-map! update-in [cx cy :contents]
                 #(-> %
                      (assoc :mode :awake)
                      (uc/wake-all :army-count :awake-armies)))
          true)

      (and contents (= :carrier (:type contents)) (= :player (:owner contents))
           (pos? (:fighter-count contents 0)))
      (do (update-game-map! update-in [cx cy :contents]
                 uc/wake-all :fighter-count :awake-fighters)
          true)

      (sleeping-player-unit? contents)
      (do (update-game-map! assoc-in [cx cy :contents]
                 (-> contents
                     (assoc :mode :awake)
                     (dissoc :coastline-steps :visited :start-pos :prev-pos :target :reason
                             :explore-steps :explore-heading :explore-origin)))
          true)

      (and (player-city? cell) (pos? (:fighter-count cell 0)))
      (do (update-game-map! update-in [cx cy]
                            (fn [city]
                              (-> city
                                  (assoc :awake-fighters (:fighter-count city 0))
                                  (uc/normalize-airport-awake-fighters))))
          true)

      :else nil)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-07T17:59:19.441301-05:00", :module-hash "-2005173", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-355558892"} {:id "defn-/update-game-map!", :kind "defn-", :line 7, :end-line 9, :hash "1805137569"} {:id "defn-/current-world", :kind "defn-", :line 11, :end-line 13, :hash "-640438772"} {:id "defn-/read-runtime-state", :kind "defn-", :line 15, :end-line 17, :hash "2315423"} {:id "defn-/write-runtime-state!", :kind "defn-", :line 19, :end-line 21, :hash "1105581680"} {:id "defn-/update-runtime-state!", :kind "defn-", :line 23, :end-line 27, :hash "-1960020981"} {:id "defn/transport-with-awake-armies?", :kind "defn", :line 29, :end-line 31, :hash "1197850544"} {:id "defn/carrier-with-awake-fighters?", :kind "defn", :line 33, :end-line 35, :hash "181269047"} {:id "defn/awake-unit?", :kind "defn", :line 37, :end-line 38, :hash "965345014"} {:id "defn-/active-transport-army", :kind "defn-", :line 40, :end-line 44, :hash "-980683020"} {:id "defn-/active-carrier-fighter", :kind "defn-", :line 46, :end-line 50, :hash "2101516371"} {:id "defn-/active-awake-player-unit", :kind "defn-", :line 52, :end-line 56, :hash "1964785296"} {:id "defn-/active-airport-fighter", :kind "defn-", :line 58, :end-line 62, :hash "-1805465566"} {:id "defn/get-active-unit", :kind "defn", :line 64, :end-line 81, :hash "-1953771626"} {:id "defn-/has-active-unit-flag?", :kind "defn-", :line 83, :end-line 86, :hash "458788292"} {:id "defn/is-army-aboard-transport?", :kind "defn", :line 88, :end-line 91, :hash "-1674561829"} {:id "defn/is-fighter-from-airport?", :kind "defn", :line 93, :end-line 96, :hash "1142151237"} {:id "defn/is-fighter-from-carrier?", :kind "defn", :line 98, :end-line 101, :hash "-1211135877"} {:id "defn/movement-context", :kind "defn", :line 103, :end-line 111, :hash "-170895213"} {:id "defn/set-unit-mode", :kind "defn", :line 113, :end-line 118, :hash "1978131580"} {:id "defn/add-unit-at", :kind "defn", :line 120, :end-line 139, :hash "1790831094"} {:id "defn-/player-city?", :kind "defn-", :line 141, :end-line 142, :hash "490674704"} {:id "defn-/player-transport-with-armies?", :kind "defn-", :line 144, :end-line 148, :hash "468210750"} {:id "defn-/sleeping-player-unit?", :kind "defn-", :line 150, :end-line 153, :hash "1791498666"} {:id "defn/wake-at", :kind "defn", :line 155, :end-line 193, :hash "1523756888"}]}
;; clj-mutate-manifest-end
