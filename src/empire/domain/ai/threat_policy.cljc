;; mutation-tested: no
(ns empire.domain.ai.threat-policy)

(def fighter-response-count 4)
(def ship-response-count 2)
(def fighter-sweep-rounds 10)
(def ship-scout-rounds 10)
(def threat-radius 5)

(def enemy-ship-types
  #{:patrol-boat :destroyer :submarine :transport :carrier :battleship})

(defn- player-unit-type
  [game-cell]
  (let [unit (:contents game-cell)]
    (when (= :player (:owner unit))
      (:type unit))))

(defn- player-city?
  [game-cell]
  (and (= :city (:type game-cell))
       (= :player (:city-status game-cell))))

(defn detection-trigger
  [game-cell]
  (let [unit-type (player-unit-type game-cell)]
    (cond
      (= :fighter unit-type) :fighter-detected
      (enemy-ship-types unit-type) :ship-detected
      (= :army unit-type) :major-invasion-trigger
      (player-city? game-cell) :major-invasion-trigger
      :else nil)))

(defn fighter-sweep-mission
  [center]
  {:threat-mission :fighter-sweep
   :threat-center center
   :threat-radius threat-radius
   :threat-rounds-left fighter-sweep-rounds})

(defn sea-scout-mission
  [center]
  {:threat-mission :sea-scout
   :threat-center center
   :threat-radius threat-radius
   :threat-rounds-left ship-scout-rounds})

(defn dec-threat-rounds
  [unit]
  (if-let [left (:threat-rounds-left unit)]
    (let [next-left (dec left)]
      (if (pos? next-left)
        (assoc unit :threat-rounds-left next-left)
        (dissoc unit :threat-mission :threat-center :threat-radius :threat-rounds-left)))
    unit))
