(ns empire.movement.context
  (:require [empire.application.ports.world-store :as world-ports]))

(defonce ^:private state-ctx* (atom nil))

(defn set-state-ctx!
  [ctx]
  (reset! state-ctx* ctx))

(defn state-ctx
  []
  (or @state-ctx*
      (throw (ex-info "Movement context not initialized"
                      {:hint "Initialize app bootstrap before calling movement services."}))))

(defn current-world
  []
  ((:load-world (state-ctx))))

(defn update-world!
  [f & args]
  (let [ctx (state-ctx)
        world ((:load-world ctx))
        next-world (apply f world args)]
    ((:save-world! ctx) next-world)))

(defn read-runtime-state
  [k]
  ((:read-runtime-state (state-ctx)) k))

(defn write-runtime-state!
  [k v]
  ((:write-runtime-state! (state-ctx)) k v))

(defn merge-continents!
  [stamp-id existing-cid]
  ((:merge-continents! (state-ctx)) stamp-id existing-cid))

(defn world-store
  []
  (:world-store (state-ctx)))

(defn world-atom
  []
  (world-ports/world-atom (world-store)))
