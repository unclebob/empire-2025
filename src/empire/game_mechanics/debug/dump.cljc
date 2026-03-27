(ns empire.game-mechanics.debug.dump
  "Region-based debug dump rendering and file output helpers."
  (:require [clojure.string :as str]
            [empire.game-mechanics.debug.dump.io :as io]
            [empire.game-mechanics.debug.dump.sections :as sections]
            [empire.state.api :as sa]))

(defn dump-region
  "Extract cells from all three maps for a coordinate range.
   Takes [start-row start-col] [end-row end-col].
   Returns {:game-map {...} :player-map {...} :computer-map {...}}
   where each map is {[row col] cell-data}."
  [[start-row start-col] [end-row end-col]]
  (let [game-map (sa/current-world)
        player-map (sa/read-state :player-map)
        computer-map (sa/read-state :computer-map)
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
                         [:major-invasion "major-invasion"]
                         [:kamikazee "kamikazee"]
                         [:kamikazee-stage "k-stage"]
                         [:army-count "army-count"]
                         [:fighter-count "fighter-count"]
                         [:transport-mission "transport-mission"]
                         [:hold-sail-to-load-since-round "hold-since"]
                         [:load-plan-failure "load-plan-failure"]
                         [:load-manifest "load-manifest"]
                         [:sail-path "sail-path"]
                         [:load-target-cell "load-target"]
                         [:country-id "cid"]
                         [:unload-event-id "eid"]
                         [:attack-target "atk-target"]
                         [:major-invasion-target "mi-target"]
                         [:kamikazee-terminal-site "k-terminal"]
                         [:kamikazee-wait-site "k-wait"]
                         [:kamikazee-hunt-resume-pos "k-resume"]
                         [:kamikazee-route "k-route"]
                         [:kamikazee-targets "k-targets"]
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

(defn- full-map-data
  []
  (let [game-map (sa/current-world)
        coords (for [row (range (count game-map))
                     col (range (count (first game-map)))]
                 [row col])]
    (into {}
          (for [coord coords
                :let [cell (get-in game-map coord)]
                :when cell]
            [coord cell]))))

(defn format-dump
  "Build complete dump string with:
   - Header with round number and selection coordinates
   - Global state
   - Recent actions (last 50)
   - All cells in the region from all three maps"
  [[start-row start-col] [end-row end-col]]
  (let [region-data (dump-region [start-row start-col] [end-row end-col])
        round (sa/read-state :round-number)
        cells-attention (sa/read-state :cells-needing-attention)
        player-items (sa/read-state :player-items)
        waiting (sa/read-state :waiting-for-input)
        dest (sa/read-state :destination)
        actions (take-last 50 (sa/read-state :action-log))
        header (str "=== Empire Debug Dump ===\n"
                    "Round: " round "\n"
                    "Selected Rectangle: [" start-row "," start-col "] to [" end-row "," end-col "]\n"
                    "Selected Rectangle Size: "
                    (inc (- end-row start-row)) "x" (inc (- end-col start-col)) "\n"
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
                                "transport-fully-loaded?: " (sa/read-state :transport-fully-loaded?) "\n"
                                "early-patrol-boat-produced?: " (sa/read-state :early-patrol-boat-produced?) "\n"
                                "early-satellite-produced?: " (sa/read-state :early-satellite-produced?) "\n"
                                (let [prod (sa/read-state :production)]
                                  (if (empty? prod)
                                    "  (no production)\n"
                                    (str/join "\n"
                                              (for [[coords p] (sort-by first prod)]
                                                (str "  " coords " " (pr-str p))))))
                                "\n\n")
        invasion-section (sections/format-major-invasion-section)
        coastline-section (sections/format-coastline-section)
        reservation-section (sections/format-transport-reservations-section)
        computer-event-section (sections/format-computer-event-section)
        movement-section (sections/format-movement-history-section)
        kamikazee-fighter-section (sections/format-kamikazee-fighter-section (:game-map region-data))
        kamikazee-airport-section (sections/format-kamikazee-airport-section (:game-map region-data))
        maps-section (str "=== Selected Region Map Data ===\n"
                          (format-map-section "selected-region game-map" (:game-map region-data))
                          "\n"
                          (format-map-section "selected-region player-map" (:player-map region-data))
                          "\n"
                          (format-map-section "selected-region computer-map" (:computer-map region-data))
                          "\n"
                          "=== Full Game Map ===\n"
                          (format-map-section "full game-map" (full-map-data)))]
    (str header global-state actions-section production-section invasion-section coastline-section reservation-section computer-event-section movement-section kamikazee-fighter-section kamikazee-airport-section maps-section)))

;; Re-exports from daughter modules
(def format-movement-entry sections/format-movement-entry)
(def screen-coords-to-cell-range io/screen-coords-to-cell-range)
(def generate-dump-filename io/generate-dump-filename)

(defn write-dump!
  "Write formatted dump to timestamped file in project root.
   Filename format: debug-YYYY-MM-DD-HHMMSS.txt
   Takes the coordinate range, calls format-dump, writes to file."
  [[start-row start-col] [end-row end-col]]
  #?(:clj
     (let [filename (generate-dump-filename)
           content (format-dump [start-row start-col] [end-row end-col])]
       (spit filename content)
       filename)
     :cljs
     (format-dump [start-row start-col] [end-row end-col])))

(defn write-full-dump!
  []
  (let [[rows cols] (or (sa/read-state :map-size) [0 0])]
    (write-dump! [0 0] [(max 0 (dec rows)) (max 0 (dec cols))])))

