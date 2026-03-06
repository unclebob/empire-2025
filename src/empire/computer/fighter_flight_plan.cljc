;; mutation-tested: 2026-03-02
(ns empire.computer.fighter-flight-plan
  (:require [empire.computer.ship :as ship]
            [empire.config.core :as config]
            [empire.computer.fighter-movement :as fm]
            [empire.computer.fighter-exploration :as fe]))

(def ^:private sortie-half-steps 16)

(def ^:private neighbor-offsets
  [[-1 -1] [-1 0] [-1 1]
   [0 -1]          [0 1]
   [1 -1]  [1 0]  [1 1]])

(defn- neighbors
  [world pos]
  (for [[dr dc] neighbor-offsets
        :let [n [(+ (first pos) dr) (+ (second pos) dc)]]
        :when (and (<= 0 (first n)) (< (first n) (count world))
                   (<= 0 (second n)) (< (second n) (count (first world)))
                   (some? (get-in world n)))]
    n))

(defn- current-refueling-site
  [world pos]
  (let [cell (get-in world pos)]
    (cond
      (and (= :city (:type cell)) (= :computer (:city-status cell)))
      pos

      :else
      (first (filter (fn [n]
                       (let [ncell (get-in world n)]
                         (and (= :carrier (get-in ncell [:contents :type]))
                              (= :computer (get-in ncell [:contents :owner]))
                              (= :holding (get-in ncell [:contents :carrier-mode])))))
                     (neighbors world pos))))))

(defn- choose-leg
  [world read-runtime-state current-site]
  (let [sites (ship/find-refueling-sites)
        reachable (filter #(and (not= % current-site)
                                (<= (fm/distance-to current-site %) config/fighter-fuel))
                          sites)
        active-targets (set (for [c (range (count world))
                                  r (range (count (first world)))
                                  :let [u (get-in world [c r :contents])
                                        target (:flight-target-site u)]
                                  :when (and (= :fighter (:type u))
                                             (= :computer (:owner u))
                                             target)]
                              target))
        candidate-targets (let [unclaimed (remove active-targets reachable)]
                            (if (seq unclaimed) unclaimed reachable))
        leg-records (or (read-runtime-state :fighter-leg-records) {})
        scored (map (fn [target]
                      (let [leg-key #{current-site target}
                            record (get leg-records leg-key)
                            last-flown (:last-flown record -1)]
                        [target last-flown]))
                    candidate-targets)]
    (when (seq scored)
      (first (first (sort-by second scored))))))

(defn- assign-regular-leg!
  [update-game-map! world pos site-pos read-runtime-state]
  (when-let [target (choose-leg world read-runtime-state site-pos)]
    (update-game-map! update-in (conj pos :contents)
                      assoc :flight-target-site target
                      :flight-origin-site site-pos
                      :flight-mode :regular)))

(defn- clamp-to-map-bounds
  [world [r c]]
  (let [height (count world)
        width (count (first world))
        max-r (dec height)
        max-c (dec width)]
    [(-> r (max 0) (min max-r))
     (-> c (max 0) (min max-c))]))

(defn assign-exploration-flight!
  [update-game-map! world pos site-pos]
  (let [heading (fe/best-exploration-heading pos sortie-half-steps)
        drone? (< (rand) 0.05)
        mode (if drone? :drone :explore)
        [dr dc] heading
        endpoint (clamp-to-map-bounds
                  world
                  [(+ (first pos) (* sortie-half-steps dr))
                   (+ (second pos) (* sortie-half-steps dc))])]
    (update-game-map! update-in (conj pos :contents)
                      assoc :flight-mode mode
                      :explore-origin site-pos
                      :explore-heading heading
                      :explore-steps-remaining sortie-half-steps
                      :flight-target-site endpoint
                      :flight-origin-site site-pos)))

(defn ensure-flight-target!
  [current-world update-game-map! read-runtime-state pos]
  (let [world (current-world)
        unit (get-in world (conj pos :contents))]
    (when (and unit (nil? (:flight-mode unit)) (nil? (:flight-target-site unit)))
      (when-let [site-pos (current-refueling-site world pos)]
        (update-game-map! assoc-in (conj pos :contents :fuel) config/fighter-fuel)
        (if (>= (rand) 0.5)
          (assign-regular-leg! update-game-map! world pos site-pos read-runtime-state)
          (assign-exploration-flight! update-game-map! world pos site-pos))))))

(defn at-flight-target?
  [current-world pos target]
  (let [target-cell (get-in (current-world) target)]
    (or (= pos target)
        (and (= :carrier (get-in target-cell [:contents :type]))
             (<= (fm/distance-to pos target) 1)))))

(defn handle-arrival!
  [current-world update-game-map! read-runtime-state write-runtime-state! pos unit]
  (let [target (:flight-target-site unit)
        origin (:flight-origin-site unit)]
    (when (and origin (not= origin target))
      (write-runtime-state! :fighter-leg-records
                            (assoc (or (read-runtime-state :fighter-leg-records) {})
                                   #{origin target}
                                   {:last-flown (or (read-runtime-state :round-number) 0)})))
    (update-game-map! assoc-in (conj pos :contents :fuel) config/fighter-fuel)
    (let [new-target (choose-leg (current-world) read-runtime-state target)]
      (update-game-map! update-in (conj pos :contents)
                        #(-> %
                             (dissoc :explore-origin :explore-heading :explore-steps-remaining :flight-mode)
                             (assoc :flight-target-site new-target :flight-origin-site target))))
    {:pos pos :hops 1}))
