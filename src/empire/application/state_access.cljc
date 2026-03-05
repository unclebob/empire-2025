(ns empire.application.state-access
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]))

(def ^:private ctx (delay (app-runtime/default-state-ctx)))

(defn state-ctx [] @ctx)

(defn current-world [] ((:load-world @ctx)))

(defn update-world! [f & args]
  (apply app-state/update-world! @ctx f args))

(defn read-state [k] ((:read-runtime-state @ctx) k))

(defn write-state! [k v] ((:write-runtime-state! @ctx) k v))

(defn update-state! [k f & args]
  (write-state! k (apply f (read-state k) args)))
