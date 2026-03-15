(ns empire.computer.ship-patrol
  "Computer patrol boat movement - coastline crawling and BFS exploration."
  (:require [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.oscillation :as oscillation]
            [empire.computer.ship-core :as ship-core]))

(defn- update-cell-visibility!
  [pos owner]
  (visibility/update-cell-visibility pos owner))

(defn- find-adjacent-player-transport
  "Finds an adjacent player transport to attack."
  [pos]
  (let [game-map (sa/current-world)]
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
  (let [game-map (sa/current-world)]
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
  (let [game-map (sa/current-world)]
    (some (fn [neighbor]
            (let [cell (get-in game-map neighbor)]
              (and cell (#{:land :city} (:type cell)))))
          (core/get-neighbors pos))))

(defn- flee-from
  "Move patrol boat away from the given enemy position."
  [pos enemy-pos]
  (let [passable (ship-core/get-passable-sea-neighbors pos)
        empty-passable (filter (fn [n]
                                 (nil? (:contents (get-in (sa/current-world) n))))
                               passable)]
    (when (seq empty-passable)
      (let [farthest (apply max-key (partial core/distance enemy-pos) empty-passable)]
        (core/move-unit-to pos farthest)
        (update-cell-visibility! pos :computer)
        (update-cell-visibility! farthest :computer)
        farthest))))

(defn patrol-crawl-step
  "Crawl along coastline. Records position in seen-coast.
   Prefers unseen coastal cells. Switches to :exploring when
   all coastal neighbors are seen or at map edge with none unseen.
  Returns new position or nil."
  [pos]
  (let [seen-coast (or (sa/read-state :seen-coast) #{})]
    (sa/write-state! :seen-coast (conj seen-coast pos)))
  (let [passable (ship-core/get-passable-sea-neighbors pos)
        empty-passable (filter #(nil? (:contents (get-in (sa/current-world) %))) passable)
        coastal (filter adjacent-to-land? empty-passable)
        unseen (remove (or (sa/read-state :seen-coast) #{}) coastal)
        targets (if (seq unseen) unseen coastal)
        switch? (empty? unseen)]
    (when (seq targets)
      (let [target (rand-nth targets)]
        (core/move-unit-to pos target)
        (update-cell-visibility! pos :computer)
        (update-cell-visibility! target :computer)
        (when switch?
          (sa/update-world! assoc-in
                            (conj target :contents :patrol-mode) :exploring))
        target))))

(defn- arrived-at-unseen-coast?
  "Returns true if pos is adjacent to land/city on computer-map and not in seen-coast."
  [pos]
  (and (not (contains? (or (sa/read-state :seen-coast) #{}) pos))
       (some (fn [neighbor]
               (let [cell (get-in (sa/read-state :computer-map) neighbor)]
                 (and cell (#{:land :city} (:type cell)))))
             (core/get-neighbors pos))))

(defn- run-bfs-and-store-path
  "Run BFS to find unseen coast, store full path on unit. Returns path or nil.
   Excludes targets already claimed by other patrol boats this round."
  [pos]
  (when-let [path (pathfinding-bfs/bfs-to-unseen-coast
                    pos
                    (sa/read-state :computer-map)
                    (sa/read-state :claimed-patrol-targets))]
    (let [claimed (or (sa/read-state :claimed-patrol-targets) #{})]
      (sa/write-state! :claimed-patrol-targets (conj claimed (last path))))
    (sa/update-world! assoc-in
                      (conj pos :contents :explore-path) (vec path))
    path))

(defn- switch-to-crawling [next-pos]
  "Switch patrol boat to crawling mode and clear explore state."
  (sa/update-world! update-in (conj next-pos :contents)
                    #(-> % (assoc :patrol-mode :crawling)
                         (dissoc :explore-path))))

(defn- follow-explore-path
  "Take one step along stored BFS path. Returns new pos or nil."
  [pos path]
  (let [next-pos (first path)
        rest-path (vec (rest path))]
    (if (core/move-unit-to pos next-pos)
      (do (update-cell-visibility! pos :computer)
          (update-cell-visibility! next-pos :computer)
          (if (arrived-at-unseen-coast? next-pos)
            (switch-to-crawling next-pos)
            (sa/update-world! assoc-in
                              (conj next-pos :contents :explore-path) rest-path))
          next-pos)
      (do (sa/update-world! update-in
                            (conj pos :contents) dissoc :explore-path)
          nil))))

(defn- generate-random-sea-walk
  "Generates a random walk of up to n steps over empty sea cells."
  [start n]
  (loop [pos start steps n path []]
    (if (zero? steps)
      (when (seq path) path)
      (let [neighbors (ship-core/get-passable-sea-neighbors pos)
            empty-nbrs (filter #(nil? (:contents (get-in (sa/current-world) %))) neighbors)]
        (if (empty? empty-nbrs)
          (when (seq path) path)
          (let [next-pos (rand-nth empty-nbrs)]
            (recur next-pos (dec steps) (conj path next-pos))))))))

(defn- store-random-walk
  "Generates a random sea walk and stores it as explore-path on the unit."
  [pos]
  (when-let [path (generate-random-sea-walk pos 10)]
    (sa/update-world! assoc-in
                      (conj pos :contents :explore-path) (vec path))
    path))

(defn patrol-explore-step
  "Explore toward unseen coast. Stores BFS path and follows it step by step.
   Falls back to random walk when BFS finds nothing within cell limit.
   Switches to crawling on arrival at unseen coast.
  Returns new position or nil."
  [pos]
  (let [unit (get-in (sa/current-world) (conj pos :contents))
        path (:explore-path unit)]
    (if (seq path)
      (follow-explore-path pos path)
      (when (or (run-bfs-and-store-path pos)
                (store-random-walk pos))
        (let [new-path (:explore-path
                         (get-in (sa/current-world) (conj pos :contents)))]
          (follow-explore-path pos new-path))))))

(defn- patrol-mode-step
  "Execute one mode-based movement step. Returns new position or nil."
  [pos]
  (let [unit (get-in (sa/current-world) (conj pos :contents))]
    (case (or (:patrol-mode unit) :crawling)
      :crawling (patrol-crawl-step pos)
      :exploring (patrol-explore-step pos))))

(defn- major-invasion-step
  [pos]
  (if-let [enemy-pos (ship-core/find-adjacent-enemy-ship pos)]
    (ship-core/attack-enemy pos enemy-pos)
    (patrol-mode-step pos)))

(defn- non-invasion-step
  [pos]
  (or (when-let [transport-pos (find-adjacent-player-transport pos)]
        (ship-core/attack-enemy pos transport-pos))
      (when-let [enemy-pos (find-adjacent-non-transport-enemy pos)]
        (flee-from pos enemy-pos))
      (patrol-mode-step pos)))

(defn- patrol-boat-step
  "Execute one step of patrol boat movement. Returns new position or nil."
  [pos]
  (let [unit (get-in (sa/current-world) (conj pos :contents))]
    (if (:major-invasion unit)
      (major-invasion-step pos)
      (non-invasion-step pos))))

(defn- patrol-random-walk-step
  [current-pos]
  (let [passable (ship-core/get-passable-sea-neighbors current-pos)
        empty-passable (filter #(nil? (:contents (get-in (sa/current-world) %))) passable)]
    (if-let [target (when (seq empty-passable) (rand-nth empty-passable))]
      (do
        (core/move-unit-to current-pos target)
        (update-cell-visibility! current-pos :computer)
        (update-cell-visibility! target :computer)
        target)
      current-pos)))

(defn- process-random-walk-patrol
  [pos]
  (let [final-pos (loop [current-pos pos
                         steps-left 4]
                    (if (zero? steps-left)
                      current-pos
                      (recur (patrol-random-walk-step current-pos)
                             (dec steps-left))))]
    (sa/update-world! update-in (conj final-pos :contents)
                      #(-> %
                           oscillation/dec-random-walk
                           oscillation/maybe-restore))
    final-pos))

(defn- attacking-major-invasion-patrol?
  [current-pos]
  (and (:major-invasion (get-in (sa/current-world) (conj current-pos :contents)))
       (ship-core/find-adjacent-enemy-ship current-pos)))

(defn- process-standard-patrol
  [pos]
  (loop [current-pos pos steps-left 4]
    (if (or (zero? steps-left)
            (nil? (get-in (sa/current-world) (conj current-pos :contents))))
      current-pos
      (if (attacking-major-invasion-patrol? current-pos)
        (or (major-invasion-step current-pos) current-pos)
        (if-let [new-pos (patrol-boat-step current-pos)]
          (recur new-pos (dec steps-left))
          (recur current-pos (dec steps-left)))))))

(defn process-patrol-boat
  "Processes a computer patrol boat. Moves up to speed 4 steps per round.
   Nil results (reflections, blocked) consume a step but loop continues.
   Stops if unit is destroyed in combat."
  [pos]
  (let [restore-keys [:patrol-mode :explore-path]]
    (sa/update-world! update-in (conj pos :contents)
                      #(oscillation/maybe-enter-random-walk % restore-keys
                                                            {:unit-type :patrol-boat
                                                             :pos pos}))
    (if (oscillation/in-random-walk? (get-in (sa/current-world) (conj pos :contents)))
      (process-random-walk-patrol pos)
      (process-standard-patrol pos))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:58:27.948086-05:00", :module-hash "-655861476", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "1898398778"} {:id "defn-/update-cell-visibility!", :kind "defn-", :line 10, :end-line 12, :hash "-1102586575"} {:id "defn-/find-adjacent-player-transport", :kind "defn-", :line 14, :end-line 24, :hash "1563452575"} {:id "defn-/find-adjacent-non-transport-enemy", :kind "defn-", :line 26, :end-line 36, :hash "-1921282721"} {:id "defn-/adjacent-to-land?", :kind "defn-", :line 38, :end-line 45, :hash "-1700771999"} {:id "defn-/flee-from", :kind "defn-", :line 47, :end-line 59, :hash "-826184541"} {:id "defn/patrol-crawl-step", :kind "defn", :line 61, :end-line 83, :hash "1931910733"} {:id "defn-/arrived-at-unseen-coast?", :kind "defn-", :line 85, :end-line 92, :hash "-414765874"} {:id "defn-/run-bfs-and-store-path", :kind "defn-", :line 94, :end-line 106, :hash "-1691419451"} {:id "defn-/switch-to-crawling", :kind "defn-", :line 108, :end-line 112, :hash "-2033427846"} {:id "defn-/follow-explore-path", :kind "defn-", :line 114, :end-line 129, :hash "-865251015"} {:id "defn-/generate-random-sea-walk", :kind "defn-", :line 131, :end-line 142, :hash "-1068655937"} {:id "defn-/store-random-walk", :kind "defn-", :line 144, :end-line 150, :hash "-731934508"} {:id "defn/patrol-explore-step", :kind "defn", :line 152, :end-line 166, :hash "-389736091"} {:id "defn-/patrol-mode-step", :kind "defn-", :line 168, :end-line 174, :hash "-53768473"} {:id "defn-/major-invasion-step", :kind "defn-", :line 176, :end-line 180, :hash "-214934784"} {:id "defn-/non-invasion-step", :kind "defn-", :line 182, :end-line 188, :hash "668836354"} {:id "defn-/patrol-boat-step", :kind "defn-", :line 190, :end-line 196, :hash "-1795611871"} {:id "defn/process-patrol-boat", :kind "defn", :line 198, :end-line 236, :hash "-1826718201"}]}
;; clj-mutate-manifest-end
