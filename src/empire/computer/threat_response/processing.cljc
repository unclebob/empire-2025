(ns empire.computer.threat-response.processing
  "Threat mission execution helpers extracted from threat-response coordinator."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.fighter-movement :as fm]
            [empire.computer.oscillation :as oscillation]
            [empire.computer.ship-core :as ship-core]
            [empire.computer.threat-response.processing-decisions :as decisions]
            [empire.config.core :as config]))

(def ^:private patrol-yield-radius 4)
(def ^:private patrol-max-invasion-distance 10)
;; Keep at least one-turn margin after reaching nearest refueling site.
(def ^:private fighter-refuel-safety-buffer 1)
(def ^:private congestion-random-walk-restore-keys
  [:threat-mission :threat-center :threat-radius :threat-rounds-left
   :major-invasion :major-invasion-target])

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
  (let [candidates (vec (remove fm/occupied? (fm/get-passable-neighbors pos)))]
    (if-let [target (when (seq candidates) (rand-nth candidates))]
      (if (core/move-unit-to pos target)
        (when (fm/consume-fighter-fuel target)
          target)
        pos)
      pos)))

(defn- update-fighter-random-walk!
  [ctx final-pos]
  (when (get-in ((:current-world ctx)) (conj final-pos :contents))
    ((:update-game-map! ctx) update-in (conj final-pos :contents)
     #(-> %
          oscillation/dec-random-walk
          oscillation/maybe-restore))))

(defn process-fighter-random-walk-round
  [ctx pos]
  (let [final-pos (or (fighter-random-walk-step pos) pos)]
    (update-fighter-random-walk! ctx final-pos))
  true)

(declare fighter-step-threat)

(defn- outside-radius-step
  [ctx pos center fuel unit]
  (or (move-hop-consume pos center fuel)
      (when center (fighter-sidestep-consume pos center))
      (when (:major-invasion unit)
        (start-fighter-congestion-random-walk! ctx pos)
        nil)))

(defn- next-fighter-threat-state
  [ctx current remaining]
  (decisions/next-threat-state
   remaining
   (fighter-step-threat ctx current (get-in ((:current-world ctx)) (conj current :contents)))))

(defn fighter-step-threat
  [ctx pos unit]
  (let [center (decisions/fighter-threat-center
                {:unit unit
                 :nearest-major-target (:nearest-major-target ctx)
                 :pos pos})
        radius (:threat-radius unit (:threat-radius ctx))
        fuel (:fuel unit config/fighter-fuel)
        enemy (fm/find-adjacent-enemy pos)
        action (decisions/fighter-threat-action
                {:enemy? (boolean enemy)
                 :low-fuel? (fm/should-return-to-refuel? pos fuel)
                 :outside-radius? (out-of-threat-radius? pos center radius)})]
    (case action
      :attack (attack-threat-step pos enemy)
      :refuel (refuel-threat-step ctx pos)
      :outside-radius (outside-radius-step ctx pos center fuel unit)
      (patrol-threat-step pos center radius fuel))))

(defn- run-fighter-threat-round
  [ctx pos]
  (loop [current pos
         remaining fm/fighter-speed]
    (when-let [{:keys [pos remaining]} (next-fighter-threat-state ctx current remaining)]
      (recur pos remaining))))

(defn- run-active-fighter-threat
  [ctx pos unit]
  (if (oscillation/in-random-walk? unit)
    (process-fighter-random-walk-round ctx pos)
    (run-fighter-threat-round ctx pos)))

(defn process-fighter-threat
  "Overrides regular fighter logic while fighter-sweep threat mission is active.
   Returns true when handled."
  [ctx pos unit]
  (when (decisions/fighter-threat-active? unit)
    (run-active-fighter-threat ctx pos unit)
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T09:18:15.840519-05:00", :module-hash "438905545", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 9, :hash "18527141"} {:id "def/patrol-yield-radius", :kind "def", :line 11, :end-line 11, :hash "-2055166791"} {:id "def/patrol-max-invasion-distance", :kind "def", :line 12, :end-line 12, :hash "-1594233431"} {:id "def/fighter-refuel-safety-buffer", :kind "def", :line 14, :end-line 14, :hash "341204353"} {:id "def/congestion-random-walk-restore-keys", :kind "def", :line 15, :end-line 17, :hash "-695224494"} {:id "defn-/can-stay-in-refuel-range?", :kind "defn-", :line 19, :end-line 23, :hash "1863176686"} {:id "defn-/move-hop-consume", :kind "defn-", :line 25, :end-line 32, :hash "-879720438"} {:id "defn-/attack-threat-step", :kind "defn-", :line 34, :end-line 38, :hash "-1527541517"} {:id "defn-/refuel-at-adjacent-site", :kind "defn-", :line 40, :end-line 45, :hash "2001014465"} {:id "defn-/refuel-threat-step", :kind "defn-", :line 47, :end-line 53, :hash "-1301196289"} {:id "defn-/out-of-threat-radius?", :kind "defn-", :line 55, :end-line 57, :hash "-365756115"} {:id "defn-/patrol-threat-step", :kind "defn-", :line 59, :end-line 67, :hash "-713058374"} {:id "defn-/fighter-sidestep-consume", :kind "defn-", :line 69, :end-line 81, :hash "-1817054816"} {:id "defn-/start-fighter-congestion-random-walk!", :kind "defn-", :line 83, :end-line 86, :hash "435727451"} {:id "defn-/fighter-random-walk-step", :kind "defn-", :line 88, :end-line 96, :hash "783131758"} {:id "defn-/update-fighter-random-walk!", :kind "defn-", :line 98, :end-line 104, :hash "-1480771239"} {:id "defn/process-fighter-random-walk-round", :kind "defn", :line 106, :end-line 110, :hash "-1302042158"} {:id "form/17/declare", :kind "declare", :line 112, :end-line 112, :hash "-165607427"} {:id "defn-/outside-radius-step", :kind "defn-", :line 114, :end-line 120, :hash "650097571"} {:id "defn-/next-fighter-threat-state", :kind "defn-", :line 122, :end-line 126, :hash "425007148"} {:id "defn/fighter-step-threat", :kind "defn", :line 128, :end-line 145, :hash "1199744703"} {:id "defn-/run-fighter-threat-round", :kind "defn-", :line 147, :end-line 152, :hash "1297858482"} {:id "defn-/run-active-fighter-threat", :kind "defn-", :line 154, :end-line 158, :hash "-1079417595"} {:id "defn/process-fighter-threat", :kind "defn", :line 160, :end-line 166, :hash "1299266710"} {:id "defn-/ship-threat-action", :kind "defn-", :line 168, :end-line 174, :hash "-788810550"} {:id "defn-/ship-sidestep-toward", :kind "defn-", :line 176, :end-line 187, :hash "480281715"} {:id "defn-/start-ship-congestion-random-walk!", :kind "defn-", :line 189, :end-line 192, :hash "-813518478"} {:id "defn-/process-ship-random-walk", :kind "defn-", :line 194, :end-line 206, :hash "-2128338263"} {:id "defn-/nearby-invading-transports", :kind "defn-", :line 208, :end-line 222, :hash "-665750489"} {:id "defn-/land-at-distance?", :kind "defn-", :line 224, :end-line 233, :hash "1571837784"} {:id "defn-/shore-band-score", :kind "defn-", :line 235, :end-line 241, :hash "1418356482"} {:id "defn-/top-random-choice", :kind "defn-", :line 243, :end-line 248, :hash "-1448750480"} {:id "defn-/candidate-neighbors", :kind "defn-", :line 250, :end-line 256, :hash "841271764"} {:id "defn-/patrol-stand-off-step", :kind "defn-", :line 258, :end-line 269, :hash "973145326"} {:id "defn-/patrol-yield-to-transport", :kind "defn-", :line 271, :end-line 298, :hash "1319047782"} {:id "defn-/sea-scout-target", :kind "defn-", :line 300, :end-line 303, :hash "-703141168"} {:id "defn-/major-invasion-target", :kind "defn-", :line 305, :end-line 307, :hash "-323684001"} {:id "defn-/occupied-position?", :kind "defn-", :line 309, :end-line 310, :hash "-891381521"} {:id "defn-/patrol-major-invasion-step", :kind "defn-", :line 312, :end-line 317, :hash "-1636460924"} {:id "defn-/run-patrol-major-invasion", :kind "defn-", :line 319, :end-line 334, :hash "-1013881691"} {:id "defn-/handle-sea-scout-ship-threat", :kind "defn-", :line 336, :end-line 339, :hash "-1034473507"} {:id "defn-/handle-major-invasion-ship-threat", :kind "defn-", :line 341, :end-line 354, :hash "1385953586"} {:id "defn/process-ship-threat", :kind "defn", :line 356, :end-line 374, :hash "-1462215788"}]}
;; clj-mutate-manifest-end
