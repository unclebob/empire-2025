(ns empire.game-mechanics.debug.dump.io
  "File I/O and screen utilities for debug dump."
  (:require [empire.state.api :as sa])
  #?(:clj (:import [java.time LocalDateTime]
                   [java.time.format DateTimeFormatter])))

(defn screen->cell
  [pixel-x pixel-y map-pixel-width map-pixel-height map-rows map-cols]
  (let [cell-w (/ map-pixel-width map-rows)
        cell-h (/ map-pixel-height map-cols)]
    [(int (Math/floor (/ pixel-x cell-w)))
     (int (Math/floor (/ pixel-y cell-h)))]))

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

(defn screen-coords-to-cell-range
  "Convert screen pixel coordinates to map cell coordinate range.
   Takes two [x y] screen coordinate pairs (drag start and end).
   Returns [[start-row start-col] [end-row end-col]] normalized so
   start is top-left and coordinates are clamped to map bounds."
  [[x1 y1] [x2 y2]]
  (let [[map-w map-h] (sa/read-state :map-screen-dimensions)
        game-map (sa/current-world)
        map-rows (count game-map)
        map-cols (count (first game-map))
        [row1 col1] (screen->cell x1 y1 map-w map-h map-rows map-cols)
        [row2 col2] (screen->cell x2 y2 map-w map-h map-rows map-cols)
        start-row (min row1 row2)
        end-row (max row1 row2)
        start-col (min col1 col2)
        end-col (max col1 col2)
        start-row (max 0 start-row)
        start-col (max 0 start-col)
        end-row (min (dec map-rows) end-row)
        end-col (min (dec map-cols) end-col)]
    [[start-row start-col] [end-row end-col]]))
