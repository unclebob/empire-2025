(ns empire.ui.util.rendering.display.colors
  (:require [clojure.string :as str]
            [empire.state.api :as sa]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.game-mechanics.movement.movement-state :as movement-state]
            [empire.game-mechanics.movement.lakes :as lakes]))

(def ^:private default-cell-color [0 0 0])
(def ^:private lake-cell-color [0 120 220])
(def ^:private attention-flash-cell-color [255 255 255])
(def ^:private attention-flash-unit-color [0 0 0])
(def ^:private attention-normal-unit-color [255 255 255])

(defn- safe-color
  [cell]
  (or (config/color-of cell) default-cell-color))

(defn- contained-unit?
  [cell contents]
  (or (pos? (:fighter-count cell 0))
      (uc/has-awake-carrier-fighter? contents)
      (uc/has-awake-army-aboard? contents)))

(defn- attention-display-unit
  [cell coords contents attention-coords]
  (when (and (= coords (first attention-coords))
             (or (and contents (:type contents))
                 (contained-unit? cell contents)))
    (or (movement-state/get-active-unit cell coords) contents)))

(defn determine-display-unit
  "Determines which unit to display, handling attention blinking.
   attention-coords is the list of cells needing attention (or nil).
   blink? is the current blink state for contained unit display."
  [col row cell attention-coords blink?]
  (let [contents (:contents cell)
        has-airport-fighter? (pos? (:fighter-count cell 0))
        has-awake-airport? (uc/has-awake? cell :awake-fighters)]
    (or (attention-display-unit cell [col row] contents attention-coords)
        (uc/normal-display-unit cell contents has-awake-airport? has-airport-fighter?))))

(defn attention-unit-color
  "Returns the color for a displayed unit. Black when cell is flashing white,
   normal color otherwise."
  [display-unit cell-flashing?]
  (if (and display-unit cell-flashing?)
    attention-flash-unit-color
    (config/unit->color display-unit)))

(defn- show-city-production?
  [cell map-to-display]
  (and (= :city (:type cell))
       (or (= :player (:city-status cell))
           (and (= :computer (:city-status cell))
                (contains? #{:computer-map :player-map} map-to-display)))))

(defn- player-production-visible?
  [cell map-to-display]
  (and (= :player (:city-status cell))
       (contains? #{:player-map :actual-map} map-to-display)))

(defn- computer-own-production-visible?
  [cell map-to-display]
  (and (= :computer (:city-status cell))
       (= :computer-map map-to-display)))

(defn- computer-known-production-visible?
  [cell map-to-display]
  (and (= :computer (:city-status cell))
       (= :player-map map-to-display)))

(defn- production-entry-for-display
  [cell production coords map-to-display]
  (cond
    (player-production-visible? cell map-to-display)
    (get production coords)

    (computer-own-production-visible? cell map-to-display)
    (get production coords)

    (computer-known-production-visible? cell map-to-display)
    (:known-production cell)

    :else nil))

(defn- production-char-for-display
  [cell item map-to-display]
  (let [char (config/item-chars item)]
    (if (and (= :computer (:city-status cell))
             (= :player-map map-to-display))
      (str/lower-case char)
      char)))

(defn production-indicator-data
  "Returns production indicator rendering data for a cell, or nil if none needed."
  ([row col cell production]
   (production-indicator-data row col cell production :player-map))
  ([row col cell production map-to-display]
   (when-let [prod (and (show-city-production? cell map-to-display)
                        (production-entry-for-display cell production [col row] map-to-display))]
    (when (and (map? prod) (:item prod))
      (let [item (:item prod)
            total (config/item-cost item)
            remaining (:remaining-rounds prod)
            progress (/ (- total remaining) (double total))
            base-color (safe-color cell)
            dark-color (mapv #(* % 0.5) base-color)]
        {:prod-char (production-char-for-display cell item map-to-display)
         :progress progress
         :remaining remaining
         :dark-color dark-color})))))

(defonce ^:private lake-cache* (atom {:map nil :limit nil :cells #{}}))

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

(defn completed-production-city? [cell production current]
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
      flash-attention? attention-flash-cell-color
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-07T17:48:58.792008-05:00", :module-hash "-916688374", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-679499033"} {:id "def/default-cell-color", :kind "def", :line 9, :end-line 9, :hash "-1922923999"} {:id "def/lake-cell-color", :kind "def", :line 10, :end-line 10, :hash "-600733486"} {:id "def/attention-flash-cell-color", :kind "def", :line 11, :end-line 11, :hash "-652560915"} {:id "def/attention-flash-unit-color", :kind "def", :line 12, :end-line 12, :hash "2066335485"} {:id "def/attention-normal-unit-color", :kind "def", :line 13, :end-line 13, :hash "-1032802797"} {:id "defn-/safe-color", :kind "defn-", :line 15, :end-line 17, :hash "-1335025619"} {:id "defn-/contained-unit?", :kind "defn-", :line 19, :end-line 23, :hash "1490892828"} {:id "defn-/attention-display-unit", :kind "defn-", :line 25, :end-line 30, :hash "-1614245067"} {:id "defn/determine-display-unit", :kind "defn", :line 32, :end-line 41, :hash "-248118789"} {:id "defn/attention-unit-color", :kind "defn", :line 43, :end-line 49, :hash "574643494"} {:id "defn-/show-city-production?", :kind "defn-", :line 51, :end-line 56, :hash "991632568"} {:id "defn-/production-entry-for-display", :kind "defn-", :line 58, :end-line 73, :hash "314423924"} {:id "defn-/production-char-for-display", :kind "defn-", :line 75, :end-line 81, :hash "904025350"} {:id "defn/production-indicator-data", :kind "defn", :line 83, :end-line 100, :hash "1578241208"} {:id "form/15/defonce", :kind "defonce", :line 102, :end-line 102, :hash "1764353803"} {:id "defn-/lake-cells-for-display", :kind "defn-", :line 104, :end-line 113, :hash "-1833440901"} {:id "defn/completed-production-city?", :kind "defn", :line 115, :end-line 119, :hash "-560500174"} {:id "defn-/cell-base-color", :kind "defn-", :line 121, :end-line 125, :hash "1147982518"} {:id "defn-/final-cell-color", :kind "defn-", :line 127, :end-line 135, :hash "-1709973204"} {:id "defn/group-cells-by-color", :kind "defn", :line 137, :end-line 159, :hash "1520872785"}]}
;; clj-mutate-manifest-end
