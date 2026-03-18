(ns empire.computer.core
  (:require [empire.game-mechanics.services.city-production :as city-production]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.state.api :as sa]
            [empire.game-mechanics.services.combat :as combat]
            [empire.computer.oscillation :as oscillation]
            [empire.game-mechanics.debug.logging :as debug]))

(def neighbor-offsets
  [[-1 -1] [-1 0] [-1 1]
   [0 -1]          [0 1]
   [1 -1]  [1 0]  [1 1]])

(defn neighbors-in-map
  [the-map [r c]]
  (if (and (sequential? the-map) (seq the-map) (sequential? (first the-map)))
    (let [height (count the-map)
          width (count (first the-map))]
      (for [[dr dc] neighbor-offsets
            :let [nr (+ r dr)
                  nc (+ c dc)]
            :when (and (<= 0 nr) (< nr height)
                       (<= 0 nc) (< nc width))]
        [nr nc]))
    []))

(defn adjacent?
  "Returns true if pos1 and pos2 are adjacent (including diagonally)."
  [pos1 pos2]
  (let [[r1 c1] pos1
        [r2 c2] pos2
        dr (Math/abs (- r2 r1))
        dc (Math/abs (- c2 c1))]
    (and (<= dr 1) (<= dc 1) (not (and (zero? dr) (zero? dc))))))

(defn- country-city-producing-armies?
  [city-pos country-id]
  (some (fn [[coords prod]]
          (and (map? prod)
               (= :army (:item prod))
               (not= coords city-pos)
               (let [cell (get-in (sa/current-world) coords)]
                 (and (= :city (:type cell))
                      (= :computer (:city-status cell))
                      (= country-id (:country-id cell))))))
        (sa/read-state :production)))

(defn- update-cell-visibility!
  ([pos owner]
   (visibility/update-cell-visibility pos owner))
  ([pos owner unit]
   (visibility/update-cell-visibility pos owner unit)))

(defn- foreign-territory?
  "Returns true if unit is a computer army with a country-id and the target
   land cell has a different country-id. Cities are always passable."
  [unit to-cell]
  (and (= :army (:type unit))
       (= :computer (:owner unit))
       (:country-id unit)
       (= :land (:type to-cell))
       (:country-id to-cell)
       (not (sa/on-same-continent? (:country-id unit) (:country-id to-cell)))))

(defn get-neighbors
  [pos]
  (neighbors-in-map (sa/read-state :computer-map) pos))

(defn distance
  [[x1 y1] [x2 y2]]
  (+ (Math/abs (- x2 x1)) (Math/abs (- y2 y1))))

(defn chebyshev-distance
  [[r1 c1] [r2 c2]]
  (max (Math/abs (- r2 r1)) (Math/abs (- c2 c1))))

(defn- has-city?
  [owner]
  (let [world (sa/current-world)]
    (boolean
     (some (fn [col]
             (some #(and (= :city (:type %))
                         (= owner (:city-status %)))
                   col))
           world))))

(defn- declare-game-over!
  [message]
  (sa/write-state! :paused true)
  (sa/write-state! :error-message message)
  (sa/write-state! :error-until Long/MAX_VALUE)
  (sa/write-state! :map-to-display :actual-map)
  (sa/write-state! :player-items [])
  (sa/write-state! :computer-items []))

(defn attackable-target?
  [cell]
  (or (and (= (:type cell) :city)
           (#{:player :free} (:city-status cell)))
      (and (:contents cell)
           (= (:owner (:contents cell)) :player)
           (not= :satellite (:type (:contents cell))))))

(defn find-visible-cities
  [status-pred]
  (let [comp-map (sa/read-state :computer-map)]
    (for [i (range (count comp-map))
          j (range (count (first comp-map)))
          :let [cell (get-in comp-map [i j])]
          :when (and (= (:type cell) :city)
                     (status-pred (:city-status cell)))]
      [i j])))

(defn move-toward
  [pos target passable-neighbors]
  (when (seq passable-neighbors)
    (apply min-key #(distance % target) passable-neighbors)))

(defn adjacent-to-computer-unexplored?
  [pos]
  (let [comp-map (sa/read-state :computer-map)]
    (boolean (some #(nil? (get-in comp-map %))
                   (neighbors-in-map comp-map pos)))))

(defn stamp-territory
  [pos unit]
  (when (and (= :army (:type unit))
             (= :computer (:owner unit))
             (:country-id unit)
             (#{:land :city} (:type (get-in (sa/current-world) pos))))
    (sa/update-world! assoc-in (conj pos :country-id) (:country-id unit))))

(defn move-unit-to
  [from-pos to-pos]
  (let [from-cell (get-in (sa/current-world) from-pos)
        to-cell (get-in (sa/current-world) to-pos)
        unit (:contents from-cell)]
    (cond
      (:contents to-cell) nil
      (foreign-territory? unit to-cell) nil
      :else
      (do
        (sa/update-world! assoc-in from-pos (dissoc from-cell :contents))
        (sa/update-world! assoc-in (conj to-pos :contents) unit)
        (when (#{:patrol-boat :transport} (:type unit))
          (sa/update-world! update-in (conj to-pos :contents)
                            oscillation/append-position to-pos))
        (stamp-territory to-pos unit)
        (update-cell-visibility! from-pos (:owner unit))
        (update-cell-visibility! to-pos (:owner unit) unit)
        to-pos))))

(defn random-away-direction
  [origin target]
  (let [[oc or'] origin
        [tc tr] target
        dc (Integer/signum (- tc oc))
        dr (Integer/signum (- tr or'))]
    [(if (zero? dc) (if (< (rand) 0.5) -1 1) dc)
     (if (zero? dr) (if (< (rand) 0.5) -1 1) dr)]))

(defn find-wakeable-sentries
  [game-map pos radius]
  (let [[pc pr] pos]
    (for [c (range (max 0 (- pc radius)) (min (count game-map) (+ pc radius 1)))
          r (range (max 0 (- pr radius)) (min (count (first game-map)) (+ pr radius 1)))
          :when (not= [c r] pos)
          :let [cell (get-in game-map [c r])
                unit (:contents cell)]
          :when (and unit
                     (= :army (:type unit))
                     (= :computer (:owner unit))
                     (= :sentry (:mode unit))
                     (<= (chebyshev-distance pos [c r]) radius))]
      [c r])))

(defn wake-nearby-sentries
  [pos radius]
  (let [candidates (find-wakeable-sentries (sa/current-world) pos radius)]
    (doseq [coord candidates
            :let [direction (random-away-direction pos coord)]]
      (sa/update-world! update-in (conj coord :contents)
                        #(-> % (assoc :mode :awake
                                      :interior-explore-direction direction)
                             (dissoc :move-history))))
    (count candidates)))

(defn board-transport
  [army-pos transport-pos]
  (when-not (adjacent? army-pos transport-pos)
    (throw (ex-info "Cannot board transport from non-adjacent cell"
                    {:army-pos army-pos :transport-pos transport-pos})))
  (sa/update-world! update-in army-pos dissoc :contents)
  (sa/update-world! update-in (conj transport-pos :contents :army-count) (fnil inc 0))
  (wake-nearby-sentries army-pos 3))

(defn find-visible-player-units
  []
  (let [comp-map (sa/read-state :computer-map)]
    (for [i (range (count comp-map))
          j (range (count (first comp-map)))
          :let [cell (get-in comp-map [i j])
                contents (:contents cell)]
          :when (and contents (= (:owner contents) :player))]
      [i j])))

(defn- transport-compatible?
  "Returns true if the transport doesn't have a matching unload-event-id as the army.
   An army should not board the same transport that unloaded it."
  [transport-unit army-unload-event-id]
  (or (nil? army-unload-event-id)
      (nil? (:unload-event-id transport-unit))
      (not= (:unload-event-id transport-unit) army-unload-event-id)))

(defn- loading-transport?
  [unit army-unload-event-id]
  (and unit
       (= :computer (:owner unit))
       (= :transport (:type unit))
       (= :loading (:transport-mission unit))
       (< (:army-count unit 0) 6)
       (transport-compatible? unit army-unload-event-id)))

(defn find-loading-transport
  ([] (find-loading-transport nil))
  ([army-unload-event-id]
   (let [world (sa/read-state :computer-map)]
     (first (for [i (range (count world))
                  j (range (count (first world)))
                  :let [unit (:contents (get-in world [i j]))]
                  :when (loading-transport? unit army-unload-event-id)]
              [i j])))))

(defn find-adjacent-loading-transport
  ([pos]
   (find-adjacent-loading-transport pos nil))
  ([pos army-unload-event-id]
   (let [world (sa/read-state :computer-map)]
     (first (filter (fn [neighbor]
                      (loading-transport? (:contents (get-in world neighbor)) army-unload-event-id))
                    (get-neighbors pos))))))

(defn attempt-conquest-computer
  [army-pos city-pos]
  (let [army-cell (get-in (sa/current-world) army-pos)
        army (:contents army-cell)
        city-cell (get-in (sa/current-world) city-pos)]
    (if (< (rand) 0.5)
      (do
        (debug/log-computer-event! :army-conquest-success army-pos {:city city-pos})
        (sa/update-world! assoc-in army-pos (dissoc army-cell :contents))
        (sa/update-world! assoc-in city-pos (assoc city-cell :city-status :computer))
        (sa/update-state! :computer-city-positions (fnil conj #{}) city-pos)
        (combat/conquer-city-contents city-pos :computer)
        (stamp-territory city-pos army)
        (when (= :player (:city-status city-cell))
          (sa/update-state! :player-map assoc-in city-pos (get-in (sa/current-world) city-pos)))
        (let [city-country-id (:country-id (get-in (sa/current-world) city-pos))]
          (when-not (and city-country-id
                         (country-city-producing-armies? city-pos city-country-id))
            (city-production/set-city-production city-pos :army)))
        (update-cell-visibility! army-pos :computer)
        (update-cell-visibility! city-pos :computer)
        (when (and (sa/read-state :game-over-check-enabled)
                   (= :player (:city-status city-cell))
                   (not (has-city? :player)))
          (declare-game-over! "****GAME OVER*****  You Lose"))
        nil)
      (do
        (debug/log-computer-event! :army-conquest-fail army-pos {:city city-pos})
        (sa/update-world! assoc-in army-pos (dissoc army-cell :contents))
        (update-cell-visibility! army-pos :computer)
        nil))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:57:31.684104-05:00", :module-hash "81164576", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-1548798229"} {:id "def/neighbor-offsets", :kind "def", :line 9, :end-line 12, :hash "-1254756339"} {:id "defn/neighbors-in-map", :kind "defn", :line 14, :end-line 25, :hash "-2068233344"} {:id "defn/adjacent?", :kind "defn", :line 27, :end-line 34, :hash "-1801643981"} {:id "defn-/country-city-producing-armies?", :kind "defn-", :line 36, :end-line 46, :hash "-864239710"} {:id "defn-/update-cell-visibility!", :kind "defn-", :line 48, :end-line 52, :hash "907462422"} {:id "defn-/foreign-territory?", :kind "defn-", :line 54, :end-line 63, :hash "442986330"} {:id "defn/get-neighbors", :kind "defn", :line 65, :end-line 67, :hash "-2061481370"} {:id "defn/distance", :kind "defn", :line 69, :end-line 71, :hash "403209233"} {:id "defn/chebyshev-distance", :kind "defn", :line 73, :end-line 75, :hash "274637302"} {:id "defn-/has-city?", :kind "defn-", :line 77, :end-line 85, :hash "676841330"} {:id "defn-/declare-game-over!", :kind "defn-", :line 87, :end-line 94, :hash "1518773855"} {:id "defn/attackable-target?", :kind "defn", :line 96, :end-line 102, :hash "-1762839441"} {:id "defn/find-visible-cities", :kind "defn", :line 104, :end-line 112, :hash "-1520668987"} {:id "defn/move-toward", :kind "defn", :line 114, :end-line 117, :hash "591799972"} {:id "defn/adjacent-to-computer-unexplored?", :kind "defn", :line 119, :end-line 123, :hash "1407628068"} {:id "defn/stamp-territory", :kind "defn", :line 125, :end-line 131, :hash "-171525200"} {:id "defn/move-unit-to", :kind "defn", :line 133, :end-line 151, :hash "-193296860"} {:id "defn/random-away-direction", :kind "defn", :line 153, :end-line 160, :hash "-939520040"} {:id "defn/find-wakeable-sentries", :kind "defn", :line 162, :end-line 175, :hash "-1946149303"} {:id "defn/wake-nearby-sentries", :kind "defn", :line 177, :end-line 186, :hash "1328014643"} {:id "defn/board-transport", :kind "defn", :line 188, :end-line 195, :hash "-1429363205"} {:id "defn/find-visible-player-units", :kind "defn", :line 197, :end-line 205, :hash "-1696683775"} {:id "defn-/transport-compatible?", :kind "defn-", :line 207, :end-line 213, :hash "822381424"} {:id "defn-/loading-transport?", :kind "defn-", :line 215, :end-line 222, :hash "556430484"} {:id "defn/find-loading-transport", :kind "defn", :line 224, :end-line 232, :hash "-1515389649"} {:id "defn/find-adjacent-loading-transport", :kind "defn", :line 234, :end-line 241, :hash "1733500994"} {:id "defn/attempt-conquest-computer", :kind "defn", :line 243, :end-line 273, :hash "-85855893"}]}
;; clj-mutate-manifest-end
