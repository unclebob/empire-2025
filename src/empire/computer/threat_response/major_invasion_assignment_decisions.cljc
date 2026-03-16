(ns empire.computer.threat-response.major-invasion-assignment-decisions)

(defn fighter-assignment
  [{:keys [major-target targets plan]}]
  {:major-invasion true
   :kamikazee true
   :major-invasion-target major-target
   :kamikazee-targets targets
   :kamikazee-route (:route plan)
   :kamikazee-terminal-site (:terminal-site plan)
   :kamikazee-stage (if (seq (:route plan)) :route :hunt)
   :clear-keys [:kamikazee-wait-site
                :kamikazee-hunt-resume-pos
                :kamikazee-trail
                :threat-mission
                :threat-center
                :threat-radius
                :threat-rounds-left]})

(defn carrier-assignment
  [{:keys [support-target ship-target]}]
  (if support-target
    {:major-invasion true
     :mode :sentry
     :major-invasion-target support-target}
    {:major-invasion true
     :major-invasion-target ship-target}))

(defn ship-assignment
  [ship-target]
  {:major-invasion true
   :major-invasion-target ship-target})

(defn army-coast-assignment
  [target]
  (cond-> {:mode :move-to-coast-for-invasion}
    target (assoc :coast-target target)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T08:17:03.459569-05:00", :module-hash "1835854174", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-1490583764"} {:id "defn/fighter-assignment", :kind "defn", :line 3, :end-line 18, :hash "-1306102944"} {:id "defn/carrier-assignment", :kind "defn", :line 20, :end-line 27, :hash "363335845"} {:id "defn/ship-assignment", :kind "defn", :line 29, :end-line 32, :hash "70971833"} {:id "defn/army-coast-assignment", :kind "defn", :line 34, :end-line 37, :hash "-1725032098"}]}
;; clj-mutate-manifest-end
