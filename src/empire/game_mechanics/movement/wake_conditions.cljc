(ns empire.game-mechanics.movement.wake-conditions
  (:require [empire.config.core :as config]
            [empire.state.api :as sa]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.movement.wake-conditions.fighter :as fighter-wake]
            [empire.game-mechanics.movement.wake-conditions.transport :as transport-wake]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.config.units.dispatcher :as dispatcher]))

(defn- write-runtime-state!
  [k v]
  (sa/write-state! k v))

(defn- map-data
  [current-map]
  (map-utils/resolve-map-source current-map))

(defn- set-error-message!
  [msg ms]
  (write-runtime-state! :error-message msg)
  (write-runtime-state! :error-until (+ (System/currentTimeMillis) ms)))

(defn near-hostile-city?
  "Returns true if position is adjacent to a hostile city."
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

(defn friendly-city-in-range?
  "Returns true if there is a friendly city within max-dist cells."
  [pos max-dist current-map]
  (let [[px py] pos
        world (map-data current-map)
        height (count world)
        width (count (first world))]
    (some (fn [[i j]]
            (let [cell (get-in world [i j])]
              (and (= (:type cell) :city)
                   (= (:city-status cell) :player)
                   (<= (max (abs (- i px)) (abs (- j py))) max-dist))))
          (for [i (range height) j (range width)] [i j]))))

(defn enemy-unit-visible?
  "Returns true if an enemy unit is within the unit's visibility radius."
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

(defn- fighter-landing-on-carrier? [unit next-cell]
  (let [next-contents (:contents next-cell)]
    (and (= (:type unit) :fighter)
         (= (:type next-contents) :carrier)
         (= (:owner next-contents) (:owner unit))
         (not (uc/full? next-contents :fighter-count (dispatcher/effective-capacity :carrier (:hits next-contents)))))))

(defn- fighter-landing-at-city? [unit next-cell]
  (and (= (:type unit) :fighter)
       (= (:type next-cell) :city)
       (= (:city-status next-cell) :player)))

(defn- occupied-blocking-reason [unit next-cell]
  (when (and (:contents next-cell)
             (not (fighter-landing-on-carrier? unit next-cell))
             (not (fighter-landing-at-city? unit next-cell)))
    :somethings-in-the-way))

(defn- army-blocking-reason [cell-type cell-status city? hostile?]
  (cond
    (= cell-type :sea)                  :cant-move-into-water
    (and city? (= cell-status :player)) :cant-move-into-city
    hostile?                            :army-found-city))

(defn- naval-blocking-reason [cell-type city?]
  (cond
    (= cell-type :land) :ships-cant-drive-on-land
    ;; Ships cannot enter cities (damaged ships docking handled in move-unit)
    city?               :ships-cant-enter-city))

(defn- hostile-city? [next-cell]
  (and (= (:type next-cell) :city)
       (config/hostile-city? (:city-status next-cell))))

(defn- unit-type-blocking-reason [unit next-cell hostile?]
  (case (:type unit)
    :army (army-blocking-reason (:type next-cell) (:city-status next-cell)
                                (= (:type next-cell) :city) hostile?)
    :fighter (when hostile? :fighter-over-defended-city)
    (when (dispatcher/naval-units (:type unit))
      (naval-blocking-reason (:type next-cell) (= (:type next-cell) :city)))))

(defn- blocking-wake-reason
  "Returns the wake reason if the unit is blocked, nil otherwise."
  [unit next-cell]
  (or (occupied-blocking-reason unit next-cell)
      (unit-type-blocking-reason unit next-cell (hostile-city? next-cell))))

(defn- wake-unit-with-reason [unit reason]
  (assoc (dissoc (assoc unit :mode :awake) :target) :reason reason))

(defn wake-before-move
  "Checks if a unit should wake before making a move due to blocking conditions.
   Returns [updated-unit woke?] where woke? indicates if the unit woke up.
   Note: Enemy visibility is checked in wake-after-move and wake-sentries-seeing-enemy,
   not here, to allow user-directed movement to proceed."
  [unit next-cell]
  (if-let [reason (blocking-wake-reason unit next-cell)]
    [(wake-unit-with-reason unit reason) true]
    [unit false]))

;; Unit-specific wake check handlers

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
  "Returns the waypoint marching orders at final-pos if unit is an army, else nil."
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
    (set-error-message! (:fighter-destroyed-by-city config/messages)
                        config/error-message-duration))
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
  "Checks if a unit should wake after making a move.
   Returns the updated unit with appropriate mode/reason."
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
;; {:version 1, :tested-at "2026-03-13T09:23:59.465157-05:00", :module-hash "1498193459", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "1941504025"} {:id "defn-/write-runtime-state!", :kind "defn-", :line 10, :end-line 12, :hash "1105581680"} {:id "defn-/map-data", :kind "defn-", :line 14, :end-line 16, :hash "406913368"} {:id "defn-/set-error-message!", :kind "defn-", :line 18, :end-line 21, :hash "678717250"} {:id "defn/near-hostile-city?", :kind "defn", :line 23, :end-line 34, :hash "1856683080"} {:id "defn/friendly-city-in-range?", :kind "defn", :line 36, :end-line 48, :hash "-624092931"} {:id "defn/enemy-unit-visible?", :kind "defn", :line 50, :end-line 71, :hash "-1068497858"} {:id "defn-/fighter-landing-on-carrier?", :kind "defn-", :line 73, :end-line 78, :hash "-1430977648"} {:id "defn-/fighter-landing-at-city?", :kind "defn-", :line 80, :end-line 83, :hash "-666029366"} {:id "defn-/occupied-blocking-reason", :kind "defn-", :line 85, :end-line 89, :hash "110612946"} {:id "defn-/army-blocking-reason", :kind "defn-", :line 91, :end-line 95, :hash "1261272110"} {:id "defn-/naval-blocking-reason", :kind "defn-", :line 97, :end-line 101, :hash "-47713617"} {:id "defn-/hostile-city?", :kind "defn-", :line 103, :end-line 105, :hash "1644532547"} {:id "defn-/unit-type-blocking-reason", :kind "defn-", :line 107, :end-line 113, :hash "-1671425505"} {:id "defn-/blocking-wake-reason", :kind "defn-", :line 115, :end-line 119, :hash "817047601"} {:id "defn-/wake-unit-with-reason", :kind "defn-", :line 121, :end-line 122, :hash "-1604482991"} {:id "defn/wake-before-move", :kind "defn", :line 124, :end-line 132, :hash "-1901749309"} {:id "defn-/wake-army-check", :kind "defn-", :line 136, :end-line 138, :hash "1076072861"} {:id "def/wake-check-handlers", :kind "def", :line 140, :end-line 143, :hash "-2086580873"} {:id "defn-/apply-wake-result", :kind "defn-", :line 145, :end-line 150, :hash "-789567457"} {:id "defn-/get-waypoint-orders", :kind "defn-", :line 152, :end-line 157, :hash "1359675064"} {:id "defn-/apply-state-changes", :kind "defn-", :line 159, :end-line 161, :hash "1856624523"} {:id "defn-/compute-handler-result", :kind "defn-", :line 163, :end-line 168, :hash "58939931"} {:id "defn-/determine-final-result", :kind "defn-", :line 170, :end-line 173, :hash "-705184977"} {:id "defn-/apply-wake-action", :kind "defn-", :line 175, :end-line 189, :hash "-2100797452"} {:id "defn/wake-after-move", :kind "defn", :line 191, :end-line 202, :hash "-418781532"}]}
;; clj-mutate-manifest-end
