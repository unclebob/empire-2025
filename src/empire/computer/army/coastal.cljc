(ns empire.computer.army.coastal
  "Coastal movement, coast-walk, and coastal positioning behaviors."
  (:require [empire.state.api :as sa]
            [empire.computer.army.coastal-invasion :as invasion]
            [empire.computer.army.coastal-positioning :as coastal-positioning]
            [empire.computer.army.movement :as movement]
            [empire.computer.army.sentry :as sentry]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.movement :as computer-movement]
            [empire.computer.shared.world-query :as world-query]
            [empire.game-mechanics.visibility :as visibility]
            [empire.game-mechanics.debug.logging :as debug]))

(defn- count-unexplored-neighbors
  "Counts unexplored cells adjacent to position on computer-map."
  [pos]
  (count (filter (fn [neighbor]
                   (nil? (get-in (sa/read-state :computer-map) neighbor)))
                 (world-query/get-neighbors pos))))

(defn- update-backtrack
  "Adds pos to visited vector, keeping at most 10 entries."
  [visited pos]
  (grid/bounded-conj visited pos 10))

(defn- terminate-coast-walk
  "Switches army from coast-walk to sentry (or awake if in a city)."
  [pos]
  (let [mode (if (= :city (:type (get-in (sa/read-state :computer-map) pos))) :awake :sentry)]
    (sa/update-world! update-in (conj pos :contents)
                      #(-> % (assoc :mode mode)
                           (dissoc :coast-direction :coast-start :coast-visited)))
    (visibility/sync-ai-unit-to-computer-map! pos)))

(defn- coast-walk-candidates
  "Returns empty land/city neighbors that are adjacent to sea."
  [pos country-id]
  (filter movement/adjacent-to-sea?
          (movement/get-empty-passable-neighbors pos country-id)))

(defn process-coast-walk
  "Handles coast-walk movement. Returns new position or nil."
  [pos country-id]
  (let [unit (get-in (sa/read-state :computer-map) (conj pos :contents))
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
          (sa/update-world! update-in (conj target :contents)
                            #(assoc % :coast-visited (update-backtrack (:coast-visited %) target)))
          (visibility/sync-ai-unit-to-computer-map! target)
          (if (= target coast-start)
            (do (terminate-coast-walk target) target)
            target))))))

(defn- empty-land-for-country? [cell country-id]
  (and (= :land (:type cell))
       (or (nil? (:country-id cell))
           (= country-id (:country-id cell)))
       (nil? (:contents cell))))

(defn find-nearest-unoccupied-coastal-cell
  [pos country-id]
  (coastal-positioning/find-nearest-unoccupied-coastal-cell pos country-id))

(defn- coast-distance [coastal c]
  (cond
    (contains? coastal c) 0
    (some (partial contains? coastal) (world-query/get-neighbors c)) 1
    :else -1))

(defn- find-nearest-cell-close-to-coast
  "Finds nearest empty land cell within 1 step of a registered coastal cell.
   Used for transport queue - army lines up near coast."
  [pos country-id]
  (when country-id
    (movement/ensure-coastal-registry country-id)
    (let [coastal (set (filter coastal-positioning/adjacent-to-ocean?
                               (get (sa/read-state :coastal-cells-by-country) country-id)))]
      (when (seq coastal)
        (let [expanded (into (set coastal) (mapcat world-query/get-neighbors coastal))
              game-map (sa/read-state :computer-map)
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
              (first (sort-by #(grid/distance pos %) near-coast)))))))))

(defn should-sentry-on-coast? [pos country-id]
  (coastal-positioning/should-sentry-on-coast? pos country-id))

(defn can-settle-here? [pos country-id]
  (coastal-positioning/can-settle-here? pos country-id))

(declare find-coast-target-once)
(declare empty-coastal-cell?)

(defn- try-move-to-coastal-cell [pos country-id]
  (when-let [target (find-nearest-unoccupied-coastal-cell pos country-id)]
    (movement/move-toward-objective pos target country-id)))

(defn- try-settle-on-coast [pos country-id]
  (when (can-settle-here? pos country-id)
    (debug/log-computer-event! :army-sentry pos {:reason :no-coastal-cell-available})
    (sentry/set-sentry-mode-if-unit! pos
                                     {:operation :try-settle-on-coast
                                      :country-id country-id})
    pos))

(defn- try-queue-near-coast [pos country-id]
  (when-let [target (find-nearest-cell-close-to-coast pos country-id)]
    (or (movement/move-toward-objective pos target country-id)
        (do (debug/log-computer-event! :army-sentry pos {:reason :transport-queue})
            (sentry/set-sentry-mode-if-unit! pos
                                             {:operation :try-queue-near-coast
                                              :country-id country-id
                                              :target target})
            pos))))

(defn- try-wake-nearby [pos]
  (when (pos? (action-resolution/wake-nearby-sentries pos 3))
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
        (sentry/set-sentry-mode-if-unit! pos
                                         {:operation :fill-coastal-cell
                                          :country-id country-id})
        pos)
    (or (try-move-to-coastal-cell pos country-id)
        (try-settle-on-coast pos country-id)
        (try-queue-near-coast pos country-id)
        (try-wake-nearby pos))))

(defn- invasion-ctx
  []
  {:current-world #(sa/read-state :computer-map)
   :update-game-map! sa/update-world!
   :read-runtime-state sa/read-state
   :sync-ai-unit! visibility/sync-ai-unit-to-computer-map!
   :adjacent-to-ocean? coastal-positioning/adjacent-to-ocean?
   :should-sentry-on-coast? should-sentry-on-coast?
   :find-coast-target-once find-coast-target-once})

(defn find-coast-target-once
  "One-time land-only BFS target selection for invasion embarkation."
  [start country-id]
  (coastal-positioning/find-coast-target-once start country-id))

(defn- found-local-empty-coast?
  [pos country-id current]
  (and (not= current pos) (empty-coastal-cell? current country-id)))

(defn- local-empty-coast-target
  [pos country-id]
  (loop [queue (conj clojure.lang.PersistentQueue/EMPTY [pos 0])
         visited #{pos}]
    (if (empty? queue)
      nil
      (let [[current depth] (peek queue)]
        (cond
          (found-local-empty-coast? pos country-id current) current
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
  (computer-movement/update-unit-and-sync!
   pos
   #(-> %
        (assoc :mode :sentry)
        (dissoc :coast-target :coast-repath-after-round :lake-retask?))))

(defn process-move-to-coast-for-invasion
  "Move an army toward its cached coast target for pickup."
  [pos country-id]
  (invasion/process-move-to-coast-for-invasion (invasion-ctx) pos country-id))

(defn- clear-transport-staging-target
  [pos]
  (sa/update-world! update-in (conj pos :contents)
                    #(-> %
                         (assoc :mode :awake)
                         (dissoc :transport-staging-target)))
  (visibility/sync-ai-unit-to-computer-map! pos)
  nil)

(defn- step-to-staging-target
  [pos target country-id]
  (or (movement/step-toward-target-cheap pos target country-id)
      (movement/local-step-toward-objective pos target country-id)
      (movement/move-toward-objective pos target country-id)))

(defn process-move-to-coast-for-transport
  [pos country-id]
  (let [unit (get-in (sa/read-state :computer-map) (conj pos :contents))
        target (:transport-staging-target unit)]
    (cond
      (nil? target) (clear-transport-staging-target pos)
      (= pos target) pos
      :else (step-to-staging-target pos target country-id))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:36:05.415959-05:00", :module-hash "1591378981", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "1102320044"} {:id "defn-/count-unexplored-neighbors", :kind "defn-", :line 15, :end-line nil, :hash "1547624350"} {:id "defn-/update-backtrack", :kind "defn-", :line 22, :end-line nil, :hash "1924347008"} {:id "defn-/terminate-coast-walk", :kind "defn-", :line 27, :end-line nil, :hash "568620892"} {:id "defn-/coast-walk-candidates", :kind "defn-", :line 36, :end-line nil, :hash "-2144569116"} {:id "defn/process-coast-walk", :kind "defn", :line 42, :end-line nil, :hash "-672161034"} {:id "defn-/empty-land-for-country?", :kind "defn-", :line 65, :end-line nil, :hash "-2093845812"} {:id "defn/find-nearest-unoccupied-coastal-cell", :kind "defn", :line 71, :end-line nil, :hash "-460023669"} {:id "defn-/coast-distance", :kind "defn-", :line 75, :end-line nil, :hash "-1999482778"} {:id "defn-/find-nearest-cell-close-to-coast", :kind "defn-", :line 81, :end-line nil, :hash "-1181874540"} {:id "defn/should-sentry-on-coast?", :kind "defn", :line 104, :end-line nil, :hash "1298390079"} {:id "defn/can-settle-here?", :kind "defn", :line 107, :end-line nil, :hash "1993215136"} {:id "form/12/declare", :kind "declare", :line 110, :end-line nil, :hash "1944609974"} {:id "form/13/declare", :kind "declare", :line 111, :end-line nil, :hash "-936231528"} {:id "defn-/try-move-to-coastal-cell", :kind "defn-", :line 113, :end-line nil, :hash "1419876926"} {:id "defn-/try-settle-on-coast", :kind "defn-", :line 117, :end-line nil, :hash "-810565410"} {:id "defn-/try-queue-near-coast", :kind "defn-", :line 125, :end-line nil, :hash "-1557527272"} {:id "defn-/try-wake-nearby", :kind "defn-", :line 135, :end-line nil, :hash "1078962120"} {:id "defn/fill-coastal-cell", :kind "defn", :line 140, :end-line nil, :hash "1399386512"} {:id "defn-/invasion-ctx", :kind "defn-", :line 157, :end-line nil, :hash "-808880577"} {:id "defn/find-coast-target-once", :kind "defn", :line 167, :end-line nil, :hash "2064648194"} {:id "defn-/found-local-empty-coast?", :kind "defn-", :line 172, :end-line nil, :hash "1742656094"} {:id "defn-/local-empty-coast-target", :kind "defn-", :line 176, :end-line nil, :hash "1332262553"} {:id "defn-/empty-coastal-cell?", :kind "defn-", :line 192, :end-line nil, :hash "-82665084"} {:id "def/local-coast-repath-interval-rounds", :kind "def", :line 196, :end-line nil, :hash "1825849599"} {:id "defn-/settle-at-coast-target!", :kind "defn-", :line 198, :end-line nil, :hash "1130954819"} {:id "defn/process-move-to-coast-for-invasion", :kind "defn", :line 206, :end-line nil, :hash "-551573708"} {:id "defn-/clear-transport-staging-target", :kind "defn-", :line 211, :end-line nil, :hash "652407877"} {:id "defn-/step-to-staging-target", :kind "defn-", :line 220, :end-line nil, :hash "-1150583007"} {:id "defn/process-move-to-coast-for-transport", :kind "defn", :line 226, :end-line nil, :hash "-1018055431"}]}
;; clj-mutate-manifest-end
