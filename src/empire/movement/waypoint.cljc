(ns empire.movement.waypoint
  (:require [empire.atoms :as atoms]))

(defn create-waypoint
  "Creates a waypoint at the given coordinates if it's an empty land cell.
   If a waypoint already exists there, removes it (toggle behavior).
   Returns true if action was taken, nil otherwise."
  [[cx cy]]
  (let [cell (get-in @atoms/game-map [cx cy])]
    (cond
      ;; Toggle off existing waypoint
      (:waypoint cell)
      (do (swap! atoms/game-map update-in [cx cy] dissoc :waypoint)
          true)

      ;; Create waypoint on empty land cell
      (and (= (:type cell) :land)
           (nil? (:contents cell)))
      (do (swap! atoms/game-map assoc-in [cx cy :waypoint] {})
          true)

      :else nil)))

(defn set-waypoint-orders
  "Sets marching orders on a waypoint at the given coordinates using the current destination.
   Returns true if orders were set, nil otherwise."
  [[cx cy]]
  (when-let [dest @atoms/destination]
    (let [cell (get-in @atoms/game-map [cx cy])]
      (when (:waypoint cell)
        (swap! atoms/game-map assoc-in [cx cy :waypoint :marching-orders] dest)
        (reset! atoms/destination nil)
        (atoms/set-confirmation-message (str "Waypoint orders set to " (first dest) "," (second dest)) 2000)
        true))))

(defn set-waypoint-orders-by-direction
  "Sets marching orders on a waypoint to the map edge in the given direction.
   Returns true if orders were set, nil otherwise."
  [[cx cy] [dx dy]]
  (let [cell (get-in @atoms/game-map [cx cy])]
    (when (:waypoint cell)
      (let [cols (count @atoms/game-map)
            rows (count (first @atoms/game-map))
            target (loop [tx cx ty cy]
                     (let [nx (+ tx dx)
                           ny (+ ty dy)]
                       (if (and (>= nx 0) (< nx cols) (>= ny 0) (< ny rows))
                         (recur nx ny)
                         [tx ty])))]
        (swap! atoms/game-map assoc-in [cx cy :waypoint :marching-orders] target)
        (atoms/set-confirmation-message (str "Waypoint orders set to " (first target) "," (second target)) 2000)
        true))))
