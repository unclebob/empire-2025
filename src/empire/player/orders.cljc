;; mutation-tested: 2026-02-25
(ns empire.player.orders
  "Standing orders on cities and units: marching orders, flight paths, waypoints.
   All functions take explicit coordinates — no Quil dependency."
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.movement :as movement]
            [empire.movement.waypoint :as waypoint]))

(defn add-unit-at [coords unit-type owner]
  (movement/add-unit-at coords unit-type owner))

(defn wake-at [coords]
  (movement/wake-at coords))

(defn own-city-at
  "Claims a city at the given coordinates for the player."
  [[cx cy]]
  (let [cell (get-in @atoms/game-map [cx cy])]
    (when (= (:type cell) :city)
      (swap! atoms/game-map assoc-in [cx cy :city-status] :player)
      true)))

(defn set-city-lookaround
  "Sets marching orders to :lookaround on a player city at the given coordinates."
  [[cx cy]]
  (let [cell (get-in @atoms/game-map [cx cy])]
    (when (and (= (:type cell) :city)
               (= (:city-status cell) :player))
      (swap! atoms/game-map assoc-in [cx cy :marching-orders] :lookaround)
      (atoms/set-turn-message "Marching orders set to lookaround" 2000)
      true)))

(defn set-destination-at
  "Sets the destination to the given coordinates."
  [[cx cy]]
  (reset! atoms/destination [cx cy])
  true)

(defn- apply-marching-orders [path dest]
  (swap! atoms/game-map assoc-in path dest)
  (reset! atoms/destination nil)
  (atoms/set-turn-message (str "Marching orders set to " (first dest) "," (second dest)) 2000)
  true)

(defn- player-city? [cell]
  (and (= (:type cell) :city) (= (:city-status cell) :player)))

(defn- player-transport? [contents]
  (and (= (:type contents) :transport) (= (:owner contents) :player)))

(defn set-marching-orders-at
  "Sets marching orders on a player city, transport, or waypoint at the given coordinates."
  [[cx cy]]
  (when-let [dest @atoms/destination]
    (let [cell (get-in @atoms/game-map [cx cy])
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
  (when-let [dest @atoms/destination]
    (let [cell (get-in @atoms/game-map [cx cy])
          contents (:contents cell)]
      (cond
        (and (= (:type cell) :city)
             (= (:city-status cell) :player))
        (do (swap! atoms/game-map assoc-in [cx cy :flight-path] dest)
            (reset! atoms/destination nil)
            (atoms/set-turn-message (str "Flight path set to " (first dest) "," (second dest)) 2000)
            true)

        (and (= (:type contents) :carrier)
             (= (:owner contents) :player))
        (do (swap! atoms/game-map assoc-in [cx cy :contents :flight-path] dest)
            (reset! atoms/destination nil)
            (atoms/set-turn-message (str "Flight path set to " (first dest) "," (second dest)) 2000)
            true)

        :else nil))))

(defn set-waypoint-at
  "Creates or removes a waypoint at the given coordinates."
  [[cx cy]]
  (when (waypoint/create-waypoint [cx cy])
    (let [cell (get-in @atoms/game-map [cx cy])]
      (if (:waypoint cell)
        (atoms/set-turn-message (str "Waypoint placed at " cx "," cy) 2000)
        (atoms/set-turn-message (str "Waypoint removed from " cx "," cy) 2000)))
    true))

(defn- project-to-edge [[cx cy] [dx dy]]
  (let [cols (count @atoms/game-map)
        rows (count (first @atoms/game-map))]
    (loop [tx cx ty cy]
      (let [nx (+ tx dx) ny (+ ty dy)]
        (if (and (>= nx 0) (< nx cols) (>= ny 0) (< ny rows))
          (recur nx ny)
          [tx ty])))))

(defn set-city-marching-orders-by-direction-at
  "Sets marching orders on a player city or waypoint to the map edge in the given direction."
  [[cx cy] k]
  (when-let [direction (config/key->direction k)]
    (let [cell (get-in @atoms/game-map [cx cy])]
      (cond
        (player-city? cell)
        (let [target (project-to-edge [cx cy] direction)]
          (swap! atoms/game-map assoc-in [cx cy :marching-orders] target)
          (atoms/set-turn-message (str "Marching orders set to " (first target) "," (second target)) 2000)
          true)

        (:waypoint cell)
        (waypoint/set-waypoint-orders-by-direction [cx cy] direction)))))
