(ns empire.computer.ship
  "Computer ship module - facade delegating to sub-modules."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.lake-naval :as lake-naval]
            [empire.computer.ship-carrier :as carrier]
            [empire.computer.ship-carrier-group :as carrier-group]
            [empire.computer.ship-core :as ship-core]
            [empire.computer.ship-escort :as escort]
            [empire.computer.ship-patrol :as patrol]
            [empire.computer.movement :as computer-movement]
            [empire.computer.threat-response :as threat-response]))


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
    (core/move-unit-to pos rp)
    (computer-movement/update-cell-visibility! pos :computer)
    (computer-movement/update-cell-visibility! rp :computer)))

(defn- try-attack-adjacent [pos]
  (when-let [ep (ship-core/find-adjacent-enemy-ship pos)]
    (ship-core/attack-enemy pos ep)))

(defn- major-invasion-neighbor-target
  [pos]
  (first (for [n (core/get-neighbors pos)
               :let [target (:contents (get-in (sa/current-world) n))]
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
      (if (> (core/distance pos transport-pos) 2)
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
    (threat-response/process-ship-threat pos ship-type unit)
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
      (let [world (sa/current-world)
            lake-cells-set (lake-naval/lake-cells (sa/read-state :computer-map)
                                                  (sa/read-state :lake-max-cells))]
        (if-let [step (lake-naval/retreat-step-from-shore world lake-cells-set pos)]
          (do
            (when (core/move-unit-to pos step)
              (when (lake-naval/deep-water? (sa/current-world) step)
                (sa/update-world! assoc-in (conj step :contents :mode) :sentry)))
            true)
          (do
            (sa/update-world! assoc-in (conj pos :contents :mode) :sentry)
            true))))))

(defn process-ship
  "Processes a computer ship using VMS Empire style logic.
   Returns nil after processing."
  [pos ship-type]
  (let [unit (:contents (get-in (sa/current-world) pos))]
    (when (and unit
               (= :computer (:owner unit))
               (= ship-type (:type unit)))
      (when-not (maybe-handle-lake-ship pos unit)
        (dispatch-ship-action pos ship-type unit))))
  nil)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:58:13.809014-05:00", :module-hash "-1633408502", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 12, :hash "-1105322947"} {:id "def/get-passable-sea-neighbors", :kind "def", :line 17, :end-line 17, :hash "354406633"} {:id "def/attack-enemy", :kind "def", :line 18, :end-line 18, :hash "1492911891"} {:id "def/move-toward", :kind "def", :line 19, :end-line 19, :hash "-1168076134"} {:id "def/patrol-crawl-step", :kind "def", :line 23, :end-line 23, :hash "1461930442"} {:id "def/patrol-explore-step", :kind "def", :line 24, :end-line 24, :hash "-491577945"} {:id "def/compute-distant-city-pairs", :kind "def", :line 28, :end-line 28, :hash "1380496625"} {:id "def/update-distant-city-pairs!", :kind "def", :line 29, :end-line 29, :hash "535834152"} {:id "def/find-reserved-pairs", :kind "def", :line 30, :end-line 30, :hash "1332278496"} {:id "def/find-unreserved-pair", :kind "def", :line 31, :end-line 31, :hash "2044228579"} {:id "def/find-position-between-cities", :kind "def", :line 32, :end-line 32, :hash "-1871859165"} {:id "def/find-refueling-sites", :kind "def", :line 33, :end-line 33, :hash "1583926520"} {:id "def/find-carrier-position", :kind "def", :line 34, :end-line 34, :hash "1020354055"} {:id "def/orbit-ring", :kind "def", :line 35, :end-line 35, :hash "-220350104"} {:id "defn-/try-dock", :kind "defn-", :line 39, :end-line 41, :hash "-4257873"} {:id "defn-/try-retreat", :kind "defn-", :line 43, :end-line 47, :hash "807061798"} {:id "defn-/try-attack-adjacent", :kind "defn-", :line 49, :end-line 51, :hash "-1110635778"} {:id "defn-/try-major-invasion-attack", :kind "defn-", :line 53, :end-line 62, :hash "-319263656"} {:id "defn-/try-escort", :kind "defn-", :line 64, :end-line 73, :hash "-1635390688"} {:id "defn-/try-escort-transport", :kind "defn-", :line 75, :end-line 80, :hash "456773294"} {:id "defn-/try-hunt-player-ship", :kind "defn-", :line 82, :end-line 84, :hash "-1171103118"} {:id "defn-/dispatch-ship-action", :kind "defn-", :line 86, :end-line 107, :hash "-1544480715"} {:id "defn-/maybe-handle-lake-ship", :kind "defn-", :line 109, :end-line 125, :hash "-1708198300"} {:id "defn/process-ship", :kind "defn", :line 127, :end-line 137, :hash "-1168961029"}]}
;; clj-mutate-manifest-end
