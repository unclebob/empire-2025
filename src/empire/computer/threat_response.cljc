(ns empire.computer.threat-response
  "Threat-response coordinator for enemy detections.
   Handles fighter/ship local responses and global major invasion mobilization."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.fighter-movement :as fighter-movement]
            [empire.computer.oscillation :as oscillation]
            [empire.computer.threat-response.kamikazee :as kamikazee]
            [empire.computer.threat-response.country-defense :as country-defense]
            [empire.computer.threat-response.invasion-decision :as invasion-decision]
            [empire.computer.threat-response.invasion-state :as invasion-state]
            [empire.computer.threat-response.major-invasion-manager :as manager]
            [empire.computer.threat-response.major-invasion :as major-invasion]
            [empire.computer.threat-response.processing :as processing]
            [empire.game-mechanics.services.threat-policy :as threat-policy]
            [empire.computer.movement :as computer-movement]))

(defn- threat-radius []
  (threat-policy/threat-radius))

(declare manager-ctx)
(declare current-round)
(declare next-review-round)

(def ^:private major-invasion-ship-types
  #{:patrol-boat :destroyer :submarine :carrier :battleship})

(def ^:private computer-sea-unit-types
  (conj major-invasion-ship-types :transport))
(def ^:private max-invasion-coastal-candidates 24)
(def ^:private preferred-invasion-landing-distance 8)

(defn- load-major-invasion-state
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

(defn- recompute-major-invasion-target-land!
  []
  (manager/recompute-major-invasion-target-land! (manager-ctx)))

(defn- find-computer-unit-positions
  [pred]
  (let [game-map (sa/current-world)]
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
    (sa/update-world! update-in (conj pos :contents) merge mission-kv)))

(defn- closest-positions
  [origin positions n]
  (->> positions
       (sort-by #(core/distance % origin))
       (take n)))

(defn- nearest-major-target
  [pos]
  (invasion-state/nearest-target (load-major-invasion-state) pos))

(defn- invasion-ctx
  []
  {:load-major-invasion-state load-major-invasion-state
   :update-major-invasion-state! update-major-invasion-state!
   :current-world sa/current-world
   :read-runtime-state sa/read-state
   :update-game-map! sa/update-world!
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

(defn- dec-threat-rounds
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
        game-map (sa/current-world)]
    (doseq [i (range (count game-map))
            j (range (count (first game-map)))
            :let [unit (get-in game-map [i j :contents])]
            :when (homeland-defense-unit? unit)]
      (let [cid (:country-id unit)
            targets (get targets-by-country cid)]
        (sa/update-world! update-in [i j :contents]
                          (if (seq targets)
                            #(country-defense/apply-country-defense % [i j] targets radius)
                            country-defense/clear-country-defense))))))

(defn- nearest-major-sea-target
  [pos]
  (major-invasion/nearest-major-sea-target (invasion-ctx) pos))

(defn- connected-coastal-candidates
  [computer-map state target]
  (major-invasion/connected-coastal-candidates computer-map state target))

(defn- best-invasion-target-and-path
  [pos target]
  (let [state (load-major-invasion-state)
        computer-map (sa/read-state :computer-map)
        all-candidates (connected-coastal-candidates computer-map state target)
        nearby-candidates (filter #(<= (core/chebyshev-distance % target)
                                       preferred-invasion-landing-distance)
                                  all-candidates)
        candidates-base (if (seq nearby-candidates) nearby-candidates all-candidates)
        candidates (->> candidates-base
                        (sort-by (fn [candidate]
                                   [(core/chebyshev-distance candidate target)
                                    candidate]))
                        (take max-invasion-coastal-candidates))
        scored (keep (fn [candidate]
                       (when-let [path (computer-movement/bfs-to-land-ho-target pos candidate computer-map)]
                         {:target candidate
                          :path (vec path)
                          :score [(core/chebyshev-distance candidate target)
                                  (count path)
                                  candidate]}))
                     candidates)]
    (when (seq scored)
      (let [{:keys [target path]} (first (sort-by :score scored))]
        {:target target :path path}))))

(defn- prepare-transport-major-invasion!
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
         :chebyshev-distance-fn core/chebyshev-distance
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

(defn- next-review-round
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
  (when (and (major-invasion-active?)
             (= :army (get-in game-cell [:contents :type]))
             (= :player (get-in game-cell [:contents :owner])))
    (kamikazee/record-army-target! (invasion-ctx) pos))
  (case (threat-policy/detection-trigger game-cell)
    :fighter-detected (handle-fighter-detection! pos)
    :ship-detected (handle-ship-detection! pos)
    :country-defense-trigger (handle-country-defense-detection! pos)
    :major-invasion-trigger (handle-major-invasion-detection! pos)
    nil)
  nil)

(defn refresh-major-invasion-assignments!
  "Applies major-invasion tags/targets to all mobilized computer units."
  []
  (manager/refresh-major-invasion-assignments! (manager-ctx)))

(defn rebuild-kamikazee-routing!
  []
  (manager/rebuild-kamikazee-routing! (manager-ctx)))

(defn on-round-start!
  "Round-start maintenance for threat responses."
  []
  (manager/on-round-start! (manager-ctx)))

(defn prepare-transport!
  "Called by transport processing; applies major-invasion directives when active."
  [pos]
  (when (major-invasion-active?)
    (when-let [unit (get-in (sa/current-world) (conj pos :contents))]
      (when (= :transport (:type unit))
        (prepare-transport-major-invasion! pos unit)
        true))))

(defn- fighter-step-threat
  [pos unit]
  (processing/fighter-step-threat
   {:current-world sa/current-world
    :update-game-map! sa/update-world!
    :nearest-major-target nearest-major-target
    :threat-radius (threat-radius)}
   pos
   unit))

(defn- fighter-threat-active?
  [unit]
  (or (= :fighter-sweep (:threat-mission unit))
      (= :country-defense (:threat-mission unit))
      (:major-invasion unit)))

(defn- run-fighter-steps
  [pos speed step-fn]
  (loop [current pos
         remaining speed]
    (when (pos? remaining)
      (when-let [{:keys [pos steps-used hops]} (step-fn current)]
        (recur pos (- remaining (or steps-used hops 1)))))))

(defn- run-kamikazee-round
  [pos]
  (run-fighter-steps
   pos
   fighter-movement/fighter-speed
   (fn [current]
     (kamikazee/process-kamikazee-fighter
      (invasion-ctx)
      current
      (get-in (sa/current-world) (conj current :contents))))))

(defn- run-standard-threat-round
  [pos unit]
  (if (oscillation/in-random-walk? unit)
    (processing/process-fighter-random-walk-round
     {:current-world sa/current-world
      :update-game-map! sa/update-world!}
     pos)
    (run-fighter-steps
     pos
     fighter-movement/fighter-speed
     (fn [current]
       (fighter-step-threat current
                            (get-in (sa/current-world) (conj current :contents)))))))

(defn process-fighter-threat
  "Overrides regular fighter logic while fighter-sweep/country-defense or major-invasion mission is active.
   Returns true when handled."
  [pos unit]
  (when (fighter-threat-active? unit)
    (if (:kamikazee unit)
      (run-kamikazee-round pos)
      (run-standard-threat-round pos unit))
    true))

(defn process-ship-threat
  "Overrides regular ship logic for sea-scout and major-invasion missions.
   Returns true when handled."
  [pos ship-type unit]
  (if (and (= :carrier ship-type)
           (:major-invasion unit)
           (kamikazee/fixed-carrier? (load-major-invasion-state) pos))
    true
    (processing/process-ship-threat
     {:current-world sa/current-world
      :update-game-map! sa/update-world!
      :nearest-major-target nearest-major-ship-target
      :threat-radius (threat-radius)}
     pos
     ship-type
     unit)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-14T10:32:07.822415-05:00", :module-hash "373625146", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 16, :hash "-931320246"} {:id "defn-/threat-radius", :kind "defn-", :line 18, :end-line 19, :hash "-686955032"} {:id "form/2/declare", :kind "declare", :line 21, :end-line 21, :hash "-1182764633"} {:id "form/3/declare", :kind "declare", :line 22, :end-line 22, :hash "-946403673"} {:id "form/4/declare", :kind "declare", :line 23, :end-line 23, :hash "-212940382"} {:id "def/major-invasion-ship-types", :kind "def", :line 25, :end-line 26, :hash "893313069"} {:id "def/computer-sea-unit-types", :kind "def", :line 28, :end-line 29, :hash "-100623827"} {:id "def/max-invasion-coastal-candidates", :kind "def", :line 30, :end-line 30, :hash "-329358744"} {:id "def/preferred-invasion-landing-distance", :kind "def", :line 31, :end-line 31, :hash "-1028129861"} {:id "defn-/load-major-invasion-state", :kind "defn-", :line 33, :end-line 35, :hash "531746481"} {:id "defn-/save-major-invasion-state!", :kind "defn-", :line 37, :end-line 39, :hash "-397333665"} {:id "defn-/update-major-invasion-state!", :kind "defn-", :line 41, :end-line 45, :hash "-1902839705"} {:id "defn-/major-invasion-active?", :kind "defn-", :line 47, :end-line 49, :hash "-647526333"} {:id "defn-/major-invasion-detection-points", :kind "defn-", :line 51, :end-line 53, :hash "-1902808775"} {:id "defn/major-invasion-target-land?", :kind "defn", :line 55, :end-line 57, :hash "-92232189"} {:id "defn-/recompute-major-invasion-target-land!", :kind "defn-", :line 59, :end-line 61, :hash "1101578532"} {:id "defn-/find-computer-unit-positions", :kind "defn-", :line 63, :end-line 72, :hash "-1221492359"} {:id "defn-/assign-threat-mission!", :kind "defn-", :line 74, :end-line 77, :hash "-123381551"} {:id "defn-/closest-positions", :kind "defn-", :line 79, :end-line 83, :hash "1752103799"} {:id "defn-/nearest-major-target", :kind "defn-", :line 85, :end-line 87, :hash "439735275"} {:id "defn-/invasion-ctx", :kind "defn-", :line 89, :end-line 98, :hash "963265871"} {:id "defn-/nearest-major-ship-target", :kind "defn-", :line 100, :end-line 103, :hash "-974514867"} {:id "defn-/recompute-sea-reachable-detection-points!", :kind "defn-", :line 105, :end-line 107, :hash "176490866"} {:id "defn/major-invasion-target-revision", :kind "defn", :line 109, :end-line 111, :hash "320153715"} {:id "defn-/dec-threat-rounds", :kind "defn-", :line 113, :end-line 115, :hash "-276107214"} {:id "defn-/homeland-defense-unit?", :kind "defn-", :line 117, :end-line 122, :hash "267565727"} {:id "defn-/refresh-country-defense!", :kind "defn-", :line 124, :end-line 138, :hash "-1399606863"} {:id "defn-/nearest-major-sea-target", :kind "defn-", :line 140, :end-line 142, :hash "-2031328092"} {:id "defn-/connected-coastal-candidates", :kind "defn-", :line 144, :end-line 146, :hash "1188740324"} {:id "defn-/best-invasion-target-and-path", :kind "defn-", :line 148, :end-line 172, :hash "29262809"} {:id "defn-/prepare-transport-major-invasion!", :kind "defn-", :line 174, :end-line 181, :hash "2095088921"} {:id "defn-/apply-major-invasion-assignment!", :kind "defn-", :line 183, :end-line 199, :hash "45768367"} {:id "defn-/manager-ctx", :kind "defn-", :line 201, :end-line 212, :hash "1603883210"} {:id "defn-/current-round", :kind "defn-", :line 214, :end-line 216, :hash "-266289192"} {:id "defn-/next-review-round", :kind "defn-", :line 218, :end-line 220, :hash "-638599933"} {:id "form/35/declare", :kind "declare", :line 222, :end-line 222, :hash "11733860"} {:id "form/36/declare", :kind "declare", :line 223, :end-line 223, :hash "1865975433"} {:id "form/37/declare", :kind "declare", :line 224, :end-line 224, :hash "-1182764633"} {:id "defn-/handle-major-invasion-detection!", :kind "defn-", :line 226, :end-line 228, :hash "1695523913"} {:id "defn-/handle-fighter-detection!", :kind "defn-", :line 230, :end-line 235, :hash "-1262313150"} {:id "defn-/handle-ship-detection!", :kind "defn-", :line 237, :end-line 244, :hash "615273792"} {:id "defn-/handle-country-defense-detection!", :kind "defn-", :line 246, :end-line 248, :hash "1864857964"} {:id "defn/handle-detection!", :kind "defn", :line 250, :end-line 263, :hash "244876381"} {:id "defn/refresh-major-invasion-assignments!", :kind "defn", :line 265, :end-line 268, :hash "599163401"} {:id "defn/rebuild-kamikazee-routing!", :kind "defn", :line 270, :end-line 272, :hash "399122879"} {:id "defn/on-round-start!", :kind "defn", :line 274, :end-line 277, :hash "-1596593207"} {:id "defn/prepare-transport!", :kind "defn", :line 279, :end-line 286, :hash "-1674860881"} {:id "defn-/fighter-step-threat", :kind "defn-", :line 288, :end-line 296, :hash "1016079261"} {:id "defn-/fighter-threat-active?", :kind "defn-", :line 298, :end-line 302, :hash "-1056646997"} {:id "defn-/run-fighter-steps", :kind "defn-", :line 304, :end-line 310, :hash "722881588"} {:id "defn-/run-kamikazee-round", :kind "defn-", :line 312, :end-line 321, :hash "-1856212374"} {:id "defn-/run-standard-threat-round", :kind "defn-", :line 323, :end-line 335, :hash "1304448325"} {:id "defn/process-fighter-threat", :kind "defn", :line 337, :end-line 345, :hash "-167523110"} {:id "defn/process-ship-threat", :kind "defn", :line 347, :end-line 362, :hash "-354486157"}]}
;; clj-mutate-manifest-end
