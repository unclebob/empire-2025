(ns empire.computer.threat-response.probe
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [empire.state.api :as sa]))

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

(defn- visible-player-evidence
  [computer-map]
  (vec
   (for [x (range (count computer-map))
         y (range (count (first computer-map)))
         :let [cell (get-in computer-map [x y])
               unit (:contents cell)]
         :when (or (= :player (:city-status cell))
                   (= :player (:owner unit)))]
     {:pos [x y]
      :cell-type (:type cell)
      :city-status (:city-status cell)
      :unit-type (:type unit)
      :unit-owner (:owner unit)})))

(defn- actual-player-evidence
  [game-map]
  (vec
   (for [x (range (count game-map))
         y (range (count (first game-map)))
         :let [cell (get-in game-map [x y])
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
        visible-player (visible-player-evidence computer-map)
        actual-player (actual-player-evidence game-map)]
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
    (sa/write-state! :error-message (str "Stopped on " (name kind) ". See " log-path))
    (sa/write-state! :error-until #?(:clj Long/MAX_VALUE
                                     :cljs js/Number.MAX_SAFE_INTEGER))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-29T18:25:38.914556082-04:00", :module-hash "1198535082", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "1330868388"} {:id "def/log-path", :kind "def", :line 6, :end-line 6, :hash "-444493232"} {:id "defn-/ensure-log-parent!", :kind "defn-", :line 8, :end-line 11, :hash "1773186771"} {:id "defn/clear-log!", :kind "defn", :line 13, :end-line 18, :hash "62872133"} {:id "defn-/stack-lines", :kind "defn-", :line 20, :end-line 25, :hash "-85849438"} {:id "defn-/visible-player-evidence", :kind "defn-", :line 27, :end-line 40, :hash "-1205361268"} {:id "defn-/actual-player-evidence", :kind "defn-", :line 42, :end-line 55, :hash "-65330508"} {:id "defn-/format-entry", :kind "defn-", :line 57, :end-line 73, :hash "-1491768849"} {:id "defn/log-event!", :kind "defn", :line 75, :end-line 87, :hash "-1127165697"}]}
;; clj-mutate-manifest-end
