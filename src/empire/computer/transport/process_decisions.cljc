(ns empire.computer.transport.process-decisions)

(def mission-order
  [:invading
   :find-armies-for-invasion
   :load-for-invasion
   :land-locked
   :unloading
   :sailing
   :sail-to-unload
   :leave-city
   :sail-to-load
   :hold-sail-to-load
   :loading])

(defn- normalize-normal-mission
  [mission _army-count]
  (case mission
    nil :sail-to-load
    :idle :sail-to-load
    mission))

(defn transport-mission-action
  [{:keys [mission never-reload? army-count]}]
  (let [normalized (normalize-normal-mission mission army-count)]
    {:fix-idle? true
     :force-sailing? (and (= :loading normalized) never-reload?)
     :mission normalized}))

(defn active-transport-action
  [{:keys [sentry? lake-handled?]}]
  (cond
    sentry? :skip
    lake-handled? :skip
    :else :dispatch))

(defn transport-process-action
  [{:keys [transport? computer-owned? random-walk?]}]
  (cond
    (not (and transport? computer-owned?)) nil
    random-walk? :random-walk
    :else :active))

(defn transport-mission-handler
  [mission]
  (get {:invading :invading
        :find-armies-for-invasion :find-armies-for-invasion
        :load-for-invasion :load-for-invasion
        :land-locked :land-locked
        :unloading :unloading
        :sailing :sailing
        :sail-to-unload :sail-to-unload
        :leave-city :leave-city
        :sail-to-load :sail-to-load
        :hold-sail-to-load :hold-sail-to-load
        :loading :loading}
       mission))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:10:00.787976-05:00", :module-hash "1865908534", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-1421218633"} {:id "def/mission-order", :kind "def", :line 3, :end-line 14, :hash "-328328691"} {:id "defn-/normalize-normal-mission", :kind "defn-", :line 16, :end-line 21, :hash "-1444633252"} {:id "defn/transport-mission-action", :kind "defn", :line 23, :end-line 28, :hash "1408473260"} {:id "defn/active-transport-action", :kind "defn", :line 30, :end-line 35, :hash "1094472312"} {:id "defn/transport-process-action", :kind "defn", :line 37, :end-line 42, :hash "819243811"} {:id "defn/transport-mission-handler", :kind "defn", :line 44, :end-line 57, :hash "1643999726"}]}
;; clj-mutate-manifest-end
