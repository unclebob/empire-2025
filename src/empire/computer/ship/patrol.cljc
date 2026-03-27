(ns empire.computer.ship.patrol
  "Computer patrol boat movement - dispatch and combat."
  (:require [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.oscillation :as oscillation]
            [empire.computer.shared.world-query :as world-query]
            [empire.computer.ship.core :as ship-core]
            [empire.computer.ship.patrol-decisions :as decisions]
            [empire.computer.ship.patrol.crawl :as crawl]
            [empire.computer.ship.patrol.explore :as explore]
            [empire.computer.ship.patrol.repulsion :as repulsion]
            [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]))

;; --- Re-exports for callers ---

(def nearby-patrol-boat-count repulsion/nearby-patrol-boat-count)
(def prefer-dispersed repulsion/prefer-dispersed)
(def patrol-crawl-step crawl/patrol-crawl-step)
(def patrol-explore-step explore/patrol-explore-step)

;; --- Helpers ---

(defn- update-cell-visibility! [pos owner]
  (visibility/update-cell-visibility pos owner))

(defn- computer-unit-at [pos]
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

;; --- Step / dispatch ---

(defn- patrol-mode-step
  "Execute one mode-based movement step. Returns new position or nil."
  [pos]
  (let [unit (computer-unit-at pos)]
    (case (or (:patrol-mode unit) :crawling)
      :crawling (crawl/patrol-crawl-step pos)
      :exploring (explore/patrol-explore-step pos))))

(defn major-invasion-step
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
    (if-let [target (when (seq empty-passable) (repulsion/prefer-dispersed current-pos (vec empty-passable) current-pos))]
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
;; {:version 1, :tested-at "2026-03-27T10:46:18.719339-05:00", :module-hash "-1789458738", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 13, :hash "134431980"} {:id "def/nearby-patrol-boat-count", :kind "def", :line 17, :end-line 17, :hash "-1466633146"} {:id "def/prefer-dispersed", :kind "def", :line 18, :end-line 18, :hash "1955101894"} {:id "def/patrol-crawl-step", :kind "def", :line 19, :end-line 19, :hash "-1163187912"} {:id "def/patrol-explore-step", :kind "def", :line 20, :end-line 20, :hash "1706077610"} {:id "defn-/update-cell-visibility!", :kind "defn-", :line 24, :end-line 25, :hash "-1102586575"} {:id "defn-/computer-unit-at", :kind "defn-", :line 27, :end-line 28, :hash "-1108811645"} {:id "defn-/find-adjacent-player-transport", :kind "defn-", :line 30, :end-line 37, :hash "1860659031"} {:id "defn-/find-adjacent-non-transport-enemy", :kind "defn-", :line 39, :end-line 46, :hash "1774905140"} {:id "defn-/flee-from", :kind "defn-", :line 48, :end-line 61, :hash "-2005029157"} {:id "defn-/patrol-mode-step", :kind "defn-", :line 65, :end-line 71, :hash "921148320"} {:id "defn/major-invasion-step", :kind "defn", :line 73, :end-line 80, :hash "-1049469881"} {:id "defn-/non-invasion-step", :kind "defn-", :line 82, :end-line 91, :hash "-1045237069"} {:id "defn-/patrol-boat-step", :kind "defn-", :line 93, :end-line 99, :hash "-1625337304"} {:id "defn-/patrol-random-walk-step", :kind "defn-", :line 101, :end-line 112, :hash "-1085347713"} {:id "defn-/process-random-walk-patrol", :kind "defn-", :line 114, :end-line 127, :hash "-716572796"} {:id "defn-/attacking-major-invasion-patrol?", :kind "defn-", :line 129, :end-line 132, :hash "-1157866042"} {:id "defn-/process-standard-patrol", :kind "defn-", :line 134, :end-line 144, :hash "-1059719863"} {:id "defn/process-patrol-boat", :kind "defn", :line 146, :end-line 159, :hash "432357682"}]}
;; clj-mutate-manifest-end
