;; mutation-tested: 2026-03-03
(ns empire.computer.ship-core
  "Core ship utilities shared by patrol, escort, and carrier sub-modules."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.ports.movement :as movement-port]
            [empire.application.state :as app-state]
            [empire.combat :as combat]
            [empire.computer.core :as core]
            [empire.computer.threat :as threat]
            [empire.containers.helpers :as uc]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- update-runtime-state!
  [k f & args]
  (let [current (read-runtime-state k)
        next-state (apply f current args)]
    ((:write-runtime-state! @state-ctx) k next-state)))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

(defn- movement-services
  []
  (:movement-port @state-ctx))

(defn- update-cell-visibility!
  ([pos owner]
   (movement-port/movement-update-cell-visibility (movement-services) pos owner))
  ([pos owner unit]
   (movement-port/movement-update-cell-visibility-with-unit (movement-services) pos owner unit)))

(defn- set-turn-message!
  [msg ms]
  (write-runtime-state! :turn-message msg)
  (write-runtime-state! :turn-message-until (if (= ms Long/MAX_VALUE)
                                               Long/MAX_VALUE
                                               (+ (System/currentTimeMillis) ms))))

(defmulti get-passable-sea-neighbors (fn [& _] :default))

(defmethod get-passable-sea-neighbors :default
  [pos]
  (let [game-map (current-world)]
    (filter (fn [neighbor]
              (let [cell (get-in game-map neighbor)]
                (and cell
                     (= :sea (:type cell))
                     (or (nil? (:contents cell))
                         (= :player (:owner (:contents cell)))))))
            (core/get-neighbors pos))))

(defmulti find-adjacent-enemy-ship (fn [& _] :default))

(defmethod find-adjacent-enemy-ship :default
  [pos]
  (let [game-map (current-world)]
    (first (filter (fn [neighbor]
                     (let [cell (get-in game-map neighbor)
                           unit (:contents cell)]
                       (and unit
                            (= :player (:owner unit))
                            (#{:patrol-boat :destroyer :submarine :transport
                               :carrier :battleship} (:type unit)))))
                   (core/get-neighbors pos)))))

(defmulti attack-enemy (fn [& _] :default))

(defmethod attack-enemy :default
  [ship-pos enemy-pos]
  (let [attacker (get-in (current-world) (conj ship-pos :contents))
        defender (get-in (current-world) (conj enemy-pos :contents))
        result (combat/resolve-combat attacker defender)
        message (combat/format-combat-status (:log result)
                                             (:type attacker)
                                             (:type defender)
                                             (:winner result))
        dead-unit (if (= :attacker (:winner result)) defender attacker)]
    (set-turn-message! message Long/MAX_VALUE)
    (update-game-map! update-in ship-pos dissoc :contents)
    (if (= :attacker (:winner result))
      (do
        (update-game-map! assoc-in (conj enemy-pos :contents) (:survivor result))
        (when (= :carrier (:type attacker))
          (update-runtime-state! :computer-carrier-positions disj ship-pos)
          (update-runtime-state! :computer-carrier-positions (fnil conj #{}) enemy-pos))
        (update-cell-visibility! ship-pos :computer)
        (update-cell-visibility! enemy-pos :computer)
        (combat/clear-escort-on-death dead-unit)
        enemy-pos)
      (do
        (update-game-map! assoc-in (conj enemy-pos :contents) (:survivor result))
        (when (= :carrier (:type attacker))
          (update-runtime-state! :computer-carrier-positions disj ship-pos))
        (update-cell-visibility! ship-pos :computer)
        (combat/clear-escort-on-death dead-unit)
        nil))))

(defmulti move-toward (fn [& _] :default))

(defmethod move-toward :default
  [pos target]
  (let [passable (get-passable-sea-neighbors pos)
        closest (core/move-toward pos target passable)]
    (when closest
      (core/move-unit-to pos closest)
      (update-cell-visibility! pos :computer)
      (update-cell-visibility! closest :computer)
      closest)))

(defmulti explore-sea (fn [& _] :default))

(defmethod explore-sea :default
  [pos ship-type]
  (when-let [target (movement-port/movement-find-nearest-unexplored (movement-services) pos ship-type)]
    (move-toward pos target)))

(defmulti find-player-ship-sighting (fn [& _] :default))

(defmethod find-player-ship-sighting :default
  [pos]
  (let [player-units (core/find-visible-player-units)]
    (when (seq player-units)
      (apply min-key (partial core/distance pos) player-units))))

(defmulti retreat-if-damaged (fn [& _] :default))

(defmethod retreat-if-damaged :default
  [pos unit]
  (let [comp-map (read-runtime-state :computer-map)]
    (when (threat/should-retreat? pos unit comp-map)
    (let [passable (get-passable-sea-neighbors pos)]
      (threat/retreat-move pos unit comp-map passable)))))

(defmulti find-computer-transports (fn [& _] :default))

(defmethod find-computer-transports :default
  []
  (let [game-map (current-world)]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [cell (get-in game-map [i j])
                unit (:contents cell)]
          :when (and unit
                     (= :computer (:owner unit))
                     (= :transport (:type unit)))]
      [i j])))

(defmulti find-nearest-transport (fn [& _] :default))

(defmethod find-nearest-transport :default
  [pos]
  (let [transports (find-computer-transports)]
    (when (seq transports)
      (apply min-key (partial core/distance pos) transports))))

(defmulti find-adjacent-dock-city (fn [& _] :default))

(defmethod find-adjacent-dock-city :default
  [pos unit]
  (first (filter (fn [neighbor]
                   (uc/ship-can-dock? unit (get-in (current-world) neighbor)))
                 (core/get-neighbors pos))))

(defmulti dock-computer-ship (fn [& _] :default))

(defmethod dock-computer-ship :default
  [ship-pos city-pos]
  (let [cell (get-in (current-world) ship-pos)
        unit (:contents cell)
        city-cell (get-in (current-world) city-pos)
        updated-city (uc/add-ship-to-shipyard city-cell (:type unit) (:hits unit))]
    (update-game-map! assoc-in ship-pos (dissoc cell :contents))
    (update-game-map! assoc-in city-pos updated-city)
    (update-cell-visibility! city-pos :computer)
    city-pos))
