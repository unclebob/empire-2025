(ns empire.game-mechanics.movement.wake-conditions.pre-move
  (:require [empire.config.core :as config]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.config.units.dispatcher :as dispatcher]))

(defn- fighter-landing-on-carrier? [unit next-cell]
  (let [next-contents (:contents next-cell)]
    (and (= (:type unit) :fighter)
         (= (:type next-contents) :carrier)
         (= (:owner next-contents) (:owner unit))
         (not (uc/full? next-contents :fighter-count (dispatcher/effective-capacity :carrier (:hits next-contents)))))))

(defn- fighter-landing-at-city? [unit next-cell]
  (and (= (:type unit) :fighter)
       (= (:type next-cell) :city)
       (= (:city-status next-cell) :player)))

(defn- occupied-blocking-reason [unit next-cell]
  (when (and (:contents next-cell)
             (not (fighter-landing-on-carrier? unit next-cell))
             (not (fighter-landing-at-city? unit next-cell)))
    :somethings-in-the-way))

(defn- army-blocking-reason [cell-type cell-status city? hostile?]
  (cond
    (= cell-type :sea)                  :cant-move-into-water
    (and city? (= cell-status :player)) :cant-move-into-city
    hostile?                            :army-found-city))

(defn- naval-blocking-reason [cell-type city?]
  (cond
    (= cell-type :land) :ships-cant-drive-on-land
    city?               :ships-cant-enter-city))

(defn- hostile-city? [next-cell]
  (and (= (:type next-cell) :city)
       (config/hostile-city? (:city-status next-cell))))

(defn- unit-type-blocking-reason [unit next-cell hostile?]
  (case (:type unit)
    :army (army-blocking-reason (:type next-cell) (:city-status next-cell)
                                (= (:type next-cell) :city) hostile?)
    :fighter (when hostile? :fighter-over-defended-city)
    (when (dispatcher/naval-units (:type unit))
      (naval-blocking-reason (:type next-cell) (= (:type next-cell) :city)))))

(defn- blocking-wake-reason
  [unit next-cell]
  (or (occupied-blocking-reason unit next-cell)
      (unit-type-blocking-reason unit next-cell (hostile-city? next-cell))))

(defn- wake-unit-with-reason [unit reason]
  (assoc (dissoc (assoc unit :mode :awake) :target) :reason reason))

(defn wake-before-move
  [unit next-cell]
  (if-let [reason (blocking-wake-reason unit next-cell)]
    [(wake-unit-with-reason unit reason) true]
    [unit false]))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:35:51.794607-05:00", :module-hash "-620658165", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "2133100190"} {:id "defn-/fighter-landing-on-carrier?", :kind "defn-", :line 7, :end-line 12, :hash "-1430977648"} {:id "defn-/fighter-landing-at-city?", :kind "defn-", :line 14, :end-line 17, :hash "-666029366"} {:id "defn-/occupied-blocking-reason", :kind "defn-", :line 19, :end-line 23, :hash "110612946"} {:id "defn-/army-blocking-reason", :kind "defn-", :line 25, :end-line 29, :hash "1261272110"} {:id "defn-/naval-blocking-reason", :kind "defn-", :line 31, :end-line 34, :hash "-47713617"} {:id "defn-/hostile-city?", :kind "defn-", :line 36, :end-line 38, :hash "1644532547"} {:id "defn-/unit-type-blocking-reason", :kind "defn-", :line 40, :end-line 46, :hash "-1671425505"} {:id "defn-/blocking-wake-reason", :kind "defn-", :line 48, :end-line 51, :hash "-1220615722"} {:id "defn-/wake-unit-with-reason", :kind "defn-", :line 53, :end-line 54, :hash "-1604482991"} {:id "defn/wake-before-move", :kind "defn", :line 56, :end-line 60, :hash "1518781239"}]}
;; clj-mutate-manifest-end
