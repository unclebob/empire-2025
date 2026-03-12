(ns empire.game.loop.round-setup
  "Round initialization: satellite moves, fuel consumption, sentry waking,
   dead unit removal, repair, step resets."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.game-mechanics.movement.satellite :as satellite]
            [empire.config.core :as config]
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
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                contents (:contents cell)]
            :when (dead-unit? contents)]
      (when (computer-carrier? contents)
        (sa/update-state! :computer-carrier-positions disj [i j]))
      (sa/update-world! assoc-in [i j] (dissoc cell :contents))
      (visibility/update-cell-visibility [i j] (:owner contents)))))

(defn reset-steps-remaining
  "Resets steps-remaining for all player units at start of round."
  []
  (let [world (sa/current-world)]
    (doseq [i (range (count world))
            j (range (count (first world)))
            :let [cell (get-in world [i j])
                unit (:contents cell)]
            :when (and unit (= (:owner unit) :player))]
      (let [steps (or (dispatcher/effective-speed (:type unit) (:hits unit)) 1)]
        (sa/update-world! assoc-in [i j :contents :steps-remaining] steps)))))

(defn move-satellites
  "Moves all satellites according to their speed.
   Removes satellites with turns-remaining at or below zero."
  []
  (satellites/move-satellites!
   {:current-world sa/current-world
    :update-game-map! sa/update-world!
    :update-visibility! visibility/update-cell-visibility
    :move-satellite satellite/move-satellite
    :satellite-speed (config/unit-speed :satellite)}))

;; Delegated to sub-modules
(def wake-airport-fighters waking/wake-airport-fighters)
(def wake-carrier-fighters waking/wake-carrier-fighters)
(def wake-sentries-seeing-enemy waking/wake-sentries-seeing-enemy)
(def consume-sentry-fighter-fuel fuel/consume-sentry-fighter-fuel)
(def evacuate-lake-patrol-boats lakes/evacuate-lake-patrol-boats)
(def mark-lake-locked-ships lakes/mark-lake-locked-ships)
(def repair-damaged-ships repair/repair-damaged-ships)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:00:08.094471-05:00", :module-hash "-1224834806", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 14, :hash "-865788478"} {:id "defn/dead-unit?", :kind "defn", :line 16, :end-line 17, :hash "774282137"} {:id "defn/computer-carrier?", :kind "defn", :line 19, :end-line 20, :hash "-931774369"} {:id "defn/remove-dead-units", :kind "defn", :line 22, :end-line 34, :hash "-1398936752"} {:id "defn/reset-steps-remaining", :kind "defn", :line 36, :end-line 46, :hash "-2013273020"} {:id "defn/move-satellites", :kind "defn", :line 48, :end-line 57, :hash "1312988440"} {:id "def/wake-airport-fighters", :kind "def", :line 60, :end-line 60, :hash "-1677494432"} {:id "def/wake-carrier-fighters", :kind "def", :line 61, :end-line 61, :hash "1788720167"} {:id "def/wake-sentries-seeing-enemy", :kind "def", :line 62, :end-line 62, :hash "1060641709"} {:id "def/consume-sentry-fighter-fuel", :kind "def", :line 63, :end-line 63, :hash "321479004"} {:id "def/evacuate-lake-patrol-boats", :kind "def", :line 64, :end-line 64, :hash "360128640"} {:id "def/mark-lake-locked-ships", :kind "def", :line 65, :end-line 65, :hash "-1083822323"} {:id "def/repair-damaged-ships", :kind "def", :line 66, :end-line 66, :hash "189964291"}]}
;; clj-mutate-manifest-end
