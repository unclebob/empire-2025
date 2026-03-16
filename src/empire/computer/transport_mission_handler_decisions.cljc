(ns empire.computer.transport-mission-handler-decisions)

(defn find-armies-for-invasion-action
  [{:keys [army-count loadable-neighbor? reachable-path?]}]
  (cond
    (pos? army-count) :start-load-for-invasion
    loadable-neighbor? :start-load-for-invasion
    reachable-path? :follow-path
    :else :revert-loading))

(defn load-for-invasion-state
  [{:keys [army-count in-unload-zone? timed-out? nearby-unloadable-land?]}]
  {:has-armies? (pos? army-count)
   :in-unload-zone? in-unload-zone?
   :timed-out? timed-out?
   :nearby-unloadable-land? nearby-unloadable-land?})

(defn load-for-invasion-with-armies-action
  [{:keys [in-unload-zone? timed-out? nearby-unloadable-land?]}]
  (cond
    in-unload-zone? :unload
    timed-out? :sail
    nearby-unloadable-land? :sail
    :else :hold))

(defn load-for-invasion-without-armies-action
  [{:keys [timed-out?]}]
  (if timed-out? :revert-loading :hold))

(defn load-for-invasion-action
  [{:keys [has-armies? in-unload-zone? timed-out? nearby-unloadable-land?]}]
  (if has-armies?
    (load-for-invasion-with-armies-action
     {:in-unload-zone? in-unload-zone?
      :timed-out? timed-out?
      :nearby-unloadable-land? nearby-unloadable-land?})
    (load-for-invasion-without-armies-action {:timed-out? timed-out?})))

(defn unloading-with-armies-action
  [{:keys [nearby-unloadable-land?]}]
  (if nearby-unloadable-land?
    :crawl-and-unload
    :start-sailing))

(defn lake-transport-action
  [{:keys [sentry? lake-locked? has-armies?]}]
  (cond
    sentry? :already-handled
    lake-locked? (if has-armies? :land-locked-unload :park-empty)
    :else nil))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T11:18:41.149449-05:00", :module-hash "-1126214187", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-1314828220"} {:id "defn/find-armies-for-invasion-action", :kind "defn", :line 3, :end-line 9, :hash "-86003404"} {:id "defn/load-for-invasion-state", :kind "defn", :line 11, :end-line 16, :hash "261152371"} {:id "defn/load-for-invasion-with-armies-action", :kind "defn", :line 18, :end-line 24, :hash "665841594"} {:id "defn/load-for-invasion-without-armies-action", :kind "defn", :line 26, :end-line 28, :hash "638709077"} {:id "defn/load-for-invasion-action", :kind "defn", :line 30, :end-line 37, :hash "-1817053707"} {:id "defn/unloading-with-armies-action", :kind "defn", :line 39, :end-line 43, :hash "-1639959746"} {:id "defn/lake-transport-action", :kind "defn", :line 45, :end-line 50, :hash "258897140"}]}
;; clj-mutate-manifest-end
