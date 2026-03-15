(ns empire.computer.transport-decisions)

(defn load-for-invasion-action
  [{:keys [has-armies? in-unload-zone? timed-out? nearby-unloadable-land?]}]
  (cond
    (and has-armies? in-unload-zone?) :unload
    (and has-armies? timed-out?) :sail
    (and has-armies? nearby-unloadable-land?) :sail
    (and (not has-armies?) timed-out?) :revert-loading
    :else nil))

(defn loading-mission-action
  [{:keys [should-start-sailing? loading-stale?]}]
  (cond
    should-start-sailing? :start-sailing
    loading-stale? :handle-stale
    :else :crawl))

(defn unloading-mission-action
  [{:keys [army-count]}]
  (if (zero? army-count) :transition-to-loading :continue-unloading))

(defn transport-process-action
  [{:keys [transport? computer-owned? random-walk?]}]
  (cond
    (not transport?) :ignore
    (not computer-owned?) :ignore
    random-walk? :random-walk
    :else :active))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T15:51:08.678597-05:00", :module-hash "-1655359473", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-589390891"} {:id "defn/load-for-invasion-action", :kind "defn", :line 3, :end-line 10, :hash "1286902489"} {:id "defn/loading-mission-action", :kind "defn", :line 12, :end-line 17, :hash "-328141758"} {:id "defn/unloading-mission-action", :kind "defn", :line 19, :end-line 21, :hash "-2113268860"} {:id "defn/transport-process-action", :kind "defn", :line 23, :end-line 29, :hash "1846781887"}]}
;; clj-mutate-manifest-end
