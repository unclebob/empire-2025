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

(defn- unload-action
  [contents cell]
  (cond
    (uc/transport-with-armies? contents) :wake-armies
    (uc/carrier-with-fighters? contents) :wake-fighters
    (and (= :city (:type cell)) (pos? (:fighter-count cell 0))) :wake-airport-fighters))

(defn- wake-airport-fighters!
  [coords cell]
  (let [active-unit (movement-state/get-active-unit cell)]
    (container-ops/wake-fighters-on-airport coords)
    (when (movement-state/is-fighter-from-airport? active-unit)
      (sa/update-world! update-in (conj coords :awake-fighters) dec))))

(def ^:private unload-handlers
  {:wake-armies (fn [coords _cell] (container-ops/wake-armies-on-transport coords))
   :wake-fighters (fn [coords _cell] (container-ops/wake-fighters-on-carrier coords))
   :wake-airport-fighters wake-airport-fighters!})

(defn- run-unload-action!
  [coords cell action]
  ((unload-handlers action) coords cell))

(defn handle-unload-key [coords cell]
  (when-let [action (unload-action (:contents cell) cell)]
    (run-unload-action! coords cell action)
    (helpers/item-processed!)
    true))

(defn- sentry-action
  [cell active-unit]
  (cond
    (movement-state/is-army-aboard-transport? active-unit) :sleep-armies
    (movement-state/is-fighter-from-carrier? active-unit) :sleep-carrier-fighters
    (movement-state/is-fighter-from-airport? active-unit) :sleep-airport-fighters
    (not= :city (:type cell)) :sentry))

(defn- sleep-airport-fighters!
  [coords]
  (container-ops/sleep-fighters-on-airport coords)
  (sa/update-state! :player-items rest))

(def ^:private sentry-handlers
  {:sleep-armies container-ops/sleep-armies-on-transport
   :sleep-carrier-fighters container-ops/sleep-fighters-on-carrier
   :sleep-airport-fighters sleep-airport-fighters!
   :sentry movement-state/set-unit-mode})

(defn- run-sentry-action!
  [coords action]
  (if (= :sentry action)
    ((sentry-handlers action) coords :sentry)
    ((sentry-handlers action) coords))
  (helpers/item-processed!)
  true)

(defn handle-sentry-key [coords cell active-unit]
  (when-let [action (sentry-action cell active-unit)]
    (run-sentry-action! coords action)))

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
  (helpers/set-warning-message! (rejection-reason config/messages))
  true)

(defn- look-around-exploring-army?
  [active-unit is-army-aboard?]
  (and (= :army (:type active-unit)) (not is-army-aboard?)))

(defn- look-around-coastline-action
  [active-unit near-coast? rejection-reason coords]
  (cond
    (coastline/coastline-follow-eligible? active-unit near-coast?)
    (begin-coastline-follow! coords)

    rejection-reason
    (show-coastline-rejection! rejection-reason)

    :else nil))

(defn handle-look-around-key [coords cell active-unit]
  (let [is-army-aboard? (movement-state/is-army-aboard-transport? active-unit)
        near-coast? (map-utils/any-neighbor-matches? coords (sa/current-world) map-utils/neighbor-offsets
                                                   #(= :land (:type %)))
        rejection-reason (coastline/coastline-follow-rejection-reason active-unit near-coast?)]
    (cond
      (look-around-exploring-army? active-unit is-army-aboard?)
      (begin-army-explore! coords)

      is-army-aboard?
      (disembark-army-to-explore! coords)

      :else
      (look-around-coastline-action active-unit near-coast? rejection-reason coords))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:18:54.084565-05:00", :module-hash "-195690588", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-1627975921"} {:id "defn/handle-space-key", :kind "defn", :line 12, :end-line nil, :hash "1077064710"} {:id "defn-/unload-action", :kind "defn-", :line 32, :end-line nil, :hash "912141839"} {:id "defn-/wake-airport-fighters!", :kind "defn-", :line 39, :end-line nil, :hash "-1846065962"} {:id "def/unload-handlers", :kind "def", :line 46, :end-line nil, :hash "-1001105680"} {:id "defn-/run-unload-action!", :kind "defn-", :line 51, :end-line nil, :hash "-1305179591"} {:id "defn/handle-unload-key", :kind "defn", :line 55, :end-line nil, :hash "1708718528"} {:id "defn-/sentry-action", :kind "defn-", :line 61, :end-line nil, :hash "158293282"} {:id "defn-/sleep-airport-fighters!", :kind "defn-", :line 69, :end-line nil, :hash "167616901"} {:id "def/sentry-handlers", :kind "def", :line 74, :end-line nil, :hash "1604811452"} {:id "defn-/run-sentry-action!", :kind "defn-", :line 80, :end-line nil, :hash "1145933451"} {:id "defn/handle-sentry-key", :kind "defn", :line 88, :end-line nil, :hash "564647558"} {:id "defn-/find-adjacent-land", :kind "defn-", :line 92, :end-line nil, :hash "-1561858921"} {:id "defn-/begin-army-explore!", :kind "defn-", :line 101, :end-line nil, :hash "1836684654"} {:id "defn-/disembark-army-to-explore!", :kind "defn-", :line 106, :end-line nil, :hash "-954811698"} {:id "defn-/begin-coastline-follow!", :kind "defn-", :line 112, :end-line nil, :hash "65514157"} {:id "defn-/show-coastline-rejection!", :kind "defn-", :line 117, :end-line nil, :hash "718412846"} {:id "defn-/look-around-exploring-army?", :kind "defn-", :line 121, :end-line nil, :hash "82160160"} {:id "defn-/look-around-coastline-action", :kind "defn-", :line 125, :end-line nil, :hash "-981502968"} {:id "defn/handle-look-around-key", :kind "defn", :line 136, :end-line nil, :hash "802559950"}]}
;; clj-mutate-manifest-end
