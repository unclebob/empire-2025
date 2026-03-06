;; mutation-tested: no
(ns empire.adapters.state.atoms
  "Atom-backed store adapter for application state boundary."
  (:require [empire.application.ports.world-store :as ports]
            [empire.application.state.atoms :as atoms]))

(defrecord AtomWorldStore []
  ports/WorldStorePort
  (load-world [_]
    @atoms/game-map)
  (save-world! [_ world]
    (reset! atoms/game-map world))
  (world-atom [_]
    atoms/game-map))

(defn world-store
  "Returns a WorldStorePort backed by empire atoms."
  []
  (->AtomWorldStore))
