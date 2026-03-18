(ns empire.computer.ship-carrier-group
  "Carrier group escort - battleship and submarine orbiting behavior."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.ship-core :as ship-core]
            [empire.computer.ship-escort :as escort]
            [empire.computer.movement :as computer-movement]
            [empire.game-mechanics.movement.visibility :as visibility]))


(defn- computer-unit-at
  [pos]
  (get-in (sa/read-state :computer-map) (conj pos :contents)))


(def orbit-ring
  "16 offsets forming a clockwise Chebyshev ring at radius 2."
  [[-2 -2] [-2 -1] [-2 0] [-2 1] [-2 2]
   [-1 2] [0 2] [1 2]
   [2 2] [2 1] [2 0] [2 -1] [2 -2]
   [1 -2] [0 -2] [-1 -2]])

(defn- find-carrier-with-open-slot
  "Finds the nearest computer carrier with an open slot for the given unit type."
  [pos unit-type]
  (let [game-map (sa/read-state :computer-map)
        candidates (for [i (range (count game-map))
                         j (range (count (first game-map)))
                         :let [cell (get-in game-map [i j])
                               unit (:contents cell)]
                         :when (and unit
                                    (= :carrier (:type unit))
                                    (= :computer (:owner unit))
                                    (case unit-type
                                      :battleship (nil? (:group-battleship-id unit))
                                      :submarine (< (count (:group-submarine-ids unit [])) 2)
                                      false))]
                     [i j])]
    (when (seq candidates)
      (apply min-key (partial core/distance pos) candidates))))

(defn- initial-orbit-angle
  "Returns the starting orbit angle for a new escort."
  [unit-type carrier]
  (case unit-type
    :battleship 0
    :submarine (if (empty? (:group-submarine-ids carrier [])) 5 11)))

(defn- adopt-carrier-escort
  "Pairs a battleship or submarine escort with a carrier."
  [pos carrier-pos unit-type]
  (let [escort-unit (computer-unit-at pos)
        carrier (computer-unit-at carrier-pos)
        carrier-id (:carrier-id carrier)
        escort-id (:escort-id escort-unit)
        angle (initial-orbit-angle unit-type carrier)]
    (sa/update-world! update-in (conj pos :contents)
                      assoc :escort-carrier-id carrier-id
                      :escort-mode :intercepting
                      :orbit-angle angle)
    (case unit-type
      :battleship
      (sa/update-world! update-in (conj carrier-pos :contents)
                        assoc :group-battleship-id escort-id)
      :submarine
      (sa/update-world! update-in (conj carrier-pos :contents)
                        update :group-submarine-ids conj escort-id))
    (visibility/sync-ai-unit-to-computer-map! pos)
    (visibility/sync-ai-unit-to-computer-map! carrier-pos)))

(defn- orbit-target-pos
  "Computes the absolute position for an orbit angle around carrier."
  [carrier-pos angle]
  (let [[dr dc] (nth orbit-ring (mod angle 16))]
    [(+ (first carrier-pos) dr) (+ (second carrier-pos) dc)]))

(defn- valid-orbit-pos?
  "Returns true if pos is a valid empty sea cell on the game map."
  [pos]
  (let [cell (get-in (sa/read-state :computer-map) pos)]
    (and cell (= :sea (:type cell)) (nil? (:contents cell)))))

(defn- find-next-orbit-angle
  "Finds the next orbit angle with a valid sea position, starting from start-angle.
   Returns nil if all 16 positions are invalid."
  [carrier-pos start-angle]
  (first (for [i (range 16)
               :let [angle (mod (+ start-angle i) 16)
                     pos (orbit-target-pos carrier-pos angle)]
               :when (valid-orbit-pos? pos)]
           angle)))

(defn- revert-escort-to-seeking
  "Reverts an escort to seeking mode, clearing carrier reference."
  [pos]
  (sa/update-world! update-in (conj pos :contents)
                    #(-> % (assoc :escort-mode :seeking)
                         (dissoc :escort-carrier-id :orbit-angle)))
  (visibility/sync-ai-unit-to-computer-map! pos))

(defn- process-escort-seeking
  "Escort seeking: find a carrier with an open slot and adopt it."
  [pos unit-type]
  (when-let [carrier-pos (find-carrier-with-open-slot pos unit-type)]
    (adopt-carrier-escort pos carrier-pos unit-type)
    (ship-core/move-toward pos carrier-pos)))

(defn- transition-to-orbiting
  "Transitions an escort to orbiting mode."
  [pos carrier-pos unit]
  (let [angle (or (:orbit-angle unit) 0)
        valid-angle (find-next-orbit-angle carrier-pos angle)]
    (if valid-angle
      (let [target (orbit-target-pos carrier-pos valid-angle)]
        (when (not= pos target)
          (ship-core/move-toward pos target))
        (sa/update-world! update-in
                          (conj (or (when (not= pos target) target) pos) :contents)
                          assoc :escort-mode :orbiting :orbit-angle valid-angle)
        (visibility/sync-ai-unit-to-computer-map! (or (when (not= pos target) target) pos)))
      (do
        (sa/update-world! update-in (conj pos :contents)
                          assoc :escort-mode :orbiting)
        (visibility/sync-ai-unit-to-computer-map! pos)))))

(defn- process-escort-intercepting
  "Escort intercepting: move toward carrier, transition to orbiting at radius 2."
  [pos]
  (let [unit (computer-unit-at pos)]
    (if-let [carrier-pos (escort/find-carrier-by-id (:escort-carrier-id unit))]
      (if (<= (core/chebyshev-distance pos carrier-pos) 2)
        (transition-to-orbiting pos carrier-pos unit)
        (ship-core/move-toward pos carrier-pos))
      (revert-escort-to-seeking pos))))

(defn- process-escort-orbiting
  "Escort orbiting: advance one step along the orbit ring."
  [pos]
  (let [unit (computer-unit-at pos)]
    (if-let [carrier-pos (escort/find-carrier-by-id (:escort-carrier-id unit))]
      (let [current-angle (or (:orbit-angle unit) 0)
            next-angle (find-next-orbit-angle carrier-pos (inc current-angle))]
        (if next-angle
          (let [target (orbit-target-pos carrier-pos next-angle)]
            (if (= pos target)
              (do
                (sa/update-world! update-in (conj pos :contents)
                                  assoc :orbit-angle next-angle)
                (visibility/sync-ai-unit-to-computer-map! pos))
              (when (valid-orbit-pos? target)
                (core/move-unit-to pos target)
                (computer-movement/update-cell-visibility! pos :computer)
                (computer-movement/update-cell-visibility! target :computer)
                (sa/update-world! update-in (conj target :contents)
                                  assoc :orbit-angle next-angle)
                (visibility/sync-ai-unit-to-computer-map! target))))
          nil))
      (revert-escort-to-seeking pos))))

(defn- find-enemy-near-carrier-group
  "Finds a player ship adjacent to escort or its carrier."
  [pos]
  (let [unit (computer-unit-at pos)
        carrier-pos (when (:escort-carrier-id unit)
                      (escort/find-carrier-by-id (:escort-carrier-id unit)))]
    (escort/find-enemy-near-positions (filter some? [pos carrier-pos]))))

(defn- orbiting-enemy-pos
  "Returns an enemy position only when the escort is orbiting."
  [pos mode]
  (when (= :orbiting mode)
    (find-enemy-near-carrier-group pos)))

(defn- dispatch-escort-mode
  "Executes the behavior for the current escort mode."
  [pos unit-type mode]
  (case mode
    :seeking (process-escort-seeking pos unit-type)
    :intercepting (process-escort-intercepting pos)
    :orbiting (process-escort-orbiting pos)
    :pursuing (escort/process-pursuit pos)
    nil))

(defn process-carrier-group-escort
  "Processes a battleship or submarine in carrier group escort mode."
  [pos unit-type]
  (let [unit (computer-unit-at pos)
        mode (:escort-mode unit)]
    (if-let [enemy-pos (orbiting-enemy-pos pos mode)]
      (escort/begin-pursuit pos enemy-pos)
      (dispatch-escort-mode pos unit-type mode))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T11:58:19.344118-05:00", :module-hash "-1544608056", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "1905823106"} {:id "def/orbit-ring", :kind "def", :line 10, :end-line 15, :hash "1896622938"} {:id "defn-/find-carrier-with-open-slot", :kind "defn-", :line 17, :end-line 34, :hash "-2019653570"} {:id "defn-/initial-orbit-angle", :kind "defn-", :line 36, :end-line 41, :hash "-1883950430"} {:id "defn-/adopt-carrier-escort", :kind "defn-", :line 43, :end-line 61, :hash "1508885090"} {:id "defn-/orbit-target-pos", :kind "defn-", :line 63, :end-line 67, :hash "2118903567"} {:id "defn-/valid-orbit-pos?", :kind "defn-", :line 69, :end-line 73, :hash "1667318196"} {:id "defn-/find-next-orbit-angle", :kind "defn-", :line 75, :end-line 83, :hash "954411254"} {:id "defn-/revert-escort-to-seeking", :kind "defn-", :line 85, :end-line 90, :hash "-2124523176"} {:id "defn-/process-escort-seeking", :kind "defn-", :line 92, :end-line 97, :hash "723363070"} {:id "defn-/transition-to-orbiting", :kind "defn-", :line 99, :end-line 112, :hash "1105365618"} {:id "defn-/process-escort-intercepting", :kind "defn-", :line 114, :end-line 122, :hash "1724523770"} {:id "defn-/process-escort-orbiting", :kind "defn-", :line 124, :end-line 143, :hash "1215011207"} {:id "defn-/find-enemy-near-carrier-group", :kind "defn-", :line 145, :end-line 151, :hash "-1065071916"} {:id "defn-/orbiting-enemy-pos", :kind "defn-", :line 153, :end-line 157, :hash "-965978677"} {:id "defn-/dispatch-escort-mode", :kind "defn-", :line 159, :end-line 167, :hash "-1246732420"} {:id "defn/process-carrier-group-escort", :kind "defn", :line 169, :end-line 176, :hash "-1690704505"}]}
;; clj-mutate-manifest-end
