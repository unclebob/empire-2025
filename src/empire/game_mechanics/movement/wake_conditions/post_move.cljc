(ns empire.game-mechanics.movement.wake-conditions.post-move
  (:require [empire.config.core :as config]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.movement.wake-conditions.fighter :as fighter-wake]
            [empire.game-mechanics.movement.wake-conditions.transport :as transport-wake]
            [empire.notifications :as notifications]))

(defn- map-data
  [current-map]
  (map-utils/resolve-map-source current-map))

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
    (notifications/warn! (:fighter-destroyed-by-city config/messages)))
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
;; {:version 1, :tested-at "2026-09-01T15:06:52.963645-05:00", :module-hash "187881791", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-582719665"} {:id "defn-/map-data", :kind "defn-", :line 9, :end-line nil, :hash "406913368"} {:id "defn/near-hostile-city?", :kind "defn", :line 13, :end-line nil, :hash "772669187"} {:id "defn/enemy-unit-visible?", :kind "defn", :line 25, :end-line nil, :hash "1034389278"} {:id "defn-/wake-army-check", :kind "defn-", :line 47, :end-line nil, :hash "1076072861"} {:id "def/wake-check-handlers", :kind "def", :line 51, :end-line nil, :hash "-2086580873"} {:id "defn-/apply-wake-result", :kind "defn-", :line 56, :end-line nil, :hash "-789567457"} {:id "defn-/get-waypoint-orders", :kind "defn-", :line 63, :end-line nil, :hash "-162371689"} {:id "defn-/apply-state-changes", :kind "defn-", :line 69, :end-line nil, :hash "1856624523"} {:id "defn-/compute-handler-result", :kind "defn-", :line 73, :end-line nil, :hash "58939931"} {:id "defn-/determine-final-result", :kind "defn-", :line 80, :end-line nil, :hash "-705184977"} {:id "defn-/apply-wake-action", :kind "defn-", :line 85, :end-line nil, :hash "-1032322667"} {:id "defn/wake-after-move", :kind "defn", :line 100, :end-line nil, :hash "1931814687"}]}
;; clj-mutate-manifest-end
