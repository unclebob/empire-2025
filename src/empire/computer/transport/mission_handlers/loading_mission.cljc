(ns empire.computer.transport.mission-handlers.loading-mission
  (:require [empire.computer.transport.loading :as loading]))

(defn- planned-loading-action
  [start-sailing transition-to-loading pos transport']
  (let [army-count' (:army-count transport' 0)
        empty? (zero? army-count')]
    (cond
      (or (>= army-count' 6) (loading/manifest-empty? transport'))
      (if empty? (transition-to-loading pos) (start-sailing pos transport'))

      (loading/loading-stale? transport')
      (if (and (<= 2 army-count') (<= army-count' 3) (not empty?))
        (start-sailing pos transport')
        (transition-to-loading pos)))))

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
;; {:version 1, :tested-at "2026-03-27T09:46:55.879313-05:00", :module-hash "-1178768140", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-1346370739"} {:id "defn-/planned-loading-action", :kind "defn-", :line 4, :end-line 15, :hash "-1340207686"} {:id "defn/process-loading-mission", :kind "defn", :line 17, :end-line 29, :hash "-1524428279"}]}
;; clj-mutate-manifest-end
