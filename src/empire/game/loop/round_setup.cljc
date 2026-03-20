(ns empire.game.loop.round-setup
  "Round initialization: satellite moves, fuel consumption, sentry waking,
   dead unit removal, repair, step resets."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.visibility :as visibility]
            [empire.game-mechanics.movement.satellite :as satellite]
            [empire.config.core :as config]
            [empire.game.loop.round-setup-decisions :as decisions]
            [empire.game-mechanics.services.round-setup :as domain-round-setup]
            [empire.game.loop.round-setup.fuel :as fuel]
            [empire.game.loop.round-setup.lakes :as lakes]
            [empire.game.loop.round-setup.repair :as repair]
            [empire.game.loop.round-setup.satellites :as satellites]
            [empire.game.loop.round-setup.waking :as waking]
            [empire.config.units.dispatcher :as dispatcher]))

(defn dead-unit? [contents]
  (domain-round-setup/dead-unit? contents))

(defn computer-carrier? [contents]
  (domain-round-setup/computer-carrier? contents))

(defn remove-dead-units
  "Removes units with hits at or below zero."
  []
  (let [world (sa/current-world)]
    (doseq [{:keys [pos owner computer-carrier? cell-without-contents]}
            (decisions/dead-unit-effects world dead-unit? computer-carrier?)]
      (when computer-carrier?
        (sa/update-state! :computer-carrier-positions disj pos))
      (sa/update-world! assoc-in pos cell-without-contents)
      (visibility/update-cell-visibility pos owner))))

(defn reset-steps-remaining
  "Resets steps-remaining for all player units at start of round."
  []
  (let [world (sa/current-world)]
    (doseq [{:keys [pos steps]}
            (decisions/step-reset-effects world dispatcher/effective-speed)]
      (sa/update-world! assoc-in (conj pos :contents :steps-remaining) steps))))

(defn move-satellites
  "Moves all satellites according to their speed.
   Removes satellites with turns-remaining at or below zero."
  []
  (satellites/move-satellites!
   (decisions/move-satellites-plan
    {:current-world sa/current-world
     :update-game-map! sa/update-world!
     :update-visibility! visibility/update-cell-visibility
     :move-satellite satellite/move-satellite
     :satellite-speed (config/unit-speed :satellite)})))

;; Delegated to sub-modules
(def wake-airport-fighters waking/wake-airport-fighters)
(def wake-carrier-fighters waking/wake-carrier-fighters)
(def wake-sentries-seeing-enemy waking/wake-sentries-seeing-enemy)
(def consume-sentry-fighter-fuel fuel/consume-sentry-fighter-fuel)
(def evacuate-lake-patrol-boats lakes/evacuate-lake-patrol-boats)
(def mark-lake-locked-ships lakes/mark-lake-locked-ships)
(def repair-damaged-ships repair/repair-damaged-ships)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T14:44:58.794707-05:00", :module-hash "1404510266", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 15, :hash "-318318723"} {:id "defn/dead-unit?", :kind "defn", :line 17, :end-line 18, :hash "774282137"} {:id "defn/computer-carrier?", :kind "defn", :line 20, :end-line 21, :hash "-931774369"} {:id "defn/remove-dead-units", :kind "defn", :line 23, :end-line 32, :hash "1315903249"} {:id "defn/reset-steps-remaining", :kind "defn", :line 34, :end-line 40, :hash "-816358328"} {:id "defn/move-satellites", :kind "defn", :line 42, :end-line 52, :hash "97305368"} {:id "def/wake-airport-fighters", :kind "def", :line 55, :end-line 55, :hash "-1677494432"} {:id "def/wake-carrier-fighters", :kind "def", :line 56, :end-line 56, :hash "1788720167"} {:id "def/wake-sentries-seeing-enemy", :kind "def", :line 57, :end-line 57, :hash "1060641709"} {:id "def/consume-sentry-fighter-fuel", :kind "def", :line 58, :end-line 58, :hash "321479004"} {:id "def/evacuate-lake-patrol-boats", :kind "def", :line 59, :end-line 59, :hash "360128640"} {:id "def/mark-lake-locked-ships", :kind "def", :line 60, :end-line 60, :hash "-1083822323"} {:id "def/repair-damaged-ships", :kind "def", :line 61, :end-line 61, :hash "189964291"}]}
;; clj-mutate-manifest-end
