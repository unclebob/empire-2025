;; mutation-tested: no
(ns empire.game-mechanics.services.threat-policy)

(defn fighter-response-count [] 4)
(defn ship-response-count [] 2)
(defn fighter-sweep-rounds [] 10)
(defn ship-scout-rounds [] 10)
(defn threat-radius [] 5)

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

(defn- army-detection-trigger
  [game-cell]
  (if (:country-id game-cell)
    :country-defense-trigger
    :major-invasion-trigger))

(defn detection-trigger
  [game-cell]
  (let [unit-type (player-unit-type game-cell)]
    (cond
      (= :fighter unit-type) :fighter-detected
      (enemy-ship-types unit-type) :ship-detected
      (= :army unit-type) (army-detection-trigger game-cell)
      (player-city? game-cell) :major-invasion-trigger
      :else nil)))

(defn fighter-sweep-mission
  [center]
  {:threat-mission :fighter-sweep
   :threat-center center
   :threat-radius (threat-radius)
   :threat-rounds-left (fighter-sweep-rounds)})

(defn sea-scout-mission
  [center]
  {:threat-mission :sea-scout
   :threat-center center
   :threat-radius (threat-radius)
   :threat-rounds-left (ship-scout-rounds)})

(defn dec-threat-rounds
  [unit]
  (if-let [left (:threat-rounds-left unit)]
    (let [next-left (dec left)]
      (if (pos? next-left)
        (assoc unit :threat-rounds-left next-left)
        (dissoc unit :threat-mission :threat-center :threat-radius :threat-rounds-left)))
    unit))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:08:38.85411-05:00", :module-hash "2063675757", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line nil, :hash "-565561972"} {:id "defn/fighter-response-count", :kind "defn", :line 4, :end-line nil, :hash "-730736599"} {:id "defn/ship-response-count", :kind "defn", :line 5, :end-line nil, :hash "-143462016"} {:id "defn/fighter-sweep-rounds", :kind "defn", :line 6, :end-line nil, :hash "-1360802506"} {:id "defn/ship-scout-rounds", :kind "defn", :line 7, :end-line nil, :hash "1481175093"} {:id "defn/threat-radius", :kind "defn", :line 8, :end-line nil, :hash "243437669"} {:id "def/enemy-ship-types", :kind "def", :line 10, :end-line nil, :hash "-1772818534"} {:id "defn-/player-unit-type", :kind "defn-", :line 13, :end-line nil, :hash "-294485486"} {:id "defn-/player-city?", :kind "defn-", :line 19, :end-line nil, :hash "717508756"} {:id "defn-/army-detection-trigger", :kind "defn-", :line 24, :end-line nil, :hash "1495061749"} {:id "defn/detection-trigger", :kind "defn", :line 30, :end-line nil, :hash "1322406127"} {:id "defn/fighter-sweep-mission", :kind "defn", :line 40, :end-line nil, :hash "1760838581"} {:id "defn/sea-scout-mission", :kind "defn", :line 47, :end-line nil, :hash "1860854697"} {:id "defn/dec-threat-rounds", :kind "defn", :line 54, :end-line nil, :hash "-609173866"}]}
;; clj-mutate-manifest-end
