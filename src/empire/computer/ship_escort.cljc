(ns empire.computer.ship-escort
  "Computer destroyer escort and shared pursuit logic."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.ship-core :as ship-core]
            [empire.computer.movement :as computer-movement]
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
                 neighbor (core/get-neighbors gpos)
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
  (some (fn [gpos] (<= (core/chebyshev-distance target-pos gpos) 1)) group-poss))

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
        candidates (conj (set (core/get-neighbors target)) target)
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
      (apply min-key (partial core/distance pos) candidates))))

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
    (if (<= (core/distance pos transport-pos) 1)
      (sa/update-world! update-in (conj pos :contents)
                        assoc :escort-mode :escorting)
      (ship-core/move-toward pos transport-pos))
    (revert-destroyer-to-seeking pos)))

(defn- process-destroyer-escorting [pos unit]
  (if-let [transport-pos (find-transport-by-id (:escort-transport-id unit))]
    (when (> (core/distance pos transport-pos) 1)
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
;; {:version 1, :tested-at "2026-03-12T11:58:25.133796-05:00", :module-hash "1358841495", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "-249113611"} {:id "defn/find-carrier-by-id", :kind "defn", :line 9, :end-line 20, :hash "-1516236955"} {:id "defn-/find-transport-by-id", :kind "defn-", :line 22, :end-line 33, :hash "724024597"} {:id "defn/find-enemy-near-positions", :kind "defn", :line 35, :end-line 48, :hash "1430359275"} {:id "defn-/group-positions", :kind "defn-", :line 50, :end-line 58, :hash "-773162896"} {:id "defn-/visible-to-group?", :kind "defn-", :line 60, :end-line 63, :hash "-1205346728"} {:id "defn-/end-pursuit", :kind "defn-", :line 65, :end-line 73, :hash "-588101425"} {:id "defn/begin-pursuit", :kind "defn", :line 75, :end-line 82, :hash "-1190171338"} {:id "defn/process-pursuit", :kind "defn", :line 84, :end-line 106, :hash "1676111292"} {:id "defn-/find-unadopted-transport", :kind "defn-", :line 110, :end-line 124, :hash "-32038296"} {:id "defn-/adopt-transport", :kind "defn-", :line 126, :end-line 136, :hash "-343915519"} {:id "defn-/find-enemy-near-destroyer-group", :kind "defn-", :line 138, :end-line 144, :hash "2079034753"} {:id "defn-/revert-destroyer-to-seeking", :kind "defn-", :line 146, :end-line 152, :hash "1346887847"} {:id "defn-/process-destroyer-seeking", :kind "defn-", :line 154, :end-line 157, :hash "1549646564"} {:id "defn-/process-destroyer-intercepting", :kind "defn-", :line 159, :end-line 165, :hash "260714804"} {:id "defn-/process-destroyer-escorting", :kind "defn-", :line 167, :end-line 171, :hash "-1398097223"} {:id "defn-/escorting-enemy-pos", :kind "defn-", :line 173, :end-line 177, :hash "-995263492"} {:id "defn-/dispatch-destroyer-mode", :kind "defn-", :line 179, :end-line 187, :hash "-961437166"} {:id "defn/process-escort-destroyer", :kind "defn", :line 189, :end-line 196, :hash "-1830899172"}]}
;; clj-mutate-manifest-end
