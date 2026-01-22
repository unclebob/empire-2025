(ns empire.attention
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.movement :as movement]
            [empire.unit-container :as uc]))

(defn is-unit-needing-attention?
  "Returns true if there is an attention-needing unit."
  [attention-coords]
  (and (seq attention-coords)
       (let [first-cell (get-in @atoms/game-map (first attention-coords))
             unit (:contents first-cell)]
         (or unit
             (uc/has-awake? first-cell :awake-fighters)
             (pos? (:awake-armies unit 0))))))

(defn is-city-needing-attention?
  "Returns true if the cell needs city handling as the first attention item."
  [cell clicked-coords attention-coords]
  (and (= (:city-status cell) :player)
       (= (:type cell) :city)
       (= clicked-coords (first attention-coords))))

(defn needs-attention?
  "Returns true if the cell at [i j] needs attention (awake unit, city with no production, awake airport fighter, carrier with awake fighters, or transport with awake armies).
   Satellites only need attention when they have no target."
  [i j]
  (let [cell (get-in @atoms/player-map [i j])
        unit (:contents cell)
        mode (:mode unit)
        satellite-with-target? (and (= (:type unit) :satellite) (:target unit))
        has-awake-airport-fighter? (uc/has-awake? cell :awake-fighters)
        has-awake-army-aboard? (pos? (:awake-armies unit 0))
        has-awake-carrier-fighter? (and (= (:type unit) :carrier)
                                        (uc/has-awake? unit :awake-fighters))]
    (and (not satellite-with-target?)
         (or (= (:city-status cell) :player)
             (= (:owner unit) :player)
             has-awake-airport-fighter?
             has-awake-carrier-fighter?)
         (or (= mode :awake)
             has-awake-airport-fighter?
             has-awake-army-aboard?
             has-awake-carrier-fighter?
             (and (= (:type cell) :city)
                  (not (@atoms/production [i j])))))))

(defn cells-needing-attention
  "Returns coordinates of player's units and cities with no production."
  []
  (for [i (range (count @atoms/player-map))
        j (range (count (first @atoms/player-map)))
        :when (needs-attention? i j)]
    [i j]))

(defn item-needs-attention?
  "Returns true if the item at coords needs user input.
   Satellites only need attention when they have no target."
  [coords]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        satellite-with-target? (and (= (:type unit) :satellite) (:target unit))
        has-awake-airport-fighter? (uc/has-awake? cell :awake-fighters)
        has-awake-army-aboard? (pos? (:awake-armies unit 0))
        has-awake-carrier-fighter? (and (= (:type unit) :carrier)
                                        (uc/has-awake? unit :awake-fighters))]
    (and (not satellite-with-target?)
         (or (= (:mode unit) :awake)
             has-awake-airport-fighter?
             has-awake-army-aboard?
             has-awake-carrier-fighter?
             (and (= (:type cell) :city)
                  (= (:city-status cell) :player)
                  (not (@atoms/production coords)))))))

;; Returns true if an army at coords has an adjacent hostile city it could attack.
;; Used to set the attention reason to :army-found-city when no other reason exists.
(defn- army-adjacent-to-enemy-city? [coords active-unit]
  (and (= :army (:type active-unit))
       (let [[ax ay] coords]
         (some (fn [[di dj]]
                 (let [adj-cell (get-in @atoms/game-map [(+ ax di) (+ ay dj)])]
                   (and adj-cell
                        (= (:type adj-cell) :city)
                        (config/hostile-city? (:city-status adj-cell)))))
               map-utils/neighbor-offsets))))

(defn set-attention-message
  "Sets the message for the current item needing attention."
  [coords]
  (let [cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        active-unit (movement/get-active-unit cell)
        is-airport-fighter? (movement/is-fighter-from-airport? active-unit)
        is-carrier-fighter? (movement/is-fighter-from-carrier? active-unit)
        is-army-aboard? (movement/is-army-aboard-transport? active-unit)]
    (reset! atoms/message (cond
                            is-airport-fighter?
                            (str "Fighter" (:unit-needs-attention config/messages) " - " (:fighter-landed-and-refueled config/messages))

                            is-carrier-fighter?
                            (str "Fighter" (:unit-needs-attention config/messages) " - aboard carrier (" (:fighter-count unit 0) " fighters)")

                            is-army-aboard?
                            (str "Army" (:unit-needs-attention config/messages) " - aboard transport (" (:army-count unit 0) " armies) - " (:transport-at-beach config/messages))

                            active-unit
                            (let [unit-type (:type active-unit)
                                  unit-name (name unit-type)
                                  max-hits (config/item-hits unit-type)
                                  current-hits (:hits active-unit max-hits)
                                  damaged? (< current-hits max-hits)
                                  damage-prefix (if damaged? "Damaged " "")
                                  cargo-str (cond
                                              (= unit-type :transport)
                                              (str " (" (:army-count active-unit 0) " armies)")
                                              (= unit-type :carrier)
                                              (str " (" (:fighter-count active-unit 0) " fighters)")
                                              :else nil)
                                  reason-key (or (:reason active-unit)
                                                 (when (army-adjacent-to-enemy-city? coords active-unit) :army-found-city))
                                  reason-str (when reason-key
                                               (if (string? reason-key)
                                                 reason-key
                                                 (reason-key config/messages)))]
                              (str damage-prefix unit-name (:unit-needs-attention config/messages) (or cargo-str "") (if reason-str (str " - " reason-str) "")))

                            :else
                            (:city-needs-attention config/messages)))))
