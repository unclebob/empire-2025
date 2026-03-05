;; mutation-tested: no
(ns empire.application.unit-stamping
  "Application port for unit stamping decisions."
  (:require [empire.application.state-access :as sa]))

(defn- next-id!
  [k]
  (let [id (or (sa/read-state k) 1)]
    (sa/write-state! k (inc id))
    id))

(defn- apply-computer-satellite-direction
  [unit]
  (if (and (= :satellite (:type unit)) (= :computer (:owner unit)))
    (assoc unit :direction (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]))
    unit))

(defn- apply-computer-transport-fields
  [unit]
  (if (and (= :transport (:type unit)) (= :computer (:owner unit)))
    (let [id (next-id! :next-transport-id)]
      (assoc unit :transport-mission :loading
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

(defn- country-coastal-cells-explored?
  [country-id]
  (if-let [f (:country-coastal-explored? (sa/state-ctx))]
    (let [result (f country-id)]
      (if (nil? result)
        (get-in (or (sa/read-state :country-stats) {})
                [country-id :coastal-explored?]
                true)
        result))
    (get-in (or (sa/read-state :country-stats) {})
            [country-id :coastal-explored?]
            true)))

(defn stamp-computer-fields
  "Applies computer-specific initial fields when stamping a produced unit."
  [unit cell]
  (-> unit
      (apply-computer-satellite-direction)
      (apply-computer-transport-fields)
      (apply-destroyer-fields)
      (apply-carrier-fields)
      (apply-escort-fields)
      (apply-country-id cell)
      (apply-patrol-fields cell)))

(defn apply-coast-walk-fields
  "Optionally assigns coast-walk mode to newly produced computer armies."
  [unit item cell coords]
  (if (and (= item :army)
           (= (:city-status cell) :computer)
           (:country-id cell)
           (< (get (sa/read-state :coast-walkers-produced) (:country-id cell) 0) 2)
           (not (country-coastal-cells-explored? (:country-id cell))))
    (let [country-id (:country-id cell)
          produced (get (sa/read-state :coast-walkers-produced) country-id 0)
          direction (if (even? produced) :clockwise :counter-clockwise)]
      (sa/write-state! :coast-walkers-produced
                            (update (or (sa/read-state :coast-walkers-produced) {})
                                    country-id (fnil inc 0)))
      (assoc unit :mode :coast-walk
                  :coast-direction direction
                  :coast-start coords
                  :coast-visited [coords]))
    unit))

(defn apply-random-explore-fields
  "Optionally assigns random-explore mode to newly produced computer armies."
  [unit item cell]
  (if (and (= item :army)
           (= :computer (:owner unit))
           (not= :coast-walk (:mode unit))
           (:country-id cell)
           (< (rand) 1/3))
    (assoc unit :mode :random-explore
                :random-explore-direction
                (rand-nth [[-1 -1] [-1 0] [-1 1] [0 -1] [0 1] [1 -1] [1 0] [1 1]]))
    unit))
