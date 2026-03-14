(ns empire.computer.threat-response.kamikazee-routing
  (:require [empire.computer.core :as core]
            [empire.computer.fighter-movement :as fm]
            [empire.computer.threat-response.kamikazee-targets :as targets]
            [empire.config.core :as config]
            [empire.state.api :as sa]))

(defn- carrier-site?
  [world pos]
  (= :carrier (get-in world (conj pos :contents :type))))

(defn site-distance
  [a b]
  (fm/distance-to a b))

(defn at-site?
  [world pos site]
  (or (= pos site)
      (and (carrier-site? world site)
           (<= (site-distance pos site) 1))))

(defn available-refueling-sites
  []
  (sa/rebuild-refueling-caches!)
  (vec (distinct (concat (or (sa/read-state :computer-city-positions) #{})
                        (or (sa/read-state :computer-carrier-positions) #{})))))

(defn- edge-reachable?
  [from to fuel-budget]
  (<= (site-distance from to) fuel-budget))

(defn- terminal-score
  [site goal-points]
  (apply min (map #(site-distance site %) goal-points)))

(defn plan-route
  [world pos fuel goal-points]
  (let [sites (available-refueling-sites)
        goal-points (vec goal-points)
        full-fuel config/fighter-fuel
        start-budget (if (some #(at-site? world pos %) sites) full-fuel fuel)
        direct? (some #(<= (site-distance pos %) full-fuel) goal-points)
        goal-site? (fn [site] (some #(<= (site-distance site %) full-fuel) goal-points))
        start-sites (vec (distinct (concat
                                    (filter #(at-site? world pos %) sites)
                                    (filter #(edge-reachable? pos % start-budget) sites))))]
    (cond
      (or direct? (empty? goal-points))
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
                                :score [(terminal-score site goal-points)
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
                                              [(terminal-score candidate goal-points) candidate])))]
                (recur (reduce #(conj %1 [%2 (conj path %2)]) (pop queue) neighbors)
                       (into visited neighbors)
                       best-partial)))))))))

(defn carrier-support-target
  [ctx pos]
  (let [world ((:current-world ctx))
        state ((:load-major-invasion-state ctx))
        support-sites (targets/fighter-support-targets state)
        target-point (targets/choose-major-target state world pos)
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
        target-points (targets/invasion-target-points state world)
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-14T08:35:47.562018-05:00", :module-hash "-449972455", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "2116081666"} {:id "defn-/carrier-site?", :kind "defn-", :line 8, :end-line 10, :hash "-128511322"} {:id "defn/site-distance", :kind "defn", :line 12, :end-line 14, :hash "1551245603"} {:id "defn/at-site?", :kind "defn", :line 16, :end-line 20, :hash "-1499854197"} {:id "defn/available-refueling-sites", :kind "defn", :line 22, :end-line 26, :hash "-1085587931"} {:id "defn-/edge-reachable?", :kind "defn-", :line 28, :end-line 30, :hash "1619735915"} {:id "defn-/terminal-score", :kind "defn-", :line 32, :end-line 34, :hash "-953728457"} {:id "defn/plan-route", :kind "defn", :line 36, :end-line 87, :hash "-98391357"} {:id "defn/carrier-support-target", :kind "defn", :line 89, :end-line 111, :hash "1583931107"} {:id "defn/invasion-production-override", :kind "defn", :line 113, :end-line 137, :hash "109214244"}]}
;; clj-mutate-manifest-end
