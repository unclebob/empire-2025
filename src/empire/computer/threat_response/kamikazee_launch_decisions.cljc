(ns empire.computer.threat-response.kamikazee-launch-decisions)

(defn airport-kamikazee-ready?
  [cell]
  (pos? (:awake-kamikazee-fighters cell 0)))

(defn build-launched-fighter
  [fighter-fuel major-target targets plan]
  {:type :fighter
   :owner :computer
   :mode :awake
   :hits 1
   :fuel fighter-fuel
   :major-invasion true
   :kamikazee true
   :major-invasion-target major-target
   :kamikazee-targets targets
   :kamikazee-route (:route plan)
   :kamikazee-terminal-site (:terminal-site plan)
   :kamikazee-stage (if (seq (:route plan)) :route :hunt)})

(defn launch-decision
  [{:keys [current-city-capacity? next-route-city-capacity? next-route-city launch-pos major-target targets plan fighter-fuel]}]
  (when (and launch-pos
             current-city-capacity?
             (or (nil? next-route-city)
                 next-route-city-capacity?))
    {:action :launch
     :launch-pos launch-pos
     :fighter (build-launched-fighter fighter-fuel major-target targets plan)}))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:41:28.218765-05:00", :module-hash "1134774238", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "1985685086"} {:id "defn/airport-kamikazee-ready?", :kind "defn", :line 3, :end-line 5, :hash "-972131625"} {:id "defn/build-launched-fighter", :kind "defn", :line 7, :end-line 20, :hash "1371288469"} {:id "defn/launch-decision", :kind "defn", :line 22, :end-line 30, :hash "1556357406"}]}
;; clj-mutate-manifest-end
