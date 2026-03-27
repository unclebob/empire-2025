(ns empire.computer.shared.transport-query
  (:require [empire.computer.shared.grid :as grid]
            [empire.computer.shared.world-query :as world-query]
            [empire.state.api :as sa]))

(defn- transport-compatible?
  "Returns true if the transport doesn't have a matching unload-event-id as the army.
   An army should not board the same transport that unloaded it."
  [transport-unit army-unload-event-id]
  (or (nil? army-unload-event-id)
      (nil? (:unload-event-id transport-unit))
      (not= (:unload-event-id transport-unit) army-unload-event-id)))

(defn- loading-transport?
  [unit army-unload-event-id]
  (and unit
       (= :computer (:owner unit))
       (= :transport (:type unit))
       (= :loading (:transport-mission unit))
       (< (:army-count unit 0) 6)
       (transport-compatible? unit army-unload-event-id)))

(def ^:private loading-transport-cache (atom nil))

(defn clear-loading-transport-cache! [] (reset! loading-transport-cache nil))

(defn scan-loading-transports
  [world army-unload-event-id]
  (vec (for [i (range (count world))
             j (range (count (first world)))
             :let [unit (:contents (get-in world [i j]))]
             :when (loading-transport? unit army-unload-event-id)]
         [i j])))

(defn- cached-loading-transports [army-unload-event-id]
  (or @loading-transport-cache
      (let [result (scan-loading-transports (sa/read-state :computer-map) army-unload-event-id)]
        (reset! loading-transport-cache result)
        result)))

(defn find-loading-transport
  ([] (find-loading-transport nil))
  ([army-unload-event-id]
   (first (cached-loading-transports army-unload-event-id))))

(defn find-nearby-loading-transport
  ([pos max-distance]
   (find-nearby-loading-transport pos max-distance nil))
  ([pos max-distance army-unload-event-id]
   (->> (cached-loading-transports army-unload-event-id)
        (filter #(<= (grid/chebyshev-distance pos %) max-distance))
        (sort-by #(vector (grid/chebyshev-distance pos %) %))
        first)))

(defn find-adjacent-loading-transport
  ([pos]
   (find-adjacent-loading-transport pos nil))
  ([pos army-unload-event-id]
   (let [world (sa/read-state :computer-map)]
     (first (filter (fn [neighbor]
                      (loading-transport? (:contents (get-in world neighbor)) army-unload-event-id))
                    (world-query/get-neighbors pos))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:19:04.494665-05:00", :module-hash "1717639348", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "-1651783802"} {:id "defn-/transport-compatible?", :kind "defn-", :line 6, :end-line 12, :hash "822381424"} {:id "defn-/loading-transport?", :kind "defn-", :line 14, :end-line 21, :hash "556430484"} {:id "def/loading-transport-cache", :kind "def", :line 23, :end-line 23, :hash "-1576138189"} {:id "defn/clear-loading-transport-cache!", :kind "defn", :line 25, :end-line 25, :hash "118935798"} {:id "defn/scan-loading-transports", :kind "defn", :line 27, :end-line 33, :hash "1966174029"} {:id "defn-/cached-loading-transports", :kind "defn-", :line 35, :end-line 39, :hash "-697172424"} {:id "defn/find-loading-transport", :kind "defn", :line 41, :end-line 44, :hash "986697854"} {:id "defn/find-nearby-loading-transport", :kind "defn", :line 46, :end-line 53, :hash "1246609103"} {:id "defn/find-adjacent-loading-transport", :kind "defn", :line 55, :end-line 62, :hash "-105860858"}]}
;; clj-mutate-manifest-end
