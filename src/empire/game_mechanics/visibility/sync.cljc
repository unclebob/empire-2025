(ns empire.game-mechanics.visibility.sync
  (:require [empire.game-mechanics.visibility.core :as core]
            [empire.game-mechanics.visibility.territory :as territory]))

(def ^:private detection-queue (atom []))

(defn drain-detections!
  "Returns and clears accumulated detection events. Each event is {:pos [r c] :cell cell-map}."
  []
  (let [events @detection-queue]
    (reset! detection-queue [])
    events))

(defn- queue-detection!
  [coords cell]
  (swap! detection-queue conj {:pos coords :cell cell}))

(defn sync-ai-unit-to-computer-map!
  [pos]
  (let [computer-map (core/read-runtime-state :computer-map)
        cell (get-in (core/current-world) pos)
        unit (:contents cell)]
    (when (and computer-map
               cell
               unit
               (= :computer (:owner unit)))
      (core/update-visible-map! :computer-map assoc-in pos cell))))

(defn sync-ai-cell-to-computer-map!
  [pos]
  (let [computer-map (core/read-runtime-state :computer-map)
        cell (get-in (core/current-world) pos)]
    (when (and computer-map cell)
      (core/update-visible-map! :computer-map assoc-in pos cell))))

(defn authoritative-computer-unit-at
  [pos]
  (let [unit (get-in (core/current-world) (conj pos :contents))]
    (when (= :computer (:owner unit))
      unit)))

(defn- reveal-visibility-ring
  [pos owner visible-map-key visible-map game-map radius stamp-id detect-threats?]
  (let [[x y] pos
        height (count game-map)
        width (count (first game-map))
        exposed-positions (atom [])]
    (doseq [di (range (- radius) (inc radius))
            dj (range (- radius) (inc radius))
            :let [ni (+ x di) nj (+ y dj)]
            :when (core/in-bounds? ni nj height width)]
      (when (core/was-unexplored? visible-map ni nj)
        (swap! exposed-positions conj [ni nj]))
      (core/reveal-and-track! visible-map-key ni nj
                              stamp-id detect-threats? visible-map queue-detection!))
    (when (= owner :computer)
      (territory/stamp-exposed-territory! visible-map-key @exposed-positions))))

(defn update-cell-visibility
  "Updates visibility around a specific cell for the given owner.
   Satellites reveal two rectangular rings (distances 1 and 2).
   When unit is a computer army with country-id, stamps newly-revealed land cells."
  ([pos owner] (update-cell-visibility pos owner nil))
  ([pos owner unit]
   (let [visible-map-key (core/visible-map-key-for owner)
         game-map (core/current-world)
         cell (get-in game-map pos)
         radius (core/cell-visibility-radius cell)
         stamp-id (core/should-stamp-country? unit)
         detect-threats? (= owner :computer)]
     (when-let [visible-map (core/read-runtime-state visible-map-key)]
       (reveal-visibility-ring pos owner visible-map-key visible-map game-map
                               radius stamp-id detect-threats?)
       nil))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:54:33.587961-05:00", :module-hash "-1312662374", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-251313654"} {:id "def/detection-queue", :kind "def", :line 5, :end-line nil, :hash "303696996"} {:id "defn/drain-detections!", :kind "defn", :line 7, :end-line nil, :hash "1195271324"} {:id "defn-/queue-detection!", :kind "defn-", :line 14, :end-line nil, :hash "-319377426"} {:id "defn/sync-ai-unit-to-computer-map!", :kind "defn", :line 18, :end-line nil, :hash "-91376998"} {:id "defn/sync-ai-cell-to-computer-map!", :kind "defn", :line 29, :end-line nil, :hash "-521139698"} {:id "defn/authoritative-computer-unit-at", :kind "defn", :line 36, :end-line nil, :hash "-1952801816"} {:id "defn-/reveal-visibility-ring", :kind "defn-", :line 42, :end-line nil, :hash "751976918"} {:id "defn/update-cell-visibility", :kind "defn", :line 59, :end-line nil, :hash "-914007802"}]}
;; clj-mutate-manifest-end
