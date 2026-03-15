(ns empire.computer.threat-response.kamikazee-mission
  (:require [empire.computer.core :as core]
            [empire.computer.fighter-movement :as fm]
            [empire.computer.threat-response.kamikazee-routing :as routing]
            [empire.computer.threat-response.kamikazee-targets :as targets]
            [empire.config.core :as config]
            [empire.config.domain.model.containers :as containers]
            [empire.state.api :as sa]))

(def ^:private hunt-trail-length 4)
(def ^:private hunt-refuel-threshold 5)
(def ^:private route-city-launch-buffer 2)

(defn- kamikazee-writeable-unit?
  [ctx pos]
  (let [unit (get-in ((:current-world ctx)) (conj pos :contents))]
    (and (= :fighter (:type unit))
         (= :computer (:owner unit))
         (:kamikazee unit))))

(defn- fill-fuel!
  [ctx pos site]
  (if (kamikazee-writeable-unit? ctx pos)
    ((:update-game-map! ctx) assoc-in (conj pos :contents :fuel) config/fighter-fuel))
  pos)

(defn- update-kamikazee-unit!
  [ctx pos f]
  (when (kamikazee-writeable-unit? ctx pos)
    ((:update-game-map! ctx) update-in (conj pos :contents) f)))

(declare process-kamikazee-fighter)

(defn- player-army-at?
  [world pos]
  (let [unit (get-in world (conj pos :contents))]
    (and unit
         (= :player (:owner unit))
         (= :army (:type unit)))))

(defn- computer-city-site?
  [world pos]
  (and (= :city (get-in world (conj pos :type)))
       (= :computer (get-in world (conj pos :city-status)))))

(defn- adjacent-player-army
  [world pos]
  (first (filter #(player-army-at? world %) (core/get-neighbors pos))))

(defn- dec-count
  [n]
  (max 0 (dec (or n 0))))

(defn- move-toward!
  [pos target]
  (when-let [hop (fm/hop-over-friendly pos target)]
    (fm/execute-hop pos hop)))

(defn- backtracking-candidates
  [pos target min-target-distance trail]
  (let [current-distance (if target (core/distance pos target) 0)
        far-enough? (fn [cand]
                      (or (nil? target)
                          (<= min-target-distance (core/distance cand target))))
        passable (fm/get-passable-neighbors pos)
        sorted-candidates (fn [cells]
                            (sort-by (fn [cand]
                                       [(if target (core/distance cand target) current-distance)
                                        cand])
                                     cells))]
    (or (seq (sorted-candidates (->> passable
                                     (remove fm/occupied?)
                                     (remove trail)
                                     (filter far-enough?))))
        (seq (sorted-candidates (->> passable
                                     (remove fm/occupied?)
                                     (filter far-enough?)))))))

(defn- apply-hunt-step
  [ctx pos moved-pos unit]
  (let [moved-unit (get-in ((:current-world ctx)) (conj moved-pos :contents))
        next-fuel (dec (:fuel moved-unit config/fighter-fuel))]
    (when (kamikazee-writeable-unit? ctx moved-pos)
      (if (<= next-fuel 0)
        (do
          ((:update-game-map! ctx) update-in moved-pos dissoc :contents)
          {:pos moved-pos :steps-used 1})
        (do
          ((:update-game-map! ctx) update-in (conj moved-pos :contents)
           #(assoc %
                   :fuel next-fuel
                   :kamikazee-trail
                   (vec (take-last hunt-trail-length (conj (:kamikazee-trail unit []) pos)))))
          {:pos moved-pos :steps-used 1})))))

(defn- non-backtracking-step
  [ctx pos target min-target-distance]
  (when (kamikazee-writeable-unit? ctx pos)
    (let [world ((:current-world ctx))
          unit (get-in world (conj pos :contents))
          trail (set (:kamikazee-trail unit))
          choices (vec (backtracking-candidates pos target min-target-distance trail))]
      (when-let [choice (when (seq choices) (rand-nth choices))]
        (when-let [moved-pos (core/move-unit-to pos choice)]
          (apply-hunt-step ctx pos moved-pos unit))))))

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
    (if-let [new-pos (fm/attack-enemy pos enemy)]
      (do
        (fm/consume-fighter-fuel new-pos)
        {:pos new-pos :steps-used 1})
      ;; A combat loss still consumes the fighter's turn. Do not fall through
      ;; into hunt movement after the attacker has already been destroyed.
      {:pos pos :steps-used 1})))

(defn- process-refuel-stage
  [ctx pos wait-site]
  (if (routing/at-site? ((:current-world ctx)) pos wait-site)
    (do
      (fill-fuel! ctx pos wait-site)
      (update-kamikazee-unit! ctx pos
                              #(-> %
                                   (assoc :kamikazee-stage :return)
                                   (dissoc :kamikazee-wait-site)))
      {:pos pos :steps-used 1})
    (move-toward! pos wait-site)))

(defn- process-return-stage
  [ctx pos unit resume-pos]
  (if (= pos resume-pos)
    (process-kamikazee-fighter ctx pos (enter-hunt! ctx pos unit))
    (move-toward! pos resume-pos)))

(defn- process-active-hunt
  [ctx pos unit current-goal refuel-sites fuel]
  (or (attack-adjacent-player-army pos ((:current-world ctx)))
      (when (<= fuel hunt-refuel-threshold)
        (when-let [site (choose-hunt-refuel-site pos fuel refuel-sites)]
          (process-kamikazee-fighter ctx pos (start-hunt-refuel! ctx pos unit site))))
      (non-backtracking-step ctx pos current-goal 1)))

(defn- airport-kamikazee-ready?
  [cell]
  (pos? (:awake-kamikazee-fighters cell 0)))

(defn- remove-airport-kamikazee!
  [ctx city-pos]
  ((:update-game-map! ctx) update-in city-pos
   #(-> %
        (update :fighter-count dec-count)
        (update :awake-fighters dec-count)
        (update :kamikazee-fighter-count dec-count)
        (update :awake-kamikazee-fighters dec-count))))

(defn- open-launch-position
  [world city-pos route major-target]
  (some (fn [candidate]
          (let [candidate-cell (get-in world candidate)]
            (when (and candidate-cell (nil? (:contents candidate-cell)))
              candidate)))
        (containers/launch-steps-toward city-pos
                                        (or (first route)
                                            major-target
                                            city-pos))))

(defn- build-launched-fighter
  [major-target targets plan]
  {:type :fighter
   :owner :computer
   :mode :awake
   :hits 1
   :fuel config/fighter-fuel
   :major-invasion true
   :kamikazee true
   :major-invasion-target major-target
   :kamikazee-targets targets
   :kamikazee-route (:route plan)
   :kamikazee-terminal-site (:terminal-site plan)
   :kamikazee-stage (if (seq (:route plan)) :route :hunt)})

(defn launch-kamikazee-from-airport!
  [ctx city-pos]
  (let [world ((:current-world ctx))
        cell (get-in world city-pos)
        state ((:load-major-invasion-state ctx))]
    (when (and (computer-city-site? world city-pos)
               (airport-kamikazee-ready? cell)
               (routing/city-has-launch-capacity? world city-pos route-city-launch-buffer))
      (let [targets (targets/ordered-army-target-positions state
                                                           (targets/current-round ctx)
                                                           world)
            plan (routing/plan-route state world city-pos config/fighter-fuel)
            major-target (targets/choose-major-target state world city-pos)
            next-route-city (when (routing/city-site? world (first (:route plan)))
                              (first (:route plan)))
            launch-pos (open-launch-position world city-pos (:route plan) major-target)
            launched-fighter (build-launched-fighter major-target targets plan)]
        (when (and launch-pos
                   (or (nil? next-route-city)
                       (routing/city-has-launch-capacity? world
                                                          next-route-city
                                                          route-city-launch-buffer)))
          (remove-airport-kamikazee! ctx city-pos)
          ((:update-game-map! ctx) assoc-in (conj launch-pos :contents) launched-fighter)
          launch-pos)))))

(defn- process-hunt-step
  [ctx pos unit current-goal refuel-sites]
  (let [stage (:kamikazee-stage unit)
        wait-site (:kamikazee-wait-site unit)
        resume-pos (:kamikazee-hunt-resume-pos unit)
        fuel (:fuel unit config/fighter-fuel)]
    (cond
      (and (= :refuel stage) wait-site)
      (process-refuel-stage ctx pos wait-site)

      (and (= :return stage) resume-pos)
      (process-return-stage ctx pos unit resume-pos)

      :else
      (process-active-hunt ctx pos unit current-goal refuel-sites fuel))))

(defn- finish-route-node!
  [ctx pos route next-site]
  (let [next-stage (if (= 1 (count route)) :hunt :route)
        remaining-route (vec (rest route))]
    (fill-fuel! ctx pos next-site)
    (update-kamikazee-unit! ctx pos
     #(-> %
          (assoc :kamikazee-terminal-site (or (:kamikazee-terminal-site %) next-site))
          (assoc :kamikazee-stage next-stage)
          (assoc :kamikazee-route remaining-route)
          (dissoc :kamikazee-wait-site
                  :kamikazee-hunt-resume-pos))))
  {:pos pos :steps-used 1})

(defn- adjacent-route-city?
  [world pos next-site]
  (and next-site
       (computer-city-site? world next-site)
       (<= (routing/site-distance pos next-site) 1)))

(defn- move-from-route
  [ctx pos current-goal]
  (if current-goal
    (or (move-toward! pos current-goal)
        (non-backtracking-step ctx pos current-goal 0))
    (non-backtracking-step ctx pos nil 0)))

(defn- process-route-stage
  [ctx pos world unit route next-site current-goal]
  (cond
    (adjacent-route-city? world pos next-site)
    (do
      (fm/land-at-city pos next-site)
      nil)

    (and next-site (routing/at-site? world pos next-site))
    (finish-route-node! ctx pos route next-site)

    next-site
    (move-toward! pos next-site)

    (close-enough-to-hunt? pos current-goal)
    (process-kamikazee-fighter ctx pos (enter-hunt! ctx pos unit))

    :else
    (move-from-route ctx pos current-goal)))

(defn process-kamikazee-fighter
  [ctx pos unit]
  (when unit
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
        :else
        (process-route-stage ctx pos world unit route next-site current-goal)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-14T08:36:44.110152-05:00", :module-hash "362437439", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "1755399322"} {:id "def/hunt-trail-length", :kind "def", :line 8, :end-line 8, :hash "608106012"} {:id "defn-/refuel-at-site!", :kind "defn-", :line 10, :end-line 14, :hash "2037084067"} {:id "defn-/move-toward!", :kind "defn-", :line 16, :end-line 19, :hash "178636244"} {:id "defn-/non-backtracking-step", :kind "defn-", :line 21, :end-line 43, :hash "-1495100354"} {:id "defn/process-kamikazee-fighter", :kind "defn", :line 45, :end-line 90, :hash "250974694"}]}
;; clj-mutate-manifest-end
