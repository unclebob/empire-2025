(ns empire.computer.shared.action-resolution
  (:require [empire.computer.shared.land-objectives :as land-objectives]
            [empire.computer.shared.movement :as computer-movement]
            [empire.computer.shared.oscillation :as oscillation]
            [empire.computer.shared.world-query :as world-query]
            [empire.game-mechanics.debug.logging :as debug]
            [empire.game-mechanics.services.city-production :as city-production]
            [empire.game-mechanics.services.combat :as combat]
            [empire.game-mechanics.services.game-over :as game-over]
            [empire.state.api :as sa]))

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

(defn- country-fill-kind
  [snapshot current lost-army-pos country-id]
  (let [same-country? (= country-id (:country-id (get-in snapshot current)))]
    (cond
      (not same-country?) :skip
      (and (not= current lost-army-pos) (computer-army-at? snapshot current)) :anchor
      :else :expand)))

(defn- apply-country-fill-step
  [snapshot current lost-army-pos country-id frontier visited cleared anchors]
  (case (country-fill-kind snapshot current lost-army-pos country-id)
    :skip [frontier (conj visited current) cleared anchors]
    :anchor [frontier (conj visited current) cleared (conj anchors current)]
    [(into frontier (remove visited) (world-query/get-neighbors current))
     (conj visited current)
     (conj cleared current)
     anchors]))

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
          (let [[frontier visited cleared anchors]
                (apply-country-fill-step snapshot current lost-army-pos country-id
                                         frontier visited cleared anchors)]
            (recur frontier visited cleared anchors))))
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
        (computer-movement/update-cell-visibility! from-pos (:owner unit))
        (computer-movement/update-cell-visibility-with-unit! to-pos (:owner unit) unit)
        (stamp-territory to-pos unit)
        (computer-movement/update-cell-visibility-with-unit! to-pos (:owner unit) unit)
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

(defn- apply-successful-computer-conquest!
  [army-pos city-pos army-cell army city-cell]
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
  (computer-movement/update-cell-visibility! army-pos :computer)
  (computer-movement/update-cell-visibility! city-pos :computer)
  (stamp-territory city-pos army)
  (computer-movement/update-cell-visibility! city-pos :computer)
  (when (= :player (:city-status city-cell))
    (sa/update-state! :player-map assoc-in city-pos (get-in (sa/current-world) city-pos)))
  (let [city-country-id (:country-id (get-in (sa/current-world) city-pos))]
    (when-not (and city-country-id
                   (country-city-producing-armies? city-pos city-country-id))
      (city-production/set-city-production city-pos :army)))
  (when (and (sa/read-state :game-over-check-enabled)
             (= :player (:city-status city-cell))
             (not (has-city? :player)))
    (game-over/declare-game-over! "****GAME OVER*****  You Lose"))
  nil)

(defn- apply-failed-computer-conquest!
  [army-pos city-pos army-cell army]
  (debug/log-computer-event! :army-conquest-fail army-pos {:city city-pos})
  (sa/update-world! assoc-in army-pos (dissoc army-cell :contents))
  (clear-country-id-region-after-failed-conquest! city-pos army-pos (:country-id army))
  (computer-movement/update-cell-visibility! army-pos :computer)
  nil)

(defn attempt-conquest-computer
  [army-pos city-pos]
  (let [army-cell (get-in (sa/current-world) army-pos)
        army (:contents army-cell)
        city-cell (get-in (sa/current-world) city-pos)]
    (if (< (rand) 0.5)
      (apply-successful-computer-conquest! army-pos city-pos army-cell army city-cell)
      (apply-failed-computer-conquest! army-pos city-pos army-cell army))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:11:36.663426-05:00", :module-hash "142886019", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-397594232"} {:id "defn-/foreign-territory?", :kind "defn-", :line 12, :end-line nil, :hash "442986330"} {:id "defn-/country-city-producing-armies?", :kind "defn-", :line 23, :end-line nil, :hash "158463725"} {:id "defn-/flood-fill-unclaimed-land", :kind "defn-", :line 35, :end-line nil, :hash "1989995945"} {:id "defn/stamp-territory", :kind "defn", :line 55, :end-line nil, :hash "-1468700128"} {:id "defn-/computer-army-at?", :kind "defn-", :line 69, :end-line nil, :hash "678298330"} {:id "defn-/country-fill-kind", :kind "defn-", :line 75, :end-line nil, :hash "-1677232935"} {:id "defn-/apply-country-fill-step", :kind "defn-", :line 83, :end-line nil, :hash "-1673515569"} {:id "defn-/flood-fill-country-region", :kind "defn-", :line 93, :end-line nil, :hash "-1716785836"} {:id "defn-/restamp-from-anchors!", :kind "defn-", :line 109, :end-line nil, :hash "719042592"} {:id "defn-/clear-country-id-region-after-failed-conquest!", :kind "defn-", :line 121, :end-line nil, :hash "1484883844"} {:id "defn/move-unit-to", :kind "defn", :line 131, :end-line nil, :hash "836609590"} {:id "defn/random-away-direction", :kind "defn", :line 152, :end-line nil, :hash "-939520040"} {:id "defn/find-wakeable-sentries", :kind "defn", :line 161, :end-line nil, :hash "-1657952121"} {:id "defn/wake-nearby-sentries", :kind "defn", :line 176, :end-line nil, :hash "-1123757030"} {:id "defn/board-transport", :kind "defn", :line 187, :end-line nil, :hash "1793683928"} {:id "defn-/has-city?", :kind "defn-", :line 198, :end-line nil, :hash "-1868148641"} {:id "defn-/apply-successful-computer-conquest!", :kind "defn-", :line 207, :end-line nil, :hash "1506424378"} {:id "defn-/apply-failed-computer-conquest!", :kind "defn-", :line 236, :end-line nil, :hash "-1442842531"} {:id "defn/attempt-conquest-computer", :kind "defn", :line 244, :end-line nil, :hash "314317599"}]}
;; clj-mutate-manifest-end
