(ns empire.config.units.ships
  (:require [empire.config.domain.core.ship-config :as ship-config]))

(def config ship-config/config)

(defn initial-state
  []
  {})

(defn can-move-to? [cell]
  (and cell (= (:type cell) :sea)))

(defn needs-attention? [unit]
  (= (:mode unit) :awake))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:47:50.438658-05:00", :module-hash "1927536432", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-838414538"} {:id "def/configs", :kind "def", :line 3, :end-line 7, :hash "-1960861086"} {:id "defn/config", :kind "defn", :line 9, :end-line 10, :hash "-1916618753"} {:id "defn/initial-state", :kind "defn", :line 12, :end-line 14, :hash "-142005869"} {:id "defn/can-move-to?", :kind "defn", :line 16, :end-line 17, :hash "-927721079"} {:id "defn/needs-attention?", :kind "defn", :line 19, :end-line 20, :hash "335118728"}]}
;; clj-mutate-manifest-end
