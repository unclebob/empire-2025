;; mutation-tested: 2026-03-02
(ns empire.computer.army.coastal
  "Coastal movement, coast-walk, and coastal positioning behaviors."
  (:require [empire.adapters.state.runtime :as runtime-state]
            [empire.application.ports :as ports]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.army.coastal.invasion :as invasion]
            [empire.computer.core :as core]
            [empire.computer.lake-naval :as lake-naval]
            [empire.computer.army.movement :as movement]
            [empire.debug :as debug]))

(defonce ^:private state-ctx (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  (let [store (runtime-state/runtime-state-store)]
    (ports/read-runtime-state store k)))

(defn- count-unexplored-neighbors
  "Counts unexplored cells adjacent to position on computer-map."
  [pos]
  (count (filter (fn [neighbor]
                   (nil? (get-in (read-runtime-state :computer-map) neighbor)))
                 (core/get-neighbors pos))))

(defn- update-backtrack
  "Adds pos to visited vector, keeping at most 10 entries."
  [visited pos]
  (let [v (conj (or visited []) pos)]
    (if (> (count v) 10)
      (subvec v (- (count v) 10))
      v)))

(defn- terminate-coast-walk
  "Switches army from coast-walk to sentry (or awake if in a city)."
  [pos]
  (let [mode (if (= :city (:type (get-in (current-world) pos))) :awake :sentry)]
    (update-game-map! update-in (conj pos :contents)
                      #(-> % (assoc :mode mode)
                           (dissoc :coast-direction :coast-start :coast-visited)))))

(defn- coast-walk-candidates
  "Returns empty land/city neighbors that are adjacent to sea."
  [pos country-id]
  (filter movement/adjacent-to-sea?
          (movement/get-empty-passable-neighbors pos country-id)))

(defn process-coast-walk
  "Handles coast-walk movement. Returns new position or nil."
  [pos country-id]
  (let [unit (get-in (current-world) (conj pos :contents))
        coast-start (:coast-start unit)
        visited (set (:coast-visited unit))
        candidates (coast-walk-candidates pos country-id)]
    (if (empty? candidates)
      (do (terminate-coast-walk pos) nil)
      (let [not-visited (remove visited candidates)
            pool (if (seq not-visited) not-visited candidates)
            scored (map (fn [c] [c (count-unexplored-neighbors c)]) pool)
            best-score (apply max (map second scored))
            best (map first (filter #(= best-score (second %)) scored))
            target (if (= 1 (count best)) (first best) (rand-nth (vec best)))]
        (when (movement/try-move pos target)
          (update-game-map! update-in (conj target :contents)
                            #(assoc % :coast-visited (update-backtrack (:coast-visited %) target)))
          (if (= target coast-start)
            (do (terminate-coast-walk target) target)
            target))))))

(defn- adjacent-to-computer-city?
  "Returns true if position has an adjacent computer city."
  [pos]
  (some (fn [neighbor]
          (let [cell (get-in (current-world) neighbor)]
            (and (= :city (:type cell))
                 (= :computer (:city-status cell)))))
        (core/get-neighbors pos)))

(defn- known-lake-cells
  []
  (lake-naval/lake-cells (read-runtime-state :computer-map)
                         (read-runtime-state :lake-max-cells)))

(defn- adjacent-to-ocean?
  [pos]
  (let [world (current-world)
        lakes (known-lake-cells)]
    (some (fn [neighbor]
            (let [cell (get-in world neighbor)]
              (and (= :sea (:type cell))
                   (not (contains? lakes neighbor)))))
          (core/get-neighbors pos))))

(defn- find-nearest-unoccupied-coastal-cell
  "Finds nearest coastal cell from registry with matching country-id, no unit.
   Excludes cells adjacent to computer cities to avoid blocking production."
  [pos country-id]
  (when country-id
    (movement/ensure-coastal-registry country-id)
    (let [coastal (get (read-runtime-state :coastal-cells-by-country) country-id)
          game-map (current-world)
          candidates (filter (fn [p]
                               (let [cell (get-in game-map p)]
                                 (and (= :land (:type cell))
                                      (adjacent-to-ocean? p)
                                      (or (nil? (:country-id cell))
                                          (= country-id (:country-id cell)))
                                      (nil? (:contents cell)))))
                             coastal)
          away-from-city (remove adjacent-to-computer-city? candidates)]
      (first (sort-by #(core/distance pos %)
                      (if (seq away-from-city) away-from-city candidates))))))

(defn- empty-land-for-country? [cell country-id]
  (and (= :land (:type cell))
       (or (nil? (:country-id cell))
           (= country-id (:country-id cell)))
       (nil? (:contents cell))))

(defn- coast-distance [coastal c]
  (cond
    (contains? coastal c) 0
    (some (partial contains? coastal) (core/get-neighbors c)) 1
    :else -1))

(defn- find-nearest-cell-close-to-coast
  "Finds nearest empty land cell within 1 step of a registered coastal cell.
   Used for transport queue - army lines up near coast."
  [pos country-id]
  (when country-id
    (movement/ensure-coastal-registry country-id)
    (let [coastal (set (filter adjacent-to-ocean?
                               (get (read-runtime-state :coastal-cells-by-country) country-id)))]
      (when (seq coastal)
        (let [expanded (into (set coastal) (mapcat core/get-neighbors coastal))
              game-map (current-world)
              candidates (filter #(empty-land-for-country? (get-in game-map %) country-id)
                                 expanded)
              with-coast-dist (keep (fn [c]
                                      (let [d (coast-distance coastal c)]
                                        (when (>= d 0) [c d])))
                                    candidates)]
          (when (seq with-coast-dist)
            (let [best-coast-dist (apply min (map second with-coast-dist))
                  near-coast (map first (filter #(= best-coast-dist (second %))
                                                with-coast-dist))]
              (first (sort-by #(core/distance pos %) near-coast)))))))))

(defn should-sentry-on-coast? [pos country-id]
  (and country-id
       (adjacent-to-ocean? pos)
       (not= :city (:type (get-in (current-world) pos)))
       (not (adjacent-to-computer-city? pos))))

(defn can-settle-here? [pos country-id]
  (and country-id
       (adjacent-to-ocean? pos)
       (not= :city (:type (get-in (current-world) pos)))))

(declare find-coast-target-once)
(declare empty-coastal-cell?)

(defn- try-move-to-coastal-cell [pos country-id]
  (when-let [target (find-nearest-unoccupied-coastal-cell pos country-id)]
    (movement/move-toward-objective pos target country-id)))

(defn- try-settle-on-coast [pos country-id]
  (when (can-settle-here? pos country-id)
    (debug/log-computer-event! :army-sentry pos {:reason :no-coastal-cell-available})
    (update-game-map! assoc-in (conj pos :contents :mode) :sentry)
    pos))

(defn- try-queue-near-coast [pos country-id]
  (when-let [target (find-nearest-cell-close-to-coast pos country-id)]
    (or (movement/move-toward-objective pos target country-id)
        (do (debug/log-computer-event! :army-sentry pos {:reason :transport-queue})
            (update-game-map! assoc-in (conj pos :contents :mode) :sentry)
            pos))))

(defn- try-wake-nearby [pos]
  (when (pos? (core/wake-nearby-sentries pos 3))
    (debug/log-computer-event! :army-wake-sentries pos {:reason :stuck})
    nil))

(defn fill-coastal-cell
  "If army is on a coastal cell away from cities, go sentry.
   Otherwise move toward nearest unoccupied coastal cell.
   If no coastal cell available, queue near coast and go sentry.
   If truly stuck, wake nearby sentries."
  [pos country-id]
  (if (should-sentry-on-coast? pos country-id)
    (do (debug/log-computer-event! :army-sentry pos {:reason :coastal-fill :country-id country-id})
        (update-game-map! assoc-in (conj pos :contents :mode) :sentry)
        pos)
    (or (try-move-to-coastal-cell pos country-id)
        (try-settle-on-coast pos country-id)
        (try-queue-near-coast pos country-id)
        (try-wake-nearby pos))))

(defn- invasion-ctx
  []
  {:current-world current-world
   :update-game-map! update-game-map!
   :read-runtime-state read-runtime-state
   :adjacent-to-ocean? adjacent-to-ocean?
   :should-sentry-on-coast? should-sentry-on-coast?
   :find-coast-target-once find-coast-target-once})

(defn find-coast-target-once
  "One-time land-only BFS target selection for invasion embarkation."
  [start country-id]
  (invasion/find-coast-target-once (invasion-ctx) start country-id))

(defn- local-empty-coast-target
  [pos country-id]
  (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [pos 0])
         visited #{pos}]
    (if (empty? queue)
      nil
      (let [[current depth] (peek queue)]
        (cond
          (and (not= current pos) (empty-coastal-cell? current country-id)) current
          (>= depth 2) (recur (pop queue) visited)
          :else
          (let [nexts (->> (movement/get-passable-neighbors current country-id)
                           (remove visited))]
            (recur (reduce #(conj %1 [%2 (inc depth)]) (pop queue) nexts)
                   (into visited nexts))))))))

(defn- empty-coastal-cell?
  [pos country-id]
  (invasion/empty-coastal-cell? (invasion-ctx) pos country-id))

(def ^:private local-coast-repath-interval-rounds 3)

(defn- settle-at-coast-target!
  [pos]
  (update-game-map! update-in (conj pos :contents)
                    #(-> %
                         (assoc :mode :sentry)
                         (dissoc :coast-target :coast-repath-after-round :lake-retask?))))

(defn- step-toward-target-cheap
  [pos target country-id]
  (let [current-dist (core/distance pos target)
        candidates (->> (movement/get-empty-passable-neighbors pos country-id)
                        (filter #(> current-dist (core/distance % target)))
                        (sort-by #(core/distance % target)))]
    (when-let [best (first candidates)]
      (movement/try-move pos best))))

(defn process-move-to-coast-for-invasion
  "Move an army toward its cached coast target for pickup."
  [pos country-id]
  (invasion/process-move-to-coast-for-invasion (invasion-ctx) pos country-id))
