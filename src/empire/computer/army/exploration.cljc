(ns empire.computer.army.exploration
  "Army exploration behaviors (interior, inland, random)."
  (:require [empire.atoms :as atoms]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.army.movement :as movement]
            [empire.computer.core :as core]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn explore-randomly
  "Move toward any unexplored territory adjacent to computer's explored area.
   Only considers empty cells. Randomizes to avoid all armies picking the same cell.
   Filters out cells in move-history to prevent oscillation."
  [pos country-id]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
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
  (let [target-cell (get-in @atoms/game-map target)]
    (if (and (movement/in-bounds? target)
             (#{:land :city} (:type target-cell))
             (not= :computer (:city-status target-cell))
             (movement/try-move pos target))
      (do (when (movement/adjacent-to-sea? target)
            (update-game-map! update-in (conj target :contents)
                              dissoc :interior-explore-direction))
          target)
      (do (update-game-map! update-in (conj pos :contents)
                            dissoc :interior-explore-direction)
          nil))))

(defn start-interior-exploration
  "Picks a random direction and takes first step of interior exploration."
  [pos _country-id]
  (let [direction (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])
        [dc dr] direction
        [c r] pos
        target [(+ c dc) (+ r dr)]]
    (update-game-map! assoc-in (conj pos :contents :interior-explore-direction) direction)
    (try-interior-move pos target)))

(defn process-interior-explore
  "Continues interior exploration in stored direction."
  [pos _country-id]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        [dc dr] (:interior-explore-direction unit)
        [c r] pos
        target [(+ c dc) (+ r dr)]]
    (try-interior-move pos target)))

(defn process-move-inland
  "Moves army one step away from coast. Switches to :random-explore once not adjacent to sea.
   If blocked, stays in :move-inland and skips the turn."
  [pos country-id]
  (if-not (movement/adjacent-to-sea? pos)
    (do (update-game-map! update-in (conj pos :contents)
                          assoc :mode :random-explore
                          :random-explore-direction (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]])
                          :random-explore-rounds 0)
        nil)
    (let [candidates (filter (fn [n]
                               (let [cell (get-in @atoms/game-map n)]
                                 (and (movement/sovereign-passable? country-id cell)
                                      (nil? (:contents cell))
                                      (not (movement/adjacent-to-sea? n)))))
                             (core/get-neighbors pos))
          target (when (seq candidates) (rand-nth (vec candidates)))]
      (when target (movement/try-move pos target)))))

(defn- at-sea-coast? [pos]
  (and (movement/adjacent-to-sea? pos)
       (not= :city (:type (get-in @atoms/game-map pos)))))

(defn- clear-random-explore-state [pos]
  (update-game-map! update-in (conj pos :contents)
                    #(-> % (assoc :mode :awake)
                         (dissoc :random-explore-direction :random-explore-rounds))))

(defn- try-random-direction-move [pos country-id unit]
  (let [[dc dr] (:random-explore-direction unit)
        [c r] pos
        target [(+ c dc) (+ r dr)]]
    (when (and (movement/in-bounds? target)
               (movement/sovereign-passable? country-id (get-in @atoms/game-map target))
               (nil? (:contents (get-in @atoms/game-map target)))
               (movement/try-move pos target))
      (when (at-sea-coast? target)
        (update-game-map! assoc-in (conj target :contents :mode) :sentry))
      target)))

(defn- handle-blocked-random-explore [pos country-id]
  (if (= :city (:type (get-in @atoms/game-map pos)))
    (when-let [neighbors (seq (movement/get-empty-passable-neighbors pos country-id))]
      (movement/try-move pos (rand-nth (vec neighbors))))
    (do (clear-random-explore-state pos) nil)))

(defn process-random-explore
  "Moves army in stored random-explore direction. Goes sentry on coast or when blocked.
   Times out after 10 rounds and transitions to fill-coastal-cell."
  [pos country-id]
  (let [unit (get-in @atoms/game-map (conj pos :contents))
        rounds (:random-explore-rounds unit 0)]
    (if (>= rounds 10)
      (do (clear-random-explore-state pos) nil)
      (do (update-game-map! update-in (conj pos :contents)
                            update :random-explore-rounds (fnil inc 0))
          (cond
            (at-sea-coast? pos)
            (do (update-game-map! assoc-in (conj pos :contents :mode) :sentry) pos)

            :else
            (or (try-random-direction-move pos country-id unit)
                (handle-blocked-random-explore pos country-id)))))))
