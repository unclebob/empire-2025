;; mutation-tested: 2026-03-02
;; mutation-tested: 2026-02-27
(ns empire.computer.ship-escort
  "Computer destroyer escort and shared pursuit logic."
  (:require [empire.application.runtime :as app-runtime]
            [empire.application.state :as app-state]
            [empire.computer.core :as core]
            [empire.computer.ship-core :as ship-core]
            [empire.movement.visibility :as visibility]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- update-game-map!
  [f & args]
  (apply app-state/update-world! @state-ctx f args))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn find-carrier-by-id
  "Finds the position of a carrier with the given carrier-id."
  [carrier-id]
  (let [game-map (current-world)]
    (first (for [i (range (count game-map))
                 j (range (count (first game-map)))
                 :let [cell (get-in game-map [i j])
                       unit (:contents cell)]
                 :when (and unit
                            (= :carrier (:type unit))
                            (= carrier-id (:carrier-id unit)))]
             [i j]))))

(defn find-transport-by-id
  "Finds the position of a transport with the given transport-id."
  [transport-id]
  (let [game-map (current-world)]
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
  (let [game-map (current-world)]
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
  (let [unit (get-in (current-world) (conj pos :contents))
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
  (let [unit (get-in (current-world) (conj pos :contents))
        return-mode (if (:escort-carrier-id unit) :orbiting :escorting)]
    (update-game-map! update-in (conj pos :contents)
                      #(-> % (assoc :escort-mode return-mode)
                           (dissoc :pursuit-target :pursuit-steps-remaining)))))

(defn begin-pursuit
  "Switches an escort ship to pursuing mode, targeting the enemy."
  [pos enemy-pos]
  (update-game-map! update-in (conj pos :contents)
                    assoc :escort-mode :pursuing
                    :pursuit-target enemy-pos
                    :pursuit-steps-remaining 5)
  (ship-core/move-toward pos enemy-pos))

(defn process-pursuit
  "Continues pursuit: move toward a cell the enemy could have gone to,
   excluding cells visible to group members. Decrements steps remaining."
  [pos]
  (let [unit (get-in (current-world) (conj pos :contents))
        target (:pursuit-target unit)
        steps (:pursuit-steps-remaining unit)
        group (group-positions pos)
        candidates (conj (set (core/get-neighbors target)) target)
        sea-candidates (filter (fn [c]
                                 (let [cell (get-in (current-world) c)]
                                   (and cell
                                        (= :sea (:type cell))
                                        (not (visible-to-group? c group)))))
                               candidates)]
    (if (or (<= steps 1) (empty? sea-candidates))
      (end-pursuit pos)
      (let [chosen (rand-nth (vec sea-candidates))
            new-pos (ship-core/move-toward pos chosen)]
        (when new-pos
          (update-game-map! update-in (conj new-pos :contents)
                            assoc :pursuit-target chosen
                            :pursuit-steps-remaining (dec steps)))))))

;; --- Destroyer-specific escort ---

(defn- find-unadopted-transport
  "Finds the nearest computer transport without an escort-destroyer-id."
  [pos]
  (let [game-map (current-world)
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
  (let [destroyer (get-in (current-world) (conj pos :contents))
        transport (get-in (current-world) (conj transport-pos :contents))
        d-id (:destroyer-id destroyer)
        t-id (:transport-id transport)]
    (update-game-map! update-in (conj pos :contents)
                      #(assoc % :escort-transport-id t-id :escort-mode :intercepting))
    (update-game-map! update-in (conj transport-pos :contents)
                      #(assoc % :escort-destroyer-id d-id))))

(defn- find-enemy-near-destroyer-group
  "Finds a player ship adjacent to destroyer or its escorted transport."
  [pos]
  (let [unit (get-in (current-world) (conj pos :contents))
        transport-pos (when (:escort-transport-id unit)
                        (find-transport-by-id (:escort-transport-id unit)))]
    (find-enemy-near-positions (filter some? [pos transport-pos]))))

(defn- revert-destroyer-to-seeking
  "Reverts a destroyer escort to seeking mode, clearing transport reference."
  [pos]
  (update-game-map! update-in (conj pos :contents)
                    #(-> % (assoc :escort-mode :seeking)
                         (dissoc :escort-transport-id)))
  nil)

(defn- process-destroyer-seeking [pos]
  (when-let [transport-pos (find-unadopted-transport pos)]
    (adopt-transport pos transport-pos)
    (ship-core/move-toward pos transport-pos)))

(defn- process-destroyer-intercepting [pos unit]
  (if-let [transport-pos (find-transport-by-id (:escort-transport-id unit))]
    (if (<= (core/distance pos transport-pos) 1)
      (update-game-map! update-in (conj pos :contents)
                        assoc :escort-mode :escorting)
      (ship-core/move-toward pos transport-pos))
    (revert-destroyer-to-seeking pos)))

(defn- process-destroyer-escorting [pos unit]
  (if-let [transport-pos (find-transport-by-id (:escort-transport-id unit))]
    (when (> (core/distance pos transport-pos) 1)
      (ship-core/move-toward pos transport-pos))
    (revert-destroyer-to-seeking pos)))

(defn process-escort-destroyer
  "Processes a destroyer in escort mode."
  [pos]
  (let [unit (get-in (current-world) (conj pos :contents))
        mode (:escort-mode unit)]
    (if-let [enemy-pos (when (= :escorting mode)
                         (find-enemy-near-destroyer-group pos))]
      (begin-pursuit pos enemy-pos)
      (case mode
        :seeking (process-destroyer-seeking pos)
        :intercepting (process-destroyer-intercepting pos unit)
        :escorting (process-destroyer-escorting pos unit)
        :pursuing (process-pursuit pos)
        nil))))
