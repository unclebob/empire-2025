(ns empire.ui.quil.input
  (:require [empire.game-mechanics.movement.map-utils :as map-utils]
            [empire.ui.util.input.dispatch :as dispatch]
            [quil.core :as q]))

(defn mouse->cell []
  (let [x (q/mouse-x) y (q/mouse-y)]
    (when (map-utils/on-map? x y)
      (map-utils/determine-cell-coordinates x y))))

(defn key-down [k]
  (dispatch/key-down k (q/mouse-x) (q/mouse-y)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:03:02.134572-05:00", :module-hash "-1341987578", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "-871134963"} {:id "defn/mouse->cell", :kind "defn", :line 6, :end-line 9, :hash "1811106585"} {:id "defn/key-down", :kind "defn", :line 11, :end-line 12, :hash "-1252084116"}]}
;; clj-mutate-manifest-end
