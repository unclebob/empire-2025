;; mutation-tested: 2026-02-25
(ns empire.player.orders
  "Standing orders on cities and units: marching orders, flight paths, waypoints.
   All functions take explicit coordinates — no Quil dependency."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.config :as config]
            [empire.movement.movement :as movement]
            [empire.movement.waypoint :as waypoint]))

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

(defn- set-turn-message!
  [msg ms]
  (write-runtime-state! :turn-message msg)
  (write-runtime-state! :turn-message-until (if (= ms Long/MAX_VALUE)
                                               Long/MAX_VALUE
                                               (+ (System/currentTimeMillis) ms))))

(defn- clamp-to-map-bounds
  [[x y]]
  (let [world (current-world)
        cols (count world)
        rows (count (first world))
        max-x (dec cols)
        max-y (dec rows)]
    [(-> x (max 0) (min max-x))
     (-> y (max 0) (min max-y))]))

(defn add-unit-at [coords unit-type owner]
  (movement/add-unit-at coords unit-type owner))

(defn wake-at [coords]
  (movement/wake-at coords))

(defn own-city-at
  "Claims a city at the given coordinates for the player."
  [[cx cy]]
  (let [cell (get-in (current-world) [cx cy])]
    (when (= (:type cell) :city)
      (update-game-map! assoc-in [cx cy :city-status] :player)
      true)))

(defn set-city-lookaround
  "Sets marching orders to :lookaround on a player city at the given coordinates."
  [[cx cy]]
  (let [cell (get-in (current-world) [cx cy])]
    (when (and (= (:type cell) :city)
               (= (:city-status cell) :player))
      (update-game-map! assoc-in [cx cy :marching-orders] :lookaround)
      (set-turn-message! "Marching orders set to lookaround" 2000)
      true)))

(defn set-destination-at
  "Sets the destination to the given coordinates."
  [[cx cy]]
  (write-runtime-state! :destination [cx cy])
  true)

(defn- apply-marching-orders [path dest]
  (update-game-map! assoc-in path dest)
  (write-runtime-state! :destination nil)
  (set-turn-message! (str "Marching orders set to " (first dest) "," (second dest)) 2000)
  true)

(defn- player-city? [cell]
  (and (= (:type cell) :city) (= (:city-status cell) :player)))

(defn- player-transport? [contents]
  (and (= (:type contents) :transport) (= (:owner contents) :player)))

(defn set-marching-orders-at
  "Sets marching orders on a player city, transport, or waypoint at the given coordinates."
  [[cx cy]]
  (when-let [dest (read-runtime-state :destination)]
    (let [cell (get-in (current-world) [cx cy])
          contents (:contents cell)]
      (cond
        (player-city? cell)
        (apply-marching-orders [cx cy :marching-orders] dest)

        (player-transport? contents)
        (apply-marching-orders [cx cy :contents :marching-orders] dest)

        (:waypoint cell)
        (do (waypoint/set-waypoint-orders [cx cy]) true)

        :else nil))))

(defn set-flight-path-at
  "Sets flight path on a player city or carrier at the given coordinates."
  [[cx cy]]
  (when-let [dest (read-runtime-state :destination)]
    (let [cell (get-in (current-world) [cx cy])
          clamped-dest (clamp-to-map-bounds dest)
          contents (:contents cell)]
      (cond
        (and (= (:type cell) :city)
             (= (:city-status cell) :player))
        (do (update-game-map! assoc-in [cx cy :flight-path] clamped-dest)
            (write-runtime-state! :destination nil)
            (set-turn-message! (str "Flight path set to " (first clamped-dest) "," (second clamped-dest)) 2000)
            true)

        (and (= (:type contents) :carrier)
             (= (:owner contents) :player))
        (do (update-game-map! assoc-in [cx cy :contents :flight-path] clamped-dest)
            (write-runtime-state! :destination nil)
            (set-turn-message! (str "Flight path set to " (first clamped-dest) "," (second clamped-dest)) 2000)
            true)

        :else nil))))

(defn set-waypoint-at
  "Creates or removes a waypoint at the given coordinates."
  [[cx cy]]
  (when (waypoint/create-waypoint [cx cy])
    (let [cell (get-in (current-world) [cx cy])]
      (if (:waypoint cell)
        (set-turn-message! (str "Waypoint placed at " cx "," cy) 2000)
        (set-turn-message! (str "Waypoint removed from " cx "," cy) 2000)))
    true))

(defn- project-to-edge [[cx cy] [dx dy]]
  (let [world (current-world)
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
    (let [cell (get-in (current-world) [cx cy])]
      (cond
        (player-city? cell)
        (let [target (project-to-edge [cx cy] direction)]
          (update-game-map! assoc-in [cx cy :marching-orders] target)
          (set-turn-message! (str "Marching orders set to " (first target) "," (second target)) 2000)
          true)

        (:waypoint cell)
        (waypoint/set-waypoint-orders-by-direction [cx cy] direction)))))
