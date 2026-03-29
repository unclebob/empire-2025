(ns empire.computer.transport.sailing-invasion
  (:require [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.oscillation :as oscillation]
            [empire.computer.transport.core :as tc]
            [empire.computer.transport.sailing-decisions :as decisions]
            [empire.computer.transport.sailing-support :as support]
            [empire.computer.transport.unloading :as unloading]
            [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]))

(defn- clear-invasion-path!
  [pos]
  (when (:type (get-in (sa/current-world) (conj pos :contents)))
    (sa/update-world! update-in (conj pos :contents)
                      dissoc :invasion-path :invasion-path-origin)
    (visibility/sync-ai-unit-to-computer-map! pos)))

(defn- store-invasion-path!
  [pos remaining]
  (when (:type (get-in (sa/current-world) (conj pos :contents)))
    (sa/update-world! update-in (conj pos :contents)
                      assoc :invasion-path remaining
                      :invasion-path-origin pos)
    (visibility/sync-ai-unit-to-computer-map! pos)))

(defn- move-invasion-step!
  [from to]
  (when (action-resolution/move-unit-to from to)
    (support/update-cell-visibility! from :computer)
    (support/update-cell-visibility! to :computer)
    to))

(defn- finish-invading-at!
  [pos]
  (clear-invasion-path! pos)
  (tc/set-transport-mission pos :unloading))

(defn- continue-invading-via-path!
  [pos path]
  (let [step1 (first path)
        remaining1 (vec (rest path))]
    (if-let [step1-pos (move-invasion-step! pos step1)]
      (if (empty? remaining1)
        (finish-invading-at! step1-pos)
        (let [step2 (first remaining1)
              remaining2 (vec (rest remaining1))]
          (if-let [step2-pos (move-invasion-step! step1-pos step2)]
            (if (empty? remaining2)
              (finish-invading-at! step2-pos)
              (store-invasion-path! step2-pos remaining2))
            (store-invasion-path! step1-pos remaining1))))
      :blocked)))

(defn- unload-zone?
  [pos target transport]
  (let [radius (if (support/enemy-ship-near-target? target support/invasion-threat-scan-radius)
                 support/invasion-threat-unload-radius
                 support/invasion-unload-radius)]
    (or (<= (grid/chebyshev-distance pos target) radius)
        (and transport (unloading/has-nearby-unloadable-land? pos transport 5)))))

(defn- retreat-away-from-target!
  [pos target]
  (let [world (sa/read-state :computer-map)
        current-distance (grid/chebyshev-distance pos target)
        candidates (->> (tc/get-passable-sea-neighbors pos)
                        (filter #(nil? (get-in world (conj % :contents))))
                        (filter #(< current-distance (grid/chebyshev-distance % target))))
        chosen (first (sort-by (fn [p]
                                 [(- (grid/chebyshev-distance p target)) p])
                               candidates))]
    (when (and chosen (action-resolution/move-unit-to pos chosen))
      (support/update-cell-visibility! pos :computer)
      (support/update-cell-visibility! chosen :computer)
      (visibility/sync-ai-unit-to-computer-map! chosen)
      chosen)))

(defn- handle-invasion-threat-near-target!
  [pos target]
  (when (and target
             (<= (grid/chebyshev-distance pos target) support/invasion-threat-unload-radius)
             (support/enemy-ship-near-target? target support/invasion-threat-scan-radius))
    (if-let [retreated (retreat-away-from-target! pos target)]
      (tc/set-transport-mission retreated :unloading)
      (tc/set-transport-mission pos :unloading))
    true))

(defn- continue-invading-without-path!
  [pos target invading-step]
  (if target
    (let [moved1 (invading-step pos)
          pos1 (or moved1 pos)
          moved2 (invading-step pos1)
          pos2 (or moved2 pos1)
          transport2 (get-in (sa/read-state :computer-map) (conj pos2 :contents))
          follow-up (decisions/crawl-follow-up
                     {:target? (boolean target)
                      :moved1? (boolean moved1)
                      :moved2? (boolean moved2)
                      :unload-zone? (unload-zone? pos2 target transport2)})]
      (when (and (:start-random-walk? follow-up)
                 (:type (get-in (sa/current-world) (conj pos :contents))))
        (sa/update-world! update-in (conj pos :contents)
                          #(oscillation/start-random-walk % support/transport-random-walk-restore-keys))
        (visibility/sync-ai-unit-to-computer-map! pos))
      (when-let [mission (:set-mission follow-up)]
        (tc/set-transport-mission pos2 mission)))
    (when-let [mission (:set-mission (decisions/crawl-follow-up {:target? false}))]
      (tc/set-transport-mission pos mission))))

(defn- inflated-sea-path?
  [path from target]
  (let [cheb (grid/chebyshev-distance from target)]
    (and (seq path)
         (pos? cheb)
         (>= (count path) (* support/sea-path-inflation-threshold cheb)))))

(defn- use-direct-invasion-shortcut?
  [pos target path]
  (let [computer-map (sa/read-state :computer-map)]
    (and target
         (inflated-sea-path? path pos target)
         (support/direct-sea-corridor? pos target computer-map))))

(defn- choose-invading-step
  [from target]
  (let [world (sa/read-state :computer-map)
        transport (get-in world (conj from :contents))
        last-pos (:invasion-last-pos transport)
        neighbors (->> (tc/get-passable-sea-neighbors from)
                       (filter #(nil? (get-in world (conj % :contents)))))
        candidates (if (and last-pos (> (count neighbors) 1))
                     (remove #(= % last-pos) neighbors)
                     neighbors)
        current-distance (if target
                           (grid/chebyshev-distance from target)
                           ##Inf)
        better (if target
                 (filter #(< (grid/chebyshev-distance % target) current-distance) candidates)
                 candidates)
        pool (if (seq better) better candidates)]
    (first (sort-by (fn [p]
                      [(if target
                         (grid/chebyshev-distance p target)
                         0)
                       p])
                    pool))))

(defn- invading-step
  [from target]
  (when-let [chosen (choose-invading-step from target)]
    (when (action-resolution/move-unit-to from chosen)
      (support/update-cell-visibility! from :computer)
      (support/update-cell-visibility! chosen :computer)
      ;; Force recompute from new position next round.
      (clear-invasion-path! chosen)
      (when (:type (get-in (sa/current-world) (conj chosen :contents)))
        (sa/update-world! assoc-in (conj chosen :contents :invasion-last-pos) from))
      (visibility/sync-ai-unit-to-computer-map! chosen)
      chosen)))

(defn- handle-blocked-invading-path!
  [pos target]
  (let [sidestep-succeeded? (boolean (invading-step pos target))]
    (when (and (:start-random-walk? (decisions/blocked-path-follow-up sidestep-succeeded?))
               (:type (get-in (sa/current-world) (conj pos :contents))))
      (sa/update-world! update-in (conj pos :contents)
                        #(oscillation/start-random-walk % support/transport-random-walk-restore-keys))
      (visibility/sync-ai-unit-to-computer-map! pos))))

(defn process-invading-mission
  "Follow precomputed invasion path. Steps up to 2 cells per round.
   When path exhausted, transition to unloading with coast-crawl."
  [pos]
  (visibility/sync-ai-unit-to-computer-map! pos)
  (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))
        path (:invasion-path transport)
        target (or (:invasion-target transport) (:major-invasion-target transport))
        threat-near-target? (handle-invasion-threat-near-target! pos target)
        empty-path? (empty? path)
        direct-shortcut? (and (not empty-path?)
                              (use-direct-invasion-shortcut? pos target path))
        action (:action (decisions/invading-action {:threat-near-target? threat-near-target?
                                                    :empty-path? empty-path?
                                                    :direct-shortcut? direct-shortcut?}))]
    (case action
      :threat nil
      :crawl (continue-invading-without-path! pos target #(invading-step % target))
      :path (when (= :blocked (continue-invading-via-path! pos path))
              (handle-blocked-invading-path! pos target))
      nil)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:14:12.99902-05:00", :module-hash "877445927", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 10, :hash "1298752322"} {:id "defn-/clear-invasion-path!", :kind "defn-", :line 12, :end-line 16, :hash "1370544043"} {:id "defn-/store-invasion-path!", :kind "defn-", :line 18, :end-line 23, :hash "-372933906"} {:id "defn-/move-invasion-step!", :kind "defn-", :line 25, :end-line 30, :hash "-208279688"} {:id "defn-/finish-invading-at!", :kind "defn-", :line 32, :end-line 35, :hash "-207367400"} {:id "defn-/continue-invading-via-path!", :kind "defn-", :line 37, :end-line 51, :hash "-1602446716"} {:id "defn-/unload-zone?", :kind "defn-", :line 53, :end-line 59, :hash "3271002"} {:id "defn-/retreat-away-from-target!", :kind "defn-", :line 61, :end-line 75, :hash "1245027508"} {:id "defn-/handle-invasion-threat-near-target!", :kind "defn-", :line 77, :end-line 85, :hash "83010819"} {:id "defn-/continue-invading-without-path!", :kind "defn-", :line 87, :end-line 107, :hash "145560672"} {:id "defn-/inflated-sea-path?", :kind "defn-", :line 109, :end-line 114, :hash "-1802791312"} {:id "defn-/use-direct-invasion-shortcut?", :kind "defn-", :line 116, :end-line 121, :hash "21264583"} {:id "defn-/choose-invading-step", :kind "defn-", :line 123, :end-line 145, :hash "-993620203"} {:id "defn-/invading-step", :kind "defn-", :line 147, :end-line 157, :hash "464316295"} {:id "defn-/handle-blocked-invading-path!", :kind "defn-", :line 159, :end-line 165, :hash "-434248414"} {:id "defn/process-invading-mission", :kind "defn", :line 167, :end-line 187, :hash "1221040635"}]}
;; clj-mutate-manifest-end
