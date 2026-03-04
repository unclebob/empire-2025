(ns empire.ui.util.input.actions
  (:require [empire.application.ports.movement :as ports]
            [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.config :as config]
            [empire.combat :as combat]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.movement.coastline :as coastline]
            [empire.movement.explore :as explore]
            [empire.movement.map-utils :as map-utils]
            [empire.units.dispatcher :as dispatcher]))

(defonce ^:private state-ctx (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

(defn- update-runtime-state!
  [k f & args]
  (let [current (read-runtime-state k)
        next-state (apply f current args)]
    (write-runtime-state! k next-state)))

(defn- movement-port []
  (or (:movement-port @state-ctx)
      (throw (ex-info "Movement port not configured in runtime state context" {}))))

(defn- game-loop-call
  [sym & args]
  (let [f (or (try
                (requiring-resolve (symbol "empire.game-loop" (name sym)))
                (catch #?(:clj Throwable :cljs :default) _
                  nil))
              (throw (ex-info (str "Unable to resolve game-loop function: " (name sym))
                              {:symbol sym})))]
    (apply f args)))

(defn- player-call
  [ns-name sym & args]
  (let [f (or (try
                (requiring-resolve (symbol ns-name (name sym)))
                (catch #?(:clj Throwable :cljs :default) _
                  nil))
              (throw (ex-info (str "Unable to resolve player function: " ns-name "/" (name sym))
                              {:namespace ns-name
                               :symbol sym})))]
    (apply f args)))

(defn- set-error-message!
  [msg ms]
  (write-runtime-state! :error-message msg)
  (write-runtime-state! :error-until (+ (System/currentTimeMillis) ms)))

(defn- item-processed!
  []
  (game-loop-call 'item-processed))

(defn- try-set-production [coords item]
  (let [[x y] coords
        coastal? (map-utils/on-coast? x y)
        naval? (dispatcher/naval-units item)]
    (if (and naval? (not coastal?))
      (set-error-message! (format "Must be coastal city to produce %s." (name item)) config/error-message-duration)
      (do
        (player-call "empire.player.production" 'set-city-production coords item)
        (item-processed!)))
    true))

(defn- handle-city-production-key [k coords cell]
  (when (and (= (:type cell) :city)
             (= (:city-status cell) :player)
             (not (ports/movement-get-active-unit (movement-port) cell)))
    (cond
      (= k :space) (do (update-runtime-state! :player-items rest)
                       (item-processed!)
                       true)
      (= k :x) (do (update-runtime-state! :production assoc coords :none)
                   (item-processed!)
                   true)
      (config/key->production-item k) (try-set-production coords (config/key->production-item k)))))

(defn- calculate-extended-target [coords [dx dy]]
  (let [world (current-world)
        height (count world)
        width (count (first world))
        [x y] coords]
    (loop [tx x ty y]
      (let [nx (+ tx dx)
            ny (+ ty dy)]
        (if (and (>= nx 0) (< nx height) (>= ny 0) (< ny width))
          (recur nx ny)
          [tx ty])))))

(defn- launch-fighter-and-update [launch-fn coords target]
  (let [fighter-pos (launch-fn coords target)]
    (write-runtime-state! :waiting-for-input false)
    (write-runtime-state! :attention-message "")
    (write-runtime-state! :cells-needing-attention [])
    (update-runtime-state! :player-items #(cons fighter-pos (rest %)))
    true))

(defn army-aboard-action [extended? target-cell hostile-city?]
  (player-call "empire.player.commands" 'army-aboard-action extended? target-cell hostile-city?))

(defn- handle-army-aboard-movement [coords adjacent-target target extended? target-cell]
  (case (army-aboard-action extended? target-cell (combat/hostile-city? adjacent-target))
    :disembark (do (container-ops/disembark-army-from-transport coords adjacent-target)
                   (item-processed!))
    :disembark-with-target (do (container-ops/disembark-army-with-target coords adjacent-target target)
                               (item-processed!))
    :conquest (do (container-ops/remove-army-from-transport coords)
                  (combat/attempt-city-conquest adjacent-target)
                  (item-processed!))
    nil)
  true)

(defn- undamaged-ship-entering-friendly-city? [active-unit adjacent-target]
  (let [target-cell (get-in (current-world) adjacent-target)
        unit-type (:type active-unit)
        max-hits (dispatcher/hits unit-type)]
    (and (dispatcher/naval-unit? unit-type)
         (= :city (:type target-cell))
         (= :player (:city-status target-cell))
         (= (:hits active-unit) max-hits))))

(defn- hostile-city-action [unit-type adjacent-target extended?]
  (when (and (not extended?) (combat/hostile-city? adjacent-target))
    ({:army :army-conquest
      :fighter :fighter-overfly} unit-type)))

(defn- standard-movement-action [active-unit adjacent-target extended?]
  (or (hostile-city-action (:type active-unit) adjacent-target extended?)
      (when (and (not extended?)
                 (undamaged-ship-entering-friendly-city? active-unit adjacent-target))
        :reject-undamaged-ship)
      :normal-move))

(defn- perform-standard-movement! [action coords adjacent-target target extended?]
  (case action
    :army-conquest (combat/attempt-conquest coords adjacent-target)
    :fighter-overfly (combat/attempt-fighter-overfly coords adjacent-target)
    :reject-undamaged-ship (set-error-message! "Ship not damaged, entry denied." config/error-message-duration)
    :normal-move (ports/movement-set-unit-movement (movement-port) coords target extended?))
  (when (not= :reject-undamaged-ship action)
    (item-processed!))
  true)

(defn- handle-standard-unit-movement [coords adjacent-target target extended? active-unit]
  (-> (standard-movement-action active-unit adjacent-target extended?)
      (perform-standard-movement! coords adjacent-target target extended?)))

(defn- execute-unit-movement [coords direction extended? active-unit cell]
  (let [[x y] coords
        [dx dy] direction
        adjacent-target [(+ x dx) (+ y dy)]
        target-cell (get-in (current-world) adjacent-target)
        target (if extended?
                 (calculate-extended-target coords direction)
                 adjacent-target)
        context (ports/movement-context (movement-port) cell active-unit)]
    (case context
      :airport-fighter (launch-fighter-and-update container-ops/launch-fighter-from-airport coords target)
      :carrier-fighter (launch-fighter-and-update container-ops/launch-fighter-from-carrier coords target)
      :army-aboard (handle-army-aboard-movement coords adjacent-target target extended? target-cell)
      :standard-unit (handle-standard-unit-movement coords adjacent-target target extended? active-unit))))

(defn- handle-unit-movement-key [k coords cell]
  (let [direction (or (config/key->direction k)
                      (config/key->extended-direction k))
        extended? (boolean (config/key->extended-direction k))]
    (when direction
      (let [active-unit (ports/movement-get-active-unit (movement-port) cell)]
        (when (and active-unit (= (:owner active-unit) :player))
          (execute-unit-movement coords direction extended? active-unit cell))))))

(defn- handle-space-key [coords]
  (let [cell (get-in (current-world) coords)
        unit (:contents cell)]
    (when unit
      (if (= :fighter (:type unit))
        (let [current-fuel (:fuel unit config/fighter-fuel)
              fuel-cost (config/unit-speed :fighter)
              new-fuel (- current-fuel fuel-cost)]
          (if (<= new-fuel 0)
            (do
              (update-game-map! assoc-in (conj coords :contents :hits) 0)
              (update-game-map! assoc-in (conj coords :contents :reason) :skipping-this-round))
            (do
              (update-game-map! assoc-in (conj coords :contents :fuel) new-fuel)
              (update-game-map! assoc-in (conj coords :contents :reason) (str "Skipping this round. Fuel: " new-fuel)))))
        (update-game-map! assoc-in (conj coords :contents :reason) :skipping-this-round))))
  (update-runtime-state! :player-items rest)
  (item-processed!)
  true)

(defn- handle-unload-key [coords cell]
  (let [contents (:contents cell)]
    (cond
      (uc/transport-with-armies? contents)
      (do (container-ops/wake-armies-on-transport coords)
          (item-processed!)
          true)

      (uc/carrier-with-fighters? contents)
      (do (container-ops/wake-fighters-on-carrier coords)
          (item-processed!)
          true)

      :else nil)))

(defn- handle-sentry-key [coords cell active-unit]
  (let [is-army-aboard? (ports/movement-is-army-aboard-transport? (movement-port) active-unit)
        is-carrier-fighter? (ports/movement-is-fighter-from-carrier? (movement-port) active-unit)
        is-airport-fighter? (ports/movement-is-fighter-from-airport? (movement-port) active-unit)]
    (cond
      is-army-aboard?
      (do (container-ops/sleep-armies-on-transport coords)
          (item-processed!)
          true)

      is-carrier-fighter?
      (do (container-ops/sleep-fighters-on-carrier coords)
          (item-processed!)
          true)

      (and (not= :city (:type cell)) (not is-airport-fighter?) (not is-carrier-fighter?))
      (do (ports/movement-set-unit-mode (movement-port) coords :sentry)
          (item-processed!)
          true)

      :else nil)))

(defn- find-adjacent-land [coords]
  (let [[x y] coords]
    (first (for [dx [-1 0 1] dy [-1 0 1]
                 :when (not (and (zero? dx) (zero? dy)))
                 :let [target [(+ x dx) (+ y dy)]
                       tcell (get-in (current-world) target)]
                 :when (and tcell (= :land (:type tcell)) (not (:contents tcell)))]
             target))))

(defn- begin-army-explore! [coords]
  (explore/set-explore-mode coords)
  (item-processed!)
  true)

(defn- disembark-army-to-explore! [coords]
  (when-let [valid-target (find-adjacent-land coords)]
    (container-ops/disembark-army-to-explore coords valid-target)
    (item-processed!))
  true)

(defn- begin-coastline-follow! [coords]
  (coastline/set-coastline-follow-mode coords)
  (item-processed!)
  true)

(defn- show-coastline-rejection! [rejection-reason]
  (write-runtime-state! :attention-message (rejection-reason config/messages))
  true)

(defn- handle-look-around-key [coords cell active-unit]
  (let [is-army-aboard? (ports/movement-is-army-aboard-transport? (movement-port) active-unit)
        near-coast? (map-utils/any-neighbor-matches? coords (current-world) map-utils/neighbor-offsets
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

(defn handle-key [k]
  (when-let [coords (first (read-runtime-state :cells-needing-attention))]
    (let [cell (get-in (current-world) coords)
          active-unit (ports/movement-get-active-unit (movement-port) cell)]
      (if active-unit
        (case k
          :space (handle-space-key coords)
          :u (handle-unload-key coords cell)
          :s (handle-sentry-key coords cell active-unit)
          :l (handle-look-around-key coords cell active-unit)
          (handle-unit-movement-key k coords cell))
        (handle-city-production-key k coords cell)))))
