(ns empire.computer.army.exploration
  "Army exploration behaviors (interior, inland, random)."
  (:require [empire.state.api :as sa]
            [empire.computer.army.movement :as movement]
            [empire.computer.army.sentry :as sentry]
            [empire.computer.shared.world-query :as world-query]
            [empire.game-mechanics.visibility :as visibility]))

(defn explore-randomly
  "Move toward any unexplored territory adjacent to computer's explored area.
  Only considers empty cells. Randomizes to avoid all armies picking the same cell.
   Filters out cells in move-history to prevent oscillation."
  [pos country-id]
  (let [unit (get-in (sa/read-state :computer-map) (conj pos :contents))
        history (set (:move-history unit))
        empty (movement/get-empty-passable-neighbors pos country-id)
        filtered (remove history empty)
        pool (if (seq filtered) filtered empty)
        frontier (filter world-query/adjacent-to-computer-unexplored? pool)]
    (when-let [target (if (seq frontier)
                        (rand-nth frontier)
                        (when (seq pool) (rand-nth pool)))]
      (movement/try-move pos target))))

(defn- try-interior-move
  "Attempts to move in a direction, clearing direction if blocked or at coast."
  [pos target]
  (let [target-cell (get-in (sa/read-state :computer-map) target)]
    (if (and (movement/in-bounds? target)
             (#{:land :city} (:type target-cell))
             (not= :computer (:city-status target-cell))
             (movement/try-move pos target))
      (do (when (movement/adjacent-to-sea? target)
            (sa/update-world! update-in (conj target :contents)
                              dissoc :interior-explore-direction))
            (visibility/sync-ai-unit-to-computer-map! target)
          target)
      (do (sa/update-world! update-in (conj pos :contents)
                            dissoc :interior-explore-direction)
          (visibility/sync-ai-unit-to-computer-map! pos)
          nil))))

(defn start-interior-exploration
  "Picks a random direction and takes first step of interior exploration."
  [pos _country-id]
  (let [direction (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])
        [dc dr] direction
        [c r] pos
        target [(+ c dc) (+ r dr)]]
    (sa/update-world! assoc-in (conj pos :contents :interior-explore-direction) direction)
    (visibility/sync-ai-unit-to-computer-map! pos)
    (try-interior-move pos target)))

(defn process-interior-explore
  "Continues interior exploration in stored direction."
  [pos _country-id]
  (let [unit (get-in (sa/read-state :computer-map) (conj pos :contents))
        [dc dr] (:interior-explore-direction unit)
        [c r] pos
        target [(+ c dc) (+ r dr)]]
    (try-interior-move pos target)))

(defn process-move-inland
  "Moves army one step away from coast. Switches to :random-explore once not adjacent to sea.
   If blocked, stays in :move-inland and skips the turn."
  [pos country-id]
  (if-not (movement/adjacent-to-sea? pos)
    (do (sa/update-world! update-in (conj pos :contents)
                          assoc :mode :random-explore
                          :random-explore-direction (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])
                          :random-explore-rounds 0)
        (visibility/sync-ai-unit-to-computer-map! pos)
        nil)
    (let [computer-map (sa/read-state :computer-map)
          candidates (filter (fn [n]
                              (let [cell (get-in computer-map n)]
                                 (and (movement/sovereign-passable? country-id cell)
                                      (nil? (:contents cell))
                                      (not (movement/adjacent-to-sea? n)))))
                             (world-query/get-neighbors pos))
          target (when (seq candidates) (rand-nth (vec candidates)))]
      (when target (movement/try-move pos target)))))

(defn- at-sea-coast? [pos]
  (and (movement/adjacent-to-sea? pos)
       (not= :city (:type (get-in (sa/read-state :computer-map) pos)))))

(defn- clear-random-explore-state [pos]
  (do
    (sa/update-world! update-in (conj pos :contents)
                      #(-> % (assoc :mode :awake)
                           (dissoc :random-explore-direction :random-explore-rounds)))
    (visibility/sync-ai-unit-to-computer-map! pos)))

(defn- try-random-direction-move [pos country-id unit]
  (let [[dc dr] (:random-explore-direction unit)
        [c r] pos
        target [(+ c dc) (+ r dr)]
        computer-map (sa/read-state :computer-map)]
    (when (and (movement/in-bounds? target)
               (movement/sovereign-passable? country-id (get-in computer-map target))
               (nil? (:contents (get-in computer-map target)))
               (movement/try-move pos target))
      (when (at-sea-coast? target)
        (sentry/set-sentry-mode-if-unit! target
                                         {:operation :try-random-direction-move
                                          :from pos
                                          :country-id country-id
                                          :unit unit}))
      target)))

(defn- handle-blocked-random-explore [pos country-id]
  (if (= :city (:type (get-in (sa/read-state :computer-map) pos)))
    (when-let [neighbors (seq (movement/get-empty-passable-neighbors pos country-id))]
      (movement/try-move pos (rand-nth (vec neighbors))))
    (do (clear-random-explore-state pos) nil)))

(defn process-random-explore
  "Moves army in stored random-explore direction. Goes sentry on coast or when blocked.
   Times out after 10 rounds and transitions to fill-coastal-cell."
  [pos country-id]
  (let [unit (get-in (sa/read-state :computer-map) (conj pos :contents))
        rounds (:random-explore-rounds unit 0)]
    (if (>= rounds 10)
      (do (clear-random-explore-state pos) nil)
      (do (sa/update-world! update-in (conj pos :contents)
                            update :random-explore-rounds (fnil inc 0))
          (visibility/sync-ai-unit-to-computer-map! pos)
          (cond
            (at-sea-coast? pos)
            (do (sentry/set-sentry-mode-if-unit! pos
                                                 {:operation :process-random-explore
                                                  :country-id country-id
                                                  :unit unit})
                pos)

            :else
            (or (try-random-direction-move pos country-id unit)
                (handle-blocked-random-explore pos country-id)))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:28:56.747924-05:00", :module-hash "-2046099145", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "1196741399"} {:id "defn-/log-missing-army-contents!", :kind "defn-", :line 9, :end-line 15, :hash "-385626896"} {:id "defn-/set-sentry-mode-if-unit!", :kind "defn-", :line 17, :end-line 24, :hash "-1969414025"} {:id "defn/explore-randomly", :kind "defn", :line 26, :end-line 40, :hash "-1628845076"} {:id "defn-/try-interior-move", :kind "defn-", :line 42, :end-line 58, :hash "-381592607"} {:id "defn/start-interior-exploration", :kind "defn", :line 60, :end-line 69, :hash "-1384532113"} {:id "defn/process-interior-explore", :kind "defn", :line 71, :end-line 78, :hash "1172007844"} {:id "defn/process-move-inland", :kind "defn", :line 80, :end-line 99, :hash "1931094706"} {:id "defn-/at-sea-coast?", :kind "defn-", :line 101, :end-line 103, :hash "-935428911"} {:id "defn-/clear-random-explore-state", :kind "defn-", :line 105, :end-line 110, :hash "1943671405"} {:id "defn-/try-random-direction-move", :kind "defn-", :line 112, :end-line 127, :hash "784320100"} {:id "defn-/handle-blocked-random-explore", :kind "defn-", :line 129, :end-line 133, :hash "-532428140"} {:id "defn/process-random-explore", :kind "defn", :line 135, :end-line 156, :hash "1497581593"}]}
;; clj-mutate-manifest-end
