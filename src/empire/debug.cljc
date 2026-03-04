;; mutation-tested: 2026-02-28
(ns empire.debug
  "Debug compatibility facade.
   Implementation is split across logging and dump namespaces."
  (:require [empire.debug.dump :as debug-dump]))

(defonce ^:private methods-loaded?
  (delay
    (try
      (require 'empire.debug.impl.facade-methods)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(defn- ensure-methods-loaded!
  []
  @methods-loaded?
  nil)

(defmulti log-player-movement! (fn [& _] (ensure-methods-loaded!) :default))
(defmulti log-computer-event! (fn [& _] (ensure-methods-loaded!) :default))
(defmulti log-action! (fn [& _] (ensure-methods-loaded!) :default))

(defmulti dump-region (fn [& _] (ensure-methods-loaded!) :default))
(defmulti format-cell (fn [& _] (ensure-methods-loaded!) :default))
(defmulti format-dump (fn [& _] (ensure-methods-loaded!) :default))
(defmulti generate-dump-filename (fn [& _] (ensure-methods-loaded!) :default))
(defmulti write-dump! (fn [& _] (ensure-methods-loaded!) :default))
(defmulti screen-coords-to-cell-range (fn [& _] (ensure-methods-loaded!) :default))

;; Preserve private var access used by debug_spec.
(defn- format-movement-entry
  [entry]
  (debug-dump/format-movement-entry entry))
