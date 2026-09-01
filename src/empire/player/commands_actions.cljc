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
            [empire.config.units.dispatcher :as dispatcher]
            [empire.notifications :as notifications]))

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

(def ^:private unload-handlers
  {:wake-armies-on-transport container-ops/wake-armies-on-transport
   :wake-fighters-on-carrier container-ops/wake-fighters-on-carrier
   :wake-fighters-on-airport container-ops/wake-fighters-on-airport})

(defn- run-unload-action!
  [coords action]
  ((unload-handlers action) coords))

(defn handle-unload-key [ctx coords cell active-unit]
  (when-let [action (:action (decisions/unload-key-action (:contents cell) cell active-unit))]
    (run-unload-action! coords action)
    (item-processed! ctx)
    true))

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

      :sleep-fighters-on-airport
      (do (container-ops/sleep-fighters-on-airport coords)
          (update-runtime-state! ctx :player-items rest)
          (item-processed! ctx)
          true)

      :set-sentry-mode
      (do (movement-state/set-unit-mode coords :sentry)
          (item-processed! ctx)
          true)

      nil))

(defn- look-around-processed!
  [ctx apply-fn]
  (apply-fn)
  (item-processed! ctx)
  true)

(defn- apply-look-around-mode
  [ctx coords decision]
  (case (:action decision)
    :set-explore-mode
    (look-around-processed! ctx #(explore/set-explore-mode coords))

    :set-coastline-follow-mode
    (look-around-processed! ctx #(coastline/set-coastline-follow-mode coords))

    nil))

(defn- apply-look-around-decision
  [ctx coords decision]
  (or (apply-look-around-mode ctx coords decision)
      (case (:action decision)
        :disembark-army-to-explore
        (look-around-processed! ctx #(container-ops/disembark-army-to-explore coords (:target decision)))

        :no-op true

        :reject
        (do (write-runtime-state! ctx :warning-message (:message decision))
            (notifications/alert!)
            true)

        nil)))

(defn handle-look-around-key [ctx coords _cell active-unit]
  (apply-look-around-decision
   ctx coords (decisions/look-around-action (current-world ctx) coords active-unit)))

(defn- launch-airport-fighter!
  [ctx attn-coords decision]
  ((:launch-fighter-and-update ctx)
   container-ops/launch-fighter-from-airport
   attn-coords
   (:target decision))
  true)

(defn- disembark-from-transport!
  [ctx attn-coords decision]
  (container-ops/disembark-army-from-transport attn-coords (:target decision))
  (item-processed! ctx)
  true)

(defn- disembark-with-target!
  [ctx attn-coords decision]
  (container-ops/disembark-army-with-target attn-coords (:target decision) (:extended-target decision))
  (item-processed! ctx)
  true)

(defn- apply-combat-action!
  [ctx combat-action attn-coords decision]
  (combat/apply-combat-result!
   (combat-action (current-world ctx) attn-coords (:target decision)))
  (item-processed! ctx)
  true)

(defn- set-unit-movement!
  [ctx attn-coords decision]
  (movement-api/set-unit-movement attn-coords (:target decision))
  (item-processed! ctx)
  true)

(defn- reject-click!
  [ctx _attn-coords decision]
  (write-runtime-state! ctx :warning-message (:message decision))
  (notifications/alert!)
  true)

(def ^:private click-handlers
  {:launch-fighter-from-airport launch-airport-fighter!
   :disembark-army-from-transport disembark-from-transport!
   :disembark-army-with-target disembark-with-target!
   :coastal-army-attack #(apply-combat-action! %1 combat/attempt-coastal-army-attack %2 %3)
   :attempt-conquest #(apply-combat-action! %1 combat/attempt-conquest %2 %3)
   :attempt-fighter-overfly #(apply-combat-action! %1 combat/attempt-fighter-overfly %2 %3)
   :set-unit-movement set-unit-movement!
   :reject reject-click!})

(defn handle-unit-click
  "Handles interaction with an attention-needing unit."
  [ctx clicked-coords attention-coords]
  (let [attn-coords (first attention-coords)
        attn-cell (get-in (current-world ctx) attn-coords)
        active-unit (movement-state/get-active-unit attn-cell attn-coords)
        context (movement-state/movement-context attn-cell active-unit)
        decision (decisions/click-action (current-world ctx) attn-coords clicked-coords context active-unit)]
    (when-let [handler (click-handlers (:action decision))]
      (handler ctx attn-coords decision))))

(defn handle-cell-click
  "Handles clicking on a map cell, prioritizing attention-needing items."
  [ctx cell-x cell-y]
  (let [attention-coords (read-runtime-state ctx :cells-needing-attention)
        clicked-coords [cell-x cell-y]]
    (when (attention/is-unit-needing-attention? attention-coords)
      (handle-unit-click ctx clicked-coords attention-coords))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:03:56.648626-05:00", :module-hash "-1968188740", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line nil, :hash "1711414380"} {:id "defn-/current-world", :kind "defn-", :line 15, :end-line nil, :hash "1933317035"} {:id "defn-/update-game-map!", :kind "defn-", :line 18, :end-line nil, :hash "416575166"} {:id "defn-/read-runtime-state", :kind "defn-", :line 21, :end-line nil, :hash "1884179192"} {:id "defn-/write-runtime-state!", :kind "defn-", :line 24, :end-line nil, :hash "-908984863"} {:id "defn-/update-runtime-state!", :kind "defn-", :line 27, :end-line nil, :hash "1255797596"} {:id "defn-/item-processed!", :kind "defn-", :line 30, :end-line nil, :hash "821174700"} {:id "defn/handle-space-key", :kind "defn", :line 34, :end-line nil, :hash "796398857"} {:id "def/unload-handlers", :kind "def", :line 57, :end-line nil, :hash "-175350766"} {:id "defn-/run-unload-action!", :kind "defn-", :line 62, :end-line nil, :hash "1816453156"} {:id "defn/handle-unload-key", :kind "defn", :line 66, :end-line nil, :hash "410383859"} {:id "defn/handle-sentry-key", :kind "defn", :line 72, :end-line nil, :hash "851365046"} {:id "defn-/look-around-processed!", :kind "defn-", :line 97, :end-line nil, :hash "2036890010"} {:id "defn-/apply-look-around-mode", :kind "defn-", :line 103, :end-line nil, :hash "-620821138"} {:id "defn-/apply-look-around-decision", :kind "defn-", :line 114, :end-line nil, :hash "510804426"} {:id "defn/handle-look-around-key", :kind "defn", :line 130, :end-line nil, :hash "341214002"} {:id "defn-/launch-airport-fighter!", :kind "defn-", :line 134, :end-line nil, :hash "-2095311380"} {:id "defn-/disembark-from-transport!", :kind "defn-", :line 142, :end-line nil, :hash "890487919"} {:id "defn-/disembark-with-target!", :kind "defn-", :line 148, :end-line nil, :hash "1929162779"} {:id "defn-/apply-combat-action!", :kind "defn-", :line 154, :end-line nil, :hash "907654037"} {:id "defn-/set-unit-movement!", :kind "defn-", :line 161, :end-line nil, :hash "1052842188"} {:id "defn-/reject-click!", :kind "defn-", :line 167, :end-line nil, :hash "-242526960"} {:id "def/click-handlers", :kind "def", :line 173, :end-line nil, :hash "532964997"} {:id "defn/handle-unit-click", :kind "defn", :line 183, :end-line nil, :hash "418988995"} {:id "defn/handle-cell-click", :kind "defn", :line 194, :end-line nil, :hash "-1491182524"}]}
;; clj-mutate-manifest-end
