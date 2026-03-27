(ns empire.game.save-load.menu
  (:require [empire.game.save-load.persistence :as persistence]
            [empire.state.api :as sa]))

(def menu-width 350)
(def menu-padding 15)
(def menu-title-height 30)
(def menu-item-height 25)

(defn open-save-menu!
  []
  (sa/write-state! :save-menu-input (persistence/default-save-basename))
  (sa/write-state! :save-menu-default-active true)
  (sa/write-state! :save-menu-open true))

(defn close-save-menu!
  []
  (sa/write-state! :save-menu-default-active false)
  (sa/write-state! :save-menu-open false))

(defn append-save-menu-char!
  [ch]
  (sa/update-state! :save-menu-input str ch))

(defn backspace-save-menu-input!
  []
  (sa/update-state! :save-menu-input
                    (fn [s]
                      (if (seq s)
                        (subs s 0 (dec (count s)))
                        ""))))

(defn save-from-menu!
  []
  (let [filename (persistence/save-game! "saves" (sa/read-state :save-menu-input))]
    (close-save-menu!)
    filename))

(defn open-load-menu!
  ([] (open-load-menu! "saves"))
  ([dir-path]
   (sa/write-state! :load-menu-files (persistence/list-save-files dir-path))
   (sa/write-state! :load-menu-hovered nil)
   (sa/write-state! :load-menu-open true)))

(defn close-load-menu!
  []
  (sa/write-state! :load-menu-open false)
  (sa/write-state! :load-menu-files [])
  (sa/write-state! :load-menu-hovered nil))

(defn menu-geometry
  [screen-w screen-h file-count]
  (let [content-height (* menu-item-height (max 1 file-count))
        total-height (+ menu-title-height content-height (* 2 menu-padding))
        left (/ (- screen-w menu-width) 2)
        top (/ (- screen-h total-height) 2)]
    {:left left
     :top top
     :right (+ left menu-width)
     :bottom (+ top total-height)
     :width menu-width
     :height total-height
     :content-top (+ top menu-padding menu-title-height)
     :item-height menu-item-height}))

(defn hovered-file-index
  [mouse-x mouse-y geom file-count]
  (when (and (> file-count 0)
             (>= mouse-x (:left geom))
             (<= mouse-x (:right geom))
             (>= mouse-y (:content-top geom))
             (< mouse-y (+ (:content-top geom) (* file-count (:item-height geom)))))
    (int (/ (- mouse-y (:content-top geom)) (:item-height geom)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T02:13:09.745444-05:00", :module-hash "973786489", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "165089373"} {:id "def/menu-width", :kind "def", :line 5, :end-line 5, :hash "-1110273700"} {:id "def/menu-padding", :kind "def", :line 6, :end-line 6, :hash "-1892092342"} {:id "def/menu-title-height", :kind "def", :line 7, :end-line 7, :hash "263027173"} {:id "def/menu-item-height", :kind "def", :line 8, :end-line 8, :hash "107622945"} {:id "defn/open-save-menu!", :kind "defn", :line 10, :end-line 14, :hash "2125946936"} {:id "defn/close-save-menu!", :kind "defn", :line 16, :end-line 19, :hash "-1091834661"} {:id "defn/append-save-menu-char!", :kind "defn", :line 21, :end-line 23, :hash "-560179540"} {:id "defn/backspace-save-menu-input!", :kind "defn", :line 25, :end-line 31, :hash "-1361916783"} {:id "defn/save-from-menu!", :kind "defn", :line 33, :end-line 37, :hash "-1238797972"} {:id "defn/open-load-menu!", :kind "defn", :line 39, :end-line 44, :hash "32243335"} {:id "defn/close-load-menu!", :kind "defn", :line 46, :end-line 50, :hash "2071713593"} {:id "defn/menu-geometry", :kind "defn", :line 52, :end-line 65, :hash "906604373"} {:id "defn/hovered-file-index", :kind "defn", :line 67, :end-line 74, :hash "1361503087"}]}
;; clj-mutate-manifest-end
