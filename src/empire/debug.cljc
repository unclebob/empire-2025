(ns empire.debug
  "Debug compatibility facade.
   Implementation is split across logging and dump namespaces."
  (:require [empire.debug.dump :as dump]
            [empire.debug.dump.output :as output]
            [empire.debug.logging :as logging]))

(defn log-player-movement!
  [unit-type from-pos to-pos mode event reason]
  (logging/log-player-movement! unit-type from-pos to-pos mode event reason))

(defn log-computer-event!
  [event pos details]
  (logging/log-computer-event! event pos details))

(defn log-action!
  [action]
  (logging/log-action! action))

(defn dump-region
  [start-coords end-coords]
  (dump/dump-region start-coords end-coords))

(defn format-cell
  [coords cell]
  (dump/format-cell coords cell))

(defn format-dump
  [start-coords end-coords]
  (dump/format-dump start-coords end-coords))

(defn generate-dump-filename
  []
  (output/generate-dump-filename))

(defn write-dump!
  [start-coords end-coords]
  (output/write-dump! start-coords end-coords))

(defn screen-coords-to-cell-range
  [start-screen end-screen]
  (output/screen-coords-to-cell-range start-screen end-screen))

;; Preserve private var access used by debug_spec.
(defn- format-movement-entry
  [entry]
  (dump/format-movement-entry entry))
