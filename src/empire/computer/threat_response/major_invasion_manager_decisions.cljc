(ns empire.computer.threat-response.major-invasion-manager-decisions)

(defn rebuild-routing?
  [previous-city-count current-city-count]
  (or (nil? previous-city-count)
      (> current-city-count previous-city-count)))

(defn should-record-detection?
  [{:keys [active? nearby-existing?]}]
  (or (not active?)
      (not nearby-existing?)))

(defn should-review-deferred?
  [{:keys [decision failure-reason next-review-round current-round]}]
  (and (= :deferred decision)
       (#{:no-sea-path :insufficient-resources :unsustainable-losses}
        failure-reason)
       (number? next-review-round)
       (>= current-round next-review-round)))

(defn invasion-start-update
  [{:keys [decision failure-reason sea-reachable-detection-points next-review-round]}]
  (if (= :ready decision)
    {:active? true
     :decision :ready
     :failure-reason nil
     :next-review-round nil
     :first-landing-round nil
     :sea-reachable-detection-points sea-reachable-detection-points}
    {:active? false
     :decision :deferred
     :failure-reason failure-reason
     :next-review-round next-review-round
     :first-landing-round nil
     :kamikazee-routing-city-count nil
     :sea-reachable-detection-points sea-reachable-detection-points}))

(defn round-start-actions
  [{:keys [active? review-deferred? failure-reason]}]
  {:refresh-active? active?
   :review-deferred? review-deferred?
   :force-patrol-exploration? (= :no-sea-path failure-reason)})

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T11:26:12.717601-05:00", :module-hash "-855683432", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "210273764"} {:id "defn/rebuild-routing?", :kind "defn", :line 3, :end-line 6, :hash "1285485113"} {:id "defn/should-record-detection?", :kind "defn", :line 8, :end-line 11, :hash "1257358420"} {:id "defn/should-review-deferred?", :kind "defn", :line 13, :end-line 19, :hash "-1330267280"} {:id "defn/invasion-start-update", :kind "defn", :line 21, :end-line 36, :hash "-656557223"} {:id "defn/round-start-actions", :kind "defn", :line 38, :end-line 42, :hash "1198327900"}]}
;; clj-mutate-manifest-end
