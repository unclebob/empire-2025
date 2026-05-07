(ns empire.config.units.transport
  (:require [empire.config.units.config :as units-config]
            [empire.config.units.container :as container]))

(defn initial-state
  []
  {:army-count 0
   :awake-armies 0
   :been-to-sea true})

(defn can-move-to?
  [cell]
  (container/sea-can-move-to? cell))

(defn needs-attention?
  [unit]
  (container/needs-attention? unit :awake-armies))

(defn full?
  [unit]
  (container/full? unit :army-count units-config/transport-capacity))

(defn has-armies?
  [unit]
  (container/has-contained? unit :army-count))

(defn has-awake-armies?
  [unit]
  (container/has-contained? unit :awake-armies))

(defn add-army
  [unit]
  (container/add-contained unit :army-count))

(defn remove-army
  [unit]
  (container/remove-contained unit :army-count))

(defn wake-armies
  [unit]
  (container/wake-contained unit :army-count :awake-armies))

(defn sleep-armies
  [unit]
  (container/sleep-contained unit :awake-armies))

(defn remove-awake-army
  [unit]
  (container/remove-awake-contained unit :army-count :awake-armies))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:49:08.137489-05:00", :module-hash "-533211529", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "655470826"} {:id "defn/initial-state", :kind "defn", :line 4, :end-line 8, :hash "-1156612753"} {:id "defn/can-move-to?", :kind "defn", :line 10, :end-line 13, :hash "-927721079"} {:id "defn/needs-attention?", :kind "defn", :line 15, :end-line 18, :hash "905010811"} {:id "defn/full?", :kind "defn", :line 20, :end-line 22, :hash "-253185517"} {:id "defn/has-armies?", :kind "defn", :line 24, :end-line 26, :hash "1179663576"} {:id "defn/has-awake-armies?", :kind "defn", :line 28, :end-line 30, :hash "2095470242"} {:id "defn/add-army", :kind "defn", :line 32, :end-line 34, :hash "1097494887"} {:id "defn/remove-army", :kind "defn", :line 36, :end-line 38, :hash "-1118940910"} {:id "defn/wake-armies", :kind "defn", :line 40, :end-line 42, :hash "-54629489"} {:id "defn/sleep-armies", :kind "defn", :line 44, :end-line 46, :hash "-312012962"} {:id "defn/remove-awake-army", :kind "defn", :line 48, :end-line 52, :hash "643700559"}]}
;; clj-mutate-manifest-end
