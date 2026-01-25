(ns empire.fsm.production
  "FSM-driven production decisions for computer cities.
   Lieutenants decide what their cities should produce."
  (:require [empire.atoms :as atoms]
            [empire.player.production :as production]))

(defn decide-production
  "Lieutenant decides what a city should produce.
   Returns unit type keyword (:army, :fighter, :transport, etc.)
   Currently simple: early game produces armies for exploration."
  [_lieutenant _city-coords unit-counts]
  (let [army-count (get unit-counts :army 0)]
    ;; Early game strategy: build armies for exploration
    ;; Later phases will add more sophisticated logic
    (if (< army-count 10)
      :army
      :army)))  ; TODO: expand with transport/ship logic

(defn find-lieutenant-for-city
  "Find the Lieutenant that owns the given city coordinates.
   Returns the Lieutenant or nil if not found."
  [city-coords]
  (when-let [general @atoms/commanding-general]
    (first (filter (fn [lt]
                     (some #(= % city-coords) (:cities lt)))
                   (:lieutenants general)))))

(defn process-computer-city-production
  "Process production for a computer city using FSM hierarchy.
   If city has no current production, asks Lieutenant what to build."
  [city-coords]
  (when-not (get @atoms/production city-coords)
    (when-let [lt (find-lieutenant-for-city city-coords)]
      (let [unit-counts {}  ; TODO: could pass actual counts
            item (decide-production lt city-coords unit-counts)]
        (production/set-city-production city-coords item)))))
