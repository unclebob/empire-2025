;; mutation-tested: no
(ns empire.player.commands-actions
  "Extracted unit action handlers for player command processing."
  (:require [empire.game-mechanics.movement.explore :as explore]
            [empire.game-mechanics.movement.coastline :as coastline]
            [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.game-mechanics.movement.api :as movement-api]
            [empire.player.attention :as attention]
            [empire.game-mechanics.services.combat :as combat]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.player.commands-action-decisions :as decisions]
            [empire.config.units.dispatcher :as dispatcher]))

(defn- current-world [ctx]
  ((:current-world ctx)))

(defn- update-game-map! [ctx f & args]
  (apply (:update-game-map! ctx) f args))

(defn- read-runtime-state [ctx k]
  ((:read-runtime-state ctx) k))

(defn- write-runtime-state! [ctx k v]
  ((:write-runtime-state! ctx) k v))

(defn- update-runtime-state! [ctx k f & args]
  (apply (:update-runtime-state! ctx) k f args))

(defn- item-processed! [ctx]
  (write-runtime-state! ctx :waiting-for-input false)
  (write-runtime-state! ctx :cells-needing-attention []))

(defn handle-space-key [ctx coords]
  (let [cell (get-in (current-world ctx) coords)
        unit (:contents cell)
        decision (decisions/space-key-action unit)]
    (case (:action decision)
      :skip-and-destroy
      (do
        (update-game-map! ctx assoc-in (conj coords :contents :hits) 0)
        (update-game-map! ctx assoc-in (conj coords :contents :reason) :skipping-this-round))

      :skip-and-burn-fuel
      (do
        (update-game-map! ctx assoc-in (conj coords :contents :fuel) (:fuel decision))
        (update-game-map! ctx assoc-in (conj coords :contents :reason) (:reason decision)))

      :skip
      (update-game-map! ctx assoc-in (conj coords :contents :reason) :skipping-this-round)

      nil))
  (update-runtime-state! ctx :player-items rest)
  (item-processed! ctx)
  true)

(defn handle-unload-key [ctx coords cell]
  (case (:action (decisions/unload-key-action (:contents cell)))
      :wake-armies-on-transport
      (do (container-ops/wake-armies-on-transport coords)
          (item-processed! ctx)
          true)

      :wake-fighters-on-carrier
      (do (container-ops/wake-fighters-on-carrier coords)
          (item-processed! ctx)
          true)

      nil))

(defn handle-sentry-key [ctx coords cell active-unit]
  (case (:action (decisions/sentry-key-action cell active-unit))
      :sleep-armies-on-transport
      (do (container-ops/sleep-armies-on-transport coords)
          (item-processed! ctx)
          true)

      :sleep-fighters-on-carrier
      (do (container-ops/sleep-fighters-on-carrier coords)
          (item-processed! ctx)
          true)

      :set-sentry-mode
      (do (movement-state/set-unit-mode coords :sentry)
          (item-processed! ctx)
          true)

      nil))

(defn handle-look-around-key [ctx coords _cell active-unit]
  (let [decision (decisions/look-around-action (current-world ctx) coords active-unit)]
    (case (:action decision)
      :set-explore-mode
      (do (explore/set-explore-mode coords)
          (item-processed! ctx)
          true)

      :disembark-army-to-explore
      (do (container-ops/disembark-army-to-explore coords (:target decision))
          (item-processed! ctx)
          true)

      :no-op true

      :set-coastline-follow-mode
      (do (coastline/set-coastline-follow-mode coords)
          (item-processed! ctx)
          true)

      :reject
      (do (write-runtime-state! ctx :attention-message (:message decision))
          true)

      nil)))

(defn handle-unit-click
  "Handles interaction with an attention-needing unit."
  [ctx clicked-coords attention-coords]
  (let [attn-coords (first attention-coords)
        attn-cell (get-in (current-world ctx) attn-coords)
        active-unit (movement-state/get-active-unit attn-cell)
        context (movement-state/movement-context attn-cell active-unit)
        decision (decisions/click-action (current-world ctx) attn-coords clicked-coords context active-unit)]
    (case (:action decision)
      :launch-fighter-from-airport
      ((:launch-fighter-and-update ctx)
       container-ops/launch-fighter-from-airport
       attn-coords
       (:target decision))

      :disembark-army-from-transport
      (container-ops/disembark-army-from-transport attn-coords (:target decision))

      :coastal-army-attack
      (combat/apply-combat-result! (combat/attempt-coastal-army-attack (current-world ctx) attn-coords (:target decision)))

      :attempt-conquest
      (combat/apply-combat-result! (combat/attempt-conquest (current-world ctx) attn-coords (:target decision)))

      :attempt-fighter-overfly
      (combat/apply-combat-result! (combat/attempt-fighter-overfly (current-world ctx) attn-coords (:target decision)))

      :set-unit-movement
      (movement-api/set-unit-movement attn-coords (:target decision))

      nil)
    (item-processed! ctx)))

(defn handle-cell-click
  "Handles clicking on a map cell, prioritizing attention-needing items."
  [ctx cell-x cell-y]
  (let [attention-coords (read-runtime-state ctx :cells-needing-attention)
        clicked-coords [cell-x cell-y]]
    (when (attention/is-unit-needing-attention? attention-coords)
      (handle-unit-click ctx clicked-coords attention-coords))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:22:00.979709-05:00", :module-hash "756213941", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 12, :hash "873119974"} {:id "defn-/current-world", :kind "defn-", :line 14, :end-line 15, :hash "1933317035"} {:id "defn-/update-game-map!", :kind "defn-", :line 17, :end-line 18, :hash "416575166"} {:id "defn-/read-runtime-state", :kind "defn-", :line 20, :end-line 21, :hash "1884179192"} {:id "defn-/write-runtime-state!", :kind "defn-", :line 23, :end-line 24, :hash "-908984863"} {:id "defn-/update-runtime-state!", :kind "defn-", :line 26, :end-line 27, :hash "1255797596"} {:id "defn-/item-processed!", :kind "defn-", :line 29, :end-line 31, :hash "821174700"} {:id "defn/handle-space-key", :kind "defn", :line 33, :end-line 54, :hash "796398857"} {:id "defn/handle-unload-key", :kind "defn", :line 56, :end-line 68, :hash "668889817"} {:id "defn/handle-sentry-key", :kind "defn", :line 70, :end-line 87, :hash "2108290207"} {:id "defn/handle-look-around-key", :kind "defn", :line 89, :end-line 113, :hash "-1046653750"} {:id "defn/handle-unit-click", :kind "defn", :line 115, :end-line 146, :hash "114957027"} {:id "defn/handle-cell-click", :kind "defn", :line 148, :end-line 154, :hash "-1491182524"}]}
;; clj-mutate-manifest-end
