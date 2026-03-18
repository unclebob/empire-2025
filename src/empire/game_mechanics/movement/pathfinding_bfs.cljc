(ns empire.game-mechanics.movement.pathfinding-bfs
  "Facade namespace for BFS movement/pathfinding utilities.
   Delegates implementation to focused submodules."
  (:require [empire.game-mechanics.movement.pathfinding-bfs.cache :as cache]
            [empire.game-mechanics.movement.pathfinding-bfs.coast-targeting :as coast-targeting]
            [empire.game-mechanics.movement.pathfinding-bfs.exploration :as exploration]
            [empire.game-mechanics.movement.pathfinding-bfs.transport :as transport]))

(defn clear-bfs-caches
  "Clears all BFS caches. Called at start of each round."
  []
  (cache/clear-bfs-caches))

(defn sea-reaches-edge? [pos]
  (coast-targeting/sea-reaches-edge? pos))

(defn find-nearest-unexplored [start unit-type]
  (exploration/find-nearest-unexplored start unit-type))

(defn find-nearest-unexplored-coastline [start unit-type]
  (exploration/find-nearest-unexplored-coastline start unit-type))

(defn bfs-to-unexplored-coast [start computer-map]
  (exploration/bfs-to-unexplored-coast start computer-map))

(defn bfs-to-unowned-coast [start computer-map game-map]
  (coast-targeting/bfs-to-unowned-coast start computer-map game-map))

(defn bfs-to-coast-target [start computer-map army-count]
  (coast-targeting/bfs-to-coast-target start computer-map army-count))

(defn bfs-to-unseen-coast [start computer-map excluded]
  (exploration/bfs-to-unseen-coast start computer-map excluded))

(defn find-nearest-unload-position [start target-continent]
  (transport/find-nearest-unload-position start target-continent))

(defn bfs-to-land-ho-target [start target-city computer-map]
  (transport/bfs-to-land-ho-target start target-city computer-map))

;; Compatibility wrappers for specs that exercise private internals via var-quote.
(defn- available-for-target? [current start depth excluded]
  (exploration/available-for-target? current start depth excluded))

(defn- unexplored-target? [current best-unexplored computer-map]
  (exploration/unexplored-target? current best-unexplored computer-map))

(defn- adjacent-to-unexplored? [pos computer-map]
  (exploration/adjacent-to-unexplored? pos computer-map))

(defn- at-exploration-frontier? [pos computer-map]
  (exploration/at-exploration-frontier? pos computer-map))

(defn- adjacent-to-target-continent-land? [pos target-continent game-map]
  (transport/adjacent-to-target-continent-land? pos target-continent game-map))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:01:28.312948-05:00", :module-hash "662608719", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-810761892"} {:id "defn/clear-bfs-caches", :kind "defn", :line 9, :end-line 12, :hash "-1136808631"} {:id "defn/sea-reaches-edge?", :kind "defn", :line 14, :end-line 15, :hash "-724960758"} {:id "defn/find-nearest-unexplored", :kind "defn", :line 17, :end-line 18, :hash "-361649596"} {:id "defn/find-nearest-unexplored-coastline", :kind "defn", :line 20, :end-line 21, :hash "1551228434"} {:id "defn/bfs-to-unexplored-coast", :kind "defn", :line 23, :end-line 24, :hash "-724596430"} {:id "defn/bfs-to-unowned-coast", :kind "defn", :line 26, :end-line 27, :hash "-1137765786"} {:id "defn/bfs-to-coast-target", :kind "defn", :line 29, :end-line 30, :hash "1415858340"} {:id "defn/bfs-to-unseen-coast", :kind "defn", :line 32, :end-line 33, :hash "2114230871"} {:id "defn/find-nearest-unload-position", :kind "defn", :line 35, :end-line 36, :hash "1298617708"} {:id "defn/bfs-to-land-ho-target", :kind "defn", :line 38, :end-line 39, :hash "1176014498"} {:id "defn-/available-for-target?", :kind "defn-", :line 42, :end-line 43, :hash "-634751383"} {:id "defn-/unexplored-target?", :kind "defn-", :line 45, :end-line 46, :hash "418828879"} {:id "defn-/adjacent-to-unexplored?", :kind "defn-", :line 48, :end-line 49, :hash "-1112039003"} {:id "defn-/at-exploration-frontier?", :kind "defn-", :line 51, :end-line 52, :hash "-1133926789"} {:id "defn-/adjacent-to-target-continent-land?", :kind "defn-", :line 54, :end-line 55, :hash "1592709019"}]}
;; clj-mutate-manifest-end
