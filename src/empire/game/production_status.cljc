;; mutation-tested: no
(ns empire.game.production-status
  (:require [clojure.string :as str]))

(def ^:private unit-type-order
  [:army :fighter :transport :destroyer :submarine :patrol-boat :carrier :battleship :satellite])

(def ^:private unit-type-labels
  {:army "A" :fighter "F" :transport "T" :destroyer "D" :submarine "S"
   :patrol-boat "P" :carrier "C" :battleship "B" :satellite "Z"})

(defn- count-airport-fighters [cell]
  (if (and (= :city (:type cell)) (= :player (:city-status cell)))
    (or (:fighter-count cell) 0)
    0))

(defn- count-shipyard-ships [cell]
  (if (and (= :city (:type cell)) (= :player (:city-status cell)))
    (count (or (:shipyard cell) []))
    0))

(defn- explored-player-cell?
  [pc]
  (and pc (not= :unexplored (:type pc))))

(defn- player-unit-type
  [unit]
  (when (and unit (= :player (:owner unit)))
    (:type unit)))

(defn- scan-production-cell
  [acc player-map game-map [col row]]
  (let [pc (get-in player-map [col row])
        cell (get-in game-map [col row])
        unit-type (player-unit-type (:contents cell))]
    (-> acc
        (update :total inc)
        (cond-> (explored-player-cell? pc) (update :explored inc)
                unit-type (update-in [:counts unit-type] inc))
        (update :airport-fighters + (count-airport-fighters cell))
        (update :shipyard-ships + (count-shipyard-ships cell)))))

(defn- production-extras
  [airport-fighters shipyard-ships]
  (cond-> []
    (pos? airport-fighters) (conj (str "Landed:" airport-fighters))
    (pos? shipyard-ships) (conj (str "Repair:" shipyard-ships))))

(defn format-production-status
  "Formats production status string: unit counts and exploration %.
   Format: A:n F:n T:n D:n S:n P:n C:n B:n Z:n | nn%
   Includes airport fighters and shipyard ships in counts."
  [game-map player-map]
  (let [cols (count game-map)
        rows (count (first game-map))
        init {:counts (zipmap unit-type-order (repeat 0))
              :airport-fighters 0
              :shipyard-ships 0
              :total 0
              :explored 0}
        {:keys [counts airport-fighters shipyard-ships total explored]}
        (reduce #(scan-production-cell %1 player-map game-map %2)
                init
                (for [col (range cols) row (range rows)] [col row]))
        pct (if (zero? total) 0 (int (* 100 (/ explored total))))
        unit-strs (map #(str (unit-type-labels %) ":" (get counts %))
                       unit-type-order)
        extras (production-extras airport-fighters shipyard-ships)]
    (str (str/join " " unit-strs)
         (when (seq extras) (str " " (str/join " " extras)))
         " | " pct "%")))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:38:17.388852-05:00", :module-hash "-291241442", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line nil, :hash "1702437270"} {:id "def/unit-type-order", :kind "def", :line 5, :end-line nil, :hash "449708913"} {:id "def/unit-type-labels", :kind "def", :line 8, :end-line nil, :hash "1339473421"} {:id "defn-/count-airport-fighters", :kind "defn-", :line 12, :end-line nil, :hash "112860251"} {:id "defn-/count-shipyard-ships", :kind "defn-", :line 17, :end-line nil, :hash "101274628"} {:id "defn-/explored-player-cell?", :kind "defn-", :line 22, :end-line nil, :hash "1634844394"} {:id "defn-/player-unit-type", :kind "defn-", :line 26, :end-line nil, :hash "1725759819"} {:id "defn-/scan-production-cell", :kind "defn-", :line 31, :end-line nil, :hash "1254383307"} {:id "defn-/production-extras", :kind "defn-", :line 43, :end-line nil, :hash "221136390"} {:id "defn/format-production-status", :kind "defn", :line 49, :end-line nil, :hash "-1045456126"}]}
;; clj-mutate-manifest-end
