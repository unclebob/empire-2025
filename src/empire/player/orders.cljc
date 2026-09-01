(ns empire.player.orders
  "Standing orders on cities and units: marching orders, flight paths, waypoints.
   All functions take explicit coordinates — no Quil dependency."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.waypoint :as waypoint]
            [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.config.core :as config]
            [empire.player.orders-decisions :as decisions]))

(defn- set-command-message!
  [msg]
  (sa/write-state! :command-message msg))

(defn add-unit-at [coords unit-type owner]
  (movement-state/add-unit-at coords unit-type owner))

(defn wake-at [coords]
  (movement-state/wake-at coords))

(defn clear-city-production-at
  "Clears production on a player city at the given coordinates so it needs attention."
  [[cx cy]]
  (let [cell (get-in (sa/current-world) [cx cy])]
    (when (and (= :city (:type cell))
               (= :player (:city-status cell)))
      (sa/update-state! :production dissoc [cx cy])
      true)))

(defn own-city-at
  "Claims a city at the given coordinates for the player."
  [[cx cy]]
  (let [cell (get-in (sa/current-world) [cx cy])]
    (when-let [_ (decisions/own-city-action cell)]
      (sa/update-world! assoc-in [cx cy :city-status] :player)
      true)))

(defn set-city-lookaround
  "Sets marching orders to :lookaround on a player city at the given coordinates."
  [[cx cy]]
  (let [cell (get-in (sa/current-world) [cx cy])]
    (when-let [decision (decisions/city-lookaround-action cell)]
      (sa/update-world! assoc-in [cx cy :marching-orders] :lookaround)
      (set-command-message! (:message decision))
      true)))

(defn set-destination-at
  "Sets the destination to the given coordinates."
  [[cx cy]]
  (sa/write-state! :destination [cx cy])
  true)

(defn clear-destination!
  "Clears the destination."
  []
  (sa/write-state! :destination nil)
  (set-command-message! "Destination cleared")
  true)

(defn- apply-marching-orders [path dest]
  (let [{:keys [path dest clear-destination? message]}
        (decisions/marching-orders-state path dest)]
    (sa/update-world! assoc-in path dest)
    (when clear-destination?
      (sa/write-state! :destination nil))
    (set-command-message! message))
  true)

(defn- clear-city-marching-orders
  [[cx cy]]
  (let [cell (get-in (sa/current-world) [cx cy])]
    (when (and (= :city (:type cell)) (= :player (:city-status cell))
               (:marching-orders cell))
      (sa/update-world! update-in [cx cy] dissoc :marching-orders)
      (set-command-message! "Marching orders cleared")
      true)))

(defn- apply-destination-marching-orders
  [[cx cy] dest]
  (let [cell (get-in (sa/current-world) [cx cy])
        decision (decisions/marching-orders-action cell dest)]
    (case (:action decision)
      :set-marching-orders
      (apply-marching-orders (into [cx cy] (:path decision)) (:dest decision))

      :set-waypoint-orders
      (do (waypoint/set-waypoint-orders [cx cy]) true)

      nil)))

(defn set-marching-orders-at
  "Sets or clears marching orders on a player city, transport, or waypoint.
   When destination is nil, clears marching orders on the city."
  [coords]
  (if-let [dest (sa/read-state :destination)]
    (apply-destination-marching-orders coords dest)
    (clear-city-marching-orders coords)))

(defn- apply-flight-path
  [[cx cy] decision]
  (let [{:keys [path dest clear-destination? message]}
        (decisions/flight-path-state (into [cx cy] (:path decision)) (:dest decision))]
    (sa/update-world! assoc-in path dest)
    (when clear-destination?
      (sa/write-state! :destination nil))
    (set-command-message! message))
  true)

(defn- flight-path-owner?
  [cell]
  (or (and (= :city (:type cell)) (= :player (:city-status cell)))
      (and (= :carrier (:type (:contents cell))) (= :player (:owner (:contents cell))))))

(defn- clear-flight-path
  [[cx cy] cell]
  (when (and (flight-path-owner? cell)
             (:flight-path cell))
    (sa/update-world! update-in [cx cy] dissoc :flight-path)
    (set-command-message! "Flight path cleared")
    true))

(defn set-flight-path-at
  "Sets or clears flight path on a player city or carrier.
   When destination is nil, clears the flight path."
  [[cx cy]]
  (if-let [dest (sa/read-state :destination)]
    (let [cell (get-in (sa/current-world) [cx cy])
          decision (decisions/flight-path-action (sa/current-world) cell dest)]
      (when (= :set-flight-path (:action decision))
        (apply-flight-path [cx cy] decision)))
    (let [cell (get-in (sa/current-world) [cx cy])]
      (clear-flight-path [cx cy] cell))))

(defn set-waypoint-at
  "Creates or removes a waypoint at the given coordinates."
  [[cx cy]]
  (when (waypoint/create-waypoint [cx cy])
    (let [cell (get-in (sa/current-world) [cx cy])]
      (set-command-message! (decisions/waypoint-message [cx cy] cell)))
    true))

(defn set-city-marching-orders-by-direction-at
  "Sets marching orders on a player city or waypoint to the map edge in the given direction."
  [[cx cy] k]
  (when-let [direction (config/key->direction k)]
    (let [cell (get-in (sa/current-world) [cx cy])
          decision (decisions/marching-orders-by-direction-action (sa/current-world) cell [cx cy] direction)]
      (case (:action decision)
        :set-marching-orders
        (let [target (:dest decision)]
          (sa/update-world! assoc-in [cx cy :marching-orders] target)
          (set-command-message! (str "Marching orders set to " (first target) "," (second target)))
          true)

        :set-waypoint-orders-by-direction
        (waypoint/set-waypoint-orders-by-direction [cx cy] direction)

        nil))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:06:18.32512-05:00", :module-hash "1081495824", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "129682513"} {:id "defn-/set-command-message!", :kind "defn-", :line 10, :end-line nil, :hash "6022488"} {:id "defn/add-unit-at", :kind "defn", :line 14, :end-line nil, :hash "813959733"} {:id "defn/wake-at", :kind "defn", :line 17, :end-line nil, :hash "779826581"} {:id "defn/clear-city-production-at", :kind "defn", :line 20, :end-line nil, :hash "1179071455"} {:id "defn/own-city-at", :kind "defn", :line 29, :end-line nil, :hash "-962136880"} {:id "defn/set-city-lookaround", :kind "defn", :line 37, :end-line nil, :hash "1596786633"} {:id "defn/set-destination-at", :kind "defn", :line 46, :end-line nil, :hash "1747846993"} {:id "defn/clear-destination!", :kind "defn", :line 52, :end-line nil, :hash "2039182570"} {:id "defn-/apply-marching-orders", :kind "defn-", :line 59, :end-line nil, :hash "-2110627180"} {:id "defn-/clear-city-marching-orders", :kind "defn-", :line 68, :end-line nil, :hash "-415425693"} {:id "defn-/apply-destination-marching-orders", :kind "defn-", :line 77, :end-line nil, :hash "-1036274723"} {:id "defn/set-marching-orders-at", :kind "defn", :line 90, :end-line nil, :hash "773626475"} {:id "defn-/apply-flight-path", :kind "defn-", :line 98, :end-line nil, :hash "-1199787006"} {:id "defn-/flight-path-owner?", :kind "defn-", :line 108, :end-line nil, :hash "-838561966"} {:id "defn-/clear-flight-path", :kind "defn-", :line 113, :end-line nil, :hash "225921772"} {:id "defn/set-flight-path-at", :kind "defn", :line 121, :end-line nil, :hash "1707657445"} {:id "defn/set-waypoint-at", :kind "defn", :line 133, :end-line nil, :hash "-944124602"} {:id "defn/set-city-marching-orders-by-direction-at", :kind "defn", :line 141, :end-line nil, :hash "773189592"}]}
;; clj-mutate-manifest-end
