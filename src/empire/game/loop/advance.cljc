(ns empire.game.loop.advance
  "Game advancement: advance-game, batch processing, pause/step, map updates."
  (:require [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.game.loop.item-processing :as item-processing]
            [empire.game.loop.control-decisions :as decisions]
            [empire.game.loop.round-start :as round-start]))

(defn- update-visible-map
  [map-key owner]
  (when-let [updated (visibility/update-combatant-map-state
                      (sa/read-state map-key)
                      owner
                      (sa/current-world))]
    (sa/write-state! map-key updated)))

(defn update-player-map
  "Reveals cells near player-owned units on the visible map."
  []
  (update-visible-map :player-map :player))

(defn update-computer-map
  "Updates the computer's visible map by revealing cells near computer-owned units."
  []
  (update-visible-map :computer-map :computer))

(defn- both-lists-empty? []
  (and (empty? (sa/read-state :player-items))
       (empty? (sa/read-state :computer-items))))

(defn- process-player-action!
  []
  (item-processing/process-player-items-batch))

(defn- start-new-round-action!
  []
  (when (pos? (sa/read-state :round-number))
    (round-start/update-handicap-before-round!))
  (round-start/start-new-round))

(defn- apply-advance-game-action!
  [action]
  (case action
    :pause (do
             (sa/write-state! :paused true)
             (sa/write-state! :pause-requested false))
    :new-round (start-new-round-action!)
    :process-player (process-player-action!)
    :process-computer (item-processing/process-computer-items)
    nil))

(defn advance-game
  "Advances the game by processing player items, then computer items.
   Processes multiple non-attention items per frame for faster rounds."
  []
  (apply-advance-game-action!
   (decisions/advance-game-action {:load-menu-open (sa/read-state :load-menu-open)
                                   :save-menu-open (sa/read-state :save-menu-open)
                                   :paused (sa/read-state :paused)
                                   :both-lists-empty? (both-lists-empty?)
                                   :pause-requested (sa/read-state :pause-requested)
                                   :waiting-for-input (sa/read-state :waiting-for-input)
                                   :player-items (sa/read-state :player-items)})))

(defn run-advance-game-batch
  [advance-game-fn]
  (loop [remaining config/advances-per-frame]
    (when (pos? remaining)
      (advance-game-fn)
      (when (decisions/continue-batch? remaining
                                       (sa/read-state :paused)
                                       (sa/read-state :waiting-for-input)
                                       (sa/read-state :player-items)
                                       (sa/read-state :computer-items))
        (recur (dec remaining))))))

(defn advance-game-batch
  "Calls advance-game up to advances-per-frame times per frame.
   Stops early when paused, waiting for input, or no items to process."
  []
  (run-advance-game-batch advance-game))

(defn toggle-pause
  "Toggles pause state. If running, requests pause at end of round.
   If paused, resumes immediately."
  []
  (case (decisions/toggle-pause-action (sa/read-state :paused))
    :resume (do
      (sa/write-state! :paused false)
      (sa/write-state! :pause-requested false))
    :request-pause (sa/write-state! :pause-requested true)
    nil))

(defn step-one-round
  "When paused, advances one round then pauses again."
  []
  (case (decisions/step-one-round-action {:paused? (sa/read-state :paused)
                                          :player-items (sa/read-state :player-items)
                                          :computer-items (sa/read-state :computer-items)})
    :start-round (do
                   (sa/write-state! :paused false)
                   (sa/write-state! :pause-requested true)
                   (round-start/start-new-round))
    :resume-one-round (do
                        (sa/write-state! :paused false)
                        (sa/write-state! :pause-requested true))
    nil))

(defn update-map
  "Updates the game map state."
  []
  (update-player-map)
  (update-computer-map)
  (advance-game))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:08:06.696421-05:00", :module-hash "-356205128", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "1161309082"} {:id "defn-/update-visible-map", :kind "defn-", :line 10, :end-line nil, :hash "7243118"} {:id "defn/update-player-map", :kind "defn", :line 18, :end-line nil, :hash "1746844627"} {:id "defn/update-computer-map", :kind "defn", :line 23, :end-line nil, :hash "-132838406"} {:id "defn-/both-lists-empty?", :kind "defn-", :line 28, :end-line nil, :hash "-2026920266"} {:id "defn-/process-player-action!", :kind "defn-", :line 32, :end-line nil, :hash "-1677333192"} {:id "defn-/start-new-round-action!", :kind "defn-", :line 36, :end-line nil, :hash "963162342"} {:id "defn-/apply-advance-game-action!", :kind "defn-", :line 42, :end-line nil, :hash "801146010"} {:id "defn/advance-game", :kind "defn", :line 53, :end-line nil, :hash "176007598"} {:id "defn/run-advance-game-batch", :kind "defn", :line 66, :end-line nil, :hash "1383607476"} {:id "defn/advance-game-batch", :kind "defn", :line 78, :end-line nil, :hash "-2125821598"} {:id "defn/toggle-pause", :kind "defn", :line 84, :end-line nil, :hash "-551704113"} {:id "defn/step-one-round", :kind "defn", :line 95, :end-line nil, :hash "-1928460524"} {:id "defn/update-map", :kind "defn", :line 110, :end-line nil, :hash "-297625781"}]}
;; clj-mutate-manifest-end
