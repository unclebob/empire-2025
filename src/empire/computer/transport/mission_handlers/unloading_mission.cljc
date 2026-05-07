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

(defn- prefer-pickup-over-unload?
  [transition-to-loading pos transport read-map]
  (let [transport-id (:transport-id transport)
        current-round (or (sa/read-state :round-number) 0)
        unloaded-recently? (when-let [last-unload (:last-unload-round transport)]
                             (>= last-unload (dec current-round)))
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
             (not unloaded-recently?)
             (boolean load-target-cell)
             (boolean (or (seq load-path) (load-targeting/target-reached? pos load-target-cell)))
             (or (empty? unload-path) (<= (count load-path) (count unload-path)))])))

(declare crawl-step-result)

(defn- retry-unloading-crawl
  [current-pos moves-left moved-any?]
  (sa/update-world! assoc-in (conj current-pos :contents :crawl-history) [])
  (visibility/sync-ai-unit-to-computer-map! current-pos)
  {:pos current-pos
   :moves-left moves-left
   :retried? true
   :moved-any? moved-any?})

(defn- crawl-loop-next-state
  [read-map process-unloading-crawl try-opportunistic-unload
   current-pos moves-left retried? moved-any?]
  (if-let [{:keys [action pos]} (crawl-step-result read-map process-unloading-crawl
                                                    try-opportunistic-unload current-pos)]
    (case action
      :continue {:pos pos :moves-left (dec moves-left) :retried? false :moved-any? true}
      :stop {:done? true :pos pos})
    (if retried?
      {:done? true :pos (when moved-any? current-pos)}
      (retry-unloading-crawl current-pos moves-left moved-any?))))

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
;; {:version 1, :tested-at "2026-03-27T09:48:36.476026-05:00", :module-hash "-155218077", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "719271079"} {:id "defn-/transport-speed", :kind "defn-", :line 10, :end-line 12, :hash "-603549653"} {:id "defn-/prefer-pickup-over-unload?", :kind "defn-", :line 14, :end-line 35, :hash "-2279059"} {:id "defn-/crawl-step-result", :kind "defn-", :line 37, :end-line 46, :hash "-1532108500"} {:id "defn-/unloading-crawl-loop", :kind "defn-", :line 48, :end-line 66, :hash "1156326782"} {:id "defn-/clear-hold-and-crawl", :kind "defn-", :line 68, :end-line 73, :hash "-28062225"} {:id "defn-/process-unloading-with-armies", :kind "defn-", :line 75, :end-line 96, :hash "70882300"} {:id "defn/process-unloading-mission", :kind "defn", :line 98, :end-line 108, :hash "-1854438957"}]}
;; clj-mutate-manifest-end
