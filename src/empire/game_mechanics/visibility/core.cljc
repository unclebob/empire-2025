(ns empire.game-mechanics.visibility.core
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.debug.logging :as debug-logging]
            [empire.config.units.dispatcher :as dispatcher]))

;; -- state access helpers (shared by all visibility sub-modules) --

(defn update-game-map!
  [f & args]
  (apply sa/update-world! f args))

(defn current-world
  []
  (sa/current-world))

(defn read-runtime-state
  [k]
  (sa/read-state k))

(defn write-runtime-state!
  [k v]
  (sa/write-state! k v))

(defn merge-continents!
  [stamp-id existing-cid]
  (sa/merge-continents! stamp-id existing-cid))

(defn read-visible-map
  [visible-map-source]
  (if (keyword? visible-map-source)
    (read-runtime-state visible-map-source)
    @visible-map-source))

(defn write-visible-map!
  [visible-map-source visible-map]
  (if (keyword? visible-map-source)
    (write-runtime-state! visible-map-source visible-map)
    (reset! visible-map-source visible-map)))

(defn update-visible-map!
  [visible-map-source f & args]
  (write-visible-map! visible-map-source (apply f (read-visible-map visible-map-source) args)))

;; -- ownership predicates --

(defn- owned-by?
  [cell owner]
  (or (= (:city-status cell) owner)
      (= (:owner (:contents cell)) owner)))

(defn is-players?
  [cell]
  (owned-by? cell :player))

(defn is-computers?
  [cell]
  (owned-by? cell :computer))

;; -- visibility helpers --

(defn was-unexplored?
  [visible-map row col]
  (let [vis-cell (get-in visible-map [row col])]
    (or (nil? vis-cell)
        (= :unexplored (:type vis-cell)))))

(defn cell-visibility-radius
  [cell]
  (if-let [unit-type (:type (:contents cell))]
    (dispatcher/visibility-radius unit-type)
    1))

(defn in-bounds?
  [row col height width]
  (and (>= row 0) (< row height)
       (>= col 0) (< col width)))

(defn should-stamp-country?
  [unit]
  (and unit
       (= :army (:type unit))
       (= :computer (:owner unit))
       (:country-id unit)))

(defn visible-map-key-for
  [owner]
  (if (= owner :player) :player-map :computer-map))

;; -- reveal logic --

(defn reveal-surrounding-cells!
  [result game-map i j height width radius]
  (let [coords (for [row (range (max 0 (- i radius)) (min height (+ i radius 1)))
                     col (range (max 0 (- j radius)) (min width (+ j radius 1)))]
                 [row col])]
    (reduce (fn [r [row col]]
              (let [cell ((game-map row) col)]
                (assoc! r row (assoc! (r row) col cell))))
            result
            coords)))

(defn- process-map-cells
  [result game-map ownership-predicate height width]
  (let [coords (for [i (range height)
                     j (range width)]
                 [i j])]
    (reduce (fn [res [i j]]
              (let [cell ((game-map i) j)]
                (if (ownership-predicate cell)
                  (reveal-surrounding-cells! res game-map i j height width
                                             (cell-visibility-radius cell))
                  res)))
            result
            coords)))

;; -- core update algorithm --

(defn update-combatant-map-state
  "Pure/state-level variant of combatant map update.
   Returns an updated visible-map from the provided visible-map and game-map."
  [visible-map owner game-map]
  (when visible-map
    (let [ownership-predicate (if (= owner :player) is-players? is-computers?)
          height (count game-map)
          width (count (first game-map))
          transient-map (transient (mapv transient visible-map))
          updated (process-map-cells transient-map game-map ownership-predicate height width)]
      (mapv persistent! (persistent! updated)))))

(defn- visible-cell-with-production
  [row col game-cell]
  (let [production-entry (when (= :city (:type game-cell))
                           (get (read-runtime-state :production) [row col]))]
    (cond-> game-cell
      (and (map? production-entry) (:item production-entry))
      (assoc :known-production production-entry))))

(defn- stamp-revealed-country!
  [row col game-cell stamp-id visible-map]
  (when (and stamp-id
             (was-unexplored? visible-map row col)
             (= :land (:type game-cell)))
    (let [existing-cid (:country-id game-cell)]
      (when (and existing-cid (not= stamp-id existing-cid))
        (merge-continents! stamp-id existing-cid)))
    (update-game-map! assoc-in [row col :country-id] stamp-id)))

(defn reveal-cell!
  [visible-map-source row col game-cell stamp-id visible-map]
  (update-visible-map! visible-map-source assoc-in [row col]
                       (visible-cell-with-production row col game-cell))
  (stamp-revealed-country! row col game-cell stamp-id visible-map))

(defn reveal-and-track!
  [visible-map-source ni nj stamp-id detect-threats? visible-map queue-detection-fn]
  (let [was-unexplored (was-unexplored? visible-map ni nj)
        game-cell (get-in (current-world) [ni nj])]
    (reveal-cell! visible-map-source ni nj game-cell stamp-id visible-map)
    (when was-unexplored
      (debug-logging/record-active-computer-unit-discovery! 1))
    (when (and detect-threats? was-unexplored)
      (queue-detection-fn [ni nj] game-cell))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T11:08:04.809486-05:00", :module-hash "1837511268", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "57074799"} {:id "defn/update-game-map!", :kind "defn", :line 8, :end-line 10, :hash "1484461537"} {:id "defn/current-world", :kind "defn", :line 12, :end-line 14, :hash "-1099101758"} {:id "defn/read-runtime-state", :kind "defn", :line 16, :end-line 18, :hash "1712255867"} {:id "defn/write-runtime-state!", :kind "defn", :line 20, :end-line 22, :hash "620772778"} {:id "defn/merge-continents!", :kind "defn", :line 24, :end-line 26, :hash "-454703343"} {:id "defn/read-visible-map", :kind "defn", :line 28, :end-line 32, :hash "1918429348"} {:id "defn/write-visible-map!", :kind "defn", :line 34, :end-line 38, :hash "-1268197640"} {:id "defn/update-visible-map!", :kind "defn", :line 40, :end-line 42, :hash "685751789"} {:id "defn/is-players?", :kind "defn", :line 46, :end-line 49, :hash "1929138368"} {:id "defn/is-computers?", :kind "defn", :line 51, :end-line 54, :hash "466607438"} {:id "defn/was-unexplored?", :kind "defn", :line 58, :end-line 62, :hash "792784525"} {:id "defn/cell-visibility-radius", :kind "defn", :line 64, :end-line 68, :hash "436151447"} {:id "defn/in-bounds?", :kind "defn", :line 70, :end-line 73, :hash "1253170649"} {:id "defn/should-stamp-country?", :kind "defn", :line 75, :end-line 80, :hash "-770933518"} {:id "defn/visible-map-key-for", :kind "defn", :line 82, :end-line 84, :hash "-1661736486"} {:id "defn/reveal-surrounding-cells!", :kind "defn", :line 88, :end-line 97, :hash "716147634"} {:id "defn-/process-map-cells", :kind "defn-", :line 99, :end-line 111, :hash "-1075363880"} {:id "defn/update-combatant-map-state", :kind "defn", :line 115, :end-line 125, :hash "297751115"} {:id "defn/reveal-cell!", :kind "defn", :line 127, :end-line 136, :hash "-454904434"} {:id "defn/reveal-and-track!", :kind "defn", :line 138, :end-line 146, :hash "605381590"}]}
;; clj-mutate-manifest-end
