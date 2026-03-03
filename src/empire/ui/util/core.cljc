(ns empire.ui.util.core
  (:require [empire.application.runtime :as app-runtime]
            [empire.config :as config]))

(defonce ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

(defn screen->cell
  "Converts screen pixel coordinates to map cell coordinates [row col].
   Pure function - takes dimensions as parameters.
   Note: Uses legacy formula where width is divided by rows and height by cols."
  [pixel-x pixel-y map-pixel-width map-pixel-height map-rows map-cols]
  (let [cell-w (/ map-pixel-width map-rows)
        cell-h (/ map-pixel-height map-cols)]
    [(int (Math/floor (/ pixel-x cell-w)))
     (int (Math/floor (/ pixel-y cell-h)))]))

(defn compute-screen-dimensions
  "Computes pixel rendering dimensions from known map-size and fixed cell-size.
   Returns a map with :map-screen-dimensions and :text-area-dimensions."
  [cols rows cell-w cell-h]
  (let [map-display-w (* cols cell-w)
        map-display-h (* rows cell-h)
        text-h (* config/text-area-rows cell-h)
        text-x 0
        text-y (+ map-display-h config/text-area-gap)
        text-w map-display-w]
    {:map-screen-dimensions [map-display-w map-display-h]
     :text-area-dimensions [text-x text-y text-w text-h]}))

(defn calculate-screen-dimensions
  "Sets pixel rendering dimensions from known map-size and fixed cell-size."
  []
  (let [[cols rows] (read-runtime-state :map-size)
        [cell-w cell-h] config/cell-size
        dims (compute-screen-dimensions cols rows cell-w cell-h)]
    (write-runtime-state! :map-screen-dimensions (:map-screen-dimensions dims))
    (write-runtime-state! :text-area-dimensions (:text-area-dimensions dims))))

(defn parse-args
  "Parses command-line args into a map of {:cols :rows :seed :window-w :window-h}.
   Throws ex-info if map exceeds screen bounds."
  [args screen-w screen-h]
  (let [seed (some #(when (.startsWith ^String % "--seed=")
                      (Long/parseLong (subs % 7))) args)
        non-seed (remove #(.startsWith ^String % "--seed=") args)
        [cols rows] (if (>= (count non-seed) 2)
                      [(Integer/parseInt (first non-seed))
                       (Integer/parseInt (second non-seed))]
                      config/default-map-size)
        [cell-w cell-h] config/cell-size
        text-area-h (* config/text-area-rows cell-h)
        window-w (* cols cell-w)
        window-h (+ (* rows cell-h) text-area-h config/text-area-gap)
        max-cols (quot screen-w cell-w)
        max-rows (quot (- screen-h text-area-h config/text-area-gap) cell-h)]
    (when (or (> window-w screen-w) (> window-h screen-h))
      (throw (ex-info "Map exceeds monitor bounds"
                      {:cols cols :rows rows :screen-w screen-w :screen-h screen-h
                       :max-cols max-cols :max-rows max-rows})))
    {:cols cols :rows rows :seed seed :window-w window-w :window-h window-h}))

(defn key-released [_ _]
  (write-runtime-state! :last-key nil))
