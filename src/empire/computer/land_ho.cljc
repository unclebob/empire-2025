(ns empire.computer.land-ho
  "Round-start assignment of land-ho invasion targets to transports.
   Checks the FIFO queue of discovered free cities and assigns the nearest
   qualifying transport to invade each target."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.movement :as computer-movement]
            [empire.game-mechanics.movement.visibility :as visibility]))

(defn- find-sailing-transports
  "Returns list of [pos transport] for computer transports in sailing mode
   with 4+ armies."
  []
  (let [game-map (sa/read-state :computer-map)
        height (count game-map)
        width (count (first game-map))]
    (for [r (range height)
          c (range width)
          :let [cell (get-in game-map [r c])
                unit (:contents cell)]
          :when (and unit
                     (= :transport (:type unit))
                     (= :computer (:owner unit))
                     (= :sailing (:transport-mission unit))
                     (>= (:army-count unit 0) 4))]
      [[r c] unit])))

(defn- nearest-transport
  "Returns [pos transport] of the transport nearest to target, or nil."
  [transports target]
  (when (seq transports)
    (apply min-key (fn [[pos _]] (core/distance pos target)) transports)))

(defn assign-land-ho-invasion
  "At round start, try to assign the first land-ho target to the nearest
   qualifying transport. One assignment per round."
  []
  (when-let [targets (seq (sa/read-state :land-ho-targets))]
    (let [target (first targets)
          transports (find-sailing-transports)]
      (if (empty? transports)
        nil ;; No qualifying transports -- leave target at front
        (let [[tpos _] (nearest-transport transports target)
              path (computer-movement/bfs-to-land-ho-target
                     tpos target (sa/read-state :computer-map))]
          (if path
            (do
              ;; Assign transport to invading mode
              (sa/update-world! assoc-in
                                (conj tpos :contents :transport-mission) :invading)
              (sa/update-world! assoc-in
                                (conj tpos :contents :invasion-target) target)
              (sa/update-world! assoc-in
                                (conj tpos :contents :invasion-path) (vec path))
              (visibility/sync-ai-unit-to-computer-map! tpos)
              ;; Remove target from queue
              (sa/write-state! :land-ho-targets (vec (rest targets))))
            ;; BFS failed -- move target to end of queue
            (sa/write-state! :land-ho-targets
                              (conj (vec (rest targets)) target))))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:57:53.544887-05:00", :module-hash "-1694485085", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-362365515"} {:id "defn-/find-sailing-transports", :kind "defn-", :line 9, :end-line 25, :hash "302493609"} {:id "defn-/nearest-transport", :kind "defn-", :line 27, :end-line 31, :hash "-1361509551"} {:id "defn/assign-land-ho-invasion", :kind "defn", :line 33, :end-line 58, :hash "-1966366827"}]}
;; clj-mutate-manifest-end
