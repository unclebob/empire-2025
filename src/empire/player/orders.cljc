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

(defn set-marching-orders-at
  "Sets or clears marching orders on a player city, transport, or waypoint.
   When destination is nil, clears marching orders on the city."
  [[cx cy]]
  (if-let [dest (sa/read-state :destination)]
    (let [cell (get-in (sa/current-world) [cx cy])
          decision (decisions/marching-orders-action cell dest)]
      (case (:action decision)
        :set-marching-orders
        (apply-marching-orders (into [cx cy] (:path decision)) (:dest decision))

        :set-waypoint-orders
        (do (waypoint/set-waypoint-orders [cx cy]) true)

        nil))
    (let [cell (get-in (sa/current-world) [cx cy])]
      (when (and (= :city (:type cell)) (= :player (:city-status cell))
                 (:marching-orders cell))
        (sa/update-world! update-in [cx cy] dissoc :marching-orders)
        (set-command-message! "Marching orders cleared")
        true))))

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
;; {:version 1, :tested-at "2026-03-27T02:27:12.659863-05:00", :module-hash "-1958894766", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "129682513"} {:id "defn-/set-turn-message!", :kind "defn-", :line 10, :end-line 15, :hash "1119113768"} {:id "defn/add-unit-at", :kind "defn", :line 17, :end-line 18, :hash "813959733"} {:id "defn/wake-at", :kind "defn", :line 20, :end-line 21, :hash "779826581"} {:id "defn/own-city-at", :kind "defn", :line 23, :end-line 29, :hash "-962136880"} {:id "defn/set-city-lookaround", :kind "defn", :line 31, :end-line 38, :hash "-542328561"} {:id "defn/set-destination-at", :kind "defn", :line 40, :end-line 44, :hash "1747846993"} {:id "defn-/apply-marching-orders", :kind "defn-", :line 46, :end-line 53, :hash "1118731918"} {:id "defn/set-marching-orders-at", :kind "defn", :line 55, :end-line 68, :hash "966461920"} {:id "defn/set-flight-path-at", :kind "defn", :line 70, :end-line 83, :hash "443065763"} {:id "defn/set-waypoint-at", :kind "defn", :line 85, :end-line 91, :hash "1468860788"} {:id "defn/set-city-marching-orders-by-direction-at", :kind "defn", :line 93, :end-line 109, :hash "-1116187818"}]}
;; clj-mutate-manifest-end
