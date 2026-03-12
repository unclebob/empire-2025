(ns empire.player.orders
  "Standing orders on cities and units: marching orders, flight paths, waypoints.
   All functions take explicit coordinates — no Quil dependency."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.waypoint :as waypoint]
            [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.config.core :as config]))

(defn- set-turn-message!
  [msg ms]
  (sa/write-state! :turn-message msg)
  (sa/write-state! :turn-message-until (if (= ms Long/MAX_VALUE)
                                               Long/MAX_VALUE
                                               (+ (System/currentTimeMillis) ms))))

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
  (movement-state/add-unit-at coords unit-type owner))

(defn wake-at [coords]
  (movement-state/wake-at coords))

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
        (do (waypoint/set-waypoint-orders [cx cy]) true)

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
  (when (waypoint/create-waypoint [cx cy])
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
        (waypoint/set-waypoint-orders-by-direction [cx cy] direction)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:02:39.840921-05:00", :module-hash "-604446303", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "971444149"} {:id "defn-/set-turn-message!", :kind "defn-", :line 9, :end-line 14, :hash "961870185"} {:id "defn-/clamp-to-map-bounds", :kind "defn-", :line 16, :end-line 24, :hash "-301232408"} {:id "defn/add-unit-at", :kind "defn", :line 26, :end-line 27, :hash "813959733"} {:id "defn/wake-at", :kind "defn", :line 29, :end-line 30, :hash "779826581"} {:id "defn/own-city-at", :kind "defn", :line 32, :end-line 38, :hash "-1561046374"} {:id "defn/set-city-lookaround", :kind "defn", :line 40, :end-line 48, :hash "980716336"} {:id "defn/set-destination-at", :kind "defn", :line 50, :end-line 54, :hash "1747846993"} {:id "defn-/apply-marching-orders", :kind "defn-", :line 56, :end-line 60, :hash "778059583"} {:id "defn-/player-city?", :kind "defn-", :line 62, :end-line 63, :hash "490674704"} {:id "defn-/player-transport?", :kind "defn-", :line 65, :end-line 66, :hash "-63135200"} {:id "defn/set-marching-orders-at", :kind "defn", :line 68, :end-line 84, :hash "340495904"} {:id "defn/set-flight-path-at", :kind "defn", :line 86, :end-line 108, :hash "-588506689"} {:id "defn/set-waypoint-at", :kind "defn", :line 110, :end-line 118, :hash "1709103029"} {:id "defn-/project-to-edge", :kind "defn-", :line 120, :end-line 128, :hash "1846050668"} {:id "defn/set-city-marching-orders-by-direction-at", :kind "defn", :line 130, :end-line 143, :hash "-584599667"}]}
;; clj-mutate-manifest-end
