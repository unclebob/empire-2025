(ns empire.computer.coordinator-decisions)

(def ^:private ship-types
  #{:destroyer :submarine :patrol-boat :carrier :battleship})

(defn computer-unit?
  [unit]
  (and unit (= (:owner unit) :computer)))

(defn dispatch-action
  [unit]
  (cond
    (not (computer-unit? unit)) nil
    (= :army (:type unit)) :army
    (= :fighter (:type unit)) :fighter
    (= :transport (:type unit)) :transport
    (ship-types (:type unit)) :ship
    :else nil))

(defn dispatch-plan
  [unit]
  (let [action (dispatch-action unit)]
    (when action
      {:action action
       :unit-type (:type unit)})))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T22:35:19.036567-05:00", :module-hash "206989904", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-297029186"} {:id "def/ship-types", :kind "def", :line 3, :end-line 4, :hash "573016639"} {:id "defn/computer-unit?", :kind "defn", :line 6, :end-line 8, :hash "-421810879"} {:id "defn/dispatch-action", :kind "defn", :line 10, :end-line 18, :hash "-847173854"} {:id "defn/dispatch-plan", :kind "defn", :line 20, :end-line 25, :hash "210878815"}]}
;; clj-mutate-manifest-end
