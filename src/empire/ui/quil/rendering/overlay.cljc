(ns empire.ui.quil.rendering.overlay
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.save-load :as save-load]
            [empire.ui.util.rendering.display :as display]
            [quil.core :as q]))

(defn update-hover-status
  "Updates hover-message based on mouse position.
   Shows contents from the currently displayed map.
   Also updates load-menu-hovered when menu is open."
  []
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when @atoms/load-menu-open
      (let [files @atoms/load-menu-files
            geom (save-load/menu-geometry (q/width) (q/height) (count files))
            idx (save-load/hovered-file-index x y geom (count files))]
        (reset! atoms/load-menu-hovered idx)))
    (reset! atoms/hover-message
            (if (map-utils/on-map? x y)
              (let [coords (vec (map-utils/determine-cell-coordinates x y))
                    the-map (display/resolve-display-map @atoms/map-to-display
                              @atoms/player-map @atoms/computer-map @atoms/game-map)]
                (display/compute-hover-message the-map @atoms/production coords))
              ""))))

(defn draw-load-menu
  "Draws the load game menu overlay when open."
  []
  (when @atoms/load-menu-open
    (let [screen-w (q/width)
          screen-h (q/height)
          files @atoms/load-menu-files
          file-count (count files)
          geom (save-load/menu-geometry screen-w screen-h file-count)
          hovered @atoms/load-menu-hovered]
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
      (q/text-font @atoms/text-font)
      (q/fill 255)
      (q/text "Load Game" (+ (:left geom) save-load/menu-padding) (+ (:top geom) save-load/menu-padding 15))
      ;; File list
      (if (empty? files)
        (do
          (q/fill 180 180 180)
          (q/text "No saved games found" (+ (:left geom) save-load/menu-padding) (+ (:content-top geom) 15)))
        (doseq [[idx filename] (map-indexed vector files)]
          (let [y (+ (:content-top geom) (* idx save-load/menu-item-height))]
            (if (= idx hovered)
              ;; Inverse colors for hover
              (do
                (q/fill 255)
                (q/no-stroke)
                (q/rect (:left geom) y (:width geom) save-load/menu-item-height)
                (q/fill 0)
                (q/text filename (+ (:left geom) save-load/menu-padding) (+ y 17)))
              ;; Normal colors
              (do
                (q/fill 255)
                (q/text filename (+ (:left geom) save-load/menu-padding) (+ y 17))))))))))
