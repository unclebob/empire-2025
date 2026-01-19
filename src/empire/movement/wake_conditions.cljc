(ns empire.movement.wake-conditions
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.map-utils :as map-utils]
            [empire.unit-container :as uc]
            [empire.units.dispatcher :as dispatcher]))

(defn near-hostile-city?
  "Returns true if position is adjacent to a hostile city."
  [pos current-map]
  (some (fn [[di dj]]
          (let [ni (+ (first pos) di)
                nj (+ (second pos) dj)
                adj-cell (get-in @current-map [ni nj])]
            (and adj-cell
                 (= (:type adj-cell) :city)
                 (config/hostile-city? (:city-status adj-cell)))))
        map-utils/neighbor-offsets))

(defn friendly-city-in-range?
  "Returns true if there is a friendly city within max-dist cells."
  [pos max-dist current-map]
  (let [[px py] pos
        height (count @current-map)
        width (count (first @current-map))]
    (some (fn [[i j]]
            (let [cell (get-in @current-map [i j])]
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
        height (count @current-map)
        width (count (first @current-map))]
    (some (fn [[di dj]]
            (let [ni (+ px di)
                  nj (+ py dj)]
              (when (and (>= ni 0) (< ni height)
                         (>= nj 0) (< nj width))
                (let [cell (get-in @current-map [ni nj])
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
         (not (uc/full? next-contents :fighter-count config/carrier-capacity)))))

(defn- blocking-wake-reason
  "Returns the wake reason if the unit is blocked, nil otherwise."
  [unit next-cell]
  (cond
    (and (:contents next-cell) (not (fighter-landing-on-carrier? unit next-cell)))
    :somethings-in-the-way

    (and (= (:type unit) :army) (= (:type next-cell) :sea))
    :cant-move-into-water

    (and (= (:type unit) :army) (= (:type next-cell) :city) (= (:city-status next-cell) :player))
    :cant-move-into-city

    (and (= (:type unit) :fighter)
         (= (:type next-cell) :city)
         (config/hostile-city? (:city-status next-cell)))
    :fighter-over-defended-city

    (and (config/naval-unit? (:type unit)) (= (:type next-cell) :land))
    :ships-cant-drive-on-land))

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
    (let [[tx ty] target
          [fx fy] final-pos
          target-cell (get-in @current-map target)
          target-contents (:contents target-cell)
          distance (max (abs (- tx fx)) (abs (- ty fy)))]
      (or (and (friendly-city? target-cell)
               (<= distance fuel))
          ;; Carrier may be moving away, so account for chase:
          ;; fuel needed = distance * fighter-speed / (fighter-speed - carrier-speed)
          ;; = distance * 8 / 6 = distance * 4/3
          (and (friendly-carrier? target-contents unit)
               (<= (* distance 4/3) fuel))))))

(defn- wake-fighter-check [unit _from-pos final-pos current-map]
  (let [dest-cell (get-in @current-map final-pos)
        entering-city? (= (:type dest-cell) :city)
        friendly-city? (= (:city-status dest-cell) :player)
        hostile-city? (and entering-city? (not friendly-city?))
        fuel (:fuel unit config/fighter-fuel)
        low-fuel? (<= fuel 1)
        bingo-fuel? (and (<= fuel (quot config/fighter-fuel 4))
                         (friendly-city-in-range? final-pos fuel current-map)
                         (not (target-is-reachable-friendly-city? unit final-pos fuel current-map)))]
    (cond
      hostile-city? {:wake? true :reason :fighter-shot-down :shot-down? true}
      entering-city? {:wake? true :reason :fighter-landed-and-refueled :refuel? true}
      low-fuel? {:wake? true :reason :fighter-out-of-fuel}
      bingo-fuel? {:wake? true :reason :fighter-bingo}
      :else nil)))

(defn- wake-transport-check [unit from-pos final-pos current-map]
  (let [has-armies? (pos? (:army-count unit 0))
        at-beach? (map-utils/adjacent-to-land? final-pos current-map)
        was-in-open-sea? (map-utils/completely-surrounded-by-sea? from-pos current-map)
        now-in-open-sea? (map-utils/completely-surrounded-by-sea? final-pos current-map)
        found-land? (and was-in-open-sea? at-beach?)
        been-to-sea? (:been-to-sea unit true)]
    (cond
      found-land? {:wake? true :reason :transport-found-land :been-to-sea false}
      (and has-armies? at-beach? been-to-sea?) {:wake? true :reason :transport-at-beach :been-to-sea false}
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
    (let [cell (get-in @current-map final-pos)]
      (:marching-orders (:waypoint cell)))))

(defn- apply-state-changes [unit result]
  (cond-> unit
    (contains? result :been-to-sea) (assoc :been-to-sea (:been-to-sea result))))

(defn wake-after-move
  "Checks if a unit should wake after making a move.
   Returns the updated unit with appropriate mode/reason."
  [unit from-pos final-pos current-map]
  (let [is-at-target? (= final-pos (:target unit))
        handler (wake-check-handlers (:type unit))
        result (when handler (handler unit from-pos final-pos current-map))
        waypoint-orders (get-waypoint-orders unit final-pos current-map)
        enemy-spotted? (enemy-unit-visible? unit final-pos current-map)
        wake-up? (and (or is-at-target? (:wake? result) enemy-spotted?)
                      (not waypoint-orders))
        final-result (if (and enemy-spotted? (not (:wake? result)))
                       {:wake? true :reason :enemy-spotted}
                       result)]
    (when (:shot-down? final-result)
      (atoms/set-line3-message (:fighter-destroyed-by-city config/messages) 3000))
    (cond
      waypoint-orders
      (-> unit
          (apply-state-changes final-result)
          (assoc :target waypoint-orders))

      wake-up?
      (dissoc (apply-wake-result unit final-result) :target)

      :else
      (apply-state-changes unit final-result))))
