;; mutation-tested: no
(ns empire.config.units.unit-metrics
  (:require [empire.config.domain.core.unit-metrics :as unit-metrics]))

(def naval-unit? unit-metrics/naval-unit?)
(def scale-by-hits unit-metrics/scale-by-hits)
(def effective-speed unit-metrics/effective-speed)
(def effective-capacity unit-metrics/effective-capacity)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:50:31.33339-05:00", :module-hash "1971052060", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 2, :hash "1776888221"} {:id "def/naval-units", :kind "def", :line 4, :end-line 5, :hash "-569052656"} {:id "defn/naval-unit?", :kind "defn", :line 7, :end-line 9, :hash "838680342"} {:id "defn/scale-by-hits", :kind "defn", :line 11, :end-line 14, :hash "-679300535"} {:id "defn/effective-speed", :kind "defn", :line 16, :end-line 18, :hash "-743163770"} {:id "defn/effective-capacity", :kind "defn", :line 20, :end-line 22, :hash "-2107664833"}]}
;; clj-mutate-manifest-end
