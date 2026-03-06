;; mutation-tested: 2026-02-26
(ns empire.game.loop.item-processing
  "Player and computer item processing, movement execution with sidestep logic."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.api :as movement-api]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.config.core :as config]
            [empire.computer.coordinator :as computer]
            [empire.computer.production :as computer-production]
            [empire.computer.threat-response :as threat-response]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.player.attention :as player-attention]
            [empire.game-mechanics.movement.explore :as explore]
            [empire.game-mechanics.movement.coastline :as coastline]))

(defn- computer-has-items?
  "Returns true if computer has any cities or units on the map."
  []
  (let [game-map (sa/current-world)]
    (some (fn [col]
            (some (fn [cell]
                    (or (= (:city-status cell) :computer)
                        (= (:owner (:contents cell)) :computer)))
                  col))
          game-map)))

(defn- declare-victory!
  "Declares player victory, pauses game, and flushes remaining items."
  []
  (sa/write-state! :paused true)
  (sa/write-state! :error-message "****YOU WIN!*****")
  (sa/write-state! :error-until Long/MAX_VALUE)
  (sa/write-state! :map-to-display :actual-map)
  (sa/write-state! :player-items [])
  (sa/write-state! :computer-items []))

(defn check-player-victory!
  "Checks if player has won (no computer items remain) and declares victory if so."
  []
  (when (and (sa/read-state :game-over-check-enabled)
             (not (computer-has-items?)))
    (declare-victory!)))

(defn- advance-step
  "Decrements steps-remaining for unit at pos. Returns pos if steps remain, nil otherwise."
[pos]
  (let [moved-unit (:contents (get-in (sa/current-world) pos))]
    (when moved-unit
      (let [new-steps (dec (:steps-remaining moved-unit 1))]
        (sa/update-world! assoc-in (conj pos :contents :steps-remaining) new-steps)
        (when (pos? new-steps) pos)))))

(defn- end-combat-move
  "Zeroes steps for attacker at pos if they won (same owner). Combat always ends the move."
[pos owner]
  (let [moved-unit (:contents (get-in (sa/current-world) pos))]
    (when (and moved-unit (= (:owner moved-unit) owner))
      (sa/update-world! assoc-in (conj pos :contents :steps-remaining) 0))))

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
   (let [world (sa/current-world)
         cell (get-in world coords)
         unit (:contents cell)]
    (when (and (= (:mode unit) :moving)
               (pos? (:steps-remaining unit 1)))
      (let [{:keys [result pos]} (movement-api/move-unit coords (:target unit) cell :game-map)
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

(defn- airport-flight-path [cell]
  (or (:flight-path cell) (:flight-path (:contents cell))))

(defn- awake-airport-fighter? [cell]
  (and (not (:contents cell)) (uc/has-awake? cell :awake-fighters)))

(defn- awake-carrier-fighter? [cell]
  (let [contents (:contents cell)]
    (and (= :carrier (:type contents)) (uc/has-awake? contents :awake-fighters))))

(defn- auto-launch-fighter [coords cell]
  "Auto-launches a fighter from city airport or carrier if flight-path is set.
   Returns new coords if launched, nil otherwise."
  (when-let [flight-path (airport-flight-path cell)]
    (cond
      (awake-airport-fighter? cell)
      (container-ops/launch-fighter-from-airport coords flight-path)

      (awake-carrier-fighter? cell)
      (container-ops/launch-fighter-from-carrier coords flight-path))))

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
                                          (let [tcell (get-in (sa/current-world) target)]
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
      (do (sa/update-state! :player-items #(cons new-coords (rest %))) :continue)
      (do (sa/update-state! :player-items rest) :done))))

(defn- satellite-with-target? [unit]
  (and (= (:type unit) :satellite) (:target unit)))

(defn- try-auto-launch-or-disembark [coords cell]
  (or (auto-launch-fighter coords cell)
      (auto-disembark-army coords cell)))

(defn- process-one-item
  "Processes a single player item. Returns :done, :continue, or :waiting."
  []
  (let [coords (first (sa/read-state :player-items))
        cell (get-in (sa/current-world) coords)
        unit (:contents cell)
        sat-moving? (satellite-with-target? unit)
        unit-in-auto-mode? (#{:moving :explore :coastline-follow} (:mode unit))
        auto-coords (when-not sat-moving? (try-auto-launch-or-disembark coords cell))]
    (cond
      sat-moving?
      (do (sa/update-state! :player-items rest) :done)

      auto-coords
      (do (sa/update-state! :player-items #(cons auto-coords (rest %))) :continue)

      unit-in-auto-mode?
      (process-auto-movement coords unit)

      (player-attention/item-needs-attention? coords)
      (do (sa/write-state! :cells-needing-attention [coords])
          (player-attention/set-attention-message coords)
          (sa/write-state! :waiting-for-input true)
          :waiting)

      :else
      (process-auto-movement coords unit))))

(defn- dispatch-detections!
  "Drains queued visibility detections and dispatches to threat-response."
  []
  (doseq [{:keys [pos cell]} (visibility/drain-detections!)]
    (threat-response/handle-detection! pos cell)))

(defn- process-one-computer-item
  "Processes a single computer item. Returns :done when item processed."
  []
  (let [coords (first (sa/read-state :computer-items))
        cell (get-in (sa/current-world) coords)
        is-computer-city? (and (= (:type cell) :city) (= (:city-status cell) :computer))
        has-computer-unit? (= (:owner (:contents cell)) :computer)]
    ;; Handle city production if this is a computer city
    (when is-computer-city?
      (computer-production/process-computer-city coords))
    ;; Process unit movement if there's a computer unit here
    (if has-computer-unit?
      (let [new-coords (computer/process-computer-unit coords)]
        (dispatch-detections!)
        (if new-coords
          (do (sa/update-state! :computer-items #(cons new-coords (rest %))) :continue)
          (do (sa/update-state! :computer-items rest) :done)))
      ;; No unit, just city processing done
      (do (sa/update-state! :computer-items rest) :done))))

(defn process-computer-items
  "Processes computer items until done or safety limit reached."
  []
  (loop [processed 0]
    (when (and (seq (sa/read-state :computer-items)) (< processed 100))
      (process-one-computer-item)
      (recur (inc processed)))))

(defn- batch-should-stop? [processed]
  (or (sa/read-state :paused)
      (empty? (sa/read-state :player-items))
      (sa/read-state :waiting-for-input)
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
