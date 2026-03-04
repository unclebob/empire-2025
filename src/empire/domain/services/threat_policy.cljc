;; mutation-tested: no
(ns empire.domain.services.threat-policy)

(defmulti fighter-response-count (fn [& _] :default))
(defmulti ship-response-count (fn [& _] :default))
(defmulti fighter-sweep-rounds (fn [& _] :default))
(defmulti ship-scout-rounds (fn [& _] :default))
(defmulti threat-radius (fn [& _] :default))

(def ^:private enemy-ship-types
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

(defmulti detection-trigger (fn [& _] :default))

(defmulti fighter-sweep-mission (fn [& _] :default))

(defmulti sea-scout-mission (fn [& _] :default))

(defmulti dec-threat-rounds (fn [& _] :default))

(defmethod fighter-response-count :default [] 4)
(defmethod ship-response-count :default [] 2)
(defmethod fighter-sweep-rounds :default [] 10)
(defmethod ship-scout-rounds :default [] 10)
(defmethod threat-radius :default [] 5)

(defmethod detection-trigger :default
  [game-cell]
  (let [unit-type (player-unit-type game-cell)]
    (cond
      (= :fighter unit-type) :fighter-detected
      (enemy-ship-types unit-type) :ship-detected
      (= :army unit-type) :major-invasion-trigger
      (player-city? game-cell) :major-invasion-trigger
      :else nil)))

(defmethod fighter-sweep-mission :default
  [center]
  {:threat-mission :fighter-sweep
   :threat-center center
   :threat-radius (threat-radius)
   :threat-rounds-left (fighter-sweep-rounds)})

(defmethod sea-scout-mission :default
  [center]
  {:threat-mission :sea-scout
   :threat-center center
   :threat-radius (threat-radius)
   :threat-rounds-left (ship-scout-rounds)})

(defmethod dec-threat-rounds :default
  [unit]
  (if-let [left (:threat-rounds-left unit)]
    (let [next-left (dec left)]
      (if (pos? next-left)
        (assoc unit :threat-rounds-left next-left)
        (dissoc unit :threat-mission :threat-center :threat-radius :threat-rounds-left)))
    unit))
