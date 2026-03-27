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
;; {:version 1, :tested-at "2026-03-27T01:00:40.90626-05:00", :module-hash "393607532", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "777327598"} {:id "def/max-action-log-size", :kind "def", :line 5, :end-line 5, :hash "670369281"} {:id "def/max-movement-log-size", :kind "def", :line 6, :end-line 6, :hash "215068873"} {:id "def/max-computer-event-log-size", :kind "def", :line 7, :end-line 7, :hash "-1473022156"} {:id "def/*computer-unit-id*", :kind "def", :line 8, :end-line 8, :hash "-1853584786"} {:id "defn-/append-computer-log-entry!", :kind "defn-", :line 10, :end-line 15, :hash "1944490239"} {:id "defn/computer-unit-logging-enabled?", :kind "defn", :line 17, :end-line 19, :hash "-1762734356"} {:id "defn/begin-computer-unit-log-round!", :kind "defn", :line 21, :end-line 26, :hash "1612047240"} {:id "defn/record-computer-unit-discovery!", :kind "defn", :line 28, :end-line 33, :hash "1012741257"} {:id "defn/record-active-computer-unit-discovery!", :kind "defn", :line 35, :end-line 38, :hash "-1243974137"} {:id "defn/record-computer-unit-conquest!", :kind "defn", :line 40, :end-line 45, :hash "1581009221"} {:id "defn/record-active-computer-unit-conquest!", :kind "defn", :line 47, :end-line 50, :hash "1715681881"} {:id "defn/with-computer-unit-context", :kind "defn", :line 52, :end-line 58, :hash "-559770717"} {:id "defn/computer-unit-snapshots", :kind "defn", :line 60, :end-line 72, :hash "-207215679"} {:id "defn/log-computer-units!", :kind "defn", :line 74, :end-line 85, :hash "1342343979"} {:id "defn/log-game-map!", :kind "defn", :line 87, :end-line 93, :hash "670762323"} {:id "defn/log-computer-unit-crash!", :kind "defn", :line 95, :end-line 104, :hash "-1342781865"} {:id "defn/log-player-movement!", :kind "defn", :line 106, :end-line 123, :hash "-282432460"} {:id "defn/log-computer-event!", :kind "defn", :line 125, :end-line 137, :hash "-1215984372"} {:id "defn/log-action!", :kind "defn", :line 139, :end-line 152, :hash "1265608058"}]}
;; clj-mutate-manifest-end
