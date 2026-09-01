(ns empire.computer.transport.mission-handlers.lake
  (:require [empire.computer.transport.core :as tc]
            [empire.computer.transport.mission-handler-decisions :as handler-decisions]))

(defn- noop-sync! [_])

(defn- land-lock-transport!
  [update-game-map! sync-transport! read-map pos]
  (let [from-mission (get-in (read-map) (conj pos :contents :transport-mission))]
    (update-game-map! update-in (conj pos :contents)
                      #(assoc % :mode :sentry
                              :transport-mission :land-locked))
    (sync-transport! pos)
    (tc/log-transport-mission-transition! pos from-mission :land-locked)))

(defn- retreat-or-land-lock-empty-transport!
  [{:keys [update-game-map! move-unit-to retreat-step-from-shore deep-water?]}
   read-map sync-transport! pos lake-cells-set]
  (if-let [step (retreat-step-from-shore (read-map) lake-cells-set pos)]
    (if (move-unit-to pos step)
      (when (deep-water? (read-map) step)
        (land-lock-transport! update-game-map! sync-transport! read-map step))
      (land-lock-transport! update-game-map! sync-transport! read-map pos))
    (land-lock-transport! update-game-map! sync-transport! read-map pos)))

(defn park-lake-transport-if-empty
  [{:keys [current-world read-computer-map sync-transport!] :as deps}
   pos lake-cells-set]
  (let [read-map (or read-computer-map current-world)
        sync-transport! (or sync-transport! noop-sync!)
        unit (get-in (read-map) (conj pos :contents))]
    (if (zero? (:army-count unit 0))
      (retreat-or-land-lock-empty-transport! deps read-map sync-transport! pos lake-cells-set)
      false)))

(defn process-land-locked-mission
  [{:keys [current-world
           read-computer-map
           process-unloading-crawl
           try-opportunistic-unload-any-land]
    :as deps}
   pos lake-cells-set]
  (let [unloaded-now? (boolean (try-opportunistic-unload-any-land pos))]
    (or (park-lake-transport-if-empty deps pos lake-cells-set)
        (let [read-map (or read-computer-map current-world)
              unit (get-in (read-map) (conj pos :contents))
              army-count (:army-count unit 0)]
          (when (pos? army-count)
            (if-let [next-pos (process-unloading-crawl pos)]
              (do
                (try-opportunistic-unload-any-land next-pos)
                (park-lake-transport-if-empty deps next-pos lake-cells-set))
              unloaded-now?))))))

(defn fix-idle-mission
  [set-transport-mission pos mission]
  (when (or (nil? mission) (= :idle mission))
    (set-transport-mission pos :sail-to-load)))

(defn- lake-cells-set
  [{:keys [read-runtime-state lake-cells]}]
  (lake-cells (read-runtime-state :computer-map)
              (read-runtime-state :lake-max-cells)))

(defn- mark-never-reload!
  [{:keys [update-game-map! sync-transport!]} pos]
  (let [sync-transport! (or sync-transport! noop-sync!)]
    (update-game-map! assoc-in (conj pos :contents :never-reload?) true)
    (sync-transport! pos)))

(defn- land-locked-handler
  [{:keys [current-world read-computer-map set-transport-mission]
    :as deps}
   pos]
  (let [read-map (or read-computer-map current-world)
        unit (get-in (read-map) (conj pos :contents))
        army-count (:army-count unit 0)
        lake-cells-set (lake-cells-set deps)]
    (if (pos? army-count)
      (do
        (set-transport-mission pos :land-locked)
        (process-land-locked-mission deps pos lake-cells-set))
      (park-lake-transport-if-empty deps pos lake-cells-set))))

(defn maybe-handle-lake-transport
  [{:keys [current-world
           read-computer-map
           read-runtime-state
           update-game-map!
           sync-transport!
           set-transport-mission
           lake-cells]
    :as deps}
   pos transport]
  (case (handler-decisions/lake-transport-action
         {:sentry? (= :sentry (:mode transport))
          :lake-locked? (:lake-locked? transport)
          :has-armies? (pos? (:army-count transport 0))})
    :already-handled true
    (:land-locked-unload :park-empty)
    (do
      (mark-never-reload! deps pos)
      (land-locked-handler deps pos)
      true)
    nil))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:48:17.04958-05:00", :module-hash "-491091766", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "1941276428"} {:id "defn-/noop-sync!", :kind "defn-", :line 5, :end-line nil, :hash "-837411842"} {:id "defn-/land-lock-transport!", :kind "defn-", :line 7, :end-line nil, :hash "628567315"} {:id "defn-/retreat-or-land-lock-empty-transport!", :kind "defn-", :line 16, :end-line nil, :hash "1788527091"} {:id "defn/park-lake-transport-if-empty", :kind "defn", :line 26, :end-line nil, :hash "2134859969"} {:id "defn/process-land-locked-mission", :kind "defn", :line 36, :end-line nil, :hash "1059021339"} {:id "defn/fix-idle-mission", :kind "defn", :line 55, :end-line nil, :hash "1763448864"} {:id "defn-/lake-cells-set", :kind "defn-", :line 60, :end-line nil, :hash "-1203434357"} {:id "defn-/mark-never-reload!", :kind "defn-", :line 65, :end-line nil, :hash "-1123583070"} {:id "defn-/land-locked-handler", :kind "defn-", :line 71, :end-line nil, :hash "403442171"} {:id "defn/maybe-handle-lake-transport", :kind "defn", :line 85, :end-line nil, :hash "1564631825"}]}
;; clj-mutate-manifest-end
