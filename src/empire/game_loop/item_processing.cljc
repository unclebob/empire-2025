;; mutation-tested: 2026-02-26
(ns empire.game-loop.item-processing
  "Player and computer item processing, movement execution with sidestep logic."
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.player.attention :as attention]
            [empire.computer :as computer]
            [empire.computer.production :as computer-production]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.movement.coastline :as coastline]
            [empire.movement.explore :as explore]
            [empire.movement.movement :as movement]))

(defn- computer-has-items?
  "Returns true if computer has any cities or units on the map."
  []
  (let [game-map @atoms/game-map]
    (some (fn [col]
            (some (fn [cell]
                    (or (= (:city-status cell) :computer)
                        (= (:owner (:contents cell)) :computer)))
                  col))
          game-map)))

(defn- declare-victory!
  "Declares player victory, pauses game, and flushes remaining items."
  []
  (reset! atoms/paused true)
  (reset! atoms/error-message "****YOU WIN!*****")
  (reset! atoms/error-until Long/MAX_VALUE)
  (reset! atoms/map-to-display :actual-map)
  (reset! atoms/player-items [])
  (reset! atoms/computer-items []))

(defn check-player-victory!
  "Checks if player has won (no computer items remain) and declares victory if so."
  []
  (when (and @atoms/game-over-check-enabled
             (not (computer-has-items?)))
    (declare-victory!)))

(defn- advance-step
  "Decrements steps-remaining for unit at pos. Returns pos if steps remain, nil otherwise."
  [pos]
  (let [moved-unit (:contents (get-in @atoms/game-map pos))]
    (when moved-unit
      (let [new-steps (dec (:steps-remaining moved-unit 1))]
        (swap! atoms/game-map assoc-in (conj pos :contents :steps-remaining) new-steps)
        (when (pos? new-steps) pos)))))

(defn- end-combat-move
  "Zeroes steps for attacker at pos if they won (same owner). Combat always ends the move."
  [pos owner]
  (let [moved-unit (:contents (get-in @atoms/game-map pos))]
    (when (and moved-unit (= (:owner moved-unit) owner))
      (swap! atoms/game-map assoc-in (conj pos :contents :steps-remaining) 0))))

(defn- resolve-move-result
  "Resolves a move result into the next position. Returns pos if unit should continue, nil if done."
  [result pos owner]
  (case result
    (:sidestep :normal) (advance-step pos)
    :combat (do (end-combat-move pos owner) nil)
    :woke pos
    :docked nil))

(defn move-current-unit
  "Moves the unit at coords one step. Returns new coords if still moving, nil if done."
  ([coords] (move-current-unit coords config/max-sidesteps))
  ([coords max-sidesteps]
   (let [cell (get-in @atoms/game-map coords)
         unit (:contents cell)]
     (when (and (= (:mode unit) :moving)
                (pos? (:steps-remaining unit 1)))
       (let [{:keys [result pos]} (movement/move-unit coords (:target unit) cell atoms/game-map)
             next-pos (resolve-move-result result pos (:owner unit))]
         (if (and (= result :sidestep) next-pos (pos? max-sidesteps))
           (recur pos (dec max-sidesteps))
           next-pos))))))

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
        cell-occupied? (:contents cell)
        has-awake-airport-fighter? (and (not cell-occupied?)
                                        (uc/has-awake? cell :awake-fighters))
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

(defn- process-auto-movement [coords unit]
  (let [new-coords (case (:mode unit)
                     :explore (move-explore-unit coords)
                     :coastline-follow (move-coastline-unit coords)
                     :moving (move-current-unit coords)
                     nil)]
    (if new-coords
      (do (swap! atoms/player-items #(cons new-coords (rest %))) :continue)
      (do (swap! atoms/player-items rest) :done))))

(defn- satellite-with-target? [unit]
  (and (= (:type unit) :satellite) (:target unit)))

(defn- process-one-item
  "Processes a single player item. Returns :done if item was processed and removed,
   :continue if item needs more processing (e.g., movement), or :waiting if item needs user input."
  []
  (let [coords (first @atoms/player-items)
        cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        sat-moving? (satellite-with-target? unit)
        unit-in-auto-mode? (#{:moving :explore :coastline-follow} (:mode unit))
        auto-coords (when-not sat-moving?
                      (or (auto-launch-fighter coords cell)
                          (auto-disembark-army coords cell)))]
    (cond
      sat-moving?
      (do (swap! atoms/player-items rest) :done)

      auto-coords
      (do (swap! atoms/player-items #(cons auto-coords (rest %))) :continue)

      ;; If unit is actively moving, let it move before checking attention
      unit-in-auto-mode?
      (process-auto-movement coords unit)

      (attention/item-needs-attention? coords)
      (do (reset! atoms/cells-needing-attention [coords])
          (attention/set-attention-message coords)
          (reset! atoms/waiting-for-input true)
          :waiting)

      :else
      (process-auto-movement coords unit))))

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

(defn process-computer-items
  "Processes computer items until done or safety limit reached."
  []
  (loop [processed 0]
    (when (and (seq @atoms/computer-items) (< processed 100))
      (process-one-computer-item)
      (recur (inc processed)))))

;; Processes player items in a batch until one of three conditions:
;; 1. player-items list becomes empty
;; 2. waiting-for-input is set (unit needs player attention)
;; 3. 100 items processed (batch limit to keep UI responsive)
;; 4. Player wins (all computer items eliminated)
(defn- batch-should-stop? [processed]
  (or @atoms/paused
      (empty? @atoms/player-items)
      @atoms/waiting-for-input
      (>= processed 100)))

(defn process-player-items-batch []
  (loop [processed 0]
    (when-not (batch-should-stop? processed)
      (let [result (process-one-item)]
        (check-player-victory!)
        (case result
          :waiting nil
          :continue (recur (inc processed))
          :done (recur (inc processed)))))))
