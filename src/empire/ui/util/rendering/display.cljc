(ns empire.ui.util.rendering.display
  (:require [clojure.string :as str]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.movement.lakes :as lakes]
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

(defn- show-city-production?
  [cell map-to-display]
  (and (= :city (:type cell))
       (or (= :player (:city-status cell))
           (and (= :computer (:city-status cell))
                (= :computer-map map-to-display)))))

(defn production-indicator-data
  "Returns production indicator rendering data for a cell, or nil if none needed."
  ([row col cell production]
   (production-indicator-data row col cell production :player-map))
  ([row col cell production map-to-display]
   (when-let [prod (and (show-city-production? cell map-to-display)
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
         :dark-color dark-color})))))

(defn- lake-cells-for-display [the-map map-to-display]
  (let [lake-limit (sa/read-state :lake-max-cells)]
    (if (and (= :computer-map map-to-display) (pos? lake-limit))
      (let [{:keys [map limit cells]} @lake-cache*]
        (if (and (identical? map the-map) (= limit lake-limit))
          cells
          (let [computed (lakes/lake-cells the-map lake-limit)]
            (reset! lake-cache* {:map the-map :limit lake-limit :cells computed})
            computed)))
      #{})))

(defn- completed-production-city? [cell production current]
  (and (= (:type cell) :city)
       (= :player (:city-status cell))
       (let [prod (production current)]
         (and (map? prod) (zero? (:remaining-rounds prod))))))

(defn- cell-base-color [cell current lake-cells]
  (if (and (= :sea (:type cell))
           (contains? lake-cells current))
    lake-cell-color
    (safe-color cell)))

(defn- final-cell-color
  [cell attention-cell production blink-attention? blink-completed? current lake-cells]
  (let [base-color (cell-base-color cell current lake-cells)
        flash-attention? (and (= current attention-cell) blink-attention?)
        flash-completed? (and (completed-production-city? cell production current) blink-completed?)]
    (cond
      flash-attention? [0 0 0]
      flash-completed? [255 255 255]
      :else base-color)))

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
         lake-cells (lake-cells-for-display the-map map-to-display)]
    (reduce
     (fn [acc [col row]]
       (let [cell (get-in the-map [col row])]
         (if (= :unexplored (:type cell))
           acc
           (let [current [col row]
                 final-color (final-cell-color cell attention-cell production
                                               blink-attention? blink-completed?
                                               current lake-cells)]
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

(defn resolve-center-lines
  "Returns up to three center-region lines.
   Always derived from debug-message text."
  [_map-to-display _major-invasion-state _round-number debug-message]
  (->> (str/split (or debug-message "") #"\n")
       (take 3)
       (filter seq)
       vec))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:03:37.199523-05:00", :module-hash "-1185310854", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "2128578818"} {:id "def/default-cell-color", :kind "def", :line 9, :end-line 9, :hash "-1922923999"} {:id "def/lake-cell-color", :kind "def", :line 10, :end-line 10, :hash "-600733486"} {:id "form/3/defonce", :kind "defonce", :line 11, :end-line 11, :hash "1764353803"} {:id "defn-/safe-color", :kind "defn-", :line 12, :end-line 14, :hash "-1335025619"} {:id "defn/resolve-display-map", :kind "defn", :line 16, :end-line 19, :hash "-442023512"} {:id "defn/compute-hover-message", :kind "defn", :line 21, :end-line 27, :hash "1130106368"} {:id "defn/compute-hover-result", :kind "defn", :line 29, :end-line 33, :hash "1222432952"} {:id "defn/determine-display-unit", :kind "defn", :line 35, :end-line 56, :hash "1957051614"} {:id "defn-/show-city-production?", :kind "defn-", :line 58, :end-line 63, :hash "1360271203"} {:id "defn/production-indicator-data", :kind "defn", :line 65, :end-line 82, :hash "1241042084"} {:id "defn-/lake-cells-for-display", :kind "defn-", :line 84, :end-line 93, :hash "-1833440901"} {:id "defn-/completed-production-city?", :kind "defn-", :line 95, :end-line 99, :hash "-1046378919"} {:id "defn-/cell-base-color", :kind "defn-", :line 101, :end-line 105, :hash "1147982518"} {:id "defn-/final-cell-color", :kind "defn-", :line 107, :end-line 115, :hash "-98827088"} {:id "defn/group-cells-by-color", :kind "defn", :line 117, :end-line 139, :hash "1520872785"} {:id "defn/should-show-error?", :kind "defn", :line 141, :end-line 144, :hash "1593526722"} {:id "defn/resolve-turn-text", :kind "defn", :line 146, :end-line 152, :hash "218744102"} {:id "defn/resolve-round-status-text", :kind "defn", :line 154, :end-line 160, :hash "2046987515"} {:id "defn/resolve-center-lines", :kind "defn", :line 162, :end-line 169, :hash "245117443"}]}
;; clj-mutate-manifest-end
