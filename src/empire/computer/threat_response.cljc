;; mutation-tested: no
(ns empire.computer.threat-response
  "Threat-response coordinator for enemy detections.
   Handles fighter/ship local responses and global major invasion mobilization."
  (:require [empire.atoms :as atoms]
            [empire.computer.core :as core]
            [empire.computer.fighter-movement :as fm]
            [empire.computer.ship-core :as ship-core]
            [empire.config :as config]
            [empire.movement.pathfinding-bfs :as pathfinding-bfs]))

(def ^:private fighter-response-count 4)
(def ^:private ship-response-count 2)
(def ^:private fighter-sweep-rounds 10)
(def ^:private ship-scout-rounds 10)
(def ^:private threat-radius 5)

(def ^:private enemy-ship-types
  #{:patrol-boat :destroyer :submarine :transport :carrier :battleship})

(def ^:private major-invasion-ship-types
  #{:patrol-boat :destroyer :submarine :carrier :battleship})

(defn major-invasion-active?
  []
  (:active? @atoms/major-invasion-state))

(defn major-invasion-detection-points
  []
  (:detection-points @atoms/major-invasion-state))

(defn major-invasion-target-land?
  [pos]
  (contains? (:target-land-set @atoms/major-invasion-state) pos))

(defn- land-or-city?
  [cell]
  (and cell (#{:land :city} (:type cell))))

(defn- flood-fill-land
  "Flood-fill on game-map over land/city cells."
  [start]
  (let [game-map @atoms/game-map]
    (when (land-or-city? (get-in game-map start))
      (loop [frontier #{start}
             visited #{}]
        (if (empty? frontier)
          visited
          (let [pos (first frontier)
                rest-frontier (disj frontier pos)]
            (if (contains? visited pos)
              (recur rest-frontier visited)
              (let [neighbors (filter (fn [n]
                                        (land-or-city? (get-in game-map n)))
                                      (core/get-neighbors pos))]
                (recur (into rest-frontier neighbors)
                       (conj visited pos))))))))))

(defn- recompute-major-invasion-target-land!
  []
  (let [points (:detection-points @atoms/major-invasion-state)
        target-land (reduce (fn [acc p]
                              (into acc (or (flood-fill-land p) #{})))
                            #{}
                            points)]
    (swap! atoms/major-invasion-state assoc :target-land-set target-land)))

(defn- find-computer-unit-positions
  [pred]
  (let [game-map @atoms/game-map]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [unit (get-in game-map [i j :contents])]
          :when (and unit
                     (= :computer (:owner unit))
                     (pred unit))]
      [i j])))

(defn- assign-threat-mission!
  [positions mission-kv]
  (doseq [pos positions]
    (swap! atoms/game-map update-in (conj pos :contents) merge mission-kv)))

(defn- closest-positions
  [origin positions n]
  (->> positions
       (sort-by #(core/distance % origin))
       (take n)))

(defn- activate-major-invasion!
  [pos]
  (swap! atoms/major-invasion-state
         (fn [s]
           (-> s
               (assoc :active? true)
               (update :detection-points conj pos)
               (assoc :started-round (or (:started-round s) @atoms/round-number)))))
  (recompute-major-invasion-target-land!))

(defn- handle-fighter-detection!
  [pos]
  (let [fighters (find-computer-unit-positions #(= :fighter (:type %)))
        selected (closest-positions pos fighters fighter-response-count)]
    (assign-threat-mission!
     selected
     {:threat-mission :fighter-sweep
      :threat-center pos
      :threat-radius threat-radius
      :threat-rounds-left fighter-sweep-rounds})))

(defn- handle-ship-detection!
  [pos]
  (let [patrols (find-computer-unit-positions #(= :patrol-boat (:type %)))
        battleships (find-computer-unit-positions #(= :battleship (:type %)))
        psel (closest-positions pos patrols ship-response-count)
        bsel (closest-positions pos battleships ship-response-count)
        selected (concat psel bsel)]
    (assign-threat-mission!
     selected
     {:threat-mission :sea-scout
      :threat-center pos
      :threat-radius threat-radius
      :threat-rounds-left ship-scout-rounds})))

(defn handle-detection!
  "Handle a newly-visible cell on computer-map for threat triggers."
  [pos game-cell]
  (let [unit (:contents game-cell)
        player-unit-type? (fn [t] (and unit (= :player (:owner unit)) (= t (:type unit))))
        player-ship? (fn [] (and unit (= :player (:owner unit)) (enemy-ship-types (:type unit))))
        player-city? (fn [] (and (= :city (:type game-cell)) (= :player (:city-status game-cell))))]
    (cond
      (player-unit-type? :fighter) (handle-fighter-detection! pos)
      (player-ship?) (handle-ship-detection! pos)
      (player-unit-type? :army) (activate-major-invasion! pos)
      (player-city?) (activate-major-invasion! pos)
      :else nil)))

(defn- dec-threat-rounds
  [unit]
  (if-let [left (:threat-rounds-left unit)]
    (let [next-left (dec left)]
      (if (pos? next-left)
        (assoc unit :threat-rounds-left next-left)
        (dissoc unit :threat-mission :threat-center :threat-radius :threat-rounds-left)))
    unit))

(defn- nearest-major-target
  [pos]
  (when-let [pts (seq (:detection-points @atoms/major-invasion-state))]
    (apply min-key #(core/distance pos %) pts)))

(defn- prepare-transport-major-invasion!
  [pos unit]
  (let [army-count (:army-count unit 0)
        target (nearest-major-target pos)
        mission (:transport-mission unit)]
    (when target
      (swap! atoms/game-map update-in (conj pos :contents)
             assoc :major-invasion true :major-invasion-target target))
    (cond
      (zero? army-count)
      (swap! atoms/game-map update-in (conj pos :contents)
             assoc :transport-mission :loading)

      (and target (not= mission :unloading))
      (when-let [path (pathfinding-bfs/bfs-to-land-ho-target pos target @atoms/computer-map)]
        (swap! atoms/game-map update-in (conj pos :contents)
               assoc :transport-mission :invading
               :invasion-target target
               :invasion-path (vec path))))))

(defn refresh-major-invasion-assignments!
  "Applies major-invasion tags/targets to all mobilized computer units."
  []
  (when (major-invasion-active?)
    (let [units (find-computer-unit-positions (constantly true))]
      (doseq [pos units
              :let [unit (get-in @atoms/game-map (conj pos :contents))
                    t (:type unit)]]
        (cond
          (= :fighter t)
          (swap! atoms/game-map update-in (conj pos :contents)
                 assoc :major-invasion true
                 :major-invasion-target (nearest-major-target pos))

          (major-invasion-ship-types t)
          (swap! atoms/game-map update-in (conj pos :contents)
                 assoc :major-invasion true
                 :major-invasion-target (nearest-major-target pos))

          (= :transport t)
          (prepare-transport-major-invasion! pos unit)

          :else nil)))))

(defn on-round-start!
  "Round-start maintenance for threat responses."
  []
  ;; Tick temporary fighter/ship response timers.
  (let [game-map @atoms/game-map]
    (doseq [i (range (count game-map))
            j (range (count (first game-map)))
            :let [unit (get-in game-map [i j :contents])]
            :when (and unit
                       (= :computer (:owner unit))
                       (:threat-mission unit))]
      (swap! atoms/game-map update-in [i j :contents] dec-threat-rounds)))
  ;; Recompute invasion theater and refresh global mobilization tags each round.
  (when (major-invasion-active?)
    (recompute-major-invasion-target-land!)
    (refresh-major-invasion-assignments!)))

(defn prepare-transport!
  "Called by transport processing; applies major-invasion directives when active."
  [pos]
  (when (major-invasion-active?)
    (when-let [unit (get-in @atoms/game-map (conj pos :contents))]
      (when (= :transport (:type unit))
        (prepare-transport-major-invasion! pos unit)
        true))))

(defn- move-hop-consume
  [pos target]
  (when-let [hop (fm/hop-over-friendly pos target)]
    (when-let [{:keys [pos hops]} (fm/execute-hop pos hop)]
      (when (fm/consume-fighter-fuel pos)
        {:pos pos :steps-used hops}))))

(defn- attack-threat-step
  [pos enemy]
  (when-let [new-pos (fm/attack-enemy pos enemy)]
    (when (fm/consume-fighter-fuel new-pos)
      {:pos new-pos :steps-used 1})))

(defn- refuel-at-adjacent-site
  [pos site]
  (if (= :city (:type (get-in @atoms/game-map site)))
    (do (fm/land-at-city pos site) nil)
    (do (swap! atoms/game-map assoc-in (conj pos :contents :fuel) config/fighter-fuel)
        {:pos pos :steps-used 1})))

(defn- refuel-threat-step
  [pos]
  (when-let [site (fm/find-nearest-refueling-site pos)]
    (if (<= (fm/distance-to pos site) 1)
      (refuel-at-adjacent-site pos site)
      (move-hop-consume pos site))))

(defn- out-of-threat-radius?
  [pos center radius]
  (and center (> (core/distance pos center) radius)))

(defn- patrol-threat-step
  [pos center radius]
  (when-let [{:keys [pos hops]} (fm/do-patrol pos)]
    (when (fm/consume-fighter-fuel pos)
      (if (out-of-threat-radius? pos center radius)
        (move-hop-consume pos center)
        {:pos pos :steps-used hops}))))

(defn- fighter-step-threat
  [pos unit]
  (let [center (:threat-center unit)
        radius (:threat-radius unit threat-radius)
        fuel (:fuel unit config/fighter-fuel)
        enemy (fm/find-adjacent-enemy pos)]
    (cond
      enemy
      (attack-threat-step pos enemy)

      (fm/should-return-to-refuel? pos fuel)
      (refuel-threat-step pos)

      (out-of-threat-radius? pos center radius)
      (move-hop-consume pos center)

      :else
      (patrol-threat-step pos center radius))))

(defn process-fighter-threat
  "Overrides regular fighter logic while fighter-sweep threat mission is active.
   Returns true when handled."
  [pos unit]
  (when (= :fighter-sweep (:threat-mission unit))
    (loop [current pos
           remaining fm/fighter-speed]
      (when (pos? remaining)
        (when-let [{:keys [pos steps-used]} (fighter-step-threat current (get-in @atoms/game-map (conj current :contents)))]
          (recur pos (- remaining steps-used)))))
    true))

(defn- ship-threat-action
  [pos ship-type move-target]
  (or (when-let [enemy-pos (ship-core/find-adjacent-enemy-ship pos)]
        (ship-core/attack-enemy pos enemy-pos))
      (when move-target
        (ship-core/move-toward pos move-target))
      (ship-core/explore-sea pos ship-type)))

(defn- sea-scout-target
  [pos center radius]
  (when (and center (> (core/distance pos center) radius))
    center))

(defn- major-invasion-target
  [pos center]
  (or center (nearest-major-target pos)))

(defn- handle-sea-scout-ship-threat
  [pos ship-type center radius]
  (ship-threat-action pos ship-type (sea-scout-target pos center radius))
  true)

(defn- handle-major-invasion-ship-threat
  [pos ship-type center]
  (ship-threat-action pos ship-type (major-invasion-target pos center))
  true)

(defn process-ship-threat
  "Overrides regular ship logic for sea-scout and major-invasion missions.
   Returns true when handled."
  [pos ship-type unit]
  (let [center (or (:threat-center unit) (:major-invasion-target unit))
        radius (:threat-radius unit threat-radius)]
    (cond
      (= :sea-scout (:threat-mission unit))
      (handle-sea-scout-ship-threat pos ship-type center radius)

      (:major-invasion unit)
      (handle-major-invasion-ship-threat pos ship-type center)

      :else false)))
