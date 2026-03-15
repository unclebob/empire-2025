(ns empire.player.orders
  "Standing orders on cities and units: marching orders, flight paths, waypoints.
   All functions take explicit coordinates — no Quil dependency."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.waypoint :as waypoint]
            [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.config.core :as config]
            [empire.player.orders-decisions :as decisions]))

(defn- set-turn-message!
  [msg ms]
  (sa/write-state! :turn-message msg)
  (sa/write-state! :turn-message-until (if (= ms Long/MAX_VALUE)
                                               Long/MAX_VALUE
                                               (+ (System/currentTimeMillis) ms))))

(defn add-unit-at [coords unit-type owner]
  (movement-state/add-unit-at coords unit-type owner))

(defn wake-at [coords]
  (movement-state/wake-at coords))

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
      (set-turn-message! (:message decision) 2000)
      true)))

(defn set-destination-at
  "Sets the destination to the given coordinates."
  [[cx cy]]
  (sa/write-state! :destination [cx cy])
  true)

(defn- apply-marching-orders [path dest]
  (sa/update-world! assoc-in path dest)
  (sa/write-state! :destination nil)
  (set-turn-message! (str "Marching orders set to " (first dest) "," (second dest)) 2000)
  true)

(defn set-marching-orders-at
  "Sets marching orders on a player city, transport, or waypoint at the given coordinates."
  [[cx cy]]
  (when-let [dest (sa/read-state :destination)]
    (let [cell (get-in (sa/current-world) [cx cy])
          decision (decisions/marching-orders-action cell dest)]
      (case (:action decision)
        :set-marching-orders
        (apply-marching-orders (into [cx cy] (:path decision)) (:dest decision))

        :set-waypoint-orders
        (do (waypoint/set-waypoint-orders [cx cy]) true)

        nil))))

(defn set-flight-path-at
  "Sets flight path on a player city or carrier at the given coordinates."
  [[cx cy]]
  (when-let [dest (sa/read-state :destination)]
    (let [cell (get-in (sa/current-world) [cx cy])
          decision (decisions/flight-path-action (sa/current-world) cell dest)]
      (when (= :set-flight-path (:action decision))
        (sa/update-world! assoc-in (into [cx cy] (:path decision)) (:dest decision))
        (sa/write-state! :destination nil)
        (set-turn-message! (str "Flight path set to " (first (:dest decision)) "," (second (:dest decision))) 2000)
        true))))

(defn set-waypoint-at
  "Creates or removes a waypoint at the given coordinates."
  [[cx cy]]
  (when (waypoint/create-waypoint [cx cy])
    (let [cell (get-in (sa/current-world) [cx cy])]
      (set-turn-message! (decisions/waypoint-message [cx cy] cell) 2000))
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
          (set-turn-message! (str "Marching orders set to " (first target) "," (second target)) 2000)
          true)

        :set-waypoint-orders-by-direction
        (waypoint/set-waypoint-orders-by-direction [cx cy] direction)

        nil))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T16:26:46.671876-05:00", :module-hash "647768200", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "129682513"} {:id "defn-/set-turn-message!", :kind "defn-", :line 10, :end-line 15, :hash "961870185"} {:id "defn/add-unit-at", :kind "defn", :line 17, :end-line 18, :hash "813959733"} {:id "defn/wake-at", :kind "defn", :line 20, :end-line 21, :hash "779826581"} {:id "defn/own-city-at", :kind "defn", :line 23, :end-line 29, :hash "-962136880"} {:id "defn/set-city-lookaround", :kind "defn", :line 31, :end-line 38, :hash "-542328561"} {:id "defn/set-destination-at", :kind "defn", :line 40, :end-line 44, :hash "1747846993"} {:id "defn-/apply-marching-orders", :kind "defn-", :line 46, :end-line 50, :hash "778059583"} {:id "defn/set-marching-orders-at", :kind "defn", :line 52, :end-line 65, :hash "966461920"} {:id "defn/set-flight-path-at", :kind "defn", :line 67, :end-line 77, :hash "-24168768"} {:id "defn/set-waypoint-at", :kind "defn", :line 79, :end-line 85, :hash "1468860788"} {:id "defn/set-city-marching-orders-by-direction-at", :kind "defn", :line 87, :end-line 103, :hash "-1116187818"}]}
;; clj-mutate-manifest-end
