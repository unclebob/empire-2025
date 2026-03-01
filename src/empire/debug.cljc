;; mutation-tested: 2026-02-28
(ns empire.debug
  "Debug compatibility facade.
   Implementation is split across logging and dump namespaces."
  (:require [empire.debug.dump :as dump]
            [empire.debug.dump.output :as output]
            [empire.debug.logging :as logging]))

(def log-player-movement! logging/log-player-movement!)
(def log-computer-event! logging/log-computer-event!)
(def log-action! logging/log-action!)

(def dump-region dump/dump-region)
(def format-cell dump/format-cell)
(def format-dump dump/format-dump)
(def generate-dump-filename output/generate-dump-filename)
(def write-dump! output/write-dump!)
(def screen-coords-to-cell-range output/screen-coords-to-cell-range)

;; Preserve private var access used by debug_spec.
(def ^:private format-movement-entry dump/format-movement-entry)
