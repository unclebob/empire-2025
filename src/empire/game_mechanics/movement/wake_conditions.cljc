(ns empire.game-mechanics.movement.wake-conditions
  (:require [empire.config.core :as config]
            [empire.state.api :as sa]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.movement.movement-pathing :as pathing]
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

(defn- friendly-city? [cell]
  (and (= (:type cell) :city)
       (= (:city-status cell) :player)))

(defn- friendly-carrier? [carrier unit]
  (and (= (:type carrier) :carrier)
       (= (:owner carrier) (:owner unit))))

(defn- target-is-reachable-friendly-city? [unit final-pos fuel current-map]
  (when-let [target (:target unit)]
    (let [world (map-data current-map)
          [tx ty] target
          [fx fy] final-pos
          target-cell (get-in world target)
          target-contents (:contents target-cell)
          distance (max (abs (- tx fx)) (abs (- ty fy)))]
      (or (and (friendly-city? target-cell)
               (<= distance fuel))
          ;; Carrier may be moving away, so account for chase:
          ;; fuel needed = distance * fighter-speed / (fighter-speed - carrier-speed)
          ;; = distance * 8 / 6 = distance * 4/3
          (and (friendly-carrier? target-contents unit)
               (<= (* distance 4/3) fuel))))))

(defn- landing-site-on-path-status [unit target next-pos next-cell]
  (cond
    (nil? next-pos) :blocked
    (hostile-city? next-cell) :blocked
    (friendly-city? next-cell) :landing-site
    (friendly-carrier? (:contents next-cell) unit) :landing-site
    (= next-pos target) :blocked
    :else :continue))

(defn- reachable-landing-site-on-path?
  [unit final-pos fuel current-map]
  (when-let [target (:target unit)]
    (let [world (map-data current-map)]
      (loop [pos final-pos
             remaining-fuel fuel]
        (when (pos? remaining-fuel)
          (let [next-pos (pathing/next-step-pos pos target)
                next-cell (get-in world next-pos)]
            (case (landing-site-on-path-status unit target next-pos next-cell)
              :landing-site true
              :blocked false
              (recur next-pos (dec remaining-fuel)))))))))

(defn- build-fighter-checks [unit final-pos current-map]
  (let [world (map-data current-map)
        dest-cell (get-in world final-pos)
        entering-city? (= (:type dest-cell) :city)
        friendly-city? (= (:city-status dest-cell) :player)
        hostile-city? (and entering-city? (not friendly-city?))
        fuel (:fuel unit config/fighter-fuel)
        low-fuel? (<= fuel 1)
        bingo-fuel? (and (<= fuel (quot config/fighter-fuel 4))
                         (friendly-city-in-range? final-pos fuel current-map)
                         (not (target-is-reachable-friendly-city? unit final-pos fuel current-map))
                         (not (reachable-landing-site-on-path? unit final-pos fuel current-map)))]
    [[hostile-city?  {:wake? true :reason :fighter-shot-down :shot-down? true}]
     [entering-city? {:wake? true :reason :fighter-landed-and-refueled :refuel? true}]
     [low-fuel?      {:wake? true :reason :fighter-out-of-fuel}]
     [bingo-fuel?    {:wake? true :reason :fighter-bingo}]]))

(defn- wake-fighter-check [unit _from-pos final-pos current-map]
  (let [checks (build-fighter-checks unit final-pos current-map)]
    (some (fn [[pred result]] (when pred result)) checks)))

(defn- found-land? [was-in-open-sea? at-beach?]
  (and was-in-open-sea? at-beach?))

(defn- should-wake-at-beach? [has-armies? at-beach? been-to-sea?]
  (and has-armies? at-beach? been-to-sea?))

(defn- wake-transport-check [unit from-pos final-pos current-map]
  (let [has-armies? (pos? (:army-count unit 0))
        at-beach? (map-utils/adjacent-to-land? final-pos current-map)
        was-in-open-sea? (map-utils/completely-surrounded-by-sea? from-pos current-map)
        now-in-open-sea? (map-utils/completely-surrounded-by-sea? final-pos current-map)
        been-to-sea? (:been-to-sea unit true)]
    (cond
      (found-land? was-in-open-sea? at-beach?) {:wake? true :reason :transport-found-land :been-to-sea false}
      (should-wake-at-beach? has-armies? at-beach? been-to-sea?) {:wake? true :reason :transport-at-beach :been-to-sea false}
      now-in-open-sea? {:been-to-sea true}
      :else nil)))

(def ^:private wake-check-handlers
  {:army wake-army-check
   :fighter wake-fighter-check
   :transport wake-transport-check})

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
;; {:version 1, :tested-at "2026-03-13T08:16:03.123175-05:00", :module-hash "-140343368", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "1361828187"} {:id "defn-/write-runtime-state!", :kind "defn-", :line 9, :end-line 11, :hash "1105581680"} {:id "defn-/map-data", :kind "defn-", :line 13, :end-line 15, :hash "406913368"} {:id "defn-/set-error-message!", :kind "defn-", :line 17, :end-line 20, :hash "678717250"} {:id "defn/near-hostile-city?", :kind "defn", :line 22, :end-line 33, :hash "1856683080"} {:id "defn/friendly-city-in-range?", :kind "defn", :line 35, :end-line 47, :hash "-624092931"} {:id "defn/enemy-unit-visible?", :kind "defn", :line 49, :end-line 70, :hash "-1068497858"} {:id "defn-/fighter-landing-on-carrier?", :kind "defn-", :line 72, :end-line 77, :hash "-1430977648"} {:id "defn-/fighter-landing-at-city?", :kind "defn-", :line 79, :end-line 82, :hash "-666029366"} {:id "defn-/occupied-blocking-reason", :kind "defn-", :line 84, :end-line 88, :hash "110612946"} {:id "defn-/army-blocking-reason", :kind "defn-", :line 90, :end-line 94, :hash "1261272110"} {:id "defn-/naval-blocking-reason", :kind "defn-", :line 96, :end-line 100, :hash "-47713617"} {:id "defn-/hostile-city?", :kind "defn-", :line 102, :end-line 104, :hash "1644532547"} {:id "defn-/unit-type-blocking-reason", :kind "defn-", :line 106, :end-line 112, :hash "-1671425505"} {:id "defn-/blocking-wake-reason", :kind "defn-", :line 114, :end-line 118, :hash "817047601"} {:id "defn-/wake-unit-with-reason", :kind "defn-", :line 120, :end-line 121, :hash "-1604482991"} {:id "defn/wake-before-move", :kind "defn", :line 123, :end-line 131, :hash "-1901749309"} {:id "defn-/wake-army-check", :kind "defn-", :line 135, :end-line 137, :hash "1076072861"} {:id "defn-/friendly-city?", :kind "defn-", :line 139, :end-line 141, :hash "798434623"} {:id "defn-/friendly-carrier?", :kind "defn-", :line 143, :end-line 145, :hash "-1325282601"} {:id "defn-/target-is-reachable-friendly-city?", :kind "defn-", :line 147, :end-line 161, :hash "468885002"} {:id "defn-/landing-site-on-path-status", :kind "defn-", :line 163, :end-line 170, :hash "-941817352"} {:id "defn-/reachable-landing-site-on-path?", :kind "defn-", :line 172, :end-line 184, :hash "1053660306"} {:id "defn-/build-fighter-checks", :kind "defn-", :line 186, :end-line 201, :hash "1568246404"} {:id "defn-/wake-fighter-check", :kind "defn-", :line 203, :end-line 205, :hash "1730640503"} {:id "defn-/found-land?", :kind "defn-", :line 207, :end-line 208, :hash "-1455074046"} {:id "defn-/should-wake-at-beach?", :kind "defn-", :line 210, :end-line 211, :hash "-566558367"} {:id "defn-/wake-transport-check", :kind "defn-", :line 213, :end-line 223, :hash "835612901"} {:id "def/wake-check-handlers", :kind "def", :line 225, :end-line 228, :hash "-2066350617"} {:id "defn-/apply-wake-result", :kind "defn-", :line 230, :end-line 235, :hash "-789567457"} {:id "defn-/get-waypoint-orders", :kind "defn-", :line 237, :end-line 242, :hash "1359675064"} {:id "defn-/apply-state-changes", :kind "defn-", :line 244, :end-line 246, :hash "1856624523"} {:id "defn-/compute-handler-result", :kind "defn-", :line 248, :end-line 253, :hash "58939931"} {:id "defn-/determine-final-result", :kind "defn-", :line 255, :end-line 258, :hash "-705184977"} {:id "defn-/apply-wake-action", :kind "defn-", :line 260, :end-line 274, :hash "-2100797452"} {:id "defn/wake-after-move", :kind "defn", :line 276, :end-line 287, :hash "-418781532"}]}
;; clj-mutate-manifest-end
