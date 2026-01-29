(ns empire.game-loop
  (:require [empire.atoms :as atoms]
            [empire.player.attention :as attention]
            [empire.computer :as computer]
            [empire.computer.production :as computer-production]
            [empire.config :as config]
            [empire.containers.ops :as container-ops]
            [empire.move-log :as move-log]
            [empire.movement.coastline :as coastline]
            [empire.movement.explore :as explore]
            [empire.movement.movement :as movement]
            [empire.movement.visibility :as visibility]
            [empire.movement.wake-conditions :as wake]
            [empire.pathfinding :as pathfinding]
            [empire.player.production :as production]
            [empire.movement.satellite :as satellite]
            [empire.containers.helpers :as uc]))

(defn update-player-map
  "Reveals cells near player-owned units on the visible map."
  []
  (visibility/update-combatant-map atoms/player-map :player))

(defn update-computer-map
  "Updates the computer's visible map by revealing cells near computer-owned units."
  []
  (visibility/update-combatant-map atoms/computer-map :computer))

(defn remove-dead-units
  "Removes units with hits at or below zero."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                contents (:contents cell)]
          :when (and contents (<= (:hits contents 1) 0))]
    (swap! atoms/game-map assoc-in [i j] (dissoc cell :contents))
    (visibility/update-cell-visibility [i j] (:owner contents))))

(defn build-player-items
  "Builds list of player city/unit coordinates to process this round."
  []
  (for [i (range (count @atoms/game-map))
        j (range (count (first @atoms/game-map)))
        :let [cell (get-in @atoms/game-map [i j])]
        :when (or (= (:city-status cell) :player)
                  (= (:owner (:contents cell)) :player))]
    [i j]))

(defn build-computer-items
  "Builds list of computer city/unit coordinates to process this round."
  []
  (for [i (range (count @atoms/game-map))
        j (range (count (first @atoms/game-map)))
        :let [cell (get-in @atoms/game-map [i j])]
        :when (or (= (:city-status cell) :computer)
                  (= (:owner (:contents cell)) :computer))]
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

           ;; Combat - attacker is at pos if won, nil if lost
           :combat
           (let [moved-cell (get-in @atoms/game-map pos)
                 moved-unit (:contents moved-cell)]
             (when (and moved-unit (= (:owner moved-unit) (:owner unit)))
               ;; Attacker won - they're now at pos
               (swap! atoms/game-map assoc-in (conj pos :contents :steps-remaining) 0)
               nil))  ;; Combat ends the move

           ;; Woke up - done moving
           :woke nil

           ;; Docked for repair - done moving
           :docked nil))))))

(defn reset-steps-remaining
  "Resets steps-remaining for all player units at start of round."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                unit (:contents cell)]
          :when (and unit (= (:owner unit) :player))]
    (let [steps (or (config/unit-speed (:type unit)) 1)]
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
                           (wake/friendly-city-in-range? pos new-fuel atoms/game-map))]
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

(defn wake-sentries-seeing-enemy
  "Wakes player sentry units that can see an enemy unit."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :player (:owner unit))
                     (= :sentry (:mode unit))
                     (wake/enemy-unit-visible? unit [i j] atoms/game-map))]
    (swap! atoms/game-map update-in [i j :contents]
           #(assoc % :mode :awake :reason :enemy-spotted))))

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
            (visibility/update-cell-visibility coords (:owner satellite))
            nil)

        ;; No more steps this round - decrement turns-remaining once per round
        (zero? steps-left)
        (let [new-turns (dec (:turns-remaining satellite 1))]
          (if (<= new-turns 0)
            (do (swap! atoms/game-map update-in coords dissoc :contents)
                (visibility/update-cell-visibility coords (:owner satellite))
                nil)
            (do (swap! atoms/game-map assoc-in (conj coords :contents :turns-remaining) new-turns)
                coords)))

        ;; Move one step
        :else
        (let [new-coords (satellite/move-satellite coords)]
          (recur new-coords (dec steps-left)))))))

(defn move-satellites
  "Moves all satellites according to their speed.
   Removes satellites with turns-remaining at or below zero."
  []
  (doseq [coords (find-satellite-coords)]
    (move-satellite-steps coords)))

(defn- repair-city-ships
  "Repairs all ships in a city's shipyard by 1 hit each.
   Launches fully repaired ships if the city cell is empty.
   Returns indices of ships that were launched (in reverse order for safe removal)."
  [city-coords]
  (let [cell (get-in @atoms/game-map city-coords)
        shipyard (uc/get-shipyard-ships cell)]
    (when (seq shipyard)
      ;; First, repair all ships
      (let [repaired-ships (mapv uc/repair-ship shipyard)]
        (swap! atoms/game-map assoc-in (conj city-coords :shipyard) repaired-ships))
      ;; Then, launch fully repaired ships if city is empty
      ;; Process from end to avoid index shifting issues
      (let [updated-cell (get-in @atoms/game-map city-coords)
            updated-shipyard (uc/get-shipyard-ships updated-cell)]
        (doseq [i (reverse (range (count updated-shipyard)))]
          (let [current-cell (get-in @atoms/game-map city-coords)
                ship (get-in current-cell [:shipyard i])]
            (when (and (uc/ship-fully-repaired? ship)
                       (nil? (:contents current-cell)))
              (container-ops/launch-ship-from-shipyard city-coords i))))))))

(defn repair-damaged-ships
  "Repairs ships in all friendly city shipyards by 1 hit per round.
   Launches fully repaired ships onto the map if the city cell is empty."
  []
  (doseq [i (range (count @atoms/game-map))
          j (range (count (first @atoms/game-map)))
          :let [cell (get-in @atoms/game-map [i j])]
          :when (and (= (:type cell) :city)
                     (#{:player :computer} (:city-status cell))
                     (seq (uc/get-shipyard-ships cell)))]
    (repair-city-ships [i j])))

(defn start-new-round
  "Starts a new round by building player and computer items lists and updating game state."
  []
  (swap! atoms/round-number inc)
  (move-log/log-round! @atoms/round-number)
  (pathfinding/clear-path-cache)
  (move-satellites)
  (consume-sentry-fighter-fuel)
  (wake-sentries-seeing-enemy)
  (remove-dead-units)
  (production/update-production)
  (repair-damaged-ships)
  (reset-steps-remaining)
  (wake-airport-fighters)
  ;; Carrier fighters stay asleep until 'u' is pressed - do not auto-wake at round start
  (reset! atoms/claimed-objectives #{})
  (reset! atoms/claimed-transport-targets #{})
  (reset! atoms/player-items (vec (build-player-items)))
  (reset! atoms/computer-items (vec (build-computer-items)))
  (reset! atoms/waiting-for-input false)
  (reset! atoms/message "")
  (reset! atoms/cells-needing-attention []))

(defn move-explore-unit
  "Moves an exploring unit. Returns new coords if still exploring, nil if done."
  [coords]
  (explore/move-explore-unit coords))

(defn move-coastline-unit
  "Moves a coastline-following unit. Returns nil when done."
  [coords]
  (coastline/move-coastline-unit coords))

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
        (container-ops/launch-fighter-from-airport coords flight-path)

        has-awake-carrier-fighter?
        (container-ops/launch-fighter-from-carrier coords flight-path)

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
          (container-ops/disembark-army-with-target coords valid-target marching-orders))))))

(defn- process-one-item
  "Processes a single player item. Returns :done if item was processed and removed,
   :continue if item needs more processing (e.g., movement), or :waiting if item needs user input."
  []
  (let [coords (first @atoms/player-items)
        cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        satellite-with-target? (and (= (:type unit) :satellite) (:target unit))]
    (if satellite-with-target?
      (do (swap! atoms/player-items rest) :done)
      (if-let [auto-coords (or (auto-launch-fighter coords cell)
                               (auto-disembark-army coords cell))]
        (do (swap! atoms/player-items #(cons auto-coords (rest %))) :continue)
        (if (attention/item-needs-attention? coords)
          (do
            (reset! atoms/cells-needing-attention [coords])
            (attention/set-attention-message coords)
            (reset! atoms/waiting-for-input true)
            :waiting)
          (let [new-coords (case (:mode unit)
                             :explore (move-explore-unit coords)
                             :coastline-follow (move-coastline-unit coords)
                             :moving (move-current-unit coords)
                             nil)]
            (if new-coords
              (do (swap! atoms/player-items #(cons new-coords (rest %))) :continue)
              (do (swap! atoms/player-items rest) :done))))))))

(defn- process-one-computer-item
  "Processes a single computer item. Returns :done when item processed."
  []
  (let [coords (first @atoms/computer-items)
        cell (get-in @atoms/game-map coords)
        is-computer-city? (and (= (:type cell) :city) (= (:city-status cell) :computer))
        has-computer-unit? (= (:owner (:contents cell)) :computer)]
    ;; Handle city production if this is a computer city
    (when is-computer-city?
      (computer-production/process-computer-city coords))
    ;; Process unit movement if there's a computer unit here
    (if has-computer-unit?
      (let [new-coords (computer/process-computer-unit coords)]
        (if new-coords
          (do (swap! atoms/computer-items #(cons new-coords (rest %))) :continue)
          (do (swap! atoms/computer-items rest) :done)))
      ;; No unit, just city processing done
      (do (swap! atoms/computer-items rest) :done))))

(defn- process-computer-items
  "Processes computer items until done or safety limit reached."
  []
  (loop [processed 0]
    (cond
      (empty? @atoms/computer-items) nil
      (>= processed 100) nil
      :else
      (let [result (process-one-computer-item)]
        (case result
          :continue (recur (inc processed))
          :done (recur (inc processed)))))))

;; Processes player items in a batch until one of three conditions:
;; 1. player-items list becomes empty
;; 2. waiting-for-input is set (unit needs player attention)
;; 3. 100 items processed (batch limit to keep UI responsive)
(defn- process-player-items-batch []
  (loop [processed 0]
    (cond
      (empty? @atoms/player-items) nil
      @atoms/waiting-for-input nil
      (>= processed 100) nil
      :else
      (let [result (process-one-item)]
        (case result
          :waiting nil
          :continue (recur (inc processed))
          :done (recur (inc processed)))))))

(defn advance-game
  "Advances the game by processing player items, then computer items.
   Processes multiple non-attention items per frame for faster rounds."
  []
  (cond
    @atoms/paused nil

    ;; Both lists empty - start new round (or pause)
    (and (empty? @atoms/player-items) (empty? @atoms/computer-items))
    (if @atoms/pause-requested
      (do (reset! atoms/paused true) (reset! atoms/pause-requested false))
      (start-new-round))

    ;; Waiting for player input
    @atoms/waiting-for-input nil

    ;; Player items to process
    (seq @atoms/player-items)
    (process-player-items-batch)

    ;; Computer items to process
    :else
    (process-computer-items)))

(defn toggle-pause
  "Toggles pause state. If running, requests pause at end of round.
   If paused, resumes immediately."
  []
  (if @atoms/paused
    (do
      (reset! atoms/paused false)
      (reset! atoms/pause-requested false))
    (reset! atoms/pause-requested true)))

(defn step-one-round
  "When paused, advances one round then pauses again."
  []
  (when @atoms/paused
    (reset! atoms/paused false)
    (reset! atoms/pause-requested true)
    (when (and (empty? @atoms/player-items) (empty? @atoms/computer-items))
      (start-new-round))))

(defn update-map
  "Updates the game map state."
  []
  (update-player-map)
  (update-computer-map)
  (advance-game))
