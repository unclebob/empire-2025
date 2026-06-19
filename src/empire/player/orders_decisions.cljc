(ns empire.player.orders-decisions
  (:require [empire.player.movement-support :as movement-support]))

(defn clamp-to-map-bounds
  [world [x y]]
  (let [cols (count world)
        rows (count (first world))
        max-x (dec cols)
        max-y (dec rows)]
    [(-> x (max 0) (min max-x))
     (-> y (max 0) (min max-y))]))

(defn player-city?
  [cell]
  (and (= (:type cell) :city) (= (:city-status cell) :player)))

(defn player-transport?
  [contents]
  (and (= (:type contents) :transport) (= (:owner contents) :player)))

(defn own-city-action
  [cell]
  (when (= (:type cell) :city)
    {:action :claim-city}))

(defn city-lookaround-action
  [cell]
  (when (player-city? cell)
    {:action :set-lookaround
     :message "Marching orders set to lookaround"}))

(defn marching-orders-action
  [cell dest]
  (let [contents (:contents cell)]
    (cond
      (nil? dest) nil
      (player-city? cell)
      {:action :set-marching-orders
       :path [:marching-orders]
       :dest dest}

      (player-transport? contents)
      {:action :set-marching-orders
       :path [:contents :marching-orders]
       :dest dest}

      (:waypoint cell)
      {:action :set-waypoint-orders}

      :else nil)))

(defn flight-path-action
  [world cell dest]
  (let [contents (:contents cell)
        clamped-dest (clamp-to-map-bounds world dest)]
    (cond
      (nil? dest) nil
      (player-city? cell)
      {:action :set-flight-path
       :path [:flight-path]
       :dest clamped-dest}

      (and (= (:type contents) :carrier)
           (= (:owner contents) :player))
      {:action :set-flight-path
       :path [:contents :flight-path]
       :dest clamped-dest}

      :else nil)))

(defn waypoint-message
  [coords cell]
  (if (:waypoint cell)
    (str "Waypoint placed at " (first coords) "," (second coords))
    (str "Waypoint removed from " (first coords) "," (second coords))))

(defn- orders-state
  [path dest message-prefix]
  {:path path
   :dest dest
   :clear-destination? true
   :message (str message-prefix (first dest) "," (second dest))})

(defn marching-orders-state
  [path dest]
  (orders-state path dest "Marching orders set to "))

(defn flight-path-state
  [path dest]
  (orders-state path dest "Flight path set to "))

(defn project-to-edge
  [world coords direction]
  (movement-support/calculate-extended-target world coords direction))

(defn marching-orders-by-direction-action
  [world cell coords direction]
  (cond
    (nil? direction) nil
    (player-city? cell)
    {:action :set-marching-orders
     :dest (project-to-edge world coords direction)}

    (:waypoint cell)
    {:action :set-waypoint-orders-by-direction
     :direction direction}

    :else nil))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-06-19T12:41:35.48493-05:00", :module-hash "1644889113", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "1459761518"} {:id "defn/clamp-to-map-bounds", :kind "defn", :line 4, :end-line 11, :hash "1279407928"} {:id "defn/player-city?", :kind "defn", :line 13, :end-line 15, :hash "-637112885"} {:id "defn/player-transport?", :kind "defn", :line 17, :end-line 19, :hash "711738148"} {:id "defn/own-city-action", :kind "defn", :line 21, :end-line 24, :hash "-1333830954"} {:id "defn/city-lookaround-action", :kind "defn", :line 26, :end-line 30, :hash "-620694156"} {:id "defn/marching-orders-action", :kind "defn", :line 32, :end-line 50, :hash "1531675460"} {:id "defn/flight-path-action", :kind "defn", :line 52, :end-line 69, :hash "1932598402"} {:id "defn/waypoint-message", :kind "defn", :line 71, :end-line 75, :hash "-1182131149"} {:id "defn-/orders-state", :kind "defn-", :line 77, :end-line 82, :hash "1229358130"} {:id "defn/marching-orders-state", :kind "defn", :line 84, :end-line 86, :hash "1088566856"} {:id "defn/flight-path-state", :kind "defn", :line 88, :end-line 90, :hash "-2119665501"} {:id "defn/project-to-edge", :kind "defn", :line 92, :end-line 94, :hash "-73360839"} {:id "defn/marching-orders-by-direction-action", :kind "defn", :line 96, :end-line 108, :hash "-1629734525"}]}
;; clj-mutate-manifest-end
