(ns empire.computer.threat-response.invasion-decision
  "Decision helpers for major invasion feasibility and attrition fallback.")

(def review-interval-rounds 10)
(def early-failure-reasons #{:no-sea-path :insufficient-resources})

(def ^:private active-invasion-transport-missions
  #{:invading :unloading :load-for-invasion :find-armies-for-invasion})

(defn- in-bounds?
  [computer-map [x y]]
  (and (<= 0 x)
       (<= 0 y)
       (< x (count computer-map))
       (< y (count (first computer-map)))))

(defn- neighbors
  [computer-map [x y]]
  (for [dx [-1 0 1]
        dy [-1 0 1]
        :when (not (and (zero? dx) (zero? dy)))
        :let [pos [(+ x dx) (+ y dy)]]
        :when (in-bounds? computer-map pos)]
    pos))

(defn- land-or-city?
  [cell]
  (and cell (#{:land :city} (:type cell))))

(defn- flood-fill-land
  [computer-map start]
  (when (land-or-city? (get-in computer-map start))
    (loop [frontier #{start}
           visited #{}]
      (if (empty? frontier)
        visited
        (let [pos (first frontier)
              rest-frontier (disj frontier pos)]
          (if (contains? visited pos)
            (recur rest-frontier visited)
            (let [land-neighbors (filter #(land-or-city? (get-in computer-map %))
                                         (neighbors computer-map pos))]
              (recur (into rest-frontier land-neighbors)
                     (conj visited pos)))))))))

(defn- computer-unit-entries
  [world]
  (for [x (range (count world))
        y (range (count (first world)))
        :let [unit (get-in world [x y :contents])]
        :when (and unit (= :computer (:owner unit)))]
    [[x y] unit]))

(defn total-computer-army-resources
  [world]
  (reduce (fn [total [_ unit]]
            (+ total
               (cond
                 (= :army (:type unit)) 1
                 (= :transport (:type unit)) (:army-count unit 0)
                 :else 0)))
          0
          (computer-unit-entries world)))

(defn computer-transport-count
  [world]
  (count (filter (fn [[_ unit]] (= :transport (:type unit)))
                 (computer-unit-entries world))))

(defn- flood-sea-reachable
  [computer-map starts]
  (loop [queue (into clojure.lang.PersistentQueue/EMPTY starts)
         visited (set starts)]
    (if (empty? queue)
      visited
      (let [current (peek queue)
            rest-queue (pop queue)
            sea-neighbors (for [n (neighbors computer-map current)
                                :let [cell (get-in computer-map n)]
                                :when (and (= :sea (:type cell))
                                           (not (contains? visited n)))]
                            n)]
        (recur (into rest-queue sea-neighbors)
               (into visited sea-neighbors))))))

(defn- reachable-sea-set
  [computer-map computer-sea-unit-types]
  (let [starts (for [x (range (count computer-map))
                     y (range (count (first computer-map)))
                     :let [unit (get-in computer-map [x y :contents])]
                     :when (and unit
                                (= :computer (:owner unit))
                                (computer-sea-unit-types (:type unit))
                                (= :sea (get-in computer-map [x y :type])))]
                 [x y])]
    (if (seq starts)
      (flood-sea-reachable computer-map starts)
      #{})))

(defn- coastal-land?
  [computer-map land-pos]
  (some (fn [n]
          (= :sea (get-in computer-map (conj n :type))))
        (neighbors computer-map land-pos)))

(defn- connected-coastal-candidates
  [computer-map target]
  (let [connected-land (or (flood-fill-land computer-map target)
                           #{target})]
    (filter #(coastal-land? computer-map %) connected-land)))

(defn sea-reachable-detection-points
  [computer-map detection-points computer-sea-unit-types]
  (let [reachable-sea (reachable-sea-set computer-map computer-sea-unit-types)]
    (if (empty? reachable-sea)
      #{}
      (set (filter (fn [target]
                     (some (fn [land-pos]
                             (some (fn [n]
                                   (and (= :sea (get-in computer-map (conj n :type)))
                                          (contains? reachable-sea n)))
                                   (neighbors computer-map land-pos)))
                           (connected-coastal-candidates computer-map target)))
                   detection-points)))))

(defn evaluate-invasion-start
  [{:keys [world computer-map detection-points computer-sea-unit-types]}]
  (let [reachable-points (sea-reachable-detection-points computer-map detection-points computer-sea-unit-types)
        transport-count (computer-transport-count world)
        armies (total-computer-army-resources world)]
    (cond
      (empty? reachable-points)
      {:decision :deferred
       :failure-reason :no-sea-path
       :sea-reachable-detection-points reachable-points}

      (and (zero? transport-count)
           (< armies 6))
      {:decision :deferred
       :failure-reason :insufficient-resources
       :sea-reachable-detection-points reachable-points}

      :else
      {:decision :ready
       :failure-reason nil
       :sea-reachable-detection-points reachable-points})))

(defn invasion-armies-on-target-continent
  [world target-land-set]
  (count (for [[pos unit] (computer-unit-entries world)
               :when (and (= :army (:type unit))
                          (contains? target-land-set pos))]
           pos)))

(defn armies-in-transports-to-target-continent
  [world target-land-set]
  (reduce (fn [total [_ unit]]
            (let [army-count (:army-count unit 0)
                  mission (:transport-mission unit)
                  target (:invasion-target unit)
                  major-target (:major-invasion-target unit)
                  heading-to-target? (or (contains? target-land-set target)
                                         (contains? target-land-set major-target))]
              (if (and (= :transport (:type unit))
                       (pos? army-count)
                       (:major-invasion unit)
                       (or heading-to-target?
                           (active-invasion-transport-missions mission)))
                (+ total army-count)
                total)))
          0
          (computer-unit-entries world)))
