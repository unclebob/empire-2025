;; mutation-tested: no
(ns empire.domain.model.combat
  (:require [clojure.string]
            [empire.units.dispatcher :as dispatcher]))

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

(defn- summarize-damage
  "Returns total hit loss for attacker/defender from combat log entries."
  [log]
  (reduce (fn [{:keys [attacker defender]} entry]
            (if (= :attacker (:hit entry))
              {:attacker (+ attacker (:damage entry))
               :defender defender}
              {:attacker attacker
               :defender (+ defender (:damage entry))}))
          {:attacker 0 :defender 0}
          log))

(defn format-combat-status
  "Formats status-line combat text including battle fact and damage totals."
  [log attacker-type defender-type winner]
  (let [attacker-name (unit-name attacker-type)
        defender-name (unit-name defender-type)
        {:keys [attacker defender]} (summarize-damage log)]
    (str "Battle: "
         (format-combat-log log attacker-type defender-type winner)
         " Damage: "
         attacker-name " lost " attacker ", "
         defender-name " lost " defender ".")))

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
