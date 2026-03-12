(ns empire.ui.util.input.actions.modes
  (:require [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.movement.coastline :as coastline]
            [empire.game-mechanics.movement.explore :as explore]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.ui.util.input.actions.helpers :as helpers]))

(defn handle-space-key [coords]
  (let [cell (get-in (sa/current-world) coords)
        unit (:contents cell)]
    (when unit
      (if (= :fighter (:type unit))
        (let [current-fuel (:fuel unit config/fighter-fuel)
              fuel-cost (config/unit-speed :fighter)
              new-fuel (- current-fuel fuel-cost)]
          (if (<= new-fuel 0)
            (do
              (sa/update-world! assoc-in (conj coords :contents :hits) 0)
              (sa/update-world! assoc-in (conj coords :contents :reason) :skipping-this-round))
            (do
              (sa/update-world! assoc-in (conj coords :contents :fuel) new-fuel)
              (sa/update-world! assoc-in (conj coords :contents :reason) (str "Skipping this round. Fuel: " new-fuel)))))
        (sa/update-world! assoc-in (conj coords :contents :reason) :skipping-this-round))))
  (sa/update-state! :player-items rest)
  (helpers/item-processed!)
  true)

(defn handle-unload-key [coords cell]
  (let [contents (:contents cell)]
    (cond
      (uc/transport-with-armies? contents)
      (do (container-ops/wake-armies-on-transport coords)
          (helpers/item-processed!)
          true)

      (uc/carrier-with-fighters? contents)
      (do (container-ops/wake-fighters-on-carrier coords)
          (helpers/item-processed!)
          true)

      :else nil)))

(defn handle-sentry-key [coords cell active-unit]
  (let [is-army-aboard? (movement-state/is-army-aboard-transport? active-unit)
        is-carrier-fighter? (movement-state/is-fighter-from-carrier? active-unit)
        is-airport-fighter? (movement-state/is-fighter-from-airport? active-unit)]
    (cond
      is-army-aboard?
      (do (container-ops/sleep-armies-on-transport coords)
          (helpers/item-processed!)
          true)

      is-carrier-fighter?
      (do (container-ops/sleep-fighters-on-carrier coords)
          (helpers/item-processed!)
          true)

      (and (not= :city (:type cell)) (not is-airport-fighter?) (not is-carrier-fighter?))
      (do (movement-state/set-unit-mode coords :sentry)
          (helpers/item-processed!)
          true)

      :else nil)))

(defn- find-adjacent-land [coords]
  (let [[x y] coords]
    (first (for [dx [-1 0 1] dy [-1 0 1]
                 :when (not (and (zero? dx) (zero? dy)))
                 :let [target [(+ x dx) (+ y dy)]
                       tcell (get-in (sa/current-world) target)]
                 :when (and tcell (= :land (:type tcell)) (not (:contents tcell)))]
             target))))

(defn- begin-army-explore! [coords]
  (explore/set-explore-mode coords)
  (helpers/item-processed!)
  true)

(defn- disembark-army-to-explore! [coords]
  (when-let [valid-target (find-adjacent-land coords)]
    (container-ops/disembark-army-to-explore coords valid-target)
    (helpers/item-processed!))
  true)

(defn- begin-coastline-follow! [coords]
  (coastline/set-coastline-follow-mode coords)
  (helpers/item-processed!)
  true)

(defn- show-coastline-rejection! [rejection-reason]
  (sa/write-state! :attention-message (rejection-reason config/messages))
  true)

(defn handle-look-around-key [coords cell active-unit]
  (let [is-army-aboard? (movement-state/is-army-aboard-transport? active-unit)
        near-coast? (map-utils/any-neighbor-matches? coords (sa/current-world) map-utils/neighbor-offsets
                                                   #(= :land (:type %)))
        rejection-reason (coastline/coastline-follow-rejection-reason active-unit near-coast?)]
    (cond
      ;; Army (not aboard) - explore mode
      (and (= :army (:type active-unit)) (not is-army-aboard?))
      (begin-army-explore! coords)

      ;; Army aboard transport - disembark to explore
      is-army-aboard?
      (disembark-army-to-explore! coords)

      ;; Transport or patrol-boat near coast - coastline follow
      (coastline/coastline-follow-eligible? active-unit near-coast?)
      (begin-coastline-follow! coords)

      ;; Transport or patrol-boat not near coast - show reason
      rejection-reason
      (show-coastline-rejection! rejection-reason)

      :else nil)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:03:22.87598-05:00", :module-hash "-134644115", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 10, :hash "-1627975921"} {:id "defn/handle-space-key", :kind "defn", :line 12, :end-line 30, :hash "1077064710"} {:id "defn/handle-unload-key", :kind "defn", :line 32, :end-line 45, :hash "1880255441"} {:id "defn/handle-sentry-key", :kind "defn", :line 47, :end-line 67, :hash "-47712445"} {:id "defn-/find-adjacent-land", :kind "defn-", :line 69, :end-line 76, :hash "-1561858921"} {:id "defn-/begin-army-explore!", :kind "defn-", :line 78, :end-line 81, :hash "1836684654"} {:id "defn-/disembark-army-to-explore!", :kind "defn-", :line 83, :end-line 87, :hash "-954811698"} {:id "defn-/begin-coastline-follow!", :kind "defn-", :line 89, :end-line 92, :hash "65514157"} {:id "defn-/show-coastline-rejection!", :kind "defn-", :line 94, :end-line 96, :hash "2003857323"} {:id "defn/handle-look-around-key", :kind "defn", :line 98, :end-line 120, :hash "-1191114588"}]}
;; clj-mutate-manifest-end
