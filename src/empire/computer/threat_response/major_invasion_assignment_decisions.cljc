(ns empire.computer.threat-response.major-invasion-assignment-decisions)

(defn- ship-assignment-action
  [type major-invasion-ship-types]
  (when (major-invasion-ship-types type)
    (if (= :carrier type) :carrier :ship)))

(defn assignment-action
  [{:keys [type major-invasion-ship-types]}]
  (or (when (= :fighter type) :fighter)
      (ship-assignment-action type major-invasion-ship-types)
      (when (= :transport type) :transport)
      (when (= :army type) :army)))

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
;; {:version 1, :tested-at "2026-09-01T15:57:27.626021-05:00", :module-hash "905818473", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-1490583764"} {:id "defn-/ship-assignment-action", :kind "defn-", :line 3, :end-line nil, :hash "126056229"} {:id "defn/assignment-action", :kind "defn", :line 8, :end-line nil, :hash "221134837"} {:id "defn/fighter-assignment", :kind "defn", :line 15, :end-line nil, :hash "-1306102944"} {:id "defn/carrier-assignment", :kind "defn", :line 32, :end-line nil, :hash "363335845"} {:id "defn/ship-assignment", :kind "defn", :line 41, :end-line nil, :hash "70971833"} {:id "defn/army-coast-assignment", :kind "defn", :line 46, :end-line nil, :hash "-1725032098"}]}
;; clj-mutate-manifest-end
