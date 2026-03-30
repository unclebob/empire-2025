(ns empire.player.commands-action-decisions
  (:require [empire.game-mechanics.movement.coastline :as coastline]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.game-mechanics.services.combat :as combat]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.config.core :as config]
            [empire.config.messages :as messages]
            [empire.config.units.dispatcher :as dispatcher]))

(defn space-key-action
  [unit]
  (when unit
    (if (= :fighter (:type unit))
      (let [current-fuel (:fuel unit config/fighter-fuel)
            fuel-cost (config/unit-speed :fighter)
            new-fuel (- current-fuel fuel-cost)]
        (if (<= new-fuel 0)
          {:action :skip-and-destroy
           :reason :skipping-this-round}
          {:action :skip-and-burn-fuel
           :fuel new-fuel
           :reason (str "Skipping this round. Fuel: " new-fuel)}))
      {:action :skip
       :reason :skipping-this-round})))

(defn unload-key-action
  [contents cell active-unit]
  (cond
    (uc/transport-with-armies? contents) {:action :wake-armies-on-transport}
    (uc/carrier-with-fighters? contents) {:action :wake-fighters-on-carrier}
    (and (= :city (:type cell)) (pos? (:fighter-count cell 0)))
    {:action :wake-fighters-on-airport}
    :else nil))

(defn sentry-key-action
  [cell active-unit]
  (let [is-army-aboard? (movement-state/is-army-aboard-transport? active-unit)
        is-carrier-fighter? (movement-state/is-fighter-from-carrier? active-unit)
        is-airport-fighter? (movement-state/is-fighter-from-airport? active-unit)]
    (cond
      is-army-aboard? {:action :sleep-armies-on-transport}
      is-carrier-fighter? {:action :sleep-fighters-on-carrier}
      is-airport-fighter? {:action :sleep-fighters-on-airport}
      (not= :city (:type cell))
      {:action :set-sentry-mode}
      :else nil)))

(defn adjacent-land-target
  [world coords]
  (let [[x y] coords]
    (first (for [dx [-1 0 1]
                 dy [-1 0 1]
                 :when (not (and (zero? dx) (zero? dy)))
                 :let [target [(+ x dx) (+ y dy)]
                       tcell (get-in world target)]
                 :when (and tcell (= :land (:type tcell)) (not (:contents tcell)))]
             target))))

(defn look-around-action
  [world coords active-unit]
  (let [is-army-aboard? (movement-state/is-army-aboard-transport? active-unit)
        near-coast? (map-utils/any-neighbor-matches?
                     coords world map-utils/neighbor-offsets
                     #(= :land (:type %)))
        rejection-reason (coastline/coastline-follow-rejection-reason active-unit near-coast?)]
    (cond
      (and (= :army (:type active-unit)) (not is-army-aboard?))
      {:action :set-explore-mode}

      is-army-aboard?
      (if-let [valid-target (adjacent-land-target world coords)]
        {:action :disembark-army-to-explore
         :target valid-target}
        {:action :no-op})

      (coastline/coastline-follow-eligible? active-unit near-coast?)
      {:action :set-coastline-follow-mode}

      rejection-reason
      {:action :reject
       :message (rejection-reason config/messages)}

      :else nil)))

(defn adjacent-coords?
  [c1 c2]
  (let [[ax ay] c1
        [cx cy] c2]
    (and (<= (abs (- ax cx)) 1)
         (<= (abs (- ay cy)) 1))))

(defn- army-coastal-attack-action
  [attn-coords clicked-coords active-unit target-cell target-unit]
  (when (and (adjacent-coords? attn-coords clicked-coords)
             (= (:type active-unit) :army)
             (= :sea (:type target-cell))
             (map-utils/on-coast? (first attn-coords) (second attn-coords))
             (combat/hostile-unit? target-unit :player))
    {:action :coastal-army-attack
     :target clicked-coords}))

(defn- hostile-city-action
  [world attn-coords clicked-coords active-unit]
  (when (and (adjacent-coords? attn-coords clicked-coords)
             (combat/hostile-city? world clicked-coords))
    {:action (case (:type active-unit)
               :army :attempt-conquest
               :fighter :attempt-fighter-overfly
               :set-unit-movement)
     :target clicked-coords}))

(defn- standard-click-action
  [world attn-coords clicked-coords active-unit]
  (let [target-cell (get-in world clicked-coords)
        target-unit (:contents target-cell)
        friendly-blocker? (and target-unit
                               (= (:owner target-unit) (:owner active-unit))
                               (not (and (= (:type active-unit) :fighter)
                                         (= (:type target-unit) :carrier)))
                               (not (and (= (:type active-unit) :fighter)
                                         (= (:type target-cell) :city)
                                         (= (:city-status target-cell) :player))))
        blocking-reason (cond
                          (nil? target-cell) :not-on-map
                          friendly-blocker?
                          :somethings-in-the-way
                          (= (:type active-unit) :army)
                          (cond
                            (= (:type target-cell) :sea) :cant-move-into-water
                            (and (= (:type target-cell) :city)
                                 (= (:city-status target-cell) :player)) :cant-move-into-city)
                          (dispatcher/naval-unit? (:type active-unit))
                          (cond
                            (= (:type target-cell) :land) :ships-cant-drive-on-land
                            (= (:type target-cell) :city) :ships-cant-enter-city))]
    (or (army-coastal-attack-action attn-coords clicked-coords active-unit target-cell target-unit)
        (hostile-city-action world attn-coords clicked-coords active-unit)
        (when blocking-reason
          {:action :reject
           :message (blocking-reason messages/messages)})
        {:action :set-unit-movement
         :target clicked-coords})))

(defn click-action
  [world attn-coords clicked-coords context active-unit]
  (case context
    :airport-fighter {:action :launch-fighter-from-airport
                      :target clicked-coords}
    :army-aboard (let [target-cell (get-in world clicked-coords)]
                   (when (and (adjacent-coords? attn-coords clicked-coords)
                              (= (:type target-cell) :land)
                              (not (:contents target-cell)))
                     {:action :disembark-army-from-transport
                      :target clicked-coords}))
    (standard-click-action world attn-coords clicked-coords active-unit)))

(defn city-production-action
  [{:keys [naval? coastal? item]}]
  (cond
    (and naval? (not coastal?))
    {:action :reject-production
     :item item}

    :else
    {:action :set-production
     :item item}))

(defn movement-context-action
  [context]
  (case context
    :airport-fighter :launch-airport-fighter
    :carrier-fighter :launch-carrier-fighter
    :army-aboard :army-aboard-movement
    :standard-unit :standard-unit-movement))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:20:50.114986-05:00", :module-hash "1098321867", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "2127714231"} {:id "defn/space-key-action", :kind "defn", :line 9, :end-line 23, :hash "1796301992"} {:id "defn/unload-key-action", :kind "defn", :line 25, :end-line 30, :hash "-1403859322"} {:id "defn/sentry-key-action", :kind "defn", :line 32, :end-line 42, :hash "1809983247"} {:id "defn/adjacent-land-target", :kind "defn", :line 44, :end-line 53, :hash "-776962319"} {:id "defn/look-around-action", :kind "defn", :line 55, :end-line 79, :hash "-1104782613"} {:id "defn/adjacent-coords?", :kind "defn", :line 81, :end-line 86, :hash "-1207367948"} {:id "defn-/army-coastal-attack-action", :kind "defn-", :line 88, :end-line 96, :hash "431117189"} {:id "defn-/hostile-city-action", :kind "defn-", :line 98, :end-line 106, :hash "-855228636"} {:id "defn-/standard-click-action", :kind "defn-", :line 108, :end-line 115, :hash "-1668856625"} {:id "defn/click-action", :kind "defn", :line 117, :end-line 128, :hash "-1111911825"} {:id "defn/city-production-action", :kind "defn", :line 130, :end-line 139, :hash "1476259673"} {:id "defn/movement-context-action", :kind "defn", :line 141, :end-line 147, :hash "527198210"}]}
;; clj-mutate-manifest-end
