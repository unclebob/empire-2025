(ns empire.computer.transport.decisions)

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
  [{:keys [should-start-sailing?]}]
  (if should-start-sailing? :start-sailing :crawl))

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
;; {:version 1, :tested-at "2026-03-27T00:03:14.651569-05:00", :module-hash "-1061558171", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-1563966574"} {:id "defn-/armed-invasion-action", :kind "defn-", :line 3, :end-line 8, :hash "-240659296"} {:id "defn-/empty-invasion-action", :kind "defn-", :line 10, :end-line 13, :hash "1572524468"} {:id "defn/load-for-invasion-action", :kind "defn", :line 15, :end-line 19, :hash "13805833"} {:id "defn/loading-mission-action", :kind "defn", :line 21, :end-line 23, :hash "-1432385325"} {:id "defn/unloading-mission-action", :kind "defn", :line 25, :end-line 27, :hash "-2113268860"} {:id "defn/transport-process-action", :kind "defn", :line 29, :end-line 35, :hash "1846781887"}]}
;; clj-mutate-manifest-end
