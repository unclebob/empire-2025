(ns empire.computer.transport.sailing-support
  (:require [empire.computer.transport.sailing-path :as sailing-path]
            [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]))

(def invasion-unload-radius 2)
(def invasion-threat-unload-radius 3)
(def invasion-threat-scan-radius 2)
(def sea-path-inflation-threshold 2)

;; Keep restore keys aligned with transport random-walk recovery behavior.
(def transport-random-walk-restore-keys
  [:transport-mission
   :load-target-cell
   :sail-path
   :invasion-target
   :invasion-path
   :invasion-path-origin
   :invasion-plan-revision
   :invasion-load-since
   :major-invasion-find-armies-round
   :major-invasion-skip-revision])

(def ^:private player-ship-types
  #{:patrol-boat :destroyer :submarine :transport :carrier :battleship})

(defn update-cell-visibility!
  [pos owner]
  (visibility/update-cell-visibility pos owner))

(defn enemy-ship-near-target?
  [target radius]
  (let [world (sa/read-state :computer-map)
        [tx ty] target
        min-x (max 0 (- tx radius))
        max-x (min (dec (count world)) (+ tx radius))
        min-y (max 0 (- ty radius))
        max-y (min (dec (count (first world))) (+ ty radius))]
    (boolean
     (some true?
           (for [x (range min-x (inc max-x))
                 y (range min-y (inc max-y))]
             (let [u (get-in world [x y :contents])]
               (and u
                    (= :player (:owner u))
                    (contains? player-ship-types (:type u)))))))))

(defn compute-sail-path
  "Compute BFS path from transport position to the current coastal target."
  [pos army-count]
  (sailing-path/compute-sail-path
   pos
   (sa/read-state :computer-map)
   army-count))

(defn compute-sail-to-unload-path
  [pos]
  (sailing-path/compute-sail-to-unload-path pos (sa/read-state :computer-map)))

(defn compute-sail-to-load-path
  [pos]
  (sailing-path/compute-sail-to-load-path pos (sa/read-state :computer-map)))

(defn direct-step
  [from to]
  (let [[fr fc] from
        [tr tc] to
        dr (Long/signum (- tr fr))
        dc (Long/signum (- tc fc))]
    [(+ fr dr) (+ fc dc)]))

(defn between-cells
  [from to]
  (loop [current from
         cells []]
    (if (= current to)
      cells
      (let [next-pos (direct-step current to)]
        (if (= next-pos to)
          cells
          (recur next-pos (conj cells next-pos)))))))

(defn sea-or-unexplored?
  [cell]
  (or (nil? cell)
      (= :sea (:type cell))
      (= :unexplored (:type cell))))

(defn direct-sea-corridor?
  [from to computer-map]
  (every? (fn [step]
            (sea-or-unexplored? (get-in computer-map step)))
          (between-cells from to)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T00:17:59.630017-05:00", :module-hash "772019343", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "-1934294507"} {:id "def/invasion-unload-radius", :kind "def", :line 6, :end-line 6, :hash "244609358"} {:id "def/invasion-threat-unload-radius", :kind "def", :line 7, :end-line 7, :hash "1250298048"} {:id "def/invasion-threat-scan-radius", :kind "def", :line 8, :end-line 8, :hash "703933872"} {:id "def/sea-path-inflation-threshold", :kind "def", :line 9, :end-line 9, :hash "2026838488"} {:id "def/transport-random-walk-restore-keys", :kind "def", :line 12, :end-line 22, :hash "455701777"} {:id "def/player-ship-types", :kind "def", :line 24, :end-line 25, :hash "-889899963"} {:id "defn/update-cell-visibility!", :kind "defn", :line 27, :end-line 29, :hash "2091185304"} {:id "defn/enemy-ship-near-target?", :kind "defn", :line 31, :end-line 46, :hash "-50093572"} {:id "defn/compute-sail-path", :kind "defn", :line 48, :end-line 54, :hash "-1652518236"} {:id "defn/compute-sail-to-unload-path", :kind "defn", :line 56, :end-line 58, :hash "1720302639"} {:id "defn/compute-sail-to-load-path", :kind "defn", :line 60, :end-line 62, :hash "-1663754764"} {:id "defn/direct-step", :kind "defn", :line 64, :end-line 70, :hash "-1268929394"} {:id "defn/between-cells", :kind "defn", :line 72, :end-line 81, :hash "206799869"} {:id "defn/sea-or-unexplored?", :kind "defn", :line 83, :end-line 87, :hash "890775111"} {:id "defn/direct-sea-corridor?", :kind "defn", :line 89, :end-line 93, :hash "-1197656732"}]}
;; clj-mutate-manifest-end
