;; mutation-tested: 2026-02-26
(ns empire.containers.helpers
  (:require [empire.units.dispatcher :as dispatcher]))

(defn get-count
  [entity count-key]
  (get entity count-key 0))

(defn get-awake-count
  [entity awake-key]
  (get entity awake-key 0))

(defn has-awake?
  [entity awake-key]
  (pos? (get entity awake-key 0)))

(defn add-unit
  [entity count-key]
  (update entity count-key (fnil inc 0)))

(defn add-awake-unit
  [entity count-key awake-key]
  (-> entity
      (update count-key (fnil inc 0))
      (update awake-key (fnil inc 0))))

(defn remove-awake-unit
  [entity count-key awake-key]
  (-> entity
      (update count-key (fnil dec 0))
      (update awake-key (fnil dec 0))))

(defn wake-all
  [entity count-key awake-key]
  (assoc entity awake-key (get entity count-key 0)))

(defn sleep-all
  [entity awake-key]
  (assoc entity awake-key 0))

(defn full?
  [entity count-key capacity]
  (>= (get entity count-key 0) capacity))

(defn transport-with-armies?
  [contents]
  (and (= (:type contents) :transport)
       (pos? (:army-count contents 0))))

(defn transport-at-beach?
  [contents]
  (and (= (:type contents) :transport)
       (pos? (:army-count contents 0))
       (or (#{:transport-at-beach :found-a-bay} (:reason contents))
           (and (= :player (:owner contents)) (= :awake (:mode contents)) (nil? (:reason contents))))))

(defn carrier-with-fighters?
  [contents]
  (and (= (:type contents) :carrier)
       (pos? (get-count contents :fighter-count))))

(defn has-awake-carrier-fighter?
  [contents]
  (and (= (:type contents) :carrier)
       (has-awake? contents :awake-fighters)))

(defn has-awake-army-aboard?
  [contents]
  (and (= (:type contents) :transport)
       (has-awake? contents :awake-armies)))

(defn blinking-contained-unit
  [has-awake-airport? has-awake-carrier? has-awake-army?]
  (cond
    has-awake-airport? {:type :fighter :mode :awake}
    has-awake-carrier? {:type :fighter :mode :awake}
    has-awake-army? {:type :army :mode :awake}
    :else nil))

(defn- has-unit-contents? [contents]
  (and contents (:type contents)))

(defn- awake-contents? [contents]
  (and (has-unit-contents? contents) (= (:mode contents) :awake)))

(defn normal-display-unit
  [_cell contents has-awake-airport? has-any-airport?]
  (cond
    (awake-contents? contents) contents
    has-awake-airport? {:type :fighter :mode :awake}
    (has-unit-contents? contents) contents
    has-any-airport? {:type :fighter :mode :sentry}
    :else nil))

;; Shipyard helpers

(defn add-ship-to-shipyard
  [city ship-type hits]
  (update city :shipyard (fnil conj []) {:type ship-type :hits hits}))

(defn remove-ship-from-shipyard
  [city index]
  (let [shipyard (:shipyard city [])
        new-shipyard (vec (concat (subvec shipyard 0 index)
                                  (subvec shipyard (inc index))))]
    (assoc city :shipyard new-shipyard)))

(defn get-shipyard-ships
  [city]
  (get city :shipyard []))

(defn repair-ship
  [ship]
  (let [max-hits (dispatcher/hits (:type ship))
        new-hits (min (inc (:hits ship)) max-hits)]
    (assoc ship :hits new-hits)))

(defn ship-fully-repaired?
  [ship]
  (= (:hits ship) (dispatcher/hits (:type ship))))

(defn ship-can-dock?
  [unit cell]
  (and (= :city (:type cell))
       (dispatcher/naval-unit? (:type unit))
       (< (:hits unit) (dispatcher/hits (:type unit)))
       (= (:owner unit)
          (case (:city-status cell)
            :player :player
            :computer :computer
            nil))))
