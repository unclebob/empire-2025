(ns empire.computer.transport.sailing-regular.transitions
  (:require [empire.computer.army.assignment :as army-assignment]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.computer.transport.core :as tc]
            [empire.computer.transport.load-targeting :as load-targeting]
            [empire.computer.transport.reservations :as reservations]
            [empire.computer.transport.sailing-regular.follow :as follow]
            [empire.computer.transport.sailing-support :as support]
            [empire.game-mechanics.visibility :as visibility]
            [empire.computer.shared.world-query :as world-query]
            [empire.state.api :as sa]))

(defn enter-leave-city!
  [pos]
  (let [computer-map (sa/read-state :computer-map)
        from-mission (get-in computer-map (conj pos :contents :transport-mission))
        transport-id (get-in computer-map (conj pos :contents :transport-id))]
    (reservations/release! transport-id)
    (when (tc/update-transport-contents!
           pos
           #(-> %
                (assoc :transport-mission :leave-city
                       :load-target-cell nil
                       :load-manifest nil
                       :load-plan-failure nil
                       :hold-sail-to-load-since-round nil
                       :loading-since-round nil
                       :sail-path [])
                (dissoc :unload-target-city)))
      (visibility/sync-ai-unit-to-computer-map! pos)
      (tc/log-transport-mission-transition! pos from-mission :leave-city))))

(defn enter-sail-to-load!
  [pos]
  (let [computer-map (sa/read-state :computer-map)
        from-mission (get-in computer-map (conj pos :contents :transport-mission))
        transport-id (get-in computer-map (conj pos :contents :transport-id))
        _ (reservations/release! transport-id)
        load-target-cell (load-targeting/choose-load-target-cell
                          pos
                          computer-map
                          {:reserved-coastal-cells (reservations/reserved-coastal-cells transport-id)
                           :excluded-country-ids (disj #{(:pickup-country-id (get-in computer-map (conj pos :contents)))}
                                                       nil)
                           :reserved-army-ids (reservations/reserved-army-ids transport-id)})
        sail-path (if load-target-cell
                    (or (load-targeting/path-to-load-target pos computer-map load-target-cell)
                        [])
                    (or (support/compute-sail-to-load-path pos)
                        []))
        path-ready? (and load-target-cell
                         (or (seq sail-path)
                             (load-targeting/target-reached? pos load-target-cell)))]
    (let [manifest (vec (army-assignment/assign-returning-transport-staging-at! pos load-target-cell))
          failure (tc/load-plan-failure pos load-target-cell sail-path manifest path-ready?)]
      (if (and load-target-cell
               (seq manifest)
               path-ready?)
        (do
          (when (tc/update-transport-contents!
                 pos
                 #(-> %
                      (assoc :transport-mission :sail-to-load)
                      (assoc :load-target-cell load-target-cell
                             :load-manifest manifest
                             :load-plan-failure nil
                             :hold-sail-to-load-since-round nil
                             :loading-since-round nil
                             :sail-path (vec sail-path))
                      (dissoc :unload-target-city)))
            (reservations/reserve! transport-id load-target-cell manifest)
            (visibility/sync-ai-unit-to-computer-map! pos)
            (tc/log-transport-mission-transition! pos from-mission :sail-to-load))
          (assoc (get-in (sa/read-state :computer-map) (conj pos :contents))
                 :transport-mission :sail-to-load
                 :load-target-cell load-target-cell
                 :load-manifest manifest
                 :load-plan-failure nil
                 :sail-path (vec sail-path)))
        (do
          (tc/enter-hold-sail-to-load! pos failure)
          (get-in (sa/read-state :computer-map) (conj pos :contents)))))))

(defn enter-sail-to-unload!
  [pos transport]
  (when-let [path (seq (support/compute-sail-to-unload-path pos))]
    (reservations/release! (:transport-id transport))
    (tc/set-transport-mission pos :sail-to-unload)
    (tc/assoc-transport-field! pos :load-target-cell nil)
    (tc/assoc-transport-field! pos :load-manifest nil)
    (tc/assoc-transport-field! pos :load-plan-failure nil)
    (tc/assoc-transport-field! pos :hold-sail-to-load-since-round nil)
    (tc/assoc-transport-field! pos :loading-since-round nil)
    (tc/mint-unload-event-id pos transport)
    (tc/mint-unload-country-id pos)
    (tc/assoc-transport-field! pos :sail-path (vec path))
    (visibility/sync-ai-unit-to-computer-map! pos)
    true))

(defn launch-from-city-to-sea
  [pos transport]
  (let [world (sa/read-state :computer-map)
        cell-type (get-in world (conj pos :type))]
    (when (= :city cell-type)
      (let [target-ref (or (:invasion-target transport)
                           (:major-invasion-target transport)
                           pos)
            options (->> (world-query/get-neighbors pos)
                         (filter (fn [n]
                                   (let [c (get-in world n)]
                                     (and c
                                          (= :sea (:type c))
                                          (nil? (:contents c))))))
                         (sort-by (fn [n]
                                    [(grid/chebyshev-distance n target-ref) n])))]
        (when-let [sea-pos (first options)]
          (when (action-resolution/move-unit-to pos sea-pos)
            (support/update-cell-visibility! pos :computer)
            (support/update-cell-visibility! sea-pos :computer)
            (visibility/sync-ai-unit-to-computer-map! sea-pos)
            sea-pos))))))

(defn transition-to-loading!
  [pos]
  (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))]
    (if (vector? (:load-manifest transport))
      (do
        (tc/set-transport-mission pos :loading)
        (tc/assoc-transport-field! pos :hold-sail-to-load-since-round nil)
        (tc/assoc-transport-field! pos :load-plan-failure nil)
        (tc/assoc-transport-field! pos :loading-since-round
                                   (or (sa/read-state :round-number) 0))
        (tc/assoc-transport-field! pos :sail-path [])
        (visibility/sync-ai-unit-to-computer-map! pos))
      (tc/enter-hold-sail-to-load! pos))))

(defn handle-launch-and-follow!
  [pos transport path-fn]
  (if-let [sea-pos (launch-from-city-to-sea pos transport)]
    (follow/compute-and-follow-path! sea-pos path-fn)
    (follow/compute-and-follow-path! pos path-fn)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T10:33:09.092151-05:00", :module-hash "815340772", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 12, :hash "-1343488182"} {:id "defn/enter-leave-city!", :kind "defn", :line 14, :end-line 32, :hash "1089066290"} {:id "defn/enter-sail-to-load!", :kind "defn", :line 34, :end-line 83, :hash "1151641005"} {:id "defn/enter-sail-to-unload!", :kind "defn", :line 85, :end-line 99, :hash "103618031"} {:id "defn/launch-from-city-to-sea", :kind "defn", :line 101, :end-line 122, :hash "772347993"} {:id "defn/transition-to-loading!", :kind "defn", :line 124, :end-line 136, :hash "-1295117481"} {:id "defn/handle-launch-and-follow!", :kind "defn", :line 138, :end-line 142, :hash "-954404153"}]}
;; clj-mutate-manifest-end
