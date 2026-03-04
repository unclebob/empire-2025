;; mutation-tested: no
(ns empire.domain.services.threat-policy)

(defonce ^:private methods-loaded?
  (delay
    (try
      (require 'empire.domain.services.impl.threat-policy)
      (catch #?(:clj Throwable :cljs :default) _
        nil))))

(defn- ensure-methods-loaded!
  []
  @methods-loaded?
  nil)

(defmulti fighter-response-count (fn [& _] (ensure-methods-loaded!) :default))
(defmulti ship-response-count (fn [& _] (ensure-methods-loaded!) :default))
(defmulti fighter-sweep-rounds (fn [& _] (ensure-methods-loaded!) :default))
(defmulti ship-scout-rounds (fn [& _] (ensure-methods-loaded!) :default))
(defmulti threat-radius (fn [& _] (ensure-methods-loaded!) :default))

(def ^:private enemy-ship-types
  #{:patrol-boat :destroyer :submarine :transport :carrier :battleship})

(defn- player-unit-type
  [game-cell]
  (let [unit (:contents game-cell)]
    (when (= :player (:owner unit))
      (:type unit))))

(defn- player-city?
  [game-cell]
  (and (= :city (:type game-cell))
       (= :player (:city-status game-cell))))

(defmulti detection-trigger (fn [& _] (ensure-methods-loaded!) :default))

(defmulti fighter-sweep-mission (fn [& _] (ensure-methods-loaded!) :default))

(defmulti sea-scout-mission (fn [& _] (ensure-methods-loaded!) :default))

(defmulti dec-threat-rounds (fn [& _] (ensure-methods-loaded!) :default))
