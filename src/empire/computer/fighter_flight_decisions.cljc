(ns empire.computer.fighter-flight-decisions
  (:require [empire.computer.fighter-exploration :as fe]
            [empire.computer.fighter-movement :as fm]
            [empire.config.core :as config]))

(def sortie-half-steps 16)

(def neighbor-offsets
  [[-1 -1] [-1 0] [-1 1]
   [0 -1]          [0 1]
   [1 -1]  [1 0]  [1 1]])

(defn neighbors
  [world pos]
  (for [[dr dc] neighbor-offsets
        :let [n [(+ (first pos) dr) (+ (second pos) dc)]]
        :when (and (<= 0 (first n)) (< (first n) (count world))
                   (<= 0 (second n)) (< (second n) (count (first world)))
                   (some? (get-in world n)))]
    n))

(defn current-refueling-site
  [world pos]
  (let [cell (get-in world pos)]
    (cond
      (and (= :city (:type cell)) (= :computer (:city-status cell))) pos
      :else
      (first (filter (fn [n]
                       (let [ncell (get-in world n)]
                         (and (= :carrier (get-in ncell [:contents :type]))
                              (= :computer (get-in ncell [:contents :owner]))
                              (= :holding (get-in ncell [:contents :carrier-mode])))))
                     (neighbors world pos))))))

(defn choose-leg
  [world sites leg-records current-site]
  (let [reachable (filter #(and (not= % current-site)
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
        scored (map (fn [target]
                      [target (:last-flown (get leg-records #{current-site target}) -1)])
                    candidate-targets)]
    (when (seq scored)
      (ffirst (sort-by second scored)))))

(defn clamp-to-map-bounds
  [world [r c]]
  (let [height (count world)
        width (count (first world))
        max-r (dec height)
        max-c (dec width)]
    [(-> r (max 0) (min max-r))
     (-> c (max 0) (min max-c))]))

(defn regular-leg-action
  [world sites leg-records pos site-pos]
  (when-let [target (choose-leg world sites leg-records site-pos)]
    {:action :assign-regular-leg
     :pos pos
     :target target
     :origin site-pos}))

(defn exploration-flight-action
  [world pos site-pos drone-roll]
  (let [heading (fe/best-exploration-heading pos sortie-half-steps)
        drone? (< drone-roll 0.05)
        mode (if drone? :drone :explore)
        [dr dc] heading
        endpoint (clamp-to-map-bounds
                  world
                  [(+ (first pos) (* sortie-half-steps dr))
                   (+ (second pos) (* sortie-half-steps dc))])]
    {:action :assign-exploration-flight
     :pos pos
     :mode mode
     :origin site-pos
     :heading heading
     :steps-remaining sortie-half-steps
     :target endpoint}))

(defn ensure-flight-target-action
  [world sites leg-records pos unit leg-roll drone-roll]
  (when (and unit (nil? (:flight-mode unit)) (nil? (:flight-target-site unit)))
    (when-let [site-pos (current-refueling-site world pos)]
      (if (>= leg-roll 0.5)
        (regular-leg-action world sites leg-records pos site-pos)
        (exploration-flight-action world pos site-pos drone-roll)))))

(defn at-flight-target?
  [world pos target]
  (let [target-cell (get-in world target)]
    (or (= pos target)
        (and (= :carrier (get-in target-cell [:contents :type]))
             (<= (fm/distance-to pos target) 1)))))

(defn arrival-action
  [world sites leg-records round-number pos unit]
  (let [target (:flight-target-site unit)
        origin (:flight-origin-site unit)
        new-target (choose-leg world sites leg-records target)
        leg-record (when (and origin (not= origin target))
                     {:leg-key #{origin target}
                      :last-flown round-number})]
    {:action :handle-arrival
     :pos pos
     :target target
     :new-target new-target
     :leg-record leg-record
     :hops 1}))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T16:22:18.317983-05:00", :module-hash "-2102160933", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "197921039"} {:id "def/sortie-half-steps", :kind "def", :line 6, :end-line 6, :hash "1596510764"} {:id "def/neighbor-offsets", :kind "def", :line 8, :end-line 11, :hash "-1254756339"} {:id "defn/neighbors", :kind "defn", :line 13, :end-line 20, :hash "741800172"} {:id "defn/current-refueling-site", :kind "defn", :line 22, :end-line 33, :hash "-409622893"} {:id "defn/choose-leg", :kind "defn", :line 35, :end-line 54, :hash "667890426"} {:id "defn/clamp-to-map-bounds", :kind "defn", :line 56, :end-line 63, :hash "1865164368"} {:id "defn/regular-leg-action", :kind "defn", :line 65, :end-line 71, :hash "1175566717"} {:id "defn/exploration-flight-action", :kind "defn", :line 73, :end-line 89, :hash "-1095327543"} {:id "defn/ensure-flight-target-action", :kind "defn", :line 91, :end-line 97, :hash "-964698914"} {:id "defn/at-flight-target?", :kind "defn", :line 99, :end-line 104, :hash "-1825324496"} {:id "defn/arrival-action", :kind "defn", :line 106, :end-line 119, :hash "249786359"}]}
;; clj-mutate-manifest-end
