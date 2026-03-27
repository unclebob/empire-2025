(ns empire.config.units.transport
  (:require [empire.config.units.config :as units-config]))

(defn initial-state
  []
  {:army-count 0
   :awake-armies 0
   :been-to-sea true})

(defn can-move-to?
  [cell]
  (and cell
       (= (:type cell) :sea)))

(defn needs-attention?
  [unit]
  (or (= (:mode unit) :awake)
      (pos? (:awake-armies unit 0))))

(defn full?
  [unit]
  (>= (:army-count unit 0) units-config/transport-capacity))

(defn has-armies?
  [unit]
  (pos? (:army-count unit 0)))

(defn has-awake-armies?
  [unit]
  (pos? (:awake-armies unit 0)))

(defn add-army
  [unit]
  (update unit :army-count (fnil inc 0)))

(defn remove-army
  [unit]
  (update unit :army-count (fnil dec 0)))

(defn wake-armies
  [unit]
  (assoc unit :awake-armies (:army-count unit 0)))

(defn sleep-armies
  [unit]
  (assoc unit :awake-armies 0))

(defn remove-awake-army
  [unit]
  (-> unit
      (update :army-count (fnil dec 0))
      (update :awake-armies (fnil dec 0))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:49:08.137489-05:00", :module-hash "-533211529", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "655470826"} {:id "defn/initial-state", :kind "defn", :line 4, :end-line 8, :hash "-1156612753"} {:id "defn/can-move-to?", :kind "defn", :line 10, :end-line 13, :hash "-927721079"} {:id "defn/needs-attention?", :kind "defn", :line 15, :end-line 18, :hash "905010811"} {:id "defn/full?", :kind "defn", :line 20, :end-line 22, :hash "-253185517"} {:id "defn/has-armies?", :kind "defn", :line 24, :end-line 26, :hash "1179663576"} {:id "defn/has-awake-armies?", :kind "defn", :line 28, :end-line 30, :hash "2095470242"} {:id "defn/add-army", :kind "defn", :line 32, :end-line 34, :hash "1097494887"} {:id "defn/remove-army", :kind "defn", :line 36, :end-line 38, :hash "-1118940910"} {:id "defn/wake-armies", :kind "defn", :line 40, :end-line 42, :hash "-54629489"} {:id "defn/sleep-armies", :kind "defn", :line 44, :end-line 46, :hash "-312012962"} {:id "defn/remove-awake-army", :kind "defn", :line 48, :end-line 52, :hash "643700559"}]}
;; clj-mutate-manifest-end
