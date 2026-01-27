(ns empire.game-loop.round-init
  "Round initialization and cleanup functions.

   Handles starting new rounds, building item lists, resetting unit steps,
   and removing dead units."
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.debug :as debug]
            [empire.game-loop.satellites :as satellites]
            [empire.game-loop.sentry :as sentry]
            [empire.game-loop.shipyard :as shipyard]
            [empire.movement.visibility :as visibility]
            [empire.pathfinding :as pathfinding]
            [empire.player.production :as production]))

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

(defn item-processed
  "Called when user input has been processed for current item."
  []
  (reset! atoms/waiting-for-input false)
  (reset! atoms/message "")
  (reset! atoms/cells-needing-attention []))

(defn start-new-round
  "Starts a new round by building player and computer items lists and updating game state."
  []
  (swap! atoms/round-number inc)
  (debug/log-action! [:round-start @atoms/round-number])
  (pathfinding/clear-path-cache)
  (satellites/move-satellites)
  (sentry/consume-sentry-fighter-fuel)
  (sentry/wake-sentries-seeing-enemy)
  (remove-dead-units)
  (production/update-production)
  (shipyard/repair-damaged-ships)
  (reset-steps-remaining)
  (sentry/wake-airport-fighters)
  ;; Carrier fighters stay asleep until 'u' is pressed - do not auto-wake at round start
  (reset! atoms/player-items (vec (build-player-items)))
  (reset! atoms/computer-items (vec (build-computer-items)))
  (reset! atoms/waiting-for-input false)
  (reset! atoms/message "")
  (reset! atoms/cells-needing-attention []))
