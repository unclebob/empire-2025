(ns empire.movement.adapter
  (:require [empire.application.ports.movement :as ports]
            [empire.movement.api :as movement]
            [empire.movement.lakes :as lakes]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.movement.visibility :as visibility]))

(defrecord MovementAdapter []
  ports/MovementPort
  (movement-move-unit [_ coords target cell current-map]
    (movement/move-unit coords target cell current-map))
  (movement-get-active-unit [_ cell]
    (movement/get-active-unit cell))
  (movement-is-army-aboard-transport? [_ active-unit]
    (movement/is-army-aboard-transport? active-unit))
  (movement-is-fighter-from-airport? [_ active-unit]
    (movement/is-fighter-from-airport? active-unit))
  (movement-is-fighter-from-carrier? [_ active-unit]
    (movement/is-fighter-from-carrier? active-unit))
  (movement-context [_ cell active-unit]
    (movement/movement-context cell active-unit))
  (movement-set-unit-mode [_ coords mode]
    (movement/set-unit-mode coords mode))
  (movement-add-unit-at [_ coords unit-type owner]
    (movement/add-unit-at coords unit-type owner))
  (movement-wake-at [_ coords]
    (movement/wake-at coords))
  (movement-set-unit-movement [_ coords target extended?]
    (if extended?
      (movement/set-unit-movement coords target true)
      (movement/set-unit-movement coords target)))
  (movement-update-cell-visibility [_ pos owner]
    (visibility/update-cell-visibility pos owner))
  (movement-update-cell-visibility-with-unit [_ pos owner unit]
    (visibility/update-cell-visibility pos owner unit))
  (movement-find-nearest-unexplored [_ pos unit-type]
    (pathfinding-bfs/find-nearest-unexplored pos unit-type))
  (movement-bfs-to-unseen-coast [_ pos computer-map claimed-targets]
    (pathfinding-bfs/bfs-to-unseen-coast pos computer-map claimed-targets))
  (movement-bfs-to-land-ho-target [_ from target computer-map]
    (pathfinding-bfs/bfs-to-land-ho-target from target computer-map))
  (movement-bfs-to-coast-target [_ from computer-map]
    (pathfinding-bfs/bfs-to-coast-target from computer-map))
  (movement-next-step [_ from target unit-type passability-fn cache-key-extra]
    (pathfinding/next-step from target unit-type passability-fn cache-key-extra))
  (movement-lake-cells [_ world lake-max-cells]
    (lakes/lake-cells world lake-max-cells)))

(defn movement-port []
  (->MovementAdapter))
