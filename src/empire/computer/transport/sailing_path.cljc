(ns empire.computer.transport.sailing-path
  (:require [empire.computer.shared.movement :as computer-movement]))

(defn compute-sail-path
  "Compatibility wrapper for callers that still dispatch by army-count."
  [pos computer-map army-count]
  (computer-movement/bfs-to-coast-target pos computer-map army-count))

(defn compute-sail-to-unload-path
  [pos computer-map]
  (computer-movement/bfs-to-unload-target pos computer-map))

(defn compute-sail-to-load-path
  [pos computer-map]
  (computer-movement/bfs-to-load-target pos computer-map))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:16:45.367551-05:00", :module-hash "1529211547", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-122502316"} {:id "defn/compute-sail-path", :kind "defn", :line 4, :end-line 7, :hash "-2108105277"} {:id "defn/compute-sail-to-unload-path", :kind "defn", :line 9, :end-line 11, :hash "-874114760"} {:id "defn/compute-sail-to-load-path", :kind "defn", :line 13, :end-line 15, :hash "-669939444"}]}
;; clj-mutate-manifest-end
