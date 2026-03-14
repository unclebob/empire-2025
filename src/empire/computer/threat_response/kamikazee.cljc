(ns empire.computer.threat-response.kamikazee
  "Kamikazee fighter support for major invasion."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.fighter-movement :as fm]
            [empire.config.core :as config]))

(def ^:private target-choice-width 3)
(def ^:private hunt-trail-length 4)

(defn current-round
  [ctx]
  (if-let [read-runtime-state (:read-runtime-state ctx)]
    (or (read-runtime-state :round-number) 0)
    0))

(defn- player-army?
  [world pos]
  (let [unit (get-in world (conj pos :contents))]
    (and unit
         (= :player (:owner unit))
         (= :army (:type unit)))))

(defn- alive-targets
  [world targets]
  (->> targets
       (filter (fn [{:keys [pos]}] (player-army? world pos)))
       vec))

(defn- invasion-target-points
  [state world]
  (let [army-targets (alive-targets world (:kamikazee-army-targets state))]
    (or (seq (map :pos army-targets))
        (seq (:detection-points state))
        (seq (:target-land-set state))
        [])))

(defn ordered-army-target-positions
  [state round-number world]
  (->> (:kamikazee-army-targets state)
       (alive-targets world)
       (sort-by (fn [{:keys [seen-round pos]}]
                  [(- seen-round) pos]))
       (mapv :pos)))

(defn refresh-army-targets!
  [ctx]
  (let [world (or (when-let [current-world (:current-world ctx)]
                    (current-world))
                  (sa/current-world))
        round-number (current-round ctx)]
    (when-let [update-major-invasion-state! (:update-major-invasion-state! ctx)]
      (update-major-invasion-state!
       (fn [state]
         (assoc state :kamikazee-army-targets
                (alive-targets world (:kamikazee-army-targets state))))))
    (let [state (or (when-let [load-major-invasion-state (:load-major-invasion-state ctx)]
                      (load-major-invasion-state))
                    (sa/read-state :major-invasion-state))
          targets (ordered-army-target-positions state round-number world)]
      (doseq [i (range (count world))
              j (range (count (first world)))
              :let [unit (get-in world [i j :contents])]
              :when (and unit
                         (= :computer (:owner unit))
                         (= :fighter (:type unit))
                         (:kamikazee unit))]
        ((:update-game-map! ctx) assoc-in [i j :contents :kamikazee-targets] targets)))))

(defn record-army-target!
  [ctx pos]
  (let [round-number (current-round ctx)]
    ((:update-major-invasion-state! ctx)
     (fn [state]
       (let [targets (remove #(= pos (:pos %)) (:kamikazee-army-targets state))]
         (assoc state :kamikazee-army-targets
                (vec (cons {:pos pos :seen-round round-number} targets)))))))
  (refresh-army-targets! ctx))

(defn- carrier-site?
  [world pos]
  (= :carrier (get-in world (conj pos :contents :type))))

(defn- city-site?
  [world pos]
  (and (= :city (get-in world (conj pos :type)))
       (= :computer (get-in world (conj pos :city-status)))))

(defn site-distance
  [a b]
  (fm/distance-to a b))

(defn at-site?
  [world pos site]
  (or (= pos site)
      (and (carrier-site? world site)
           (<= (site-distance pos site) 1))))

(defn- available-refueling-sites
  []
  (sa/rebuild-refueling-caches!)
  (vec (distinct (concat (or (sa/read-state :computer-city-positions) #{})
                        (or (sa/read-state :computer-carrier-positions) #{})))))

(defn- edge-reachable?
  [from to fuel-budget]
  (<= (site-distance from to) fuel-budget))

(defn- terminal-score
  [site targets]
  (apply min (map #(site-distance site %) targets)))

(defn plan-route
  [world pos fuel targets]
  (let [sites (available-refueling-sites)
        targets (vec targets)
        full-fuel config/fighter-fuel
        start-budget (if (some #(at-site? world pos %) sites) full-fuel fuel)
        direct? (some #(<= (site-distance pos %) full-fuel) targets)
        goal-site? (fn [site] (some #(<= (site-distance site %) full-fuel) targets))
        start-sites (vec (distinct (concat
                                    (filter #(at-site? world pos %) sites)
                                    (filter #(edge-reachable? pos % start-budget) sites))))]
    (cond
      (or direct? (empty? targets))
      {:route [] :terminal-site pos :complete? true}

      (empty? start-sites)
      {:route [] :terminal-site nil :complete? false}

      :else
      (loop [queue (into clojure.lang.PersistentQueue/EMPTY
                         (map (fn [site] [site [site]]) start-sites))
             visited (set start-sites)
             best-partial nil]
        (if (empty? queue)
          (if best-partial
            {:route (:path best-partial)
             :terminal-site (:site best-partial)
             :complete? false}
            {:route [] :terminal-site nil :complete? false})
          (let [[site path] (peek queue)
                partial-record {:site site
                                :path path
                                :score [(terminal-score site targets)
                                        (count path)
                                        site]}
                best-partial (if (or (nil? best-partial)
                                     (neg? (compare (:score partial-record)
                                                    (:score best-partial))))
                               partial-record
                               best-partial)]
            (if (goal-site? site)
              {:route (if (at-site? world pos (first path)) (vec (rest path)) (vec path))
               :terminal-site site
               :complete? true}
              (let [neighbors (->> sites
                                   (remove visited)
                                   (filter #(edge-reachable? site % full-fuel))
                                   (sort-by (fn [candidate]
                                              [(terminal-score candidate targets) candidate])))]
                (recur (reduce #(conj %1 [%2 (conj path %2)]) (pop queue) neighbors)
                       (into visited neighbors)
                       best-partial)))))))))

(defn choose-army-target
  [state round-number world]
  (let [ordered (ordered-army-target-positions state round-number world)
        choices (vec (take target-choice-width ordered))]
    (when (seq choices)
      (rand-nth choices))))

(defn- choose-major-target
  [state world pos]
  (let [targets (invasion-target-points state world)]
    (when (seq targets)
      (apply min-key #(core/distance pos %) targets))))

(defn fighter-support-targets
  [state]
  (or (seq (:kamikazee-terminal-sites state))
      (seq (:sea-reachable-detection-points state))
      (seq (:detection-points state))
      []))

(defn carrier-support-target
  [ctx pos]
  (let [world ((:current-world ctx))
        state ((:load-major-invasion-state ctx))
        support-sites (fighter-support-targets state)
        target-point (choose-major-target state world pos)
        sea-candidates (for [i (range (count world))
                             j (range (count (first world)))
                             :let [cell (get-in world [i j])]
                             :when (and (= :sea (:type cell))
                                        (or (nil? (:contents cell))
                                            (= pos [i j])))]
                         [i j])]
    (when (and (seq support-sites) target-point (seq sea-candidates))
      (let [site (apply min-key #(core/distance pos %) support-sites)
            midpoint [(quot (+ (first site) (first target-point)) 2)
                      (quot (+ (second site) (second target-point)) 2)]]
        (first (sort-by (fn [cand]
                          [(core/distance cand midpoint)
                           (+ (core/distance cand site)
                              (core/distance cand target-point))
                           cand])
                        sea-candidates))))))

(defn invasion-production-override
  [city-pos]
  (let [state (sa/read-state :major-invasion-state)
        world (sa/current-world)
        target-points (invasion-target-points state world)
        loaded-transports
        (for [i (range (count world))
              j (range (count (first world)))
              :let [unit (get-in world [i j :contents])]
              :when (and unit
                         (= :transport (:type unit))
                         (= :computer (:owner unit))
                         (:major-invasion unit)
                         (pos? (:army-count unit 0)))]
          [i j])]
    (when (:active? state)
      (cond
        (and (seq target-points)
             (some #(<= (core/distance city-pos %) config/fighter-fuel) target-points))
        :fighter

        (seq loaded-transports)
        :fighter

        :else nil))))

(defn- refuel-at-site!
  [ctx pos site]
  ((:update-game-map! ctx) assoc-in (conj pos :contents :fuel) config/fighter-fuel)
  ((:update-game-map! ctx) assoc-in (conj pos :contents :kamikazee-wait-site) site)
  pos)

(defn- move-toward!
  [pos target]
  (when-let [hop (fm/hop-over-friendly pos target)]
    (fm/execute-hop pos hop)))

(defn- non-backtracking-step
  [ctx pos target]
  (let [world ((:current-world ctx))
        unit (get-in world (conj pos :contents))
        trail (set (:kamikazee-trail unit))
        current-distance (if target (core/distance pos target) 0)
        candidates (->> (fm/get-passable-neighbors pos)
                        (remove fm/occupied?)
                        (remove trail)
                        (sort-by (fn [cand]
                                   [(if target (core/distance cand target) current-distance)
                                    cand])))
        choices (vec (if (seq candidates)
                       candidates
                       (->> (fm/get-passable-neighbors pos)
                            (remove fm/occupied?)
                            vec)))]
    (when-let [choice (when (seq choices) (rand-nth choices))]
      (when (core/move-unit-to pos choice)
        (when (fm/consume-fighter-fuel choice)
          ((:update-game-map! ctx) assoc-in (conj choice :contents :kamikazee-trail)
           (vec (take-last hunt-trail-length (conj (:kamikazee-trail unit []) pos))))
          {:pos choice :steps-used 1})))))

(defn process-kamikazee-fighter
  [ctx pos unit]
  (let [world ((:current-world ctx))
        state ((:load-major-invasion-state ctx))
        round-number (current-round ctx)
        army-target (choose-army-target state round-number world)
        major-target (choose-major-target state world pos)
        current-goal (or army-target major-target)
        route (:kamikazee-route unit)
        next-site (first route)
        fuel (:fuel unit config/fighter-fuel)
        refuel-sites (available-refueling-sites)]
    (cond
      (fm/find-adjacent-enemy pos)
      (when-let [new-pos (fm/attack-enemy pos (fm/find-adjacent-enemy pos))]
        (when (fm/consume-fighter-fuel new-pos)
          {:pos new-pos :steps-used 1}))

      (and next-site (at-site? world pos next-site))
      (do
        (refuel-at-site! ctx pos next-site)
        ((:update-game-map! ctx) update-in (conj pos :contents)
         #(-> %
              (assoc :kamikazee-terminal-site (or (:kamikazee-terminal-site %) next-site))
              (assoc :kamikazee-stage (if (= 1 (count route)) :hunt :route))
              (assoc :kamikazee-route (vec (rest route)))))
        {:pos pos :steps-used 1})

      next-site
      (move-toward! pos next-site)

      (and (seq refuel-sites)
           (<= fuel (+ 2 (apply min (map #(site-distance pos %) refuel-sites)))))
      (if-let [site (apply min-key #(site-distance pos %) refuel-sites)]
        (if (at-site? world pos site)
          (do (refuel-at-site! ctx pos site)
              {:pos pos :steps-used 1})
          (move-toward! pos site))
        (non-backtracking-step ctx pos current-goal))

      current-goal
      (or (move-toward! pos current-goal)
          (non-backtracking-step ctx pos current-goal))

      :else
      (non-backtracking-step ctx pos nil))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-14T07:45:21.731052-05:00", :module-hash "2051665793", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "201117200"} {:id "def/target-choice-width", :kind "def", :line 8, :end-line 8, :hash "981135397"} {:id "def/hunt-trail-length", :kind "def", :line 9, :end-line 9, :hash "608106012"} {:id "defn/current-round", :kind "defn", :line 11, :end-line 15, :hash "246685672"} {:id "defn-/player-army?", :kind "defn-", :line 17, :end-line 22, :hash "-1781861708"} {:id "defn-/alive-targets", :kind "defn-", :line 24, :end-line 28, :hash "83254104"} {:id "defn-/invasion-target-points", :kind "defn-", :line 30, :end-line 36, :hash "-1787471456"} {:id "defn/ordered-army-target-positions", :kind "defn", :line 38, :end-line 44, :hash "872172517"} {:id "defn/refresh-army-targets!", :kind "defn", :line 46, :end-line 68, :hash "-1393250656"} {:id "defn/record-army-target!", :kind "defn", :line 70, :end-line 78, :hash "-1093510877"} {:id "defn-/carrier-site?", :kind "defn-", :line 80, :end-line 82, :hash "-128511322"} {:id "defn-/city-site?", :kind "defn-", :line 84, :end-line 87, :hash "-1283235803"} {:id "defn/site-distance", :kind "defn", :line 89, :end-line 91, :hash "1551245603"} {:id "defn/at-site?", :kind "defn", :line 93, :end-line 97, :hash "-1499854197"} {:id "defn-/available-refueling-sites", :kind "defn-", :line 99, :end-line 103, :hash "-984306760"} {:id "defn-/edge-reachable?", :kind "defn-", :line 105, :end-line 107, :hash "1619735915"} {:id "defn-/terminal-score", :kind "defn-", :line 109, :end-line 111, :hash "1776648839"} {:id "defn/plan-route", :kind "defn", :line 113, :end-line 164, :hash "-1452674909"} {:id "defn/choose-army-target", :kind "defn", :line 166, :end-line 171, :hash "-1766208919"} {:id "defn-/choose-major-target", :kind "defn-", :line 173, :end-line 177, :hash "1652663499"} {:id "defn/fighter-support-targets", :kind "defn", :line 179, :end-line 184, :hash "958643692"} {:id "defn/carrier-support-target", :kind "defn", :line 186, :end-line 208, :hash "1269338849"} {:id "defn/invasion-production-override", :kind "defn", :line 210, :end-line 234, :hash "-770531531"} {:id "defn-/refuel-at-site!", :kind "defn-", :line 236, :end-line 240, :hash "2037084067"} {:id "defn-/move-toward!", :kind "defn-", :line 242, :end-line 245, :hash "178636244"} {:id "defn-/non-backtracking-step", :kind "defn-", :line 247, :end-line 269, :hash "-1495100354"} {:id "defn/process-kamikazee-fighter", :kind "defn", :line 271, :end-line 316, :hash "-302883633"}]}
;; clj-mutate-manifest-end
