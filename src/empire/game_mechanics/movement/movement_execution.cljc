(ns empire.game-mechanics.movement.movement-execution
  (:require [empire.config.core :as config]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.state.api :as sa]
            [empire.game-mechanics.visibility :as visibility]
            [empire.config.units.dispatcher :as dispatcher]))

(defn- update-game-map!
  [f & args]
  (apply sa/update-world! f args))

(defn- current-world
  []
  (sa/current-world))

(defn process-consumables [unit to-cell]
  (if (and unit (= (:type unit) :fighter))
    (if (= (:type to-cell) :city)
      unit
      (let [current-fuel (:fuel unit config/fighter-fuel)
            new-fuel (dec current-fuel)]
        (if (<= new-fuel -1)
          nil
          (assoc unit :fuel new-fuel))))
    unit))

(defn- fighter-landing-city? [unit to-cell]
  (and unit
       (= (:type unit) :fighter)
       (= (:type to-cell) :city)
       (= (:city-status to-cell) :player)))

(defn- fighter-landing-carrier? [unit to-cell]
  (let [to-contents (:contents to-cell)]
    (and unit
         (= (:type unit) :fighter)
         (= (:type to-contents) :carrier)
         (= (:owner to-contents) (:owner unit))
         (not (uc/full? to-contents :fighter-count (dispatcher/effective-capacity :carrier (:hits to-contents)))))))

(defn- classify-move [processed-unit to-cell _original-target _final-pos]
  (cond
    (nil? processed-unit) :unit-destroyed
    (fighter-landing-city? processed-unit to-cell) :fighter-land-at-city
    (fighter-landing-carrier? processed-unit to-cell) :fighter-land-on-carrier
    :else :normal-move))

(defn- land-fighter-at-city [to-cell _unit]
  (uc/add-unit to-cell :fighter-count))

(defn- land-fighter-on-carrier [to-cell _unit]
  (update to-cell :contents uc/add-unit :fighter-count))

(def ^:private destination-updaters
  {:unit-destroyed (fn [to-cell _unit] to-cell)
   :fighter-land-at-city land-fighter-at-city
   :fighter-land-on-carrier land-fighter-on-carrier
   :normal-move (fn [to-cell unit] (assoc to-cell :contents unit))})

(defn update-destination-cell [move-type to-cell processed-unit]
  ((destination-updaters move-type) to-cell processed-unit))

(defn do-move [from-coords final-pos cell final-unit]
  (let [from-cell (dissoc cell :contents)
        to-cell (get-in (current-world) final-pos)
        processed-unit (process-consumables final-unit to-cell)
        owner (get-in cell [:contents :owner])
        original-target (:target (:contents cell))
        move-type (classify-move processed-unit to-cell original-target final-pos)
        updated-to-cell (update-destination-cell move-type to-cell processed-unit)]
    (update-game-map! assoc-in from-coords from-cell)
    (update-game-map! assoc-in final-pos updated-to-cell)
    (visibility/update-cell-visibility from-coords owner)
    (visibility/update-cell-visibility final-pos owner)
    (when (= (:type processed-unit) :transport)
      (container-ops/load-adjacent-sentry-armies final-pos))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:01:11.804695-05:00", :module-hash "-1660016225", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-1662838984"} {:id "defn-/update-game-map!", :kind "defn-", :line 9, :end-line 11, :hash "1805137569"} {:id "defn-/current-world", :kind "defn-", :line 13, :end-line 15, :hash "-640438772"} {:id "defn/process-consumables", :kind "defn", :line 17, :end-line 26, :hash "-1697464319"} {:id "defn-/fighter-landing-city?", :kind "defn-", :line 28, :end-line 32, :hash "1355442950"} {:id "defn-/fighter-landing-carrier?", :kind "defn-", :line 34, :end-line 40, :hash "-933514139"} {:id "defn-/classify-move", :kind "defn-", :line 42, :end-line 47, :hash "-865682671"} {:id "defn-/land-fighter-at-city", :kind "defn-", :line 49, :end-line 50, :hash "-2016618902"} {:id "defn-/land-fighter-on-carrier", :kind "defn-", :line 52, :end-line 53, :hash "-840788205"} {:id "def/destination-updaters", :kind "def", :line 55, :end-line 59, :hash "1808783798"} {:id "defn/update-destination-cell", :kind "defn", :line 61, :end-line 62, :hash "-956624395"} {:id "defn/do-move", :kind "defn", :line 64, :end-line 77, :hash "869945486"}]}
;; clj-mutate-manifest-end
