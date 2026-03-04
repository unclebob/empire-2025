;; mutation-tested: 2026-02-25
(ns empire.units.impl.ships
  "Concrete default implementations for `empire.units.ships`."
  (:require [empire.units.ships :as ships]))

(def ^:private configs
  {:patrol-boat {:speed 4 :cost 15 :hits 1 :strength 1 :display-char "P" :visibility-radius 1}
   :destroyer   {:speed 2 :cost 20 :hits 3 :strength 1 :display-char "D" :visibility-radius 1}
   :submarine   {:speed 2 :cost 20 :hits 2 :strength 3 :display-char "S" :visibility-radius 1}
   :battleship  {:speed 2 :cost 40 :hits 10 :strength 2 :display-char "B" :visibility-radius 1}})

(defmethod ships/config :default [ship-type key]
  (get-in configs [ship-type key]))

(defmethod ships/initial-state :default []
  {})

(defmethod ships/can-move-to? :default [cell]
  (and cell (= (:type cell) :sea)))

(defmethod ships/needs-attention? :default [unit]
  (= (:mode unit) :awake))

(defn load-methods!
  []
  true)
