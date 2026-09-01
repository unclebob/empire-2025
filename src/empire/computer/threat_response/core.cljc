(ns empire.computer.threat-response.core
  "Threat-response coordinator for enemy detections.
   Thin facade delegating to detection and refresh daughters."
  (:require [empire.state.api :as sa]
            [empire.computer.fighter.movement :as fighter-movement]
            [empire.computer.shared.oscillation :as oscillation]
            [empire.computer.threat-response.decisions :as decisions]
            [empire.computer.threat-response.detection :as detection]
            [empire.computer.threat-response.kamikazee :as kamikazee]
            [empire.computer.threat-response.processing :as processing]
            [empire.computer.threat-response.refresh :as refresh]
            [empire.game-mechanics.services.threat-policy :as threat-policy]
            [empire.game-mechanics.visibility :as visibility]))

;; --- re-exports from refresh ---
(def load-major-invasion-state refresh/load-major-invasion-state)
(def major-invasion-target-land? refresh/major-invasion-target-land?)
(def major-invasion-target-revision refresh/major-invasion-target-revision)
(def nearest-major-sea-target refresh/nearest-major-sea-target)
(def best-invasion-target-and-path refresh/best-invasion-target-and-path)
(def prepare-transport-major-invasion! refresh/prepare-transport-major-invasion!)
(def connected-coastal-candidates refresh/connected-coastal-candidates)
(def dec-threat-rounds refresh/dec-threat-rounds)
(def next-review-round refresh/next-review-round)
(def launch-kamikazee-from-airport! refresh/launch-kamikazee-from-airport!)

;; --- re-exports from detection ---
(def find-computer-unit-positions detection/find-computer-unit-positions)

;; --- manager context (bridges both daughters) ---

(declare recompute-major-invasion-target-land!)

(defn- manager-ctx []
  (refresh/manager-ctx
   {:find-computer-unit-positions-fn detection/find-computer-unit-positions
    :refresh-country-defense!-fn detection/refresh-country-defense!
    :recompute-major-invasion-target-land!-fn recompute-major-invasion-target-land!}))

;; --- facade functions that need manager-ctx ---

(defn recompute-major-invasion-target-land! []
  (refresh/recompute-major-invasion-target-land! (manager-ctx)))

(defn refresh-major-invasion-assignments!
  "Applies major-invasion tags/targets to all mobilized computer units."
  []
  (refresh/refresh-major-invasion-assignments! (manager-ctx)))

(defn rebuild-kamikazee-routing! []
  (refresh/rebuild-kamikazee-routing! (manager-ctx)))

(defn on-round-start!
  "Round-start maintenance for threat responses."
  []
  (refresh/on-round-start! (manager-ctx)))

;; --- detection orchestration ---

(defn- invasion-army-target?
  [game-cell]
  (and (refresh/major-invasion-active?)
       (= :army (get-in game-cell [:contents :type]))
       (= :player (get-in game-cell [:contents :owner]))))

(defn- apply-detection-trigger
  [pos trigger]
  (case trigger
    :fighter-detected (detection/handle-fighter-detection! pos)
    :ship-detected (detection/handle-ship-detection! pos)
    :country-defense-trigger (detection/handle-country-defense-detection! pos)
    :major-invasion-trigger (refresh/handle-major-invasion-detection! (manager-ctx) pos)
    nil))

(defn handle-detection!
  "Handle a newly-visible cell on computer-map for threat triggers."
  [pos game-cell]
  (refresh/refresh-computer-map!)
  (let [{:keys [record-army-target? trigger]}
        (decisions/detection-action
         {:record-army-target? (invasion-army-target? game-cell)
          :trigger (threat-policy/detection-trigger game-cell)})]
    (when record-army-target?
      (refresh/record-army-target! pos))
    (apply-detection-trigger pos trigger)
    nil))

;; --- processing (stays in core) ---

(defn prepare-transport!
  "Called by transport processing; applies major-invasion directives when active."
  [pos]
  (when-let [unit (get-in (sa/read-state :computer-map) (conj pos :contents))]
    (when (= :prepare-transport
             (decisions/prepare-transport-action
              {:major-invasion-active? (refresh/major-invasion-active?)
               :unit unit}))
      (refresh/prepare-transport-major-invasion! pos unit)
      (visibility/sync-ai-unit-to-computer-map! pos)
      true)))

(defn fighter-step-threat
  [pos unit]
  (processing/fighter-step-threat
   {:current-world #(sa/read-state :computer-map)
    :update-game-map! sa/update-world!
    :sync-ai-unit! visibility/sync-ai-unit-to-computer-map!
    :nearest-major-target refresh/nearest-major-target
    :threat-radius (refresh/threat-radius)}
   pos
   unit))

(defn- run-fighter-steps
  [pos speed step-fn]
  (loop [current pos
         remaining speed]
    (when (pos? remaining)
      (when-let [{:keys [pos steps-used hops]} (step-fn current)]
        (let [used (or steps-used hops 1)]
          (recur pos (- remaining used)))))))

(defn- run-kamikazee-round [pos]
  (run-fighter-steps
   pos
   fighter-movement/fighter-speed
   (fn [current]
     (kamikazee/process-kamikazee-fighter
      (refresh/invasion-ctx)
      current
      (get-in (sa/read-state :computer-map) (conj current :contents))))))

(defn- run-standard-threat-round [pos unit]
  (if (oscillation/in-random-walk? unit)
    (processing/process-fighter-random-walk-round
     {:current-world #(sa/read-state :computer-map)
      :update-game-map! sa/update-world!
      :sync-ai-unit! visibility/sync-ai-unit-to-computer-map!}
     pos)
    (run-fighter-steps
     pos
     fighter-movement/fighter-speed
     (fn [current]
       (fighter-step-threat current
                            (get-in (sa/read-state :computer-map) (conj current :contents)))))))

(defn process-fighter-threat
  "Overrides regular fighter logic while fighter-sweep/country-defense or major-invasion mission is active.
   Returns true when handled."
  [pos unit]
  (when-let [action (decisions/fighter-threat-round-action unit)]
    (if (= :kamikazee action)
      (run-kamikazee-round pos)
      (run-standard-threat-round pos unit))
    true))

(defn process-ship-threat
  "Overrides regular ship logic for sea-scout and major-invasion missions.
   Returns true when handled."
  [pos ship-type unit]
  (case (decisions/ship-threat-action
         {:ship-type ship-type
          :major-invasion? (:major-invasion unit)
          :fixed-carrier? (kamikazee/fixed-carrier? (refresh/load-major-invasion-state) pos)})
    :hold true
    (processing/process-ship-threat
     {:current-world #(sa/read-state :computer-map)
      :update-game-map! sa/update-world!
      :read-runtime-state sa/read-state
      :sync-ai-unit! visibility/sync-ai-unit-to-computer-map!
      :nearest-major-target refresh/nearest-major-ship-target
      :threat-radius (refresh/threat-radius)}
     pos
     ship-type
     unit)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:07:50.499849-05:00", :module-hash "-1153474400", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "2035666372"} {:id "def/load-major-invasion-state", :kind "def", :line 16, :end-line nil, :hash "2113425401"} {:id "def/major-invasion-target-land?", :kind "def", :line 17, :end-line nil, :hash "-1753695645"} {:id "def/major-invasion-target-revision", :kind "def", :line 18, :end-line nil, :hash "-1683170246"} {:id "def/nearest-major-sea-target", :kind "def", :line 19, :end-line nil, :hash "-1107613216"} {:id "def/best-invasion-target-and-path", :kind "def", :line 20, :end-line nil, :hash "-1224006001"} {:id "def/prepare-transport-major-invasion!", :kind "def", :line 21, :end-line nil, :hash "867876160"} {:id "def/connected-coastal-candidates", :kind "def", :line 22, :end-line nil, :hash "-902343127"} {:id "def/dec-threat-rounds", :kind "def", :line 23, :end-line nil, :hash "106779857"} {:id "def/next-review-round", :kind "def", :line 24, :end-line nil, :hash "1885645127"} {:id "def/launch-kamikazee-from-airport!", :kind "def", :line 25, :end-line nil, :hash "1880278175"} {:id "def/find-computer-unit-positions", :kind "def", :line 28, :end-line nil, :hash "1496583980"} {:id "form/12/declare", :kind "declare", :line 32, :end-line nil, :hash "-2010768394"} {:id "defn-/manager-ctx", :kind "defn-", :line 34, :end-line nil, :hash "890659250"} {:id "defn/recompute-major-invasion-target-land!", :kind "defn", :line 42, :end-line nil, :hash "1299321252"} {:id "defn/refresh-major-invasion-assignments!", :kind "defn", :line 45, :end-line nil, :hash "-358650840"} {:id "defn/rebuild-kamikazee-routing!", :kind "defn", :line 50, :end-line nil, :hash "-2075574711"} {:id "defn/on-round-start!", :kind "defn", :line 53, :end-line nil, :hash "2059714081"} {:id "defn-/invasion-army-target?", :kind "defn-", :line 60, :end-line nil, :hash "-986446546"} {:id "defn-/apply-detection-trigger", :kind "defn-", :line 66, :end-line nil, :hash "-583421208"} {:id "defn/handle-detection!", :kind "defn", :line 75, :end-line nil, :hash "192128963"} {:id "defn/prepare-transport!", :kind "defn", :line 90, :end-line nil, :hash "-255422372"} {:id "defn/fighter-step-threat", :kind "defn", :line 102, :end-line nil, :hash "627701348"} {:id "defn-/run-fighter-steps", :kind "defn-", :line 113, :end-line nil, :hash "409096778"} {:id "defn-/run-kamikazee-round", :kind "defn-", :line 122, :end-line nil, :hash "2042572277"} {:id "defn-/run-standard-threat-round", :kind "defn-", :line 132, :end-line nil, :hash "528878794"} {:id "defn/process-fighter-threat", :kind "defn", :line 146, :end-line nil, :hash "430514376"} {:id "defn/process-ship-threat", :kind "defn", :line 156, :end-line nil, :hash "153701568"}]}
;; clj-mutate-manifest-end
