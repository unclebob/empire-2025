(ns empire.game-mechanics.debug.logging
  "Debug log appenders with bounded history."
  (:require [empire.state.api :as sa]))

(def ^:private max-action-log-size 100)
(def ^:private max-movement-log-size 500)
(def ^:private max-computer-event-log-size 2000)
(def ^:dynamic *computer-unit-id* nil)

(defn- append-computer-log-entry!
  [entry]
  (when-let [log-file (sa/read-state :computer-unit-log-file)]
    (spit log-file
          (str (pr-str entry) "\n")
          :append true)))

(defn computer-unit-logging-enabled?
  []
  (boolean (sa/read-state :computer-unit-log-file)))

(defn begin-computer-unit-log-round!
  "Clears per-unit discovery totals for the current computer turn."
  []
  (when (computer-unit-logging-enabled?)
    (sa/write-state! :computer-unit-round-discoveries {})
    (sa/write-state! :computer-unit-round-conquests {})))

(defn record-computer-unit-discovery!
  "Adds newly discovered cell count for the given computer unit id."
  [unit-id discovered-cells]
  (when (and (computer-unit-logging-enabled?) unit-id (pos? discovered-cells))
    (sa/update-state! :computer-unit-round-discoveries
                      #(update (or % {}) unit-id (fnil + 0) discovered-cells))))

(defn record-active-computer-unit-discovery!
  "Adds newly discovered cells for the computer unit currently being processed."
  [discovered-cells]
  (record-computer-unit-discovery! *computer-unit-id* discovered-cells))

(defn record-computer-unit-conquest!
  "Adds conquered city count for the given computer unit id."
  [unit-id conquered-cities]
  (when (and (computer-unit-logging-enabled?) unit-id (pos? conquered-cities))
    (sa/update-state! :computer-unit-round-conquests
                      #(update (or % {}) unit-id (fnil + 0) conquered-cities))))

(defn record-active-computer-unit-conquest!
  "Adds conquered cities for the computer unit currently being processed."
  [conquered-cities]
  (record-computer-unit-conquest! *computer-unit-id* conquered-cities))

(defn with-computer-unit-context
  "Runs f while attributing discovery logging to the given computer unit id."
  [unit-id f]
  (if (computer-unit-logging-enabled?)
    (binding [*computer-unit-id* unit-id]
      (f))
    (f)))

(defn computer-unit-snapshots
  "Build per-round computer unit snapshots with per-unit discovery totals."
  [world round-number discovery-counts conquest-counts]
  (vec
   (for [row (range (count world))
         col (range (count (first world)))
         :let [unit (get-in world [row col :contents])]
         :when (and unit (= :computer (:owner unit)))]
     {:round round-number
      :pos [row col]
      :unit unit
      :discovered-cells (get discovery-counts (:computer-unit-id unit) 0)
      :conquered-cities (get conquest-counts (:computer-unit-id unit) 0)})))

(defn log-computer-units!
  "Append current computer unit snapshots, including this round's discovered-cell counts."
  []
  (when-let [log-file (sa/read-state :computer-unit-log-file)]
    (let [entries (computer-unit-snapshots (sa/current-world)
                                           (sa/read-state :round-number)
                                           (or (sa/read-state :computer-unit-round-discoveries) {})
                                           (or (sa/read-state :computer-unit-round-conquests) {}))]
      (when (seq entries)
        (spit log-file
              (apply str (map #(str (pr-str %) "\n") entries))
              :append true)))))

(defn log-game-map!
  "Append a full authoritative game-map snapshot to the unit log when enabled."
  [round-number]
  (append-computer-log-entry!
   {:round round-number
    :event :game-map-snapshot
    :game-map (sa/current-world)}))

(defn log-computer-unit-crash!
  "Append an explicit computer fighter crash entry to the unit log when enabled."
  [pos unit reason details]
  (append-computer-log-entry!
   (cond-> {:round (sa/read-state :round-number)
            :event :fighter-crash
            :pos pos
            :unit unit
            :reason reason}
     details (merge details))))

(defn log-player-movement!
  "Log a player unit movement for debugging.
   event is :move, :wake, or :blocked.
   reason is the wake/block reason (e.g., :steps-exhausted, :blocked) or nil for normal moves."
  [unit-type from-pos to-pos mode event reason]
  (let [entry {:round (sa/read-state :round-number)
               :unit-type unit-type
               :from from-pos
               :to to-pos
               :mode mode
               :event event
               :reason reason}]
    (sa/update-state! :player-movement-log
                           (fn [log]
                             (let [new-log (conj (or log []) entry)]
                               (if (> (count new-log) max-movement-log-size)
                                 (vec (drop (- (count new-log) max-movement-log-size) new-log))
                                 new-log))))))

(defn log-computer-event!
  "Log a computer unit event. event is a keyword like :army-move, :army-die, etc.
   pos is the unit's position. details is an optional map of extra info."
  [event pos details]
  (let [entry (cond-> {:round (sa/read-state :round-number) :event event :pos pos}
                details (merge details))]
    (append-computer-log-entry! entry)
    (sa/update-state! :computer-event-log
                           (fn [log]
                             (let [new-log (conj (or log []) entry)]
                               (if (> (count new-log) max-computer-event-log-size)
                                 (vec (drop (- (count new-log) max-computer-event-log-size) new-log))
                                 new-log))))))

(defn log-action!
  "Append action to circular buffer with timestamp. Cap at 100 entries.
   Takes an action vector (e.g., [:move :army [4,6] [4,7]]).
   Adds {:timestamp <ms> :action action} to the action-log atom.
   If log exceeds 100 entries, drops oldest."
  [action]
  (let [entry {:timestamp (System/currentTimeMillis)
               :action action}]
    (sa/update-state! :action-log
                           (fn [log]
                             (let [new-log (conj (or log []) entry)]
                               (if (> (count new-log) max-action-log-size)
                                 (vec (drop (- (count new-log) max-action-log-size) new-log))
                                 new-log))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:00:49.459279-05:00", :module-hash "-211312106", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "777327598"} {:id "def/max-action-log-size", :kind "def", :line 5, :end-line 5, :hash "670369281"} {:id "def/max-movement-log-size", :kind "def", :line 6, :end-line 6, :hash "215068873"} {:id "def/max-computer-event-log-size", :kind "def", :line 7, :end-line 7, :hash "-1473022156"} {:id "defn/log-player-movement!", :kind "defn", :line 9, :end-line 26, :hash "-282432460"} {:id "defn/log-computer-event!", :kind "defn", :line 28, :end-line 39, :hash "-782503197"} {:id "defn/log-action!", :kind "defn", :line 41, :end-line 54, :hash "1265608058"}]}
;; clj-mutate-manifest-end
