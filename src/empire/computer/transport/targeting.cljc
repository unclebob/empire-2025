(ns empire.computer.transport.targeting
  "Transport target selection — finding unload targets and pickup continents."
  (:require [empire.state.api :as sa]
            [empire.computer.shared.grid :as grid]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.shared.world-query :as world-query]
            [empire.computer.transport.targeting-decisions :as decisions]))

(defn- score-target-city
  "Score a target city for a transport. Lower = more attractive.
   Factors: distance, continent attackable cities, computer presence."
  [transport-pos target-city]
  (let [dist (grid/distance transport-pos target-city)
        target-continent (land-objectives/flood-fill-continent target-city)
        scan (when target-continent (land-objectives/scan-continent target-continent))
        attackable (+ (:player-cities scan 0) (:free-cities scan 0))
        continent-factor (if (pos? attackable)
                           (/ 100.0 attackable)
                           100.0)
        presence-penalty (if (pos? (:computer-cities scan 0)) 10.0 1.0)]
    (* dist continent-factor presence-penalty)))

(defn find-unload-target
  "Find best enemy city to unload near, excluding pickup continent.
   Prioritizes player cities over free cities.
   Prefers unclaimed targets to spread transports."
  [pickup-continent transport-pos]
  (let [player-cities (world-query/find-visible-cities #{:player})
        free-cities (world-query/find-visible-cities #{:free})
        ;; Filter both to off-continent
        player-off (if pickup-continent
                     (remove #(contains? pickup-continent %) player-cities)
                     player-cities)
        free-off (if pickup-continent
                   (remove #(contains? pickup-continent %) free-cities)
                   free-cities)
        ;; Priority: player cities first
        priority-targets (if (seq player-off) player-off free-off)]
    (when-let [{:keys [best claimed]} (decisions/claimed-target-choice
                                       priority-targets
                                       (or (sa/read-state :claimed-transport-targets) #{})
                                       #(score-target-city transport-pos %))]
      (sa/write-state! :claimed-transport-targets claimed)
      best)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:22:19.510729-05:00", :module-hash "-955632872", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-1784909429"} {:id "defn-/score-target-city", :kind "defn-", :line 9, :end-line 21, :hash "-781637387"} {:id "defn/find-unload-target", :kind "defn", :line 23, :end-line 44, :hash "-793128703"}]}
;; clj-mutate-manifest-end
