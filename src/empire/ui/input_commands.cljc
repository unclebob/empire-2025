(ns empire.ui.input-commands
  "Multi-step order commands and production handling.

   Handles:
   - Production key commands
   - Marching orders (destination setting)
   - Flight paths
   - Waypoints
   - Lookaround commands"
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop :as game-loop]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.movement :as movement]
            [empire.movement.waypoint :as waypoint]
            [empire.player.production :as production]
            [empire.units.dispatcher :as dispatcher]
            [quil.core :as q]))

;; Production handling

(defn- try-set-production [coords item]
  (let [[x y] coords
        coastal? (map-utils/on-coast? x y)
        naval? (dispatcher/naval-units item)]
    (if (and naval? (not coastal?))
      (atoms/set-line3-message (format "Must be coastal city to produce %s." (name item)) 3000)
      (do
        (production/set-city-production coords item)
        (game-loop/item-processed)))
    true))

(defn handle-city-production-key [k coords cell]
  (when (and (= (:type cell) :city)
             (= (:city-status cell) :player)
             (not (movement/get-active-unit cell)))
    (cond
      (= k :space) (do (swap! atoms/player-items rest)
                       (game-loop/item-processed)
                       true)
      (= k :x) (do (swap! atoms/production assoc coords :none)
                   (game-loop/item-processed)
                   true)
      (config/key->production-item k) (try-set-production coords (config/key->production-item k)))))

;; Destination and marching orders

(defn set-destination
  "Sets the destination to the given cell coordinates."
  [[cx cy]]
  (reset! atoms/destination [cx cy])
  true)

(defn set-destination-at-mouse
  "Sets the destination to the cell under the mouse cursor."
  []
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (set-destination (map-utils/determine-cell-coordinates x y)))))

(defn set-city-marching-orders
  "Sets marching orders on a player city. Returns true if set, nil otherwise."
  [coords dest]
  (let [cell (get-in @atoms/game-map coords)]
    (when (and (= (:type cell) :city)
               (= (:city-status cell) :player))
      (swap! atoms/game-map assoc-in (conj coords :marching-orders) dest)
      (reset! atoms/destination nil)
      (atoms/set-confirmation-message (str "Marching orders set to " (first dest) "," (second dest)) 2000)
      true)))

(defn set-transport-marching-orders
  "Sets marching orders on a player transport. Returns true if set, nil otherwise."
  [coords dest]
  (let [cell (get-in @atoms/game-map coords)
        contents (:contents cell)]
    (when (and (= (:type contents) :transport)
               (= (:owner contents) :player))
      (swap! atoms/game-map assoc-in (conj coords :contents :marching-orders) dest)
      (reset! atoms/destination nil)
      (atoms/set-confirmation-message (str "Marching orders set to " (first dest) "," (second dest)) 2000)
      true)))

(defn set-marching-orders-for-cell
  "Sets marching orders on cell (city, transport, or waypoint). Returns true if set."
  [coords dest]
  (let [cell (get-in @atoms/game-map coords)]
    (or (set-city-marching-orders coords dest)
        (set-transport-marching-orders coords dest)
        (when (:waypoint cell)
          (waypoint/set-waypoint-orders coords)
          true))))

(defn set-marching-orders-at-mouse
  "Sets marching orders on a player city, transport, or waypoint under the mouse to the current destination."
  []
  (when-let [dest @atoms/destination]
    (let [x (q/mouse-x)
          y (q/mouse-y)]
      (when (map-utils/on-map? x y)
        (set-marching-orders-for-cell (map-utils/determine-cell-coordinates x y) dest)))))

(defn set-city-marching-orders-to-edge
  "Sets marching orders on a player city at the given coordinates to the map edge in the given direction."
  [[cx cy] direction]
  (let [cell (get-in @atoms/game-map [cx cy])]
    (cond
      (and (= (:type cell) :city)
           (= (:city-status cell) :player))
      (let [[dx dy] direction
            cols (count @atoms/game-map)
            rows (count (first @atoms/game-map))
            target (loop [tx cx ty cy]
                     (let [nx (+ tx dx)
                           ny (+ ty dy)]
                       (if (and (>= nx 0) (< nx cols) (>= ny 0) (< ny rows))
                         (recur nx ny)
                         [tx ty])))]
        (swap! atoms/game-map assoc-in [cx cy :marching-orders] target)
        (atoms/set-confirmation-message (str "Marching orders set to " (first target) "," (second target)) 2000)
        true)

      (:waypoint cell)
      (waypoint/set-waypoint-orders-by-direction [cx cy] direction)

      :else nil)))

(defn set-city-marching-orders-by-direction
  "Sets marching orders on a player city or waypoint under the mouse to the map edge in the given direction."
  [k]
  (when-let [direction (config/key->direction k)]
    (let [x (q/mouse-x)
          y (q/mouse-y)]
      (when (map-utils/on-map? x y)
        (set-city-marching-orders-to-edge (map-utils/determine-cell-coordinates x y) direction)))))

;; Flight paths

(defn set-flight-path
  "Sets flight path on a player city or carrier at the given coordinates."
  [[cx cy] dest]
  (let [cell (get-in @atoms/game-map [cx cy])
        contents (:contents cell)]
    (cond
      (and (= (:type cell) :city)
           (= (:city-status cell) :player))
      (do (swap! atoms/game-map assoc-in [cx cy :flight-path] dest)
          (reset! atoms/destination nil)
          (atoms/set-confirmation-message (str "Flight path set to " (first dest) "," (second dest)) 2000)
          true)

      (and (= (:type contents) :carrier)
           (= (:owner contents) :player))
      (do (swap! atoms/game-map assoc-in [cx cy :contents :flight-path] dest)
          (reset! atoms/destination nil)
          (atoms/set-confirmation-message (str "Flight path set to " (first dest) "," (second dest)) 2000)
          true)

      :else nil)))

(defn set-flight-path-at-mouse
  "Sets flight path on a player city or carrier under the mouse to the current destination."
  []
  (when-let [dest @atoms/destination]
    (let [x (q/mouse-x)
          y (q/mouse-y)]
      (when (map-utils/on-map? x y)
        (set-flight-path (map-utils/determine-cell-coordinates x y) dest)))))

;; Waypoints

(defn set-waypoint
  "Creates or removes a waypoint at the given cell coordinates."
  [[cx cy]]
  (when (waypoint/create-waypoint [cx cy])
    (let [cell (get-in @atoms/game-map [cx cy])]
      (if (:waypoint cell)
        (atoms/set-confirmation-message (str "Waypoint placed at " cx "," cy) 2000)
        (atoms/set-confirmation-message (str "Waypoint removed from " cx "," cy) 2000)))
    true))

(defn set-waypoint-at-mouse
  "Creates or removes a waypoint at the cell under the mouse cursor."
  []
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (set-waypoint (map-utils/determine-cell-coordinates x y)))))

;; Lookaround

(defn set-city-lookaround
  "Sets marching orders to :lookaround on a player city at the given coordinates."
  [[cx cy]]
  (let [cell (get-in @atoms/game-map [cx cy])]
    (when (and (= (:type cell) :city)
               (= (:city-status cell) :player))
      (swap! atoms/game-map assoc-in [cx cy :marching-orders] :lookaround)
      (atoms/set-confirmation-message "Marching orders set to lookaround" 2000)
      true)))

(defn set-lookaround-at-mouse
  "Sets lookaround marching orders on a player city under the mouse cursor."
  []
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (set-city-lookaround (map-utils/determine-cell-coordinates x y)))))
