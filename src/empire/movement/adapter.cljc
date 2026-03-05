(ns empire.movement.adapter
  (:require [empire.application.ports.unit-state :as unit-state-ports]
            [empire.application.ports.movement-execution :as exec-ports]
            [empire.application.ports.pathfinding :as path-ports]
            [empire.movement.api :as api]
            [empire.movement.movement-state :as state]
            [empire.movement.lakes :as lakes]
            [empire.movement.pathfinding :as pathfinding]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.movement.visibility :as visibility]))

(defrecord UnitStateAdapter []
  unit-state-ports/UnitStatePort
  (movement-get-active-unit [_ cell]
    (state/get-active-unit cell))
  (movement-is-army-aboard-transport? [_ active-unit]
    (state/is-army-aboard-transport? active-unit))
  (movement-is-fighter-from-airport? [_ active-unit]
    (state/is-fighter-from-airport? active-unit))
  (movement-is-fighter-from-carrier? [_ active-unit]
    (state/is-fighter-from-carrier? active-unit))
  (movement-context [_ cell active-unit]
    (state/movement-context cell active-unit))
  (movement-set-unit-mode [_ coords mode]
    (state/set-unit-mode coords mode))
  (movement-add-unit-at [_ coords unit-type owner]
    (state/add-unit-at coords unit-type owner))
  (movement-wake-at [_ coords]
    (state/wake-at coords)))

(defrecord MovementExecutionAdapter []
  exec-ports/MovementExecutionPort
  (movement-move-unit [_ coords target cell current-map]
    (api/move-unit coords target cell current-map))
  (movement-set-unit-movement [_ coords target extended?]
    (if extended?
      (api/set-unit-movement coords target true)
      (api/set-unit-movement coords target)))
  (movement-update-cell-visibility [_ pos owner]
    (visibility/update-cell-visibility pos owner))
  (movement-update-cell-visibility-with-unit [_ pos owner unit]
    (visibility/update-cell-visibility pos owner unit)))

(defrecord PathfindingAdapter []
  path-ports/PathfindingPort
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


(defn unit-state-port [] (->UnitStateAdapter))
(defn execution-port [] (->MovementExecutionAdapter))
(defn pathfinding-port [] (->PathfindingAdapter))
