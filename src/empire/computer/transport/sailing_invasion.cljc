(ns empire.computer.transport.sailing-invasion
  (:require [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.movement :as computer-movement]
            [empire.computer.shared.oscillation :as oscillation]
            [empire.computer.transport.core :as tc]
            [empire.computer.transport.sailing-decisions :as decisions]
            [empire.computer.transport.sailing-support :as support]
            [empire.computer.transport.unloading :as unloading]
            [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]))

(defn- clear-invasion-path!
  [pos]
  (computer-movement/update-unit-and-sync!
   pos
   #(when (:type %) (dissoc % :invasion-path :invasion-path-origin))))

(defn- store-invasion-path!
  [pos remaining]
  (sa/update-world! update-in (conj pos :contents)
                    #(when (:type %) (assoc % :invasion-path remaining
                                             :invasion-path-origin pos)))
  (visibility/sync-ai-unit-to-computer-map! pos))

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

(defn- crawl-two-steps
  [pos invading-step]
  (let [moved1 (invading-step pos)
        pos1 (or moved1 pos)
        moved2 (invading-step pos1)
        pos2 (or moved2 pos1)]
    {:moved1 moved1
     :moved2 moved2
     :pos2 pos2}))

(defn- start-random-walk-from!
  [pos]
  (sa/update-world! update-in (conj pos :contents)
                    #(when (:type %) (oscillation/start-random-walk %
                                                                    support/transport-random-walk-restore-keys)))
  (visibility/sync-ai-unit-to-computer-map! pos))

(defn- apply-crawl-follow-up!
  [pos pos2 follow-up]
  (when (:start-random-walk? follow-up)
    (start-random-walk-from! pos))
  (when-let [mission (:set-mission follow-up)]
    (tc/set-transport-mission pos2 mission)))

(defn- target-crawl-follow-up
  [target {:keys [moved1 moved2 pos2]}]
  (let [transport2 (get-in (sa/read-state :computer-map) (conj pos2 :contents))]
    (decisions/crawl-follow-up
     {:target? true
      :moved1? (boolean moved1)
      :moved2? (boolean moved2)
      :unload-zone? (unload-zone? pos2 target transport2)})))

(defn- continue-invading-without-path!
  [pos target invading-step]
  (if target
    (let [{:keys [pos2] :as crawl-result} (crawl-two-steps pos invading-step)]
      (apply-crawl-follow-up! pos pos2 (target-crawl-follow-up target crawl-result)))
    (when-let [mission (:set-mission (decisions/crawl-follow-up {:target? false}))]
      (tc/set-transport-mission pos mission))))

(defn- use-direct-invasion-shortcut?
  [pos target path]
  (let [computer-map (sa/read-state :computer-map)]
    (and target
         (grid/inflated-path? path pos target support/sea-path-inflation-threshold)
         (support/direct-sea-corridor? pos target computer-map))))

(defn- invading-step-candidates
  [from world last-pos]
  (let [neighbors (->> (tc/get-passable-sea-neighbors from)
                       (filter #(nil? (get-in world (conj % :contents)))))]
    (if (and last-pos (> (count neighbors) 1))
      (remove #(= % last-pos) neighbors)
      neighbors)))

(defn- invading-step-pool
  [from target candidates]
  (if-not target
    candidates
    (let [current-distance (grid/chebyshev-distance from target)
          better (filter #(< (grid/chebyshev-distance % target) current-distance) candidates)]
      (if (seq better) better candidates))))

(defn- choose-invading-step
  [from target]
  (let [world (sa/read-state :computer-map)
        last-pos (:invasion-last-pos (get-in world (conj from :contents)))
        candidates (invading-step-candidates from world last-pos)
        pool (invading-step-pool from target candidates)]
    (first (sort-by (fn [p]
                      [(if target (grid/chebyshev-distance p target) 0)
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
      (sa/update-world! update-in (conj chosen :contents)
                        #(when (:type %) (assoc % :invasion-last-pos from)))
      (visibility/sync-ai-unit-to-computer-map! chosen)
      chosen)))

(defn- handle-blocked-invading-path!
  [pos target]
  (let [sidestep-succeeded? (boolean (invading-step pos target))]
    (when (:start-random-walk? (decisions/blocked-path-follow-up sidestep-succeeded?))
      (sa/update-world! update-in (conj pos :contents)
                        #(when (:type %) (oscillation/start-random-walk % support/transport-random-walk-restore-keys)))
      (visibility/sync-ai-unit-to-computer-map! pos))))

(defn- invading-mission-action
  [pos path target]
  (let [threat-near-target? (handle-invasion-threat-near-target! pos target)
        empty-path? (empty? path)
        direct-shortcut? (and (not empty-path?)
                              (use-direct-invasion-shortcut? pos target path))]
    (:action (decisions/invading-action {:threat-near-target? threat-near-target?
                                         :empty-path? empty-path?
                                         :direct-shortcut? direct-shortcut?}))))

(defn- apply-invading-action
  [pos path target action]
  (case action
    :threat nil
    :crawl (continue-invading-without-path! pos target #(invading-step % target))
    :path (when (= :blocked (continue-invading-via-path! pos path))
            (handle-blocked-invading-path! pos target))
    nil))

(defn process-invading-mission
  "Follow precomputed invasion path. Steps up to 2 cells per round.
   When path exhausted, transition to unloading with coast-crawl."
  [pos]
  (visibility/sync-ai-unit-to-computer-map! pos)
  (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))
        path (:invasion-path transport)
        target (or (:invasion-target transport) (:major-invasion-target transport))]
    (apply-invading-action pos path target (invading-mission-action pos path target))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-07T17:36:43.356784-05:00", :module-hash "1800368724", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 11, :hash "17419080"} {:id "defn-/clear-invasion-path!", :kind "defn-", :line 13, :end-line 17, :hash "-786211817"} {:id "defn-/store-invasion-path!", :kind "defn-", :line 19, :end-line 24, :hash "1541348549"} {:id "defn-/move-invasion-step!", :kind "defn-", :line 26, :end-line 31, :hash "-208279688"} {:id "defn-/finish-invading-at!", :kind "defn-", :line 33, :end-line 36, :hash "-207367400"} {:id "defn-/continue-invading-via-path!", :kind "defn-", :line 38, :end-line 52, :hash "-1602446716"} {:id "defn-/unload-zone?", :kind "defn-", :line 54, :end-line 60, :hash "3271002"} {:id "defn-/retreat-away-from-target!", :kind "defn-", :line 62, :end-line 76, :hash "2033806174"} {:id "defn-/handle-invasion-threat-near-target!", :kind "defn-", :line 78, :end-line 86, :hash "83010819"} {:id "defn-/crawl-two-steps", :kind "defn-", :line 88, :end-line 96, :hash "160876629"} {:id "defn-/start-random-walk-from!", :kind "defn-", :line 98, :end-line 103, :hash "415670099"} {:id "defn-/apply-crawl-follow-up!", :kind "defn-", :line 105, :end-line 110, :hash "1604198202"} {:id "defn-/target-crawl-follow-up", :kind "defn-", :line 112, :end-line 119, :hash "-1152990199"} {:id "defn-/continue-invading-without-path!", :kind "defn-", :line 121, :end-line 127, :hash "-437609397"} {:id "defn-/use-direct-invasion-shortcut?", :kind "defn-", :line 129, :end-line 134, :hash "1655568541"} {:id "defn-/choose-invading-step", :kind "defn-", :line 136, :end-line 158, :hash "-2039612965"} {:id "defn-/invading-step", :kind "defn-", :line 160, :end-line 171, :hash "480851697"} {:id "defn-/handle-blocked-invading-path!", :kind "defn-", :line 173, :end-line 179, :hash "44868908"} {:id "defn/process-invading-mission", :kind "defn", :line 181, :end-line 201, :hash "2090134072"}]}
;; clj-mutate-manifest-end
