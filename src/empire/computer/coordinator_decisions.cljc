(ns empire.computer.coordinator-decisions)

(def ^:private ship-types
  #{:destroyer :submarine :patrol-boat :carrier :battleship})

(defn computer-unit?
  [unit]
  (and unit (= (:owner unit) :computer)))

(defn- typed-dispatch-action
  [unit-type]
  (cond
    (= :army unit-type) :army
    (= :fighter unit-type) :fighter
    (= :transport unit-type) :transport
    (ship-types unit-type) :ship
    :else nil))

(defn dispatch-action
  [unit]
  (when (computer-unit? unit)
    (typed-dispatch-action (:type unit))))

(defn dispatch-plan
  [unit]
  (let [action (dispatch-action unit)]
    (when action
      {:action action
       :unit-type (:type unit)})))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:07:16.741171-05:00", :module-hash "1775642061", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-297029186"} {:id "def/ship-types", :kind "def", :line 3, :end-line nil, :hash "573016639"} {:id "defn/computer-unit?", :kind "defn", :line 6, :end-line nil, :hash "-421810879"} {:id "defn-/typed-dispatch-action", :kind "defn-", :line 10, :end-line nil, :hash "1399161230"} {:id "defn/dispatch-action", :kind "defn", :line 19, :end-line nil, :hash "-849450967"} {:id "defn/dispatch-plan", :kind "defn", :line 24, :end-line nil, :hash "210878815"}]}
;; clj-mutate-manifest-end
