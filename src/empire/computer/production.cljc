(ns empire.computer.production
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.production :as production]))

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

(defn- need-transports?
  "Returns true if we need more transports for invasion.
   Build first transport after 6 armies, then another after each 6 more."
  [unit-counts]
  (let [armies (get unit-counts :army 0)
        transports (get unit-counts :transport 0)]
    (>= armies (* 6 (inc transports)))))

(defn- need-fighters?
  "Returns true if we need air support.
   Request fighters after we have 6+ armies (past early buildup)."
  [unit-counts]
  (let [fighters (get unit-counts :fighter 0)
        armies (get unit-counts :army 0)]
    (and (>= armies 6)
         (< fighters 2))))

(defn- need-warships?
  "Returns true if we need naval combat vessels.
   Only request warships when we have enough armies for existing transports
   (i.e., at transport capacity, not still building toward next transport)."
  [unit-counts]
  (let [destroyers (get unit-counts :destroyer 0)
        patrol-boats (get unit-counts :patrol-boat 0)
        transports (get unit-counts :transport 0)
        armies (get unit-counts :army 0)]
    (and (pos? transports)
         (= armies (* 6 transports))
         (< (+ destroyers patrol-boats) 2))))

(defn decide-production
  "Decides what a computer city should produce based on strategic needs.
   Priority: build 6 armies, then transport, then 6 more armies, repeat.
   Skips transport production if city is marked :no-more-transports."
  [city-pos]
  (let [unit-counts (count-computer-units)
        coastal? (city-is-coastal? city-pos)
        city-cell (get-in @atoms/game-map city-pos)
        no-more-transports? (:no-more-transports city-cell)]
    (cond
      ;; Coastal cities: build transport after every 6 armies (unless blocked)
      (and coastal? (not no-more-transports?) (need-transports? unit-counts))
      :transport

      ;; Warships only after we have transport capacity
      (and coastal? (need-warships? unit-counts))
      (rand-nth [:destroyer :patrol-boat])

      ;; Fighters only after we have transport capacity
      (need-fighters? unit-counts)
      :fighter

      ;; Default to armies
      :else
      :army)))

(defn process-computer-city
  "Processes a computer city. Sets production if none exists."
  [pos]
  (when-not (@atoms/production pos)
    (let [unit-type (decide-production pos)]
      (production/set-city-production pos unit-type))))
