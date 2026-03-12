(ns empire.computer.army.exploration
  "Army exploration behaviors (interior, inland, random)."
  (:require [empire.state.api :as sa]
            [empire.computer.army.movement :as movement]
            [empire.computer.core :as core]))

(defn explore-randomly
  "Move toward any unexplored territory adjacent to computer's explored area.
   Only considers empty cells. Randomizes to avoid all armies picking the same cell.
   Filters out cells in move-history to prevent oscillation."
  [pos country-id]
  (let [unit (get-in (sa/current-world) (conj pos :contents))
        history (set (:move-history unit))
        empty (movement/get-empty-passable-neighbors pos country-id)
        filtered (remove history empty)
        pool (if (seq filtered) filtered empty)
        frontier (filter core/adjacent-to-computer-unexplored? pool)]
    (when-let [target (if (seq frontier)
                        (rand-nth frontier)
                        (when (seq pool) (rand-nth pool)))]
      (movement/try-move pos target))))

(defn- try-interior-move
  "Attempts to move in a direction, clearing direction if blocked or at coast."
  [pos target]
  (let [target-cell (get-in (sa/current-world) target)]
    (if (and (movement/in-bounds? target)
             (#{:land :city} (:type target-cell))
             (not= :computer (:city-status target-cell))
             (movement/try-move pos target))
      (do (when (movement/adjacent-to-sea? target)
            (sa/update-world! update-in (conj target :contents)
                              dissoc :interior-explore-direction))
          target)
      (do (sa/update-world! update-in (conj pos :contents)
                            dissoc :interior-explore-direction)
          nil))))

(defn start-interior-exploration
  "Picks a random direction and takes first step of interior exploration."
  [pos _country-id]
  (let [direction (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])
        [dc dr] direction
        [c r] pos
        target [(+ c dc) (+ r dr)]]
    (sa/update-world! assoc-in (conj pos :contents :interior-explore-direction) direction)
    (try-interior-move pos target)))

(defn process-interior-explore
  "Continues interior exploration in stored direction."
  [pos _country-id]
  (let [unit (get-in (sa/current-world) (conj pos :contents))
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
        nil)
    (let [candidates (filter (fn [n]
                               (let [cell (get-in (sa/current-world) n)]
                                 (and (movement/sovereign-passable? country-id cell)
                                      (nil? (:contents cell))
                                      (not (movement/adjacent-to-sea? n)))))
                             (core/get-neighbors pos))
          target (when (seq candidates) (rand-nth (vec candidates)))]
      (when target (movement/try-move pos target)))))

(defn- at-sea-coast? [pos]
  (and (movement/adjacent-to-sea? pos)
       (not= :city (:type (get-in (sa/current-world) pos)))))

(defn- clear-random-explore-state [pos]
  (sa/update-world! update-in (conj pos :contents)
                    #(-> % (assoc :mode :awake)
                         (dissoc :random-explore-direction :random-explore-rounds))))

(defn- try-random-direction-move [pos country-id unit]
  (let [[dc dr] (:random-explore-direction unit)
        [c r] pos
        target [(+ c dc) (+ r dr)]]
    (when (and (movement/in-bounds? target)
               (movement/sovereign-passable? country-id (get-in (sa/current-world) target))
               (nil? (:contents (get-in (sa/current-world) target)))
               (movement/try-move pos target))
      (when (at-sea-coast? target)
        (sa/update-world! assoc-in (conj target :contents :mode) :sentry))
      target)))

(defn- handle-blocked-random-explore [pos country-id]
  (if (= :city (:type (get-in (sa/current-world) pos)))
    (when-let [neighbors (seq (movement/get-empty-passable-neighbors pos country-id))]
      (movement/try-move pos (rand-nth (vec neighbors))))
    (do (clear-random-explore-state pos) nil)))

(defn process-random-explore
  "Moves army in stored random-explore direction. Goes sentry on coast or when blocked.
   Times out after 10 rounds and transitions to fill-coastal-cell."
  [pos country-id]
  (let [unit (get-in (sa/current-world) (conj pos :contents))
        rounds (:random-explore-rounds unit 0)]
    (if (>= rounds 10)
      (do (clear-random-explore-state pos) nil)
      (do (sa/update-world! update-in (conj pos :contents)
                            update :random-explore-rounds (fnil inc 0))
          (cond
            (at-sea-coast? pos)
            (do (sa/update-world! assoc-in (conj pos :contents :mode) :sentry) pos)

            :else
            (or (try-random-direction-move pos country-id unit)
                (handle-blocked-random-explore pos country-id)))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:57:19.786247-05:00", :module-hash "-923858380", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "-2023733251"} {:id "defn/explore-randomly", :kind "defn", :line 7, :end-line 21, :hash "372054611"} {:id "defn-/try-interior-move", :kind "defn-", :line 23, :end-line 37, :hash "-1296383180"} {:id "defn/start-interior-exploration", :kind "defn", :line 39, :end-line 47, :hash "-1724163131"} {:id "defn/process-interior-explore", :kind "defn", :line 49, :end-line 56, :hash "495713377"} {:id "defn/process-move-inland", :kind "defn", :line 58, :end-line 75, :hash "-1504930586"} {:id "defn-/at-sea-coast?", :kind "defn-", :line 77, :end-line 79, :hash "-1357323273"} {:id "defn-/clear-random-explore-state", :kind "defn-", :line 81, :end-line 84, :hash "719587439"} {:id "defn-/try-random-direction-move", :kind "defn-", :line 86, :end-line 96, :hash "-1623945617"} {:id "defn-/handle-blocked-random-explore", :kind "defn-", :line 98, :end-line 102, :hash "-1372413408"} {:id "defn/process-random-explore", :kind "defn", :line 104, :end-line 120, :hash "1299623537"}]}
;; clj-mutate-manifest-end
