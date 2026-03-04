;; mutation-tested: no
(ns empire.application.impl.production-status
  (:require [clojure.string :as str]
            [empire.application.production-status :as production-status]))

(def ^:private unit-type-order
  [:army :fighter :transport :destroyer :submarine :patrol-boat :carrier :battleship :satellite])

(def ^:private unit-type-labels
  {:army "A" :fighter "F" :transport "T" :destroyer "D" :submarine "S"
   :patrol-boat "P" :carrier "C" :battleship "B" :satellite "Z"})

(defmethod production-status/format-production-status :default
  [game-map player-map]
  (let [cols (count game-map)
        rows (count (first game-map))
        init {:counts (zipmap unit-type-order (repeat 0))
              :total 0
              :explored 0}
        {:keys [counts total explored]}
        (reduce (fn [{:keys [counts total explored]} [col row]]
                  (let [pc (get-in player-map [col row])
                        exp (if (and pc (not= :unexplored (:type pc)))
                              (inc explored) explored)
                        unit (:contents (get-in game-map [col row]))
                        cts (if (and unit (= :player (:owner unit)))
                              (update counts (:type unit) inc) counts)]
                    {:counts cts :total (inc total) :explored exp}))
                init
                (for [col (range cols) row (range rows)] [col row]))
        pct (if (zero? total) 0 (int (* 100 (/ explored total))))
        unit-strs (map #(str (unit-type-labels %) ":" (get counts %))
                       unit-type-order)]
    (str (str/join " " unit-strs) " | " pct "%")))
