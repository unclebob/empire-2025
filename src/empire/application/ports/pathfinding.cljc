(ns empire.application.ports.pathfinding)

(defprotocol PathfindingPort
  (movement-find-nearest-unexplored [this pos unit-type] "Find nearest unexplored target for the given unit type.")
  (movement-bfs-to-unseen-coast [this pos computer-map claimed-targets] "Find path to unseen coast for patrol exploration.")
  (movement-bfs-to-land-ho-target [this from target computer-map] "Find sea path to target coastal objective.")
  (movement-bfs-to-coast-target [this from computer-map] "Find path to best coast target.")
  (movement-next-step [this from target unit-type passability-fn cache-key-extra] "Compute next A* step toward target.")
  (movement-lake-cells [this world lake-max-cells] "Classify explored small sea components as lakes."))
