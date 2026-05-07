(ns empire.computer.army.coastal-invasion-decisions)

(defn resolve-coast-target
  [cached-target computed-target]
  (or cached-target computed-target))

(defn retry-repath-now?
  [current-round retry-at]
  (or (nil? retry-at) (<= retry-at (or current-round 0))))

(defn- lake-retask-action
  [cheap-step?]
  (if cheap-step?
    {:action :cheap-step :target cheap-step?}
    {:action :settle}))

(defn- first-target-action
  [candidates]
  (when-let [[action target] (first (filter (comp some? second) candidates))]
    {:action action :target target}))

(defn coast-step-action
  [{:keys [pos target lake-retask? cheap-step? local-step? move-step? repath-step?]}]
  (cond
    (= pos target) {:action :settle}
    lake-retask? (lake-retask-action cheap-step?)
    :else (first-target-action [[:local-step local-step?]
                                [:move move-step?]
                                [:repath repath-step?]])))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:21:37.943117-05:00", :module-hash "-430335997", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "762304692"} {:id "defn/resolve-coast-target", :kind "defn", :line 3, :end-line 5, :hash "20400912"} {:id "defn/retry-repath-now?", :kind "defn", :line 7, :end-line 9, :hash "1521633072"} {:id "defn/coast-step-action", :kind "defn", :line 11, :end-line 21, :hash "-1097113932"}]}
;; clj-mutate-manifest-end
