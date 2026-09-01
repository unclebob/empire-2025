(ns empire.ui.quil.rendering.overlay
  (:require [empire.game.save-load :as save-load]
            [empire.state.api :as sa]
            [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.ui.util.help :as help]
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
    (when (sa/read-state :help-open)
      (let [geom (help/help-geometry (q/width) (q/height))]
        (sa/write-state! :help-geometry geom)
        (sa/write-state! :help-dismiss-hovered
                         (boolean (help/dismiss-button-hit? x y geom)))))
    (if (map-utils/on-map? x y)
      (let [coords (vec (map-utils/determine-cell-coordinates x y))
            the-map (display/resolve-display-map (sa/read-state :map-to-display)
                                                 (sa/read-state :player-map)
                                                 (sa/read-state :computer-map)
                                                 (sa/current-world))]
        (sa/write-state! :hover-cell coords)
        (sa/write-state! :hover-message
                         (display/compute-hover-message the-map (sa/read-state :production) coords)))
      (do
        (sa/write-state! :hover-cell nil)
        (sa/write-state! :hover-message "")))))

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

(defn- in-clip?
  [y clip]
  (or (nil? clip)
      (and (>= y (:y clip))
           (< y (+ (:y clip) (:h clip))))))

(defn- draw-clipped-text
  [s x y clip]
  (when (in-clip? y clip)
    (q/text s x y)))

(defn- draw-help-entry
  [x y entry clip]
  (q/fill 255 220 120)
  (draw-clipped-text (:keys entry) x y clip)
  (q/fill 210 210 210)
  (let [lines (help/wrap-words (:explanation entry) help/explanation-wrap)]
    (doseq [[idx line] (map-indexed vector lines)]
      (draw-clipped-text line x (+ y (* (inc idx) help/line-height)) clip))
    (+ y (* (inc (count lines)) help/line-height))))

(defn- draw-help-section
  [x y section clip]
  (q/fill 120 200 255)
  (draw-clipped-text (:title section) x y clip)
  (let [after-entries (reduce (fn [entry-y entry]
                                (draw-help-entry x (+ entry-y help/line-height) entry clip))
                              y
                              (:entries section))]
    (+ after-entries help/line-height)))

(defn- draw-help-column
  [x y sections clip]
  (reduce (fn [section-y section]
            (draw-help-section x section-y section clip))
          y
          sections))

(defn- draw-help-scrollbar
  [geom scroll]
  (when (pos? (:max-scroll geom))
    (let [clip (:content-clip geom)
          track-x (+ (:right geom) -14)
          track-y (:y clip)
          track-h (:h clip)
          ratio (/ (:viewport-height geom) (max 1 (:content-height geom)))
          thumb-h (max 16 (* track-h ratio))
          travel (max 0 (- track-h thumb-h))
          thumb-y (+ track-y (* travel (/ scroll (max 1 (:max-scroll geom)))))]
      (q/fill 60 60 60)
      (q/no-stroke)
      (q/rect track-x track-y 8 track-h)
      (q/fill 190 190 190)
      (q/rect track-x thumb-y 8 thumb-h))))

(defn- draw-dismiss-button
  [geom]
  (let [{:keys [x y w h]} (:dismiss-button geom)
        label-x (+ x 28)
        label-y (+ y 16)]
    (if (sa/read-state :help-dismiss-hovered)
      (do
        (q/fill 255)
        (q/no-stroke)
        (q/rect x y w h)
        (q/fill 0)
        (q/text help/dismiss-label label-x label-y))
      (do
        (q/fill 70 70 70)
        (q/stroke 255)
        (q/rect x y w h)
        (q/fill 255)
        (q/text help/dismiss-label label-x label-y)))))

(defn draw-help-window
  "Draws the keystroke help overlay when open."
  []
  (when (sa/read-state :help-open)
    (let [screen-w (q/width)
          screen-h (q/height)
          geom (help/help-geometry screen-w screen-h)
          [left-col right-col] (:columns geom)
          col-width (/ (- (:width geom) (* 3 help/help-padding)) 2)
          left-x (+ (:left geom) help/help-padding)
          right-x (+ left-x col-width help/help-padding)
          scroll (or (sa/read-state :help-scroll) 0)
          clip (:content-clip geom)
          col-y (- (:content-top geom) scroll)]
      (sa/write-state! :help-geometry geom)
      (q/fill 0 0 0 160)
      (q/rect 0 0 screen-w screen-h)
      (q/fill 40 40 40)
      (q/stroke 255)
      (q/stroke-weight 2)
      (q/rect (:left geom) (:top geom) (:width geom) (:height geom))
      (q/stroke-weight 1)
      (q/text-font (sa/read-state :text-font))
      (q/fill 255)
      (q/text "Keystrokes"
              (+ (:left geom) help/help-padding)
              (+ (:top geom) help/help-padding 14))
      (draw-help-column left-x col-y left-col clip)
      (draw-help-column right-x col-y right-col clip)
      (draw-help-scrollbar geom scroll)
      (draw-dismiss-button geom))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:44:36.789213-05:00", :module-hash "-1365137892", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "-467571804"} {:id "defn/update-hover-status", :kind "defn", :line 8, :end-line 31, :hash "941608475"} {:id "defn/draw-load-menu", :kind "defn", :line 33, :end-line 76, :hash "2122055074"} {:id "defn/draw-save-menu", :kind "defn", :line 78, :end-line 124, :hash "550099933"}]}
;; clj-mutate-manifest-end
