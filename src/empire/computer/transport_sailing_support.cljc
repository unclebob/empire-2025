(ns empire.computer.transport-sailing-support
  (:require [empire.computer.core :as core]
            [empire.computer.transport-core :as tc]
            [empire.computer.transport-sailing-path :as sailing-path]
            [empire.computer.transport-unloading :as unloading]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.state.api :as sa]))

(def invasion-unload-radius 2)
(def invasion-threat-unload-radius 3)
(def invasion-threat-scan-radius 2)
(def sea-path-inflation-threshold 2)

;; Keep restore keys aligned with transport random-walk recovery behavior.
(def transport-random-walk-restore-keys
  [:transport-mission
   :sail-path
   :invasion-target
   :invasion-path
   :invasion-path-origin
   :invasion-plan-revision
   :invasion-load-since
   :major-invasion-find-armies-round
   :major-invasion-skip-revision])

(def ^:private player-ship-types
  #{:patrol-boat :destroyer :submarine :transport :carrier :battleship})

(defn update-cell-visibility!
  [pos owner]
  (visibility/update-cell-visibility pos owner))

(defn enemy-ship-near-target?
  [target radius]
  (let [world (sa/read-state :computer-map)
        [tx ty] target
        min-x (max 0 (- tx radius))
        max-x (min (dec (count world)) (+ tx radius))
        min-y (max 0 (- ty radius))
        max-y (min (dec (count (first world))) (+ ty radius))]
    (boolean
     (some true?
           (for [x (range min-x (inc max-x))
                 y (range min-y (inc max-y))]
             (let [u (get-in world [x y :contents])]
               (and u
                    (= :player (:owner u))
                    (contains? player-ship-types (:type u)))))))))

(defn compute-sail-path
  "Compute BFS path from transport position to the current coastal target."
  [pos army-count]
  (sailing-path/compute-sail-path
   pos
   (sa/read-state :computer-map)
   army-count))

(defn compute-sail-to-unload-path
  [pos]
  (sailing-path/compute-sail-to-unload-path pos (sa/read-state :computer-map)))

(defn compute-sail-to-load-path
  [pos]
  (sailing-path/compute-sail-to-load-path pos (sa/read-state :computer-map)))

(defn set-unloading-and-try!
  [pos]
  (tc/set-transport-mission pos :unloading)
  (unloading/try-opportunistic-unload pos))

(defn direct-step
  [from to]
  (let [[fr fc] from
        [tr tc] to
        dr (Long/signum (- tr fr))
        dc (Long/signum (- tc fc))]
    [(+ fr dr) (+ fc dc)]))

(defn between-cells
  [from to]
  (loop [current from
         cells []]
    (if (= current to)
      cells
      (let [next-pos (direct-step current to)]
        (if (= next-pos to)
          cells
          (recur next-pos (conj cells next-pos)))))))

(defn sea-or-unexplored?
  [cell]
  (or (nil? cell)
      (= :sea (:type cell))
      (= :unexplored (:type cell))))

(defn direct-sea-corridor?
  [from to computer-map]
  (every? (fn [step]
            (sea-or-unexplored? (get-in computer-map step)))
          (between-cells from to)))

(def ^:private random-sail-radius 4)

(defn- claimed-land?
  [cell]
  (or (and (= :land (:type cell))
           (some? (:country-id cell)))
      (and (= :city (:type cell))
           (= :computer (:city-status cell)))))

(defn- safe-random-sail-cell?
  [pos computer-map]
  (let [cell (get-in computer-map pos)]
    (and (= :sea (:type cell))
         (nil? (:contents cell))
         (not (map-utils/any-neighbor-matches? pos computer-map map-utils/neighbor-offsets claimed-land?)))))

(defn random-sail-path
  "Returns a random 4-step sea path that stays away from claimed land."
  [pos]
  (let [computer-map (sa/read-state :computer-map)
        directions (shuffle map-utils/neighbor-offsets)]
    (some (fn [[dr dc]]
            (loop [current pos
                   steps []]
              (if (= random-sail-radius (count steps))
                steps
                (let [next-pos [(+ (first current) dr) (+ (second current) dc)]]
                  (when (safe-random-sail-cell? next-pos computer-map)
                    (recur next-pos (conj steps next-pos)))))))
          directions)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T15:00:49.881765-05:00", :module-hash "1562060755", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-302277002"} {:id "def/invasion-unload-radius", :kind "def", :line 9, :end-line 9, :hash "244609358"} {:id "def/invasion-threat-unload-radius", :kind "def", :line 10, :end-line 10, :hash "1250298048"} {:id "def/invasion-threat-scan-radius", :kind "def", :line 11, :end-line 11, :hash "703933872"} {:id "def/sea-path-inflation-threshold", :kind "def", :line 12, :end-line 12, :hash "2026838488"} {:id "def/transport-random-walk-restore-keys", :kind "def", :line 15, :end-line 26, :hash "-1043576350"} {:id "def/player-ship-types", :kind "def", :line 28, :end-line 29, :hash "-889899963"} {:id "defn/update-cell-visibility!", :kind "defn", :line 31, :end-line 33, :hash "2091185304"} {:id "defn/enemy-ship-near-target?", :kind "defn", :line 35, :end-line 50, :hash "-201589004"} {:id "defn/compute-sail-path", :kind "defn", :line 52, :end-line 58, :hash "837981545"} {:id "defn/set-unloading-and-try!", :kind "defn", :line 60, :end-line 63, :hash "-263500716"} {:id "defn/direct-step", :kind "defn", :line 65, :end-line 71, :hash "-1268929394"} {:id "defn/between-cells", :kind "defn", :line 73, :end-line 82, :hash "206799869"} {:id "defn/sea-or-unexplored?", :kind "defn", :line 84, :end-line 88, :hash "890775111"} {:id "defn/direct-sea-corridor?", :kind "defn", :line 90, :end-line 94, :hash "-1197656732"}]}
;; clj-mutate-manifest-end
