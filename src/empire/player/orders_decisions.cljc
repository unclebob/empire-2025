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
;; {:version 1, :tested-at "2026-03-15T16:18:57.473216-05:00", :module-hash "-175057864", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-1363542895"} {:id "defn/clamp-to-map-bounds", :kind "defn", :line 3, :end-line 10, :hash "1279407928"} {:id "defn/player-city?", :kind "defn", :line 12, :end-line 14, :hash "-637112885"} {:id "defn/player-transport?", :kind "defn", :line 16, :end-line 18, :hash "711738148"} {:id "defn/own-city-action", :kind "defn", :line 20, :end-line 23, :hash "-1333830954"} {:id "defn/city-lookaround-action", :kind "defn", :line 25, :end-line 29, :hash "-620694156"} {:id "defn/marching-orders-action", :kind "defn", :line 31, :end-line 49, :hash "1531675460"} {:id "defn/flight-path-action", :kind "defn", :line 51, :end-line 68, :hash "1932598402"} {:id "defn/waypoint-message", :kind "defn", :line 70, :end-line 74, :hash "-1182131149"} {:id "defn/project-to-edge", :kind "defn", :line 76, :end-line 85, :hash "-350541431"} {:id "defn/marching-orders-by-direction-action", :kind "defn", :line 87, :end-line 99, :hash "-1629734525"}]}
;; clj-mutate-manifest-end
