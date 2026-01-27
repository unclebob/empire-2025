(ns empire.atoms-helpers
  "Common atom manipulation patterns for reducing coupling.

   These helpers provide a cleaner API for common game-state operations,
   encapsulating the underlying atom structure and reducing direct atom access."
  (:require [empire.atoms :as atoms]))

;; Cell manipulation

(defn update-cell!
  "Update a cell in the game map using function f."
  [coords f]
  (swap! atoms/game-map update-in coords f))

(defn get-cell
  "Get a cell from the game map."
  [coords]
  (get-in @atoms/game-map coords))

(defn set-cell-contents!
  "Set the contents of a cell."
  [coords contents]
  (swap! atoms/game-map assoc-in (conj coords :contents) contents))

(defn clear-cell-contents!
  "Clear the contents of a cell."
  [coords]
  (swap! atoms/game-map update-in coords dissoc :contents))

(defn set-cell-field!
  "Set a specific field on a cell."
  [coords field value]
  (swap! atoms/game-map assoc-in (conj coords field) value))

;; Unit manipulation

(defn get-unit
  "Get the unit at the given coordinates."
  [coords]
  (:contents (get-cell coords)))

(defn update-unit!
  "Update the unit at coords using function f."
  [coords f]
  (when-let [unit (get-unit coords)]
    (set-cell-contents! coords (f unit))))

(defn set-unit-field!
  "Set a field on the unit at coords."
  [coords field value]
  (swap! atoms/game-map assoc-in (conj coords :contents field) value))

;; Message helpers

(defn set-message!
  "Set the main game message."
  [msg]
  (reset! atoms/message msg))

(defn clear-message!
  "Clear the main game message."
  []
  (reset! atoms/message ""))

(defn show-warning!
  "Show a flashing warning message for the specified duration."
  [msg duration-ms]
  (atoms/set-line3-message msg duration-ms))

(defn show-confirmation!
  "Show a confirmation message for the specified duration."
  [msg duration-ms]
  (atoms/set-confirmation-message msg duration-ms))

;; Game state helpers

(defn waiting-for-input?
  "Returns true if waiting for user input."
  []
  @atoms/waiting-for-input)

(defn set-waiting-for-input!
  "Set the waiting-for-input flag."
  [waiting?]
  (reset! atoms/waiting-for-input waiting?))

(defn paused?
  "Returns true if the game is paused."
  []
  @atoms/paused)

(defn current-attention-coords
  "Get the coordinates of the current cell needing attention."
  []
  (first @atoms/cells-needing-attention))

(defn map-dimensions
  "Returns [height width] of the game map."
  []
  [(count @atoms/game-map)
   (count (first @atoms/game-map))])
