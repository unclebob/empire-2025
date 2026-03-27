(ns empire.game.loop.core
  "Facade: re-exports from round-start, advance, round-setup, and item-processing."
  (:require [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.game.loop.round-start :as round-start]
            [empire.game.loop.advance :as advance]
            [empire.game.loop.control-decisions :as decisions]
            [empire.game.loop.round-setup :as round-setup]
            [empire.game.loop.item-processing :as item-processing]))

;; --- round-start delegates ---
(def build-player-items round-start/build-player-items)
(def build-computer-items round-start/build-computer-items)
(def declare-game-over! round-start/declare-game-over!)
(def handicap-active? round-start/handicap-active?)
(def current-player-items round-start/current-player-items)
(def update-handicap-before-round! round-start/update-handicap-before-round!)
(def start-new-round round-start/start-new-round)

;; --- advance delegates ---
(def update-player-map advance/update-player-map)
(def update-computer-map advance/update-computer-map)
(def advance-game advance/advance-game)

(defn advance-game-batch
  "Calls advance-game up to advances-per-frame times per frame.
   Stops early when paused, waiting for input, or no items to process.
   Defined here (not delegated) so with-redefs on advance-game works."
  []
  (loop [remaining config/advances-per-frame]
    (when (pos? remaining)
      (advance-game)
      (when (decisions/continue-batch? remaining
                                       (sa/read-state :paused)
                                       (sa/read-state :waiting-for-input)
                                       (sa/read-state :player-items)
                                       (sa/read-state :computer-items))
        (recur (dec remaining))))))

(def toggle-pause advance/toggle-pause)
(def step-one-round advance/step-one-round)
(def update-map advance/update-map)

;; --- item-processed (stays here) ---
(defn item-processed
  "Called when user input has been processed for current item.
   Victory check happens in item-processing/process-player-items-batch."
  []
  (sa/write-state! :waiting-for-input false)
  (sa/write-state! :cells-needing-attention []))

;; --- round-setup delegates ---
(def remove-dead-units round-setup/remove-dead-units)
(def reset-steps-remaining round-setup/reset-steps-remaining)
(def wake-airport-fighters round-setup/wake-airport-fighters)
(def wake-carrier-fighters round-setup/wake-carrier-fighters)
(def consume-sentry-fighter-fuel round-setup/consume-sentry-fighter-fuel)
(def wake-sentries-seeing-enemy round-setup/wake-sentries-seeing-enemy)
(def move-satellites round-setup/move-satellites)
(def repair-damaged-ships round-setup/repair-damaged-ships)

;; --- item-processing delegates ---
(def move-current-unit item-processing/move-current-unit)
(def move-explore-unit item-processing/move-explore-unit)
(def move-coastline-unit item-processing/move-coastline-unit)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T11:53:15.479008-05:00", :module-hash "196598447", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 9, :hash "-1196570604"} {:id "def/build-player-items", :kind "def", :line 12, :end-line 12, :hash "-1327823416"} {:id "def/build-computer-items", :kind "def", :line 13, :end-line 13, :hash "1897995408"} {:id "def/declare-game-over!", :kind "def", :line 14, :end-line 14, :hash "-646802357"} {:id "def/handicap-active?", :kind "def", :line 15, :end-line 15, :hash "-522068491"} {:id "def/current-player-items", :kind "def", :line 16, :end-line 16, :hash "-974144580"} {:id "def/update-handicap-before-round!", :kind "def", :line 17, :end-line 17, :hash "630254694"} {:id "def/start-new-round", :kind "def", :line 18, :end-line 18, :hash "1368387506"} {:id "def/update-player-map", :kind "def", :line 21, :end-line 21, :hash "1377071872"} {:id "def/update-computer-map", :kind "def", :line 22, :end-line 22, :hash "-390301336"} {:id "def/advance-game", :kind "def", :line 23, :end-line 23, :hash "-1389747581"} {:id "defn/advance-game-batch", :kind "defn", :line 25, :end-line 38, :hash "-1837720730"} {:id "def/toggle-pause", :kind "def", :line 40, :end-line 40, :hash "409295913"} {:id "def/step-one-round", :kind "def", :line 41, :end-line 41, :hash "844639668"} {:id "def/update-map", :kind "def", :line 42, :end-line 42, :hash "1345569363"} {:id "defn/item-processed", :kind "defn", :line 45, :end-line 50, :hash "-1477047071"} {:id "def/remove-dead-units", :kind "def", :line 53, :end-line 53, :hash "2048804574"} {:id "def/reset-steps-remaining", :kind "def", :line 54, :end-line 54, :hash "-2073169805"} {:id "def/wake-airport-fighters", :kind "def", :line 55, :end-line 55, :hash "879726790"} {:id "def/wake-carrier-fighters", :kind "def", :line 56, :end-line 56, :hash "840162374"} {:id "def/consume-sentry-fighter-fuel", :kind "def", :line 57, :end-line 57, :hash "-647149942"} {:id "def/wake-sentries-seeing-enemy", :kind "def", :line 58, :end-line 58, :hash "-1383325288"} {:id "def/move-satellites", :kind "def", :line 59, :end-line 59, :hash "493297701"} {:id "def/repair-damaged-ships", :kind "def", :line 60, :end-line 60, :hash "2109716449"} {:id "def/move-current-unit", :kind "def", :line 63, :end-line 63, :hash "122457069"} {:id "def/move-explore-unit", :kind "def", :line 64, :end-line 64, :hash "-1327293082"} {:id "def/move-coastline-unit", :kind "def", :line 65, :end-line 65, :hash "-314642053"}]}
;; clj-mutate-manifest-end
