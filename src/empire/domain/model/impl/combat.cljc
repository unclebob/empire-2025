;; mutation-tested: no
(ns empire.domain.model.impl.combat
  (:require [clojure.string]
            [empire.domain.model.combat :as combat]
            [empire.units.dispatcher :as dispatcher]))

(defn- unit-name
  [unit-type]
  (-> unit-type name clojure.string/capitalize))

(defn- format-log-entry
  [entry attacker-type defender-type]
  (let [unit-char (if (= :defender (:hit entry))
                    (clojure.string/lower-case (dispatcher/display-char defender-type))
                    (clojure.string/upper-case (dispatcher/display-char attacker-type)))]
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

(defmethod combat/format-combat-log :default
  [log attacker-type defender-type winner]
  (let [entries (map #(format-log-entry % attacker-type defender-type) log)
        exchange-str (clojure.string/join "," entries)
        loser-type (if (= winner :attacker) defender-type attacker-type)
        loser-name (unit-name loser-type)]
    (str exchange-str ". " loser-name " destroyed.")))

(defmethod combat/format-combat-status :default
  [log attacker-type defender-type winner]
  (let [attacker-name (unit-name attacker-type)
        defender-name (unit-name defender-type)
        {:keys [attacker defender]} (summarize-damage log)]
    (str "Battle: "
         (combat/format-combat-log log attacker-type defender-type winner)
         " Damage: "
         attacker-name " lost " attacker ", "
         defender-name " lost " defender ".")))

(defmethod combat/fight-round :default
  [attacker defender]
  (if (< (rand) 0.5)
    (let [damage (dispatcher/strength (:type attacker))]
      [attacker (update defender :hits - damage) {:hit :defender :damage damage}])
    (let [damage (dispatcher/strength (:type defender))]
      [(update attacker :hits - damage) defender {:hit :attacker :damage damage}])))

(defmethod combat/resolve-combat :default
  [attacker defender]
  (loop [a attacker d defender log []]
    (let [[new-a new-d log-entry] (combat/fight-round a d)
          new-log (conj log log-entry)]
      (cond
        (<= (:hits new-d) 0) {:winner :attacker :survivor new-a :log new-log}
        (<= (:hits new-a) 0) {:winner :defender :survivor new-d :log new-log}
        :else (recur new-a new-d new-log)))))

(defn load-methods!
  []
  true)
