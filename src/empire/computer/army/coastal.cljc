(ns empire.computer.army.coastal
  "Coastal movement, coast-walk, and coastal positioning behaviors."
  (:require [empire.state.api :as sa]
            [empire.computer.army.coastal-invasion :as invasion]
            [empire.computer.core :as core]
            [empire.computer.lake-naval :as lake-naval]
            [empire.computer.army.movement :as movement]
            [empire.game-mechanics.debug.integrity :as integrity]
            [empire.game-mechanics.debug.logging :as debug]))

(defn- log-missing-army-contents!
  [reason context]
  (integrity/write-stacktrace-error-log!
   "army-error"
   (merge {:reason reason} context)
   (ex-info "Army sentry update attempted without unit contents"
            (merge {:reason reason} context))))

(defn- set-sentry-mode-if-unit!
  [pos context]
  (if (get-in (sa/current-world) (conj pos :contents))
    (sa/update-world! update-in (conj pos :contents) assoc :mode :sentry)
    (log-missing-army-contents! :missing-contents-for-sentry
                                (assoc context :pos pos :cell (get-in (sa/current-world) pos)))))

(defn- count-unexplored-neighbors
  "Counts unexplored cells adjacent to position on computer-map."
  [pos]
  (count (filter (fn [neighbor]
                   (nil? (get-in (sa/read-state :computer-map) neighbor)))
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
  (let [mode (if (= :city (:type (get-in (sa/current-world) pos))) :awake :sentry)]
    (sa/update-world! update-in (conj pos :contents)
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
  (let [unit (get-in (sa/current-world) (conj pos :contents))
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
          (if (= target coast-start)
            (do (terminate-coast-walk target) target)
            target))))))

(defn- adjacent-to-computer-city?
  "Returns true if position has an adjacent computer city."
  [pos]
  (some (fn [neighbor]
          (let [cell (get-in (sa/current-world) neighbor)]
            (and (= :city (:type cell))
                 (= :computer (:city-status cell)))))
        (core/get-neighbors pos)))

(defn- known-lake-cells
  []
  (lake-naval/lake-cells (sa/read-state :computer-map)
                         (sa/read-state :lake-max-cells)))

(defn- adjacent-to-ocean?
  [pos]
  (let [world (sa/read-state :computer-map)
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
    (let [coastal (get (sa/read-state :coastal-cells-by-country) country-id)
          game-map (sa/current-world)
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
                               (get (sa/read-state :coastal-cells-by-country) country-id)))]
      (when (seq coastal)
        (let [expanded (into (set coastal) (mapcat core/get-neighbors coastal))
              game-map (sa/current-world)
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
       (not= :city (:type (get-in (sa/current-world) pos)))
       (not (adjacent-to-computer-city? pos))))

(defn can-settle-here? [pos country-id]
  (and country-id
       (adjacent-to-ocean? pos)
       (not= :city (:type (get-in (sa/current-world) pos)))))

(declare find-coast-target-once)
(declare empty-coastal-cell?)

(defn- try-move-to-coastal-cell [pos country-id]
  (when-let [target (find-nearest-unoccupied-coastal-cell pos country-id)]
    (movement/move-toward-objective pos target country-id)))

(defn- try-settle-on-coast [pos country-id]
  (when (can-settle-here? pos country-id)
    (debug/log-computer-event! :army-sentry pos {:reason :no-coastal-cell-available})
    (set-sentry-mode-if-unit! pos
                              {:operation :try-settle-on-coast
                               :country-id country-id})
    pos))

(defn- try-queue-near-coast [pos country-id]
  (when-let [target (find-nearest-cell-close-to-coast pos country-id)]
    (or (movement/move-toward-objective pos target country-id)
        (do (debug/log-computer-event! :army-sentry pos {:reason :transport-queue})
            (set-sentry-mode-if-unit! pos
                                      {:operation :try-queue-near-coast
                                       :country-id country-id
                                       :target target})
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
        (set-sentry-mode-if-unit! pos
                                  {:operation :fill-coastal-cell
                                   :country-id country-id})
        pos)
    (or (try-move-to-coastal-cell pos country-id)
        (try-settle-on-coast pos country-id)
        (try-queue-near-coast pos country-id)
        (try-wake-nearby pos))))

(defn- invasion-ctx
  []
  {:current-world sa/current-world
   :update-game-map! sa/update-world!
   :read-runtime-state sa/read-state
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
  (sa/update-world! update-in (conj pos :contents)
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:57:11.847836-05:00", :module-hash "-1290895994", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "887839761"} {:id "defn-/count-unexplored-neighbors", :kind "defn-", :line 10, :end-line 15, :hash "-502005581"} {:id "defn-/update-backtrack", :kind "defn-", :line 17, :end-line 23, :hash "1267740938"} {:id "defn-/terminate-coast-walk", :kind "defn-", :line 25, :end-line 31, :hash "593971767"} {:id "defn-/coast-walk-candidates", :kind "defn-", :line 33, :end-line 37, :hash "-2144569116"} {:id "defn/process-coast-walk", :kind "defn", :line 39, :end-line 59, :hash "1760263969"} {:id "defn-/adjacent-to-computer-city?", :kind "defn-", :line 61, :end-line 68, :hash "1885790618"} {:id "defn-/known-lake-cells", :kind "defn-", :line 70, :end-line 73, :hash "-816529896"} {:id "defn-/adjacent-to-ocean?", :kind "defn-", :line 75, :end-line 83, :hash "-162695580"} {:id "defn-/find-nearest-unoccupied-coastal-cell", :kind "defn-", :line 85, :end-line 103, :hash "-1856407704"} {:id "defn-/empty-land-for-country?", :kind "defn-", :line 105, :end-line 109, :hash "-2093845812"} {:id "defn-/coast-distance", :kind "defn-", :line 111, :end-line 115, :hash "457986144"} {:id "defn-/find-nearest-cell-close-to-coast", :kind "defn-", :line 117, :end-line 138, :hash "1212588464"} {:id "defn/should-sentry-on-coast?", :kind "defn", :line 140, :end-line 144, :hash "1181106337"} {:id "defn/can-settle-here?", :kind "defn", :line 146, :end-line 149, :hash "666562383"} {:id "form/15/declare", :kind "declare", :line 151, :end-line 151, :hash "1944609974"} {:id "form/16/declare", :kind "declare", :line 152, :end-line 152, :hash "-936231528"} {:id "defn-/try-move-to-coastal-cell", :kind "defn-", :line 154, :end-line 156, :hash "1419876926"} {:id "defn-/try-settle-on-coast", :kind "defn-", :line 158, :end-line 162, :hash "-1804019222"} {:id "defn-/try-queue-near-coast", :kind "defn-", :line 164, :end-line 169, :hash "-1855119570"} {:id "defn-/try-wake-nearby", :kind "defn-", :line 171, :end-line 174, :hash "-706969155"} {:id "defn/fill-coastal-cell", :kind "defn", :line 176, :end-line 189, :hash "567939937"} {:id "defn-/invasion-ctx", :kind "defn-", :line 191, :end-line 198, :hash "-489282100"} {:id "defn/find-coast-target-once", :kind "defn", :line 200, :end-line 203, :hash "126024350"} {:id "defn-/local-empty-coast-target", :kind "defn-", :line 205, :end-line 219, :hash "1791390187"} {:id "defn-/empty-coastal-cell?", :kind "defn-", :line 221, :end-line 223, :hash "-82665084"} {:id "def/local-coast-repath-interval-rounds", :kind "def", :line 225, :end-line 225, :hash "1825849599"} {:id "defn-/settle-at-coast-target!", :kind "defn-", :line 227, :end-line 232, :hash "1741113764"} {:id "defn-/step-toward-target-cheap", :kind "defn-", :line 234, :end-line 241, :hash "-1508804504"} {:id "defn/process-move-to-coast-for-invasion", :kind "defn", :line 243, :end-line 246, :hash "-551573708"}]}
;; clj-mutate-manifest-end
