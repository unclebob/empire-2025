(ns empire.computer.transport-decisions)

(defn- armed-invasion-action
  [{:keys [in-unload-zone? timed-out? nearby-unloadable-land?]}]
  (cond
    in-unload-zone? :unload
    (or timed-out? nearby-unloadable-land?) :sail
    :else nil))

(defn- empty-invasion-action
  [{:keys [timed-out?]}]
  (when timed-out?
    :revert-loading))

(defn load-for-invasion-action
  [{:keys [has-armies?] :as state}]
  (if has-armies?
    (armed-invasion-action state)
    (empty-invasion-action state)))

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
;; {:version 1, :tested-at "2026-03-17T07:59:09.98278-05:00", :module-hash "-1883954645", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-589390891"} {:id "defn-/armed-invasion-action", :kind "defn-", :line 3, :end-line 8, :hash "-240659296"} {:id "defn-/empty-invasion-action", :kind "defn-", :line 10, :end-line 13, :hash "1572524468"} {:id "defn/load-for-invasion-action", :kind "defn", :line 15, :end-line 19, :hash "13805833"} {:id "defn/loading-mission-action", :kind "defn", :line 21, :end-line 26, :hash "-328141758"} {:id "defn/unloading-mission-action", :kind "defn", :line 28, :end-line 30, :hash "-2113268860"} {:id "defn/transport-process-action", :kind "defn", :line 32, :end-line 38, :hash "1846781887"}]}
;; clj-mutate-manifest-end
