(ns empire.computer.threat-response.processing-ship
  (:require [empire.state.api :as sa]
            [empire.computer.shared.oscillation :as oscillation]
            [empire.computer.ship.core :as ship-core]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.computer.threat-response.processing-decisions :as decisions]))

(def patrol-yield-radius 4)
(def patrol-max-invasion-distance 10)

(defn- visible-world
  [ctx]
  (or (when-let [read-runtime-state (:read-runtime-state ctx)]
        (read-runtime-state :computer-map))
      (sa/read-state :computer-map)))

(defn ship-threat-action
  [pos ship-type move-target]
  (or (when-let [enemy-pos (ship-core/find-adjacent-enemy-ship pos)]
        (ship-core/attack-enemy pos enemy-pos))
      (when move-target
        (ship-core/move-toward pos move-target))
      (ship-core/explore-sea pos ship-type)))

(defn ship-sidestep-toward
  [pos target]
  (let [world (sa/read-state :computer-map)
        current-distance (grid/distance pos target)
        candidates (->> (ship-core/get-passable-sea-neighbors pos)
                        (filter #(nil? (:contents (get-in world %))))
                        (map (fn [p] {:pos p :distance (grid/distance p target)}))
                        (filter #(<= (:distance %) current-distance))
                        (sort-by (fn [{:keys [distance pos]}] [distance pos])))]
    (when-let [choice (:pos (first candidates))]
      (when (action-resolution/move-unit-to pos choice)
        choice))))

(defn start-ship-congestion-random-walk!
  [ctx pos congestion-random-walk-restore-keys]
  (oscillation/start-random-walk! ctx pos congestion-random-walk-restore-keys))

(defn process-ship-random-walk
  [ctx pos]
  (let [world (visible-world ctx)
        candidates (vec (->> (ship-core/get-passable-sea-neighbors pos)
                             (filter #(nil? (:contents (get-in world %))))))
        final-pos (if-let [target (when (seq candidates) (rand-nth candidates))]
                    (if (action-resolution/move-unit-to pos target) target pos)
                    pos)]
    ((:update-game-map! ctx) update-in (conj final-pos :contents)
     #(-> %
          oscillation/dec-random-walk
          oscillation/maybe-restore))
    (when-let [sync-ai-unit! (:sync-ai-unit! ctx)]
      (sync-ai-unit! final-pos))
    true))

(defn nearby-invading-transports
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

(defn land-at-distance?
  [world [x y] d]
  (boolean
   (some (fn [pos]
           (let [cell (get-in world pos)]
             (and cell (#{:land :city} (:type cell)))))
         (for [dx (range (- d) (inc d))
               dy (range (- d) (inc d))
               :when (= d (max (Math/abs dx) (Math/abs dy)))]
           [(+ x dx) (+ y dy)]))))

(defn shore-band-score
  [world pos]
  (cond
    (land-at-distance? world pos 1) -20
    (land-at-distance? world pos 2) 12
    (land-at-distance? world pos 3) 4
    :else 0))

(defn top-random-choice
  [scored]
  (when (seq scored)
    (let [sorted (sort-by (fn [{:keys [score]}] (- score)) scored)
          topn (vec (take 3 sorted))]
      (:pos (rand-nth topn)))))

(defn candidate-neighbors
  [world pos center]
  (let [passable (ship-core/get-passable-sea-neighbors pos)
        empty-passable (filter #(nil? (:contents (get-in world %))) passable)]
    (if center
      (filter #(<= (grid/distance % center) patrol-max-invasion-distance) empty-passable)
      empty-passable)))

(defn patrol-stand-off-step
  [ctx pos center]
  (let [world (visible-world ctx)
        candidates (candidate-neighbors world pos center)
        scored (for [cand candidates]
                 {:pos cand
                  :score (+ (shore-band-score world cand)
                            (if center (- 12 (min 12 (grid/distance cand center))) 0))})]
    (when-let [target (top-random-choice scored)]
      (when (action-resolution/move-unit-to pos target)
        target))))

(defn patrol-yield-to-transport
  [ctx pos center]
  (let [visible-map (visible-world ctx)
        world ((:current-world ctx))]
    (let [transports (nearby-invading-transports world pos)]
      (when (seq transports)
        (let [candidates (candidate-neighbors visible-map pos center)
              scored (for [cand candidates
                           :let [clearance (apply min (map #(grid/distance cand %) transports))
                                 center-bias (if center
                                               (- 8 (min 8 (grid/distance cand center)))
                                               0)]]
                       {:pos cand
                        :score (+ (* 4 clearance)
                                  (shore-band-score visible-map cand)
                                  (quot center-bias 4))})]
          (when-let [target (:pos (first (sort-by (fn [{:keys [score] :as cand}]
                                                    (let [cand-pos (:pos cand)
                                                          row (first cand-pos)
                                                          col (second cand-pos)]
                                                      [(- score)
                                                       (- row)
                                                       col]))
                                                  scored)))]
            (when (action-resolution/move-unit-to pos target)
              target)))))))

(defn sea-scout-target
  [pos center radius]
  (decisions/sea-scout-move-target
   {:pos pos
    :center center
    :radius radius
    :distance-fn grid/distance}))

(defn major-invasion-target
  [nearest-major-target pos center]
  (decisions/major-invasion-move-target
   {:center center
    :nearest-major-target nearest-major-target
    :pos pos}))

(defn occupied-position? [ctx pos]
  (some? (get-in ((:current-world ctx)) (conj pos :contents))))

(defn patrol-major-invasion-step
  [ctx nearest-major-target current center]
  (or (when-let [enemy-pos (ship-core/find-adjacent-enemy-ship current)]
        (ship-core/attack-enemy current enemy-pos))
      (patrol-stand-off-step ctx current
                             (major-invasion-target nearest-major-target current center))))

(defn- patrol-invasion-done?
  [ctx current steps-left]
  (or (zero? steps-left)
      (not (occupied-position? ctx current))))

(defn run-patrol-major-invasion
  [ctx nearest-major-target pos center]
  (loop [current pos
         steps-left 4]
    (if (patrol-invasion-done? ctx current steps-left)
      current
      (if-let [yield-pos (patrol-yield-to-transport ctx current center)]
        yield-pos
        (recur (or (patrol-major-invasion-step ctx nearest-major-target current center)
                   current)
               (dec steps-left))))))

(defn handle-sea-scout-ship-threat
  [pos ship-type center radius]
  (ship-threat-action pos ship-type (sea-scout-target pos center radius))
  true)

(defn- pursue-major-invasion-target
  [ctx pos target congestion-random-walk-restore-keys]
  (when target
    (or (ship-core/move-toward pos target)
        (ship-sidestep-toward pos target)
        (do (start-ship-congestion-random-walk! ctx pos congestion-random-walk-restore-keys)
            true))))

(defn handle-major-invasion-ship-threat
  [ctx nearest-major-target pos ship-type center congestion-random-walk-restore-keys]
  (if (= :patrol-boat ship-type)
    (run-patrol-major-invasion ctx nearest-major-target pos center)
    (let [target (major-invasion-target nearest-major-target pos center)]
      (or (when-let [enemy-pos (ship-core/find-adjacent-enemy-ship pos)]
            (ship-core/attack-enemy pos enemy-pos))
          (pursue-major-invasion-target ctx pos target congestion-random-walk-restore-keys)
          (ship-core/explore-sea pos ship-type))))
  true)

(defn- ship-threat-mode-input
  [unit]
  {:random-walk? (and (:major-invasion unit) (oscillation/in-random-walk? unit))
   :sea-scout? (= :sea-scout (:threat-mission unit))
   :major-invasion? (:major-invasion unit)})

(defn process-ship-threat
  [ctx pos ship-type unit congestion-random-walk-restore-keys process-ship-random-walk-fn]
  (let [center (or (:threat-center unit) (:major-invasion-target unit))
        radius (:threat-radius unit (:threat-radius ctx))
        nearest-major-target (:nearest-major-target ctx)]
    (case (decisions/ship-threat-mode (ship-threat-mode-input unit))
      :random-walk (process-ship-random-walk-fn ctx pos)
      :sea-scout (handle-sea-scout-ship-threat pos ship-type center radius)
      :major-invasion (handle-major-invasion-ship-threat ctx nearest-major-target pos ship-type center congestion-random-walk-restore-keys)
      false)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-07T18:35:39.376522-05:00", :module-hash "-481543727", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-1909031043"} {:id "def/patrol-yield-radius", :kind "def", :line 9, :end-line 9, :hash "-2055166791"} {:id "def/patrol-max-invasion-distance", :kind "def", :line 10, :end-line 10, :hash "-1594233431"} {:id "defn-/visible-world", :kind "defn-", :line 12, :end-line 16, :hash "1238115483"} {:id "defn/ship-threat-action", :kind "defn", :line 18, :end-line 24, :hash "-1509416151"} {:id "defn/ship-sidestep-toward", :kind "defn", :line 26, :end-line 37, :hash "1309917641"} {:id "defn/start-ship-congestion-random-walk!", :kind "defn", :line 39, :end-line 41, :hash "761797024"} {:id "defn/process-ship-random-walk", :kind "defn", :line 43, :end-line 57, :hash "-1413180417"} {:id "defn/nearby-invading-transports", :kind "defn", :line 59, :end-line 73, :hash "1013786602"} {:id "defn/land-at-distance?", :kind "defn", :line 75, :end-line 84, :hash "-1281486861"} {:id "defn/shore-band-score", :kind "defn", :line 86, :end-line 92, :hash "-1955351171"} {:id "defn/top-random-choice", :kind "defn", :line 94, :end-line 99, :hash "-28880076"} {:id "defn/candidate-neighbors", :kind "defn", :line 101, :end-line 107, :hash "-597944319"} {:id "defn/patrol-stand-off-step", :kind "defn", :line 109, :end-line 119, :hash "1653988852"} {:id "defn/patrol-yield-to-transport", :kind "defn", :line 121, :end-line 146, :hash "-900586249"} {:id "defn/sea-scout-target", :kind "defn", :line 148, :end-line 154, :hash "2135436017"} {:id "defn/major-invasion-target", :kind "defn", :line 156, :end-line 161, :hash "1142149879"} {:id "defn/occupied-position?", :kind "defn", :line 163, :end-line 164, :hash "-1183999174"} {:id "defn/patrol-major-invasion-step", :kind "defn", :line 166, :end-line 171, :hash "1182676753"} {:id "defn/run-patrol-major-invasion", :kind "defn", :line 173, :end-line 186, :hash "-1835871187"} {:id "defn/handle-sea-scout-ship-threat", :kind "defn", :line 188, :end-line 191, :hash "-115651107"} {:id "defn/handle-major-invasion-ship-threat", :kind "defn", :line 193, :end-line 206, :hash "-121391592"} {:id "defn/process-ship-threat", :kind "defn", :line 208, :end-line 221, :hash "-697317044"}]}
;; clj-mutate-manifest-end
