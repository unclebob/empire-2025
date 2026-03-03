;; mutation-tested: 2026-03-01
(ns empire.ui.util.rendering.display
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.containers.helpers :as uc]
            [empire.movement.lakes :as lakes]
            [empire.ui.util.rendering.format :as fmt]))

(def ^:private default-cell-color [0 0 0])
(def ^:private lake-cell-color [0 120 220])
(defonce ^:private lake-cache* (atom {:map nil :limit nil :cells #{}}))

(defn- safe-color
  [cell]
  (or (config/color-of cell) default-cell-color))

(defn resolve-display-map
  "Returns the appropriate map based on map-to-display keyword."
  [map-to-display player-map computer-map game-map]
  ({:player-map player-map :computer-map computer-map :actual-map game-map} map-to-display))

(defn compute-hover-message
  "Looks up cell and production at coords in the-map, returns formatted hover string.
   Returns empty string when cell has no displayable status."
  [the-map production-map coords]
  (let [cell (get-in the-map coords)
        production (get production-map coords)]
    (or (fmt/format-hover-status coords cell production) "")))

(defn compute-hover-result
  "Computes hover message from map display state and mouse position."
  [map-to-display player-map computer-map game-map production coords]
  (let [the-map (resolve-display-map map-to-display player-map computer-map game-map)]
    (compute-hover-message the-map production coords)))

(defn determine-display-unit
  "Determines which unit to display, handling attention blinking.
   attention-coords is the list of cells needing attention (or nil).
   blink? is the current blink state for contained unit display."
  [col row cell attention-coords blink?]
  (let [contents (:contents cell)
        has-awake-airport? (uc/has-awake? cell :awake-fighters)
        has-any-airport? (pos? (uc/get-count cell :fighter-count))
        has-awake-carrier? (uc/has-awake-carrier-fighter? contents)
        has-awake-army? (uc/has-awake-army-aboard? contents)
        has-contained-unit? (or has-awake-airport? has-awake-carrier? has-awake-army?)
        is-attention-cell? (and (seq attention-coords) (= [col row] (first attention-coords)))
        show-contained? (and is-attention-cell? has-contained-unit? blink?)]
    (cond
      show-contained?
      (uc/blinking-contained-unit has-awake-airport? has-awake-carrier? has-awake-army?)

      (and is-attention-cell? has-awake-airport?)
      nil ;; Hide airport fighter on alternate blink frame

      :else
      (uc/normal-display-unit cell contents has-awake-airport? has-any-airport?))))

(defn production-indicator-data
  "Returns production indicator rendering data for a cell, or nil if none needed."
  [row col cell production]
  (when-let [prod (and (= :city (:type cell))
                       (= :player (:city-status cell))
                       (get production [col row]))]
    (when (and (map? prod) (:item prod))
      (let [item (:item prod)
            total (config/item-cost item)
            remaining (:remaining-rounds prod)
            progress (/ (- total remaining) (double total))
            base-color (safe-color cell)
            dark-color (mapv #(* % 0.5) base-color)]
        {:prod-char (config/item-chars item)
         :progress progress
         :remaining remaining
         :dark-color dark-color}))))

(defn group-cells-by-color
  "Groups map cells by their display color for batched rendering.
   Returns a map of [r g b] color to seq of {:col :row :cell} maps.
   blink-attention? and blink-completed? control flash states for attention cells and completed cities."
  ([the-map attention-coords production blink-attention? blink-completed?]
   (group-cells-by-color the-map attention-coords production blink-attention? blink-completed? :player-map))
  ([the-map attention-coords production blink-attention? blink-completed? map-to-display]
   (let [cols (count the-map)
        rows (count (first the-map))
        attention-cell (first attention-coords)
        lake-limit @atoms/lake-max-cells
        lake-cells (if (and (= :computer-map map-to-display) (pos? lake-limit))
                     (let [{:keys [map limit cells]} @lake-cache*]
                       (if (and (identical? map the-map) (= limit lake-limit))
                         cells
                         (let [computed (lakes/lake-cells the-map lake-limit)]
                           (reset! lake-cache* {:map the-map :limit lake-limit :cells computed})
                           computed)))
                     #{})]
    (reduce
     (fn [acc [col row]]
       (let [cell (get-in the-map [col row])]
         (if (= :unexplored (:type cell))
           acc
           (let [base-color (if (and (= :sea (:type cell))
                                     (contains? lake-cells [col row]))
                              lake-cell-color
                              (safe-color cell))
                 current [col row]
                 should-flash-black (= current attention-cell)
                 completed? (and (= (:type cell) :city)
                                 (= :player (:city-status cell))
                                 (let [prod (production [col row])]
                                   (and (map? prod) (= (:remaining-rounds prod) 0))))
                 blink-white? (and completed? blink-completed?)
                 blink-black? (and should-flash-black blink-attention?)
                 final-color (cond blink-black? [0 0 0]
                                   blink-white? [255 255 255]
                                   :else base-color)]
             (update acc final-color conj {:col col :row row :cell cell})))))
     {}
     (for [col (range cols) row (range rows)] [col row])))))

(defn should-show-error?
  "Returns true if the error message should be shown."
  [error-until]
  (< (System/currentTimeMillis) error-until))

(defn resolve-turn-text
  "Returns the turn text to display, falling back to destination."
  [turn-message destination]
  (cond
    (seq turn-message) turn-message
    destination (format (:destination config/messages) (first destination) (second destination))
    :else nil))

(defn resolve-round-status-text
  "Returns the round status text with optional PAUSED prefix."
  [round-number paused pause-requested]
  (let [round-str (str "Round: " round-number)]
    (if (fmt/should-show-paused? paused pause-requested)
      {:text (str "PAUSED  " round-str) :paused? true :round-str round-str}
      {:text round-str :paused? false})))
