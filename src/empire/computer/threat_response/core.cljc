(ns empire.computer.threat-response.core
  "Threat-response coordinator for enemy detections.
   Handles fighter/ship local responses and global major invasion mobilization."
  (:require [empire.state.api :as sa]
            [empire.computer.fighter.movement :as fighter-movement]
            [empire.computer.shared.oscillation :as oscillation]
            [empire.computer.threat-response.kamikazee :as kamikazee]
            [empire.computer.threat-response.country-defense :as country-defense]
            [empire.computer.threat-response.decisions :as decisions]
            [empire.computer.threat-response.invasion-decision :as invasion-decision]
            [empire.computer.threat-response.invasion-state :as invasion-state]
            [empire.computer.threat-response.major-invasion-manager :as manager]
            [empire.computer.threat-response.major-invasion :as major-invasion]
            [empire.computer.threat-response.processing :as processing]
            [empire.game-mechanics.services.threat-policy :as threat-policy]
            [empire.game-mechanics.visibility :as visibility]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.movement :as computer-movement]))

(defn- threat-radius []
  (threat-policy/threat-radius))

(defn- refresh-computer-map!
  []
  (visibility/refresh-visible-map! :computer))

(declare manager-ctx)
(declare current-round)
(declare next-review-round)

(def ^:private major-invasion-ship-types
  #{:patrol-boat :destroyer :submarine :carrier :battleship})

(def ^:private computer-sea-unit-types
  (conj major-invasion-ship-types :transport))
(def ^:private max-invasion-coastal-candidates 24)
(def ^:private preferred-invasion-landing-distance 8)

(defn load-major-invasion-state
  []
  (sa/read-state :major-invasion-state))

(defn- save-major-invasion-state!
  [state]
  (sa/write-state! :major-invasion-state state))

(defn- update-major-invasion-state!
  [f & args]
  (let [current (load-major-invasion-state)
        next-state (apply f current args)]
    (save-major-invasion-state! next-state)))

(defn- major-invasion-active?
  []
  (:active? (load-major-invasion-state)))

(defn- major-invasion-detection-points
  []
  (:detection-points (load-major-invasion-state)))

(defn major-invasion-target-land?
  [pos]
  (contains? (:target-land-set (load-major-invasion-state)) pos))

(defn recompute-major-invasion-target-land!
  []
  (manager/recompute-major-invasion-target-land! (manager-ctx)))

(defn find-computer-unit-positions
  [pred]
  (let [game-map (sa/read-state :computer-map)]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [unit (get-in game-map [i j :contents])]
          :when (and unit
                     (= :computer (:owner unit))
                     (pred unit))]
      [i j])))

(defn- assign-threat-mission!
  [positions mission-kv]
  (doseq [pos positions]
    (sa/update-world! update-in (conj pos :contents) merge mission-kv)
    (visibility/sync-ai-unit-to-computer-map! pos)))

(defn- closest-positions
  [origin positions n]
  (->> positions
       (sort-by #(grid/distance % origin))
       (take n)))

(defn- nearest-major-target
  [pos]
  (invasion-state/nearest-target (load-major-invasion-state) pos))

(defn- invasion-ctx
  []
  {:load-major-invasion-state load-major-invasion-state
   :update-major-invasion-state! update-major-invasion-state!
   :current-world #(sa/read-state :computer-map)
   :read-runtime-state sa/read-state
   :update-game-map! sa/update-world!
   :sync-ai-unit! visibility/sync-ai-unit-to-computer-map!
   :nearest-major-target nearest-major-target
   :major-invasion-ship-types major-invasion-ship-types
   :computer-sea-unit-types computer-sea-unit-types})

(defn- nearest-major-ship-target
  [pos]
  (or (major-invasion/nearest-major-sea-target (invasion-ctx) pos)
      (nearest-major-target pos)))

(defn- recompute-sea-reachable-detection-points!
  []
  (major-invasion/recompute-sea-reachable-detection-points! (invasion-ctx)))

(defn major-invasion-target-revision
  []
  (major-invasion/major-invasion-target-revision (invasion-ctx)))

(defn dec-threat-rounds
  [unit]
  (threat-policy/dec-threat-rounds unit))

(defn- homeland-defense-unit?
  [unit]
  (and unit
       (= :computer (:owner unit))
       (#{:army :fighter} (:type unit))
       (:country-id unit)))

(defn- refresh-country-defense!
  []
  (let [targets-by-country (country-defense/player-armies-by-country (sa/read-state :computer-map))
        radius (threat-radius)
        game-map (sa/read-state :computer-map)]
    (doseq [i (range (count game-map))
            j (range (count (first game-map)))
            :let [unit (get-in game-map [i j :contents])]
            :when (homeland-defense-unit? unit)]
      (let [cid (:country-id unit)
            targets (get targets-by-country cid)]
        (sa/update-world! update-in [i j :contents]
                          (if (seq targets)
                            #(country-defense/apply-country-defense % [i j] targets radius)
                            country-defense/clear-country-defense))
        (visibility/sync-ai-unit-to-computer-map! [i j])))))

(defn nearest-major-sea-target
  [pos]
  (major-invasion/nearest-major-sea-target (invasion-ctx) pos))

(defn connected-coastal-candidates
  [computer-map state target]
  (major-invasion/connected-coastal-candidates computer-map state target))

(defn best-invasion-target-and-path
  [pos target]
  (let [state (load-major-invasion-state)
        computer-map (sa/read-state :computer-map)
        all-candidates (connected-coastal-candidates computer-map state target)
        nearby-candidates (filter #(<= (grid/chebyshev-distance % target)
                                       preferred-invasion-landing-distance)
                                  all-candidates)
        candidates-base (if (seq nearby-candidates) nearby-candidates all-candidates)
        candidates (->> candidates-base
                        (sort-by (fn [candidate]
                                   [(grid/chebyshev-distance candidate target)
                                    candidate]))
                        (take max-invasion-coastal-candidates))
        scored (keep (fn [candidate]
                       (when-let [path (computer-movement/bfs-to-land-ho-target pos candidate computer-map)]
                         {:target candidate
                          :path (vec path)
                          :score [(grid/chebyshev-distance candidate target)
                                  (count path)
                                  candidate]}))
                     candidates)]
    (when (seq scored)
      (let [{:keys [target path]} (first (sort-by :score scored))]
        {:target target :path path}))))

(defn prepare-transport-major-invasion!
  [pos unit]
  (major-invasion/prepare-transport-major-invasion!
   (assoc (invasion-ctx)
          :nearest-major-sea-target-fn nearest-major-sea-target
          :best-invasion-target-and-path-fn best-invasion-target-and-path)
   pos
   unit))

(defn- apply-major-invasion-assignment!
  [pos unit]
  (let [t (:type unit)]
    (cond
      (= :fighter t)
      (major-invasion/apply-major-invasion-assignment! (invasion-ctx) pos unit)

      (major-invasion-ship-types t)
      (major-invasion/apply-major-invasion-assignment! (invasion-ctx) pos unit)

      (= :transport t)
      (prepare-transport-major-invasion! pos unit)

      (= :army t)
      (major-invasion/apply-major-invasion-assignment! (invasion-ctx) pos unit)

      :else nil)))

(defn- manager-ctx
  []
  (assoc (invasion-ctx)
         :chebyshev-distance-fn grid/chebyshev-distance
         :current-round-fn current-round
         :next-review-round-fn next-review-round
         :dec-threat-rounds-fn dec-threat-rounds
         :find-computer-unit-positions-fn find-computer-unit-positions
         :apply-major-invasion-assignment!-fn apply-major-invasion-assignment!
         :refresh-country-defense!-fn refresh-country-defense!
         :recompute-major-invasion-target-land!-fn recompute-major-invasion-target-land!
         :recompute-sea-reachable-detection-points!-fn recompute-sea-reachable-detection-points!))

(defn- current-round
  []
  (or (sa/read-state :round-number) 0))

(defn next-review-round
  []
  (+ (current-round) invasion-decision/review-interval-rounds))

(declare refresh-major-invasion-assignments!)
(declare apply-major-invasion-assignment!)
(declare manager-ctx)

(defn- handle-major-invasion-detection!
  [pos]
  (manager/handle-major-invasion-detection! (manager-ctx) pos))

(defn- handle-fighter-detection!
  [pos]
  (let [fighters (find-computer-unit-positions #(= :fighter (:type %)))
        selected (closest-positions pos fighters (threat-policy/fighter-response-count))]
    (assign-threat-mission!
     selected (threat-policy/fighter-sweep-mission pos))))

(defn- handle-ship-detection!
  [pos]
  (let [patrols (find-computer-unit-positions #(= :patrol-boat (:type %)))
        battleships (find-computer-unit-positions #(= :battleship (:type %)))
        psel (closest-positions pos patrols (threat-policy/ship-response-count))
        bsel (closest-positions pos battleships (threat-policy/ship-response-count))
        selected (concat psel bsel)]
    (assign-threat-mission! selected (threat-policy/sea-scout-mission pos))))

(defn- handle-country-defense-detection!
  [_pos]
  (refresh-country-defense!))

(defn handle-detection!
  "Handle a newly-visible cell on computer-map for threat triggers."
  [pos game-cell]
  (refresh-computer-map!)
  (let [{:keys [record-army-target? trigger]}
        (decisions/detection-action
         {:record-army-target? (and (major-invasion-active?)
                                    (= :army (get-in game-cell [:contents :type]))
                                    (= :player (get-in game-cell [:contents :owner])))
          :trigger (threat-policy/detection-trigger game-cell)})]
  (when record-army-target?
    (kamikazee/record-army-target! (invasion-ctx) pos))
  (case trigger
    :fighter-detected (handle-fighter-detection! pos)
    :ship-detected (handle-ship-detection! pos)
    :country-defense-trigger (handle-country-defense-detection! pos)
    :major-invasion-trigger (handle-major-invasion-detection! pos)
    nil)
  nil))

(defn refresh-major-invasion-assignments!
  "Applies major-invasion tags/targets to all mobilized computer units."
  []
  (refresh-computer-map!)
  (manager/refresh-major-invasion-assignments! (manager-ctx)))

(defn rebuild-kamikazee-routing!
  []
  (refresh-computer-map!)
  (manager/rebuild-kamikazee-routing! (manager-ctx)))

(defn launch-kamikazee-from-airport!
  [city-pos]
  (kamikazee/launch-kamikazee-from-airport! (invasion-ctx) city-pos))

(defn on-round-start!
  "Round-start maintenance for threat responses."
  []
  (refresh-computer-map!)
  (manager/on-round-start! (manager-ctx)))

(defn prepare-transport!
  "Called by transport processing; applies major-invasion directives when active."
  [pos]
  (when-let [unit (get-in (sa/read-state :computer-map) (conj pos :contents))]
    (when (= :prepare-transport
             (decisions/prepare-transport-action
              {:major-invasion-active? (major-invasion-active?)
               :unit unit}))
      (prepare-transport-major-invasion! pos unit)
      (visibility/sync-ai-unit-to-computer-map! pos)
      true)))

(defn fighter-step-threat
  [pos unit]
  (processing/fighter-step-threat
   {:current-world #(sa/read-state :computer-map)
    :update-game-map! sa/update-world!
    :sync-ai-unit! visibility/sync-ai-unit-to-computer-map!
    :nearest-major-target nearest-major-target
    :threat-radius (threat-radius)}
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

(defn- run-kamikazee-round
  [pos]
  (run-fighter-steps
   pos
   fighter-movement/fighter-speed
   (fn [current]
     (kamikazee/process-kamikazee-fighter
      (invasion-ctx)
      current
      (get-in (sa/read-state :computer-map) (conj current :contents))))))

(defn- run-standard-threat-round
  [pos unit]
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
          :fixed-carrier? (kamikazee/fixed-carrier? (load-major-invasion-state) pos)})
    :hold true
    (processing/process-ship-threat
     {:current-world #(sa/read-state :computer-map)
      :update-game-map! sa/update-world!
      :read-runtime-state sa/read-state
      :sync-ai-unit! visibility/sync-ai-unit-to-computer-map!
      :nearest-major-target nearest-major-ship-target
      :threat-radius (threat-radius)}
     pos
     ship-type
     unit)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T10:21:43.840436-05:00", :module-hash "2101796623", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 17, :hash "598401551"} {:id "defn-/threat-radius", :kind "defn-", :line 19, :end-line 20, :hash "-686955032"} {:id "form/2/declare", :kind "declare", :line 22, :end-line 22, :hash "-1182764633"} {:id "form/3/declare", :kind "declare", :line 23, :end-line 23, :hash "-946403673"} {:id "form/4/declare", :kind "declare", :line 24, :end-line 24, :hash "-212940382"} {:id "def/major-invasion-ship-types", :kind "def", :line 26, :end-line 27, :hash "893313069"} {:id "def/computer-sea-unit-types", :kind "def", :line 29, :end-line 30, :hash "-100623827"} {:id "def/max-invasion-coastal-candidates", :kind "def", :line 31, :end-line 31, :hash "-329358744"} {:id "def/preferred-invasion-landing-distance", :kind "def", :line 32, :end-line 32, :hash "-1028129861"} {:id "defn-/load-major-invasion-state", :kind "defn-", :line 34, :end-line 36, :hash "531746481"} {:id "defn-/save-major-invasion-state!", :kind "defn-", :line 38, :end-line 40, :hash "-397333665"} {:id "defn-/update-major-invasion-state!", :kind "defn-", :line 42, :end-line 46, :hash "-1902839705"} {:id "defn-/major-invasion-active?", :kind "defn-", :line 48, :end-line 50, :hash "-647526333"} {:id "defn-/major-invasion-detection-points", :kind "defn-", :line 52, :end-line 54, :hash "-1902808775"} {:id "defn/major-invasion-target-land?", :kind "defn", :line 56, :end-line 58, :hash "-92232189"} {:id "defn-/recompute-major-invasion-target-land!", :kind "defn-", :line 60, :end-line 62, :hash "1101578532"} {:id "defn-/find-computer-unit-positions", :kind "defn-", :line 64, :end-line 73, :hash "-1221492359"} {:id "defn-/assign-threat-mission!", :kind "defn-", :line 75, :end-line 78, :hash "-123381551"} {:id "defn-/closest-positions", :kind "defn-", :line 80, :end-line 84, :hash "1752103799"} {:id "defn-/nearest-major-target", :kind "defn-", :line 86, :end-line 88, :hash "439735275"} {:id "defn-/invasion-ctx", :kind "defn-", :line 90, :end-line 99, :hash "963265871"} {:id "defn-/nearest-major-ship-target", :kind "defn-", :line 101, :end-line 104, :hash "-974514867"} {:id "defn-/recompute-sea-reachable-detection-points!", :kind "defn-", :line 106, :end-line 108, :hash "176490866"} {:id "defn/major-invasion-target-revision", :kind "defn", :line 110, :end-line 112, :hash "320153715"} {:id "defn-/dec-threat-rounds", :kind "defn-", :line 114, :end-line 116, :hash "-276107214"} {:id "defn-/homeland-defense-unit?", :kind "defn-", :line 118, :end-line 123, :hash "267565727"} {:id "defn-/refresh-country-defense!", :kind "defn-", :line 125, :end-line 139, :hash "-1399606863"} {:id "defn-/nearest-major-sea-target", :kind "defn-", :line 141, :end-line 143, :hash "-2031328092"} {:id "defn-/connected-coastal-candidates", :kind "defn-", :line 145, :end-line 147, :hash "1188740324"} {:id "defn-/best-invasion-target-and-path", :kind "defn-", :line 149, :end-line 173, :hash "29262809"} {:id "defn-/prepare-transport-major-invasion!", :kind "defn-", :line 175, :end-line 182, :hash "2095088921"} {:id "defn-/apply-major-invasion-assignment!", :kind "defn-", :line 184, :end-line 200, :hash "45768367"} {:id "defn-/manager-ctx", :kind "defn-", :line 202, :end-line 213, :hash "1603883210"} {:id "defn-/current-round", :kind "defn-", :line 215, :end-line 217, :hash "-266289192"} {:id "defn-/next-review-round", :kind "defn-", :line 219, :end-line 221, :hash "-638599933"} {:id "form/35/declare", :kind "declare", :line 223, :end-line 223, :hash "11733860"} {:id "form/36/declare", :kind "declare", :line 224, :end-line 224, :hash "1865975433"} {:id "form/37/declare", :kind "declare", :line 225, :end-line 225, :hash "-1182764633"} {:id "defn-/handle-major-invasion-detection!", :kind "defn-", :line 227, :end-line 229, :hash "1695523913"} {:id "defn-/handle-fighter-detection!", :kind "defn-", :line 231, :end-line 236, :hash "-1262313150"} {:id "defn-/handle-ship-detection!", :kind "defn-", :line 238, :end-line 245, :hash "615273792"} {:id "defn-/handle-country-defense-detection!", :kind "defn-", :line 247, :end-line 249, :hash "1864857964"} {:id "defn/handle-detection!", :kind "defn", :line 251, :end-line 268, :hash "-889795849"} {:id "defn/refresh-major-invasion-assignments!", :kind "defn", :line 270, :end-line 273, :hash "599163401"} {:id "defn/rebuild-kamikazee-routing!", :kind "defn", :line 275, :end-line 277, :hash "399122879"} {:id "defn/launch-kamikazee-from-airport!", :kind "defn", :line 279, :end-line 281, :hash "-1741600319"} {:id "defn/on-round-start!", :kind "defn", :line 283, :end-line 286, :hash "-1596593207"} {:id "defn/prepare-transport!", :kind "defn", :line 288, :end-line 297, :hash "-198203592"} {:id "defn-/fighter-step-threat", :kind "defn-", :line 299, :end-line 307, :hash "1016079261"} {:id "defn-/run-fighter-steps", :kind "defn-", :line 309, :end-line 316, :hash "409096778"} {:id "defn-/run-kamikazee-round", :kind "defn-", :line 318, :end-line 327, :hash "-1856212374"} {:id "defn-/run-standard-threat-round", :kind "defn-", :line 329, :end-line 341, :hash "1304448325"} {:id "defn/process-fighter-threat", :kind "defn", :line 343, :end-line 351, :hash "430514376"} {:id "defn/process-ship-threat", :kind "defn", :line 353, :end-line 369, :hash "154677011"}]}
;; clj-mutate-manifest-end
