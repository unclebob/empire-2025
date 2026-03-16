(ns empire.computer.transport-process-decisions)

(def mission-order
  [:invading
   :find-armies-for-invasion
   :load-for-invasion
   :land-locked
   :unloading
   :sailing
   :loading])

(defn transport-mission-action
  [{:keys [mission never-reload?]}]
  {:fix-idle? true
   :force-sailing? (and (= :loading (or mission :loading)) never-reload?)
   :mission (or mission :loading)})

(defn active-transport-action
  [{:keys [sentry? lake-handled?]}]
  (cond
    sentry? :skip
    lake-handled? :skip
    :else :dispatch))

(defn transport-process-action
  [{:keys [transport? computer-owned? random-walk?]}]
  (cond
    (not (and transport? computer-owned?)) nil
    random-walk? :random-walk
    :else :active))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T10:25:19.923419-05:00", :module-hash "-521919767", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "1704854845"} {:id "def/mission-order", :kind "def", :line 3, :end-line 10, :hash "983343553"} {:id "defn/transport-mission-action", :kind "defn", :line 12, :end-line 16, :hash "-1306383217"} {:id "defn/active-transport-action", :kind "defn", :line 18, :end-line 23, :hash "1094472312"} {:id "defn/transport-process-action", :kind "defn", :line 25, :end-line 30, :hash "819243811"}]}
;; clj-mutate-manifest-end
