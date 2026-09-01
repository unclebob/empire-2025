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

(defn- unit-count-tooltip-line
  [token]
  (when (re-matches #"[AFTDSPCBZ]:\d+" token)
    (let [label (subs token 0 1)
          n (subs token 2)]
      (when (not= n "0")
        (str n " " (get production-token-labels label "units"))))))

(defn- extra-production-tooltip-line
  [token]
  (cond
    (re-matches #"Landed:\d+" token)
    (str (subs token 7) " fighters landed at airports")
    (re-matches #"Repair:\d+" token)
    (str (subs token 7) " ships in repair")))

(defn full-production-tooltip
  "Builds a comprehensive production summary from the production-status string."
  [production-status]
  (when production-status
    (let [tokens (clojure.string/split production-status #"\s+")]
      (clojure.string/join ", " (concat (keep unit-count-tooltip-line tokens)
                                        (keep extra-production-tooltip-line tokens))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:04:26.127505-05:00", :module-hash "-1591721932", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-1415477505"} {:id "def/production-token-labels", :kind "def", :line 4, :end-line nil, :hash "2064211572"} {:id "def/fixed-token-tooltips", :kind "def", :line 15, :end-line nil, :hash "-772142312"} {:id "defn-/round-tooltip", :kind "defn-", :line 25, :end-line nil, :hash "815943068"} {:id "defn-/counted-token-tooltip", :kind "defn-", :line 30, :end-line nil, :hash "40877624"} {:id "defn/status-token-tooltip", :kind "defn", :line 48, :end-line nil, :hash "1161146077"} {:id "defn-/unit-count-tooltip-line", :kind "defn-", :line 55, :end-line nil, :hash "1920184849"} {:id "defn-/extra-production-tooltip-line", :kind "defn-", :line 63, :end-line nil, :hash "-895489545"} {:id "defn/full-production-tooltip", :kind "defn", :line 71, :end-line nil, :hash "684507060"}]}
;; clj-mutate-manifest-end
