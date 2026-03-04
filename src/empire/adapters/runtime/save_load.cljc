;; mutation-tested: no
(ns empire.adapters.runtime.save-load
  "Runtime adapter facade for save/load operations used by UI adapters."
  (:require [empire.save-load :as save-load]))

(def menu-padding save-load/menu-padding)
(def menu-item-height save-load/menu-item-height)

(defn save-game! [] (save-load/save-game!))
(defn load-game! [filename] (save-load/load-game! filename))
(defn open-load-menu! [] (save-load/open-load-menu!))
(defn close-load-menu! [] (save-load/close-load-menu!))
(defn menu-geometry [screen-w screen-h file-count]
  (save-load/menu-geometry screen-w screen-h file-count))
(defn hovered-file-index [mouse-x mouse-y geom file-count]
  (save-load/hovered-file-index mouse-x mouse-y geom file-count))
