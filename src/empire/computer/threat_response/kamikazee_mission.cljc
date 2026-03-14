(ns empire.computer.threat-response.kamikazee-mission
  (:require [empire.computer.core :as core]
            [empire.computer.fighter-movement :as fm]
            [empire.computer.threat-response.kamikazee-routing :as routing]
            [empire.computer.threat-response.kamikazee-targets :as targets]
            [empire.config.core :as config]))

(def ^:private hunt-trail-length 4)
(def ^:private hunt-refuel-threshold 5)

(defn- fill-fuel!
  [ctx pos site]
  ((:update-game-map! ctx) assoc-in (conj pos :contents :fuel) config/fighter-fuel)
  pos)

(defn- update-kamikazee-unit!
  [ctx pos f]
  ((:update-game-map! ctx) update-in (conj pos :contents) f))

(defn- player-army-at?
  [world pos]
  (let [unit (get-in world (conj pos :contents))]
    (and unit
         (= :player (:owner unit))
         (= :army (:type unit)))))

(defn- adjacent-player-army
  [world pos]
  (first (filter #(player-army-at? world %) (core/get-neighbors pos))))

(defn- move-toward!
  [pos target]
  (when-let [hop (fm/hop-over-friendly pos target)]
    (fm/execute-hop pos hop)))

(defn- non-backtracking-step
  [ctx pos target min-target-distance]
  (let [world ((:current-world ctx))
        unit (get-in world (conj pos :contents))
        trail (set (:kamikazee-trail unit))
        current-distance (if target (core/distance pos target) 0)
        far-enough? (fn [cand]
                      (or (nil? target)
                          (<= min-target-distance (core/distance cand target))))
        candidates (->> (fm/get-passable-neighbors pos)
                        (remove fm/occupied?)
                        (remove trail)
                        (filter far-enough?)
                        (sort-by (fn [cand]
                                   [(if target (core/distance cand target) current-distance)
                                    cand])))
        choices (vec (if (seq candidates)
                       candidates
                       (->> (fm/get-passable-neighbors pos)
                            (remove fm/occupied?)
                            (filter far-enough?)
                            vec)))]
    (when-let [choice (when (seq choices) (rand-nth choices))]
      (when (core/move-unit-to pos choice)
        (when (fm/consume-fighter-fuel choice)
          ((:update-game-map! ctx) assoc-in (conj choice :contents :kamikazee-trail)
           (vec (take-last hunt-trail-length (conj (:kamikazee-trail unit []) pos))))
          {:pos choice :steps-used 1})))))

(defn- enter-hunt!
  [ctx pos unit]
  (let [next-unit (-> unit
                      (assoc :kamikazee-stage :hunt)
                      (dissoc :kamikazee-wait-site
                              :kamikazee-hunt-resume-pos))]
    (update-kamikazee-unit! ctx pos (constantly next-unit))
    next-unit))

(defn- close-enough-to-hunt?
  [pos target]
  (and target
       (<= (core/distance pos target) 1)))

(defn- choose-hunt-refuel-site
  [pos fuel refuel-sites]
  (first (sort-by (fn [site] [(routing/site-distance pos site) site])
                  (filter #(<= (routing/site-distance pos %) fuel) refuel-sites))))

(defn- start-hunt-refuel!
  [ctx pos unit site]
  (let [next-unit (assoc unit
                         :kamikazee-stage :refuel
                         :kamikazee-wait-site site
                         :kamikazee-hunt-resume-pos pos)]
    (update-kamikazee-unit! ctx pos (constantly next-unit))
    next-unit))

(defn- attack-adjacent-player-army
  [pos world]
  (when-let [enemy (adjacent-player-army world pos)]
    (when-let [new-pos (fm/attack-enemy pos enemy)]
      (when (fm/consume-fighter-fuel new-pos)
        {:pos new-pos :steps-used 1}))))

(declare process-kamikazee-fighter)

(defn- process-hunt-step
  [ctx pos unit current-goal refuel-sites]
  (let [world ((:current-world ctx))
        stage (:kamikazee-stage unit)
        wait-site (:kamikazee-wait-site unit)
        resume-pos (:kamikazee-hunt-resume-pos unit)
        fuel (:fuel unit config/fighter-fuel)]
    (cond
      (and (= :refuel stage) wait-site)
      (if (routing/at-site? world pos wait-site)
        (do
          (fill-fuel! ctx pos wait-site)
          (update-kamikazee-unit! ctx pos
                                  #(-> %
                                       (assoc :kamikazee-stage :return)
                                       (dissoc :kamikazee-wait-site)))
          {:pos pos :steps-used 1})
        (move-toward! pos wait-site))

      (and (= :return stage) resume-pos)
      (if (= pos resume-pos)
        (process-kamikazee-fighter ctx pos (enter-hunt! ctx pos unit))
        (move-toward! pos resume-pos))

      :else
      (or (attack-adjacent-player-army pos world)
          (when (<= fuel hunt-refuel-threshold)
            (when-let [site (choose-hunt-refuel-site pos fuel refuel-sites)]
              (process-kamikazee-fighter ctx pos (start-hunt-refuel! ctx pos unit site))))
          (non-backtracking-step ctx pos current-goal 1)))))

(defn process-kamikazee-fighter
  [ctx pos unit]
  (let [world ((:current-world ctx))
        state ((:load-major-invasion-state ctx))
        round-number (targets/current-round ctx)
        army-target (targets/choose-army-target state round-number world)
        major-target (targets/choose-major-target state world pos)
        current-goal (or army-target major-target)
        route (:kamikazee-route unit)
        next-site (first route)
        refuel-sites (routing/available-refueling-sites)]
    (cond
      (#{:hunt :refuel :return} (:kamikazee-stage unit))
      (process-hunt-step ctx pos unit current-goal refuel-sites)

      (and next-site (routing/at-site? world pos next-site))
      (do
        (fill-fuel! ctx pos next-site)
        (update-kamikazee-unit! ctx pos
         #(-> %
              (assoc :kamikazee-terminal-site (or (:kamikazee-terminal-site %) next-site))
              (assoc :kamikazee-stage (if (= 1 (count route)) :hunt :route))
              (assoc :kamikazee-route (vec (rest route)))
              (dissoc :kamikazee-wait-site
                      :kamikazee-hunt-resume-pos)))
        {:pos pos :steps-used 1})

      next-site
      (move-toward! pos next-site)

      (close-enough-to-hunt? pos current-goal)
      (process-kamikazee-fighter ctx pos (enter-hunt! ctx pos unit))

      current-goal
      (or (move-toward! pos current-goal)
          (non-backtracking-step ctx pos current-goal 0))

      :else
      (non-backtracking-step ctx pos nil 0))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-14T08:36:44.110152-05:00", :module-hash "362437439", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "1755399322"} {:id "def/hunt-trail-length", :kind "def", :line 8, :end-line 8, :hash "608106012"} {:id "defn-/refuel-at-site!", :kind "defn-", :line 10, :end-line 14, :hash "2037084067"} {:id "defn-/move-toward!", :kind "defn-", :line 16, :end-line 19, :hash "178636244"} {:id "defn-/non-backtracking-step", :kind "defn-", :line 21, :end-line 43, :hash "-1495100354"} {:id "defn/process-kamikazee-fighter", :kind "defn", :line 45, :end-line 90, :hash "250974694"}]}
;; clj-mutate-manifest-end
