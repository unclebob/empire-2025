(ns empire.menus
  (:require [empire.atoms :as atoms]
            [empire.config :as config]
            [quil.core :as q]))

;; Menu constants
(def menu-width 150)
(def item-height 20)
(def menu-item-start-y-offset 45)
(def text-ascent-offset 11)
(def text-descent-offset 3)
(def menu-text-x-offset 10)
(def header-y-offset 20)
(def line-margin 5)
(def line-y-offset 25)

(defn show-menu
  "Displays a menu with the given header and items positioned relative to a cell."
  [cell-x cell-y header items]
  (let [menu-width 150
        menu-height (+ 45 (* (count items) 20))
        [map-w map-h] @atoms/map-screen-dimensions
        cols (count @atoms/game-map)
        rows (count (first @atoms/game-map))
        cell-left (* cell-x (/ map-w cols))
        cell-top (* cell-y (/ map-h rows))
        cell-bottom (+ cell-top (/ map-h rows))
        [_ text-y _ _] @atoms/text-area-dimensions
        screen-w (q/width)
        menu-x (min cell-left (- screen-w menu-width))
        menu-y (if (<= (+ cell-bottom menu-height) (min map-h text-y))
                 cell-bottom
                 (max 0 (- cell-top menu-height)))
        display-items (map config/menu-items->strings items)]
    (reset! atoms/menu-state {:coords [cell-x cell-y]
                              :visible true
                              :x menu-x
                              :y menu-y
                              :header header
                              :items display-items})))


(defn find-menu-item
  "Finds the menu item index based on mouse position."
  [menu-x menu-y x y items]
  (when (and (>= x menu-x) (< x (+ menu-x menu-width)))
    (first (filter #(let [item-y (+ menu-y menu-item-start-y-offset (* % item-height))]
                      (and (>= y (- item-y text-ascent-offset)) (< y (+ item-y text-descent-offset))))
                   (range (count items))))))

(defn dismiss-existing-menu
  "Dismisses the menu if clicking outside it."
  [x y]
  (when (:visible @atoms/menu-state)
    (let [menu @atoms/menu-state
          menu-x (:x menu)
          menu-y (:y menu)
          items (:items menu)
          menu-height (+ menu-item-start-y-offset (* (count items) item-height))]
      (when-not (and (>= x menu-x) (< x (+ menu-x menu-width))
                     (>= y menu-y) (< y (+ menu-y menu-height)))
        (swap! atoms/menu-state assoc :visible false)))))

(defn handle-menu-click
  "Handles clicking on menu items. Returns the clicked item keyword if a menu item was clicked, nil otherwise."
  [x y]
  (when (:visible @atoms/menu-state)
    (let [menu @atoms/menu-state
          menu-x (:x menu)
          menu-y (:y menu)
          items (:items menu)
          clicked-item-idx (find-menu-item menu-x menu-y x y items)]
      (when clicked-item-idx
        (let [item (nth items clicked-item-idx)
              keyword-item (config/menu-strings->items item)]
          (swap! atoms/menu-state assoc :visible false)
          keyword-item)))))

(defn draw-menu
  "Draws the menu if it's visible."
  []
  (when (:visible @atoms/menu-state)
    (let [{:keys [x y header items]} @atoms/menu-state
          menu-height (+ menu-item-start-y-offset (* (count items) item-height))
          mouse-x (q/mouse-x)
          mouse-y (q/mouse-y)
          highlighted-idx (find-menu-item x y mouse-x mouse-y items)
          header-string (config/menu-header->string header)]
      (when (or (nil? header) (nil? header-string))
        (prn 'Bad-menu 'header header 'string header-string))
      (when (some? header-string)
        (q/fill 200 200 200 200)
        (q/rect x y menu-width menu-height)
        ;; Header
        (q/fill 0)
        (q/text-font @atoms/menu-header-font)
        (q/text header-string (+ x menu-text-x-offset) (+ y header-y-offset))
        ;; Line
        (q/stroke 0)
        (q/line (+ x line-margin) (+ y line-y-offset) (- (+ x menu-width) line-margin) (+ y line-y-offset))
        ;; Items
        (q/text-font @atoms/menu-item-font)
        (doseq [[idx item] (map-indexed vector items)]
          (if (= idx highlighted-idx)
            (q/fill 255)
            (q/fill 0))
          (q/text item (+ x menu-text-x-offset) (+ y menu-item-start-y-offset (* idx item-height))))))))