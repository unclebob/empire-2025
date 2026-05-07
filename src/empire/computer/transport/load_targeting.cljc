(ns empire.computer.transport.load-targeting
  (:require [empire.computer.shared.transport-load-targeting :as shared]))

(def all-coastal-army-positions shared/all-coastal-army-positions)

(defn choose-load-target-cell
  ([transport-pos computer-map]
   (choose-load-target-cell transport-pos computer-map {}))
  ([transport-pos computer-map options]
   (with-redefs [shared/all-coastal-army-positions all-coastal-army-positions]
     (shared/choose-load-target-cell transport-pos computer-map options))))

(def path-to-load-target shared/path-to-load-target)

(def target-reached? shared/target-reached?)

(def neighborhood-tile-army-positions shared/neighborhood-tile-army-positions)
