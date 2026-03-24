(ns empire.computer.army.coastal-invasion-decisions)

(defn resolve-coast-target
  [cached-target computed-target]
  (or cached-target computed-target))

(defn retry-repath-now?
  [current-round retry-at]
  (or (nil? retry-at) (<= retry-at (or current-round 0))))

(defn coast-step-action
  [{:keys [pos target lake-retask? cheap-step? local-step? move-step? repath-step?]}]
  (cond
    (= pos target) {:action :settle}
    lake-retask? (if cheap-step?
                   {:action :cheap-step :target cheap-step?}
                   {:action :settle})
    local-step? {:action :local-step :target local-step?}
    move-step? {:action :move :target move-step?}
    repath-step? {:action :repath :target repath-step?}
    :else nil))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T16:52:39.89357-05:00", :module-hash "-1675407646", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "762304692"} {:id "defn/resolve-coast-target", :kind "defn", :line 3, :end-line 5, :hash "20400912"} {:id "defn/retry-repath-now?", :kind "defn", :line 7, :end-line 9, :hash "1521633072"} {:id "defn/coast-step-action", :kind "defn", :line 11, :end-line 20, :hash "758810844"}]}
;; clj-mutate-manifest-end
