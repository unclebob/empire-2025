(ns empire.computer.ship.patrol.explore
  "Patrol boat exploration and BFS pathfinding."
  (:require [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.ship.core :as ship-core]
            [empire.computer.ship.patrol.crawl :as crawl]
            [empire.computer.ship.patrol.repulsion :as repulsion]
            [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.movement.ray-pathfinding :as ray]
            [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]))

(defn- update-cell-visibility! [pos owner]
  (visibility/update-cell-visibility pos owner))

(defn- computer-unit-at [pos]
  (get-in (sa/read-state :computer-map) (conj pos :contents)))

(defn find-nearest-unseen-coast-target
  "Uses coastal index to find nearest unseen coastal-sea-cell visible on computer-map."
  [pos]
  (let [coastal-index (sa/read-state :coastal-index)
        computer-map (sa/read-state :computer-map)
        seen-coast (or (sa/read-state :seen-coast) #{})
        excluded (or (sa/read-state :claimed-patrol-targets) #{})
        candidates (when coastal-index
                     (filter (fn [c]
                               (and (not (contains? seen-coast c))
                                    (not (contains? excluded c))
                                    (let [cell (get-in computer-map c)]
                                      (and cell (= :sea (:type cell))))))
                             (:coastal-sea-cells coastal-index)))]
    (when (seq candidates)
      (apply min-key (fn [[r c]]
                       (+ (Math/abs (- r (first pos)))
                          (Math/abs (- c (second pos)))))
             candidates))))

(defn run-bfs-and-store-path
  "Find unseen coast target and route to it. Uses coastal index when available,
   falls back to BFS. Excludes targets claimed by other patrol boats this round."
  [pos]
  (let [target (find-nearest-unseen-coast-target pos)
        path (if target
               (ray/find-sea-path pos target (sa/read-state :computer-map))
               (pathfinding-bfs/bfs-to-unseen-coast
                 pos
                 (sa/read-state :computer-map)
                 (sa/read-state :claimed-patrol-targets)))]
    (when (seq path)
      (let [claimed (or (sa/read-state :claimed-patrol-targets) #{})]
        (sa/write-state! :claimed-patrol-targets (conj claimed (last path))))
      (sa/update-world! assoc-in
                        (conj pos :contents :explore-path) (vec path))
      (visibility/sync-ai-unit-to-computer-map! pos)
      path)))

(defn follow-explore-path
  "Take one step along stored BFS path. Returns new pos or nil."
  [pos path]
  (let [next-pos (first path)
        rest-path (vec (rest path))]
    (if (action-resolution/move-unit-to pos next-pos)
      (do (update-cell-visibility! pos :computer)
          (update-cell-visibility! next-pos :computer)
          (if (crawl/arrived-at-unseen-coast? next-pos)
            (crawl/switch-to-crawling next-pos)
            (do
              (sa/update-world! assoc-in
                                (conj next-pos :contents :explore-path) rest-path)
              (visibility/sync-ai-unit-to-computer-map! next-pos)))
          next-pos)
      (do (sa/update-world! update-in
                            (conj pos :contents) dissoc :explore-path)
          (visibility/sync-ai-unit-to-computer-map! pos)
          nil))))

(defn generate-random-sea-walk
  "Generates a random walk of up to n steps over empty sea cells."
  [start n]
  (loop [pos start steps n path []]
    (if (zero? steps)
      (when (seq path) path)
      (let [computer-map (sa/read-state :computer-map)
            neighbors (ship-core/get-passable-sea-neighbors pos)
            empty-nbrs (filter #(nil? (:contents (get-in computer-map %))) neighbors)]
        (if (empty? empty-nbrs)
          (when (seq path) path)
          (let [next-pos (repulsion/prefer-dispersed start (vec empty-nbrs) pos)]
            (recur next-pos (dec steps) (conj path next-pos))))))))

(defn store-random-walk
  "Generates a random sea walk and stores it as explore-path on the unit."
  [pos]
  (when-let [path (generate-random-sea-walk pos 10)]
    (sa/update-world! assoc-in
                      (conj pos :contents :explore-path) (vec path))
    (visibility/sync-ai-unit-to-computer-map! pos)
    path))

(defn patrol-explore-step
  "Explore toward unseen coast. Stores BFS path and follows it step by step.
   Falls back to random walk when BFS finds nothing within cell limit.
   Switches to crawling on arrival at unseen coast.
  Returns new position or nil."
  [pos]
  (let [unit (computer-unit-at pos)
        path (:explore-path unit)]
    (if (seq path)
      (follow-explore-path pos path)
      (when (or (run-bfs-and-store-path pos)
                (store-random-walk pos))
        (let [new-path (:explore-path (computer-unit-at pos))]
          (follow-explore-path pos new-path))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T10:49:52.561134-05:00", :module-hash "-415845125", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 10, :hash "-1104737066"} {:id "defn-/update-cell-visibility!", :kind "defn-", :line 12, :end-line 13, :hash "-1102586575"} {:id "defn-/computer-unit-at", :kind "defn-", :line 15, :end-line 16, :hash "-1108811645"} {:id "defn/find-nearest-unseen-coast-target", :kind "defn", :line 18, :end-line 36, :hash "-2133181843"} {:id "defn/run-bfs-and-store-path", :kind "defn", :line 38, :end-line 55, :hash "728992204"} {:id "defn/follow-explore-path", :kind "defn", :line 57, :end-line 75, :hash "300570959"} {:id "defn/generate-random-sea-walk", :kind "defn", :line 77, :end-line 89, :hash "1718981275"} {:id "defn/store-random-walk", :kind "defn", :line 91, :end-line 98, :hash "1507320071"} {:id "defn/patrol-explore-step", :kind "defn", :line 100, :end-line 113, :hash "-964020680"}]}
;; clj-mutate-manifest-end
