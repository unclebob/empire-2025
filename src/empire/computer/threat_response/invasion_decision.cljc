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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:58:42.736157-05:00", :module-hash "-2016806495", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "1870989655"} {:id "def/review-interval-rounds", :kind "def", :line 4, :end-line 4, :hash "-1458306295"} {:id "def/early-failure-reasons", :kind "def", :line 5, :end-line 5, :hash "1578859977"} {:id "def/active-invasion-transport-missions", :kind "def", :line 7, :end-line 8, :hash "-1679782488"} {:id "defn-/in-bounds?", :kind "defn-", :line 10, :end-line 15, :hash "2026281899"} {:id "defn-/neighbors", :kind "defn-", :line 17, :end-line 24, :hash "1126894636"} {:id "defn-/land-or-city?", :kind "defn-", :line 26, :end-line 28, :hash "-1270821929"} {:id "defn-/flood-fill-land", :kind "defn-", :line 30, :end-line 44, :hash "-171698098"} {:id "defn-/computer-unit-entries", :kind "defn-", :line 46, :end-line 52, :hash "1831888205"} {:id "defn/total-computer-army-resources", :kind "defn", :line 54, :end-line 63, :hash "-1853660440"} {:id "defn/computer-transport-count", :kind "defn", :line 65, :end-line 68, :hash "-573275255"} {:id "defn-/flood-sea-reachable", :kind "defn-", :line 70, :end-line 84, :hash "-613226617"} {:id "defn-/reachable-sea-set", :kind "defn-", :line 86, :end-line 98, :hash "-2060666826"} {:id "defn-/coastal-land?", :kind "defn-", :line 100, :end-line 104, :hash "-71128293"} {:id "defn-/connected-coastal-candidates", :kind "defn-", :line 106, :end-line 110, :hash "-314498278"} {:id "defn/sea-reachable-detection-points", :kind "defn", :line 112, :end-line 124, :hash "-1916246574"} {:id "defn/evaluate-invasion-start", :kind "defn", :line 126, :end-line 146, :hash "1656210724"} {:id "defn/invasion-armies-on-target-continent", :kind "defn", :line 148, :end-line 153, :hash "805871703"} {:id "defn/armies-in-transports-to-target-continent", :kind "defn", :line 155, :end-line 172, :hash "-1719692887"}]}
;; clj-mutate-manifest-end
