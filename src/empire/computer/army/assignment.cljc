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
  (let [selected (vec (load-target-staging-armies transport-id target))]
    (doseq [{:keys [pos]} selected]
      (let [current (get-in (sa/read-state :computer-map) (conj pos :contents))
            staging-target (choose-load-target-staging-target current [target])]
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
;; {:version 1, :tested-at "2026-03-26T22:18:46.983927-05:00", :module-hash "-1484712455", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 12, :hash "-430255929"} {:id "def/transport-staging-radius", :kind "def", :line 14, :end-line 14, :hash "36521304"} {:id "def/max-staging-armies", :kind "def", :line 15, :end-line 15, :hash "1349315492"} {:id "def/max-returning-staging-armies", :kind "def", :line 16, :end-line 16, :hash "-289178787"} {:id "def/producer-staging-cells-per-side", :kind "def", :line 17, :end-line 17, :hash "143056762"} {:id "defn-/transport-staging-mode?", :kind "defn-", :line 19, :end-line 21, :hash "2124331926"} {:id "defn-/assignable-staging-army?", :kind "defn-", :line 23, :end-line 29, :hash "1111446583"} {:id "defn-/claimed-land?", :kind "defn-", :line 31, :end-line 36, :hash "1424037850"} {:id "defn-/staging-armies", :kind "defn-", :line 38, :end-line 52, :hash "432751848"} {:id "defn-/assign-staging-armies!", :kind "defn-", :line 54, :end-line 67, :hash "-1953349379"} {:id "defn-/producer-staging-cell?", :kind "defn-", :line 69, :end-line 75, :hash "-1472626004"} {:id "defn-/producer-staging-seed-cells", :kind "defn-", :line 77, :end-line 84, :hash "-1548094276"} {:id "defn-/producer-branch-cells", :kind "defn-", :line 86, :end-line 103, :hash "-404764016"} {:id "defn-/producer-staging-targets", :kind "defn-", :line 105, :end-line 121, :hash "1951476364"} {:id "defn-/staging-anchor-for-sail-to-load", :kind "defn-", :line 123, :end-line 132, :hash "1265462819"} {:id "defn-/assignable-load-target-army?", :kind "defn-", :line 134, :end-line 140, :hash "-963390664"} {:id "defn-/returning-load-target", :kind "defn-", :line 142, :end-line 145, :hash "69667170"} {:id "defn-/load-target-staging-armies", :kind "defn-", :line 147, :end-line 163, :hash "-417808589"} {:id "defn-/choose-load-target-staging-target", :kind "defn-", :line 165, :end-line 170, :hash "325915003"} {:id "defn-/assign-load-target-staging-armies!", :kind "defn-", :line 172, :end-line 185, :hash "2048758232"} {:id "defn/assign-city-attacks", :kind "defn", :line 187, :end-line 198, :hash "1651822477"} {:id "defn/assign-producer-transport-staging!", :kind "defn", :line 200, :end-line 206, :hash "1261905038"} {:id "defn/assign-returning-transport-staging-at!", :kind "defn", :line 208, :end-line 218, :hash "64326099"} {:id "defn/assign-returning-transport-staging!", :kind "defn", :line 220, :end-line 230, :hash "-1515140133"} {:id "defn/assign-transport-staging!", :kind "defn", :line 232, :end-line 235, :hash "244571516"}]}
;; clj-mutate-manifest-end
