(ns empire.computer.ship.patrol
  "Computer patrol boat movement - coastline crawling and BFS exploration."
  (:require [empire.game-mechanics.movement.pathfinding-bfs :as pathfinding-bfs]
            [empire.game-mechanics.movement.ray-pathfinding :as ray]
            [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.oscillation :as oscillation]
            [empire.computer.ship.patrol-decisions :as decisions]
            [empire.computer.shared.world-query :as world-query]
            [empire.computer.ship.core :as ship-core]))

(defn- update-cell-visibility!
  [pos owner]
  (visibility/update-cell-visibility pos owner))

(defn- computer-unit-at
  [pos]
  (get-in (sa/read-state :computer-map) (conj pos :contents)))

(defn- find-adjacent-player-transport
  "Finds an adjacent player transport to attack."
  [pos]
  (let [game-map (sa/read-state :computer-map)
        neighbors (map (fn [neighbor]
                         (assoc (:contents (get-in game-map neighbor)) :pos neighbor))
                       (world-query/get-neighbors pos))]
    (:pos (decisions/adjacent-player-transport neighbors))))

(defn- find-adjacent-non-transport-enemy
  "Finds an adjacent player unit that is not a transport."
  [pos]
  (let [game-map (sa/read-state :computer-map)
        neighbors (map (fn [neighbor]
                         (assoc (:contents (get-in game-map neighbor)) :pos neighbor))
                       (world-query/get-neighbors pos))]
    (:pos (decisions/adjacent-non-transport-enemy neighbors))))

(defn- adjacent-to-land?
  "Returns true if the given position has at least one adjacent land or city cell."
  [pos]
  (let [game-map (sa/read-state :computer-map)]
    (some (fn [neighbor]
            (let [cell (get-in game-map neighbor)]
              (and cell (#{:land :city} (:type cell)))))
          (world-query/get-neighbors pos))))

(defn- flee-from
  "Move patrol boat away from the given enemy position."
  [pos enemy-pos]
  (let [computer-map (sa/read-state :computer-map)
        passable (ship-core/get-passable-sea-neighbors pos)
        empty-passable (filter (fn [n]
                                 (nil? (:contents (get-in computer-map n))))
                               passable)]
    (when (seq empty-passable)
      (let [farthest (apply max-key (partial grid/distance enemy-pos) empty-passable)]
        (action-resolution/move-unit-to pos farthest)
        (update-cell-visibility! pos :computer)
        (update-cell-visibility! farthest :computer)
        farthest))))

(defn nearby-patrol-boat-count
  "Counts computer patrol boats within radius of pos, excluding the one at self-pos."
  [pos self-pos radius]
  (let [computer-map (sa/read-state :computer-map)
        [px py] pos]
    (count (for [dx (range (- radius) (inc radius))
                 dy (range (- radius) (inc radius))
                 :let [nx (+ px dx) ny (+ py dy)
                       npos [nx ny]]
                 :when (and (not= npos self-pos)
                            (not (and (zero? dx) (zero? dy))))
                 :let [unit (:contents (get-in computer-map npos))]
                 :when (and (= :patrol-boat (:type unit))
                            (= :computer (:owner unit)))]
             npos))))

(defn prefer-dispersed
  "From candidates, pick the one farthest from other patrol boats.
   Falls back to rand-nth if all equally clear."
  [self-pos candidates current-pos]
  (if (<= (count candidates) 1)
    (first candidates)
    (let [scored (map (fn [c] [c (nearby-patrol-boat-count c self-pos 3)]) candidates)
          min-score (apply min (map second scored))
          best (map first (filter #(= min-score (second %)) scored))]
      (if (= (count best) 1)
        (first best)
        (rand-nth (vec best))))))

(defn patrol-crawl-step
  "Crawl along coastline. Records position in seen-coast.
   Prefers unseen coastal cells. Switches to :exploring when
   all coastal neighbors are seen or at map edge with none unseen.
  Returns new position or nil."
  [pos]
  (let [seen-coast (or (sa/read-state :seen-coast) #{})]
    (sa/write-state! :seen-coast (conj seen-coast pos)))
  (let [computer-map (sa/read-state :computer-map)
        passable (ship-core/get-passable-sea-neighbors pos)
        empty-passable (filter #(nil? (:contents (get-in computer-map %))) passable)
        coastal (filter adjacent-to-land? empty-passable)
        unseen (remove (or (sa/read-state :seen-coast) #{}) coastal)
        targets (if (seq unseen) unseen coastal)
        switch? (empty? unseen)]
    (when (seq targets)
      (let [target (prefer-dispersed pos targets pos)]
        (action-resolution/move-unit-to pos target)
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
             (world-query/get-neighbors pos))))

(defn- find-nearest-unseen-coast-target
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

(defn- run-bfs-and-store-path
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

(defn- switch-to-crawling [next-pos]
  "Switch patrol boat to crawling mode and clear explore state."
  (sa/update-world! update-in (conj next-pos :contents)
                    #(-> % (assoc :patrol-mode :crawling)
                         (dissoc :explore-path)))
  (visibility/sync-ai-unit-to-computer-map! next-pos))

(defn- follow-explore-path
  "Take one step along stored BFS path. Returns new pos or nil."
  [pos path]
  (let [next-pos (first path)
        rest-path (vec (rest path))]
    (if (action-resolution/move-unit-to pos next-pos)
      (do (update-cell-visibility! pos :computer)
          (update-cell-visibility! next-pos :computer)
          (if (arrived-at-unseen-coast? next-pos)
            (switch-to-crawling next-pos)
            (do
              (sa/update-world! assoc-in
                                (conj next-pos :contents :explore-path) rest-path)
              (visibility/sync-ai-unit-to-computer-map! next-pos)))
          next-pos)
      (do (sa/update-world! update-in
                            (conj pos :contents) dissoc :explore-path)
          (visibility/sync-ai-unit-to-computer-map! pos)
          nil))))

(defn- generate-random-sea-walk
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
          (let [next-pos (prefer-dispersed start (vec empty-nbrs) pos)]
            (recur next-pos (dec steps) (conj path next-pos))))))))

(defn- store-random-walk
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

(defn- patrol-mode-step
  "Execute one mode-based movement step. Returns new position or nil."
  [pos]
  (let [unit (computer-unit-at pos)]
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
  (let [unit (computer-unit-at pos)]
    (if (:major-invasion unit)
      (major-invasion-step pos)
      (non-invasion-step pos))))

(defn- patrol-random-walk-step
  [current-pos]
  (let [computer-map (sa/read-state :computer-map)
        passable (ship-core/get-passable-sea-neighbors current-pos)
        empty-passable (filter #(nil? (:contents (get-in computer-map %))) passable)]
    (if-let [target (when (seq empty-passable) (prefer-dispersed current-pos (vec empty-passable) current-pos))]
      (do
        (action-resolution/move-unit-to current-pos target)
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
    (visibility/sync-ai-unit-to-computer-map! final-pos)
    final-pos))

(defn- attacking-major-invasion-patrol?
  [current-pos]
  (and (:major-invasion (computer-unit-at current-pos))
       (ship-core/find-adjacent-enemy-ship current-pos)))

(defn- process-standard-patrol
  [pos]
  (loop [current-pos pos steps-left 4]
    (if (or (zero? steps-left)
            (nil? (computer-unit-at current-pos)))
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
    (visibility/sync-ai-unit-to-computer-map! pos)
    (if (oscillation/in-random-walk? (computer-unit-at pos))
      (process-random-walk-patrol pos)
      (process-standard-patrol pos))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T20:47:51.544905-05:00", :module-hash "-1542776778", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 11, :hash "865583525"} {:id "defn-/update-cell-visibility!", :kind "defn-", :line 13, :end-line 15, :hash "-1102586575"} {:id "defn-/computer-unit-at", :kind "defn-", :line 17, :end-line 19, :hash "-1108811645"} {:id "defn-/find-adjacent-player-transport", :kind "defn-", :line 21, :end-line 28, :hash "1860659031"} {:id "defn-/find-adjacent-non-transport-enemy", :kind "defn-", :line 30, :end-line 37, :hash "1774905140"} {:id "defn-/adjacent-to-land?", :kind "defn-", :line 39, :end-line 46, :hash "-503511394"} {:id "defn-/flee-from", :kind "defn-", :line 48, :end-line 61, :hash "-2005029157"} {:id "defn/nearby-patrol-boat-count", :kind "defn", :line 63, :end-line 77, :hash "-1155909470"} {:id "defn/prefer-dispersed", :kind "defn", :line 79, :end-line 90, :hash "842639306"} {:id "defn/patrol-crawl-step", :kind "defn", :line 92, :end-line 115, :hash "-930769669"} {:id "defn-/arrived-at-unseen-coast?", :kind "defn-", :line 117, :end-line 124, :hash "-65237802"} {:id "defn-/run-bfs-and-store-path", :kind "defn-", :line 126, :end-line 139, :hash "-1541537017"} {:id "defn-/switch-to-crawling", :kind "defn-", :line 141, :end-line 146, :hash "-488013839"} {:id "defn-/follow-explore-path", :kind "defn-", :line 148, :end-line 166, :hash "-526925132"} {:id "defn-/generate-random-sea-walk", :kind "defn-", :line 168, :end-line 180, :hash "-63283780"} {:id "defn-/store-random-walk", :kind "defn-", :line 182, :end-line 189, :hash "1350074739"} {:id "defn/patrol-explore-step", :kind "defn", :line 191, :end-line 204, :hash "-964020680"} {:id "defn-/patrol-mode-step", :kind "defn-", :line 206, :end-line 212, :hash "-685232392"} {:id "defn-/major-invasion-step", :kind "defn-", :line 214, :end-line 221, :hash "691060817"} {:id "defn-/non-invasion-step", :kind "defn-", :line 223, :end-line 232, :hash "-1045237069"} {:id "defn-/patrol-boat-step", :kind "defn-", :line 234, :end-line 240, :hash "-1625337304"} {:id "defn-/patrol-random-walk-step", :kind "defn-", :line 242, :end-line 253, :hash "2055675527"} {:id "defn-/process-random-walk-patrol", :kind "defn-", :line 255, :end-line 270, :hash "1244705546"} {:id "defn-/attacking-major-invasion-patrol?", :kind "defn-", :line 272, :end-line 275, :hash "-1157866042"} {:id "defn-/process-standard-patrol", :kind "defn-", :line 277, :end-line 287, :hash "-1059719863"} {:id "defn/process-patrol-boat", :kind "defn", :line 289, :end-line 302, :hash "1984257275"}]}
;; clj-mutate-manifest-end
