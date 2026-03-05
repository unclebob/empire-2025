(ns empire.movement.waypoint-services
  (:require [empire.movement.waypoint :as waypoint]))

(defn create-waypoint
  [coords]
  (waypoint/create-waypoint coords))

(defn set-waypoint-orders
  [coords]
  (waypoint/set-waypoint-orders coords))

(defn set-waypoint-orders-by-direction
  [coords direction]
  (waypoint/set-waypoint-orders-by-direction coords direction))
