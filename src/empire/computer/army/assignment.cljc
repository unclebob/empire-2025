(ns empire.computer.army.assignment
  "Attack-target and transport staging assignment for computer armies."
  (:require [empire.state.api :as sa]
            [empire.computer.early-game.strategy :as opening]
            [empire.computer.transport.load-targeting :as load-targeting]
            [empire.game-mechanics.visibility :as visibility]
            [empire.computer.army.assignment-decisions :as decisions]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.world-query :as world-query]
            [empire.computer.land-objectives :as land-objectives]))

(def ^:private transport-staging-radius 5)
(def ^:private max-staging-armies 6)
(def ^:private max-returning-staging-armies 5)

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
  [anchor]
  (doseq [{:keys [pos]} (staging-armies anchor)]
    (sa/update-world! update-in (conj pos :contents)
                      #(assoc %
                              :mode :move-to-coast-for-transport
                              :transport-staging-target anchor))
    (visibility/sync-ai-unit-to-computer-map! pos)))

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
  [unit]
  (and (= :army (:type unit))
       (= :computer (:owner unit))
       (not (:attack-target unit))
       (not= :move-to-coast-for-invasion (:mode unit))))

(defn- returning-load-target
  [transport-pos]
  (get-in (sa/read-state :computer-map) (conj transport-pos :contents :load-target-cell)))

(defn- load-target-staging-armies
  [target]
  (let [computer-map (sa/read-state :computer-map)]
    (->> (load-targeting/neighborhood-tile-army-positions target computer-map)
         shuffle
         (keep (fn [pos]
                 (let [unit (get-in computer-map (conj pos :contents))]
                   (when (assignable-load-target-army? unit)
                     {:pos pos
                      :computer-unit-id (:computer-unit-id unit)}))))
         (take max-returning-staging-armies))))

(defn- assign-load-target-staging-armies!
  [target]
  (let [selected (vec (load-target-staging-armies target))]
    (doseq [{:keys [pos]} selected]
      (sa/update-world! update-in (conj pos :contents)
                        #(assoc %
                                :mode :move-to-coast-for-transport
                                :transport-staging-target target))
      (visibility/sync-ai-unit-to-computer-map! pos))
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
    (assign-staging-armies! pos)))

(defn assign-returning-transport-staging-at!
  [transport-pos]
  (if-let [target (returning-load-target transport-pos)]
    (assign-load-target-staging-armies! target)
    (when-let [anchor (staging-anchor-for-sail-to-load transport-pos)]
      (assign-staging-armies! anchor)
      [])))

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
