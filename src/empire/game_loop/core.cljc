(ns empire.game-loop.core
  "Core game loop functions.

   Contains the main advance-game function, pause controls, and unit movement
   result handlers."
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.game-loop.item-processing :as item-processing]
            [empire.game-loop.round-init :as round-init]
            [empire.game-loop.visibility :as visibility]
            [empire.movement.movement :as movement]))

(defn handle-sidestep-result
  "Handles sidestep movement result. Returns pos if steps remain, nil otherwise."
  [pos max-sidesteps]
  (let [moved-cell (get-in @atoms/game-map pos)
        moved-unit (:contents moved-cell)]
    (when moved-unit
      (let [new-steps (dec (:steps-remaining moved-unit 1))]
        (swap! atoms/game-map assoc-in (conj pos :contents :steps-remaining) new-steps)
        (when (> new-steps 0)
          (if (pos? max-sidesteps) pos pos))))))

(defn handle-normal-move-result
  "Handles normal movement result. Returns pos if steps remain, nil otherwise."
  [pos]
  (let [moved-cell (get-in @atoms/game-map pos)
        moved-unit (:contents moved-cell)]
    (when moved-unit
      (let [new-steps (dec (:steps-remaining moved-unit 1))]
        (swap! atoms/game-map assoc-in (conj pos :contents :steps-remaining) new-steps)
        (when (> new-steps 0)
          pos)))))

(defn handle-combat-result
  "Handles combat result. Sets steps to 0 if attacker won. Always returns nil."
  [pos original-owner]
  (let [moved-cell (get-in @atoms/game-map pos)
        moved-unit (:contents moved-cell)]
    (when (and moved-unit (= (:owner moved-unit) original-owner))
      (swap! atoms/game-map assoc-in (conj pos :contents :steps-remaining) 0))
    nil))

(defn move-current-unit
  "Moves the unit at coords one step. Returns new coords if still moving, nil if done.
   Sidesteps consume a step but continue moving if steps remain."
  ([coords] (move-current-unit coords 10))
  ([coords max-sidesteps]
   (let [cell (get-in @atoms/game-map coords)
         unit (:contents cell)]
     (when (= (:mode unit) :moving)
       (let [target (:target unit)
             {:keys [result pos]} (movement/move-unit coords target cell atoms/game-map)]
         (case result
           :sidestep (when-let [new-pos (handle-sidestep-result pos max-sidesteps)]
                       (if (pos? max-sidesteps)
                         (recur new-pos (dec max-sidesteps))
                         new-pos))
           :normal (handle-normal-move-result pos)
           :combat (handle-combat-result pos (:owner unit))
           :woke nil
           :docked nil))))))

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
      (round-init/start-new-round))))

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
      (round-init/start-new-round))

    ;; Waiting for player input
    @atoms/waiting-for-input nil

    ;; Player items to process
    (seq @atoms/player-items)
    (item-processing/process-player-items-batch move-current-unit)

    ;; Computer items to process
    :else
    (item-processing/process-computer-items)))

(defn update-map
  "Updates the game map state."
  []
  (visibility/update-player-map)
  (visibility/update-computer-map)
  (advance-game))
