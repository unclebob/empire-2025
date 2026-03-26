(ns empire.computer.army.assignment
  "Attack-target and transport staging assignment for computer armies."
  (:require [empire.state.api :as sa]
            [empire.computer.early-game.strategy :as opening]
            [empire.computer.transport.load-targeting :as load-targeting]
            [empire.computer.transport.reservations :as reservations]
            [empire.game-mechanics.visibility :as visibility]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.computer.army.assignment-decisions :as decisions]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.world-query :as world-query]
            [empire.computer.land-objectives :as land-objectives]))

(def ^:private transport-staging-radius 5)
(def ^:private max-staging-armies 6)
(def ^:private max-returning-staging-armies 6)
(def ^:private producer-staging-cells-per-side 3)

(defn- transport-staging-mode?
  [unit]
  (= :move-to-coast-for-transport (:mode unit)))

(defn- assignable-staging-army?
  [anchor unit-pos unit]
  (and (= :army (:type unit))
       (= :computer (:owner unit))
       (<= (grid/chebyshev-distance anchor unit-pos) transport-staging-radius)
       (not (:attack-target unit))
       (not= :move-to-coast-for-invasion (:mode unit))))

(defn- claimed-land?
  [cell]
  (or (and (= :land (:type cell))
           (some? (:country-id cell)))
      (and (= :city (:type cell))
           (= :computer (:city-status cell)))))

(defn- staging-armies
  [anchor]
  (let [computer-map (sa/read-state :computer-map)
        positions (for [c (range (count computer-map))
                        r (range (count (first computer-map)))]
                    [c r])]
    (->> positions
         (keep (fn [pos]
                 (let [unit (get-in computer-map (conj pos :contents))]
                   (when (assignable-staging-army? anchor pos unit)
                     {:pos pos
                      :distance (grid/chebyshev-distance anchor pos)
                      :already-staging? (transport-staging-mode? unit)}))))
         (sort-by (juxt :distance :already-staging? :pos))
         (take max-staging-armies))))

(defn- assign-staging-armies!
  [anchor targets]
  (doseq [[{:keys [pos]} target]
          (map vector
               (staging-armies anchor)
               (cycle targets))]
    (let [current (get-in (sa/read-state :computer-map) (conj pos :contents))]
      (when (or (not= :move-to-coast-for-transport (:mode current))
                (not= target (:transport-staging-target current)))
        (sa/update-world! update-in (conj pos :contents)
                          #(assoc %
                                  :mode :move-to-coast-for-transport
                                  :transport-staging-target target))
        (visibility/sync-ai-unit-to-computer-map! pos)))))

(defn- producer-staging-cell?
  [computer-map city-pos country-id pos]
  (let [cell (get-in computer-map pos)]
    (and (not= city-pos pos)
         (= :land (:type cell))
         (= country-id (:country-id cell))
         (map-utils/adjacent-to-sea? pos computer-map))))

(defn- producer-staging-seed-cells
  [computer-map city-pos country-id]
  (->> (world-query/get-neighbors city-pos)
       (filter #(= :sea (:type (get-in computer-map %))))
       (mapcat world-query/get-neighbors)
       (filter #(producer-staging-cell? computer-map city-pos country-id %))
       sort
       distinct))

(defn- producer-branch-cells
  [computer-map city-pos country-id seed visited]
  (loop [queue (conj clojure.lang.PersistentQueue/EMPTY seed)
         seen (conj visited seed)
         branch []]
    (if (empty? queue)
      branch
      (let [current (peek queue)
            branch' (conj branch current)]
        (if (>= (count branch') producer-staging-cells-per-side)
          branch'
          (let [neighbors (->> (world-query/get-neighbors current)
                               (filter #(producer-staging-cell? computer-map city-pos country-id %))
                               (remove seen)
                               sort)]
            (recur (reduce conj (pop queue) neighbors)
                   (into seen neighbors)
                   branch')))))))

(defn- producer-staging-targets
  [city-pos]
  (let [computer-map (sa/read-state :computer-map)
        country-id (:country-id (get-in computer-map city-pos))
        seeds (producer-staging-seed-cells computer-map city-pos country-id)]
    (loop [remaining seeds
           visited #{}
           targets []]
      (if (empty? remaining)
        targets
        (let [seed (first remaining)
              branch (if (contains? visited seed)
                       []
                       (producer-branch-cells computer-map city-pos country-id seed visited))]
          (recur (rest remaining)
                 (into visited branch)
                 (into targets branch)))))))

(defn- staging-anchor-for-sail-to-load
  [transport-pos]
  (let [computer-map (sa/read-state :computer-map)
        transport (get-in computer-map (conj transport-pos :contents))
        endpoint (or (last (:sail-path transport))
                     transport-pos)]
    (->> (world-query/get-neighbors endpoint)
         (filter #(claimed-land? (get-in computer-map %)))
         (sort)
         first)))

(defn- assignable-load-target-army?
  [reserved-army-ids unit]
  (and (= :army (:type unit))
       (= :computer (:owner unit))
       (not (contains? reserved-army-ids (:computer-unit-id unit)))
       (not (:attack-target unit))
       (not= :move-to-coast-for-invasion (:mode unit))))

(defn- returning-load-target
  [transport-pos target]
  (or target
      (get-in (sa/read-state :computer-map) (conj transport-pos :contents :load-target-cell))))

(defn- load-target-staging-armies
  [transport-id target]
  (let [computer-map (sa/read-state :computer-map)]
    (->> (load-targeting/neighborhood-tile-army-positions target computer-map)
         (remove (comp (reservations/reserved-army-ids transport-id)
                       #(get-in computer-map (conj % :contents :computer-unit-id))))
         (keep (fn [pos]
                 (let [unit (get-in computer-map (conj pos :contents))]
                   (when (assignable-load-target-army?
                          (reservations/reserved-army-ids transport-id)
                          unit)
                     {:pos pos
                      :distance (grid/chebyshev-distance pos target)
                      :already-staging? (transport-staging-mode? unit)
                      :computer-unit-id (:computer-unit-id unit)}))))
         (sort-by (juxt :distance :already-staging? :pos))
         (take max-returning-staging-armies))))

(defn- choose-load-target-staging-target
  [current targets]
  (let [current-target (:transport-staging-target current)]
    (if (some #{current-target} targets)
      current-target
      (rand-nth (vec targets)))))

(defn- assign-load-target-staging-armies!
  [transport-id target]
  (let [computer-map (sa/read-state :computer-map)
        landing-zone-targets (or (seq (load-targeting/neighborhood-tile-coastal-targets target computer-map))
                                 [target])
        selected (vec (load-target-staging-armies transport-id target))]
    (doseq [{:keys [pos]} selected]
      (let [current (get-in (sa/read-state :computer-map) (conj pos :contents))
            staging-target (choose-load-target-staging-target current landing-zone-targets)]
        (when (or (not= :move-to-coast-for-transport (:mode current))
                  (not= staging-target (:transport-staging-target current)))
          (sa/update-world! update-in (conj pos :contents)
                            #(assoc %
                                    :mode :move-to-coast-for-transport
                                    :transport-staging-target staging-target))
          (visibility/sync-ai-unit-to-computer-map! pos))))
    (vec (keep :computer-unit-id selected))))

(defn assign-city-attacks
  "Scans computer-map for visible free/player cities and assigns up to 6 closest armies each."
  []
  (let [cities (decisions/visible-target-cities (sa/read-state :computer-map))
        armies (decisions/assignable-armies (sa/read-state :computer-map))
        assignments (decisions/assignment-updates cities
                                                  armies
                                                  contains?
                                                  land-objectives/flood-fill-continent
                                                  grid/distance)]
    (doseq [{:keys [pos target]} assignments]
      (sa/update-world! assoc-in (conj pos :contents :attack-target) target))))

(defn assign-producer-transport-staging!
  []
  (doseq [[pos prod] (sa/read-state :production)
          :when (and (= :transport (:item prod))
                     (<= (:remaining-rounds prod 99) transport-staging-radius)
                     (opening/city-usable-coastal? pos))]
    (when-let [targets (seq (producer-staging-targets pos))]
      (assign-staging-armies! pos targets))))

(defn assign-returning-transport-staging-at!
  ([transport-pos]
   (assign-returning-transport-staging-at! transport-pos nil))
  ([transport-pos target]
  (let [transport-id (get-in (sa/read-state :computer-map)
                             (conj transport-pos :contents :transport-id))]
    (if-let [target (returning-load-target transport-pos target)]
      (assign-load-target-staging-armies! transport-id target)
      (when-let [anchor (staging-anchor-for-sail-to-load transport-pos)]
        (assign-staging-armies! anchor [anchor])
        [])))))

(defn assign-returning-transport-staging!
  []
  (let [computer-map (sa/read-state :computer-map)]
    (doseq [c (range (count computer-map))
            r (range (count (first computer-map)))
            :let [pos [c r]
                  unit (get-in computer-map (conj pos :contents))]
            :when (and (= :transport (:type unit))
                       (= :computer (:owner unit))
                       (= :sail-to-load (:transport-mission unit)))]
      (assign-returning-transport-staging-at! pos))))

(defn assign-transport-staging!
  []
  (assign-producer-transport-staging!)
  (assign-returning-transport-staging!))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T14:21:45.748132-05:00", :module-hash "-419708176", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "-72483662"} {:id "defn/assign-city-attacks", :kind "defn", :line 8, :end-line 19, :hash "1116748109"}]}
;; clj-mutate-manifest-end
