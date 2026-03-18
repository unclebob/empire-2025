(ns empire.computer.army.assignment
  "Attack-target assignment for computer armies."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.army.assignment-decisions :as decisions]
            [empire.computer.land-objectives :as land-objectives]))

(defn assign-city-attacks
  "Scans computer-map for visible free/player cities and assigns up to 6 closest armies each."
  []
  (let [cities (decisions/visible-target-cities (sa/read-state :computer-map))
        armies (decisions/assignable-armies (sa/read-state :computer-map))
        assignments (decisions/assignment-updates cities
                                                  armies
                                                  contains?
                                                  land-objectives/flood-fill-continent
                                                  core/distance)]
    (doseq [{:keys [pos target]} assignments]
      (sa/update-world! assoc-in (conj pos :contents :attack-target) target))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T14:21:45.748132-05:00", :module-hash "-419708176", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "-72483662"} {:id "defn/assign-city-attacks", :kind "defn", :line 8, :end-line 19, :hash "1116748109"}]}
;; clj-mutate-manifest-end
