(ns empire.player.orders-decisions)

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

(defn turn-message-state
  [msg ms now]
  {:turn-message msg
   :turn-message-until (if (= ms Long/MAX_VALUE)
                         Long/MAX_VALUE
                         (+ now ms))})

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

(defn marching-orders-state
  [path dest]
  {:path path
   :dest dest
   :clear-destination? true
   :message (str "Marching orders set to " (first dest) "," (second dest))})

(defn flight-path-state
  [path dest]
  {:path path
   :dest dest
   :clear-destination? true
   :message (str "Flight path set to " (first dest) "," (second dest))})

(defn project-to-edge
  [world [cx cy] [dx dy]]
  (let [cols (count world)
        rows (count (first world))]
    (loop [tx cx ty cy]
      (let [nx (+ tx dx)
            ny (+ ty dy)]
        (if (and (>= nx 0) (< nx cols) (>= ny 0) (< ny rows))
          (recur nx ny)
          [tx ty])))))

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
;; {:version 1, :tested-at "2026-03-16T14:25:37.958966-05:00", :module-hash "1137937525", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-1363542895"} {:id "defn/clamp-to-map-bounds", :kind "defn", :line 3, :end-line 10, :hash "1279407928"} {:id "defn/player-city?", :kind "defn", :line 12, :end-line 14, :hash "-637112885"} {:id "defn/player-transport?", :kind "defn", :line 16, :end-line 18, :hash "711738148"} {:id "defn/own-city-action", :kind "defn", :line 20, :end-line 23, :hash "-1333830954"} {:id "defn/turn-message-state", :kind "defn", :line 25, :end-line 30, :hash "723041161"} {:id "defn/city-lookaround-action", :kind "defn", :line 32, :end-line 36, :hash "-620694156"} {:id "defn/marching-orders-action", :kind "defn", :line 38, :end-line 56, :hash "1531675460"} {:id "defn/flight-path-action", :kind "defn", :line 58, :end-line 75, :hash "1932598402"} {:id "defn/waypoint-message", :kind "defn", :line 77, :end-line 81, :hash "-1182131149"} {:id "defn/marching-orders-state", :kind "defn", :line 83, :end-line 88, :hash "-1364786927"} {:id "defn/flight-path-state", :kind "defn", :line 90, :end-line 95, :hash "-52545009"} {:id "defn/project-to-edge", :kind "defn", :line 97, :end-line 106, :hash "-350541431"} {:id "defn/marching-orders-by-direction-action", :kind "defn", :line 108, :end-line 120, :hash "-1629734525"}]}
;; clj-mutate-manifest-end
