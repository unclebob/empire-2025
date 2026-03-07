;; mutation-tested: 2026-03-02
(ns empire.computer.threat-response.processing
  "Threat mission execution helpers extracted from threat-response coordinator."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.fighter-movement :as fm]
            [empire.computer.oscillation :as oscillation]
            [empire.computer.ship-core :as ship-core]
            [empire.config.core :as config]))

(def ^:private patrol-yield-radius 4)
(def ^:private patrol-max-invasion-distance 10)
;; Keep at least one-turn margin after reaching nearest refueling site.
(def ^:private fighter-refuel-safety-buffer 1)
(def ^:private congestion-random-walk-restore-keys
  [:threat-mission :threat-center :threat-radius :threat-rounds-left
   :major-invasion :major-invasion-target])

(defn- fighter-threat-active?
  [unit]
  (or (= :fighter-sweep (:threat-mission unit))
      (= :country-defense (:threat-mission unit))
      (:major-invasion unit)))

(defn- can-stay-in-refuel-range?
  [pos remaining-fuel]
  (when (pos? remaining-fuel)
    (when-let [site (fm/find-nearest-refueling-site pos)]
      (>= remaining-fuel (+ (fm/distance-to pos site) fighter-refuel-safety-buffer)))))

(defn- move-hop-consume
  [pos target fuel]
  (when-let [hop (fm/hop-over-friendly pos target)]
    (let [remaining-fuel (- fuel (:hops hop))]
      (when (can-stay-in-refuel-range? (:dest hop) remaining-fuel)
        (when-let [{:keys [pos hops]} (fm/execute-hop pos hop)]
          (when (fm/consume-fighter-fuel pos)
            {:pos pos :steps-used hops}))))))

(defn- attack-threat-step
  [pos enemy]
  (when-let [new-pos (fm/attack-enemy pos enemy)]
    (when (fm/consume-fighter-fuel new-pos)
      {:pos new-pos :steps-used 1})))

(defn- refuel-at-adjacent-site
  [ctx pos site]
  (if (= :city (:type (get-in ((:current-world ctx)) site)))
    (do (fm/land-at-city pos site) nil)
    (do ((:update-game-map! ctx) assoc-in (conj pos :contents :fuel) config/fighter-fuel)
        {:pos pos :steps-used 1})))

(defn- refuel-threat-step
  [ctx pos]
  (let [fuel (:fuel (get-in ((:current-world ctx)) (conj pos :contents)) config/fighter-fuel)]
    (when-let [site (fm/find-nearest-refueling-site pos)]
    (if (<= (fm/distance-to pos site) 1)
      (refuel-at-adjacent-site ctx pos site)
      (move-hop-consume pos site fuel)))))

(defn- out-of-threat-radius?
  [pos center radius]
  (and center (> (core/distance pos center) radius)))

(defn- patrol-threat-step
  [pos center radius fuel]
  (when-let [{:keys [pos hops]} (fm/do-patrol pos)]
    (let [remaining-fuel (- fuel hops)]
      (when (can-stay-in-refuel-range? pos remaining-fuel)
        (when (fm/consume-fighter-fuel pos)
          (if (out-of-threat-radius? pos center radius)
            (move-hop-consume pos center remaining-fuel)
            {:pos pos :steps-used hops}))))))

(defn- fighter-sidestep-consume
  [pos center]
  (let [current-distance (core/distance pos center)
        passable (fm/get-passable-neighbors pos)
        candidates (->> passable
                        (remove fm/occupied?)
                        (map (fn [p] {:pos p :distance (core/distance p center)}))
                        (filter #(<= (:distance %) current-distance))
                        (sort-by (fn [{:keys [distance pos]}] [distance pos])))]
    (when-let [target (:pos (first candidates))]
      (when (core/move-unit-to pos target)
        (when (fm/consume-fighter-fuel target)
          {:pos target :steps-used 1})))))

(defn- start-fighter-congestion-random-walk!
  [ctx pos]
  ((:update-game-map! ctx) update-in (conj pos :contents)
   #(oscillation/start-random-walk % congestion-random-walk-restore-keys)))

(defn- fighter-random-walk-step
  [pos]
  (let [passable (fm/get-passable-neighbors pos)
        candidates (vec (remove fm/occupied? passable))]
    (if-let [target (when (seq candidates) (rand-nth candidates))]
      (if (core/move-unit-to pos target)
        (when (fm/consume-fighter-fuel target)
          target)
        pos)
      pos)))

(defn process-fighter-random-walk-round
  [ctx pos]
  (let [final-pos (or (fighter-random-walk-step pos) pos)
        unit (get-in ((:current-world ctx)) (conj final-pos :contents))]
    (when unit
      ((:update-game-map! ctx) update-in (conj final-pos :contents)
       #(-> %
            oscillation/dec-random-walk
            oscillation/maybe-restore))))
  true)

(defn fighter-step-threat
  [ctx pos unit]
  (let [center (or (:threat-center unit)
                   (:major-invasion-target unit)
                   (when-let [nearest-major-target (:nearest-major-target ctx)]
                     (nearest-major-target pos)))
        radius (:threat-radius unit (:threat-radius ctx))
        fuel (:fuel unit config/fighter-fuel)
        enemy (fm/find-adjacent-enemy pos)]
    (cond
      enemy
      (attack-threat-step pos enemy)

      (fm/should-return-to-refuel? pos fuel)
      (refuel-threat-step ctx pos)

      (out-of-threat-radius? pos center radius)
      (or (move-hop-consume pos center fuel)
          (when center (fighter-sidestep-consume pos center))
          (when (:major-invasion unit)
            (start-fighter-congestion-random-walk! ctx pos)
            nil))

      :else
      (patrol-threat-step pos center radius fuel))))

(defn process-fighter-threat
  "Overrides regular fighter logic while fighter-sweep threat mission is active.
   Returns true when handled."
  [ctx pos unit]
  (when (fighter-threat-active? unit)
    (if (oscillation/in-random-walk? unit)
      (process-fighter-random-walk-round ctx pos)
    (loop [current pos
           remaining fm/fighter-speed]
      (when (pos? remaining)
        (when-let [{:keys [pos steps-used]}
                   (fighter-step-threat ctx current (get-in ((:current-world ctx)) (conj current :contents)))]
            (recur pos (- remaining steps-used))))))
    true))

(defn- ship-threat-action
  [pos ship-type move-target]
  (or (when-let [enemy-pos (ship-core/find-adjacent-enemy-ship pos)]
        (ship-core/attack-enemy pos enemy-pos))
      (when move-target
        (ship-core/move-toward pos move-target))
      (ship-core/explore-sea pos ship-type)))

(defn- ship-sidestep-toward
  [pos target]
  (let [world (sa/current-world)
        current-distance (core/distance pos target)
        candidates (->> (ship-core/get-passable-sea-neighbors pos)
                        (filter #(nil? (:contents (get-in world %))))
                        (map (fn [p] {:pos p :distance (core/distance p target)}))
                        (filter #(<= (:distance %) current-distance))
                        (sort-by (fn [{:keys [distance pos]}] [distance pos])))]
    (when-let [choice (:pos (first candidates))]
      (when (core/move-unit-to pos choice)
        choice))))

(defn- start-ship-congestion-random-walk!
  [ctx pos]
  ((:update-game-map! ctx) update-in (conj pos :contents)
   #(oscillation/start-random-walk % congestion-random-walk-restore-keys)))

(defn- process-ship-random-walk
  [ctx pos]
  (let [world ((:current-world ctx))
        candidates (vec (->> (ship-core/get-passable-sea-neighbors pos)
                             (filter #(nil? (:contents (get-in world %))))))
        final-pos (if-let [target (when (seq candidates) (rand-nth candidates))]
                    (if (core/move-unit-to pos target) target pos)
                    pos)]
    ((:update-game-map! ctx) update-in (conj final-pos :contents)
     #(-> %
          oscillation/dec-random-walk
          oscillation/maybe-restore))
    true))

(defn- nearby-invading-transports
  [world pos]
  (let [[x y] pos
        transports (for [dx (range (- patrol-yield-radius) (inc patrol-yield-radius))
                         dy (range (- patrol-yield-radius) (inc patrol-yield-radius))
                         :when (<= (max (Math/abs dx) (Math/abs dy)) patrol-yield-radius)
                         :let [candidate [(+ x dx) (+ y dy)]
                               unit (get-in world (conj candidate :contents))]
                         :when (and unit
                                    (= :computer (:owner unit))
                                    (= :transport (:type unit))
                                    (:major-invasion unit)
                                    (#{:invading :unloading} (:transport-mission unit)))]
                     candidate)]
    (vec transports)))

(defn- land-at-distance?
  [world [x y] d]
  (boolean
   (some (fn [pos]
           (let [cell (get-in world pos)]
             (and cell (#{:land :city} (:type cell)))))
         (for [dx (range (- d) (inc d))
               dy (range (- d) (inc d))
               :when (= d (max (Math/abs dx) (Math/abs dy)))]
           [(+ x dx) (+ y dy)]))))

(defn- shore-band-score
  [world pos]
  (cond
    (land-at-distance? world pos 1) -20
    (land-at-distance? world pos 2) 12
    (land-at-distance? world pos 3) 4
    :else 0))

(defn- top-random-choice
  [scored]
  (when (seq scored)
    (let [sorted (sort-by (fn [{:keys [score]}] (- score)) scored)
          topn (vec (take 3 sorted))]
      (:pos (rand-nth topn)))))

(defn- candidate-neighbors
  [world pos center]
  (let [passable (ship-core/get-passable-sea-neighbors pos)
        empty-passable (filter #(nil? (:contents (get-in world %))) passable)]
    (if center
      (filter #(<= (core/distance % center) patrol-max-invasion-distance) empty-passable)
      empty-passable)))

(defn- patrol-stand-off-step
  [ctx pos center]
  (let [world ((:current-world ctx))
        candidates (candidate-neighbors world pos center)
        scored (for [cand candidates]
                 {:pos cand
                  :score (+ (shore-band-score world cand)
                            ;; Bias toward invasion point while respecting max radius.
                            (if center (- 12 (min 12 (core/distance cand center))) 0))})]
    (when-let [target (top-random-choice scored)]
      (when (core/move-unit-to pos target)
        target))))

(defn- patrol-yield-to-transport
  [ctx pos center]
  (let [world ((:current-world ctx))]
    (let [transports (nearby-invading-transports world pos)]
      (when (seq transports)
        (let [candidates (candidate-neighbors world pos center)
              scored (for [cand candidates
                           :let [clearance (apply min (map #(core/distance cand %) transports))
                                 center-bias (if center
                                               (- 8 (min 8 (core/distance cand center)))
                                               0)]]
                       {:pos cand
                        ;; Yield should prioritize clearing transport lanes over target pursuit.
                        :score (+ (* 4 clearance)
                                  (shore-band-score world cand)
                                  (quot center-bias 4))})]
          ;; Use deterministic tie-breaking so yield behavior is stable in tests and gameplay.
          ;; Prefer better score, then a stable board-order tie break.
          (when-let [target (:pos (first (sort-by (fn [{:keys [score] :as cand}]
                                                    (let [cand-pos (:pos cand)
                                                          row (first cand-pos)
                                                          col (second cand-pos)]
                                                      [(- score)
                                                       (- row)
                                                       col]))
                                                  scored)))]
            (when (core/move-unit-to pos target)
              target)))))))

(defn- sea-scout-target
  [pos center radius]
  (when (and center (> (core/distance pos center) radius))
    center))

(defn- major-invasion-target
  [nearest-major-target pos center]
  (or center (nearest-major-target pos)))

(defn- occupied-position? [ctx pos]
  (some? (get-in ((:current-world ctx)) (conj pos :contents))))

(defn- patrol-major-invasion-step
  [ctx nearest-major-target current center]
  (or (when-let [enemy-pos (ship-core/find-adjacent-enemy-ship current)]
        (ship-core/attack-enemy current enemy-pos))
      (patrol-stand-off-step ctx current
                             (major-invasion-target nearest-major-target current center))))

(defn- run-patrol-major-invasion
  [ctx nearest-major-target pos center]
  ;; Keep patrol boats cheap in crowded invasion theaters, but preserve patrol speed.
  ;; Do not run expensive BFS explore fallback while invasion is active.
  (loop [current pos
         steps-left 4]
    (cond
      (zero? steps-left) current
      (not (occupied-position? ctx current)) current
      :else
      (if-let [yield-pos (patrol-yield-to-transport ctx current center)]
        ;; Yield once to clear transport lanes, then stop this patrol boat for the round.
        yield-pos
        (recur (or (patrol-major-invasion-step ctx nearest-major-target current center)
                   current)
               (dec steps-left))))))

(defn- handle-sea-scout-ship-threat
  [pos ship-type center radius]
  (ship-threat-action pos ship-type (sea-scout-target pos center radius))
  true)

(defn- handle-major-invasion-ship-threat
  [ctx nearest-major-target pos ship-type center]
  (if (= :patrol-boat ship-type)
    (run-patrol-major-invasion ctx nearest-major-target pos center)
    (let [target (major-invasion-target nearest-major-target pos center)]
      (or (when-let [enemy-pos (ship-core/find-adjacent-enemy-ship pos)]
            (ship-core/attack-enemy pos enemy-pos))
          (when target
            (or (ship-core/move-toward pos target)
                (ship-sidestep-toward pos target)
                (do (start-ship-congestion-random-walk! ctx pos)
                    true)))
          (ship-core/explore-sea pos ship-type))))
  true)

(defn process-ship-threat
  "Overrides regular ship logic for sea-scout and major-invasion missions.
   Returns true when handled."
  [ctx pos ship-type unit]
  (let [center (or (:threat-center unit) (:major-invasion-target unit))
        radius (:threat-radius unit (:threat-radius ctx))
        nearest-major-target (:nearest-major-target ctx)
        result (cond
                 (and (:major-invasion unit) (oscillation/in-random-walk? unit))
                 (process-ship-random-walk ctx pos)

                 (= :sea-scout (:threat-mission unit))
                 (handle-sea-scout-ship-threat pos ship-type center radius)

                 (:major-invasion unit)
                 (handle-major-invasion-ship-threat ctx nearest-major-target pos ship-type center)

                 :else false)]
    result))
