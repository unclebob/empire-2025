(ns empire.game.save-load
  (:require [empire.game.save-load.menu :as menu]
            [empire.game.save-load.persistence :as persistence]))

(def saveable-atoms persistence/saveable-atoms)
(def normalize-save-filename persistence/normalize-save-filename)
(def list-save-files persistence/list-save-files)
(def save-game! persistence/save-game!)
(def load-game! persistence/load-game!)

(def open-save-menu! menu/open-save-menu!)
(def close-save-menu! menu/close-save-menu!)
(def append-save-menu-char! menu/append-save-menu-char!)
(def backspace-save-menu-input! menu/backspace-save-menu-input!)
(def save-from-menu! menu/save-from-menu!)
(def open-load-menu! menu/open-load-menu!)
(def close-load-menu! menu/close-load-menu!)
(def menu-width menu/menu-width)
(def menu-padding menu/menu-padding)
(def menu-title-height menu/menu-title-height)
(def menu-item-height menu/menu-item-height)
(def menu-geometry menu/menu-geometry)
(def hovered-file-index menu/hovered-file-index)

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-13T16:19:26.304536-05:00", :module-hash "-485749828", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-298693592"} {:id "def/saveable-atoms", :kind "def", :line 5, :end-line 5, :hash "-1204332107"} {:id "def/normalize-save-filename", :kind "def", :line 6, :end-line 6, :hash "511134978"} {:id "def/list-save-files", :kind "def", :line 7, :end-line 7, :hash "-171234518"} {:id "def/save-game!", :kind "def", :line 8, :end-line 8, :hash "-1497753570"} {:id "def/load-game!", :kind "def", :line 9, :end-line 9, :hash "382234279"} {:id "def/open-save-menu!", :kind "def", :line 11, :end-line 11, :hash "-469108860"} {:id "def/close-save-menu!", :kind "def", :line 12, :end-line 12, :hash "-214256845"} {:id "def/append-save-menu-char!", :kind "def", :line 13, :end-line 13, :hash "-193535612"} {:id "def/backspace-save-menu-input!", :kind "def", :line 14, :end-line 14, :hash "1535068716"} {:id "def/save-from-menu!", :kind "def", :line 15, :end-line 15, :hash "1747316967"} {:id "def/open-load-menu!", :kind "def", :line 16, :end-line 16, :hash "1989549686"} {:id "def/close-load-menu!", :kind "def", :line 17, :end-line 17, :hash "-1456120811"} {:id "def/menu-width", :kind "def", :line 18, :end-line 18, :hash "-365903193"} {:id "def/menu-padding", :kind "def", :line 19, :end-line 19, :hash "-507423549"} {:id "def/menu-title-height", :kind "def", :line 20, :end-line 20, :hash "435872571"} {:id "def/menu-item-height", :kind "def", :line 21, :end-line 21, :hash "518895263"} {:id "def/menu-geometry", :kind "def", :line 22, :end-line 22, :hash "1408657009"} {:id "def/hovered-file-index", :kind "def", :line 23, :end-line 23, :hash "-1510976232"}]}
;; clj-mutate-manifest-end
