;; mutation-tested: no
(ns empire.computer.shared.movement
  (:require [empire.game-mechanics.movement.lakes :as lakes]
            [empire.game-mechanics.movement.pathfinding :as pathfinding]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]))

(defn update-cell-visibility!
  [pos owner]
  (visibility/update-cell-visibility pos owner))

(defn update-cell-visibility-with-unit!
  [pos owner unit]
  (visibility/update-cell-visibility pos owner unit))

(defn update-unit-and-sync!
  [pos f & args]
  (apply sa/update-world! update-in (conj pos :contents) f args)
  (visibility/sync-ai-unit-to-computer-map! pos))

(defn find-nearest-unexplored
  [pos unit-type]
  (pathfinding-bfs/find-nearest-unexplored pos unit-type))

(defn bfs-to-unseen-coast
  [pos computer-map claimed-targets]
  (pathfinding-bfs/bfs-to-unseen-coast pos computer-map claimed-targets))

(defn bfs-to-land-ho-target
  [from target computer-map]
  (pathfinding-bfs/bfs-to-land-ho-target from target computer-map))

(defn bfs-to-coast-target
  [from computer-map army-count]
  (pathfinding-bfs/bfs-to-coast-target from computer-map army-count))

(defn bfs-to-unload-target
  [from computer-map]
  (pathfinding-bfs/bfs-to-unload-target from computer-map))

(defn bfs-to-load-target
  [from computer-map]
  (pathfinding-bfs/bfs-to-load-target from computer-map))

(defn next-step
  ([from target unit-type]
   (pathfinding/next-step from target unit-type nil nil))
  ([from target unit-type passability-fn cache-key-extra]
   (pathfinding/next-step from target unit-type passability-fn cache-key-extra)))

(defn lake-cells
  [world lake-max-cells]
  (lakes/lake-cells world lake-max-cells))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:13:38.071963-05:00", :module-hash "-1676825177", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 6, :hash "1340532088"} {:id "defn/update-cell-visibility!", :kind "defn", :line 8, :end-line 10, :hash "2091185304"} {:id "defn/update-cell-visibility-with-unit!", :kind "defn", :line 12, :end-line 14, :hash "995877984"} {:id "defn/find-nearest-unexplored", :kind "defn", :line 16, :end-line 18, :hash "-1907967911"} {:id "defn/bfs-to-unseen-coast", :kind "defn", :line 20, :end-line 22, :hash "-1293530870"} {:id "defn/bfs-to-land-ho-target", :kind "defn", :line 24, :end-line 26, :hash "-582620538"} {:id "defn/bfs-to-coast-target", :kind "defn", :line 28, :end-line 30, :hash "-966395380"} {:id "defn/bfs-to-unload-target", :kind "defn", :line 32, :end-line 34, :hash "-409929024"} {:id "defn/bfs-to-load-target", :kind "defn", :line 36, :end-line 38, :hash "-197637091"} {:id "defn/next-step", :kind "defn", :line 40, :end-line 44, :hash "700421114"} {:id "defn/lake-cells", :kind "defn", :line 46, :end-line 48, :hash "-1317429819"}]}
;; clj-mutate-manifest-end
