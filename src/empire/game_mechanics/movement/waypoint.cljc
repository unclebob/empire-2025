;; mutation-tested: 2026-02-25
(ns empire.game-mechanics.movement.waypoint
  (:require [empire.state.api :as sa]))

(defn- update-game-map!
  [f & args]
  (apply sa/update-world! f args))

(defn- current-world
  []
  (sa/current-world))

(defn- read-runtime-state
  [k]
  (sa/read-state k))

(defn- write-runtime-state!
  [k v]
  (sa/write-state! k v))

(defn- set-turn-message!
  [msg ms]
  (write-runtime-state! :turn-message msg)
  (write-runtime-state! :turn-message-until
                        (if (= ms Long/MAX_VALUE)
                          Long/MAX_VALUE
                          (+ (System/currentTimeMillis) ms))))

(defn create-waypoint
  "Creates a waypoint at the given coordinates if it's an empty land cell.
   If a waypoint already exists there, removes it (toggle behavior).
   Returns true if action was taken, nil otherwise."
  [[cx cy]]
  (let [cell (get-in (current-world) [cx cy])]
    (cond
      ;; Toggle off existing waypoint
      (:waypoint cell)
      (do (update-game-map! update-in [cx cy] dissoc :waypoint)
          true)

      ;; Create waypoint on empty land cell
      (and (= (:type cell) :land)
           (nil? (:contents cell)))
      (do (update-game-map! assoc-in [cx cy :waypoint] {})
          true)

      :else nil)))

(defn set-waypoint-orders
  "Sets marching orders on a waypoint at the given coordinates using the current destination.
   Returns true if orders were set, nil otherwise."
  [[cx cy]]
  (when-let [dest (read-runtime-state :destination)]
    (let [cell (get-in (current-world) [cx cy])]
      (when (:waypoint cell)
        (update-game-map! assoc-in [cx cy :waypoint :marching-orders] dest)
        (write-runtime-state! :destination nil)
        (set-turn-message! (str "Waypoint orders set to " (first dest) "," (second dest)) 2000)
        true))))

(defn set-waypoint-orders-by-direction
  "Sets marching orders on a waypoint to the map edge in the given direction.
   Returns true if orders were set, nil otherwise."
  [[cx cy] [dx dy]]
  (let [cell (get-in (current-world) [cx cy])]
    (when (:waypoint cell)
      (let [world (current-world)
            cols (count world)
            rows (count (first world))
            target (loop [tx cx ty cy]
                     (let [nx (+ tx dx)
                           ny (+ ty dy)]
                       (if (and (>= nx 0) (< nx cols) (>= ny 0) (< ny rows))
                         (recur nx ny)
                         [tx ty])))]
        (update-game-map! assoc-in [cx cy :waypoint :marching-orders] target)
        (set-turn-message! (str "Waypoint orders set to " (first target) "," (second target)) 2000)
        true))))
