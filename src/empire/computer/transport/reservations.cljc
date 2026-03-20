(ns empire.computer.transport.reservations
  (:require [empire.state.api :as sa]))

(defn reservations
  []
  (or (sa/read-state :transport-load-reservations) {}))

(defn reservation-for
  [transport-id]
  (get (reservations) transport-id))

(defn reserved-coastal-cells
  ([] (reserved-coastal-cells nil))
  ([exclude-transport-id]
   (->> (reservations)
        (remove (fn [[transport-id _]]
                  (= transport-id exclude-transport-id)))
        (keep (fn [[_ {:keys [coastal-cell]}]]
                coastal-cell))
        set)))

(defn reserved-army-ids
  ([] (reserved-army-ids nil))
  ([exclude-transport-id]
   (->> (reservations)
        (remove (fn [[transport-id _]]
                  (= transport-id exclude-transport-id)))
        (mapcat (fn [[_ {:keys [army-ids]}]]
                  army-ids))
        set)))

(defn reserve!
  [transport-id coastal-cell army-ids]
  (when transport-id
    (sa/update-state! :transport-load-reservations
                      assoc
                      transport-id
                      {:coastal-cell coastal-cell
                       :army-ids (set army-ids)})))

(defn update-army-ids!
  [transport-id army-ids]
  (when transport-id
    (sa/update-state! :transport-load-reservations
                      update transport-id
                      #(when %
                         (assoc % :army-ids (set army-ids))))))

(defn release!
  [transport-id]
  (when transport-id
    (sa/update-state! :transport-load-reservations dissoc transport-id)))
