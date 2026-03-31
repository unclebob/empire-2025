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

(defn- set-command-message!
  [msg]
  (write-runtime-state! :command-message msg))

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
        (set-command-message! (str "Waypoint orders set to " (first dest) "," (second dest)))
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
        (set-command-message! (str "Waypoint orders set to " (first target) "," (second target)))
        true))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:38:32.932973-05:00", :module-hash "-1809214551", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-1287571662"} {:id "defn-/update-game-map!", :kind "defn-", :line 4, :end-line 6, :hash "1805137569"} {:id "defn-/current-world", :kind "defn-", :line 8, :end-line 10, :hash "-640438772"} {:id "defn-/read-runtime-state", :kind "defn-", :line 12, :end-line 14, :hash "2315423"} {:id "defn-/write-runtime-state!", :kind "defn-", :line 16, :end-line 18, :hash "1105581680"} {:id "defn-/set-turn-message!", :kind "defn-", :line 20, :end-line 26, :hash "476286532"} {:id "defn/create-waypoint", :kind "defn", :line 28, :end-line 46, :hash "616206223"} {:id "defn/set-waypoint-orders", :kind "defn", :line 48, :end-line 58, :hash "46634298"} {:id "defn/set-waypoint-orders-by-direction", :kind "defn", :line 60, :end-line 77, :hash "-363045717"}]}
;; clj-mutate-manifest-end
