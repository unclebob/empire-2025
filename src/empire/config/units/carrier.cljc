(ns empire.config.units.carrier
  (:require [empire.config.units.config :as units-config]
            [empire.config.units.container :as container]))

(defn initial-state
  []
  {:fighter-count 0
   :awake-fighters 0})

(defn can-move-to?
  [cell]
  (container/sea-can-move-to? cell))

(defn needs-attention?
  [unit]
  (container/needs-attention? unit :awake-fighters))

(defn full?
  [unit]
  (container/full? unit :fighter-count units-config/carrier-capacity))

(defn has-fighters?
  [unit]
  (container/has-contained? unit :fighter-count))

(defn has-awake-fighters?
  [unit]
  (container/has-contained? unit :awake-fighters))

(defn add-fighter
  [unit]
  (container/add-contained unit :fighter-count))

(defn remove-fighter
  [unit]
  (container/remove-contained unit :fighter-count))

(defn wake-fighters
  [unit]
  (container/wake-contained unit :fighter-count :awake-fighters))

(defn sleep-fighters
  [unit]
  (container/sleep-contained unit :awake-fighters))

(defn remove-awake-fighter
  [unit]
  (container/remove-awake-contained unit :fighter-count :awake-fighters))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:41:14.850539-05:00", :module-hash "-120242575", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-464260100"} {:id "defn/initial-state", :kind "defn", :line 4, :end-line 7, :hash "-1301450262"} {:id "defn/can-move-to?", :kind "defn", :line 9, :end-line 12, :hash "-927721079"} {:id "defn/needs-attention?", :kind "defn", :line 14, :end-line 17, :hash "1707406277"} {:id "defn/full?", :kind "defn", :line 19, :end-line 21, :hash "887611612"} {:id "defn/has-fighters?", :kind "defn", :line 23, :end-line 25, :hash "-58498268"} {:id "defn/has-awake-fighters?", :kind "defn", :line 27, :end-line 29, :hash "-84387739"} {:id "defn/add-fighter", :kind "defn", :line 31, :end-line 33, :hash "39050578"} {:id "defn/remove-fighter", :kind "defn", :line 35, :end-line 37, :hash "-1131519273"} {:id "defn/wake-fighters", :kind "defn", :line 39, :end-line 41, :hash "-730805140"} {:id "defn/sleep-fighters", :kind "defn", :line 43, :end-line 45, :hash "1961051629"} {:id "defn/remove-awake-fighter", :kind "defn", :line 47, :end-line 51, :hash "43113909"}]}
;; clj-mutate-manifest-end
