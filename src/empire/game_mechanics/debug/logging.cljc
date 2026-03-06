;; mutation-tested: 2026-02-28
(ns empire.game-mechanics.debug.logging
  "Debug log appenders with bounded history."
  (:require [empire.state.api :as sa]))

(def ^:private max-action-log-size 100)
(def ^:private max-movement-log-size 500)
(def ^:private max-computer-event-log-size 2000)

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
