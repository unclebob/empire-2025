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
            [empire.computer.threat-response.major-invasion :as major-invasion]
            [empire.computer.threat-response.processing :as processing]
            [empire.game-mechanics.services.threat-policy :as threat-policy]
            [empire.computer.movement :as computer-movement]))

(defn- threat-radius []
  (threat-policy/threat-radius))

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
  (let [state (load-major-invasion-state)
        target-land (invasion-state/recompute-target-land
                     (sa/current-world)
                     (:detection-points state))
        current-target-land (:target-land-set state)
        changed? (not= current-target-land target-land)
        next-revision (if changed?
                        (inc (or (:target-land-revision state) 0))
                        (or (:target-land-revision state) 0))]
    (update-major-invasion-state! assoc
                                  :target-land-set target-land
                                  :target-land-revision next-revision)))

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

(defn- current-round
  []
  (or (sa/read-state :round-number) 0))

(defn- next-review-round
  []
  (+ (current-round) invasion-decision/review-interval-rounds))

(defn- mission-needs-reset?
  [unit]
  (and (= :transport (:type unit))
       (#{:invading :unloading :load-for-invasion :find-armies-for-invasion}
        (:transport-mission unit))))

(declare refresh-major-invasion-assignments!)

(defn- clear-major-invasion-from-unit
  [unit]
  (let [base (dissoc unit :major-invasion
                     :major-invasion-target
                     :kamikazee
                     :kamikazee-targets
                     :kamikazee-route
                     :kamikazee-terminal-site
                     :kamikazee-stage
                     :kamikazee-wait-site
                     :kamikazee-trail)]
    (if (= :transport (:type base))
      (let [transport (-> base
                          (dissoc :major-invasion-find-armies-round
                                  :major-invasion-skip-revision
                                  :invasion-target
                                  :invasion-path
                                  :invasion-path-origin
                                  :invasion-plan-revision
                                  :invasion-load-since))]
        (if (mission-needs-reset? transport)
          (assoc transport :transport-mission :sailing)
          transport))
      base)))

(defn- stand-down-major-invasion!
  [failure-reason]
  (let [game-map (sa/current-world)]
    (doseq [x (range (count game-map))
            y (range (count (first game-map)))
            :let [unit (get-in game-map [x y :contents])]
            :when (and unit
                       (= :computer (:owner unit))
                       (or (:major-invasion unit)
                           (mission-needs-reset? unit)))]
      (sa/update-world! update-in [x y :contents] clear-major-invasion-from-unit)))
  (update-major-invasion-state! assoc
                                :active? false
                                :decision :deferred
                                :failure-reason failure-reason
                                :next-review-round (next-review-round)
                                :first-landing-round nil))

(defn- force-patrol-boat-exploration!
  []
  (doseq [pos (find-computer-unit-positions #(= :patrol-boat (:type %)))]
    (sa/update-world! update-in (conj pos :contents)
                      #(-> %
                           (assoc :patrol-mode :exploring)
                           (dissoc :explore-path)))))

(defn- evaluate-major-invasion-start!
  []
  (let [state (load-major-invasion-state)
        evaluation (invasion-decision/evaluate-invasion-start
                    {:world (sa/current-world)
                     :computer-map (sa/read-state :computer-map)
                     :detection-points (:detection-points state)
                     :computer-sea-unit-types computer-sea-unit-types})]
    (if (= :ready (:decision evaluation))
      (do
        (update-major-invasion-state! assoc
                                      :active? true
                                      :decision :ready
                                      :failure-reason nil
                                      :next-review-round nil
                                      :first-landing-round nil
                                      :sea-reachable-detection-points
                                      (:sea-reachable-detection-points evaluation))
        (recompute-major-invasion-target-land!)
        (recompute-sea-reachable-detection-points!)
        (refresh-major-invasion-assignments!))
      (update-major-invasion-state! assoc
                                    :active? false
                                    :decision :deferred
                                    :failure-reason (:failure-reason evaluation)
                                    :next-review-round (next-review-round)
                                    :first-landing-round nil
                                    :sea-reachable-detection-points
                                    (:sea-reachable-detection-points evaluation)))))

(defn- maybe-record-major-invasion-detection!
  [pos]
  (let [state (load-major-invasion-state)
        nearby-existing? (some #(<= (core/chebyshev-distance pos %) 2)
                               (:detection-points state))
        should-add? (or (not (:active? state))
                        (not nearby-existing?))]
    (when should-add?
      (update-major-invasion-state!
       (fn [s]
         (-> s
             (update :detection-points conj pos)
             (assoc :started-round (or (:started-round s) (current-round)))))))
    should-add?))

(defn- handle-major-invasion-detection!
  [pos]
  (when (maybe-record-major-invasion-detection! pos)
    (if (:active? (load-major-invasion-state))
      (do
        (recompute-major-invasion-target-land!)
        (recompute-sea-reachable-detection-points!)
        (refresh-major-invasion-assignments!))
      (when (nil? (:decision (load-major-invasion-state)))
        (evaluate-major-invasion-start!)))))

(defn- maybe-handle-unsustainable-losses!
  []
  (let [state (load-major-invasion-state)
        target-land-set (:target-land-set state)]
    (when (and (:active? state)
               (seq target-land-set))
      (let [world (sa/current-world)
            armies-on-target (invasion-decision/invasion-armies-on-target-continent world target-land-set)]
        (when (and (nil? (:first-landing-round state))
                   (pos? armies-on-target))
          (update-major-invasion-state! assoc :first-landing-round (current-round)))
        (let [updated-state (load-major-invasion-state)
              armies-in-transit (invasion-decision/armies-in-transports-to-target-continent
                                 world
                                 target-land-set)]
          (when (and (:first-landing-round updated-state)
                     (zero? armies-on-target)
                     (zero? armies-in-transit))
            (stand-down-major-invasion! :unsustainable-losses)))))))

(defn- maybe-review-deferred-major-invasion!
  []
  (let [state (load-major-invasion-state)]
    (when (and (= :deferred (:decision state))
               (#{:no-sea-path :insufficient-resources :unsustainable-losses}
                (:failure-reason state))
               (number? (:next-review-round state))
               (>= (current-round) (:next-review-round state)))
      (evaluate-major-invasion-start!))))

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
  (when (major-invasion-active?)
    (let [units (find-computer-unit-positions (constantly true))
          world (sa/current-world)]
      (update-major-invasion-state! assoc :kamikazee-terminal-sites #{})
      (doseq [pos units
              :let [unit (get-in world (conj pos :contents))]
              :when (= :fighter (:type unit))]
        (apply-major-invasion-assignment! pos unit))
      (kamikazee/refresh-army-targets! (invasion-ctx))
      (doseq [pos units
              :let [unit (get-in world (conj pos :contents))]
              :when (and unit (not= :fighter (:type unit)))]
        (apply-major-invasion-assignment! pos unit)))))

(defn on-round-start!
  "Round-start maintenance for threat responses."
  []
  (let [game-map (sa/current-world)]
    (doseq [i (range (count game-map))
            j (range (count (first game-map)))
            :let [unit (get-in game-map [i j :contents])]
            :when (and unit
                       (= :computer (:owner unit))
                       (:threat-mission unit))]
      (sa/update-world! update-in [i j :contents] dec-threat-rounds)))
  (when (major-invasion-active?)
    (recompute-major-invasion-target-land!)
    (recompute-sea-reachable-detection-points!)
    (kamikazee/refresh-army-targets! (invasion-ctx))
    (maybe-handle-unsustainable-losses!)
    (when (major-invasion-active?)
      (refresh-major-invasion-assignments!)))
  (refresh-country-defense!)
  (maybe-review-deferred-major-invasion!)
  (when (= :no-sea-path (:failure-reason (load-major-invasion-state)))
    (force-patrol-boat-exploration!)))

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

(defn process-fighter-threat
  "Overrides regular fighter logic while fighter-sweep/country-defense or major-invasion mission is active.
   Returns true when handled."
  [pos unit]
  (when (or (= :fighter-sweep (:threat-mission unit))
            (= :country-defense (:threat-mission unit))
            (:major-invasion unit))
    (if (:kamikazee unit)
      (loop [current pos
             remaining fighter-movement/fighter-speed]
        (when (pos? remaining)
          (when-let [{:keys [pos steps-used]}
                     (kamikazee/process-kamikazee-fighter
                      (invasion-ctx)
                      current
                      (get-in (sa/current-world) (conj current :contents)))]
            (recur pos (- remaining steps-used)))))
      (if (oscillation/in-random-walk? unit)
        (processing/process-fighter-random-walk-round
         {:current-world sa/current-world
          :update-game-map! sa/update-world!}
         pos)
        (loop [current pos
               remaining fighter-movement/fighter-speed]
          (when (pos? remaining)
            (when-let [{:keys [pos steps-used]}
                       (fighter-step-threat current (get-in (sa/current-world) (conj current :contents)))]
              (recur pos (- remaining steps-used)))))))
    true))

(defn process-ship-threat
  "Overrides regular ship logic for sea-scout and major-invasion missions.
   Returns true when handled."
  [pos ship-type unit]
  (processing/process-ship-threat
   {:current-world sa/current-world
    :update-game-map! sa/update-world!
    :nearest-major-target nearest-major-ship-target
    :threat-radius (threat-radius)}
   pos
   ship-type
   unit))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:58:36.790138-05:00", :module-hash "-1014450785", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 14, :hash "1114461074"} {:id "defn-/threat-radius", :kind "defn-", :line 16, :end-line 17, :hash "-686955032"} {:id "def/major-invasion-ship-types", :kind "def", :line 19, :end-line 20, :hash "893313069"} {:id "def/computer-sea-unit-types", :kind "def", :line 22, :end-line 23, :hash "-100623827"} {:id "def/max-invasion-coastal-candidates", :kind "def", :line 24, :end-line 24, :hash "-329358744"} {:id "def/preferred-invasion-landing-distance", :kind "def", :line 25, :end-line 25, :hash "-1028129861"} {:id "defn-/load-major-invasion-state", :kind "defn-", :line 27, :end-line 29, :hash "531746481"} {:id "defn-/save-major-invasion-state!", :kind "defn-", :line 31, :end-line 33, :hash "-397333665"} {:id "defn-/update-major-invasion-state!", :kind "defn-", :line 35, :end-line 39, :hash "-1902839705"} {:id "defn-/major-invasion-active?", :kind "defn-", :line 41, :end-line 43, :hash "-647526333"} {:id "defn-/major-invasion-detection-points", :kind "defn-", :line 45, :end-line 47, :hash "-1902808775"} {:id "defn/major-invasion-target-land?", :kind "defn", :line 49, :end-line 51, :hash "-92232189"} {:id "defn-/recompute-major-invasion-target-land!", :kind "defn-", :line 53, :end-line 66, :hash "1763848680"} {:id "defn-/find-computer-unit-positions", :kind "defn-", :line 68, :end-line 77, :hash "-1221492359"} {:id "defn-/assign-threat-mission!", :kind "defn-", :line 79, :end-line 82, :hash "-123381551"} {:id "defn-/closest-positions", :kind "defn-", :line 84, :end-line 88, :hash "1452837897"} {:id "defn-/nearest-major-target", :kind "defn-", :line 90, :end-line 92, :hash "439735275"} {:id "defn-/invasion-ctx", :kind "defn-", :line 94, :end-line 103, :hash "963265871"} {:id "defn-/nearest-major-ship-target", :kind "defn-", :line 105, :end-line 108, :hash "-974514867"} {:id "defn-/recompute-sea-reachable-detection-points!", :kind "defn-", :line 110, :end-line 112, :hash "176490866"} {:id "defn/major-invasion-target-revision", :kind "defn", :line 114, :end-line 116, :hash "320153715"} {:id "defn-/dec-threat-rounds", :kind "defn-", :line 118, :end-line 120, :hash "-276107214"} {:id "defn-/homeland-defense-unit?", :kind "defn-", :line 122, :end-line 127, :hash "267565727"} {:id "defn-/refresh-country-defense!", :kind "defn-", :line 129, :end-line 143, :hash "2043575809"} {:id "defn-/nearest-major-sea-target", :kind "defn-", :line 145, :end-line 147, :hash "-2031328092"} {:id "defn-/connected-coastal-candidates", :kind "defn-", :line 149, :end-line 151, :hash "1188740324"} {:id "defn-/best-invasion-target-and-path", :kind "defn-", :line 153, :end-line 177, :hash "-1865847068"} {:id "defn-/prepare-transport-major-invasion!", :kind "defn-", :line 179, :end-line 186, :hash "2095088921"} {:id "defn-/apply-major-invasion-assignment!", :kind "defn-", :line 188, :end-line 208, :hash "1270595669"} {:id "defn-/current-round", :kind "defn-", :line 210, :end-line 212, :hash "-266289192"} {:id "defn-/next-review-round", :kind "defn-", :line 214, :end-line 216, :hash "-638599933"} {:id "defn-/mission-needs-reset?", :kind "defn-", :line 218, :end-line 222, :hash "2076506354"} {:id "form/32/declare", :kind "declare", :line 224, :end-line 224, :hash "11733860"} {:id "defn-/clear-major-invasion-from-unit", :kind "defn-", :line 226, :end-line 241, :hash "-1397595053"} {:id "defn-/stand-down-major-invasion!", :kind "defn-", :line 243, :end-line 259, :hash "-1206086988"} {:id "defn-/force-patrol-boat-exploration!", :kind "defn-", :line 261, :end-line 267, :hash "577731169"} {:id "defn-/evaluate-major-invasion-start!", :kind "defn-", :line 269, :end-line 297, :hash "-906560618"} {:id "defn-/maybe-record-major-invasion-detection!", :kind "defn-", :line 299, :end-line 312, :hash "-1852301740"} {:id "defn-/handle-major-invasion-detection!", :kind "defn-", :line 314, :end-line 323, :hash "1674406168"} {:id "defn-/maybe-handle-unsustainable-losses!", :kind "defn-", :line 325, :end-line 343, :hash "102341245"} {:id "defn-/maybe-review-deferred-major-invasion!", :kind "defn-", :line 345, :end-line 353, :hash "1879271684"} {:id "defn-/handle-fighter-detection!", :kind "defn-", :line 355, :end-line 360, :hash "261704964"} {:id "defn-/handle-ship-detection!", :kind "defn-", :line 362, :end-line 369, :hash "-475811477"} {:id "defn-/handle-country-defense-detection!", :kind "defn-", :line 371, :end-line 373, :hash "1864857964"} {:id "defn/handle-detection!", :kind "defn", :line 375, :end-line 384, :hash "-630652861"} {:id "defn/refresh-major-invasion-assignments!", :kind "defn", :line 386, :end-line 395, :hash "280362517"} {:id "defn/on-round-start!", :kind "defn", :line 397, :end-line 417, :hash "2076254402"} {:id "defn/prepare-transport!", :kind "defn", :line 419, :end-line 426, :hash "-1674860881"} {:id "defn-/fighter-step-threat", :kind "defn-", :line 428, :end-line 436, :hash "1016079261"} {:id "defn/process-fighter-threat", :kind "defn", :line 438, :end-line 456, :hash "-1415314909"} {:id "defn/process-ship-threat", :kind "defn", :line 458, :end-line 469, :hash "-656962398"}]}
;; clj-mutate-manifest-end
