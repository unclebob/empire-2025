(ns empire.game-mechanics.movement.wake-conditions.post-move
  (:require [empire.config.core :as config]
            [empire.state.api :as sa]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.movement.wake-conditions.fighter :as fighter-wake]
            [empire.game-mechanics.movement.wake-conditions.transport :as transport-wake]
            [empire.ui.sound :as sound]))

(defn- map-data
  [current-map]
  (map-utils/resolve-map-source current-map))

(defn- write-runtime-state!
  [k v]
  (sa/write-state! k v))

(defn- set-warning-message!
  [msg]
  (write-runtime-state! :warning-message msg)
  (sound/play-bonk!))

(defn near-hostile-city?
  [pos current-map]
  (let [world (map-data current-map)]
    (some (fn [[di dj]]
            (let [ni (+ (first pos) di)
                  nj (+ (second pos) dj)
                  adj-cell (get-in world [ni nj])]
              (and adj-cell
                   (= (:type adj-cell) :city)
                   (config/hostile-city? (:city-status adj-cell)))))
          map-utils/neighbor-offsets)))

(defn enemy-unit-visible?
  [unit pos current-map]
  (let [[px py] pos
        radius (dispatcher/visibility-radius (:type unit))
        owner (:owner unit)
        world (map-data current-map)
        height (count world)
        width (count (first world))]
    (some (fn [[di dj]]
            (let [ni (+ px di)
                  nj (+ py dj)]
              (when (and (>= ni 0) (< ni height)
                         (>= nj 0) (< nj width))
                (let [cell (get-in world [ni nj])
                      contents (:contents cell)]
                  (and contents
                       (not= (:owner contents) owner))))))
          (for [di (range (- radius) (inc radius))
                dj (range (- radius) (inc radius))
                :when (not (and (zero? di) (zero? dj)))]
            [di dj]))))

(defn- wake-army-check [_unit _from-pos final-pos current-map]
  (when (near-hostile-city? final-pos current-map)
    {:wake? true :reason :army-found-city}))

(def ^:private wake-check-handlers
  {:army wake-army-check
   :fighter fighter-wake/wake-check
   :transport transport-wake/wake-check})

(defn- apply-wake-result [unit result]
  (cond-> (assoc unit :mode :awake)
    (:reason result) (assoc :reason (:reason result))
    (:refuel? result) (assoc :fuel config/fighter-fuel)
    (:shot-down? result) (assoc :hits 0 :steps-remaining 0)
    (contains? result :been-to-sea) (assoc :been-to-sea (:been-to-sea result))))

(defn- get-waypoint-orders
  [unit final-pos current-map]
  (when (= :army (:type unit))
    (let [cell (get-in (map-data current-map) final-pos)]
      (:marching-orders (:waypoint cell)))))

(defn- apply-state-changes [unit result]
  (cond-> unit
    (contains? result :been-to-sea) (assoc :been-to-sea (:been-to-sea result))))

(defn- compute-handler-result [unit from-pos final-pos current-map is-at-target?]
  (let [handler (wake-check-handlers (:type unit))
        result (when handler (handler unit from-pos final-pos current-map))
        at-edge? (and is-at-target? (:extended unit) (not (:reason result))
                      (map-utils/at-map-edge? final-pos current-map))]
    (if at-edge? (assoc (or result {}) :reason :hit-edge) result)))

(defn- determine-final-result [result enemy-spotted?]
  (if (and enemy-spotted? (not (:wake? result)))
    {:wake? true :reason :enemy-spotted}
    result))

(defn- apply-wake-action [unit final-result waypoint-orders wake-up?]
  (when (:shot-down? final-result)
    (set-warning-message! (:fighter-destroyed-by-city config/messages)))
  (cond
    waypoint-orders
    (-> unit
        (apply-state-changes final-result)
        (assoc :target waypoint-orders))

    wake-up?
    (dissoc (apply-wake-result unit final-result) :target)

    :else
    (apply-state-changes unit final-result)))

(defn wake-after-move
  [unit from-pos final-pos current-map]
  (let [is-at-target? (= final-pos (:target unit))
        result (compute-handler-result unit from-pos final-pos current-map is-at-target?)
        waypoint-orders (get-waypoint-orders unit final-pos current-map)
        enemy-spotted? (enemy-unit-visible? unit final-pos current-map)
        wake-up? (and (or is-at-target? (:wake? result) enemy-spotted?)
                      (not waypoint-orders))
        final-result (determine-final-result result enemy-spotted?)]
    (apply-wake-action unit final-result waypoint-orders wake-up?)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:34:35.820413-05:00", :module-hash "1512035131", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "675149490"} {:id "defn-/map-data", :kind "defn-", :line 9, :end-line 11, :hash "406913368"} {:id "defn-/write-runtime-state!", :kind "defn-", :line 13, :end-line 15, :hash "1105581680"} {:id "defn-/set-error-message!", :kind "defn-", :line 17, :end-line 20, :hash "678717250"} {:id "defn/near-hostile-city?", :kind "defn", :line 22, :end-line 32, :hash "772669187"} {:id "defn/enemy-unit-visible?", :kind "defn", :line 34, :end-line 54, :hash "1034389278"} {:id "defn-/wake-army-check", :kind "defn-", :line 56, :end-line 58, :hash "1076072861"} {:id "def/wake-check-handlers", :kind "def", :line 60, :end-line 63, :hash "-2086580873"} {:id "defn-/apply-wake-result", :kind "defn-", :line 65, :end-line 70, :hash "-789567457"} {:id "defn-/get-waypoint-orders", :kind "defn-", :line 72, :end-line 76, :hash "-162371689"} {:id "defn-/apply-state-changes", :kind "defn-", :line 78, :end-line 80, :hash "1856624523"} {:id "defn-/compute-handler-result", :kind "defn-", :line 82, :end-line 87, :hash "58939931"} {:id "defn-/determine-final-result", :kind "defn-", :line 89, :end-line 92, :hash "-705184977"} {:id "defn-/apply-wake-action", :kind "defn-", :line 94, :end-line 108, :hash "-2100797452"} {:id "defn/wake-after-move", :kind "defn", :line 110, :end-line 119, :hash "1931814687"}]}
;; clj-mutate-manifest-end
