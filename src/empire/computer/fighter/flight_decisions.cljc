(ns empire.computer.fighter.flight-decisions
  (:require [empire.computer.fighter.exploration :as fe]
            [empire.computer.fighter.movement :as fm]
            [empire.computer.shared.grid :as grid]
            [empire.config.core :as config]))

(def neighbor-offsets
  [[-1 -1] [-1 0] [-1 1]
   [0 -1]          [0 1]
   [1 -1]  [1 0]  [1 1]])

(defn neighbors
  [world pos]
  (for [[dr dc] neighbor-offsets
        :let [n [(+ (first pos) dr) (+ (second pos) dc)]]
        :when (and (<= 0 (first n)) (< (first n) (count world))
                   (<= 0 (second n)) (< (second n) (count (first world)))
                   (some? (get-in world n)))]
    n))

(defn current-refueling-site
  [world pos]
  (let [cell (get-in world pos)]
    (cond
      (and (= :city (:type cell)) (= :computer (:city-status cell))) pos
      :else
      (first (filter (fn [n]
                       (let [ncell (get-in world n)]
                         (and (= :carrier (get-in ncell [:contents :type]))
                              (= :computer (get-in ncell [:contents :owner]))
                              (= :holding (get-in ncell [:contents :carrier-mode])))))
                     (neighbors world pos))))))

(def ^:private active-targets-cache (atom nil))

(defn clear-active-targets-cache! [] (reset! active-targets-cache nil))

(defn scan-active-targets [world]
  (set (for [c (range (count world))
             r (range (count (first world)))
             :let [u (get-in world [c r :contents])
                   target (:flight-target-site u)]
             :when (and (= :fighter (:type u))
                        (= :computer (:owner u))
                        target)]
         target)))

(defn- cached-active-targets [world]
  (or @active-targets-cache
      (let [result (scan-active-targets world)]
        (reset! active-targets-cache result)
        result)))

(defn choose-leg
  [world sites leg-records current-site]
  (let [reachable (filter #(and (not= % current-site)
                                (<= (fm/distance-to current-site %) config/fighter-fuel))
                          sites)
        active-targets (cached-active-targets world)
        candidate-targets (let [unclaimed (remove active-targets reachable)]
                            (if (seq unclaimed) unclaimed reachable))
        scored (map (fn [target]
                      [target (:last-flown (get leg-records #{current-site target}) -1)])
                    candidate-targets)]
    (when (seq scored)
      (ffirst (sort-by second scored)))))

(defn- computer-city-site?
  [world site]
  (let [cell (get-in world site)]
    (and (= :city (:type cell))
         (= :computer (:city-status cell)))))

(defn- computer-city-sites
  [world sites]
  (filter #(computer-city-site? world %) sites))

(defn- cached-site-distance-fn
  []
  (let [cache (atom {})]
    (fn [site]
      (if (contains? @cache site)
        (get @cache site)
        (let [distance (fe/nearest-unexplored-distance site)]
          (swap! cache assoc site distance)
          distance)))))

(defn- path-distance
  [path]
  (reduce + 0 (map (fn [[from to]] (fm/distance-to from to))
                   (partition 2 1 path))))

(defn- direct-city-hop-paths
  [paths start city-sites]
  (reduce (fn [acc city]
            (if (or (contains? acc city)
                    (> (fm/distance-to start city) config/fighter-fuel))
              acc
              (assoc acc city [start city])))
          paths
          city-sites))

(defn- city-hop-neighbors
  [paths city-sites node]
  (for [city city-sites
        :when (and (not (contains? paths city))
                   (not= city node)
                   (<= (fm/distance-to node city) config/fighter-fuel))]
    city))

(defn- add-city-hop-paths
  [paths node neighbors]
  (reduce (fn [acc city]
            (assoc acc city (conj (get acc node [node]) city)))
          paths
          neighbors))

(defn- reachable-city-hop-paths
  [world start sites]
  (let [city-sites (vec (computer-city-sites world sites))
        start-paths (cond-> {}
                      (computer-city-site? world start) (assoc start [start]))]
    (loop [queue (if (computer-city-site? world start) [start] [])
           seen (cond-> #{start}
                  (computer-city-site? world start) (into city-sites))
           paths start-paths]
      (if (empty? queue)
        (direct-city-hop-paths paths start city-sites)
        (let [node (first queue)
              neighbors (city-hop-neighbors paths city-sites node)
              next-paths (add-city-hop-paths paths node neighbors)]
          (recur (into (vec (rest queue)) neighbors)
                 (into seen neighbors)
                 next-paths))))))

(defn- ranked-city-sites
  [world sites site-distance]
  (->> (computer-city-sites world sites)
       (keep (fn [site]
               (when-let [unexplored-distance (site-distance site)]
                 {:site site
                  :unexplored-distance unexplored-distance})))
       (sort-by (fn [{:keys [site unexplored-distance]}]
                  [unexplored-distance site]))))

(defn best-sortie-staging-plan
  [world sites current-site]
  (let [site-distance (cached-site-distance-fn)
        paths (reachable-city-hop-paths world current-site sites)
        scored (keep (fn [[city path]]
                       (when-let [unexplored-distance (site-distance city)]
                         {:city city
                          :path path
                          :unexplored-distance unexplored-distance
                          :hop-count (dec (count path))
                          :path-distance (path-distance path)}))
                     paths)]
    (when (seq scored)
      (first
       (sort-by (fn [{:keys [unexplored-distance hop-count path-distance city]}]
                  [unexplored-distance hop-count path-distance city])
                scored)))))

(defn clamp-to-map-bounds
  [world [r c]]
  (let [height (count world)
        width (count (first world))
        max-r (dec height)
        max-c (dec width)]
    [(-> r (max 0) (min max-r))
     (-> c (max 0) (min max-c))]))

(defn regular-leg-action
  [world sites leg-records pos site-pos]
  (when-let [target (choose-leg world sites leg-records site-pos)]
    {:action :assign-regular-leg
     :pos pos
     :target target
     :origin site-pos}))

(declare exploration-flight-action)

(defn- staging-action
  [world sites leg-records pos site-pos]
  (when-let [{:keys [city path]} (best-sortie-staging-plan world sites site-pos)]
    (if (= city site-pos)
      (exploration-flight-action world sites pos site-pos)
      {:action :assign-regular-leg
       :pos pos
       :target (second path)
       :origin site-pos})))

(defn- projected-endpoint
  [world start direction steps]
  (reduce (fn [last-pos step]
            (let [[dr dc] direction
                  next-pos [(+ (first start) (* step dr))
                            (+ (second start) (* step dc))]]
              (if (grid/in-bounds? world next-pos)
                next-pos
                (reduced last-pos))))
          start
          (range 1 (inc steps))))

(defn- nearest-reachable-refueling-site
  [sites pos remaining-fuel]
  (when-let [scored (seq (keep (fn [site]
                                 (let [distance (fm/distance-to pos site)]
                                   (when (<= distance remaining-fuel)
                                     [site distance])))
                               sites))]
    (first (first (sort-by second scored)))))

(defn- reachable-sites
  [sites pos remaining-fuel]
  (keep (fn [site]
          (let [distance (fm/distance-to pos site)]
            (when (<= distance remaining-fuel)
              [site distance])))
        sites))

(defn- best-reachable-landing-site
  [world sites pos remaining-fuel ranked-cities]
  (let [reachable (vec (reachable-sites sites pos remaining-fuel))
        reachable-city-distances (into {}
                                      (keep (fn [[site distance]]
                                              (when (computer-city-site? world site)
                                                [site distance])))
                                      reachable)
        city-scored (keep (fn [{:keys [site unexplored-distance]}]
                            (when-let [distance (get reachable-city-distances site)]
                              [site unexplored-distance distance]))
                          ranked-cities)]
    (cond
      (seq city-scored)
      (first (first (sort-by (fn [[_ unexplored-distance distance]]
                               [unexplored-distance distance])
                             city-scored)))

      (seq reachable-city-distances)
      (first (first (sort-by second reachable-city-distances)))

      :else
      (nearest-reachable-refueling-site sites pos remaining-fuel))))

(defn- max-sortie-plan
  [world sites pos direction ranked-cities]
  (let [max-fuel config/fighter-fuel]
    (loop [steps 1
           best-plan nil]
      (if (> steps max-fuel)
        best-plan
        (let [endpoint (projected-endpoint world pos direction steps)
              actual-steps (fm/distance-to pos endpoint)
              remaining-fuel (- max-fuel actual-steps)
              landing-site (best-reachable-landing-site world sites endpoint remaining-fuel ranked-cities)
              next-plan (when (and (pos? actual-steps) landing-site)
                          {:steps actual-steps
                           :endpoint endpoint
                           :landing-site landing-site})]
          (if (and (= endpoint pos) (nil? next-plan))
            best-plan
            (recur (inc steps) (or next-plan best-plan))))))))

(defn- choose-exploration-plan
  [world sites pos]
  (let [site-distance (cached-site-distance-fn)
        ranked-cities (ranked-city-sites world sites site-distance)]
    (when-let [plans (seq (keep (fn [dir]
                                  (when-let [plan (max-sortie-plan world sites pos dir ranked-cities)]
                                    (assoc plan
                                           :direction dir
                                           :score (fe/count-unexplored-along-direction
                                                   pos
                                                   dir
                                                   (:steps plan)))))
                                neighbor-offsets))]
      (let [best-score (apply max (map :score plans))
            best-plans (filter #(= best-score (:score %)) plans)]
        (rand-nth (vec best-plans))))))

(defn exploration-flight-action
  [world sites pos site-pos]
  (when-let [{:keys [direction endpoint steps landing-site]} (choose-exploration-plan world sites pos)]
    {:action :assign-exploration-flight
     :pos pos
     :mode :explore
     :origin site-pos
     :heading direction
     :steps-remaining steps
     :target endpoint
     :landing-site landing-site}))

(defn ensure-flight-target-action
  [world sites leg-records pos unit leg-roll drone-roll]
  (when (and unit (nil? (:flight-mode unit)) (nil? (:flight-target-site unit)))
    (when-let [site-pos (current-refueling-site world pos)]
      (or (staging-action world sites leg-records pos site-pos)
          (exploration-flight-action world sites pos site-pos)))))

(defn at-flight-target?
  [world pos target]
  (let [target-cell (get-in world target)]
    (or (= pos target)
        (and (= :carrier (get-in target-cell [:contents :type]))
             (<= (fm/distance-to pos target) 1)))))

(defn arrival-action
  [world sites leg-records round-number pos unit drone-roll]
  (let [target (:flight-target-site unit)
        origin (:flight-origin-site unit)
        next-action (or (staging-action world sites leg-records pos target)
                        (exploration-flight-action world sites pos target))
        leg-record (when (and origin (not= origin target))
                     {:leg-key #{origin target}
                      :last-flown round-number})
        exploration? (= :assign-exploration-flight (:action next-action))]
    {:action :handle-arrival
     :pos pos
     :target target
     :next-action next-action
     :launch-exploration? exploration?
     :leg-record leg-record
     :hops 1}))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-07T18:43:27.343777-05:00", :module-hash "1899628020", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "2009187554"} {:id "def/neighbor-offsets", :kind "def", :line 7, :end-line 10, :hash "-1254756339"} {:id "defn/neighbors", :kind "defn", :line 12, :end-line 19, :hash "741800172"} {:id "defn/current-refueling-site", :kind "defn", :line 21, :end-line 32, :hash "-409622893"} {:id "def/active-targets-cache", :kind "def", :line 34, :end-line 34, :hash "1272292329"} {:id "defn/clear-active-targets-cache!", :kind "defn", :line 36, :end-line 36, :hash "1181716916"} {:id "defn/scan-active-targets", :kind "defn", :line 38, :end-line 46, :hash "1962586445"} {:id "defn-/cached-active-targets", :kind "defn-", :line 48, :end-line 52, :hash "1610930394"} {:id "defn/choose-leg", :kind "defn", :line 54, :end-line 66, :hash "389278545"} {:id "defn-/computer-city-site?", :kind "defn-", :line 68, :end-line 72, :hash "-672364839"} {:id "defn-/computer-city-sites", :kind "defn-", :line 74, :end-line 76, :hash "293974693"} {:id "defn-/cached-site-distance-fn", :kind "defn-", :line 78, :end-line 86, :hash "-1667464056"} {:id "defn-/path-distance", :kind "defn-", :line 88, :end-line 91, :hash "784197927"} {:id "defn-/direct-city-hop-paths", :kind "defn-", :line 93, :end-line 101, :hash "1310858071"} {:id "defn-/city-hop-neighbors", :kind "defn-", :line 103, :end-line 109, :hash "-117462596"} {:id "defn-/add-city-hop-paths", :kind "defn-", :line 111, :end-line 116, :hash "-2026950695"} {:id "defn-/reachable-city-hop-paths", :kind "defn-", :line 118, :end-line 134, :hash "-2095496394"} {:id "defn-/ranked-city-sites", :kind "defn-", :line 136, :end-line 144, :hash "-941926028"} {:id "defn/best-sortie-staging-plan", :kind "defn", :line 146, :end-line 162, :hash "-2064897373"} {:id "defn/clamp-to-map-bounds", :kind "defn", :line 164, :end-line 171, :hash "1865164368"} {:id "defn/regular-leg-action", :kind "defn", :line 173, :end-line 179, :hash "1175566717"} {:id "form/21/declare", :kind "declare", :line 181, :end-line 181, :hash "-1238763831"} {:id "defn-/staging-action", :kind "defn-", :line 183, :end-line 191, :hash "-2007565385"} {:id "defn-/projected-endpoint", :kind "defn-", :line 193, :end-line 203, :hash "1201380276"} {:id "defn-/nearest-reachable-refueling-site", :kind "defn-", :line 205, :end-line 212, :hash "2046294938"} {:id "defn-/reachable-sites", :kind "defn-", :line 214, :end-line 220, :hash "-1622807208"} {:id "defn-/best-reachable-landing-site", :kind "defn-", :line 222, :end-line 244, :hash "1610178207"} {:id "defn-/max-sortie-plan", :kind "defn-", :line 246, :end-line 263, :hash "246754581"} {:id "defn-/choose-exploration-plan", :kind "defn-", :line 265, :end-line 280, :hash "-1030688859"} {:id "defn/exploration-flight-action", :kind "defn", :line 282, :end-line 292, :hash "745991422"} {:id "defn/ensure-flight-target-action", :kind "defn", :line 294, :end-line 299, :hash "1715647447"} {:id "defn/at-flight-target?", :kind "defn", :line 301, :end-line 306, :hash "-1825324496"} {:id "defn/arrival-action", :kind "defn", :line 308, :end-line 324, :hash "-1127031301"}]}
;; clj-mutate-manifest-end
