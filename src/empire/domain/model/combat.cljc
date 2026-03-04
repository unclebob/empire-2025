;; mutation-tested: no
(ns empire.domain.model.combat
  (:require [clojure.string]
            [empire.units.config :as units-config]
            [empire.units.ships :as ships]))

(defmulti format-combat-log
  (fn [& _]
    :default))

(defmulti format-combat-status
  (fn [& _]
    :default))

(defmulti fight-round
  (fn [& _]
    :default))

(defmulti resolve-combat
  (fn [& _]
    :default))

(defn- unit-name
  [unit-type]
  (-> unit-type name clojure.string/capitalize))

(defn- strength-for
  [unit-type]
  (case unit-type
    :army units-config/army-strength
    :fighter units-config/fighter-strength
    :satellite units-config/satellite-strength
    :transport units-config/transport-strength
    :carrier units-config/carrier-strength
    (ships/config unit-type :strength)))

(defn- display-char-for
  [unit-type]
  (case unit-type
    :army units-config/army-display-char
    :fighter units-config/fighter-display-char
    :satellite units-config/satellite-display-char
    :transport units-config/transport-display-char
    :carrier units-config/carrier-display-char
    (ships/config unit-type :display-char)))

(defn- format-log-entry
  [entry attacker-type defender-type]
  (let [unit-char (if (= :defender (:hit entry))
                    (clojure.string/lower-case (display-char-for defender-type))
                    (clojure.string/upper-case (display-char-for attacker-type)))]
    (str unit-char "-" (:damage entry))))

(defn- summarize-damage
  [log]
  (reduce (fn [{:keys [attacker defender]} entry]
            (if (= :attacker (:hit entry))
              {:attacker (+ attacker (:damage entry))
               :defender defender}
              {:attacker attacker
               :defender (+ defender (:damage entry))}))
          {:attacker 0 :defender 0}
          log))

(defmethod format-combat-log :default
  [log attacker-type defender-type winner]
  (let [entries (map #(format-log-entry % attacker-type defender-type) log)
        exchange-str (clojure.string/join "," entries)
        loser-type (if (= winner :attacker) defender-type attacker-type)
        loser-name (unit-name loser-type)]
    (str exchange-str ". " loser-name " destroyed.")))

(defmethod format-combat-status :default
  [log attacker-type defender-type winner]
  (let [attacker-name (unit-name attacker-type)
        defender-name (unit-name defender-type)
        {:keys [attacker defender]} (summarize-damage log)]
    (str "Battle: "
         (format-combat-log log attacker-type defender-type winner)
         " Damage: "
         attacker-name " lost " attacker ", "
         defender-name " lost " defender ".")))

(defmethod fight-round :default
  [attacker defender]
  (if (< (rand) 0.5)
    (let [damage (strength-for (:type attacker))]
      [attacker (update defender :hits - damage) {:hit :defender :damage damage}])
    (let [damage (strength-for (:type defender))]
      [(update attacker :hits - damage) defender {:hit :attacker :damage damage}])))

(defmethod resolve-combat :default
  [attacker defender]
  (loop [a attacker d defender log []]
    (let [[new-a new-d log-entry] (fight-round a d)
          new-log (conj log log-entry)]
      (cond
        (<= (:hits new-d) 0) {:winner :attacker :survivor new-a :log new-log}
        (<= (:hits new-a) 0) {:winner :defender :survivor new-d :log new-log}
        :else (recur new-a new-d new-log)))))
