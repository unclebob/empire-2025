(ns empire.computer.production.selection-decisions)

(defn country-production-choice
  [{:keys [transport army patrol-boat destroyer fighter]}]
  (or transport army patrol-boat destroyer fighter))

(defn global-production-choice
  [{:keys [carrier capital-ship satellite]}]
  (or carrier capital-ship satellite))

(defn production-choice
  [{:keys [override opening country-choice global-choice fallback-army?]}]
  (or override
      opening
      country-choice
      global-choice
      (when fallback-army? :army)))

(defn process-city-action
  [{:keys [reset-lake-production? current-production unit-type]}]
  {:reset-lake-production? reset-lake-production?
   :set-production? (and (nil? current-production) unit-type)
   :unit-type unit-type})

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:08:09.236386-05:00", :module-hash "-134449550", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-191882309"} {:id "defn/country-production-choice", :kind "defn", :line 3, :end-line 5, :hash "1709241974"} {:id "defn/global-production-choice", :kind "defn", :line 7, :end-line 9, :hash "-1375913643"} {:id "defn/production-choice", :kind "defn", :line 11, :end-line 17, :hash "1990763068"} {:id "defn/process-city-action", :kind "defn", :line 19, :end-line 23, :hash "-741549430"}]}
;; clj-mutate-manifest-end
