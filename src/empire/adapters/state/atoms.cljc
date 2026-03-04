;; mutation-tested: no
(ns empire.adapters.state.atoms
  "Atom-backed store adapter for application state boundary."
  (:require [empire.application.ports.world-store :as ports]))

(defn- game-map-atom
  []
  (or (some-> 'empire.atoms/game-map requiring-resolve var-get)
      (throw (ex-info "Unable to resolve legacy atom var: empire.atoms/game-map" {}))))

(defrecord AtomWorldStore []
  ports/WorldStorePort
  (load-world [_]
    @(game-map-atom))
  (save-world! [_ world]
    (reset! (game-map-atom) world))
  (world-atom [_]
    (game-map-atom)))

(defn world-store
  "Returns a WorldStorePort backed by empire atoms."
  []
  (->AtomWorldStore))
