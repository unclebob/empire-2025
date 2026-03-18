(ns empire.computer.transport-sailing-path
  (:require [empire.computer.movement :as computer-movement]))

(defn passable-sea?
  "Returns true if pos is a passable sea cell for a transport."
  [world pos]
  (let [cell (get-in world pos)]
    (and cell
         (= :sea (:type cell))
         (or (nil? (:contents cell))
             (= :computer (:owner (:contents cell)))))))

(defn continue-pos
  "Returns pos + direction vector, or nil if out of bounds or not passable sea."
  [world from to]
  (let [dr (- (first to) (first from))
        dc (- (second to) (second from))
        candidate [(+ (first to) dr) (+ (second to) dc)]]
    (when (passable-sea? world candidate) candidate)))

(defn compute-sail-path
  "Compute BFS path from transport position to a coastal target.
   Loaded transports seek unclaimed land. Empty transports seek claimed land
   outside the radius-4 safety zone."
  [pos computer-map army-count]
  (computer-movement/bfs-to-coast-target pos computer-map army-count))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:59:05.788689-05:00", :module-hash "-841832673", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "1935759957"} {:id "defn/passable-sea?", :kind "defn", :line 4, :end-line 11, :hash "-900944866"} {:id "defn/continue-pos", :kind "defn", :line 13, :end-line 19, :hash "1325348995"} {:id "defn/compute-sail-path", :kind "defn", :line 21, :end-line 25, :hash "-1187537644"}]}
;; clj-mutate-manifest-end
