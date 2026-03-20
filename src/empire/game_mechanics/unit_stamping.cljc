;; mutation-tested: no
(ns empire.game-mechanics.unit-stamping
  "Unit stamping: applies initial fields to newly produced units."
  (:require [empire.config.ai :as ai]
            [empire.state.api :as sa]))

(defn- next-id!
  [k]
  (let [id (or (sa/read-state k) 1)]
    (sa/write-state! k (inc id))
    id))

(defn ensure-computer-unit-id
  [unit]
  (if (and (= :computer (:owner unit))
           (nil? (:computer-unit-id unit)))
    (assoc unit :computer-unit-id (next-id! :next-computer-unit-id))
    unit))

(defn- apply-computer-satellite-direction
  [unit]
  (if (and (= :satellite (:type unit)) (= :computer (:owner unit)))
    (assoc unit :direction (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]))
    unit))

(defn- apply-computer-transport-fields
  [unit]
  (if (and (= :transport (:type unit)) (= :computer (:owner unit)))
    (let [id (next-id! :next-transport-id)]
      (assoc unit :transport-mission :sail-to-load
                  :transport-id id
                  :army-count 0))
    unit))

(defn- apply-country-id
  [unit cell]
  (if (and (#{:army :transport :fighter :patrol-boat} (:type unit)) (:country-id cell))
    (assoc unit :country-id (:country-id cell))
    unit))

(defn- apply-patrol-fields
  [unit cell]
  (if (and (= :patrol-boat (:type unit))
           (= :computer (:city-status cell))
           (:country-id cell))
    (assoc unit :patrol-mode :crawling)
    unit))

(defn- apply-carrier-fields
  [unit]
  (if (and (= :carrier (:type unit)) (= :computer (:owner unit)))
    (let [id (next-id! :next-carrier-id)]
      (assoc unit :carrier-mode :positioning
                  :carrier-id id
                  :group-battleship-id nil
                  :group-submarine-ids []))
    unit))

(defn- apply-escort-fields
  [unit]
  (if (and (#{:battleship :submarine} (:type unit)) (= :computer (:owner unit)))
    (let [id (next-id! :next-escort-id)]
      (assoc unit :escort-id id :escort-mode :seeking))
    unit))

(defn- apply-destroyer-fields
  [unit]
  (if (and (= :destroyer (:type unit)) (= :computer (:owner unit)))
    (let [id (next-id! :next-destroyer-id)]
      (assoc unit :destroyer-id id :escort-mode :seeking))
    unit))

(defn stamp-computer-fields
  "Applies computer-specific initial fields when stamping a produced unit."
  [unit cell]
  (-> unit
      (ensure-computer-unit-id)
      (apply-computer-satellite-direction)
      (apply-computer-transport-fields)
      (apply-destroyer-fields)
      (apply-carrier-fields)
      (apply-escort-fields)
      (apply-country-id cell)
      (apply-patrol-fields cell)))

(defn backfill-missing-computer-unit-ids!
  []
  (let [world (sa/current-world)]
    (doseq [row (range (count world))
            col (range (count (first world)))
            :let [unit (get-in world [row col :contents])]
            :when (and unit
                       (= :computer (:owner unit))
                       (nil? (:computer-unit-id unit)))]
      (sa/update-world! update-in [row col :contents] ensure-computer-unit-id))))

(defn- country-coastal-cells-explored? [country-id]
  (get-in (or (sa/read-state :country-stats) {}) [country-id :coastal-explored?] true))

(defn- country-coastal-city-count [country-id]
  (count (get-in (or (sa/read-state :country-stats) {}) [country-id :coastal-city-positions] #{})))

(defn apply-coast-walk-fields
  "Optionally assigns coast-walk mode to newly produced computer armies."
  [unit item cell coords]
  (let [country-id (:country-id cell)
        {:keys [coast-walk-limit]} (ai/opening-exploration-profile
                                    (country-coastal-city-count country-id))]
    (if (and (= item :army)
             (= (:city-status cell) :computer)
             country-id
             (< (get (sa/read-state :coast-walkers-produced) country-id 0) coast-walk-limit)
             (not (country-coastal-cells-explored? country-id)))
      (let [produced (get (sa/read-state :coast-walkers-produced) country-id 0)
            direction (if (even? produced) :clockwise :counter-clockwise)]
        (sa/write-state! :coast-walkers-produced
                         (update (or (sa/read-state :coast-walkers-produced) {})
                                 country-id (fnil inc 0)))
        (assoc unit :mode :coast-walk
                    :coast-direction direction
                    :coast-start coords
                    :coast-visited [coords]))
      unit)))

(defn apply-random-explore-fields
  "Optionally assigns random-explore mode to newly produced computer armies."
  [unit item cell coords]
  (let [{:keys [random-explore-chance]} (ai/opening-exploration-profile
                                         (country-coastal-city-count (:country-id cell)))]
    (if (and (= item :army)
             (= :computer (:owner unit))
             (not= :coast-walk (:mode unit))
             (:country-id cell)
             (< (rand) random-explore-chance))
      (assoc unit :mode :random-explore
                  :random-explore-direction
                  (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]))
      unit)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-13T15:24:22.583689-05:00", :module-hash "1019372859", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 4, :hash "837172622"} {:id "defn-/next-id!", :kind "defn-", :line 6, :end-line 10, :hash "-78904397"} {:id "defn-/apply-computer-satellite-direction", :kind "defn-", :line 12, :end-line 16, :hash "-382100866"} {:id "defn-/apply-computer-transport-fields", :kind "defn-", :line 18, :end-line 25, :hash "-1980935197"} {:id "defn-/apply-country-id", :kind "defn-", :line 27, :end-line 31, :hash "-1622960732"} {:id "defn-/apply-patrol-fields", :kind "defn-", :line 33, :end-line 39, :hash "-31215873"} {:id "defn-/apply-carrier-fields", :kind "defn-", :line 41, :end-line 49, :hash "2075914662"} {:id "defn-/apply-escort-fields", :kind "defn-", :line 51, :end-line 56, :hash "1647652015"} {:id "defn-/apply-destroyer-fields", :kind "defn-", :line 58, :end-line 63, :hash "970361077"} {:id "defn/stamp-computer-fields", :kind "defn", :line 65, :end-line 75, :hash "-800983818"}]}
;; clj-mutate-manifest-end
