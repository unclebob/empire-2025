(ns empire.game-loop.satellites
  "Satellite movement and lifecycle management.

   Satellites orbit the map, revealing fog-of-war as they pass.
   They have a limited lifespan (turns-remaining) and bounce off map edges."
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.satellite :as satellite]
            [empire.movement.visibility :as visibility]))

(defn- find-satellite-coords
  "Returns coordinates of all satellites on the map.
   Returns a vector to avoid lazy evaluation issues during map modification."
  []
  (vec (for [i (range (count @atoms/game-map))
             j (range (count (first @atoms/game-map)))
             :let [cell (get-in @atoms/game-map [i j])
                   contents (:contents cell)]
             :when (= (:type contents) :satellite)]
         [i j])))

(defn- remove-satellite!
  "Removes a satellite from the map and updates visibility."
  [coords owner]
  (swap! atoms/game-map update-in coords dissoc :contents)
  (visibility/update-cell-visibility coords owner)
  nil)

(defn- end-of-round-update!
  "Handles end-of-round satellite state: decrements turns and removes if expired.
   Returns coords if satellite survives, nil if removed."
  [coords sat]
  (let [new-turns (dec (:turns-remaining sat 1))]
    (if (<= new-turns 0)
      (remove-satellite! coords (:owner sat))
      (do (swap! atoms/game-map assoc-in (conj coords :contents :turns-remaining) new-turns)
          coords))))

(defn- move-satellite-steps
  "Moves a satellite the number of steps based on its speed.
   Decrements turns-remaining once per round.
   Returns final position or nil if satellite expired."
  [start-coords]
  (loop [coords start-coords
         steps-left (config/unit-speed :satellite)]
    (let [cell (get-in @atoms/game-map coords)
          sat (:contents cell)]
      (cond
        (not sat) nil
        (<= (:turns-remaining sat 0) 0) (remove-satellite! coords (:owner sat))
        (zero? steps-left) (end-of-round-update! coords sat)
        :else (recur (satellite/move-satellite coords) (dec steps-left))))))

(defn move-satellites
  "Moves all satellites according to their speed.
   Removes satellites with turns-remaining at or below zero."
  []
  (doseq [coords (find-satellite-coords)]
    (move-satellite-steps coords)))
