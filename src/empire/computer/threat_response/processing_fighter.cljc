(ns empire.computer.threat-response.processing-fighter
  (:require [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.fighter.movement :as fm]
            [empire.computer.shared.oscillation :as oscillation]
            [empire.computer.threat-response.processing-decisions :as decisions]
            [empire.computer.shared.grid :as grid]
            [empire.config.core :as config]))

(defn can-stay-in-refuel-range?
  [pos remaining-fuel fighter-refuel-safety-buffer]
  (when (pos? remaining-fuel)
    (when-let [site (fm/find-nearest-refueling-site pos)]
      (>= remaining-fuel (+ (fm/distance-to pos site) fighter-refuel-safety-buffer)))))

(defn move-hop-consume
  [pos target fuel fighter-refuel-safety-buffer]
  (when-let [hop (fm/hop-over-friendly pos target)]
    (let [remaining-fuel (- fuel (:hops hop))]
      (when (can-stay-in-refuel-range? (:dest hop) remaining-fuel fighter-refuel-safety-buffer)
        (when-let [{:keys [pos hops]} (fm/execute-hop pos hop)]
          (when (fm/consume-fighter-fuel pos)
            {:pos pos :steps-used hops}))))))

(defn attack-threat-step
  [pos enemy]
  (when-let [new-pos (fm/attack-enemy pos enemy)]
    (when (fm/consume-fighter-fuel new-pos)
      {:pos new-pos :steps-used 1})))

(defn refuel-at-adjacent-site
  [ctx pos site]
  (if (= :city (:type (get-in ((:current-world ctx)) site)))
    (do (fm/land-at-city pos site) nil)
    (do ((:update-game-map! ctx) assoc-in (conj pos :contents :fuel) config/fighter-fuel)
        (when-let [sync-ai-unit! (:sync-ai-unit! ctx)]
          (sync-ai-unit! pos))
        {:pos pos :steps-used 1})))

(defn refuel-threat-step
  [ctx pos fighter-refuel-safety-buffer]
  (let [fuel (:fuel (get-in ((:current-world ctx)) (conj pos :contents)) config/fighter-fuel)]
    (when-let [site (fm/find-nearest-refueling-site pos)]
      (if (<= (fm/distance-to pos site) 1)
        (refuel-at-adjacent-site ctx pos site)
        (move-hop-consume pos site fuel fighter-refuel-safety-buffer)))))

(defn out-of-threat-radius?
  [pos center radius]
  (and center (> (grid/distance pos center) radius)))

(defn patrol-threat-step
  [pos center radius fuel fighter-refuel-safety-buffer]
  (when-let [{:keys [pos hops]} (fm/do-patrol pos)]
    (let [remaining-fuel (- fuel hops)]
      (when (can-stay-in-refuel-range? pos remaining-fuel fighter-refuel-safety-buffer)
        (when (fm/consume-fighter-fuel pos)
          (if (out-of-threat-radius? pos center radius)
            (move-hop-consume pos center remaining-fuel fighter-refuel-safety-buffer)
            {:pos pos :steps-used hops}))))))

(defn- sidestep-fuel-result
  [target]
  (when (fm/consume-fighter-fuel target)
    {:pos target :steps-used 1}))

(defn- consume-sidestep-move
  [pos target]
  (when (action-resolution/move-unit-to pos target)
    (sidestep-fuel-result target)))

(defn fighter-sidestep-consume
  [pos center]
  (let [current-distance (grid/distance pos center)
        passable (fm/get-passable-neighbors pos)
        candidates (->> passable
                        (remove fm/occupied?)
                        (map (fn [p] {:pos p :distance (grid/distance p center)}))
                        (filter #(<= (:distance %) current-distance))
                        (sort-by (fn [{:keys [distance pos]}] [distance pos])))]
    (when-let [target (:pos (first candidates))]
      (consume-sidestep-move pos target))))

(defn start-fighter-congestion-random-walk!
  [ctx pos congestion-random-walk-restore-keys]
  (oscillation/start-random-walk! ctx pos congestion-random-walk-restore-keys))

(defn fighter-random-walk-step
  [pos]
  (let [candidates (vec (remove fm/occupied? (fm/get-passable-neighbors pos)))]
    (if-let [target (when (seq candidates) (rand-nth candidates))]
      (if (action-resolution/move-unit-to pos target)
        (when (fm/consume-fighter-fuel target)
          target)
        pos)
      pos)))

(defn update-fighter-random-walk!
  [ctx final-pos]
  (when (get-in ((:current-world ctx)) (conj final-pos :contents))
    ((:update-game-map! ctx) update-in (conj final-pos :contents)
     #(-> %
          oscillation/dec-random-walk
          oscillation/maybe-restore))
    (when-let [sync-ai-unit! (:sync-ai-unit! ctx)]
      (sync-ai-unit! final-pos))))

(defn process-fighter-random-walk-round
  [ctx pos]
  (let [final-pos (or (fighter-random-walk-step pos) pos)]
    (update-fighter-random-walk! ctx final-pos))
  true)

(defn outside-radius-step
  [ctx pos center fuel unit fighter-refuel-safety-buffer congestion-random-walk-restore-keys]
  (or (move-hop-consume pos center fuel fighter-refuel-safety-buffer)
      (when center (fighter-sidestep-consume pos center))
      (when (:major-invasion unit)
        (start-fighter-congestion-random-walk! ctx pos congestion-random-walk-restore-keys)
        nil)))

(defn next-fighter-threat-state
  [ctx current remaining fighter-step-fn]
  (decisions/next-threat-state
   remaining
   (fighter-step-fn ctx current (get-in ((:current-world ctx)) (conj current :contents)))))

(defn fighter-step-threat
  [ctx pos unit fighter-refuel-safety-buffer congestion-random-walk-restore-keys]
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
      :refuel (refuel-threat-step ctx pos fighter-refuel-safety-buffer)
      :outside-radius (outside-radius-step ctx pos center fuel unit fighter-refuel-safety-buffer congestion-random-walk-restore-keys)
      (patrol-threat-step pos center radius fuel fighter-refuel-safety-buffer))))

(defn run-fighter-threat-round
  [ctx pos fighter-step-fn]
  (loop [current pos
         remaining fm/fighter-speed]
    (when-let [{:keys [pos remaining]} (next-fighter-threat-state ctx current remaining fighter-step-fn)]
      (recur pos remaining))))

(defn run-active-fighter-threat
  [ctx pos unit run-fighter-threat-round-fn]
  (case (decisions/fighter-threat-round-mode
         {:random-walk? (oscillation/in-random-walk? unit)})
    :random-walk
    (process-fighter-random-walk-round ctx pos)
    (run-fighter-threat-round-fn ctx pos)))

(defn process-fighter-threat
  [ctx pos unit run-fighter-threat-round-fn]
  (when (decisions/fighter-threat-active? unit)
    (run-active-fighter-threat ctx pos unit run-fighter-threat-round-fn)
    true))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:19:36.804605-05:00", :module-hash "-1445033361", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "255705640"} {:id "defn/can-stay-in-refuel-range?", :kind "defn", :line 9, :end-line nil, :hash "146059271"} {:id "defn/move-hop-consume", :kind "defn", :line 15, :end-line nil, :hash "-143820027"} {:id "defn/attack-threat-step", :kind "defn", :line 24, :end-line nil, :hash "2076924029"} {:id "defn/refuel-at-adjacent-site", :kind "defn", :line 30, :end-line nil, :hash "1692230419"} {:id "defn/refuel-threat-step", :kind "defn", :line 39, :end-line nil, :hash "-68265831"} {:id "defn/out-of-threat-radius?", :kind "defn", :line 47, :end-line nil, :hash "861618178"} {:id "defn/patrol-threat-step", :kind "defn", :line 51, :end-line nil, :hash "605278432"} {:id "defn-/sidestep-fuel-result", :kind "defn-", :line 61, :end-line nil, :hash "1793193918"} {:id "defn-/consume-sidestep-move", :kind "defn-", :line 66, :end-line nil, :hash "-316943091"} {:id "defn/fighter-sidestep-consume", :kind "defn", :line 71, :end-line nil, :hash "-1691942966"} {:id "defn/start-fighter-congestion-random-walk!", :kind "defn", :line 83, :end-line nil, :hash "-2033184560"} {:id "defn/fighter-random-walk-step", :kind "defn", :line 87, :end-line nil, :hash "1525166631"} {:id "defn/update-fighter-random-walk!", :kind "defn", :line 97, :end-line nil, :hash "1419975849"} {:id "defn/process-fighter-random-walk-round", :kind "defn", :line 107, :end-line nil, :hash "-1302042158"} {:id "defn/outside-radius-step", :kind "defn", :line 113, :end-line nil, :hash "-1571483941"} {:id "defn/next-fighter-threat-state", :kind "defn", :line 121, :end-line nil, :hash "-1505513143"} {:id "defn/fighter-step-threat", :kind "defn", :line 127, :end-line nil, :hash "-1022707219"} {:id "defn/run-fighter-threat-round", :kind "defn", :line 146, :end-line nil, :hash "-1908373204"} {:id "defn/run-active-fighter-threat", :kind "defn", :line 153, :end-line nil, :hash "1580350321"} {:id "defn/process-fighter-threat", :kind "defn", :line 161, :end-line nil, :hash "-660793342"}]}
;; clj-mutate-manifest-end
