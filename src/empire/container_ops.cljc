(ns empire.container-ops
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.map-utils :as map-utils]
            [empire.unit-container :as uc]
            [empire.movement.visibility :as visibility]
            [empire.units.dispatcher :as dispatcher]))

;; Transport operations

(defn load-adjacent-sentry-armies
  "Loads adjacent sentry armies onto a transport at the given coords.
   Wakes up the transport if it has armies and is at a beach."
  [transport-coords]
  (let [cell (get-in @atoms/game-map transport-coords)
        unit (:contents cell)]
    (when (and (= (:type unit) :transport)
               (not (uc/full? unit :army-count config/transport-capacity)))
      (let [[tx ty] transport-coords
            height (count @atoms/game-map)
            width (count (first @atoms/game-map))]
        (doseq [[dx dy] map-utils/neighbor-offsets]
          (let [nx (+ tx dx)
                ny (+ ty dy)]
            (when (and (>= nx 0) (< nx height) (>= ny 0) (< ny width))
              (let [adj-cell (get-in @atoms/game-map [nx ny])
                    adj-unit (:contents adj-cell)
                    transport (get-in @atoms/game-map (conj transport-coords :contents))]
                (when (and adj-unit
                           (= (:type adj-unit) :army)
                           (= (:mode adj-unit) :sentry)
                           (= (:owner adj-unit) (:owner transport))
                           (not (uc/full? transport :army-count config/transport-capacity)))
                  (swap! atoms/game-map assoc-in [nx ny] (dissoc adj-cell :contents))
                  (swap! atoms/game-map update-in (conj transport-coords :contents) uc/add-unit :army-count))))))
        ;; After loading, wake transport if at beach with armies
        (let [updated-transport (get-in @atoms/game-map (conj transport-coords :contents))
              has-armies? (pos? (uc/get-count updated-transport :army-count))
              at-beach? (map-utils/adjacent-to-land? transport-coords atoms/game-map)]
          (when (and has-armies? at-beach? (= (:mode updated-transport) :sentry))
            (swap! atoms/game-map update-in (conj transport-coords :contents)
                   #(assoc % :mode :awake :reason :transport-at-beach))))))))

(defn wake-armies-on-transport
  "Wakes up all armies aboard the transport at the given coords.
   Sets steps-remaining to 0 to end the transport's turn."
  [transport-coords]
  (let [cell (get-in @atoms/game-map transport-coords)
        transport (:contents cell)
        updated-transport (-> transport
                              (uc/wake-all :army-count :awake-armies)
                              (assoc :mode :sentry)
                              (assoc :steps-remaining 0)
                              (dissoc :reason))
        updated-cell (assoc cell :contents updated-transport)]
    (swap! atoms/game-map assoc-in transport-coords updated-cell)))

(defn sleep-armies-on-transport
  "Puts all armies aboard the transport back to sleep (sentry mode).
   Wakes up the transport so it can receive orders."
  [transport-coords]
  (let [cell (get-in @atoms/game-map transport-coords)
        transport (:contents cell)
        updated-transport (-> transport
                              (uc/sleep-all :awake-armies)
                              (assoc :mode :awake)
                              (dissoc :reason))
        updated-cell (assoc cell :contents updated-transport)]
    (swap! atoms/game-map assoc-in transport-coords updated-cell)))

(defn disembark-army-from-transport
  "Removes first awake army from transport and places it on target land cell.
   Army remains awake and ready for orders. Other armies remain on transport.
   Wakes the transport when no more awake armies remain.
   Returns the coordinates where the army was placed."
  [transport-coords target-coords]
  (let [cell (get-in @atoms/game-map transport-coords)
        transport (:contents cell)
        after-remove (uc/remove-awake-unit transport :army-count :awake-armies)
        no-more-awake? (not (uc/has-awake? after-remove :awake-armies))
        disembarked-army {:type :army :mode :awake :owner (:owner transport) :hits 1
                         :steps-remaining (config/unit-speed :army)}
        updated-transport (cond-> after-remove
                            no-more-awake? (assoc :mode :awake)
                            no-more-awake? (dissoc :reason))
        updated-cell (assoc cell :contents updated-transport)]
    (swap! atoms/game-map assoc-in transport-coords updated-cell)
    (swap! atoms/game-map assoc-in (conj target-coords :contents) disembarked-army)
    (visibility/update-cell-visibility target-coords (:owner transport))
    target-coords))

(defn disembark-army-with-target
  "Removes first awake army from transport and places it on adjacent cell in moving mode.
   Army will continue moving toward the extended target on subsequent turns.
   Steps-remaining is 0 because the disembark used the army's one step."
  [transport-coords adjacent-coords extended-target]
  (let [cell (get-in @atoms/game-map transport-coords)
        transport (:contents cell)
        after-remove (uc/remove-awake-unit transport :army-count :awake-armies)
        no-more-awake? (not (uc/has-awake? after-remove :awake-armies))
        moving-army {:type :army :mode :moving :owner (:owner transport) :hits 1
                     :steps-remaining 0
                     :target extended-target}
        updated-transport (cond-> after-remove
                            no-more-awake? (assoc :mode :awake)
                            no-more-awake? (dissoc :reason))
        updated-cell (assoc cell :contents updated-transport)]
    (swap! atoms/game-map assoc-in transport-coords updated-cell)
    (swap! atoms/game-map assoc-in (conj adjacent-coords :contents) moving-army)
    (visibility/update-cell-visibility adjacent-coords (:owner transport))))

(defn disembark-army-to-explore
  "Removes first awake army from transport and places it on target land cell in explore mode.
   Returns the coordinates where the army was placed."
  [transport-coords target-coords]
  (let [cell (get-in @atoms/game-map transport-coords)
        transport (:contents cell)
        after-remove (uc/remove-awake-unit transport :army-count :awake-armies)
        no-more-awake? (not (uc/has-awake? after-remove :awake-armies))
        exploring-army {:type :army :mode :explore :owner (:owner transport) :hits 1
                        :steps-remaining (config/unit-speed :army)
                        :explore-steps config/explore-steps
                        :visited #{target-coords}}
        updated-transport (cond-> after-remove
                            no-more-awake? (assoc :mode :awake)
                            no-more-awake? (dissoc :reason))
        updated-cell (assoc cell :contents updated-transport)]
    (swap! atoms/game-map assoc-in transport-coords updated-cell)
    (swap! atoms/game-map assoc-in (conj target-coords :contents) exploring-army)
    (visibility/update-cell-visibility target-coords (:owner transport))
    target-coords))

;; Carrier operations

(defn wake-fighters-on-carrier
  "Wakes up all fighters aboard the carrier at the given coords."
  [carrier-coords]
  (let [cell (get-in @atoms/game-map carrier-coords)
        carrier (:contents cell)
        updated-carrier (-> carrier
                            (uc/wake-all :fighter-count :awake-fighters)
                            (assoc :mode :sentry)
                            (dissoc :reason))
        updated-cell (assoc cell :contents updated-carrier)]
    (swap! atoms/game-map assoc-in carrier-coords updated-cell)))

(defn sleep-fighters-on-carrier
  "Puts all fighters aboard the carrier back to sleep.
   Wakes up the carrier so it can receive orders."
  [carrier-coords]
  (let [cell (get-in @atoms/game-map carrier-coords)
        carrier (:contents cell)
        updated-carrier (-> carrier
                            (uc/sleep-all :awake-fighters)
                            (assoc :mode :awake)
                            (dissoc :reason))
        updated-cell (assoc cell :contents updated-carrier)]
    (swap! atoms/game-map assoc-in carrier-coords updated-cell)))

(defn launch-fighter-from-carrier
  "Removes first awake fighter from carrier and sets it moving to target.
   Fighter is placed at the adjacent cell toward target.
   Carrier stays in its current mode (sentry carriers remain sentry).
   Returns the coordinates where the fighter was placed."
  [carrier-coords target-coords]
  (let [cell (get-in @atoms/game-map carrier-coords)
        carrier (:contents cell)
        after-remove (uc/remove-awake-unit carrier :fighter-count :awake-fighters)
        ;; Calculate first step toward target
        [cx cy] carrier-coords
        [tx ty] target-coords
        dx (cond (zero? (- tx cx)) 0 (pos? (- tx cx)) 1 :else -1)
        dy (cond (zero? (- ty cy)) 0 (pos? (- ty cy)) 1 :else -1)
        first-step [(+ cx dx) (+ cy dy)]
        moving-fighter {:type :fighter :mode :moving :owner (:owner carrier) :fuel config/fighter-fuel :target target-coords :hits 1
                        :steps-remaining (dec (config/unit-speed :fighter))}
        updated-cell (assoc cell :contents after-remove)
        target-cell (get-in @atoms/game-map first-step)]
    ;; Update carrier
    (swap! atoms/game-map assoc-in carrier-coords updated-cell)
    ;; Place fighter at first step position
    (swap! atoms/game-map assoc-in first-step (assoc target-cell :contents moving-fighter))
    (visibility/update-cell-visibility first-step (:owner carrier))
    first-step))

;; Airport operations

(defn launch-fighter-from-airport
  "Removes first awake fighter from airport and sets it moving to target.
   Returns the coordinates where the fighter was placed."
  [city-coords target-coords]
  (let [cell (get-in @atoms/game-map city-coords)
        after-remove (uc/remove-awake-unit cell :fighter-count :awake-fighters)
        moving-fighter {:type :fighter :mode :moving :owner :player :fuel config/fighter-fuel :target target-coords :hits 1
                        :steps-remaining (config/unit-speed :fighter)}
        updated-cell (assoc after-remove :contents moving-fighter)]
    (swap! atoms/game-map assoc-in city-coords updated-cell)
    city-coords))

;; Shipyard operations

(defn launch-ship-from-shipyard
  "Removes ship at given index from city's shipyard and places on map.
   Reconstructs full unit from minimal shipyard data."
  [city-coords ship-index]
  (let [cell (get-in @atoms/game-map city-coords)
        ship-data (get-in cell [:shipyard ship-index])
        owner (case (:city-status cell)
                :player :player
                :computer :computer
                :player)  ; default to player for free cities
        ship {:type (:type ship-data)
              :owner owner
              :hits (:hits ship-data)
              :mode :awake
              :steps-remaining (dispatcher/speed (:type ship-data))}
        updated-city (uc/remove-ship-from-shipyard cell ship-index)]
    (swap! atoms/game-map assoc-in city-coords (assoc updated-city :contents ship))
    (visibility/update-cell-visibility city-coords owner)))
