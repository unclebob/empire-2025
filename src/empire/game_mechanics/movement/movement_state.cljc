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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:01:21.919255-05:00", :module-hash "1926017434", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-1861369831"} {:id "defn-/update-game-map!", :kind "defn-", :line 7, :end-line 9, :hash "1805137569"} {:id "defn-/current-world", :kind "defn-", :line 11, :end-line 13, :hash "-640438772"} {:id "defn-/read-runtime-state", :kind "defn-", :line 15, :end-line 17, :hash "2315423"} {:id "defn-/write-runtime-state!", :kind "defn-", :line 19, :end-line 21, :hash "1105581680"} {:id "defn-/update-runtime-state!", :kind "defn-", :line 23, :end-line 27, :hash "-1960020981"} {:id "defn/transport-with-awake-armies?", :kind "defn", :line 29, :end-line 31, :hash "1197850544"} {:id "defn/carrier-with-awake-fighters?", :kind "defn", :line 33, :end-line 35, :hash "181269047"} {:id "defn/awake-unit?", :kind "defn", :line 37, :end-line 38, :hash "965345014"} {:id "defn/get-active-unit", :kind "defn", :line 40, :end-line 60, :hash "1526732841"} {:id "defn/is-army-aboard-transport?", :kind "defn", :line 62, :end-line 66, :hash "1626424258"} {:id "defn/is-fighter-from-airport?", :kind "defn", :line 68, :end-line 72, :hash "1362764502"} {:id "defn/is-fighter-from-carrier?", :kind "defn", :line 74, :end-line 78, :hash "-497420128"} {:id "defn/movement-context", :kind "defn", :line 80, :end-line 88, :hash "-170895213"} {:id "defn/set-unit-mode", :kind "defn", :line 90, :end-line 95, :hash "1978131580"} {:id "defn/add-unit-at", :kind "defn", :line 97, :end-line 116, :hash "1790831094"} {:id "defn-/player-city?", :kind "defn-", :line 118, :end-line 119, :hash "490674704"} {:id "defn-/player-transport-with-armies?", :kind "defn-", :line 121, :end-line 125, :hash "468210750"} {:id "defn-/sleeping-player-unit?", :kind "defn-", :line 127, :end-line 130, :hash "1791498666"} {:id "defn/wake-at", :kind "defn", :line 132, :end-line 158, :hash "-1933571224"}]}
;; clj-mutate-manifest-end
