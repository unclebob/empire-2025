(ns empire.computer.transport.reservations
  (:require [empire.state.api :as sa]))

(defn reservations
  []
  (or (sa/read-state :transport-load-reservations) {}))

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:11:25.525618-05:00", :module-hash "25048205", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 2, :hash "1538881731"} {:id "defn/reservations", :kind "defn", :line 4, :end-line 6, :hash "1259820045"} {:id "defn/reserved-coastal-cells", :kind "defn", :line 8, :end-line 16, :hash "1846612988"} {:id "defn/reserved-army-ids", :kind "defn", :line 18, :end-line 26, :hash "-1361175711"} {:id "defn/reserve!", :kind "defn", :line 28, :end-line 35, :hash "726379864"} {:id "defn/update-army-ids!", :kind "defn", :line 37, :end-line 43, :hash "-1354717537"} {:id "defn/release!", :kind "defn", :line 45, :end-line 48, :hash "-819621441"}]}
;; clj-mutate-manifest-end
