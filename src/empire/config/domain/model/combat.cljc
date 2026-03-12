(ns empire.config.domain.model.combat
  (:require [clojure.string]
            [empire.config.units.config :as units-config]
            [empire.config.units.ships :as ships]))

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

(defn format-combat-log
  [log attacker-type defender-type winner]
  (let [entries (map #(format-log-entry % attacker-type defender-type) log)
        exchange-str (clojure.string/join "," entries)
        loser-type (if (= winner :attacker) defender-type attacker-type)
        loser-name (unit-name loser-type)]
    (str exchange-str ". " loser-name " destroyed.")))

(defn format-combat-status
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
  [attacker defender]
  (if (< (rand) 0.5)
    (let [damage (strength-for (:type attacker))]
      [attacker (update defender :hits - damage) {:hit :defender :damage damage}])
    (let [damage (strength-for (:type defender))]
      [(update attacker :hits - damage) defender {:hit :attacker :damage damage}])))

(defn resolve-combat
  [attacker defender]
  (loop [a attacker d defender log []]
    (let [[new-a new-d log-entry] (fight-round a d)
          new-log (conj log log-entry)]
      (cond
        (<= (:hits new-d) 0) {:winner :attacker :survivor new-a :log new-log}
        (<= (:hits new-a) 0) {:winner :defender :survivor new-d :log new-log}
        :else (recur new-a new-d new-log)))))

;; --- Pure combat predicates ---

(def ^:private hostile-city-statuses
  "City statuses that are hostile to the player."
  #{:free :computer})

(defn hostile-city?
  "Returns true if the cell at target-coords in world is a hostile city."
  [world target-coords]
  (let [target-cell (get-in world target-coords)]
    (and (= (:type target-cell) :city)
         (hostile-city-statuses (:city-status target-cell)))))

(defn hostile-unit?
  "Returns true if unit exists and is not owned by owner."
  [unit owner]
  (and unit (not= (:owner unit) owner)))

;; --- Pure world transformations ---

(def ^:private flippable-types
  "Unit types that flip ownership on city conquest (ships and fighters)."
  #{:fighter :transport :patrol-boat :destroyer :submarine :carrier :battleship})

(defn conquer-city-contents-world
  "Pure world transformation: updates city contents upon conquest."
  [game-map city-coords new-owner]
  (let [cell (get-in game-map city-coords)
        contents (:contents cell)
        with-updated-contents
        (cond
          (nil? contents) game-map
          (= :satellite (:type contents)) game-map
          (= :army (:type contents))
          (update-in game-map city-coords dissoc :contents)
          (flippable-types (:type contents))
          (let [flipped (-> contents
                            (assoc :owner new-owner :mode :awake)
                            (dissoc :target :reason))
                flipped (cond-> flipped
                          (= :transport (:type flipped))
                          (assoc :army-count 0 :awake-armies 0)
                          (= :carrier (:type flipped))
                          (assoc :fighter-count 0 :awake-fighters 0))]
            (assoc-in game-map (conj city-coords :contents) flipped))
          :else game-map)]
    (update-in with-updated-contents city-coords dissoc :marching-orders :flight-path)))

(defn apply-fighter-overfly-world
  "Pure world transformation: moves fighter to city as shot-down."
  [game-map fighter-coords city-coords shot-down-fighter]
  (let [fighter-cell (get-in game-map fighter-coords)
        city-cell (get-in game-map city-coords)]
    (-> game-map
        (assoc-in fighter-coords (dissoc fighter-cell :contents))
        (assoc-in city-coords (assoc city-cell :contents shot-down-fighter)))))

(defn apply-attack-world
  "Pure world transformation: removes attacker, places survivor at target."
  [game-map attacker-coords target-coords survivor]
  (-> game-map
      (assoc-in (conj attacker-coords :contents) nil)
      (assoc-in (conj target-coords :contents) survivor)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:59:29.42716-05:00", :module-hash "252402050", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "1048650541"} {:id "defn-/unit-name", :kind "defn-", :line 6, :end-line 8, :hash "1717224002"} {:id "defn-/strength-for", :kind "defn-", :line 10, :end-line 18, :hash "-1559981813"} {:id "defn-/display-char-for", :kind "defn-", :line 20, :end-line 28, :hash "611228276"} {:id "defn-/format-log-entry", :kind "defn-", :line 30, :end-line 35, :hash "1535212448"} {:id "defn-/summarize-damage", :kind "defn-", :line 37, :end-line 46, :hash "1849469563"} {:id "defn/format-combat-log", :kind "defn", :line 48, :end-line 54, :hash "102831498"} {:id "defn/format-combat-status", :kind "defn", :line 56, :end-line 65, :hash "-701495449"} {:id "defn/fight-round", :kind "defn", :line 67, :end-line 73, :hash "-454614533"} {:id "defn/resolve-combat", :kind "defn", :line 75, :end-line 83, :hash "-937115642"} {:id "def/hostile-city-statuses", :kind "def", :line 87, :end-line 89, :hash "-609193175"} {:id "defn/hostile-city?", :kind "defn", :line 91, :end-line 96, :hash "2103341631"} {:id "defn/hostile-unit?", :kind "defn", :line 98, :end-line 101, :hash "-1666723256"} {:id "def/flippable-types", :kind "def", :line 105, :end-line 107, :hash "2050450636"} {:id "defn/conquer-city-contents-world", :kind "defn", :line 109, :end-line 131, :hash "-1585273182"} {:id "defn/apply-fighter-overfly-world", :kind "defn", :line 133, :end-line 140, :hash "135062069"} {:id "defn/apply-attack-world", :kind "defn", :line 142, :end-line 147, :hash "-272239538"}]}
;; clj-mutate-manifest-end
