;; mutation-tested: no
(ns empire.adapters.state.major-invasion
  "Atom-backed major-invasion state adapter."
  (:require [empire.application.ports :as ports]
            [empire.atoms :as atoms]))

(defrecord AtomMajorInvasionStore []
  ports/MajorInvasionStorePort
  (load-major-invasion-state [_]
    @atoms/major-invasion-state)
  (save-major-invasion-state! [_ state]
    (reset! atoms/major-invasion-state state)))

(defn major-invasion-store
  []
  (->AtomMajorInvasionStore))
