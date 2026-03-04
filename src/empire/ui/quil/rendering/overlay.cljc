(ns empire.ui.quil.rendering.overlay
  (:require [empire.save-load :as save-load]
            [empire.application.runtime :as app-runtime]
            [empire.movement.map-utils :as map-utils]
            [empire.ui.util.rendering.display :as display]
            [quil.core :as q]))

(defonce ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  ((:read-runtime-state @state-ctx) k))

(defn- write-runtime-state!
  [k v]
  ((:write-runtime-state! @state-ctx) k v))

(defn update-hover-status
  "Updates hover-message based on mouse position.
   Shows contents from the currently displayed map.
   Also updates load-menu-hovered when menu is open."
  []
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (read-runtime-state :load-menu-open)
      (let [files (read-runtime-state :load-menu-files)
            geom (save-load/menu-geometry (q/width) (q/height) (count files))
            idx (save-load/hovered-file-index x y geom (count files))]
        (write-runtime-state! :load-menu-hovered idx)))
    (write-runtime-state! :hover-message
                          (if (map-utils/on-map? x y)
                            (let [coords (vec (map-utils/determine-cell-coordinates x y))
                                  the-map (display/resolve-display-map (read-runtime-state :map-to-display)
                                                                       (read-runtime-state :player-map)
                                                                       (read-runtime-state :computer-map)
                                                                       (current-world))]
                              (display/compute-hover-message the-map (read-runtime-state :production) coords))
                            ""))))

(defn draw-load-menu
  "Draws the load game menu overlay when open."
  []
  (when (read-runtime-state :load-menu-open)
    (let [screen-w (q/width)
          screen-h (q/height)
          files (read-runtime-state :load-menu-files)
          file-count (count files)
          geom (save-load/menu-geometry screen-w screen-h file-count)
          hovered (read-runtime-state :load-menu-hovered)
          menu-padding save-load/menu-padding
          menu-item-height save-load/menu-item-height]
      ;; Semi-transparent overlay
      (q/fill 0 0 0 128)
      (q/rect 0 0 screen-w screen-h)
      ;; Menu background
      (q/fill 40 40 40)
      (q/stroke 255)
      (q/stroke-weight 2)
      (q/rect (:left geom) (:top geom) (:width geom) (:height geom))
      (q/stroke-weight 1)
      ;; Title
      (q/text-font (read-runtime-state :text-font))
      (q/fill 255)
      (q/text "Load Game" (+ (:left geom) menu-padding) (+ (:top geom) menu-padding 15))
      ;; File list
      (if (empty? files)
        (do
          (q/fill 180 180 180)
          (q/text "No saved games found" (+ (:left geom) menu-padding) (+ (:content-top geom) 15)))
        (doseq [[idx filename] (map-indexed vector files)]
          (let [y (+ (:content-top geom) (* idx menu-item-height))]
            (if (= idx hovered)
              ;; Inverse colors for hover
              (do
                (q/fill 255)
                (q/no-stroke)
                (q/rect (:left geom) y (:width geom) menu-item-height)
                (q/fill 0)
                (q/text filename (+ (:left geom) menu-padding) (+ y 17)))
              ;; Normal colors
              (do
                (q/fill 255)
                (q/text filename (+ (:left geom) menu-padding) (+ y 17))))))))))
