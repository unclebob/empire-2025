(ns empire.computer.transport-sailing-invasion
  (:require [empire.computer.core :as core]
            [empire.computer.oscillation :as oscillation]
            [empire.computer.transport-core :as tc]
            [empire.computer.transport-sailing-decisions :as decisions]
            [empire.computer.transport-sailing-support :as support]
            [empire.computer.transport-unloading :as unloading]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.state.api :as sa]))

(defn- clear-invasion-path!
  [pos]
  (sa/update-world! update-in (conj pos :contents)
                    dissoc :invasion-path :invasion-path-origin)
  (visibility/sync-ai-unit-to-computer-map! pos))

(defn- store-invasion-path!
  [pos remaining]
  (sa/update-world! update-in (conj pos :contents)
                    assoc :invasion-path remaining
                    :invasion-path-origin pos)
  (visibility/sync-ai-unit-to-computer-map! pos))

(defn- move-invasion-step!
  [from to]
  (when (core/move-unit-to from to)
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
    (or (<= (core/chebyshev-distance pos target) radius)
        (and transport (unloading/has-nearby-unloadable-land? pos transport 5)))))

(defn- retreat-away-from-target!
  [pos target]
  (let [world (sa/read-state :computer-map)
        current-distance (core/chebyshev-distance pos target)
        candidates (->> (tc/get-passable-sea-neighbors pos)
                        (filter #(nil? (get-in world (conj % :contents))))
                        (filter #(< current-distance (core/chebyshev-distance % target))))
        chosen (first (sort-by (fn [p]
                                 [(- (core/chebyshev-distance p target)) p])
                               candidates))]
    (when (and chosen (core/move-unit-to pos chosen))
      (support/update-cell-visibility! pos :computer)
      (support/update-cell-visibility! chosen :computer)
      (visibility/sync-ai-unit-to-computer-map! chosen)
      chosen)))

(defn- handle-invasion-threat-near-target!
  [pos target]
  (when (and target
             (<= (core/chebyshev-distance pos target) support/invasion-threat-unload-radius)
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
      (when (:start-random-walk? follow-up)
        (sa/update-world! update-in (conj pos :contents)
                          #(oscillation/start-random-walk % support/transport-random-walk-restore-keys))
        (visibility/sync-ai-unit-to-computer-map! pos))
      (when-let [mission (:set-mission follow-up)]
        (tc/set-transport-mission pos2 mission)))
    (when-let [mission (:set-mission (decisions/crawl-follow-up {:target? false}))]
      (tc/set-transport-mission pos mission))))

(defn- inflated-sea-path?
  [path from target]
  (let [cheb (core/chebyshev-distance from target)]
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
                           (core/chebyshev-distance from target)
                           ##Inf)
        better (if target
                 (filter #(< (core/chebyshev-distance % target) current-distance) candidates)
                 candidates)
        pool (if (seq better) better candidates)]
    (first (sort-by (fn [p]
                      [(if target
                         (core/chebyshev-distance p target)
                         0)
                       p])
                    pool))))

(defn- invading-step
  [from target]
  (when-let [chosen (choose-invading-step from target)]
    (when (core/move-unit-to from chosen)
      (support/update-cell-visibility! from :computer)
      (support/update-cell-visibility! chosen :computer)
      ;; Force recompute from new position next round.
      (clear-invasion-path! chosen)
      (sa/update-world! assoc-in (conj chosen :contents :invasion-last-pos) from)
      (visibility/sync-ai-unit-to-computer-map! chosen)
      chosen)))

(defn- handle-blocked-invading-path!
  [pos target]
  (let [sidestep-succeeded? (boolean (invading-step pos target))]
    (when (:start-random-walk? (decisions/blocked-path-follow-up sidestep-succeeded?))
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
;; {:version 1, :tested-at "2026-03-16T15:02:46.617668-05:00", :module-hash "-1862765542", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "-1636700275"} {:id "defn-/clear-invasion-path!", :kind "defn-", :line 10, :end-line 13, :hash "-1817877579"} {:id "defn-/store-invasion-path!", :kind "defn-", :line 15, :end-line 19, :hash "-1045842145"} {:id "defn-/move-invasion-step!", :kind "defn-", :line 21, :end-line 26, :hash "-170660455"} {:id "defn-/finish-invading-at!", :kind "defn-", :line 28, :end-line 31, :hash "-207367400"} {:id "defn-/continue-invading-via-path!", :kind "defn-", :line 33, :end-line 47, :hash "-1602446716"} {:id "defn-/unload-zone?", :kind "defn-", :line 49, :end-line 55, :hash "-1249422758"} {:id "defn-/retreat-away-from-target!", :kind "defn-", :line 57, :end-line 70, :hash "-1008673029"} {:id "defn-/handle-invasion-threat-near-target!", :kind "defn-", :line 72, :end-line 80, :hash "-1340782514"} {:id "defn-/continue-invading-without-path!", :kind "defn-", :line 82, :end-line 101, :hash "-1120403998"} {:id "defn-/inflated-sea-path?", :kind "defn-", :line 103, :end-line 108, :hash "-1326966630"} {:id "defn-/use-direct-invasion-shortcut?", :kind "defn-", :line 110, :end-line 115, :hash "21264583"} {:id "defn-/choose-invading-step", :kind "defn-", :line 117, :end-line 139, :hash "1732413921"} {:id "defn-/invading-step", :kind "defn-", :line 141, :end-line 150, :hash "-604809418"} {:id "defn-/handle-blocked-invading-path!", :kind "defn-", :line 152, :end-line 157, :hash "49709005"} {:id "defn/process-invading-mission", :kind "defn", :line 159, :end-line 178, :hash "192370326"}]}
;; clj-mutate-manifest-end
