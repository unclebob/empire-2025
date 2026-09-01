(ns empire.computer.threat-response.kamikazee-mission
  (:require [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.fighter.movement :as fm]
            [empire.computer.threat-response.kamikazee-launch-decisions :as launch-decisions]
            [empire.computer.threat-response.kamikazee-mission-decisions :as decisions]
            [empire.computer.threat-response.kamikazee-routing :as routing]
            [empire.computer.threat-response.kamikazee-targets :as targets]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.launch :as launch]
            [empire.game-mechanics.visibility :as visibility]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.world-query :as world-query]
            [empire.state.api :as sa]))

(def ^:private hunt-trail-length 4)
(def ^:private hunt-refuel-threshold 5)
(def ^:private route-city-launch-buffer 2)

(defn- sync-kamikazee-cell!
  [pos]
  (visibility/sync-ai-cell-to-computer-map! pos))

(defn- authoritative-kamikazee-unit?
  [pos]
  (let [unit (visibility/authoritative-computer-unit-at pos)]
    (and (= :fighter (:type unit))
         (:kamikazee unit))))

(defn- kamikazee-writeable-unit?
  [ctx pos]
  (sync-kamikazee-cell! pos)
  (let [unit (get-in ((:current-world ctx)) (conj pos :contents))]
    (and (authoritative-kamikazee-unit? pos)
         (= :fighter (:type unit))
         (= :computer (:owner unit))
         (:kamikazee unit))))

(defn- fill-fuel!
  [ctx pos site]
  (when (kamikazee-writeable-unit? ctx pos)
    ((:update-game-map! ctx) assoc-in (conj pos :contents :fuel) config/fighter-fuel)
    (sync-kamikazee-cell! pos))
  pos)

(defn- update-kamikazee-unit!
  [ctx pos f]
  (when (kamikazee-writeable-unit? ctx pos)
    ((:update-game-map! ctx) update-in (conj pos :contents) f)
    (sync-kamikazee-cell! pos)))

(declare process-kamikazee-fighter)

(defn- computer-city-site?
  [world pos]
  (and (= :city (get-in world (conj pos :type)))
       (= :computer (get-in world (conj pos :city-status)))))

(defn- adjacent-player-army
  [world pos]
  (first (filter #(targets/player-army? world %) (world-query/get-neighbors pos))))

(defn- move-toward!
  [pos target]
  (when-let [hop (fm/hop-over-friendly pos target)]
    (fm/execute-hop pos hop)))

(defn- backtracking-candidates
  [pos target min-target-distance trail]
  (let [current-distance (if target (grid/distance pos target) 0)
        far-enough? (fn [cand]
                      (or (nil? target)
                          (<= min-target-distance (grid/distance cand target))))
        passable (fm/get-passable-neighbors pos)
        sorted-candidates (fn [cells]
                            (sort-by (fn [cand]
                                       [(if target (grid/distance cand target) current-distance)
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
  (sync-kamikazee-cell! pos)
  (sync-kamikazee-cell! moved-pos)
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
          (sync-kamikazee-cell! moved-pos)
          {:pos moved-pos :steps-used 1})
        :update-unit
        (do
          ((:update-game-map! ctx) update-in (conj moved-pos :contents)
           #(merge % (:unit-updates action)))
          (sync-kamikazee-cell! moved-pos)
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
        (when-let [moved-pos (action-resolution/move-unit-to pos choice)]
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
        (launch/launch-steps-toward city-pos
                                    (or (first route)
                                        major-target
                                        city-pos))))

(defn- airport-ready-to-launch-kamikazee?
  [world city-pos cell]
  (and (computer-city-site? world city-pos)
       (launch-decisions/airport-kamikazee-ready? cell)
       (routing/city-has-launch-capacity? world city-pos route-city-launch-buffer)))

(defn- can-launch-kamikazee-action?
  [world action next-route-city]
  (and action
       (or (nil? next-route-city)
           (routing/city-has-launch-capacity? world next-route-city route-city-launch-buffer))))

(defn launch-kamikazee-from-airport!
  [ctx city-pos]
  (let [world ((:current-world ctx))
        cell (get-in world city-pos)
        state ((:load-major-invasion-state ctx))]
    (when (airport-ready-to-launch-kamikazee? world city-pos cell)
      (let [targets (targets/ordered-army-target-positions state
                                                           (targets/current-round ctx)
                                                           world)
            plan (routing/plan-route state world city-pos config/fighter-fuel)
            major-target (targets/choose-major-target state world city-pos)
            next-route-city (when (routing/city-site? world (first (:route plan)))
                              (first (:route plan)))
            launch-pos (open-launch-position world city-pos (:route plan) major-target)
            launch-state (decisions/airport-launch-state
                          {:city-has-capacity? (routing/city-has-launch-capacity? world city-pos route-city-launch-buffer)
                           :next-route-city-capacity? (routing/city-has-launch-capacity? world next-route-city route-city-launch-buffer)
                           :next-route-city next-route-city
                           :launch-pos launch-pos
                           :major-target major-target
                           :targets targets
                           :plan plan
                           :fighter-fuel config/fighter-fuel})
            action (launch-decisions/launch-decision launch-state)]
        (when (can-launch-kamikazee-action? world action next-route-city)
          (remove-airport-kamikazee! ctx city-pos)
          ((:update-game-map! ctx) assoc-in (conj (:launch-pos action) :contents) (:fighter action))
          (sync-kamikazee-cell! city-pos)
          (sync-kamikazee-cell! (:launch-pos action))
          (:launch-pos action))))))

(defn- hunt-refuel-stage?
  [stage wait-site]
  (and (= :refuel stage) wait-site))

(defn- hunt-return-stage?
  [stage resume-pos]
  (and (= :return stage) resume-pos))

(defn- process-hunt-step
  [ctx pos unit current-goal refuel-sites]
  (let [stage (:kamikazee-stage unit)
        wait-site (:kamikazee-wait-site unit)
        resume-pos (:kamikazee-hunt-resume-pos unit)
        fuel (:fuel unit config/fighter-fuel)]
    (cond
      (hunt-refuel-stage? stage wait-site)
      (process-refuel-stage ctx pos wait-site)

      (hunt-return-stage? stage resume-pos)
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

(defn- route-stage-inputs
  [world pos next-site current-goal]
  {:adjacent-route-city? (adjacent-route-city? world pos next-site)
   :at-route-site? (and next-site (routing/at-site? world pos next-site))
   :has-next-site? (boolean next-site)
   :close-enough-to-goal? (decisions/close-enough-to-hunt?
                           (when current-goal (grid/distance pos current-goal)))
   :has-goal? (boolean current-goal)})

(defn- land-at-route-city!
  [pos next-site]
  (fm/land-at-city pos next-site)
  (sync-kamikazee-cell! pos)
  (sync-kamikazee-cell! next-site)
  nil)

(defn- process-route-stage
  [ctx pos world unit route next-site current-goal]
  (case (decisions/route-stage-action (route-stage-inputs world pos next-site current-goal))
    :land-at-city (land-at-route-city! pos next-site)
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
      (case (decisions/kamikazee-stage-action {:stage (:kamikazee-stage unit)})
        :hunt-stage (process-hunt-step ctx pos unit current-goal refuel-sites)
        (process-route-stage ctx pos world unit route next-site current-goal)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:46:57.927868-05:00", :module-hash "-273308747", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "1583733051"} {:id "def/hunt-trail-length", :kind "def", :line 15, :end-line nil, :hash "608106012"} {:id "def/hunt-refuel-threshold", :kind "def", :line 16, :end-line nil, :hash "-1625190623"} {:id "def/route-city-launch-buffer", :kind "def", :line 17, :end-line nil, :hash "1942264726"} {:id "defn-/sync-kamikazee-cell!", :kind "defn-", :line 19, :end-line nil, :hash "524317486"} {:id "defn-/authoritative-kamikazee-unit?", :kind "defn-", :line 23, :end-line nil, :hash "-1528367643"} {:id "defn-/kamikazee-writeable-unit?", :kind "defn-", :line 29, :end-line nil, :hash "-1786862034"} {:id "defn-/fill-fuel!", :kind "defn-", :line 38, :end-line nil, :hash "-1440443400"} {:id "defn-/update-kamikazee-unit!", :kind "defn-", :line 45, :end-line nil, :hash "-1858506043"} {:id "form/9/declare", :kind "declare", :line 51, :end-line nil, :hash "-1144547704"} {:id "defn-/computer-city-site?", :kind "defn-", :line 53, :end-line nil, :hash "-280839162"} {:id "defn-/adjacent-player-army", :kind "defn-", :line 58, :end-line nil, :hash "-72650102"} {:id "defn-/move-toward!", :kind "defn-", :line 62, :end-line nil, :hash "178636244"} {:id "defn-/backtracking-candidates", :kind "defn-", :line 67, :end-line nil, :hash "-1472526749"} {:id "defn-/apply-hunt-step", :kind "defn-", :line 87, :end-line nil, :hash "1004713315"} {:id "defn-/non-backtracking-step", :kind "defn-", :line 112, :end-line nil, :hash "1802790062"} {:id "defn-/enter-hunt!", :kind "defn-", :line 123, :end-line nil, :hash "638879639"} {:id "defn-/choose-hunt-refuel-site", :kind "defn-", :line 132, :end-line nil, :hash "412885724"} {:id "defn-/start-hunt-refuel!", :kind "defn-", :line 137, :end-line nil, :hash "1161236971"} {:id "defn-/attack-adjacent-player-army", :kind "defn-", :line 146, :end-line nil, :hash "-1123602667"} {:id "defn-/process-refuel-stage", :kind "defn-", :line 157, :end-line nil, :hash "1724518943"} {:id "defn-/process-return-stage", :kind "defn-", :line 169, :end-line nil, :hash "1620256583"} {:id "defn-/process-active-hunt", :kind "defn-", :line 175, :end-line nil, :hash "-1462288368"} {:id "defn-/remove-airport-kamikazee!", :kind "defn-", :line 191, :end-line nil, :hash "-1574425026"} {:id "defn-/open-launch-position", :kind "defn-", :line 200, :end-line nil, :hash "-1106547413"} {:id "defn-/airport-ready-to-launch-kamikazee?", :kind "defn-", :line 211, :end-line nil, :hash "-1320617883"} {:id "defn-/can-launch-kamikazee-action?", :kind "defn-", :line 217, :end-line nil, :hash "848745713"} {:id "defn/launch-kamikazee-from-airport!", :kind "defn", :line 223, :end-line nil, :hash "-1736651046"} {:id "defn-/hunt-refuel-stage?", :kind "defn-", :line 254, :end-line nil, :hash "1661620863"} {:id "defn-/hunt-return-stage?", :kind "defn-", :line 258, :end-line nil, :hash "-1394597455"} {:id "defn-/process-hunt-step", :kind "defn-", :line 262, :end-line nil, :hash "-2136127443"} {:id "defn-/finish-route-node!", :kind "defn-", :line 278, :end-line nil, :hash "-36865006"} {:id "defn-/adjacent-route-city?", :kind "defn-", :line 292, :end-line nil, :hash "1971017307"} {:id "defn-/move-from-route", :kind "defn-", :line 298, :end-line nil, :hash "1250423462"} {:id "defn-/route-stage-inputs", :kind "defn-", :line 305, :end-line nil, :hash "-574329280"} {:id "defn-/land-at-route-city!", :kind "defn-", :line 314, :end-line nil, :hash "570793927"} {:id "defn-/process-route-stage", :kind "defn-", :line 321, :end-line nil, :hash "1154669454"} {:id "defn/process-kamikazee-fighter", :kind "defn", :line 330, :end-line nil, :hash "-694224085"}]}
;; clj-mutate-manifest-end
