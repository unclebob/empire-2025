(ns empire.ui.util.rendering.display
  (:require [empire.ui.util.rendering.display.colors :as colors]
            [empire.ui.util.rendering.display.text :as text]
            [empire.ui.util.rendering.format :as fmt]))

(defn resolve-display-map
  "Returns the appropriate map based on map-to-display keyword."
  [map-to-display player-map computer-map game-map]
  ({:player-map player-map :computer-map computer-map :actual-map game-map} map-to-display))

(defn compute-hover-message
  "Looks up cell and production at coords in the-map, returns formatted hover string.
   Returns empty string when cell has no displayable status."
  [the-map production-map coords]
  (let [cell (get-in the-map coords)
        production (get production-map coords)]
    (or (fmt/format-hover-status coords cell production) "")))

(defn compute-hover-result
  "Computes hover message from map display state and mouse position."
  [map-to-display player-map computer-map game-map production coords]
  (let [the-map (resolve-display-map map-to-display player-map computer-map game-map)]
    (compute-hover-message the-map production coords)))

;; colors
(def determine-display-unit colors/determine-display-unit)
(def attention-unit-color colors/attention-unit-color)
(def production-indicator-data colors/production-indicator-data)
(def group-cells-by-color colors/group-cells-by-color)
(def completed-production-city? colors/completed-production-city?)

;; text
(def should-show-error? text/should-show-error?)
(def resolve-banner text/resolve-banner)
(def resolve-banner-pair text/resolve-banner-pair)
(def resolve-banner-list text/resolve-banner-list)
(def resolve-status-line text/resolve-status-line)
(def resolve-inspector-lines text/resolve-inspector-lines)
(def resolve-turn-text text/resolve-turn-text)
(def resolve-round-status-text text/resolve-round-status-text)
(def resolve-center-lines text/resolve-center-lines)
(def resolve-attention-zone text/resolve-attention-zone)
(def resolve-warning-zone text/resolve-warning-zone)
(def resolve-command-zone text/resolve-command-zone)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T11:55:44.528986-05:00", :module-hash "-954728763", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "-1057276397"} {:id "defn/resolve-display-map", :kind "defn", :line 6, :end-line 9, :hash "-442023512"} {:id "defn/compute-hover-message", :kind "defn", :line 11, :end-line 17, :hash "1130106368"} {:id "defn/compute-hover-result", :kind "defn", :line 19, :end-line 23, :hash "1222432952"} {:id "def/determine-display-unit", :kind "def", :line 26, :end-line 26, :hash "-1739916313"} {:id "def/attention-unit-color", :kind "def", :line 27, :end-line 27, :hash "1751327690"} {:id "def/production-indicator-data", :kind "def", :line 28, :end-line 28, :hash "-862321956"} {:id "def/group-cells-by-color", :kind "def", :line 29, :end-line 29, :hash "389909284"} {:id "def/should-show-error?", :kind "def", :line 32, :end-line 32, :hash "-1484883968"} {:id "def/resolve-banner", :kind "def", :line 33, :end-line 33, :hash "-1624956605"} {:id "def/resolve-banner-pair", :kind "def", :line 34, :end-line 34, :hash "-1104115904"} {:id "def/resolve-banner-list", :kind "def", :line 35, :end-line 35, :hash "-1976963895"} {:id "def/resolve-status-line", :kind "def", :line 36, :end-line 36, :hash "313927942"} {:id "def/resolve-inspector-lines", :kind "def", :line 37, :end-line 37, :hash "-1820155688"} {:id "def/resolve-turn-text", :kind "def", :line 38, :end-line 38, :hash "1377221148"} {:id "def/resolve-round-status-text", :kind "def", :line 39, :end-line 39, :hash "-49962932"} {:id "def/resolve-center-lines", :kind "def", :line 40, :end-line 40, :hash "328979740"}]}
;; clj-mutate-manifest-end
