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

(defn- chebyshev-distance
  [[ax ay] [bx by]]
  (max (abs (- ax bx))
       (abs (- ay by))))

(defn- adjacent-open-land-target
  [world coords clicked-coords]
  (let [[x y] coords]
    (->> (for [dx [-1 0 1]
               dy [-1 0 1]
               :when (not (and (zero? dx) (zero? dy)))
               :let [target [(+ x dx) (+ y dy)]
                     tcell (get-in world target)]
               :when (and tcell
                          (= :land (:type tcell))
                          (not (:contents tcell)))]
           target)
         (sort-by #(chebyshev-distance % clicked-coords))
         first)))

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

(defn- friendly-blocker?
  [target-cell target-unit active-unit]
  (and target-unit
       (= (:owner target-unit) (:owner active-unit))
       (not (and (= (:type active-unit) :fighter)
                 (= (:type target-unit) :carrier)))
       (not (and (= (:type active-unit) :fighter)
                 (= (:type target-cell) :city)
                 (= (:city-status target-cell) :player)))))

(defn- army-blocking-reason
  [target-cell]
  (case (:type target-cell)
    :sea :cant-move-into-water
    :city (when (= (:city-status target-cell) :player)
            :cant-move-into-city)
    nil))

(defn- naval-blocking-reason
  [target-cell]
  ({:land :ships-cant-drive-on-land
    :city :ships-cant-enter-city} (:type target-cell)))

(defn- movement-blocking-reason
  [target-cell target-unit active-unit]
  (cond
    (nil? target-cell) :not-on-map
    (friendly-blocker? target-cell target-unit active-unit) :somethings-in-the-way
    (= (:type active-unit) :army) (army-blocking-reason target-cell)
    (dispatcher/naval-unit? (:type active-unit)) (naval-blocking-reason target-cell)))

(defn- rejection-action
  [reason]
  (when reason
    {:action :reject
     :message (reason messages/messages)}))

(defn- standard-click-action
  [world attn-coords clicked-coords active-unit]
  (let [target-cell (get-in world clicked-coords)
        target-unit (:contents target-cell)
        blocking-reason (movement-blocking-reason target-cell target-unit active-unit)]
    (or (some identity
              [(army-coastal-attack-action attn-coords clicked-coords active-unit target-cell target-unit)
               (hostile-city-action world attn-coords clicked-coords active-unit)
               (rejection-action blocking-reason)])
        {:action :set-unit-movement
         :target clicked-coords})))

(defn- army-aboard-attack-action
  [attn-coords clicked-coords active-unit target-unit]
  (when (and (adjacent-coords? attn-coords clicked-coords)
             (combat/hostile-unit? target-unit (:owner active-unit)))
    {:action :coastal-army-attack
     :target clicked-coords}))

(defn- army-aboard-disembark-action
  [attn-coords clicked-coords target-cell]
  (when (and (adjacent-coords? attn-coords clicked-coords)
             (= (:type target-cell) :land)
             (not (:contents target-cell)))
    {:action :disembark-army-from-transport
     :target clicked-coords}))

(defn- army-aboard-extended-disembark-action
  [adjacent-land-target clicked-coords]
  (when adjacent-land-target
    {:action :disembark-army-with-target
     :target adjacent-land-target
     :extended-target clicked-coords}))

(defn- army-aboard-click-action
  [world attn-coords clicked-coords active-unit]
  (let [target-cell (get-in world clicked-coords)
        target-unit (:contents target-cell)
        adjacent-land-target (adjacent-open-land-target world attn-coords clicked-coords)]
    (some identity
          [(army-aboard-attack-action attn-coords clicked-coords active-unit target-unit)
           (hostile-city-action world attn-coords clicked-coords active-unit)
           (army-aboard-disembark-action attn-coords clicked-coords target-cell)
           (army-aboard-extended-disembark-action adjacent-land-target clicked-coords)])))

(defn click-action
  [world attn-coords clicked-coords context active-unit]
  (case context
    :airport-fighter {:action :launch-fighter-from-airport
                      :target clicked-coords}
    :army-aboard (army-aboard-click-action world attn-coords clicked-coords active-unit)
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
;; {:version 1, :tested-at "2026-05-07T17:23:51.053753-05:00", :module-hash "-1369621678", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 9, :hash "323401925"} {:id "defn/space-key-action", :kind "defn", :line 11, :end-line 25, :hash "1796301992"} {:id "defn/unload-key-action", :kind "defn", :line 27, :end-line 34, :hash "674384875"} {:id "defn/sentry-key-action", :kind "defn", :line 36, :end-line 47, :hash "506811832"} {:id "defn/adjacent-land-target", :kind "defn", :line 49, :end-line 58, :hash "-776962319"} {:id "defn/look-around-action", :kind "defn", :line 60, :end-line 84, :hash "-1104782613"} {:id "defn/adjacent-coords?", :kind "defn", :line 86, :end-line 91, :hash "-1207367948"} {:id "defn-/chebyshev-distance", :kind "defn-", :line 93, :end-line 96, :hash "-94764975"} {:id "defn-/adjacent-open-land-target", :kind "defn-", :line 98, :end-line 111, :hash "-334674420"} {:id "defn-/army-coastal-attack-action", :kind "defn-", :line 113, :end-line 121, :hash "431117189"} {:id "defn-/hostile-city-action", :kind "defn-", :line 123, :end-line 131, :hash "-855228636"} {:id "defn-/friendly-blocker?", :kind "defn-", :line 133, :end-line 141, :hash "320660491"} {:id "defn-/army-blocking-reason", :kind "defn-", :line 143, :end-line 149, :hash "24121794"} {:id "defn-/naval-blocking-reason", :kind "defn-", :line 151, :end-line 154, :hash "127848293"} {:id "defn-/movement-blocking-reason", :kind "defn-", :line 156, :end-line 162, :hash "1315273709"} {:id "defn-/rejection-action", :kind "defn-", :line 164, :end-line 168, :hash "2014820854"} {:id "defn-/standard-click-action", :kind "defn-", :line 170, :end-line 180, :hash "-2002292125"} {:id "defn-/army-aboard-attack-action", :kind "defn-", :line 182, :end-line 187, :hash "152398951"} {:id "defn-/army-aboard-disembark-action", :kind "defn-", :line 189, :end-line 195, :hash "1591121177"} {:id "defn-/army-aboard-extended-disembark-action", :kind "defn-", :line 197, :end-line 202, :hash "487831673"} {:id "defn-/army-aboard-click-action", :kind "defn-", :line 204, :end-line 213, :hash "-464994346"} {:id "defn/click-action", :kind "defn", :line 215, :end-line 221, :hash "659796768"} {:id "defn/city-production-action", :kind "defn", :line 223, :end-line 232, :hash "1476259673"} {:id "defn/movement-context-action", :kind "defn", :line 234, :end-line 240, :hash "527198210"}]}
;; clj-mutate-manifest-end
