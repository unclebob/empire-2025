;; mutation-tested: 2026-02-25
(ns empire.movement.waypoint
  (:require [empire.adapters.state.runtime :as runtime-state]
            [empire.application.ports :as ports]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]))

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
  (let [store (runtime-state/runtime-state-store)]
    (ports/read-runtime-state store k)))

(defn- write-runtime-state!
  [k v]
  (let [store (runtime-state/runtime-state-store)]
    (ports/write-runtime-state! store k v)))

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
        (runtime-state/set-turn-message! (str "Waypoint orders set to " (first dest) "," (second dest)) 2000)
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
        (runtime-state/set-turn-message! (str "Waypoint orders set to " (first target) "," (second target)) 2000)
        true))))
