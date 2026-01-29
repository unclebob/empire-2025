(ns empire.debug
  "Debug utilities for dumping game state to file.
   Provides circular action logging and region-based state dumps."
  (:require [empire.atoms :as atoms]
            [empire.ui.coordinates :as coords]
            [clojure.string :as str])
  #?(:clj (:import [java.time LocalDateTime]
                   [java.time.format DateTimeFormatter])))

(def ^:private max-action-log-size 100)

(defn log-action!
  "Append action to circular buffer with timestamp. Cap at 100 entries.
   Takes an action vector (e.g., [:move :army [4,6] [4,7]]).
   Adds {:timestamp <ms> :action action} to the action-log atom.
   If log exceeds 100 entries, drops oldest."
  [action]
  (let [entry {:timestamp (System/currentTimeMillis)
               :action action}]
    (swap! atoms/action-log
           (fn [log]
             (let [new-log (conj log entry)]
               (if (> (count new-log) max-action-log-size)
                 (vec (drop (- (count new-log) max-action-log-size) new-log))
                 new-log))))))

(defn dump-region
  "Extract cells from all three maps for a coordinate range.
   Takes [start-row start-col] [end-row end-col].
   Returns {:game-map {...} :player-map {...} :computer-map {...}}
   where each map is {[row col] cell-data}."
  [[start-row start-col] [end-row end-col]]
  (let [game-map @atoms/game-map
        player-map @atoms/player-map
        computer-map @atoms/computer-map
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
                         [:transport-mission "transport-mission"]]
        optional-strs (for [[k label] optional-fields
                            :let [v (get contents k)]
                            :when v]
                        (str " " label ":" v))]
    (str " contents:{type:" (name (:type contents))
         " owner:" (name (:owner contents))
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

(defn format-dump
  "Build complete dump string with:
   - Header with round number and selection coordinates
   - Global state
   - Recent actions (last 50)
   - All cells in the region from all three maps"
  [[start-row start-col] [end-row end-col]]
  (let [region-data (dump-region [start-row start-col] [end-row end-col])
        round @atoms/round-number
        cells-attention @atoms/cells-needing-attention
        player-items @atoms/player-items
        waiting @atoms/waiting-for-input
        dest @atoms/destination
        actions (take-last 50 @atoms/action-log)
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
        maps-section (str "=== Map Data ===\n"
                          (format-map-section "game-map" (:game-map region-data))
                          "\n"
                          (format-map-section "player-map" (:player-map region-data))
                          "\n"
                          (format-map-section "computer-map" (:computer-map region-data)))]
    (str header global-state actions-section maps-section)))

(defn generate-dump-filename
  "Generate a timestamped filename for the dump file.
   Format: debug-YYYY-MM-DD-HHMMSS.txt"
  []
  #?(:clj
     (let [now (LocalDateTime/now)
           formatter (DateTimeFormatter/ofPattern "yyyy-MM-dd-HHmmss")]
       (str "debug-" (.format now formatter) ".txt"))
     :cljs
     (let [now (js/Date.)
           pad (fn [n] (if (< n 10) (str "0" n) (str n)))
           year (.getFullYear now)
           month (pad (inc (.getMonth now)))
           day (pad (.getDate now))
           hour (pad (.getHours now))
           min (pad (.getMinutes now))
           sec (pad (.getSeconds now))]
       (str "debug-" year "-" month "-" day "-" hour min sec ".txt"))))

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
     ;; ClojureScript version - just return the content
     ;; (actual file writing would require node.js or browser download)
     (format-dump [start-row start-col] [end-row end-col])))

(defn screen-coords-to-cell-range
  "Convert screen pixel coordinates to map cell coordinate range.
   Takes two [x y] screen coordinate pairs (drag start and end).
   Returns [[start-row start-col] [end-row end-col]] normalized so
   start is top-left and coordinates are clamped to map bounds."
  [[x1 y1] [x2 y2]]
  (let [[map-w map-h] @atoms/map-screen-dimensions
        game-map @atoms/game-map
        map-rows (count game-map)
        map-cols (count (first game-map))
        ;; Convert screen coords to cell coords
        [row1 col1] (coords/screen->cell x1 y1 map-w map-h map-rows map-cols)
        [row2 col2] (coords/screen->cell x2 y2 map-w map-h map-rows map-cols)
        ;; Normalize so start <= end
        start-row (min row1 row2)
        end-row (max row1 row2)
        start-col (min col1 col2)
        end-col (max col1 col2)
        ;; Clamp to map bounds
        start-row (max 0 start-row)
        start-col (max 0 start-col)
        end-row (min (dec map-rows) end-row)
        end-col (min (dec map-cols) end-col)]
    [[start-row start-col] [end-row end-col]]))
