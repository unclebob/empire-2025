(ns empire.ui.util.rendering.format
  (:require [empire.ui.util.rendering.format.hover :as hover]
            [empire.ui.util.rendering.format.production :as production]))

;; hover
(def should-show-paused? hover/should-show-paused?)
(def format-unit-status hover/format-unit-status)
(def format-city-status hover/format-city-status)
(def format-waypoint-status hover/format-waypoint-status)
(def format-hover-status hover/format-hover-status)
(def split-hover-status hover/split-hover-status)

;; production
(def format-production-status production/format-production-status)
(def compact-production-status production/compact-production-status)
(def hidden-production-status production/hidden-production-status)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T12:06:40.02441-05:00", :module-hash "1382392282", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-1400228180"} {:id "def/should-show-paused?", :kind "def", :line 6, :end-line 6, :hash "1594853644"} {:id "def/format-unit-status", :kind "def", :line 7, :end-line 7, :hash "1173297929"} {:id "def/format-city-status", :kind "def", :line 8, :end-line 8, :hash "-844985730"} {:id "def/format-waypoint-status", :kind "def", :line 9, :end-line 9, :hash "1844901061"} {:id "def/format-hover-status", :kind "def", :line 10, :end-line 10, :hash "-2077593552"} {:id "def/split-hover-status", :kind "def", :line 11, :end-line 11, :hash "-621852973"} {:id "def/format-production-status", :kind "def", :line 14, :end-line 14, :hash "653840398"} {:id "def/compact-production-status", :kind "def", :line 15, :end-line 15, :hash "-182865995"} {:id "def/hidden-production-status", :kind "def", :line 16, :end-line 16, :hash "1254755813"}]}
;; clj-mutate-manifest-end
