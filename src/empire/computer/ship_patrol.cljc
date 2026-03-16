(ns empire.computer.ship-patrol
  "Computer patrol boat movement - coastline crawling and BFS exploration."
  (:require [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.oscillation :as oscillation]
            [empire.computer.ship-patrol-decisions :as decisions]
            [empire.computer.ship-core :as ship-core]))

(defn- update-cell-visibility!
  [pos owner]
  (visibility/update-cell-visibility pos owner))

(defn- find-adjacent-player-transport
  "Finds an adjacent player transport to attack."
  [pos]
  (let [game-map (sa/current-world)
        neighbors (map (fn [neighbor]
                         (assoc (:contents (get-in game-map neighbor)) :pos neighbor))
                       (core/get-neighbors pos))]
    (:pos (decisions/adjacent-player-transport neighbors))))

(defn- find-adjacent-non-transport-enemy
  "Finds an adjacent player unit that is not a transport."
  [pos]
  (let [game-map (sa/current-world)
        neighbors (map (fn [neighbor]
                         (assoc (:contents (get-in game-map neighbor)) :pos neighbor))
                       (core/get-neighbors pos))]
    (:pos (decisions/adjacent-non-transport-enemy neighbors))))

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
  (let [{:keys [action target]} (decisions/patrol-action {:major-invasion true
                                                          :adjacent-enemy-ship (ship-core/find-adjacent-enemy-ship pos)})]
    (case action
      :attack (ship-core/attack-enemy pos target)
      :patrol (patrol-mode-step pos)
      nil)))

(defn- non-invasion-step
  [pos]
  (let [{:keys [action target]} (decisions/patrol-action {:major-invasion false
                                                          :adjacent-transport (find-adjacent-player-transport pos)
                                                          :adjacent-enemy (find-adjacent-non-transport-enemy pos)})]
    (case action
      :attack (ship-core/attack-enemy pos target)
      :flee (flee-from pos target)
      :patrol (patrol-mode-step pos)
      nil)))

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
                      ;; LCOV currently omits this recur-arg line even when exercised,
                      ;; so clj-mutate may report the dec site here as uncovered.
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
;; {:version 1, :tested-at "2026-03-16T07:14:57.946388-05:00", :module-hash "613465120", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 9, :hash "2132455298"} {:id "defn-/update-cell-visibility!", :kind "defn-", :line 11, :end-line 13, :hash "-1102586575"} {:id "defn-/find-adjacent-player-transport", :kind "defn-", :line 15, :end-line 22, :hash "-1314512096"} {:id "defn-/find-adjacent-non-transport-enemy", :kind "defn-", :line 24, :end-line 31, :hash "147016519"} {:id "defn-/adjacent-to-land?", :kind "defn-", :line 33, :end-line 40, :hash "-1700771999"} {:id "defn-/flee-from", :kind "defn-", :line 42, :end-line 54, :hash "-826184541"} {:id "defn/patrol-crawl-step", :kind "defn", :line 56, :end-line 78, :hash "-1482926849"} {:id "defn-/arrived-at-unseen-coast?", :kind "defn-", :line 80, :end-line 87, :hash "-414765874"} {:id "defn-/run-bfs-and-store-path", :kind "defn-", :line 89, :end-line 101, :hash "-1691419451"} {:id "defn-/switch-to-crawling", :kind "defn-", :line 103, :end-line 107, :hash "842111692"} {:id "defn-/follow-explore-path", :kind "defn-", :line 109, :end-line 124, :hash "-865251015"} {:id "defn-/generate-random-sea-walk", :kind "defn-", :line 126, :end-line 137, :hash "-197687084"} {:id "defn-/store-random-walk", :kind "defn-", :line 139, :end-line 145, :hash "-731934508"} {:id "defn/patrol-explore-step", :kind "defn", :line 147, :end-line 161, :hash "-389736091"} {:id "defn-/patrol-mode-step", :kind "defn-", :line 163, :end-line 169, :hash "-53768473"} {:id "defn-/major-invasion-step", :kind "defn-", :line 171, :end-line 178, :hash "691060817"} {:id "defn-/non-invasion-step", :kind "defn-", :line 180, :end-line 189, :hash "-1045237069"} {:id "defn-/patrol-boat-step", :kind "defn-", :line 191, :end-line 197, :hash "-1795611871"} {:id "defn-/patrol-random-walk-step", :kind "defn-", :line 199, :end-line 209, :hash "1488646946"} {:id "defn-/process-random-walk-patrol", :kind "defn-", :line 211, :end-line 223, :hash "1412742770"} {:id "defn-/attacking-major-invasion-patrol?", :kind "defn-", :line 225, :end-line 228, :hash "-369145064"} {:id "defn-/process-standard-patrol", :kind "defn-", :line 230, :end-line 240, :hash "401771249"} {:id "defn/process-patrol-boat", :kind "defn", :line 242, :end-line 254, :hash "415532834"}]}
;; clj-mutate-manifest-end
