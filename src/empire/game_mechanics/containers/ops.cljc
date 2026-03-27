(ns empire.game-mechanics.containers.ops
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.unit-stamping :as unit-stamping]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.containers.launch :as launch]
            [empire.game-mechanics.containers.visibility-port :as visibility-port]
            [empire.config.domain.model.containers :as domain-containers]
            [empire.game-mechanics.spatial.neighbors :as neighbors]
            [empire.config.units.dispatcher :as dispatcher]))

(defn- neighbor-offsets
  []
  neighbors/neighbor-offsets)

(defn- any-neighbor-matches?
  [coords world offsets pred]
  (neighbors/any-neighbor-matches? coords world offsets pred))

(defn- get-matching-neighbors
  [coords world offsets pred]
  (neighbors/get-matching-neighbors coords world offsets pred))

(defn- update-cell-visibility!
  [coords owner]
  (visibility-port/apply-container-visibility!
    (visibility-port/container-visibility-port)
    coords
    owner))

(defn- stamp-unit-fields
  [unit city]
  (unit-stamping/stamp-computer-fields unit city))

(defn- stamp-computer-unit-id
  [unit]
  (unit-stamping/ensure-computer-unit-id unit))

;; Transport operations

(defn- loadable-army?
  "Returns true if adj-unit is a sentry army owned by the same player as transport,
   and the transport is not full."
  [adj-unit transport]
  (and adj-unit
       (= (:type adj-unit) :army)
       (= (:mode adj-unit) :sentry)
       (= (:owner adj-unit) (:owner transport))
       (not (uc/full? transport :army-count
                      (dispatcher/effective-capacity :transport (:hits transport))))))

(defn- wake-transport-if-needed
  "Wakes a sentry transport at beach that has armies loaded."
  [transport-coords]
  (let [world (sa/current-world)
        transport (get-in world (conj transport-coords :contents))
        has-armies? (pos? (uc/get-count transport :army-count))
        at-beach? (any-neighbor-matches? transport-coords world (neighbor-offsets)
                                         #(= :land (:type %)))]
    (when (and has-armies? at-beach? (= (:mode transport) :sentry))
      (sa/update-world! update-in (conj transport-coords :contents)
                        #(assoc % :mode :awake :reason :transport-at-beach)))))

(defn non-full-transport? [unit]
  (and (= (:type unit) :transport)
       (not (uc/full? unit :army-count (dispatcher/effective-capacity :transport (:hits unit))))))

(defn- try-load-from-neighbor [transport-coords [nx ny]]
  (let [world (sa/current-world)
        adj-cell (get-in world [nx ny])
        adj-unit (:contents adj-cell)
        transport (get-in world (conj transport-coords :contents))]
    (when (loadable-army? adj-unit transport)
      (sa/update-world! assoc-in [nx ny] (dissoc adj-cell :contents))
      (sa/update-world! update-in (conj transport-coords :contents) uc/add-unit :army-count))))

(defn load-adjacent-sentry-armies
  [transport-coords]
  (let [unit (:contents (get-in (sa/current-world) transport-coords))]
    (when (non-full-transport? unit)
      (let [neighbors (get-matching-neighbors transport-coords
                                              (sa/current-world)
                                              (neighbor-offsets)
                                              (constantly true))]
        (doseq [n neighbors]
          (try-load-from-neighbor transport-coords n))
        (wake-transport-if-needed transport-coords)))))

(defn wake-armies-on-transport
  [transport-coords]
  (let [cell (get-in (sa/current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/wake-transport-armies transport)
        updated-cell (assoc cell :contents updated-transport)]
    (sa/update-world! assoc-in transport-coords updated-cell)))

(defn sleep-armies-on-transport
  [transport-coords]
  (let [cell (get-in (sa/current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/sleep-transport-armies transport)
        updated-cell (assoc cell :contents updated-transport)]
    (sa/update-world! assoc-in transport-coords updated-cell)))

(defn remove-army-from-transport
  [transport-coords]
  (let [cell (get-in (sa/current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/remove-awake-transport-army transport)
        updated-cell (assoc cell :contents updated-transport)]
    (sa/update-world! assoc-in transport-coords updated-cell)))

(defn disembark-army-from-transport
  [transport-coords target-coords]
  (let [cell (get-in (sa/current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/remove-awake-transport-army transport)
        disembarked-army (-> (domain-containers/disembarked-army (:owner transport))
                             (stamp-computer-unit-id))
        updated-cell (assoc cell :contents updated-transport)]
    (sa/update-world! assoc-in transport-coords updated-cell)
    (sa/update-world! assoc-in (conj target-coords :contents) disembarked-army)
    (update-cell-visibility! target-coords (:owner transport))
    target-coords))

(defn disembark-army-with-target
  [transport-coords adjacent-coords extended-target]
  (let [cell (get-in (sa/current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/remove-awake-transport-army transport)
        moving-army (-> (domain-containers/moving-disembarked-army (:owner transport) extended-target)
                        (stamp-computer-unit-id))
        updated-cell (assoc cell :contents updated-transport)]
    (sa/update-world! assoc-in transport-coords updated-cell)
    (sa/update-world! assoc-in (conj adjacent-coords :contents) moving-army)
    (update-cell-visibility! adjacent-coords (:owner transport))))

(defn disembark-army-to-explore
  [transport-coords target-coords]
  (let [cell (get-in (sa/current-world) transport-coords)
        transport (:contents cell)
        updated-transport (domain-containers/remove-awake-transport-army transport)
        exploring-army (-> (domain-containers/exploring-disembarked-army (:owner transport) target-coords)
                           (stamp-computer-unit-id))
        updated-cell (assoc cell :contents updated-transport)]
    (sa/update-world! assoc-in transport-coords updated-cell)
    (sa/update-world! assoc-in (conj target-coords :contents) exploring-army)
    (update-cell-visibility! target-coords (:owner transport))
    target-coords))

;; Carrier operations

(defn wake-fighters-on-carrier
  [carrier-coords]
  (let [cell (get-in (sa/current-world) carrier-coords)
        carrier (:contents cell)
        updated-carrier (domain-containers/wake-carrier-fighters carrier)
        updated-cell (assoc cell :contents updated-carrier)]
    (sa/update-world! assoc-in carrier-coords updated-cell)))

(defn sleep-fighters-on-carrier
  [carrier-coords]
  (let [cell (get-in (sa/current-world) carrier-coords)
        carrier (:contents cell)
        updated-carrier (domain-containers/sleep-carrier-fighters carrier)
        updated-cell (assoc cell :contents updated-carrier)]
    (sa/update-world! assoc-in carrier-coords updated-cell)))

(defn launch-fighter-from-carrier
  [carrier-coords target-coords]
  (let [world (sa/current-world)
        cell (get-in world carrier-coords)
        carrier (:contents cell)
        after-remove (uc/remove-awake-unit carrier :fighter-count :awake-fighters)
        first-step (domain-containers/first-step-toward carrier-coords target-coords)
        moving-fighter (-> (domain-containers/launched-fighter
                            (:owner carrier)
                            target-coords
                            (dec (config/unit-speed :fighter)))
                           (stamp-computer-unit-id))
        updated-cell (assoc cell :contents after-remove)
        target-cell (get-in world first-step)]
    ;; Update carrier
    (sa/update-world! assoc-in carrier-coords updated-cell)
    ;; Place fighter at first step position
    (sa/update-world! assoc-in first-step (assoc target-cell :contents moving-fighter))
    (update-cell-visibility! first-step (:owner carrier))
    first-step))

;; Airport operations

(defn launch-fighter-from-airport
  [city-coords target-coords]
  (let [world (sa/current-world)
        cell (get-in world city-coords)
        owner (case (:city-status cell)
                :computer :computer
                :player)
        first-step (some (fn [candidate]
                           (let [candidate-cell (get-in world candidate)]
                             (when (and candidate-cell (nil? (:contents candidate-cell)))
                               candidate)))
                         (launch/launch-steps-toward city-coords target-coords))
        target-cell (get-in world first-step)]
    (when first-step
      (let [after-remove (uc/remove-awake-unit cell :fighter-count :awake-fighters)
            moving-fighter (-> (domain-containers/launched-fighter
                                owner
                                target-coords
                                (dec (config/unit-speed :fighter)))
                               (stamp-computer-unit-id))]
        (sa/update-world! assoc-in city-coords after-remove)
        (sa/update-world! assoc-in first-step (assoc target-cell :contents moving-fighter))
        (update-cell-visibility! first-step owner)
        first-step))))

;; Shipyard operations

(defn launch-ship-from-shipyard
  ([city-coords ship-index]
   (launch-ship-from-shipyard city-coords ship-index city-coords))
  ([city-coords ship-index launch-pos]
   (let [cell (get-in (sa/current-world) city-coords)
         ship-data (get-in cell [:shipyard ship-index])
         owner (case (:city-status cell)
                 :player :player
                 :computer :computer
                 :player)  ; default to player for free cities
         ship (-> {:type (:type ship-data)
                   :owner owner
                   :hits (:hits ship-data)
                   :mode :awake
                   :steps-remaining (dispatcher/effective-speed (:type ship-data) (:hits ship-data))}
                  (stamp-unit-fields cell))
         updated-city (uc/remove-ship-from-shipyard cell ship-index)]
     (sa/update-world! assoc-in city-coords updated-city)
     (if (= launch-pos city-coords)
       (sa/update-world! assoc-in city-coords (assoc updated-city :contents ship))
       (sa/update-world! assoc-in (conj launch-pos :contents) ship))
     (update-cell-visibility! launch-pos owner))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:55:49.304943-05:00", :module-hash "-649743568", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 10, :hash "1603886500"} {:id "defn-/neighbor-offsets", :kind "defn-", :line 12, :end-line 14, :hash "801755107"} {:id "defn-/any-neighbor-matches?", :kind "defn-", :line 16, :end-line 18, :hash "-1280585355"} {:id "defn-/get-matching-neighbors", :kind "defn-", :line 20, :end-line 22, :hash "-1885613787"} {:id "defn-/update-cell-visibility!", :kind "defn-", :line 24, :end-line 29, :hash "1765455439"} {:id "defn-/stamp-unit-fields", :kind "defn-", :line 31, :end-line 33, :hash "595628565"} {:id "defn-/stamp-computer-unit-id", :kind "defn-", :line 35, :end-line 37, :hash "-1614409584"} {:id "defn-/loadable-army?", :kind "defn-", :line 41, :end-line 50, :hash "1070021271"} {:id "defn-/wake-transport-if-needed", :kind "defn-", :line 52, :end-line 62, :hash "-1345424461"} {:id "defn/non-full-transport?", :kind "defn", :line 64, :end-line 66, :hash "-1149958313"} {:id "defn-/try-load-from-neighbor", :kind "defn-", :line 68, :end-line 75, :hash "507435039"} {:id "defn/load-adjacent-sentry-armies", :kind "defn", :line 77, :end-line 87, :hash "757945724"} {:id "defn/wake-armies-on-transport", :kind "defn", :line 89, :end-line 95, :hash "1045990134"} {:id "defn/sleep-armies-on-transport", :kind "defn", :line 97, :end-line 103, :hash "-1250429803"} {:id "defn/remove-army-from-transport", :kind "defn", :line 105, :end-line 111, :hash "629881545"} {:id "defn/disembark-army-from-transport", :kind "defn", :line 113, :end-line 124, :hash "-1332278300"} {:id "defn/disembark-army-with-target", :kind "defn", :line 126, :end-line 136, :hash "-1296086243"} {:id "defn/disembark-army-to-explore", :kind "defn", :line 138, :end-line 149, :hash "993578209"} {:id "defn/wake-fighters-on-carrier", :kind "defn", :line 153, :end-line 159, :hash "-1240843406"} {:id "defn/sleep-fighters-on-carrier", :kind "defn", :line 161, :end-line 167, :hash "-865163527"} {:id "defn/launch-fighter-from-carrier", :kind "defn", :line 169, :end-line 188, :hash "389104838"} {:id "defn/launch-fighter-from-airport", :kind "defn", :line 192, :end-line 215, :hash "606735034"} {:id "defn/launch-ship-from-shipyard", :kind "defn", :line 219, :end-line 240, :hash "-1500136512"}]}
;; clj-mutate-manifest-end
