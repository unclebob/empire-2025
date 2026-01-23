(ns empire.computer.production
  "Computer production module - gutted for CommandingGeneral refactor.
   Decision logic removed; production decisions will come from CommandingGeneral."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.player.production :as production]))

;; Preserved utilities

(defn- get-neighbors
  "Returns valid neighbor coordinates for a position."
  [pos]
  (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets
                                    some?))

(defn city-is-coastal?
  "Returns true if city has adjacent sea cells."
  [city-pos]
  (some (fn [neighbor]
          (= :sea (:type (get-in @atoms/game-map neighbor))))
        (get-neighbors city-pos)))

(defn count-computer-units
  "Counts computer units by type. Returns map of type to count."
  []
  (let [units (for [i (range (count @atoms/game-map))
                    j (range (count (first @atoms/game-map)))
                    :let [cell (get-in @atoms/game-map [i j])
                          unit (:contents cell)]
                    :when (and unit (= :computer (:owner unit)))]
                (:type unit))]
    (frequencies units)))

;; Decision logic removed. Production decisions will come from CommandingGeneral.

(defn process-computer-city
  "Processes a computer city. Currently does nothing - awaits CommandingGeneral."
  [pos]
  nil)
