(ns empire.computer.threat-response.kamikazee-mission
  (:require [empire.computer.core :as core]
            [empire.computer.fighter-movement :as fm]
            [empire.computer.threat-response.kamikazee-routing :as routing]
            [empire.computer.threat-response.kamikazee-targets :as targets]
            [empire.config.core :as config]))

(def ^:private hunt-trail-length 4)

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
        round-number (targets/current-round ctx)
        army-target (targets/choose-army-target state round-number world)
        major-target (targets/choose-major-target state world pos)
        current-goal (or army-target major-target)
        route (:kamikazee-route unit)
        next-site (first route)
        fuel (:fuel unit config/fighter-fuel)
        refuel-sites (routing/available-refueling-sites)]
    (cond
      (fm/find-adjacent-enemy pos)
      (when-let [new-pos (fm/attack-enemy pos (fm/find-adjacent-enemy pos))]
        (when (fm/consume-fighter-fuel new-pos)
          {:pos new-pos :steps-used 1}))

      (and next-site (routing/at-site? world pos next-site))
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
           (<= fuel (+ 2 (apply min (map #(routing/site-distance pos %) refuel-sites)))))
      (if-let [site (apply min-key #(routing/site-distance pos %) refuel-sites)]
        (if (routing/at-site? world pos site)
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
;; {:version 1, :tested-at "2026-03-14T08:36:44.110152-05:00", :module-hash "362437439", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "1755399322"} {:id "def/hunt-trail-length", :kind "def", :line 8, :end-line 8, :hash "608106012"} {:id "defn-/refuel-at-site!", :kind "defn-", :line 10, :end-line 14, :hash "2037084067"} {:id "defn-/move-toward!", :kind "defn-", :line 16, :end-line 19, :hash "178636244"} {:id "defn-/non-backtracking-step", :kind "defn-", :line 21, :end-line 43, :hash "-1495100354"} {:id "defn/process-kamikazee-fighter", :kind "defn", :line 45, :end-line 90, :hash "250974694"}]}
;; clj-mutate-manifest-end
