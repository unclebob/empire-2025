;; mutation-tested: 2026-02-28
(ns empire.debug
  "Debug compatibility facade.
   Implementation is split across logging and dump namespaces."
  (:require [empire.debug.dump :as debug-dump]))

(defmulti log-player-movement! (fn [& _] :default))
(defmulti log-computer-event! (fn [& _] :default))
(defmulti log-action! (fn [& _] :default))

(defmulti dump-region (fn [& _] :default))
(defmulti format-cell (fn [& _] :default))
(defmulti format-dump (fn [& _] :default))
(defmulti generate-dump-filename (fn [& _] :default))
(defmulti write-dump! (fn [& _] :default))
(defmulti screen-coords-to-cell-range (fn [& _] :default))

;; Preserve private var access used by debug_spec.
(defn- format-movement-entry
  [entry]
  (debug-dump/format-movement-entry entry))
