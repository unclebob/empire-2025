(ns empire.computer.transport.mission-handlers.loading-mission
  (:require [empire.computer.transport.loading :as loading]))

(defn- finish-planned-loading
  [start-sailing transition-to-loading pos transport' empty?]
  (if empty?
    (transition-to-loading pos)
    (start-sailing pos transport')))

(defn- stale-loading-action
  [start-sailing transition-to-loading pos transport' army-count' empty?]
  (if (and (<= 2 army-count') (<= army-count' 3) (not empty?))
    (start-sailing pos transport')
    (transition-to-loading pos)))

(defn- planned-loading-action
  [start-sailing transition-to-loading pos transport']
  (let [army-count' (:army-count transport' 0)
        empty? (zero? army-count')]
    (cond
      (or (>= army-count' 6) (loading/manifest-empty? transport'))
      (finish-planned-loading start-sailing transition-to-loading pos transport' empty?)

      (loading/loading-stale? transport')
      (stale-loading-action start-sailing transition-to-loading pos transport' army-count' empty?))))

(defn process-loading-mission
  [{:keys [current-world
           read-computer-map
           load-adjacent-armies
           start-sailing
           transition-to-loading]}
   pos]
  (load-adjacent-armies pos)
  (let [read-map (or read-computer-map current-world)
        transport' (get-in (read-map) (conj pos :contents))]
    (if (loading/planned-loading? transport')
      (planned-loading-action start-sailing transition-to-loading pos transport')
      (transition-to-loading pos))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:04:50.622194-05:00", :module-hash "-1401451353", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-1346370739"} {:id "defn-/finish-planned-loading", :kind "defn-", :line 4, :end-line nil, :hash "461147455"} {:id "defn-/stale-loading-action", :kind "defn-", :line 10, :end-line nil, :hash "942269700"} {:id "defn-/planned-loading-action", :kind "defn-", :line 16, :end-line nil, :hash "2108279009"} {:id "defn/process-loading-mission", :kind "defn", :line 27, :end-line nil, :hash "-1524428279"}]}
;; clj-mutate-manifest-end
