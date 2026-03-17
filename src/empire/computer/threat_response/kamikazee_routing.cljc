(ns empire.computer.threat-response.kamikazee-routing
  (:require [empire.computer.core :as core]
            [empire.computer.fighter-movement :as fm]
            [empire.computer.threat-response.kamikazee-targets :as targets]
            [empire.config.core :as config]
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
                 (core/neighbors-in-map world pos))))

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
  (apply min (map #(core/distance pos %) goal-points)))

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

(defn plan-route
  [state world pos fuel]
  (let [route (cond
                (city-site? world pos)
                (route-from-node state pos)

                (carrier-site? world pos)
                (route-from-node state pos)

                :else
                (route-from-reachable-city state pos fuel))
        terminal-site (or (peek route)
                          (when (contains? (:kamikazee-terminal-sites state) pos) pos))]
    {:route (vec route)
     :terminal-site terminal-site
     :complete? (boolean (or (seq route)
                             (contains? (:kamikazee-terminal-sites state) pos)))}))

(defn carrier-support-target
  [ctx pos]
  (let [state ((:load-major-invasion-state ctx))]
    (when (fixed-carrier? state pos)
      pos)))

(defn invasion-production-override
  [city-pos]
  (let [state (sa/read-state :major-invasion-state)
        world (sa/current-world)
        visible-world (or (sa/read-state :computer-map) world)
        target-points (targets/invasion-target-points state visible-world)
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
;; {:version 1, :tested-at "2026-03-14T10:05:09.816873-05:00", :module-hash "-332387105", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "2116081666"} {:id "defn/carrier-site?", :kind "defn", :line 8, :end-line 10, :hash "-642704688"} {:id "defn/city-site?", :kind "defn", :line 12, :end-line 15, :hash "878576996"} {:id "defn/site-distance", :kind "defn", :line 17, :end-line 19, :hash "1551245603"} {:id "defn/at-site?", :kind "defn", :line 21, :end-line 25, :hash "-1499854197"} {:id "defn/available-refueling-sites", :kind "defn", :line 27, :end-line 31, :hash "-1085587931"} {:id "defn-/edge-reachable?", :kind "defn-", :line 33, :end-line 35, :hash "1619735915"} {:id "defn-/target-distance", :kind "defn-", :line 37, :end-line 39, :hash "-1588550186"} {:id "defn-/computer-city-positions", :kind "defn-", :line 41, :end-line 48, :hash "-33793779"} {:id "defn-/computer-carriers", :kind "defn-", :line 50, :end-line 57, :hash "2071859718"} {:id "defn-/reachable-marked-city", :kind "defn-", :line 59, :end-line 63, :hash "343051173"} {:id "defn-/bridging-carrier", :kind "defn-", :line 65, :end-line 77, :hash "-1066173304"} {:id "defn-/pick-root-city", :kind "defn-", :line 79, :end-line 82, :hash "1757221937"} {:id "defn-/choose-forward-carrier", :kind "defn-", :line 84, :end-line 93, :hash "746269383"} {:id "defn/rebuild-routing-graph", :kind "defn", :line 95, :end-line 148, :hash "2125941602"} {:id "defn/rebuild-routing-graph!", :kind "defn", :line 150, :end-line 156, :hash "-1690621996"} {:id "defn/fixed-carrier?", :kind "defn", :line 158, :end-line 161, :hash "1253731980"} {:id "defn-/next-hop", :kind "defn-", :line 163, :end-line 166, :hash "1670891984"} {:id "defn-/route-from-node", :kind "defn-", :line 168, :end-line 176, :hash "-1672535525"} {:id "defn-/route-from-reachable-city", :kind "defn-", :line 178, :end-line 191, :hash "-1244797622"} {:id "defn/plan-route", :kind "defn", :line 193, :end-line 209, :hash "283588680"} {:id "defn/carrier-support-target", :kind "defn", :line 211, :end-line 215, :hash "1550006344"} {:id "defn/invasion-production-override", :kind "defn", :line 217, :end-line 241, :hash "589623714"}]}
;; clj-mutate-manifest-end
