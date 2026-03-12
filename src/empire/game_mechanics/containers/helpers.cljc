(ns empire.game-mechanics.containers.helpers
  (:require [empire.config.units.dispatcher :as dispatcher]))

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:00:30.492397-05:00", :module-hash "-1261388013", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "-1349476459"} {:id "defn/get-count", :kind "defn", :line 4, :end-line 6, :hash "-312362946"} {:id "defn/get-awake-count", :kind "defn", :line 8, :end-line 10, :hash "87369032"} {:id "defn/has-awake?", :kind "defn", :line 12, :end-line 14, :hash "316184489"} {:id "defn/add-unit", :kind "defn", :line 16, :end-line 18, :hash "380493756"} {:id "defn/add-awake-unit", :kind "defn", :line 20, :end-line 24, :hash "-2026023306"} {:id "defn/remove-awake-unit", :kind "defn", :line 26, :end-line 30, :hash "1135063129"} {:id "defn/wake-all", :kind "defn", :line 32, :end-line 34, :hash "-1981059247"} {:id "defn/sleep-all", :kind "defn", :line 36, :end-line 38, :hash "-2118055070"} {:id "defn/full?", :kind "defn", :line 40, :end-line 42, :hash "1062641169"} {:id "defn/transport-with-armies?", :kind "defn", :line 44, :end-line 47, :hash "-785264170"} {:id "defn/transport-at-beach?", :kind "defn", :line 49, :end-line 54, :hash "-765665612"} {:id "defn/carrier-with-fighters?", :kind "defn", :line 56, :end-line 59, :hash "1493046611"} {:id "defn/has-awake-carrier-fighter?", :kind "defn", :line 61, :end-line 64, :hash "1311087669"} {:id "defn/has-awake-army-aboard?", :kind "defn", :line 66, :end-line 69, :hash "-127221660"} {:id "defn/blinking-contained-unit", :kind "defn", :line 71, :end-line 77, :hash "-92254159"} {:id "defn-/has-unit-contents?", :kind "defn-", :line 79, :end-line 80, :hash "-1262839957"} {:id "defn-/awake-contents?", :kind "defn-", :line 82, :end-line 83, :hash "-1654519459"} {:id "defn/normal-display-unit", :kind "defn", :line 85, :end-line 92, :hash "612991802"} {:id "defn/add-ship-to-shipyard", :kind "defn", :line 96, :end-line 98, :hash "-1595084627"} {:id "defn/remove-ship-from-shipyard", :kind "defn", :line 100, :end-line 105, :hash "1893133989"} {:id "defn/get-shipyard-ships", :kind "defn", :line 107, :end-line 109, :hash "-574195843"} {:id "defn/repair-ship", :kind "defn", :line 111, :end-line 115, :hash "2100738170"} {:id "defn/ship-fully-repaired?", :kind "defn", :line 117, :end-line 119, :hash "614792750"} {:id "defn/ship-can-dock?", :kind "defn", :line 121, :end-line 130, :hash "-667679379"}]}
;; clj-mutate-manifest-end
