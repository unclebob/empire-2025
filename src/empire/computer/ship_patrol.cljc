;; mutation-tested: 2026-02-27
(ns empire.computer.ship-patrol
  "Computer patrol boat movement - coastline crawling and BFS exploration."
  (:require [empire.atoms :as atoms]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.core :as core]
            [empire.computer.ship-core :as ship-core]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.movement.visibility :as visibility]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- find-adjacent-player-transport
  "Finds an adjacent player transport to attack."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit))
                            (= :transport (:type unit)))))
                   (core/get-neighbors pos)))))

(defn- find-adjacent-non-transport-enemy
  "Finds an adjacent player unit that is not a transport."
  [pos]
  (let [game-map @atoms/game-map]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit))
                            (not= :transport (:type unit)))))
                   (core/get-neighbors pos)))))

(defn- adjacent-to-land?
  "Returns true if the given position has at least one adjacent land or city cell."
  [pos]
  (let [game-map @atoms/game-map]
    (some (fn [neighbor]
            (let [cell (get-in game-map neighbor)]
              (and cell (#{:land :city} (:type cell)))))
          (core/get-neighbors pos))))

(defn- flee-from
  "Move patrol boat away from the given enemy position."
  [pos enemy-pos]
  (let [passable (ship-core/get-passable-sea-neighbors pos)
        empty-passable (filter (fn [n]
                                 (nil? (:contents (get-in @atoms/game-map n))))
                               passable)]
    (when (seq empty-passable)
      (let [farthest (apply max-key (partial core/distance enemy-pos) empty-passable)]
        (core/move-unit-to pos farthest)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility farthest :computer)
        farthest))))

(defn patrol-crawl-step
  "Crawl along coastline. Records position in seen-coast.
   Prefers unseen coastal cells. Switches to :exploring when
   all coastal neighbors are seen or at map edge with none unseen.
   Returns new position or nil."
  [pos]
  (swap! atoms/seen-coast conj pos)
  (let [passable (ship-core/get-passable-sea-neighbors pos)
        empty-passable (filter #(nil? (:contents (get-in @atoms/game-map %))) passable)
        coastal (filter adjacent-to-land? empty-passable)
        unseen (remove @atoms/seen-coast coastal)
        targets (if (seq unseen) unseen coastal)
        switch? (empty? unseen)]
    (when (seq targets)
      (let [target (rand-nth targets)]
        (core/move-unit-to pos target)
        (visibility/update-cell-visibility pos :computer)
        (visibility/update-cell-visibility target :computer)
        (when switch?
          (update-game-map! assoc-in
                            (conj target :contents :patrol-mode) :exploring))
        target))))

(defn- arrived-at-unseen-coast?
  "Returns true if pos is adjacent to land/city on computer-map and not in seen-coast."
  [pos]
  (and (not (contains? @atoms/seen-coast pos))
       (some (fn [neighbor]
               (let [cell (get-in @atoms/computer-map neighbor)]
                 (and cell (#{:land :city} (:type cell)))))
             (core/get-neighbors pos))))

(defn- run-bfs-and-store-path
  "Run BFS to find unseen coast, store full path on unit. Returns path or nil.
   Excludes targets already claimed by other patrol boats this round."
  [pos]
  (when-let [path (pathfinding-bfs/bfs-to-unseen-coast
                    pos @atoms/computer-map @atoms/claimed-patrol-targets)]
    (swap! atoms/claimed-patrol-targets conj (last path))
    (update-game-map! assoc-in
                      (conj pos :contents :explore-path) (vec path))
    path))

(defn- switch-to-crawling [next-pos]
  "Switch patrol boat to crawling mode and clear explore state."
  (update-game-map! update-in (conj next-pos :contents)
                    #(-> % (assoc :patrol-mode :crawling)
                         (dissoc :explore-path))))

(defn- follow-explore-path
  "Take one step along stored BFS path. Returns new pos or nil."
  [pos path]
  (let [next-pos (first path)
        rest-path (vec (rest path))]
    (if (core/move-unit-to pos next-pos)
      (do (visibility/update-cell-visibility pos :computer)
          (visibility/update-cell-visibility next-pos :computer)
          (if (arrived-at-unseen-coast? next-pos)
            (switch-to-crawling next-pos)
            (update-game-map! assoc-in
                              (conj next-pos :contents :explore-path) rest-path))
          next-pos)
      (do (update-game-map! update-in
                            (conj pos :contents) dissoc :explore-path)
          nil))))

(defn- generate-random-sea-walk
  "Generates a random walk of up to n steps over empty sea cells."
  [start n]
  (loop [pos start steps n path []]
    (if (zero? steps)
      (when (seq path) path)
      (let [neighbors (ship-core/get-passable-sea-neighbors pos)
            empty-nbrs (filter #(nil? (:contents (get-in @atoms/game-map %))) neighbors)]
        (if (empty? empty-nbrs)
          (when (seq path) path)
          (let [next-pos (rand-nth empty-nbrs)]
            (recur next-pos (dec steps) (conj path next-pos))))))))

(defn- store-random-walk
  "Generates a random sea walk and stores it as explore-path on the unit."
  [pos]
  (when-let [path (generate-random-sea-walk pos 10)]
    (update-game-map! assoc-in
                      (conj pos :contents :explore-path) (vec path))
    path))

(defn patrol-explore-step
  "Explore toward unseen coast. Stores BFS path and follows it step by step.
   Falls back to random walk when BFS finds nothing within cell limit.
   Switches to crawling on arrival at unseen coast.
   Returns new position or nil."
  [pos]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        path (:explore-path unit)]
    (if (seq path)
      (follow-explore-path pos path)
      (when (or (run-bfs-and-store-path pos)
                (store-random-walk pos))
        (let [new-path (:explore-path
                         (get-in @atoms/game-map (conj pos :contents)))]
          (follow-explore-path pos new-path))))))

(defn- patrol-boat-step
  "Execute one step of patrol boat movement. Returns new position or nil."
  [pos]
  (if-let [transport-pos (find-adjacent-player-transport pos)]
    (ship-core/attack-enemy pos transport-pos)
    (if-let [enemy-pos (find-adjacent-non-transport-enemy pos)]
      (flee-from pos enemy-pos)
      (let [unit (get-in @atoms/game-map (conj pos :contents))]
        (case (or (:patrol-mode unit) :crawling)
          :crawling (patrol-crawl-step pos)
          :exploring (patrol-explore-step pos))))))

(defn process-patrol-boat
  "Processes a computer patrol boat. Moves up to speed 4 steps per round.
   Nil results (reflections, blocked) consume a step but loop continues.
   Stops if unit is destroyed in combat."
  [pos]
  (loop [current-pos pos steps-left 4]
    (if (or (zero? steps-left)
            (nil? (get-in @atoms/game-map (conj current-pos :contents))))
      current-pos
      (if-let [new-pos (patrol-boat-step current-pos)]
        (recur new-pos (dec steps-left))
        (recur current-pos (dec steps-left))))))
