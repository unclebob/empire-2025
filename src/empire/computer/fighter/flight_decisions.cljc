(ns empire.computer.fighter.flight-decisions
  (:require [empire.computer.fighter.exploration :as fe]
            [empire.computer.fighter.movement :as fm]
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

(defn choose-leg
  [world sites leg-records current-site]
  (let [reachable (filter #(and (not= % current-site)
                                (<= (fm/distance-to current-site %) config/fighter-fuel))
                          sites)
        active-targets (set (for [c (range (count world))
                                  r (range (count (first world)))
                                  :let [u (get-in world [c r :contents])
                                        target (:flight-target-site u)]
                                  :when (and (= :fighter (:type u))
                                             (= :computer (:owner u))
                                             target)]
                              target))
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
        (reduce (fn [acc city]
                  (if (contains? acc city)
                    acc
                    (if (<= (fm/distance-to start city) config/fighter-fuel)
                      (assoc acc city [start city])
                      acc)))
                paths
                city-sites)
        (let [node (first queue)
              neighbors (for [city city-sites
                              :when (and (not (contains? paths city))
                                         (not= city node)
                                         (<= (fm/distance-to node city) config/fighter-fuel))]
                          city)
              next-paths (reduce (fn [acc city]
                                   (assoc acc city (conj (get acc node [node]) city)))
                                 paths
                                 neighbors)]
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

(defn- in-bounds?
  [world [r c]]
  (and (<= 0 r) (< r (count world))
       (<= 0 c) (< c (count (first world)))))

(defn- projected-endpoint
  [world start direction steps]
  (reduce (fn [last-pos step]
            (let [[dr dc] direction
                  next-pos [(+ (first start) (* step dr))
                            (+ (second start) (* step dc))]]
              (if (in-bounds? world next-pos)
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
;; {:version 1, :tested-at "2026-03-15T16:22:18.317983-05:00", :module-hash "-2102160933", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "197921039"} {:id "def/sortie-half-steps", :kind "def", :line 6, :end-line 6, :hash "1596510764"} {:id "def/neighbor-offsets", :kind "def", :line 8, :end-line 11, :hash "-1254756339"} {:id "defn/neighbors", :kind "defn", :line 13, :end-line 20, :hash "741800172"} {:id "defn/current-refueling-site", :kind "defn", :line 22, :end-line 33, :hash "-409622893"} {:id "defn/choose-leg", :kind "defn", :line 35, :end-line 54, :hash "667890426"} {:id "defn/clamp-to-map-bounds", :kind "defn", :line 56, :end-line 63, :hash "1865164368"} {:id "defn/regular-leg-action", :kind "defn", :line 65, :end-line 71, :hash "1175566717"} {:id "defn/exploration-flight-action", :kind "defn", :line 73, :end-line 89, :hash "-1095327543"} {:id "defn/ensure-flight-target-action", :kind "defn", :line 91, :end-line 97, :hash "-964698914"} {:id "defn/at-flight-target?", :kind "defn", :line 99, :end-line 104, :hash "-1825324496"} {:id "defn/arrival-action", :kind "defn", :line 106, :end-line 119, :hash "249786359"}]}
;; clj-mutate-manifest-end
