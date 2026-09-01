(ns empire.computer.transport.mission-handlers.unloading-mission
  (:require [empire.computer.transport.decisions :as decisions]
            [empire.computer.transport.load-targeting :as load-targeting]
            [empire.computer.transport.reservations :as reservations]
            [empire.computer.transport.sailing-support :as sailing-support]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]))

(defn- transport-speed
  []
  (dispatcher/speed :transport))

(defn- unloaded-recently?
  [transport current-round]
  (when-let [last-unload (:last-unload-round transport)]
    (>= last-unload (dec current-round))))

(defn- load-path-ready?
  [pos load-target-cell load-path]
  (or (seq load-path) (load-targeting/target-reached? pos load-target-cell)))

(defn- pickup-closer-than-unload?
  [load-path unload-path]
  (or (empty? unload-path) (<= (count load-path) (count unload-path))))

(defn- prefer-pickup-over-unload?
  [transition-to-loading pos transport read-map]
  (let [transport-id (:transport-id transport)
        current-round (or (sa/read-state :round-number) 0)
        computer-map (read-map)
        load-target-cell (load-targeting/choose-load-target-cell
                          pos computer-map
                          {:reserved-coastal-cells (reservations/reserved-coastal-cells transport-id)
                           :excluded-country-ids (disj #{(:pickup-country-id transport)} nil)
                           :reserved-army-ids (reservations/reserved-army-ids transport-id)})
        load-path (when load-target-cell
                    (or (load-targeting/path-to-load-target pos computer-map load-target-cell) []))
        unload-path (or (sailing-support/compute-sail-to-unload-path pos) [])]
    (every? true?
            [(boolean transition-to-loading)
             (pos? (:army-count transport 0))
             (< (:army-count transport 0) 6)
             (not (unloaded-recently? transport current-round))
             (boolean load-target-cell)
             (boolean (load-path-ready? pos load-target-cell load-path))
             (pickup-closer-than-unload? load-path unload-path)])))

(declare crawl-step-result)

(defn- retry-unloading-crawl
  [current-pos moves-left moved-any?]
  (sa/update-world! assoc-in (conj current-pos :contents :crawl-history) [])
  (visibility/sync-ai-unit-to-computer-map! current-pos)
  {:pos current-pos
   :moves-left moves-left
   :retried? true
   :moved-any? moved-any?})

(defn- crawl-blocked-next-state
  [current-pos moves-left retried? moved-any?]
  (if retried?
    {:done? true :pos (when moved-any? current-pos)}
    (retry-unloading-crawl current-pos moves-left moved-any?)))

(defn- crawl-loop-next-state
  [read-map process-unloading-crawl try-opportunistic-unload
   current-pos moves-left retried? moved-any?]
  (if-let [{:keys [action pos]} (crawl-step-result read-map process-unloading-crawl
                                                    try-opportunistic-unload current-pos)]
    (case action
      :continue {:pos pos :moves-left (dec moves-left) :retried? false :moved-any? true}
      :stop {:done? true :pos pos})
    (crawl-blocked-next-state current-pos moves-left retried? moved-any?)))

(defn- crawl-step-result
  "Returns :continue, :stop, or nil (blocked) after one crawl step."
  [read-map process-unloading-crawl try-opportunistic-unload current-pos]
  (when-let [next-pos (process-unloading-crawl current-pos)]
    (let [mission (:transport-mission (get-in (read-map) (conj next-pos :contents)))
          still-unloading? (= :unloading mission)
          unloaded? (and still-unloading? (boolean (try-opportunistic-unload next-pos)))]
      (if (and still-unloading? (not unloaded?))
        {:action :continue :pos next-pos}
        {:action :stop :pos next-pos}))))

(defn- unloading-crawl-loop
  [read-map process-unloading-crawl try-opportunistic-unload pos]
  (loop [current-pos pos
         moves-left (transport-speed)
         retried? false
         moved-any? false]
    (if (zero? moves-left)
      (when moved-any? current-pos)
      (let [{:keys [done? pos moves-left retried? moved-any?]}
            (crawl-loop-next-state read-map process-unloading-crawl try-opportunistic-unload
                                   current-pos moves-left retried? moved-any?)]
        (if done?
          pos
          (recur pos moves-left retried? moved-any?))))))

(defn- clear-hold-and-crawl
  [read-map process-unloading-crawl try-opportunistic-unload pos hold-since-round]
  (when hold-since-round
    (sa/update-world! update-in (conj pos :contents) dissoc :unloading-hold-since-round)
    (visibility/sync-ai-unit-to-computer-map! pos))
  (unloading-crawl-loop read-map process-unloading-crawl try-opportunistic-unload pos))

(defn- unload-active-hold!
  [try-opportunistic-unload pos]
  (try-opportunistic-unload pos)
  pos)

(defn- refresh-opportunistic-unload!
  [try-opportunistic-unload pos]
  (when (try-opportunistic-unload pos)
    (visibility/sync-ai-unit-to-computer-map! pos)))

(defn- unloading-with-armies-action
  [transition-to-loading pos transport read-map hold-active?]
  (if hold-active?
    :hold
    (let [transport' (get-in (read-map) (conj pos :contents))]
      (cond
        (not= :unloading (:transport-mission transport')) :stay
        (prefer-pickup-over-unload? transition-to-loading pos transport read-map) :transition-to-loading
        :else :crawl))))

(defn- unloading-hold-active?
  [transport]
  (let [hold-since-round (:unloading-hold-since-round transport)]
    (and hold-since-round
         (< (- (or (sa/read-state :round-number) 0) hold-since-round) 4))))

(defn- refresh-unless-holding!
  [hold-active? try-opportunistic-unload pos]
  (when-not hold-active?
    (refresh-opportunistic-unload! try-opportunistic-unload pos)))

(defn- apply-unloading-with-armies-action
  [{:keys [transition-to-loading process-unloading-crawl try-opportunistic-unload]}
   pos read-map hold-since-round action]
  (case action
    :hold (unload-active-hold! try-opportunistic-unload pos)
    :stay pos
    :transition-to-loading (transition-to-loading pos)
    :crawl (clear-hold-and-crawl read-map process-unloading-crawl
                                 try-opportunistic-unload pos hold-since-round)))

(defn- process-unloading-with-armies
  [{:keys [current-world
           read-computer-map
           transition-to-loading
           try-opportunistic-unload]
    :as deps} pos transport]
  (let [read-map (or read-computer-map current-world)
        hold-since-round (:unloading-hold-since-round transport)
        hold-active? (unloading-hold-active? transport)
        _ (refresh-unless-holding! hold-active? try-opportunistic-unload pos)
        action (unloading-with-armies-action transition-to-loading pos transport read-map hold-active?)]
    (apply-unloading-with-armies-action deps pos read-map hold-since-round action)))

(defn process-unloading-mission
  [{:keys [current-world
           read-computer-map
           transition-to-loading]
    :as deps}
  pos army-count]
  (case (decisions/unloading-mission-action {:army-count army-count})
    :transition-to-loading (transition-to-loading pos)
    (let [read-map (or read-computer-map current-world)
          transport (get-in (read-map) (conj pos :contents))]
      (process-unloading-with-armies deps pos transport))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:43:04.296706-05:00", :module-hash "1191617902", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "719271079"} {:id "defn-/transport-speed", :kind "defn-", :line 10, :end-line nil, :hash "-603549653"} {:id "defn-/unloaded-recently?", :kind "defn-", :line 14, :end-line nil, :hash "-434656680"} {:id "defn-/load-path-ready?", :kind "defn-", :line 19, :end-line nil, :hash "-1180645318"} {:id "defn-/pickup-closer-than-unload?", :kind "defn-", :line 23, :end-line nil, :hash "1935015463"} {:id "defn-/prefer-pickup-over-unload?", :kind "defn-", :line 27, :end-line nil, :hash "1316316669"} {:id "form/6/declare", :kind "declare", :line 49, :end-line nil, :hash "1719495462"} {:id "defn-/retry-unloading-crawl", :kind "defn-", :line 51, :end-line nil, :hash "-488460340"} {:id "defn-/crawl-blocked-next-state", :kind "defn-", :line 60, :end-line nil, :hash "287184687"} {:id "defn-/crawl-loop-next-state", :kind "defn-", :line 66, :end-line nil, :hash "-764506963"} {:id "defn-/crawl-step-result", :kind "defn-", :line 76, :end-line nil, :hash "-1532108500"} {:id "defn-/unloading-crawl-loop", :kind "defn-", :line 87, :end-line nil, :hash "1362755407"} {:id "defn-/clear-hold-and-crawl", :kind "defn-", :line 102, :end-line nil, :hash "-28062225"} {:id "defn-/unload-active-hold!", :kind "defn-", :line 109, :end-line nil, :hash "-117127951"} {:id "defn-/refresh-opportunistic-unload!", :kind "defn-", :line 114, :end-line nil, :hash "-2047716663"} {:id "defn-/unloading-with-armies-action", :kind "defn-", :line 119, :end-line nil, :hash "-982517707"} {:id "defn-/unloading-hold-active?", :kind "defn-", :line 129, :end-line nil, :hash "-492755004"} {:id "defn-/refresh-unless-holding!", :kind "defn-", :line 135, :end-line nil, :hash "-1564378213"} {:id "defn-/apply-unloading-with-armies-action", :kind "defn-", :line 140, :end-line nil, :hash "723323891"} {:id "defn-/process-unloading-with-armies", :kind "defn-", :line 150, :end-line nil, :hash "-750434967"} {:id "defn/process-unloading-mission", :kind "defn", :line 163, :end-line nil, :hash "-1854438957"}]}
;; clj-mutate-manifest-end
