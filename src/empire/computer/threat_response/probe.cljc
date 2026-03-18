(ns empire.computer.threat-response.probe
  (:require [clojure.string :as str]
            [empire.state.api :as sa]))

(def ^:private log-path "target/major-invasion-probe.log")

(defn clear-log!
  []
  #?(:clj (spit log-path "")
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
  #?(:clj (spit log-path (format-entry kind payload) :append true)
     :cljs nil)
  (when (and (sa/read-state :headless-stop-on-major-invasion?)
             (not (sa/read-state :major-invasion-probe-hit?)))
    (sa/write-state! :major-invasion-probe-hit? true)
    (sa/write-state! :paused true)
    (sa/write-state! :error-message (str "Stopped on " (name kind) ". See " log-path))
    (sa/write-state! :error-until #?(:clj Long/MAX_VALUE
                                     :cljs js/Number.MAX_SAFE_INTEGER))))
