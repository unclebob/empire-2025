(ns empire.player.production-decisions
  (:require [empire.config.core :as config]))

(defn create-base-unit
  [item owner]
  {:type item :hits (config/item-hits item) :mode :awake :owner owner})

(defn apply-unit-type-attributes
  [unit item]
  (cond-> unit
    (= item :fighter) (assoc :fuel config/fighter-fuel)
    (= item :satellite) (assoc :turns-remaining config/satellite-turns)))

(defn- army-movement-order
  [marching-orders]
  (cond
    (= marching-orders :lookaround) :army-explore
    marching-orders :army-move
    :else nil))

(defn- movement-order-action
  [item marching-orders flight-path]
  (cond
    (= item :army) (army-movement-order marching-orders)
    (and (= item :fighter) flight-path) :fighter-move))

(def ^:private movement-order-handlers
  {:army-explore (fn [unit _marching-orders _flight-path]
                   (assoc unit :mode :explore :explore-steps 50))
   :army-move (fn [unit marching-orders _flight-path]
                (assoc unit :mode :moving :target marching-orders))
   :fighter-move (fn [unit _marching-orders flight-path]
                   (assoc unit :mode :moving :target flight-path))})

(defn apply-movement-orders
  [unit item marching-orders flight-path]
  (if-let [handler (movement-order-handlers (movement-order-action item marching-orders flight-path))]
    (handler unit marching-orders flight-path)
    unit))

(defn build-produced-unit
  [item owner marching-orders flight-path]
  (when (not= item :fighter)
    (-> (create-base-unit item owner)
        (apply-unit-type-attributes item)
        (apply-movement-orders item marching-orders flight-path))))

(defn city-production-step
  [cell prod]
  (cond
    (and (:contents cell) (not= (:item prod) :fighter)) {:action :blocked}
    :else
    (let [item (:item prod)
          remaining (dec (:remaining-rounds prod))]
      (if (zero? remaining)
        {:action :complete :item item}
        {:action :decrement :production (assoc prod :remaining-rounds remaining)}))))

(defn production-complete-action
  [owner coords prod]
  (if (= owner :computer)
    {:action :clear-production}
    {:action :reset-production
     :coords coords
     :production (assoc prod :remaining-rounds (config/item-cost (:item prod)))}))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:28:25.540031-05:00", :module-hash "1177753980", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-122510373"} {:id "defn/create-base-unit", :kind "defn", :line 4, :end-line 6, :hash "221992479"} {:id "defn/apply-unit-type-attributes", :kind "defn", :line 8, :end-line 12, :hash "-884774356"} {:id "defn/apply-movement-orders", :kind "defn", :line 14, :end-line 26, :hash "-173663708"} {:id "defn/build-produced-unit", :kind "defn", :line 28, :end-line 32, :hash "-1450807009"} {:id "defn/city-production-step", :kind "defn", :line 34, :end-line 43, :hash "1523504390"} {:id "defn/production-complete-action", :kind "defn", :line 45, :end-line 51, :hash "1011809885"}]}
;; clj-mutate-manifest-end
