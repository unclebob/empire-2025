(ns empire.computer.threat-response.kamikazee-target-decisions)

(defn trim-dead-army-targets-state
  [world state]
  (update state :kamikazee-army-targets
          (fn [targets]
            (->> targets
                 (filter (fn [{:keys [pos]}]
                           (let [unit (get-in world (conj pos :contents))]
                             (and unit
                                  (= :player (:owner unit))
                                  (= :army (:type unit))))))
                 vec))))

(defn fighter-target-writes
  [world targets]
  (for [i (range (count world))
        j (range (count (first world)))
        :let [unit (get-in world [i j :contents])]
        :when (and unit
                   (= :computer (:owner unit))
                   (= :fighter (:type unit))
                   (:kamikazee unit))]
    {:pos [i j]
     :targets targets}))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T12:48:44.534134-05:00", :module-hash "-175981704", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "1920677497"} {:id "defn/trim-dead-army-targets-state", :kind "defn", :line 3, :end-line 13, :hash "208996487"} {:id "defn/fighter-target-writes", :kind "defn", :line 15, :end-line 25, :hash "-1952127639"}]}
;; clj-mutate-manifest-end
