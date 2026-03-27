(ns empire.computer.threat-response.refresh
  "State management, invasion context, and round-boundary refresh logic."
  (:require [empire.state.api :as sa]
            [empire.computer.shared.grid :as grid]
            [empire.computer.threat-response.invasion-decision :as invasion-decision]
            [empire.computer.threat-response.invasion-state :as invasion-state]
            [empire.computer.threat-response.kamikazee :as kamikazee]
            [empire.computer.threat-response.major-invasion :as major-invasion]
            [empire.computer.threat-response.major-invasion-manager :as manager]
            [empire.game-mechanics.services.threat-policy :as threat-policy]
            [empire.game-mechanics.visibility :as visibility]))

(defn threat-radius []
  (threat-policy/threat-radius))

(defn refresh-computer-map! []
  (visibility/refresh-visible-map! :computer))

(def ^:private major-invasion-ship-types
  #{:patrol-boat :destroyer :submarine :carrier :battleship})

(def ^:private computer-sea-unit-types
  (conj major-invasion-ship-types :transport))

(defn load-major-invasion-state []
  (sa/read-state :major-invasion-state))

(defn- save-major-invasion-state! [state]
  (sa/write-state! :major-invasion-state state))

(defn- update-major-invasion-state! [f & args]
  (let [current (load-major-invasion-state)
        next-state (apply f current args)]
    (save-major-invasion-state! next-state)))

(defn major-invasion-active? []
  (:active? (load-major-invasion-state)))

(defn major-invasion-detection-points []
  (:detection-points (load-major-invasion-state)))

(defn major-invasion-target-land? [pos]
  (contains? (:target-land-set (load-major-invasion-state)) pos))

(defn- current-round []
  (or (sa/read-state :round-number) 0))

(defn next-review-round []
  (+ (current-round) invasion-decision/review-interval-rounds))

(defn dec-threat-rounds [unit]
  (threat-policy/dec-threat-rounds unit))

(defn nearest-major-target [pos]
  (invasion-state/nearest-target (load-major-invasion-state) pos))

(defn invasion-ctx []
  {:load-major-invasion-state load-major-invasion-state
   :update-major-invasion-state! update-major-invasion-state!
   :current-world #(sa/read-state :computer-map)
   :read-runtime-state sa/read-state
   :update-game-map! sa/update-world!
   :sync-ai-unit! visibility/sync-ai-unit-to-computer-map!
   :nearest-major-target nearest-major-target
   :major-invasion-ship-types major-invasion-ship-types
   :computer-sea-unit-types computer-sea-unit-types})

(defn nearest-major-sea-target [pos]
  (major-invasion/nearest-major-sea-target (invasion-ctx) pos))

(defn nearest-major-ship-target [pos]
  (or (nearest-major-sea-target pos)
      (nearest-major-target pos)))

(defn- recompute-sea-reachable-detection-points! []
  (major-invasion/recompute-sea-reachable-detection-points! (invasion-ctx)))

(defn major-invasion-target-revision []
  (major-invasion/major-invasion-target-revision (invasion-ctx)))

(defn connected-coastal-candidates [computer-map state target]
  (major-invasion/connected-coastal-candidates computer-map state target))

(defn best-invasion-target-and-path [pos target]
  (major-invasion/best-invasion-target-and-path (invasion-ctx) pos target))

(defn prepare-transport-major-invasion! [pos unit]
  (major-invasion/prepare-transport-major-invasion!
   (assoc (invasion-ctx)
          :nearest-major-sea-target-fn nearest-major-sea-target
          :best-invasion-target-and-path-fn best-invasion-target-and-path)
   pos
   unit))

(defn- apply-major-invasion-assignment! [pos unit]
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

(defn launch-kamikazee-from-airport! [city-pos]
  (kamikazee/launch-kamikazee-from-airport! (invasion-ctx) city-pos))

(defn manager-ctx
  "Builds the manager context. Accepts detection-specific fns to merge in."
  [detection-fns]
  (assoc (invasion-ctx)
         :chebyshev-distance-fn grid/chebyshev-distance
         :current-round-fn current-round
         :next-review-round-fn next-review-round
         :dec-threat-rounds-fn dec-threat-rounds
         :apply-major-invasion-assignment!-fn apply-major-invasion-assignment!
         :recompute-sea-reachable-detection-points!-fn recompute-sea-reachable-detection-points!
         :find-computer-unit-positions-fn (:find-computer-unit-positions-fn detection-fns)
         :refresh-country-defense!-fn (:refresh-country-defense!-fn detection-fns)
         :recompute-major-invasion-target-land!-fn (:recompute-major-invasion-target-land!-fn detection-fns)))

(defn refresh-major-invasion-assignments!
  "Applies major-invasion tags/targets to all mobilized computer units."
  [mgr-ctx]
  (refresh-computer-map!)
  (manager/refresh-major-invasion-assignments! mgr-ctx))

(defn rebuild-kamikazee-routing! [mgr-ctx]
  (refresh-computer-map!)
  (manager/rebuild-kamikazee-routing! mgr-ctx))

(defn on-round-start! [mgr-ctx]
  (refresh-computer-map!)
  (manager/on-round-start! mgr-ctx))

(defn recompute-major-invasion-target-land! [mgr-ctx]
  (manager/recompute-major-invasion-target-land! mgr-ctx))

(defn handle-major-invasion-detection! [mgr-ctx pos]
  (manager/handle-major-invasion-detection! mgr-ctx pos))

(defn record-army-target! [pos]
  (kamikazee/record-army-target! (invasion-ctx) pos))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T10:15:03.70741-05:00", :module-hash "-1561310794", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 11, :hash "1030765093"} {:id "defn/threat-radius", :kind "defn", :line 13, :end-line 14, :hash "-623660641"} {:id "defn/refresh-computer-map!", :kind "defn", :line 16, :end-line 17, :hash "-1912779426"} {:id "def/major-invasion-ship-types", :kind "def", :line 19, :end-line 20, :hash "893313069"} {:id "def/computer-sea-unit-types", :kind "def", :line 22, :end-line 23, :hash "-100623827"} {:id "defn/load-major-invasion-state", :kind "defn", :line 25, :end-line 26, :hash "-390718744"} {:id "defn-/save-major-invasion-state!", :kind "defn-", :line 28, :end-line 29, :hash "-397333665"} {:id "defn-/update-major-invasion-state!", :kind "defn-", :line 31, :end-line 34, :hash "-1902839705"} {:id "defn/major-invasion-active?", :kind "defn", :line 36, :end-line 37, :hash "1915881370"} {:id "defn/major-invasion-detection-points", :kind "defn", :line 39, :end-line 40, :hash "936792017"} {:id "defn/major-invasion-target-land?", :kind "defn", :line 42, :end-line 43, :hash "-92232189"} {:id "defn-/current-round", :kind "defn-", :line 45, :end-line 46, :hash "-266289192"} {:id "defn/next-review-round", :kind "defn", :line 48, :end-line 49, :hash "683611648"} {:id "defn/dec-threat-rounds", :kind "defn", :line 51, :end-line 52, :hash "-1836381503"} {:id "defn/nearest-major-target", :kind "defn", :line 54, :end-line 55, :hash "-593896567"} {:id "defn/invasion-ctx", :kind "defn", :line 57, :end-line 66, :hash "943527243"} {:id "defn/nearest-major-sea-target", :kind "defn", :line 68, :end-line 69, :hash "1325716562"} {:id "defn/nearest-major-ship-target", :kind "defn", :line 71, :end-line 73, :hash "408368133"} {:id "defn-/recompute-sea-reachable-detection-points!", :kind "defn-", :line 75, :end-line 76, :hash "176490866"} {:id "defn/major-invasion-target-revision", :kind "defn", :line 78, :end-line 79, :hash "320153715"} {:id "defn/connected-coastal-candidates", :kind "defn", :line 81, :end-line 82, :hash "1523738296"} {:id "defn/best-invasion-target-and-path", :kind "defn", :line 84, :end-line 85, :hash "-1379281990"} {:id "defn/prepare-transport-major-invasion!", :kind "defn", :line 87, :end-line 93, :hash "-1613178822"} {:id "defn-/apply-major-invasion-assignment!", :kind "defn-", :line 95, :end-line 110, :hash "45768367"} {:id "defn/launch-kamikazee-from-airport!", :kind "defn", :line 112, :end-line 113, :hash "-1741600319"} {:id "defn/manager-ctx", :kind "defn", :line 115, :end-line 127, :hash "780568155"} {:id "defn/refresh-major-invasion-assignments!", :kind "defn", :line 129, :end-line 133, :hash "1799690499"} {:id "defn/rebuild-kamikazee-routing!", :kind "defn", :line 135, :end-line 137, :hash "-1073596792"} {:id "defn/on-round-start!", :kind "defn", :line 139, :end-line 141, :hash "1893942802"} {:id "defn/recompute-major-invasion-target-land!", :kind "defn", :line 143, :end-line 144, :hash "197922668"} {:id "defn/handle-major-invasion-detection!", :kind "defn", :line 146, :end-line 147, :hash "-1344366993"} {:id "defn/record-army-target!", :kind "defn", :line 149, :end-line 150, :hash "-749963171"}]}
;; clj-mutate-manifest-end
