;; mutation-tested: 2026-02-26
(ns empire.combat
  (:require [clojure.string]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.visibility :as visibility]
            [empire.units.dispatcher :as dispatcher]))

(def ^:private flippable-types
  "Unit types that flip ownership on city conquest (ships and fighters)."
  #{:fighter :transport :patrol-boat :destroyer :submarine :carrier :battleship})

(defn conquer-city-contents
  "Handles units at a conquered city per VMS Empire rules:
   - Armies are killed
   - Satellites are left unchanged
   - Ships and fighters flip ownership, wake up, clear orders
   - Transported armies and carried fighters are killed
   - City production and standing orders are cleared"
  [city-coords new-owner]
  (let [cell (get-in @atoms/game-map city-coords)
        contents (:contents cell)]
    ;; Process the unit standing on the city
    (when contents
      (cond
        ;; Kill armies
        (= :army (:type contents))
        (swap! atoms/game-map update-in city-coords dissoc :contents)

        ;; Leave satellites unchanged
        (= :satellite (:type contents))
        nil

        ;; Flip ships and fighters
        (flippable-types (:type contents))
        (let [flipped (-> contents
                          (assoc :owner new-owner :mode :awake)
                          (dissoc :target :reason))
              ;; Kill cargo inside containers
              flipped (cond-> flipped
                        (= :transport (:type flipped))
                        (assoc :army-count 0 :awake-armies 0)
                        (= :carrier (:type flipped))
                        (assoc :fighter-count 0 :awake-fighters 0))]
          (swap! atoms/game-map assoc-in (conj city-coords :contents) flipped)))))
  ;; Clear production
  (swap! atoms/production dissoc city-coords)
  ;; Clear standing orders
  (swap! atoms/game-map update-in city-coords dissoc :marching-orders :flight-path))

(defn hostile-city? [target-coords]
  (let [target-cell (get-in @atoms/game-map target-coords)]
    (and (= (:type target-cell) :city)
         (config/hostile-city? (:city-status target-cell)))))

(defn attempt-city-conquest
  "Rolls for city conquest. On success, converts city to player and conquers contents.
   On failure, shows failure message. Returns true regardless of outcome."
  [city-coords]
  (let [city-cell (get-in @atoms/game-map city-coords)]
    (if (< (rand) 0.5)
      (do
        (swap! atoms/game-map assoc-in city-coords (assoc city-cell :city-status :player))
        (conquer-city-contents city-coords :player)
        (visibility/update-cell-visibility city-coords :player)
        (swap! atoms/computer-map assoc-in (conj city-coords :city-status) :player))
      (atoms/set-error-message (:conquest-failed config/messages) config/error-message-duration))
    true))

(defn attempt-conquest
  "Attempts to conquer a city with an army. Returns true if conquest was attempted."
  [army-coords city-coords]
  (let [army-cell (get-in @atoms/game-map army-coords)]
    (swap! atoms/game-map assoc-in army-coords (dissoc army-cell :contents))
    (visibility/update-cell-visibility army-coords :player)
    (attempt-city-conquest city-coords)))

(defn attempt-fighter-overfly
  "Fighter flies over hostile city and gets shot down."
  [fighter-coords city-coords]
  (let [fighter-cell (get-in @atoms/game-map fighter-coords)
        fighter (:contents fighter-cell)
        city-cell (get-in @atoms/game-map city-coords)
        shot-down-fighter (assoc fighter :mode :awake :hits 0 :steps-remaining 0 :reason :fighter-shot-down)]
    (swap! atoms/game-map assoc-in fighter-coords (dissoc fighter-cell :contents))
    (swap! atoms/game-map assoc-in city-coords (assoc city-cell :contents shot-down-fighter))
    (atoms/set-error-message (:fighter-destroyed-by-city config/messages) config/error-message-duration)
    true))

(defn hostile-unit?
  "Returns true if the unit is hostile to the given owner."
  [unit owner]
  (and unit (not= (:owner unit) owner)))

(defn- unit-name
  "Returns a capitalized display name for a unit type."
  [unit-type]
  (-> unit-type name clojure.string/capitalize))

(defn- format-log-entry
  "Formats a single combat log entry.
   Uses lowercase for defender, uppercase for attacker."
  [entry attacker-type defender-type]
  (let [unit-char (if (= :defender (:hit entry))
                    (clojure.string/lower-case (dispatcher/display-char defender-type))
                    (clojure.string/upper-case (dispatcher/display-char attacker-type)))]
    (str unit-char "-" (:damage entry))))

(defn format-combat-log
  "Formats a combat log for display.
   Format: c-3,S-1,S-1. Submarine destroyed."
  [log attacker-type defender-type winner]
  (let [entries (map #(format-log-entry % attacker-type defender-type) log)
        exchange-str (clojure.string/join "," entries)
        loser-type (if (= winner :attacker) defender-type attacker-type)
        loser-name (unit-name loser-type)]
    (str exchange-str ". " loser-name " destroyed.")))

(defn fight-round
  "Executes one round of combat. 50% chance attacker hits, 50% chance defender hits.
   Returns [updated-attacker updated-defender log-entry]."
  [attacker defender]
  (if (< (rand) 0.5)
    (let [damage (dispatcher/strength (:type attacker))]
      [attacker (update defender :hits - damage) {:hit :defender :damage damage}])
    (let [damage (dispatcher/strength (:type defender))]
      [(update attacker :hits - damage) defender {:hit :attacker :damage damage}])))

(defn resolve-combat
  "Fights combat rounds until one unit dies.
   Returns {:winner :attacker|:defender :survivor unit-map :log [log-entries]}."
  [attacker defender]
  (loop [a attacker d defender log []]
    (let [[new-a new-d log-entry] (fight-round a d)
          new-log (conj log log-entry)]
      (cond
        (<= (:hits new-d) 0) {:winner :attacker :survivor new-a :log new-log}
        (<= (:hits new-a) 0) {:winner :defender :survivor new-d :log new-log}
        :else (recur new-a new-d new-log)))))

(defn- find-units-where
  "Returns coords of all units on game-map where (pred unit) is true."
  [pred]
  (let [game-map @atoms/game-map]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [unit (get-in game-map [i j :contents])]
          :when (and unit (pred unit))]
      [i j])))

(defn- clear-dead-escort-from-carrier
  "Remove dead battleship/submarine reference from its paired carrier."
  [dead-unit]
  (let [carrier-id (:escort-carrier-id dead-unit)
        escort-id (:escort-id dead-unit)
        coords (find-units-where #(and (= :carrier (:type %))
                                       (= carrier-id (:carrier-id %))))]
    (doseq [pos coords]
      (case (:type dead-unit)
        :battleship
        (swap! atoms/game-map assoc-in (conj pos :contents :group-battleship-id) nil)
        :submarine
        (swap! atoms/game-map update-in (conj pos :contents :group-submarine-ids)
               (fn [ids] (vec (remove #{escort-id} ids))))))))

(defn- release-carrier-escorts
  "When a carrier dies, release all its escorts to seeking mode."
  [dead-unit]
  (let [carrier-id (:carrier-id dead-unit)
        coords (find-units-where #(= carrier-id (:escort-carrier-id %)))]
    (doseq [pos coords]
      (swap! atoms/game-map update-in (conj pos :contents)
             #(-> % (assoc :escort-mode :seeking)
                  (dissoc :escort-carrier-id :orbit-angle))))))

(defn- clear-carrier-group-on-death
  "When a carrier group member dies, update the group pairing."
  [dead-unit]
  (cond
    (and (#{:battleship :submarine} (:type dead-unit))
         (:escort-carrier-id dead-unit))
    (clear-dead-escort-from-carrier dead-unit)

    (and (= :carrier (:type dead-unit))
         (:carrier-id dead-unit))
    (release-carrier-escorts dead-unit)))

(defn clear-escort-on-death
  "When a unit with escort pairing is destroyed, clear the partner's reference."
  [dead-unit]
  (cond
    ;; Dead destroyer: clear transport's escort-destroyer-id
    (and (= :destroyer (:type dead-unit))
         (:escort-transport-id dead-unit))
    (let [tid (:escort-transport-id dead-unit)
          coords (find-units-where #(and (= :transport (:type %))
                                         (= tid (:transport-id %))))]
      (doseq [pos coords]
        (swap! atoms/game-map update-in (conj pos :contents) dissoc :escort-destroyer-id)))

    ;; Dead transport: set destroyer to seeking
    (and (= :transport (:type dead-unit))
         (:escort-destroyer-id dead-unit))
    (let [did (:escort-destroyer-id dead-unit)
          coords (find-units-where #(and (= :destroyer (:type %))
                                         (= did (:destroyer-id %))))]
      (doseq [pos coords]
        (swap! atoms/game-map update-in (conj pos :contents)
               #(-> % (assoc :escort-mode :seeking)
                    (dissoc :escort-transport-id))))))
  ;; Also handle carrier group pairings
  (clear-carrier-group-on-death dead-unit))

(defn- drown-excess-cargo
  "After combat, if a container's cargo exceeds its effective capacity, kill excess."
  [coords survivor]
  (when (#{:transport :carrier} (:type survivor))
    (let [cap (dispatcher/effective-capacity (:type survivor) (:hits survivor))
          [count-key awake-key] (if (= :transport (:type survivor))
                                  [:army-count :awake-armies]
                                  [:fighter-count :awake-fighters])
          current-count (get survivor count-key 0)
          excess (- current-count cap)]
      (when (pos? excess)
        (let [current-awake (get survivor awake-key 0)
              new-awake (min current-awake cap)]
          (swap! atoms/game-map update-in (conj coords :contents)
                 assoc count-key cap awake-key new-awake))))))

(defn attempt-attack
  "Attempts to attack an enemy unit at target-coords from attacker-coords.
   Returns true if attack was attempted, false otherwise."
  [attacker-coords target-coords]
  (let [attacker-cell (get-in @atoms/game-map attacker-coords)
        target-cell (get-in @atoms/game-map target-coords)
        attacker (:contents attacker-cell)
        defender (:contents target-cell)]
    (if (or (nil? defender) (not (hostile-unit? defender (:owner attacker))))
      false
      (let [result (resolve-combat attacker defender)
            message (format-combat-log (:log result)
                                       (:type attacker)
                                       (:type defender)
                                       (:winner result))
            dead-unit (if (= :attacker (:winner result)) defender attacker)]
        (swap! atoms/game-map assoc-in (conj attacker-coords :contents) nil)
        (swap! atoms/game-map assoc-in (conj target-coords :contents) (:survivor result))
        ;; Drown excess cargo if surviving container took damage
        (drown-excess-cargo target-coords (:survivor result))
        ;; Clear escort pairing if destroyer or transport died
        (clear-escort-on-death dead-unit)
        (atoms/set-turn-message message Long/MAX_VALUE)
        true))))
