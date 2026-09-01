(ns empire.computer.threat-response.probe
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [empire.state.api :as sa]
            [empire.notifications :as notifications]))

(def ^:private log-path "target/major-invasion-probe.log")

(defn- ensure-log-parent!
  []
  #?(:clj (io/make-parents (io/file log-path))
     :cljs nil))

(defn clear-log!
  []
  #?(:clj (do
            (ensure-log-parent!)
            (spit log-path ""))
     :cljs nil))

(defn- stack-lines
  []
  #?(:clj (->> (.getStackTrace (Thread/currentThread))
               (map str)
               vec)
     :cljs []))

(defn- player-evidence
  [world]
  (vec
   (for [x (range (count world))
         y (range (count (first world)))
         :let [cell (get-in world [x y])
               unit (:contents cell)]
         :when (or (= :player (:city-status cell))
                   (= :player (:owner unit)))]
     {:pos [x y]
      :cell-type (:type cell)
      :city-status (:city-status cell)
      :unit-type (:type unit)
      :unit-owner (:owner unit)})))

(defn- format-entry
  [kind payload]
  (let [computer-map (sa/read-state :computer-map)
        game-map (sa/read-state :game-map)
        visible-player (player-evidence computer-map)
        actual-player (player-evidence game-map)]
    (str "=== " (name kind) " ===\n"
         "round: " (pr-str (sa/read-state :round-number)) "\n"
         "payload: " (pr-str payload) "\n"
         "major-invasion-state: " (pr-str (sa/read-state :major-invasion-state)) "\n"
         "visible-player-on-computer-map-count: " (count visible-player) "\n"
         "visible-player-on-computer-map: " (pr-str visible-player) "\n"
         "actual-player-on-game-map-count: " (count actual-player) "\n"
         "actual-player-on-game-map: " (pr-str actual-player) "\n"
         "stack:\n"
         (str/join "\n" (stack-lines))
         "\n\n")))

(defn log-event!
  [kind payload]
  #?(:clj (do
            (ensure-log-parent!)
            (spit log-path (format-entry kind payload) :append true))
     :cljs nil)
  (when (and (sa/read-state :headless-stop-on-major-invasion?)
             (not (sa/read-state :major-invasion-probe-hit?)))
    (sa/write-state! :major-invasion-probe-hit? true)
    (sa/write-state! :paused true)
    (notifications/warn! (str "Stopped on " (name kind) ". See " log-path))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:04:58.393459-05:00", :module-hash "-312072661", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-567330547"} {:id "def/log-path", :kind "def", :line 7, :end-line nil, :hash "-444493232"} {:id "defn-/ensure-log-parent!", :kind "defn-", :line 9, :end-line nil, :hash "1773186771"} {:id "defn/clear-log!", :kind "defn", :line 14, :end-line nil, :hash "62872133"} {:id "defn-/stack-lines", :kind "defn-", :line 21, :end-line nil, :hash "-85849438"} {:id "defn-/player-evidence", :kind "defn-", :line 28, :end-line nil, :hash "-2007615286"} {:id "defn-/format-entry", :kind "defn-", :line 43, :end-line nil, :hash "-1260980058"} {:id "defn/log-event!", :kind "defn", :line 61, :end-line nil, :hash "-708470391"}]}
;; clj-mutate-manifest-end
