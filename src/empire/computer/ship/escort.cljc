(ns empire.computer.ship.escort
  "Computer destroyer escort and shared pursuit logic."
  (:require [empire.state.api :as sa]
            [empire.computer.ship.core :as ship-core]
            [empire.computer.shared.grid :as grid]
            [empire.computer.shared.world-query :as world-query]
            [empire.computer.shared.movement :as computer-movement]
            [empire.game-mechanics.visibility :as visibility]))


(defn- computer-unit-at
  [pos]
  (get-in (sa/read-state :computer-map) (conj pos :contents)))


(defn find-carrier-by-id
  "Finds the position of a carrier with the given carrier-id."
  [carrier-id]
  (let [game-map (sa/read-state :computer-map)]
    (first (for [i (range (count game-map))
                 j (range (count (first game-map)))
                 :let [cell (get-in game-map [i j])
                       unit (:contents cell)]
                 :when (and unit
                            (= :carrier (:type unit))
                            (= carrier-id (:carrier-id unit)))]
             [i j]))))

(defn- find-transport-by-id
  "Finds the position of a transport with the given transport-id."
  [transport-id]
  (let [game-map (sa/read-state :computer-map)]
    (first (for [i (range (count game-map))
                 j (range (count (first game-map)))
                 :let [cell (get-in game-map [i j])
                       unit (:contents cell)]
                 :when (and unit
                            (= :transport (:type unit))
                            (= transport-id (:transport-id unit)))]
             [i j]))))

(defn find-enemy-near-positions
  "Finds a player ship adjacent to any of the given positions.
   Returns enemy position or nil."
  [positions]
  (let [game-map (sa/read-state :computer-map)]
    (first (for [gpos positions
                 neighbor (world-query/get-neighbors gpos)
                 :let [cell (get-in game-map neighbor)
                       contents (:contents cell)]
                 :when (and contents
                            (= :player (:owner contents))
                            (#{:patrol-boat :destroyer :submarine :transport
                               :carrier :battleship} (:type contents)))]
             neighbor))))

(defn- group-positions
  "Returns positions of all ships in the escort group (self + transport or carrier)."
  [pos]
  (let [unit (computer-unit-at pos)
        transport-pos (when (:escort-transport-id unit)
                        (find-transport-by-id (:escort-transport-id unit)))
        carrier-pos (when (:escort-carrier-id unit)
                      (find-carrier-by-id (:escort-carrier-id unit)))]
    (filter some? [pos transport-pos carrier-pos])))

(defn- visible-to-group?
  "Returns true if pos is within visibility (Chebyshev 1) of any group member."
  [target-pos group-poss]
  (some (fn [gpos] (<= (grid/chebyshev-distance target-pos gpos) 1)) group-poss))

(defn- end-pursuit
  "Reverts a pursuing ship back to its pre-pursuit mode.
   Destroyers return to :escorting, carrier group escorts to :orbiting."
  [pos]
  (let [unit (computer-unit-at pos)
        return-mode (if (:escort-carrier-id unit) :orbiting :escorting)]
    (sa/update-world! update-in (conj pos :contents)
                      #(-> % (assoc :escort-mode return-mode)
                           (dissoc :pursuit-target :pursuit-steps-remaining)))
    (visibility/sync-ai-unit-to-computer-map! pos)))

(defn begin-pursuit
  "Switches an escort ship to pursuing mode, targeting the enemy."
  [pos enemy-pos]
  (sa/update-world! update-in (conj pos :contents)
                    assoc :escort-mode :pursuing
                    :pursuit-target enemy-pos
                    :pursuit-steps-remaining 5)
  (visibility/sync-ai-unit-to-computer-map! pos)
  (ship-core/move-toward pos enemy-pos))

(defn process-pursuit
  "Continues pursuit: move toward a cell the enemy could have gone to,
   excluding cells visible to group members. Decrements steps remaining."
  [pos]
  (let [unit (computer-unit-at pos)
        computer-map (sa/read-state :computer-map)
        target (:pursuit-target unit)
        steps (:pursuit-steps-remaining unit)
        group (group-positions pos)
        candidates (conj (set (world-query/get-neighbors target)) target)
        sea-candidates (filter (fn [c]
                                 (let [cell (get-in computer-map c)]
                                   (and cell
                                        (= :sea (:type cell))
                                        (not (visible-to-group? c group)))))
                               candidates)]
    (if (or (<= steps 1) (empty? sea-candidates))
      (end-pursuit pos)
      (let [chosen (rand-nth (vec sea-candidates))
            new-pos (ship-core/move-toward pos chosen)]
        (when new-pos
          (sa/update-world! update-in (conj new-pos :contents)
                            assoc :pursuit-target chosen
                            :pursuit-steps-remaining (dec steps))
          (visibility/sync-ai-unit-to-computer-map! new-pos))))))

;; --- Destroyer-specific escort ---

(defn- find-unadopted-transport
  "Finds the nearest computer transport without an escort-destroyer-id."
  [pos]
  (let [game-map (sa/read-state :computer-map)
        candidates (for [i (range (count game-map))
                         j (range (count (first game-map)))
                         :let [cell (get-in game-map [i j])
                               unit (:contents cell)]
                         :when (and unit
                                    (= :computer (:owner unit))
                                    (= :transport (:type unit))
                                    (nil? (:escort-destroyer-id unit)))]
                     [i j])]
    (when (seq candidates)
      (apply min-key (partial grid/distance pos) candidates))))

(defn- adopt-transport
  "Pairs a destroyer at pos with a transport at transport-pos."
  [pos transport-pos]
  (let [destroyer (computer-unit-at pos)
        transport (computer-unit-at transport-pos)
        d-id (:destroyer-id destroyer)
        t-id (:transport-id transport)]
    (sa/update-world! update-in (conj pos :contents)
                      #(assoc % :escort-transport-id t-id :escort-mode :intercepting))
    (sa/update-world! update-in (conj transport-pos :contents)
                      #(assoc % :escort-destroyer-id d-id))
    (visibility/sync-ai-unit-to-computer-map! pos)
    (visibility/sync-ai-unit-to-computer-map! transport-pos)))

(defn- find-enemy-near-destroyer-group
  "Finds a player ship adjacent to destroyer or its escorted transport."
  [pos]
  (let [unit (computer-unit-at pos)
        transport-pos (when (:escort-transport-id unit)
                        (find-transport-by-id (:escort-transport-id unit)))]
    (find-enemy-near-positions (filter some? [pos transport-pos]))))

(defn- revert-destroyer-to-seeking
  "Reverts a destroyer escort to seeking mode, clearing transport reference."
  [pos]
  (sa/update-world! update-in (conj pos :contents)
                    #(-> % (assoc :escort-mode :seeking)
                         (dissoc :escort-transport-id)))
  (visibility/sync-ai-unit-to-computer-map! pos)
  nil)

(defn- process-destroyer-seeking [pos]
  (when-let [transport-pos (find-unadopted-transport pos)]
    (adopt-transport pos transport-pos)
    (ship-core/move-toward pos transport-pos)))

(defn- process-destroyer-intercepting [pos unit]
  (if-let [transport-pos (find-transport-by-id (:escort-transport-id unit))]
    (if (<= (grid/distance pos transport-pos) 1)
      (sa/update-world! update-in (conj pos :contents)
                        assoc :escort-mode :escorting)
      (ship-core/move-toward pos transport-pos))
    (revert-destroyer-to-seeking pos)))

(defn- process-destroyer-escorting [pos unit]
  (if-let [transport-pos (find-transport-by-id (:escort-transport-id unit))]
    (when (> (grid/distance pos transport-pos) 1)
      (ship-core/move-toward pos transport-pos))
    (revert-destroyer-to-seeking pos)))

(defn- escorting-enemy-pos
  "Returns an enemy position only when the destroyer is escorting."
  [pos mode]
  (when (= :escorting mode)
    (find-enemy-near-destroyer-group pos)))

(defn- dispatch-destroyer-mode
  "Executes mode-specific destroyer escort behavior."
  [pos unit mode]
  (case mode
    :seeking (process-destroyer-seeking pos)
    :intercepting (process-destroyer-intercepting pos unit)
    :escorting (process-destroyer-escorting pos unit)
    :pursuing (process-pursuit pos)
    nil))

(defn process-escort-destroyer
  "Processes a destroyer in escort mode."
  [pos]
  (let [unit (computer-unit-at pos)
        mode (:escort-mode unit)]
    (if-let [enemy-pos (escorting-enemy-pos pos mode)]
      (begin-pursuit pos enemy-pos)
      (dispatch-destroyer-mode pos unit mode))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:28:22.383978-05:00", :module-hash "719320607", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "-20713476"} {:id "defn-/computer-unit-at", :kind "defn-", :line 11, :end-line 13, :hash "-1108811645"} {:id "defn/find-carrier-by-id", :kind "defn", :line 16, :end-line 27, :hash "899367482"} {:id "defn-/find-transport-by-id", :kind "defn-", :line 29, :end-line 40, :hash "772327638"} {:id "defn/find-enemy-near-positions", :kind "defn", :line 42, :end-line 55, :hash "162168956"} {:id "defn-/group-positions", :kind "defn-", :line 57, :end-line 65, :hash "76318296"} {:id "defn-/visible-to-group?", :kind "defn-", :line 67, :end-line 70, :hash "1779388866"} {:id "defn-/end-pursuit", :kind "defn-", :line 72, :end-line 81, :hash "799548584"} {:id "defn/begin-pursuit", :kind "defn", :line 83, :end-line 91, :hash "166453116"} {:id "defn/process-pursuit", :kind "defn", :line 93, :end-line 117, :hash "-1887751255"} {:id "defn-/find-unadopted-transport", :kind "defn-", :line 121, :end-line 135, :hash "-2111938564"} {:id "defn-/adopt-transport", :kind "defn-", :line 137, :end-line 149, :hash "-547789195"} {:id "defn-/find-enemy-near-destroyer-group", :kind "defn-", :line 151, :end-line 157, :hash "-1284588307"} {:id "defn-/revert-destroyer-to-seeking", :kind "defn-", :line 159, :end-line 166, :hash "1188108935"} {:id "defn-/process-destroyer-seeking", :kind "defn-", :line 168, :end-line 171, :hash "1549646564"} {:id "defn-/process-destroyer-intercepting", :kind "defn-", :line 173, :end-line 179, :hash "-1389503503"} {:id "defn-/process-destroyer-escorting", :kind "defn-", :line 181, :end-line 185, :hash "-1270558274"} {:id "defn-/escorting-enemy-pos", :kind "defn-", :line 187, :end-line 191, :hash "-995263492"} {:id "defn-/dispatch-destroyer-mode", :kind "defn-", :line 193, :end-line 201, :hash "-961437166"} {:id "defn/process-escort-destroyer", :kind "defn", :line 203, :end-line 210, :hash "1409649396"}]}
;; clj-mutate-manifest-end
