(ns empire.computer.threat-response.decisions)

(defn detection-action
  [{:keys [record-army-target? trigger]}]
  {:record-army-target? record-army-target?
   :trigger trigger})

(defn prepare-transport-action
  [{:keys [major-invasion-active? unit]}]
  (when (and major-invasion-active?
             (= :transport (:type unit)))
    :prepare-transport))

(defn fighter-threat-round-action
  [unit]
  (when (or (= :fighter-sweep (:threat-mission unit))
            (= :country-defense (:threat-mission unit))
            (:major-invasion unit))
    (if (:kamikazee unit)
      :kamikazee
      :standard)))

(defn ship-threat-action
  [{:keys [ship-type major-invasion? fixed-carrier?]}]
  (cond
    (and (= :carrier ship-type) major-invasion? fixed-carrier?) :hold
    :else :process))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:37:10.138839-05:00", :module-hash "-987471815", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-200427073"} {:id "defn/detection-action", :kind "defn", :line 3, :end-line 6, :hash "-1647122110"} {:id "defn/prepare-transport-action", :kind "defn", :line 8, :end-line 12, :hash "1502879100"} {:id "defn/fighter-threat-round-action", :kind "defn", :line 14, :end-line 21, :hash "808068233"} {:id "defn/ship-threat-action", :kind "defn", :line 23, :end-line 27, :hash "-995134528"}]}
;; clj-mutate-manifest-end
