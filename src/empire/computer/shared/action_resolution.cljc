(ns empire.computer.shared.action-resolution
  (:require [empire.computer.land-objectives :as land-objectives]
            [empire.computer.shared.oscillation :as oscillation]
            [empire.computer.shared.world-query :as world-query]
            [empire.game-mechanics.debug.logging :as debug]
            [empire.game-mechanics.visibility :as visibility]
            [empire.game-mechanics.services.city-production :as city-production]
            [empire.game-mechanics.services.combat :as combat]
            [empire.state.api :as sa]))

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

(defn- country-city-producing-armies?
  [city-pos country-id]
  (some (fn [[coords prod]]
          (and (map? prod)
               (= :army (:item prod))
               (not= coords city-pos)
               (let [cell (get-in (sa/read-state :computer-map) coords)]
                 (and (= :city (:type cell))
                      (= :computer (:city-status cell))
                      (= country-id (:country-id cell))))))
        (sa/read-state :production)))

(defn- flood-fill-unclaimed-land
  [computer-map pos]
  (loop [frontier #{pos}
         visited #{}
         to-claim #{}]
    (if (empty? frontier)
      to-claim
      (let [current (first frontier)
            remaining (disj frontier current)]
        (if (visited current)
          (recur remaining visited to-claim)
          (let [cell (get-in computer-map current)
                claimable? (and (= :land (:type cell))
                                (nil? (:country-id cell)))]
            (if claimable?
              (recur (into remaining (remove visited (world-query/get-neighbors current)))
                     (conj visited current)
                     (conj to-claim current))
              (recur remaining (conj visited current) to-claim))))))))

(defn stamp-territory
  [pos unit]
  (when (and (= :army (:type unit))
             (= :computer (:owner unit))
             (:country-id unit)
             (#{:land :city} (:type (get-in (sa/read-state :computer-map) pos))))
    (let [computer-map (sa/read-state :computer-map)
          cell (get-in computer-map pos)
          country-id (:country-id unit)]
      (if (and (= :land (:type cell)) (nil? (:country-id cell)))
        (doseq [claim-pos (flood-fill-unclaimed-land computer-map pos)]
          (sa/update-world! assoc-in (conj claim-pos :country-id) country-id))
        (sa/update-world! assoc-in (conj pos :country-id) country-id)))))

(defn- computer-army-at?
  [snapshot pos]
  (let [unit (:contents (get-in snapshot pos))]
    (and (= :computer (:owner unit))
         (= :army (:type unit)))))

(defn- flood-fill-country-region
  [snapshot city-pos lost-army-pos country-id]
  (loop [frontier #{city-pos}
         visited #{}
         cleared #{}
         anchors #{}]
    (if-let [current (first frontier)]
      (let [frontier (disj frontier current)]
        (if (visited current)
          (recur frontier visited cleared anchors)
          (let [cell (get-in snapshot current)
                same-country? (= country-id (:country-id cell))
                anchored? (and same-country?
                               (not= current lost-army-pos)
                               (computer-army-at? snapshot current))]
            (cond
              (not same-country?)
              (recur frontier (conj visited current) cleared anchors)
              anchored?
              (recur frontier (conj visited current) cleared (conj anchors current))
              :else
              (recur (into frontier (remove visited) (world-query/get-neighbors current))
                     (conj visited current)
                     (conj cleared current)
                     anchors)))))
      {:cleared cleared :anchors anchors})))

(defn- restamp-from-anchors! [cleared anchors country-id]
  (loop [frontier anchors
         seen anchors]
    (when-let [current (first frontier)]
      (let [frontier (disj frontier current)
            restamp (->> (world-query/get-neighbors current)
                         (filter cleared) (remove seen) set)]
        (doseq [pos restamp]
          (sa/update-world! assoc-in (conj pos :country-id) country-id)
          (sa/update-state! :computer-map assoc-in (conj pos :country-id) country-id))
        (recur (into frontier restamp) (into seen restamp))))))

(defn- clear-country-id-region-after-failed-conquest!
  [city-pos lost-army-pos country-id]
  (when country-id
    (let [{:keys [cleared anchors]}
          (flood-fill-country-region (sa/read-state :computer-map) city-pos lost-army-pos country-id)]
      (doseq [pos cleared]
        (sa/update-world! assoc-in (conj pos :country-id) nil)
        (sa/update-state! :computer-map assoc-in (conj pos :country-id) nil))
      (restamp-from-anchors! cleared anchors country-id))))

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
        (update-cell-visibility! from-pos (:owner unit))
        (update-cell-visibility! to-pos (:owner unit) unit)
        (stamp-territory to-pos unit)
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
                     (<= (max (Math/abs (- c pc)) (Math/abs (- r pr))) radius))]
      [c r])))

(defn wake-nearby-sentries
  [pos radius]
  (let [candidates (find-wakeable-sentries (sa/read-state :computer-map) pos radius)]
    (doseq [coord candidates
            :let [direction (random-away-direction pos coord)]]
      (sa/update-world! update-in (conj coord :contents)
                        #(-> % (assoc :mode :awake
                                      :interior-explore-direction direction)
                             (dissoc :move-history))))
    (count candidates)))

(defn board-transport
  [army-pos transport-pos]
  (when-not (and (<= (Math/abs (- (first transport-pos) (first army-pos))) 1)
                 (<= (Math/abs (- (second transport-pos) (second army-pos))) 1)
                 (not= army-pos transport-pos))
    (throw (ex-info "Cannot board transport from non-adjacent cell"
                    {:army-pos army-pos :transport-pos transport-pos})))
  (sa/update-world! update-in army-pos dissoc :contents)
  (sa/update-world! update-in (conj transport-pos :contents :army-count) (fnil inc 0))
  (wake-nearby-sentries army-pos 3))

(defn- has-city?
  [owner]
  (boolean
   (some (fn [col]
           (some #(and (= :city (:type %))
                       (= owner (:city-status %)))
                 col))
         (sa/current-world))))

(defn- declare-game-over!
  [message]
  (sa/write-state! :paused true)
  (sa/write-state! :error-message message)
  (sa/write-state! :error-until Long/MAX_VALUE)
  (sa/write-state! :map-to-display :actual-map)
  (sa/write-state! :player-items [])
  (sa/write-state! :computer-items []))

(defn attempt-conquest-computer
  [army-pos city-pos]
  (let [army-cell (get-in (sa/current-world) army-pos)
        army (:contents army-cell)
        city-cell (get-in (sa/current-world) city-pos)]
    (if (< (rand) 0.5)
      (do
        (debug/record-active-computer-unit-conquest! 1)
        (debug/log-computer-event! :army-conquest-success
                                   army-pos
                                   {:city city-pos
                                    :continent-id (land-objectives/continent-id-for-pos city-pos)
                                    :computer-unit-id (:computer-unit-id army)
                                    :country-id (:country-id army)})
        (sa/update-world! assoc-in army-pos (dissoc army-cell :contents))
        (sa/update-world! assoc-in city-pos (assoc city-cell :city-status :computer))
        (sa/update-state! :computer-city-positions (fnil conj #{}) city-pos)
        (combat/conquer-city-contents city-pos :computer)
        (update-cell-visibility! army-pos :computer)
        (update-cell-visibility! city-pos :computer)
        (stamp-territory city-pos army)
        (update-cell-visibility! city-pos :computer)
        (when (= :player (:city-status city-cell))
          (sa/update-state! :player-map assoc-in city-pos (get-in (sa/current-world) city-pos)))
        (let [city-country-id (:country-id (get-in (sa/current-world) city-pos))]
          (when-not (and city-country-id
                         (country-city-producing-armies? city-pos city-country-id))
            (city-production/set-city-production city-pos :army)))
        (when (and (sa/read-state :game-over-check-enabled)
                   (= :player (:city-status city-cell))
                   (not (has-city? :player)))
          (declare-game-over! "****GAME OVER*****  You Lose"))
        nil)
      (do
        (debug/log-computer-event! :army-conquest-fail army-pos {:city city-pos})
        (sa/update-world! assoc-in army-pos (dissoc army-cell :contents))
        (clear-country-id-region-after-failed-conquest! city-pos army-pos (:country-id army))
        (update-cell-visibility! army-pos :computer)
        nil))))
