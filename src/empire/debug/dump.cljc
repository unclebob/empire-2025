;; mutation-tested: 2026-02-28
(ns empire.debug.dump
  "Region-based debug dump rendering and file output helpers."
  (:require [clojure.string :as str]
            [empire.application.runtime :as app-runtime]))

(defonce ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn dump-region
  "Extract cells from all three maps for a coordinate range.
   Takes [start-row start-col] [end-row end-col].
   Returns {:game-map {...} :player-map {...} :computer-map {...}}
   where each map is {[row col] cell-data}."
  [[start-row start-col] [end-row end-col]]
  (let [game-map (current-world)
        player-map (read-runtime-state :player-map)
        computer-map (read-runtime-state :computer-map)
        coords-in-range (for [row (range start-row (inc end-row))
                              col (range start-col (inc end-col))]
                          [row col])]
    {:game-map (into {}
                     (for [coord coords-in-range
                           :let [cell (get-in game-map coord)]
                           :when cell]
                       [coord cell]))
     :player-map (into {}
                       (for [coord coords-in-range
                             :let [cell (get-in player-map coord)]
                             :when cell]
                         [coord cell]))
     :computer-map (into {}
                         (for [coord coords-in-range
                               :let [cell (get-in computer-map coord)]
                               :when cell]
                           [coord cell]))}))

(defn- format-contents
  "Format unit contents for display."
  [contents]
  (let [optional-fields [[:mode "mode"]
                         [:hits "hits"]
                         [:fuel "fuel"]
                         [:army-count "army-count"]
                         [:fighter-count "fighter-count"]
                         [:transport-mission "transport-mission"]
                         [:country-id "cid"]
                         [:unload-event-id "eid"]
                         [:attack-target "atk-target"]
                         [:pickup-continent-pos "pcp"]
                         [:stuck-since-round "stuck-since"]
                         [:patrol-mode "patrol-mode"]]
        optional-strs (for [[k label] optional-fields
                            :let [v (get contents k)]
                            :when v]
                        (str " " label ":" v))]
    (str " contents:{type:" (some-> (:type contents) name)
         " owner:" (some-> (:owner contents) name)
         (apply str optional-strs)
         "}")))

(defn- format-cell-data
  "Format non-nil cell data for display."
  [coord-str cell]
  (let [type-str (name (:type cell))
        city-status (when (:city-status cell)
                      (str " city-status:" (name (:city-status cell))))
        contents-str (when (:contents cell)
                       (format-contents (:contents cell)))
        extra-keys (dissoc cell :type :city-status :contents)
        extra-str (when (seq extra-keys)
                    (str " " (pr-str extra-keys)))]
    (str coord-str " :" type-str city-status contents-str extra-str)))

(defn format-cell
  "Pretty-print a single cell's state.
   Takes coords and cell data, returns formatted string."
  [coords cell]
  (let [[row col] coords
        coord-str (str "[" row "," col "]")]
    (if (nil? cell)
      (str coord-str " nil")
      (format-cell-data coord-str cell))))

(defn- format-action-entry
  "Format a single action log entry for display."
  [{:keys [timestamp action]}]
  (str "  " timestamp " " (pr-str action)))

(defn- find-coastline-units
  "Find all units in coastline-follow mode."
  []
  (let [game-map (current-world)]
    (for [col (range (count game-map))
          row (range (count (first game-map)))
          :let [cell (get-in game-map [col row])
                unit (:contents cell)]
          :when (= (:mode unit) :coastline-follow)]
      {:pos [col row]
       :type (:type unit)
       :owner (:owner unit)
       :visited (count (:visited unit))
       :steps-remaining (:coastline-steps unit)})))

(defn- format-coastline-section
  "Format coastline-follow units for debug dump."
  []
  (let [units (find-coastline-units)]
    (str "=== Coastline-Follow Units ===\n"
         (if (empty? units)
           "  (none)\n"
           (str (str/join "\n"
                          (for [{:keys [pos type owner visited steps-remaining]} units]
                            (str "  " pos " " (name type) " owner:" (name owner)
                                 " visited:" visited " steps:" steps-remaining)))
                "\n"))
         "\n")))

(defn- format-computer-event-entry
  "Format a single computer event log entry."
  [{:keys [event pos] :as entry}]
  (let [extras (dissoc entry :round :event :pos)
        extra-str (when (seq extras) (str " " (pr-str extras)))]
    (str "    " (name event) " " pos extra-str)))

(defn- format-computer-event-section
  "Format computer unit event history for the last 50 rounds."
  []
  (let [current-round (read-runtime-state :round-number)
        min-round (max 1 (- current-round 49))
        entries (read-runtime-state :computer-event-log)
        recent (filter #(<= min-round (:round %) current-round) entries)
        by-round (group-by :round recent)
        rounds (sort (keys by-round))]
    (str "=== Computer Unit Events (last 50 rounds) ===\n"
         (if (empty? rounds)
           "  (no events logged)\n"
           (str/join "\n"
                     (for [r rounds]
                       (str "  Round " r ":\n"
                            (str/join "\n" (map format-computer-event-entry (get by-round r)))))))
         "\n\n")))

(defn format-movement-entry
  "Format a single movement log entry."
  [{:keys [unit-type from to mode event reason]}]
  (str "    " (name unit-type) " " from "→" to
       " " (name mode)
       (when (not= event :move) (str " " (name event)))
       (when reason (str " (" (name reason) ")"))))

(defn- format-movement-history-section
  "Format player unit movement history for the last 20 rounds."
  []
  (let [current-round (read-runtime-state :round-number)
        min-round (max 1 (- current-round 19))
        entries (read-runtime-state :player-movement-log)
        recent-entries (filter #(<= min-round (:round %) current-round) entries)
        by-round (group-by :round recent-entries)
        rounds-with-moves (sort (keys by-round))]
    (str "=== Player Unit Movement History (last 20 rounds) ===\n"
         (if (empty? rounds-with-moves)
           "  (no movements logged)\n"
           (str/join "\n"
                     (for [r rounds-with-moves]
                       (str "  Round " r ":\n"
                            (str/join "\n" (map format-movement-entry (get by-round r)))))))
         "\n\n")))

(defn- format-map-section
  "Format a map section (game-map, player-map, or computer-map) for display."
  [label cell-map]
  (if (empty? cell-map)
    (str label ":\n  (empty)\n")
    (str label ":\n"
         (str/join "\n"
                   (for [[coord cell] (sort-by first cell-map)]
                     (str "  " (format-cell coord cell))))
         "\n")))

(defn format-dump
  "Build complete dump string with:
   - Header with round number and selection coordinates
   - Global state
   - Recent actions (last 50)
   - All cells in the region from all three maps"
  [[start-row start-col] [end-row end-col]]
  (let [region-data (dump-region [start-row start-col] [end-row end-col])
        round (read-runtime-state :round-number)
        cells-attention (read-runtime-state :cells-needing-attention)
        player-items (read-runtime-state :player-items)
        waiting (read-runtime-state :waiting-for-input)
        dest (read-runtime-state :destination)
        actions (take-last 50 (read-runtime-state :action-log))
        header (str "=== Empire Debug Dump ===\n"
                    "Round: " round "\n"
                    "Selection: [" start-row "," start-col "] to [" end-row "," end-col "]\n"
                    "Timestamp: " (System/currentTimeMillis) "\n\n")
        global-state (str "=== Global State ===\n"
                          "round-number: " round "\n"
                          "cells-needing-attention: " (pr-str cells-attention) "\n"
                          "player-items: " (pr-str player-items) "\n"
                          "waiting-for-input: " waiting "\n"
                          "destination: " (pr-str dest) "\n\n")
        actions-section (str "=== Recent Actions (last 50) ===\n"
                             (if (empty? actions)
                               "  (none)\n"
                               (str (str/join "\n" (map format-action-entry actions)) "\n"))
                             "\n")
        production-section (str "=== Production State ===\n"
                                "transport-fully-loaded?: " (read-runtime-state :transport-fully-loaded?) "\n"
                                "early-patrol-boat-produced?: " (read-runtime-state :early-patrol-boat-produced?) "\n"
                                "early-satellite-produced?: " (read-runtime-state :early-satellite-produced?) "\n"
                                (let [prod (read-runtime-state :production)]
                                  (if (empty? prod)
                                    "  (no production)\n"
                                    (str/join "\n"
                                              (for [[coords p] (sort-by first prod)]
                                                (str "  " coords " " (pr-str p))))))
                                "\n\n")
        coastline-section (format-coastline-section)
        computer-event-section (format-computer-event-section)
        movement-section (format-movement-history-section)
        maps-section (str "=== Map Data ===\n"
                          (format-map-section "game-map" (:game-map region-data))
                          "\n"
                          (format-map-section "player-map" (:player-map region-data))
                          "\n"
                          (format-map-section "computer-map" (:computer-map region-data)))]
    (str header global-state actions-section production-section coastline-section computer-event-section movement-section maps-section)))
