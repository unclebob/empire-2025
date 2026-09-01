(ns empire.computer.threat-response.kamikazee-routing
  (:require [empire.computer.shared.grid :as grid]
            [empire.computer.fighter.movement :as fm]
            [empire.computer.threat-response.kamikazee-targets :as targets]
            [empire.config.core :as config]
            [empire.computer.shared.world-query :as world-query]
            [empire.state.api :as sa]))

(defn carrier-site?
  [world pos]
  (= :carrier (get-in world (conj pos :contents :type))))

(defn city-site?
  [world pos]
  (and (= :city (get-in world (conj pos :type)))
       (= :computer (get-in world (conj pos :city-status)))))

(defn free-adjacent-cell-count
  [world pos]
  (count (filter #(nil? (get-in world (conj % :contents)))
                 (grid/neighbors-in-map world pos))))

(defn city-has-launch-capacity?
  [world pos required-free-cells]
  (and (city-site? world pos)
       (>= (free-adjacent-cell-count world pos) required-free-cells)))

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

(defn- target-distance
  [pos goal-points]
  (apply min (map #(grid/distance pos %) goal-points)))

(defn- computer-city-positions
  [world]
  (->> (for [i (range (count world))
             j (range (count (first world)))
             :when (city-site? world [i j])]
         [i j])
       sort
       vec))

(defn- computer-carriers
  [world]
  (->> (for [i (range (count world))
             j (range (count (first world)))
             :when (carrier-site? world [i j])]
         [i j])
       sort
       vec))

(defn- reachable-marked-city
  [city marked-cities]
  (first (sort-by (fn [marked]
                    [(site-distance city marked) marked])
                  (filter #(edge-reachable? city % config/fighter-fuel) marked-cities))))

(defn- bridging-carrier
  [city marked-cities carriers]
  (first
   (sort-by (fn [[carrier marked]]
              [(site-distance city carrier)
               (site-distance carrier marked)
               carrier
               marked])
            (for [carrier carriers
                  marked marked-cities
                  :when (and (edge-reachable? city carrier config/fighter-fuel)
                             (edge-reachable? carrier marked config/fighter-fuel))]
              [carrier marked]))))

(defn- pick-root-city
  [cities goal-points]
  (when (and (seq cities) (seq goal-points))
    (first (sort-by #(vector (target-distance % goal-points) %) cities))))

(defn- choose-forward-carrier
  [root-city carriers goal-points]
  (let [root-distance (target-distance root-city goal-points)]
    (first (sort-by (fn [carrier]
                      [(target-distance carrier goal-points)
                       (site-distance root-city carrier)
                       carrier])
                    (filter #(and (edge-reachable? root-city % config/fighter-fuel)
                                  (< (target-distance % goal-points) root-distance))
                            carriers)))))

(defn- build-routing-additions
  [pending marked-cities carriers]
  (->> pending
       (keep (fn [city]
               (if-let [marked (reachable-marked-city city marked-cities)]
                 {:city city :next-hop marked}
                 (when-let [[carrier marked] (bridging-carrier city marked-cities carriers)]
                   {:city city :next-hop carrier :carrier carrier :carrier-next marked}))))))

(defn- merge-routing-additions
  [city-next-hops carrier-next-hops bridge-carriers additions]
  {:city-next-hops (reduce (fn [acc {:keys [city next-hop]}]
                             (assoc acc city next-hop))
                           city-next-hops
                           additions)
   :carrier-next-hops (reduce (fn [acc {:keys [carrier carrier-next]}]
                                (cond-> acc
                                  carrier (assoc carrier carrier-next)))
                              carrier-next-hops
                              additions)
   :bridge-carriers (into bridge-carriers (keep :carrier additions))})

(defn- finalize-routing-graph
  [root-city carriers goal-points city-next-hops carrier-next-hops bridge-carriers]
  (let [forward-carrier (when (seq goal-points)
                          (choose-forward-carrier root-city carriers goal-points))
        final-city-next-hops (cond-> city-next-hops
                               forward-carrier (assoc root-city forward-carrier))
        terminal-sites (cond-> #{root-city}
                         forward-carrier (conj forward-carrier))]
    {:kamikazee-root-city root-city
     :kamikazee-city-next-hops final-city-next-hops
     :kamikazee-carrier-next-hops carrier-next-hops
     :kamikazee-bridge-carriers bridge-carriers
     :kamikazee-forward-carrier forward-carrier
     :kamikazee-terminal-sites terminal-sites}))

(defn rebuild-routing-graph
  [world state]
  (let [goal-points (vec (targets/invasion-target-points state world))
        cities (computer-city-positions world)
        carriers (computer-carriers world)
        root-city (pick-root-city cities goal-points)]
    (if-not root-city
      {:kamikazee-root-city nil
       :kamikazee-city-next-hops {}
       :kamikazee-carrier-next-hops {}
       :kamikazee-bridge-carriers #{}
       :kamikazee-forward-carrier nil
       :kamikazee-terminal-sites #{}}
      (loop [marked-cities #{root-city}
             city-next-hops {}
             carrier-next-hops {}
             bridge-carriers #{}
             pending (disj (set cities) root-city)]
        (let [additions (build-routing-additions pending marked-cities carriers)
              next-marked-cities (into marked-cities (map :city additions))
              {:keys [city-next-hops carrier-next-hops bridge-carriers]}
              (merge-routing-additions city-next-hops carrier-next-hops bridge-carriers additions)]
          (if (empty? additions)
            (finalize-routing-graph root-city
                                    carriers
                                    goal-points
                                    city-next-hops
                                    carrier-next-hops
                                    bridge-carriers)
            (recur next-marked-cities
                   city-next-hops
                   carrier-next-hops
                   bridge-carriers
                   (apply disj pending (map :city additions)))))))))

(defn rebuild-routing-graph!
  [ctx]
  (let [world ((:current-world ctx))
        state ((:load-major-invasion-state ctx))
        graph (rebuild-routing-graph world state)]
    ((:update-major-invasion-state! ctx) merge graph)
    graph))

(defn fixed-carrier?
  [state pos]
  (or (contains? (:kamikazee-bridge-carriers state) pos)
      (= (:kamikazee-forward-carrier state) pos)))

(defn- next-hop
  [state pos]
  (or (get (:kamikazee-city-next-hops state) pos)
      (get (:kamikazee-carrier-next-hops state) pos)))

(defn- route-from-node
  [state start]
  (loop [node start
         visited #{}
         route []]
    (if-let [hop (and (not (visited node))
                      (next-hop state node))]
      (recur hop (conj visited node) (conj route hop))
      route)))

(defn- route-from-reachable-city
  [state pos fuel]
  (let [marked-cities (cond-> (set (keys (:kamikazee-city-next-hops state)))
                        (:kamikazee-root-city state) (conj (:kamikazee-root-city state)))
        goal-points (or (seq (:target-land-set state))
                        (seq (:detection-points state))
                        [pos])]
    (when-let [city (first (sort-by (fn [city]
                                      [(site-distance pos city)
                                       (target-distance city goal-points)
                                       city])
                                    (filter #(edge-reachable? pos % fuel)
                                            marked-cities)))]
      (vec (cons city (route-from-node state city))))))

(defn- route-from-current-site
  [state world pos fuel]
  (if (or (city-site? world pos) (carrier-site? world pos))
    (route-from-node state pos)
    (route-from-reachable-city state pos fuel)))

(defn- route-complete?
  [state pos route]
  (boolean (or (seq route)
               (contains? (:kamikazee-terminal-sites state) pos))))

(defn plan-route
  [state world pos fuel]
  (let [route (route-from-current-site state world pos fuel)
        terminal-site (or (peek route)
                          (when (contains? (:kamikazee-terminal-sites state) pos) pos))]
    {:route (vec route)
     :terminal-site terminal-site
     :complete? (route-complete? state pos route)}))

(defn carrier-support-target
  [ctx pos]
  (let [state ((:load-major-invasion-state ctx))]
    (when (fixed-carrier? state pos)
      pos)))

(defn- loaded-invasion-transport?
  [unit]
  (and unit
       (= :transport (:type unit))
       (= :computer (:owner unit))
       (:major-invasion unit)
       (pos? (:army-count unit 0))))

(defn- loaded-invasion-transports
  [world]
  (for [i (range (count world))
        j (range (count (first world)))
        :let [unit (get-in world [i j :contents])]
        :when (loaded-invasion-transport? unit)]
    [i j]))

(defn- fighter-override-target?
  [city-pos target-points]
  (and (seq target-points)
       (some #(<= (grid/distance city-pos %) config/fighter-fuel) target-points)))

(defn invasion-production-override
  [city-pos]
  (let [state (sa/read-state :major-invasion-state)
        world (sa/read-state :computer-map)
        target-points (targets/invasion-target-points state world)
        loaded-transports (loaded-invasion-transports world)]
    (when (:active? state)
      (when (or (fighter-override-target? city-pos target-points)
                (seq loaded-transports))
        :fighter))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:45:20.22607-05:00", :module-hash "-1176144320", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "178015626"} {:id "defn/carrier-site?", :kind "defn", :line 9, :end-line nil, :hash "-642704688"} {:id "defn/city-site?", :kind "defn", :line 13, :end-line nil, :hash "878576996"} {:id "defn/free-adjacent-cell-count", :kind "defn", :line 18, :end-line nil, :hash "885183822"} {:id "defn/city-has-launch-capacity?", :kind "defn", :line 23, :end-line nil, :hash "-1472539427"} {:id "defn/site-distance", :kind "defn", :line 28, :end-line nil, :hash "1551245603"} {:id "defn/at-site?", :kind "defn", :line 32, :end-line nil, :hash "-1499854197"} {:id "defn/available-refueling-sites", :kind "defn", :line 38, :end-line nil, :hash "-1085587931"} {:id "defn-/edge-reachable?", :kind "defn-", :line 44, :end-line nil, :hash "1619735915"} {:id "defn-/target-distance", :kind "defn-", :line 48, :end-line nil, :hash "575727553"} {:id "defn-/computer-city-positions", :kind "defn-", :line 52, :end-line nil, :hash "-33793779"} {:id "defn-/computer-carriers", :kind "defn-", :line 61, :end-line nil, :hash "2071859718"} {:id "defn-/reachable-marked-city", :kind "defn-", :line 70, :end-line nil, :hash "406135352"} {:id "defn-/bridging-carrier", :kind "defn-", :line 76, :end-line nil, :hash "-1066173304"} {:id "defn-/pick-root-city", :kind "defn-", :line 90, :end-line nil, :hash "1725456612"} {:id "defn-/choose-forward-carrier", :kind "defn-", :line 95, :end-line nil, :hash "935657973"} {:id "defn-/build-routing-additions", :kind "defn-", :line 106, :end-line nil, :hash "-225963138"} {:id "defn-/merge-routing-additions", :kind "defn-", :line 115, :end-line nil, :hash "926755"} {:id "defn-/finalize-routing-graph", :kind "defn-", :line 128, :end-line nil, :hash "-1783247182"} {:id "defn/rebuild-routing-graph", :kind "defn", :line 143, :end-line nil, :hash "-1649765226"} {:id "defn/rebuild-routing-graph!", :kind "defn", :line 178, :end-line nil, :hash "-1690621996"} {:id "defn/fixed-carrier?", :kind "defn", :line 186, :end-line nil, :hash "1253731980"} {:id "defn-/next-hop", :kind "defn-", :line 191, :end-line nil, :hash "1670891984"} {:id "defn-/route-from-node", :kind "defn-", :line 196, :end-line nil, :hash "-1672535525"} {:id "defn-/route-from-reachable-city", :kind "defn-", :line 206, :end-line nil, :hash "-634844958"} {:id "defn-/route-from-current-site", :kind "defn-", :line 221, :end-line nil, :hash "-1589401715"} {:id "defn-/route-complete?", :kind "defn-", :line 227, :end-line nil, :hash "-9257626"} {:id "defn/plan-route", :kind "defn", :line 232, :end-line nil, :hash "-1507490415"} {:id "defn/carrier-support-target", :kind "defn", :line 241, :end-line nil, :hash "1550006344"} {:id "defn-/loaded-invasion-transport?", :kind "defn-", :line 247, :end-line nil, :hash "599793708"} {:id "defn-/loaded-invasion-transports", :kind "defn-", :line 255, :end-line nil, :hash "-1265761061"} {:id "defn-/fighter-override-target?", :kind "defn-", :line 263, :end-line nil, :hash "894471491"} {:id "defn/invasion-production-override", :kind "defn", :line 268, :end-line nil, :hash "122416700"}]}
;; clj-mutate-manifest-end
