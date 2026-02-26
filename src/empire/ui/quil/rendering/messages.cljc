(ns empire.ui.quil.rendering.messages
  (:require [clojure.string :as str]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.movement.map-utils :as map-utils]
            [empire.ui.util.rendering.format :as fmt]
            [quil.core :as q]))

(defn- draw-text-right-justified
  "Draws text right-justified against the given right edge at vertical position y."
  [text right-edge y]
  (let [text-width (q/text-width text)
        x (- right-edge text-width)]
    (q/text text x y)))

;; --- Game Info region (left-justified, 37.5%) ---

(defn- draw-attention
  "Draws the attention message (row 1) left-justified in the Game Info region."
  [left-x text-y]
  (when (seq @atoms/attention-message)
    (q/text @atoms/attention-message (+ left-x config/msg-left-padding) (+ text-y config/msg-line-1-y))))

(defn- draw-turn
  "Draws the turn message (row 2) left-justified in the Game Info region.
   Falls back to destination display when no turn message is active."
  [left-x text-y]
  (let [msg @atoms/turn-message
        dest @atoms/destination]
    (cond
      (seq msg)
      (q/text msg (+ left-x config/msg-left-padding) (+ text-y config/msg-line-2-y))

      dest
      (let [dest-str (format (:destination config/messages) (first dest) (second dest))]
        (q/text dest-str (+ left-x config/msg-left-padding) (+ text-y config/msg-line-2-y))))))

(defn- draw-error
  "Draws the error message (row 3) in red, flashing, left-justified in the Game Info region."
  [left-x text-y]
  (when (< (System/currentTimeMillis) @atoms/error-until)
    (when (and (seq @atoms/error-message)
               (map-utils/blink? 500))
      (q/fill 255 0 0)
      (q/text @atoms/error-message (+ left-x config/msg-left-padding) (+ text-y config/msg-line-3-y))
      (q/fill 255))))

(defn- draw-game-info
  "Draws the Game Info region (left): attention, turn, error."
  [left-x text-y]
  (draw-attention left-x text-y)
  (draw-turn left-x text-y)
  (draw-error left-x text-y))

;; --- Debug region (centered, 25%) ---

(defn- draw-debug
  "Draws the debug message centered in the Debug region."
  [debug-x debug-w text-y]
  (when (seq @atoms/debug-message)
    (let [center-x (+ debug-x (/ debug-w 2))
          lines (str/split @atoms/debug-message #"\n")
          y-offsets [config/msg-line-1-y config/msg-line-2-y config/msg-line-3-y]]
      (q/fill 0 255 255)
      (doseq [[line y-off] (map vector (take 3 lines) y-offsets)]
        (let [msg-width (q/text-width line)
              msg-x (- center-x (/ msg-width 2))]
          (q/text line msg-x (+ text-y y-off))))
      (q/fill 255))))

;; --- Game Status region (right-justified, 37.5%) ---

(defn- draw-round-status
  "Draws round status (row 1) right-justified. Prepends red PAUSED when paused."
  [right-edge text-y]
  (let [round-str (str "Round: " @atoms/round-number)]
    (if (fmt/should-show-paused? @atoms/paused @atoms/pause-requested)
      (let [full-str (str "PAUSED  " round-str)
            full-width (q/text-width full-str)
            x (- right-edge full-width)
            paused-width (q/text-width "PAUSED  ")]
        (q/fill 255 0 0)
        (q/text "PAUSED  " x (+ text-y config/msg-line-1-y))
        (q/fill 255)
        (q/text round-str (+ x paused-width) (+ text-y config/msg-line-1-y)))
      (draw-text-right-justified round-str right-edge (+ text-y config/msg-line-1-y)))))

(defn- draw-hover-info
  "Draws hover info (row 2) right-justified in the Game Status region."
  [right-edge text-y]
  (when (seq @atoms/hover-message)
    (draw-text-right-justified @atoms/hover-message right-edge (+ text-y config/msg-line-2-y))))

(defn- draw-production-status
  "Draws production status (row 3) right-justified in the Game Status region."
  [right-edge text-y]
  (when (seq @atoms/production-status)
    (draw-text-right-justified @atoms/production-status right-edge (+ text-y config/msg-line-3-y))))

(defn- draw-game-status
  "Draws the Game Status region (right): round status, hover info, production."
  [right-edge text-y]
  (draw-round-status right-edge text-y)
  (draw-hover-info right-edge text-y)
  (draw-production-status right-edge text-y))

;; --- Message area master function ---

(defn draw-message-area
  "Draws the three-region message area below the map."
  []
  (let [[text-x text-y text-w _] @atoms/text-area-dimensions
        info-w (* text-w config/game-info-width-fraction)
        debug-w (* text-w config/debug-width-fraction)
        debug-x (+ text-x info-w)
        right-edge (+ text-x text-w)]
    (q/stroke 255)
    (q/line text-x (- text-y config/msg-separator-offset) (+ text-x text-w) (- text-y config/msg-separator-offset))
    (q/text-font @atoms/text-font)
    (q/fill 255)
    (draw-game-info text-x text-y)
    (draw-debug debug-x debug-w text-y)
    (draw-game-status right-edge text-y)))
