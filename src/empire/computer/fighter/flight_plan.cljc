(ns empire.computer.fighter.flight-plan
  (:require [empire.computer.ship :as ship]
            [empire.config.core :as config]
            [empire.computer.fighter.flight-decisions :as decisions]))

(defn assign-exploration-flight!
  [update-game-map! world pos site-pos]
  (when-let [{:keys [mode origin heading steps-remaining target landing-site]}
             (decisions/exploration-flight-action world
                                                  (ship/find-refueling-sites)
                                                  pos
                                                  site-pos)]
    (update-game-map! update-in (conj pos :contents)
                      assoc :flight-mode mode
                      :explore-origin origin
                      :explore-heading heading
                      :explore-steps-remaining steps-remaining
                      :explore-landing-site landing-site
                      :flight-target-site target
                      :flight-origin-site origin)))

(defn ensure-flight-target!
  [current-world update-game-map! read-runtime-state pos]
  (let [world (current-world)
        unit (get-in world (conj pos :contents))
        decision (decisions/ensure-flight-target-action world
                                                        (ship/find-refueling-sites)
                                                        (or (read-runtime-state :fighter-leg-records) {})
                                                        pos
                                                        unit
                                                        (rand)
                                                        (rand))]
    (when decision
      (update-game-map! assoc-in (conj pos :contents :fuel) config/fighter-fuel)
      (case (:action decision)
        :assign-regular-leg
        (update-game-map! update-in (conj pos :contents)
                          assoc :flight-target-site (:target decision)
                          :flight-origin-site (:origin decision)
                          :flight-mode :regular)

        :assign-exploration-flight
        (update-game-map! update-in (conj pos :contents)
                          assoc :flight-mode (:mode decision)
                          :explore-origin (:origin decision)
                          :explore-heading (:heading decision)
                          :explore-steps-remaining (:steps-remaining decision)
                          :explore-landing-site (:landing-site decision)
                          :flight-target-site (:target decision)
                          :flight-origin-site (:origin decision))
        nil))))

(defn at-flight-target?
  [current-world pos target]
  (decisions/at-flight-target? (current-world) pos target))

(defn- record-arrival-leg!
  [read-runtime-state write-runtime-state! decision]
  (when-let [leg-record (:leg-record decision)]
    (write-runtime-state! :fighter-leg-records
                          (assoc (or (read-runtime-state :fighter-leg-records) {})
                                 (:leg-key leg-record)
                                 {:last-flown (:last-flown leg-record)}))))

(defn- apply-arrival-next-action
  [fighter decision]
  (let [base (-> fighter
                 (dissoc :explore-origin :explore-heading :explore-steps-remaining
                         :explore-landing-site :flight-mode)
                 (assoc :flight-origin-site (:target decision)))
        next-action (:next-action decision)]
    (case (:action next-action)
      :assign-regular-leg
      (assoc base
             :flight-target-site (:target next-action)
             :flight-mode :regular)

      :assign-exploration-flight
      (assoc base
             :flight-mode (:mode next-action)
             :explore-origin (:origin next-action)
             :explore-heading (:heading next-action)
             :explore-steps-remaining (:steps-remaining next-action)
             :explore-landing-site (:landing-site next-action)
             :flight-target-site (:target next-action)
             :flight-origin-site (:origin next-action))

      (dissoc base :flight-target-site))))

(defn handle-arrival!
  [current-world update-game-map! read-runtime-state write-runtime-state! pos unit]
  (let [decision (decisions/arrival-action (current-world)
                                           (ship/find-refueling-sites)
                                           (or (read-runtime-state :fighter-leg-records) {})
                                           (or (read-runtime-state :round-number) 0)
                                           pos
                                           unit
                                           (rand))]
    (record-arrival-leg! read-runtime-state write-runtime-state! decision)
    (update-game-map! assoc-in (conj pos :contents :fuel) config/fighter-fuel)
    (update-game-map! update-in (conj pos :contents)
                      #(apply-arrival-next-action % decision))
    {:pos pos :hops (:hops decision)}))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:57:43.101829-05:00", :module-hash "-745766345", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "990494705"} {:id "defn/assign-exploration-flight!", :kind "defn", :line 6, :end-line 20, :hash "2051787068"} {:id "defn/ensure-flight-target!", :kind "defn", :line 22, :end-line 51, :hash "-2126986128"} {:id "defn/at-flight-target?", :kind "defn", :line 53, :end-line 55, :hash "-942003409"} {:id "defn/handle-arrival!", :kind "defn", :line 57, :end-line 96, :hash "312930749"}]}
;; clj-mutate-manifest-end
