(ns empire.computer.fighter.flight-plan
  (:require [empire.computer.ship :as ship]
            [empire.config.core :as config]
            [empire.game.loop.profiling :as profiling]
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

(defn handle-arrival!
  [current-world update-game-map! read-runtime-state write-runtime-state! pos unit]
  (let [decision (profiling/time-phase :process-computer/fighter-arrival-decision
                                       (fn []
                                         (decisions/arrival-action (current-world)
                                                                   (ship/find-refueling-sites)
                                                                   (or (read-runtime-state :fighter-leg-records) {})
                                                                   (or (read-runtime-state :round-number) 0)
                                                                   pos
                                                                   unit
                                                                   (rand))))]
    (profiling/time-phase :process-computer/fighter-arrival-writeback
                          (fn []
                            (when-let [leg-record (:leg-record decision)]
                              (write-runtime-state! :fighter-leg-records
                                                    (assoc (or (read-runtime-state :fighter-leg-records) {})
                                                           (:leg-key leg-record)
                                                           {:last-flown (:last-flown leg-record)})))
                            (update-game-map! assoc-in (conj pos :contents :fuel) config/fighter-fuel)
                            (update-game-map! update-in (conj pos :contents)
                                              (fn [fighter]
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

                                                    (dissoc base :flight-target-site)))))))
    {:pos pos :hops (:hops decision)}))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T16:29:00.222716-05:00", :module-hash "-22093989", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "-815924111"} {:id "defn/assign-exploration-flight!", :kind "defn", :line 6, :end-line 16, :hash "-1277964829"} {:id "defn/ensure-flight-target!", :kind "defn", :line 18, :end-line 46, :hash "1009996313"} {:id "defn/at-flight-target?", :kind "defn", :line 48, :end-line 50, :hash "-942003409"} {:id "defn/handle-arrival!", :kind "defn", :line 52, :end-line 71, :hash "1478044019"}]}
;; clj-mutate-manifest-end
