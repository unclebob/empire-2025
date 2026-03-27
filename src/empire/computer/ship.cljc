(ns empire.computer.ship
  "Computer ship module - facade delegating to sub-modules."
  (:require [empire.state.api :as sa]
            [empire.computer.ship.lake-naval :as lake-naval]
            [empire.computer.ship.carrier :as carrier]
            [empire.computer.ship.carrier-group :as carrier-group]
            [empire.computer.ship.core :as ship-core]
            [empire.computer.ship.escort :as escort]
            [empire.computer.ship.patrol :as patrol]
            [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.movement :as computer-movement]
            [empire.computer.shared.world-query :as world-query]
            [empire.computer.threat-response-port :as threat-response-port]
            [empire.game-mechanics.visibility :as visibility]))


(defn- computer-unit-at
  [pos]
  (:contents (get-in (sa/read-state :computer-map) pos)))


;; --- Core utility re-exports ---

(def get-passable-sea-neighbors ship-core/get-passable-sea-neighbors)
(def attack-enemy ship-core/attack-enemy)
(def move-toward ship-core/move-toward)

;; --- Patrol re-exports ---

(def patrol-crawl-step patrol/patrol-crawl-step)
(def patrol-explore-step patrol/patrol-explore-step)

;; --- Carrier re-exports ---

(def compute-distant-city-pairs carrier/compute-distant-city-pairs)
(def update-distant-city-pairs! carrier/update-distant-city-pairs!)
(def find-reserved-pairs carrier/find-reserved-pairs)
(def find-unreserved-pair carrier/find-unreserved-pair)
(def find-position-between-cities carrier/find-position-between-cities)
(def find-refueling-sites carrier/find-refueling-sites)
(def find-carrier-position carrier/find-carrier-position)
(def orbit-ring carrier-group/orbit-ring)

;; --- Dispatch helpers ---

(defn- try-dock [pos unit]
  (when-let [city (ship-core/find-adjacent-dock-city pos unit)]
    (ship-core/dock-computer-ship pos city)))

(defn- try-retreat [pos unit]
  (when-let [rp (ship-core/retreat-if-damaged pos unit)]
    (action-resolution/move-unit-to pos rp)
    (computer-movement/update-cell-visibility! pos :computer)
    (computer-movement/update-cell-visibility! rp :computer)))

(defn- try-attack-adjacent [pos]
  (when-let [ep (ship-core/find-adjacent-enemy-ship pos)]
    (ship-core/attack-enemy pos ep)))

(defn- major-invasion-neighbor-target
  [pos]
  (first (for [n (world-query/get-neighbors pos)
               :let [target (:contents (get-in (sa/read-state :computer-map) n))]
               :when (and target
                          (= :player (:owner target))
                          (not= :transport (:type target))
                          (not= :satellite (:type target)))]
           n)))

(defn- try-major-invasion-attack [pos]
  (or (try-attack-adjacent pos)
      (when-let [ep (major-invasion-neighbor-target pos)]
        (ship-core/attack-enemy pos ep))))

(defn- try-escort [pos ship-type unit]
  (when (:escort-mode unit)
    (cond
      (= :destroyer ship-type)
      (or (escort/process-escort-destroyer pos)
          (ship-core/explore-sea pos ship-type))

      (#{:battleship :submarine} ship-type)
      (or (carrier-group/process-carrier-group-escort pos ship-type)
          (ship-core/explore-sea pos ship-type)))))

(defn- try-escort-transport [pos ship-type]
  (when (= :destroyer ship-type)
    (when-let [transport-pos (ship-core/find-nearest-transport pos)]
      (if (> (grid/distance pos transport-pos) 2)
        (ship-core/move-toward pos transport-pos)
        (ship-core/explore-sea pos ship-type)))))

(defn- try-hunt-player-ship [pos]
  (when-let [sighting (ship-core/find-player-ship-sighting pos)]
    (ship-core/move-toward pos sighting)))

(defn- patrol-boat-action
  [pos unit]
  (if (:major-invasion unit)
    (or (try-major-invasion-attack pos)
        (patrol/process-patrol-boat pos))
    (patrol/process-patrol-boat pos)))

(defn- standard-ship-action
  [pos ship-type unit]
  (or (try-dock pos unit)
      (try-retreat pos unit)
      (try-attack-adjacent pos)
      (try-escort pos ship-type unit)
      (try-escort-transport pos ship-type)
      (try-hunt-player-ship pos)
      (ship-core/explore-sea pos ship-type)))

(defn- dispatch-ship-action [pos ship-type unit]
  (cond
    (threat-response-port/process-ship-threat pos ship-type unit)
    true

    (= :patrol-boat ship-type)
    (patrol-boat-action pos unit)

    (and (= :carrier ship-type) (:carrier-mode unit))
    (carrier/process-carrier pos)

    :else
    (standard-ship-action pos ship-type unit)))

(defn- maybe-handle-lake-ship
  [pos unit]
  (if (= :sentry (:mode unit))
    true
    (when (:lake-locked? unit)
      (let [computer-map (sa/read-state :computer-map)
            lake-cells-set (lake-naval/lake-cells computer-map
                                                  (sa/read-state :lake-max-cells))]
        (if-let [step (lake-naval/retreat-step-from-shore computer-map lake-cells-set pos)]
          (do
            (when (action-resolution/move-unit-to pos step)
              (when (lake-naval/deep-water? computer-map step)
                (sa/update-world! assoc-in (conj step :contents :mode) :sentry)
                (visibility/sync-ai-unit-to-computer-map! step)))
            true)
          (do
            (sa/update-world! assoc-in (conj pos :contents :mode) :sentry)
            (visibility/sync-ai-unit-to-computer-map! pos)
            true))))))

(defn- refresh-ship-visibility?
  [ship-type unit]
  (or (= :carrier ship-type)
      (:escort-mode unit)
      (:escort-carrier-id unit)))

(defn process-ship
  "Processes a computer ship using VMS Empire style logic.
   Returns nil after processing."
  [pos ship-type]
  (let [unit (computer-unit-at pos)]
    (when (and unit
               (= :computer (:owner unit))
               (= ship-type (:type unit)))
      (when (refresh-ship-visibility? ship-type unit)
        (computer-movement/update-cell-visibility-with-unit! pos :computer unit))
      (when-not (maybe-handle-lake-ship pos unit)
        (dispatch-ship-action pos ship-type unit))))
  nil)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:21:49.433806-05:00", :module-hash "-498055853", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 15, :hash "-1024098747"} {:id "defn-/computer-unit-at", :kind "defn-", :line 18, :end-line 20, :hash "-1795563674"} {:id "def/get-passable-sea-neighbors", :kind "def", :line 25, :end-line 25, :hash "354406633"} {:id "def/attack-enemy", :kind "def", :line 26, :end-line 26, :hash "1492911891"} {:id "def/move-toward", :kind "def", :line 27, :end-line 27, :hash "-1168076134"} {:id "def/patrol-crawl-step", :kind "def", :line 31, :end-line 31, :hash "1461930442"} {:id "def/patrol-explore-step", :kind "def", :line 32, :end-line 32, :hash "-491577945"} {:id "def/compute-distant-city-pairs", :kind "def", :line 36, :end-line 36, :hash "1380496625"} {:id "def/update-distant-city-pairs!", :kind "def", :line 37, :end-line 37, :hash "535834152"} {:id "def/find-reserved-pairs", :kind "def", :line 38, :end-line 38, :hash "1332278496"} {:id "def/find-unreserved-pair", :kind "def", :line 39, :end-line 39, :hash "2044228579"} {:id "def/find-position-between-cities", :kind "def", :line 40, :end-line 40, :hash "-1871859165"} {:id "def/find-refueling-sites", :kind "def", :line 41, :end-line 41, :hash "1583926520"} {:id "def/find-carrier-position", :kind "def", :line 42, :end-line 42, :hash "1020354055"} {:id "def/orbit-ring", :kind "def", :line 43, :end-line 43, :hash "-220350104"} {:id "defn-/try-dock", :kind "defn-", :line 47, :end-line 49, :hash "-4257873"} {:id "defn-/try-retreat", :kind "defn-", :line 51, :end-line 55, :hash "-1327058512"} {:id "defn-/try-attack-adjacent", :kind "defn-", :line 57, :end-line 59, :hash "-1110635778"} {:id "defn-/major-invasion-neighbor-target", :kind "defn-", :line 61, :end-line 69, :hash "715144783"} {:id "defn-/try-major-invasion-attack", :kind "defn-", :line 71, :end-line 74, :hash "-807434089"} {:id "defn-/try-escort", :kind "defn-", :line 76, :end-line 85, :hash "-1635390688"} {:id "defn-/try-escort-transport", :kind "defn-", :line 87, :end-line 92, :hash "-312380251"} {:id "defn-/try-hunt-player-ship", :kind "defn-", :line 94, :end-line 96, :hash "-1171103118"} {:id "defn-/patrol-boat-action", :kind "defn-", :line 98, :end-line 103, :hash "-1310702182"} {:id "defn-/standard-ship-action", :kind "defn-", :line 105, :end-line 113, :hash "-1061311883"} {:id "defn-/dispatch-ship-action", :kind "defn-", :line 115, :end-line 127, :hash "-293550548"} {:id "defn-/maybe-handle-lake-ship", :kind "defn-", :line 129, :end-line 147, :hash "-1360447952"} {:id "defn-/refresh-ship-visibility?", :kind "defn-", :line 149, :end-line 153, :hash "1943729809"} {:id "defn/process-ship", :kind "defn", :line 155, :end-line 167, :hash "-1906963063"}]}
;; clj-mutate-manifest-end
