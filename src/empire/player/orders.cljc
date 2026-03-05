;; mutation-tested: 2026-02-25
(ns empire.player.orders
  "Standing orders on cities and units: marching orders, flight paths, waypoints.
   All functions take explicit coordinates — no Quil dependency."
  (:require [empire.application.state-access :as sa]
            [empire.application.waypoint-services :as waypoint-services]
            [empire.application.ports.unit-state :as ports]
            [empire.config :as config]))

(defn- set-turn-message!
  [msg ms]
  (sa/write-state! :turn-message msg)
  (sa/write-state! :turn-message-until (if (= ms Long/MAX_VALUE)
                                               Long/MAX_VALUE
                                               (+ (System/currentTimeMillis) ms))))

(defn- movement-port []
  (or (:movement-port (sa/state-ctx))
      (throw (ex-info "Movement port not configured in runtime state context" {}))))

(defn- clamp-to-map-bounds
  [[x y]]
  (let [world (sa/current-world)
        cols (count world)
        rows (count (first world))
        max-x (dec cols)
        max-y (dec rows)]
    [(-> x (max 0) (min max-x))
     (-> y (max 0) (min max-y))]))

(defn add-unit-at [coords unit-type owner]
  (ports/movement-add-unit-at (movement-port) coords unit-type owner))

(defn wake-at [coords]
  (ports/movement-wake-at (movement-port) coords))

(defn own-city-at
  "Claims a city at the given coordinates for the player."
  [[cx cy]]
  (let [cell (get-in (sa/current-world) [cx cy])]
    (when (= (:type cell) :city)
      (sa/update-world! assoc-in [cx cy :city-status] :player)
      true)))

(defn set-city-lookaround
  "Sets marching orders to :lookaround on a player city at the given coordinates."
  [[cx cy]]
  (let [cell (get-in (sa/current-world) [cx cy])]
    (when (and (= (:type cell) :city)
               (= (:city-status cell) :player))
      (sa/update-world! assoc-in [cx cy :marching-orders] :lookaround)
      (set-turn-message! "Marching orders set to lookaround" 2000)
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

(defn- player-city? [cell]
  (and (= (:type cell) :city) (= (:city-status cell) :player)))

(defn- player-transport? [contents]
  (and (= (:type contents) :transport) (= (:owner contents) :player)))

(defn set-marching-orders-at
  "Sets marching orders on a player city, transport, or waypoint at the given coordinates."
  [[cx cy]]
  (when-let [dest (sa/read-state :destination)]
    (let [cell (get-in (sa/current-world) [cx cy])
          contents (:contents cell)]
      (cond
        (player-city? cell)
        (apply-marching-orders [cx cy :marching-orders] dest)

        (player-transport? contents)
        (apply-marching-orders [cx cy :contents :marching-orders] dest)

        (:waypoint cell)
        (do (waypoint-services/set-waypoint-orders [cx cy]) true)

        :else nil))))

(defn set-flight-path-at
  "Sets flight path on a player city or carrier at the given coordinates."
  [[cx cy]]
  (when-let [dest (sa/read-state :destination)]
    (let [cell (get-in (sa/current-world) [cx cy])
          clamped-dest (clamp-to-map-bounds dest)
          contents (:contents cell)]
      (cond
        (and (= (:type cell) :city)
             (= (:city-status cell) :player))
        (do (sa/update-world! assoc-in [cx cy :flight-path] clamped-dest)
            (sa/write-state! :destination nil)
            (set-turn-message! (str "Flight path set to " (first clamped-dest) "," (second clamped-dest)) 2000)
            true)

        (and (= (:type contents) :carrier)
             (= (:owner contents) :player))
        (do (sa/update-world! assoc-in [cx cy :contents :flight-path] clamped-dest)
            (sa/write-state! :destination nil)
            (set-turn-message! (str "Flight path set to " (first clamped-dest) "," (second clamped-dest)) 2000)
            true)

        :else nil))))

(defn set-waypoint-at
  "Creates or removes a waypoint at the given coordinates."
  [[cx cy]]
  (when (waypoint-services/create-waypoint [cx cy])
    (let [cell (get-in (sa/current-world) [cx cy])]
      (if (:waypoint cell)
        (set-turn-message! (str "Waypoint placed at " cx "," cy) 2000)
        (set-turn-message! (str "Waypoint removed from " cx "," cy) 2000)))
    true))

(defn- project-to-edge [[cx cy] [dx dy]]
  (let [world (sa/current-world)
        cols (count world)
        rows (count (first world))]
    (loop [tx cx ty cy]
      (let [nx (+ tx dx) ny (+ ty dy)]
        (if (and (>= nx 0) (< nx cols) (>= ny 0) (< ny rows))
          (recur nx ny)
          [tx ty])))))

(defn set-city-marching-orders-by-direction-at
  "Sets marching orders on a player city or waypoint to the map edge in the given direction."
  [[cx cy] k]
  (when-let [direction (config/key->direction k)]
    (let [cell (get-in (sa/current-world) [cx cy])]
      (cond
        (player-city? cell)
        (let [target (project-to-edge [cx cy] direction)]
          (sa/update-world! assoc-in [cx cy :marching-orders] target)
          (set-turn-message! (str "Marching orders set to " (first target) "," (second target)) 2000)
          true)

        (:waypoint cell)
        (waypoint-services/set-waypoint-orders-by-direction [cx cy] direction)))))
