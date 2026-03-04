;; mutation-tested: no
(ns empire.debug.impl.facade-methods
  (:require [empire.debug :as debug]
            [empire.debug.dump :as dump]
            [empire.debug.dump.output :as output]
            [empire.debug.logging :as logging]))

(defmethod debug/log-player-movement! :default
  [unit-type from-pos to-pos mode event reason]
  (logging/log-player-movement! unit-type from-pos to-pos mode event reason))

(defmethod debug/log-computer-event! :default
  [event pos details]
  (logging/log-computer-event! event pos details))

(defmethod debug/log-action! :default
  [action]
  (logging/log-action! action))

(defmethod debug/dump-region :default
  [start-coords end-coords]
  (dump/dump-region start-coords end-coords))

(defmethod debug/format-cell :default
  [coords cell]
  (dump/format-cell coords cell))

(defmethod debug/format-dump :default
  [start-coords end-coords]
  (dump/format-dump start-coords end-coords))

(defmethod debug/generate-dump-filename :default
  []
  (output/generate-dump-filename))

(defmethod debug/write-dump! :default
  [start-coords end-coords]
  (output/write-dump! start-coords end-coords))

(defmethod debug/screen-coords-to-cell-range :default
  [start-screen end-screen]
  (output/screen-coords-to-cell-range start-screen end-screen))
