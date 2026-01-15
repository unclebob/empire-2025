(ns empire.game-loop
  (:require [empire.atoms :as atoms]
            [empire.attention :as attention]
            [empire.config :as config]
            [empire.movement :as movement]
            [empire.production :as production]
            [empire.unit-container :as uc]))

(defn update-player-map
  "Reveals cells near player-owned units on the visible map."
  []
  (movement/update-combatant-map atoms/player-map :player))

(defn update-computer-map
  "Updates the computer's visible map by revealing cells near computer-owned units."
  []
  (movement/update-combatant-map atoms/computer-map :computer))

(defn remove-dead-units
  "Removes units with hits at or below zero."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                contents (:contents cell)]
          :when (and contents (<= (:hits contents 1) 0))]
    (swap! atoms/game-map assoc-in [i j] (dissoc cell :contents))
    (movement/update-cell-visibility [i j] (:owner contents))))

(defn build-player-items
  "Builds list of player city/unit coordinates to process this round."
  []
  (for [i (range (count @atoms/game-map))
        j (range (count (first @atoms/game-map)))
        :let [cell (get-in @atoms/game-map [i j])]
        :when (or (= (:city-status cell) :player)
                  (= (:owner (:contents cell)) :player))]
    [i j]))

(defn move-current-unit
  "Moves the unit at coords one step. Returns new coords if still moving, nil if done.
   Sidesteps consume a step but continue moving if steps remain."
  ([coords] (move-current-unit coords 10))  ;; max 10 consecutive sidesteps to prevent infinite loops
  ([coords max-sidesteps]
   (let [cell (get-in @atoms/game-map coords)
         unit (:contents cell)]
     (when (= (:mode unit) :moving)
       (let [target (:target unit)
             {:keys [result pos]} (movement/move-unit coords target cell atoms/game-map)]
         (case result
           ;; Sidestep - consume a step, continue if steps remain
           :sidestep
           (let [moved-cell (get-in @atoms/game-map pos)
                 moved-unit (:contents moved-cell)]
             (when moved-unit
               (let [new-steps (dec (:steps-remaining moved-unit 1))]
                 (swap! atoms/game-map assoc-in (conj pos :contents :steps-remaining) new-steps)
                 (when (> new-steps 0)
                   (if (pos? max-sidesteps)
                     (recur pos (dec max-sidesteps))
                     pos)))))

           ;; Normal move - decrement steps and continue if steps remain
           :normal
           (let [moved-cell (get-in @atoms/game-map pos)
                 moved-unit (:contents moved-cell)]
             (when moved-unit
               (let [new-steps (dec (:steps-remaining moved-unit 1))]
                 (swap! atoms/game-map assoc-in (conj pos :contents :steps-remaining) new-steps)
                 (when (> new-steps 0)
                   pos))))

           ;; Woke up - done moving
           :woke nil))))))

(defn reset-steps-remaining
  "Resets steps-remaining for all player units at start of round."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                unit (:contents cell)]
          :when (and unit (= (:owner unit) :player))]
    (let [steps (get config/unit-speed (:type unit) 1)]
      (swap! atoms/game-map assoc-in [i j :contents :steps-remaining] steps))))

(defn wake-airport-fighters
  "Wakes all fighters in player city airports at start of round.
   Fighters will be auto-launched if the city has a flight-path,
   otherwise they will demand attention."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])]
          :when (and (= (:type cell) :city)
                     (= (:city-status cell) :player)
                     (pos? (uc/get-count cell :fighter-count)))]
    (let [total (uc/get-count cell :fighter-count)]
      (swap! atoms/game-map assoc-in [i j :awake-fighters] total))))

(defn wake-carrier-fighters
  "Wakes all fighters on player carriers at start of round.
   Fighters will be auto-launched if the carrier has a flight-path,
   otherwise they will demand attention."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :carrier (:type unit))
                     (= :player (:owner unit))
                     (pos? (uc/get-count unit :fighter-count)))]
    (let [total (uc/get-count unit :fighter-count)]
      (swap! atoms/game-map assoc-in [i j :contents :awake-fighters] total))))

(defn consume-sentry-fighter-fuel
  "Consumes fuel for sentry fighters each round, applying fuel warnings."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :fighter (:type unit))
                     (= :sentry (:mode unit)))]
    (let [current-fuel (:fuel unit config/fighter-fuel)
          new-fuel (dec current-fuel)
          pos [i j]
          bingo-threshold (quot config/fighter-fuel 4)
          low-fuel? (<= new-fuel 1)
          bingo-fuel? (and (<= new-fuel bingo-threshold)
                           (movement/friendly-city-in-range? pos new-fuel atoms/game-map))]
      (cond
        (<= new-fuel 0)
        (swap! atoms/game-map assoc-in [i j :contents :hits] 0)

        low-fuel?
        (swap! atoms/game-map update-in [i j :contents]
               #(assoc % :fuel new-fuel :mode :awake :reason :fighter-out-of-fuel))

        bingo-fuel?
        (swap! atoms/game-map update-in [i j :contents]
               #(assoc % :fuel new-fuel :mode :awake :reason :fighter-bingo))

        :else
        (swap! atoms/game-map assoc-in [i j :contents :fuel] new-fuel)))))

(defn item-processed
  "Called when user input has been processed for current item."
  []
  (reset! atoms/waiting-for-input false)
  (reset! atoms/message "")
  (reset! atoms/cells-needing-attention []))

(defn- find-satellite-coords
  "Returns coordinates of all satellites on the map.
   Returns a vector to avoid lazy evaluation issues during map modification."
  []
  (vec (for [i (range (count @atoms/game-map))
             j (range (count (first @atoms/game-map)))
             :let [cell (get-in @atoms/game-map [i j])
                   contents (:contents cell)]
             :when (= (:type contents) :satellite)]
         [i j])))

(defn- move-satellite-steps
  "Moves a satellite the number of steps based on its speed.
   Decrements turns-remaining once per round.
   Returns final position or nil if satellite expired."
  [start-coords]
  (loop [coords start-coords
         steps-left (config/unit-speed :satellite)]
    (let [cell (get-in @atoms/game-map coords)
          satellite (:contents cell)]
      (cond
        ;; No satellite here (already removed or error)
        (not satellite)
        nil

        ;; Satellite expired
        (<= (:turns-remaining satellite 0) 0)
        (do (swap! atoms/game-map update-in coords dissoc :contents)
            (movement/update-cell-visibility coords (:owner satellite))
            nil)

        ;; No more steps this round - decrement turns-remaining once per round
        (zero? steps-left)
        (let [new-turns (dec (:turns-remaining satellite 1))]
          (if (<= new-turns 0)
            (do (swap! atoms/game-map update-in coords dissoc :contents)
                (movement/update-cell-visibility coords (:owner satellite))
                nil)
            (do (swap! atoms/game-map assoc-in (conj coords :contents :turns-remaining) new-turns)
                coords)))

        ;; Move one step
        :else
        (let [new-coords (movement/move-satellite coords)]
          (recur new-coords (dec steps-left)))))))

(defn move-satellites
  "Moves all satellites according to their speed.
   Removes satellites with turns-remaining at or below zero."
  []
  (doseq [coords (find-satellite-coords)]
    (move-satellite-steps coords)))

(defn start-new-round
  "Starts a new round by building player items list and updating game state."
  []
  (swap! atoms/round-number inc)
  (move-satellites)
  (consume-sentry-fighter-fuel)
  (remove-dead-units)
  (production/update-production)
  (reset-steps-remaining)
  (wake-airport-fighters)
  (wake-carrier-fighters)
  (reset! atoms/player-items (vec (build-player-items)))
  (reset! atoms/waiting-for-input false)
  (reset! atoms/message "")
  (reset! atoms/cells-needing-attention []))

(defn move-explore-unit
  "Moves an exploring unit. Returns new coords if still exploring, nil if done."
  [coords]
  (movement/move-explore-unit coords))

(defn- auto-launch-fighter [coords cell]
  "Auto-launches a fighter from city airport or carrier if flight-path is set.
   Returns new coords if launched, nil otherwise."
  (let [flight-path (or (:flight-path cell)
                        (:flight-path (:contents cell)))
        has-awake-airport-fighter? (uc/has-awake? cell :awake-fighters)
        has-awake-carrier-fighter? (and (= (:type (:contents cell)) :carrier)
                                        (uc/has-awake? (:contents cell) :awake-fighters))]
    (when flight-path
      (cond
        has-awake-airport-fighter?
        (movement/launch-fighter-from-airport coords flight-path)

        has-awake-carrier-fighter?
        (movement/launch-fighter-from-carrier coords flight-path)

        :else nil))))

(defn- auto-disembark-army [coords cell]
  "Auto-disembarks an army from transport if marching-orders is set.
   Returns new coords if disembarked, nil otherwise."
  (let [contents (:contents cell)
        marching-orders (:marching-orders contents)
        has-awake-army? (and (= (:type contents) :transport)
                             (pos? (:awake-armies contents 0)))]
    (when (and marching-orders has-awake-army?)
      (let [[x y] coords
            adjacent-cells (for [dx [-1 0 1] dy [-1 0 1]
                                 :when (not (and (zero? dx) (zero? dy)))]
                             [(+ x dx) (+ y dy)])
            valid-target (first (filter (fn [target]
                                          (let [tcell (get-in @atoms/game-map target)]
                                            (and tcell
                                                 (= :land (:type tcell))
                                                 (not (:contents tcell)))))
                                        adjacent-cells))]
        (when valid-target
          (movement/disembark-army-with-target coords valid-target marching-orders))))))

(defn advance-game
  "Advances the game by processing the current item or starting new round."
  []
  (if (empty? @atoms/player-items)
    (start-new-round)
    (when-not @atoms/waiting-for-input
      (let [coords (first @atoms/player-items)
            cell (get-in @atoms/game-map coords)
            unit (:contents cell)
            ;; Satellites with targets are moved by move-satellites, skip them here
            satellite-with-target? (and (= (:type unit) :satellite) (:target unit))]
        (if satellite-with-target?
          (swap! atoms/player-items rest)
          (if-let [auto-coords (or (auto-launch-fighter coords cell)
                                   (auto-disembark-army coords cell))]
            (swap! atoms/player-items #(cons auto-coords (rest %)))
            (if (attention/item-needs-attention? coords)
              (do
                (reset! atoms/cells-needing-attention [coords])
                (attention/set-attention-message coords)
                (reset! atoms/waiting-for-input true))
              (let [new-coords (case (:mode unit)
                                 :explore (move-explore-unit coords)
                                 :moving (move-current-unit coords)
                                 nil)]
                (if new-coords
                  (swap! atoms/player-items #(cons new-coords (rest %)))
                  (swap! atoms/player-items rest))))))))))

(defn update-map
  "Updates the game map state."
  []
  (update-player-map)
  (update-computer-map)
  (advance-game))
