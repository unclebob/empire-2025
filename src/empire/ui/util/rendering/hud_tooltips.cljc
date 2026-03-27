(ns empire.ui.util.rendering.hud-tooltips
  (:require [empire.ui.util.rendering.format :as fmt]))

(def ^:private production-token-labels
  {"A" "armies"
   "F" "fighters"
   "T" "transports"
   "D" "destroyers"
   "S" "submarines"
   "P" "patrol boats"
   "C" "carriers"
   "B" "battleships"
   "Z" "satellites"})

(def ^:private fixed-token-tooltips
  {"PAUSED" "Game updates are paused."
   "Comp" "Showing the computer map."
   "Actual" "Showing the actual map."
   "Dest" "Current destination."
   "Flight" "Current flight path orders."
   "March" "Current marching orders."
   "Waypoint" "Current waypoint orders."
   "Lookaround" "Current lookaround orders."})

(defn- round-tooltip
  [token]
  (when (re-matches #"R\d+" token)
    (str "Round " (subs token 1) ".")))

(defn- counted-token-tooltip
  [token production-status]
  (cond
    (re-matches #"[AFTDSPCBZ]\d+" token)
    (let [label (subs token 0 1)
          count (subs token 1)]
      (str count " " (get production-token-labels label "units") "."))

    (re-matches #"\+\d+" token)
    (if-let [hidden (fmt/hidden-production-status production-status)]
      (str "Hidden counts: " hidden ".")
      (str (subs token 1) " more unit types not shown."))

    (re-matches #"\d+%" token)
    (str token " of the map has been explored by the player.")

    :else nil))

(defn status-token-tooltip
  "Explains abbreviated HUD status tokens."
  [token production-status]
  (or (get fixed-token-tooltips token)
      (round-tooltip token)
      (counted-token-tooltip token production-status)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:59:30.774241-05:00", :module-hash "990770620", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-1415477505"} {:id "def/production-token-labels", :kind "def", :line 4, :end-line 13, :hash "2064211572"} {:id "def/fixed-token-tooltips", :kind "def", :line 15, :end-line 23, :hash "-772142312"} {:id "defn-/round-tooltip", :kind "defn-", :line 25, :end-line 28, :hash "815943068"} {:id "defn-/counted-token-tooltip", :kind "defn-", :line 30, :end-line 46, :hash "40877624"} {:id "defn/status-token-tooltip", :kind "defn", :line 48, :end-line 53, :hash "1161146077"}]}
;; clj-mutate-manifest-end
