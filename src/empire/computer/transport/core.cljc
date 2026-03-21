(ns empire.computer.transport.core
  "Shared transport helpers — no dependencies on other transport sub-modules."
  (:require [empire.state.api :as sa]
            [empire.computer.shared.world-query :as world-query]
            [empire.game-mechanics.debug.logging :as debug]
            [empire.game-mechanics.visibility :as visibility]))

(defn computer-transport?
  [unit]
  (and (map? unit)
       (= :transport (:type unit))
       (= :computer (:owner unit))))

(defn log-transport-write-miss!
  [pos op details]
  (let [cell (get-in (sa/read-state :computer-map) pos)]
    (debug/log-computer-event!
     :transport-write-miss
     pos
     {:op op
      :details details
      :cell-type (:type cell)
      :cell-contents (:contents cell)})))

(defn assoc-transport-field!
  [pos k v]
  (if (computer-transport? (get-in (sa/read-state :computer-map) (conj pos :contents)))
    (do
      (sa/update-world! assoc-in (conj pos :contents k) v)
      true)
    (do
      (log-transport-write-miss! pos :assoc-transport-field {:field k :value v})
      false)))

(defn update-transport-contents!
  [pos f]
  (if (computer-transport? (get-in (sa/read-state :computer-map) (conj pos :contents)))
    (do
      (sa/update-world! update-in (conj pos :contents) f)
      true)
    (do
      (log-transport-write-miss! pos :update-transport-contents {})
      false)))

(defn get-passable-sea-neighbors
  [pos]
  (let [game-map (sa/read-state :computer-map)]
            (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and (or (nil? cell)
                         (= :sea (:type cell))
                         (= :unexplored (:type cell)))
                     (or (nil? (:contents cell))
                         (= :computer (:owner (:contents cell)))))))
            (world-query/get-neighbors pos))))

(defn recently-unloaded-country?
  [unloaded-countries country-id]
  (when-let [unload-round (get unloaded-countries country-id)]
    (< (- (or (sa/read-state :round-number) 0) unload-round) 10)))

(defn adjacent-to-land?
  [pos]
  (let [game-map (sa/read-state :computer-map)]
    (some (fn [n]
            (#{:land :city} (:type (get-in game-map n))))
          (world-query/get-neighbors pos))))

(defn find-adjacent-land-pos
  [pos]
  (let [game-map (sa/read-state :computer-map)]
    (first (filter (fn [n]
                     (let [cell (get-in game-map n)]
                       (and cell (#{:land :city} (:type cell)))))
                   (world-query/get-neighbors pos)))))

(defn log-transport-mission-transition!
  [pos from-mission to-mission]
  (when (not= from-mission to-mission)
    (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))]
      (debug/log-computer-event! :transport-mission-transition
                                 pos
                                 {:from from-mission
                                  :to to-mission
                                  :transport-id (:transport-id transport)
                                  :armies (:army-count transport 0)}))))

(defn set-transport-mission
  [pos mission]
  (let [from-mission (get-in (sa/read-state :computer-map) (conj pos :contents :transport-mission))]
    (when (assoc-transport-field! pos :transport-mission mission)
      (visibility/sync-ai-unit-to-computer-map! pos)
      (log-transport-mission-transition! pos from-mission mission)))
  nil)

(def hold-sail-to-load-rounds 5)

(defn load-plan-failure
  ([pos load-target-cell sail-path manifest]
   (load-plan-failure pos load-target-cell sail-path manifest (seq sail-path)))
  ([pos load-target-cell sail-path manifest path-ready?]
  (let [reasons (cond-> []
                  (nil? load-target-cell) (conj :no-load-target)
                  (empty? manifest) (conj :no-manifest)
                  (not path-ready?) (conj :no-sail-path))]
    {:round (or (sa/read-state :round-number) 0)
     :at pos
     :reasons reasons
     :load-target-cell load-target-cell
     :manifest-count (count manifest)
     :sail-path-length (count sail-path)})))

(defn enter-hold-sail-to-load!
  ([pos]
   (enter-hold-sail-to-load! pos nil))
  ([pos failure]
   (let [from-mission (get-in (sa/read-state :computer-map) (conj pos :contents :transport-mission))]
     (when (update-transport-contents!
            pos
            #(-> %
                 (assoc :transport-mission :hold-sail-to-load
                        :hold-sail-to-load-since-round (or (sa/read-state :round-number) 0)
                        :load-target-cell nil
                        :load-manifest nil
                        :loading-since-round nil
                        :sail-path []
                        :load-plan-failure failure)
                 (dissoc :unload-target-city)))
       (visibility/sync-ai-unit-to-computer-map! pos)
       (log-transport-mission-transition! pos from-mission :hold-sail-to-load)))))

(defn release-hold-sail-to-load!
  [pos]
  (let [from-mission (get-in (sa/read-state :computer-map) (conj pos :contents :transport-mission))]
    (when (update-transport-contents!
           pos
           #(-> %
                (assoc :transport-mission :sail-to-load
                       :hold-sail-to-load-since-round nil
                       :load-target-cell nil
                       :load-manifest nil
                       :loading-since-round nil
                       :sail-path []
                       :load-plan-failure nil)
                (dissoc :unload-target-city)))
      (visibility/sync-ai-unit-to-computer-map! pos)
      (log-transport-mission-transition! pos from-mission :sail-to-load))))

(defn hold-sail-to-load-elapsed?
  [transport]
  (when-let [started (:hold-sail-to-load-since-round transport)]
    (>= (- (or (sa/read-state :round-number) 0) started)
        hold-sail-to-load-rounds)))

(defn mint-unload-event-id
  [pos _transport]
  (let [id (or (sa/read-state :next-unload-event-id) 0)]
    (sa/write-state! :next-unload-event-id (inc id))
    (when (assoc-transport-field! pos :unload-event-id id)
      (visibility/sync-ai-unit-to-computer-map! pos))))

(defn mint-unload-country-id
  [pos]
  (let [cid (or (sa/read-state :next-country-id) 0)]
    (sa/write-state! :next-country-id (inc cid))
    (when (assoc-transport-field! pos :unload-country-id cid)
      (visibility/sync-ai-unit-to-computer-map! pos))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:58:55.047797-05:00", :module-hash "-1339241194", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "432609882"} {:id "defn/get-passable-sea-neighbors", :kind "defn", :line 7, :end-line 16, :hash "-230437541"} {:id "defn/recently-unloaded-country?", :kind "defn", :line 18, :end-line 21, :hash "-493759778"} {:id "defn/adjacent-to-land?", :kind "defn", :line 23, :end-line 28, :hash "2037017228"} {:id "defn/find-adjacent-land-pos", :kind "defn", :line 30, :end-line 36, :hash "-1582415204"} {:id "defn/set-transport-mission", :kind "defn", :line 38, :end-line 43, :hash "-164182747"} {:id "defn/mint-unload-event-id", :kind "defn", :line 45, :end-line 50, :hash "953808771"} {:id "defn/mint-unload-country-id", :kind "defn", :line 52, :end-line 57, :hash "-845291531"} {:id "defn/record-pickup-continent-pos", :kind "defn", :line 59, :end-line 67, :hash "26774624"}]}
;; clj-mutate-manifest-end
