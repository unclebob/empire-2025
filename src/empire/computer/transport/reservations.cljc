(ns empire.computer.transport.reservations
  (:require [empire.computer.shared.transport-reservations :as shared]))

(def reservations shared/reservations)

(def reserved-coastal-cells shared/reserved-coastal-cells)

(def reserved-army-ids shared/reserved-army-ids)

(def reserve! shared/reserve!)

(def update-army-ids! shared/update-army-ids!)

(def release! shared/release!)
