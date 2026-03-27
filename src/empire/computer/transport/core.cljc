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

;; These guarded write helpers are intentional instrumentation for a sticky
;; integrity bug where stale transport writes can fabricate metadata-only
;; :contents maps on sea cells. When a suspected write site fires after the
;; transport has moved or disappeared, we want a precise event telling us
;; which write was attempted and what was actually at that position.
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
                                  :armies (:army-count transport 0)
                                  :major-invasion (:major-invasion transport)
                                  :load-target-cell (:load-target-cell transport)
                                  :invasion-target (:invasion-target transport)
                                  :major-invasion-target (:major-invasion-target transport)}))))

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
;; {:version 1, :tested-at "2026-03-27T00:01:59.014983-05:00", :module-hash "1688052436", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "-572253242"} {:id "defn/computer-transport?", :kind "defn", :line 8, :end-line 12, :hash "817746107"} {:id "defn/log-transport-write-miss!", :kind "defn", :line 19, :end-line 28, :hash "1522214814"} {:id "defn/assoc-transport-field!", :kind "defn", :line 30, :end-line 38, :hash "-1946238572"} {:id "defn/update-transport-contents!", :kind "defn", :line 40, :end-line 48, :hash "-1406208418"} {:id "defn/get-passable-sea-neighbors", :kind "defn", :line 50, :end-line 60, :hash "290979142"} {:id "defn/adjacent-to-land?", :kind "defn", :line 63, :end-line 68, :hash "870012466"} {:id "defn/find-adjacent-land-pos", :kind "defn", :line 70, :end-line 76, :hash "-2064088485"} {:id "defn/log-transport-mission-transition!", :kind "defn", :line 78, :end-line 91, :hash "2076077910"} {:id "defn/set-transport-mission", :kind "defn", :line 93, :end-line 99, :hash "685119280"} {:id "def/hold-sail-to-load-rounds", :kind "def", :line 101, :end-line 101, :hash "-1479745939"} {:id "defn/load-plan-failure", :kind "defn", :line 103, :end-line 116, :hash "-1027820484"} {:id "defn/enter-hold-sail-to-load!", :kind "defn", :line 118, :end-line 135, :hash "595926306"} {:id "defn/hold-sail-to-load-elapsed?", :kind "defn", :line 137, :end-line 141, :hash "-205920922"} {:id "defn/mint-unload-event-id", :kind "defn", :line 143, :end-line 148, :hash "1536039978"} {:id "defn/mint-unload-country-id", :kind "defn", :line 150, :end-line 155, :hash "967535281"}]}
;; clj-mutate-manifest-end
