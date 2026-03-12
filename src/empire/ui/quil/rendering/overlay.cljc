(ns empire.ui.quil.rendering.overlay
  (:require [empire.game.save-load :as save-load]
            [empire.state.api :as sa]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.ui.util.rendering.display :as display]
            [quil.core :as q]))

(defn update-hover-status
  "Updates hover-message based on mouse position.
   Shows contents from the currently displayed map.
   Also updates load-menu-hovered when menu is open."
  []
  (let [x (q/mouse-x)
        y (q/mouse-y)]
    (when (sa/read-state :load-menu-open)
      (let [files (sa/read-state :load-menu-files)
            geom (save-load/menu-geometry (q/width) (q/height) (count files))
            idx (save-load/hovered-file-index x y geom (count files))]
        (sa/write-state! :load-menu-hovered idx)))
    (sa/write-state! :hover-message
                          (if (map-utils/on-map? x y)
                            (let [coords (vec (map-utils/determine-cell-coordinates x y))
                                  the-map (display/resolve-display-map (sa/read-state :map-to-display)
                                                                       (sa/read-state :player-map)
                                                                       (sa/read-state :computer-map)
                                                                       (sa/current-world))]
                              (display/compute-hover-message the-map (sa/read-state :production) coords))
                            ""))))

(defn draw-load-menu
  "Draws the load game menu overlay when open."
  []
  (when (sa/read-state :load-menu-open)
    (let [screen-w (q/width)
          screen-h (q/height)
          files (sa/read-state :load-menu-files)
          file-count (count files)
          geom (save-load/menu-geometry screen-w screen-h file-count)
          hovered (sa/read-state :load-menu-hovered)
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
      (q/text-font (sa/read-state :text-font))
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

(defn draw-save-menu
  "Draws the save-name dialog overlay when open."
  []
  (when (sa/read-state :save-menu-open)
    (let [screen-w (q/width)
          screen-h (q/height)
          ;; Reuse menu sizing with enough vertical room for prompt, input, and help text.
          geom (save-load/menu-geometry screen-w screen-h 3)
          menu-padding save-load/menu-padding
          input (or (sa/read-state :save-menu-input) "")
          default-active? (sa/read-state :save-menu-default-active)
          prompt "Save file name (.edn optional):"
          input-y (+ (:content-top geom) 12)]
      ;; Semi-transparent overlay
      (q/fill 0 0 0 128)
      (q/rect 0 0 screen-w screen-h)
      ;; Dialog background
      (q/fill 40 40 40)
      (q/stroke 255)
      (q/stroke-weight 2)
      (q/rect (:left geom) (:top geom) (:width geom) (:height geom))
      (q/stroke-weight 1)
      (q/text-font (sa/read-state :text-font))
      ;; Title and prompt
      (q/fill 255)
      (q/text "Save Game" (+ (:left geom) menu-padding) (+ (:top geom) menu-padding 15))
      (q/fill 200 200 200)
      (q/text prompt (+ (:left geom) menu-padding) input-y)
      ;; Input box
      (let [box-x (+ (:left geom) menu-padding)
            box-y (+ input-y 8)
            box-w (- (:width geom) (* 2 menu-padding))
            box-h 24]
        (q/fill 255)
        (q/stroke 160)
        (q/rect box-x box-y box-w box-h)
        (if default-active?
          (q/fill 120)
          (q/fill 0))
        (q/text input (+ box-x 6) (+ box-y 16)))
      (q/fill 200 200 200)
      (q/text "Enter=Save  Esc=Cancel"
              (+ (:left geom) menu-padding)
              (+ input-y 50))
      (q/text "Backspace/Delete=Remove Last"
              (+ (:left geom) menu-padding)
              (+ input-y 68)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:03:09.479509-05:00", :module-hash "-612126401", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "-467571804"} {:id "defn/update-hover-status", :kind "defn", :line 8, :end-line 28, :hash "1331641784"} {:id "defn/draw-load-menu", :kind "defn", :line 30, :end-line 73, :hash "2122055074"} {:id "defn/draw-save-menu", :kind "defn", :line 75, :end-line 121, :hash "550099933"}]}
;; clj-mutate-manifest-end
