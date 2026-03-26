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
;; {:version 1, :tested-at "2026-03-12T11:59:05.788689-05:00", :module-hash "-841832673", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "1935759957"} {:id "defn/passable-sea?", :kind "defn", :line 4, :end-line 11, :hash "-900944866"} {:id "defn/continue-pos", :kind "defn", :line 13, :end-line 19, :hash "1325348995"} {:id "defn/compute-sail-path", :kind "defn", :line 21, :end-line 25, :hash "-1187537644"}]}
;; clj-mutate-manifest-end
