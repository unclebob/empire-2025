;; mutation-tested: no
(ns empire.domain.ai.threat-policy)

(def fighter-response-count 4)
(def ship-response-count 2)
(def fighter-sweep-rounds 10)
(def ship-scout-rounds 10)
(def threat-radius 5)

(def enemy-ship-types
  #{:patrol-boat :destroyer :submarine :transport :carrier :battleship})

(defn detection-trigger
  [game-cell]
  (let [unit (:contents game-cell)
        player-unit-type? (fn [t] (and unit (= :player (:owner unit)) (= t (:type unit))))
        player-ship? (fn [] (and unit (= :player (:owner unit)) (enemy-ship-types (:type unit))))
        player-city? (fn [] (and (= :city (:type game-cell)) (= :player (:city-status game-cell))))]
    (cond
      (player-unit-type? :fighter) :fighter-detected
      (player-ship?) :ship-detected
      (player-unit-type? :army) :major-invasion-trigger
      (player-city?) :major-invasion-trigger
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
