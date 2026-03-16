(ns empire.computer.threat-response.kamikazee-mission
  (:require [empire.computer.core :as core]
            [empire.computer.fighter-movement :as fm]
            [empire.computer.threat-response.kamikazee-launch-decisions :as launch-decisions]
            [empire.computer.threat-response.kamikazee-mission-decisions :as decisions]
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
        action (decisions/hunt-step-result moved-unit
                                           moved-pos
                                           pos
                                           hunt-trail-length
                                           config/fighter-fuel)]
    (when (kamikazee-writeable-unit? ctx moved-pos)
      (case (:action action)
        :destroy
        (do
          ((:update-game-map! ctx) update-in moved-pos dissoc :contents)
          {:pos moved-pos :steps-used 1})
        :update-unit
        (do
          ((:update-game-map! ctx) update-in (conj moved-pos :contents)
           #(merge % (:unit-updates action)))
          {:pos moved-pos :steps-used 1})
        nil))))

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
  (let [site (when (<= fuel hunt-refuel-threshold)
               (choose-hunt-refuel-site pos fuel refuel-sites))]
    (case (decisions/hunt-stage-action {:stage (:kamikazee-stage unit)
                                        :has-wait-site? (boolean (:kamikazee-wait-site unit))
                                        :has-resume-pos? (boolean (:kamikazee-hunt-resume-pos unit))
                                        :fuel fuel
                                        :refuel-threshold hunt-refuel-threshold
                                        :has-adjacent-player-army? (boolean (adjacent-player-army ((:current-world ctx)) pos))
                                        :has-reachable-refuel-site? (boolean site)})
      :attack (attack-adjacent-player-army pos ((:current-world ctx)))
      :start-refuel (process-kamikazee-fighter ctx pos (start-hunt-refuel! ctx pos unit site))
      :walk (non-backtracking-step ctx pos current-goal 1)
      nil)))

(defn- remove-airport-kamikazee!
  [ctx city-pos]
  ((:update-game-map! ctx) update-in city-pos
   #(-> %
        (update :fighter-count decisions/dec-count)
        (update :awake-fighters decisions/dec-count)
        (update :kamikazee-fighter-count decisions/dec-count)
        (update :awake-kamikazee-fighters decisions/dec-count))))

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

(defn launch-kamikazee-from-airport!
  [ctx city-pos]
  (let [world ((:current-world ctx))
        cell (get-in world city-pos)
        state ((:load-major-invasion-state ctx))]
    (when (and (computer-city-site? world city-pos)
               (launch-decisions/airport-kamikazee-ready? cell)
               (routing/city-has-launch-capacity? world city-pos route-city-launch-buffer))
      (let [targets (targets/ordered-army-target-positions state
                                                           (targets/current-round ctx)
                                                           world)
            plan (routing/plan-route state world city-pos config/fighter-fuel)
            major-target (targets/choose-major-target state world city-pos)
            next-route-city (when (routing/city-site? world (first (:route plan)))
                              (first (:route plan)))
            launch-pos (open-launch-position world city-pos (:route plan) major-target)
            action (launch-decisions/launch-decision
                    {:current-city-capacity? (routing/city-has-launch-capacity? world city-pos route-city-launch-buffer)
                     :next-route-city-capacity? (or (nil? next-route-city)
                                                    (routing/city-has-launch-capacity? world next-route-city route-city-launch-buffer))
                     :next-route-city next-route-city
                     :launch-pos launch-pos
                     :major-target major-target
                     :targets targets
                     :plan plan
                     :fighter-fuel config/fighter-fuel})]
        (when (and action
                   (or (nil? next-route-city)
                       (routing/city-has-launch-capacity? world next-route-city route-city-launch-buffer)))
          (remove-airport-kamikazee! ctx city-pos)
          ((:update-game-map! ctx) assoc-in (conj (:launch-pos action) :contents) (:fighter action))
          (:launch-pos action))))))

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
  (let [{:keys [clear-keys] :as updates} (decisions/finish-route-node-update route
                                                                             next-site
                                                                             (:kamikazee-terminal-site (get-in ((:current-world ctx)) (conj pos :contents))))]
    (fill-fuel! ctx pos next-site)
    (update-kamikazee-unit! ctx pos
     (fn [current]
       (apply dissoc
              (merge current (dissoc updates :clear-keys))
              clear-keys)))
    )
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
  (case (decisions/route-stage-action {:adjacent-route-city? (adjacent-route-city? world pos next-site)
                                       :at-route-site? (and next-site (routing/at-site? world pos next-site))
                                       :has-next-site? (boolean next-site)
                                       :close-enough-to-goal? (decisions/close-enough-to-hunt?
                                                               (when current-goal (core/distance pos current-goal)))
                                       :has-goal? (boolean current-goal)})
    :land-at-city
    (do
      (fm/land-at-city pos next-site)
      nil)

    :finish-route-node (finish-route-node! ctx pos route next-site)
    :move-to-next-site (move-toward! pos next-site)
    :enter-hunt (process-kamikazee-fighter ctx pos (enter-hunt! ctx pos unit))
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
;; {:version 1, :tested-at "2026-03-16T08:21:47.225746-05:00", :module-hash "-463187672", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 10, :hash "-1465756470"} {:id "def/hunt-trail-length", :kind "def", :line 12, :end-line 12, :hash "608106012"} {:id "def/hunt-refuel-threshold", :kind "def", :line 13, :end-line 13, :hash "-1625190623"} {:id "def/route-city-launch-buffer", :kind "def", :line 14, :end-line 14, :hash "1942264726"} {:id "defn-/kamikazee-writeable-unit?", :kind "defn-", :line 16, :end-line 21, :hash "1900221379"} {:id "defn-/fill-fuel!", :kind "defn-", :line 23, :end-line 27, :hash "-91382987"} {:id "defn-/update-kamikazee-unit!", :kind "defn-", :line 29, :end-line 32, :hash "-315117894"} {:id "form/7/declare", :kind "declare", :line 34, :end-line 34, :hash "-1144547704"} {:id "defn-/player-army-at?", :kind "defn-", :line 36, :end-line 41, :hash "-1261804669"} {:id "defn-/computer-city-site?", :kind "defn-", :line 43, :end-line 46, :hash "-280839162"} {:id "defn-/adjacent-player-army", :kind "defn-", :line 48, :end-line 50, :hash "1436602471"} {:id "defn-/move-toward!", :kind "defn-", :line 52, :end-line 55, :hash "178636244"} {:id "defn-/backtracking-candidates", :kind "defn-", :line 57, :end-line 75, :hash "-1930704525"} {:id "defn-/apply-hunt-step", :kind "defn-", :line 77, :end-line 96, :hash "-1458732556"} {:id "defn-/non-backtracking-step", :kind "defn-", :line 98, :end-line 107, :hash "-2110455071"} {:id "defn-/enter-hunt!", :kind "defn-", :line 109, :end-line 116, :hash "638879639"} {:id "defn-/choose-hunt-refuel-site", :kind "defn-", :line 118, :end-line 121, :hash "-813573447"} {:id "defn-/start-hunt-refuel!", :kind "defn-", :line 123, :end-line 130, :hash "1161236971"} {:id "defn-/attack-adjacent-player-army", :kind "defn-", :line 132, :end-line 141, :hash "-1123602667"} {:id "defn-/process-refuel-stage", :kind "defn-", :line 143, :end-line 153, :hash "-590686010"} {:id "defn-/process-return-stage", :kind "defn-", :line 155, :end-line 159, :hash "1620256583"} {:id "defn-/process-active-hunt", :kind "defn-", :line 161, :end-line 175, :hash "-1462288368"} {:id "defn-/remove-airport-kamikazee!", :kind "defn-", :line 177, :end-line 184, :hash "-1081261902"} {:id "defn-/open-launch-position", :kind "defn-", :line 186, :end-line 195, :hash "50118627"} {:id "defn/launch-kamikazee-from-airport!", :kind "defn", :line 197, :end-line 228, :hash "2069853580"} {:id "defn-/process-hunt-step", :kind "defn-", :line 230, :end-line 244, :hash "1286237139"} {:id "defn-/finish-route-node!", :kind "defn-", :line 246, :end-line 258, :hash "-36865006"} {:id "defn-/adjacent-route-city?", :kind "defn-", :line 260, :end-line 264, :hash "1971017307"} {:id "defn-/move-from-route", :kind "defn-", :line 266, :end-line 271, :hash "1250423462"} {:id "defn-/process-route-stage", :kind "defn-", :line 273, :end-line 289, :hash "-1237850103"} {:id "defn/process-kamikazee-fighter", :kind "defn", :line 291, :end-line 307, :hash "1503701697"}]}
;; clj-mutate-manifest-end
