;; mutation-tested: no
(ns empire.adapters.state.atoms
  "Atom-backed store adapter for application state boundary."
  (:require [empire.atoms :as atoms]
            [empire.application.ports :as ports]))

(defrecord AtomWorldStore []
  ports/WorldStorePort
  (load-world [_]
    @atoms/game-map)
  (save-world! [_ world]
    (reset! atoms/game-map world)))

(defn world-store
  "Returns a WorldStorePort backed by empire atoms."
  []
  (->AtomWorldStore))
