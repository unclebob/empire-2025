;; mutation-tested: no
(ns empire.game.production-status
  (:require [clojure.string :as str]))

(def ^:private unit-type-order
  [:army :fighter :transport :destroyer :submarine :patrol-boat :carrier :battleship :satellite])

(def ^:private unit-type-labels
  {:army "A" :fighter "F" :transport "T" :destroyer "D" :submarine "S"
   :patrol-boat "P" :carrier "C" :battleship "B" :satellite "Z"})

(defn format-production-status
  "Formats production status string: unit counts and exploration %.
   Format: A:n F:n T:n D:n S:n P:n C:n B:n Z:n | nn%"
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:00:24.19144-05:00", :module-hash "-852133878", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 3, :hash "1702437270"} {:id "def/unit-type-order", :kind "def", :line 5, :end-line 6, :hash "449708913"} {:id "def/unit-type-labels", :kind "def", :line 8, :end-line 10, :hash "1339473421"} {:id "defn/format-production-status", :kind "defn", :line 12, :end-line 35, :hash "-485312180"}]}
;; clj-mutate-manifest-end
