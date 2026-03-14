(ns empire.game.loop.item-processing
  "Player and computer item processing, movement execution with sidestep logic."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.api :as movement-api]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.player.attention :as player-attention]
            [empire.game-mechanics.movement.explore :as explore]
            [empire.game-mechanics.movement.coastline :as coastline]
            [empire.game.loop.item-processing.computer-items :as computer-items]))

(defn check-player-victory!
  "Game-over from city elimination is handled at city-conquest time."
  []
  nil)

(defn- advance-step
  "Decrements steps-remaining for unit at pos. Returns pos if steps remain, nil otherwise."
[pos]
  (let [moved-unit (:contents (get-in (sa/current-world) pos))]
    (when moved-unit
      (let [new-steps (dec (:steps-remaining moved-unit 1))]
        (sa/update-world! assoc-in (conj pos :contents :steps-remaining) new-steps)
        (when (pos? new-steps) pos)))))

(defn- end-combat-move
  "Zeroes steps for attacker at pos if they won (same owner). Combat always ends the move."
[pos owner]
  (let [moved-unit (:contents (get-in (sa/current-world) pos))]
    (when (and moved-unit (= (:owner moved-unit) owner))
      (if (= :fighter (:type moved-unit))
        (let [new-steps (dec (:steps-remaining moved-unit 1))]
          (sa/update-world! update-in (conj pos :contents)
                            #(-> %
                                 (assoc :mode :awake
                                        :steps-remaining new-steps)
                                 (dissoc :target :extended))))
        (sa/update-world! assoc-in (conj pos :contents :steps-remaining) 0)))))

(defn- resolve-move-result
  "Resolves a move result into the next position. Returns pos if unit should continue, nil if done."
  [result pos owner]
  (case result
    (:sidestep :normal) (advance-step pos)
    :combat (do (end-combat-move pos owner)
                (let [moved-unit (:contents (get-in (sa/current-world) pos))]
                  (when (and moved-unit
                             (= :fighter (:type moved-unit))
                             (= (:owner moved-unit) owner)
                             (pos? (:steps-remaining moved-unit 0)))
                    pos)))
    :woke pos
    :docked nil))

(defn move-current-unit
  "Moves the unit at coords one step. Returns new coords if still moving, nil if done."
 ([coords] (move-current-unit coords config/max-sidesteps))
 ([coords max-sidesteps]
   (let [world (sa/current-world)
         cell (get-in world coords)
         unit (:contents cell)]
    (when (and (= (:mode unit) :moving)
               (pos? (:steps-remaining unit 1)))
      (let [{:keys [result pos]} (movement-api/move-unit coords (:target unit) cell :game-map)
            next-pos (resolve-move-result result pos (:owner unit))]
        (if (and (= result :sidestep) next-pos (pos? max-sidesteps))
          (recur pos (dec max-sidesteps))
           next-pos))))))

(defn move-explore-unit
  "Moves an exploring unit. Returns new coords if still exploring, nil if done."
  [coords]
  (explore/move-explore-unit coords))

(defn move-coastline-unit
  "Moves a coastline-following unit. Returns nil when done."
  [coords]
  (coastline/move-coastline-unit coords))

(defn- airport-flight-path [cell]
  (or (:flight-path cell) (:flight-path (:contents cell))))

(defn- awake-airport-fighter? [cell]
  (and (not (:contents cell)) (uc/has-awake? cell :awake-fighters)))

(defn- awake-carrier-fighter? [cell]
  (let [contents (:contents cell)]
    (and (= :carrier (:type contents)) (uc/has-awake? contents :awake-fighters))))

(defn- should-requeue-airport?
  [coords]
  (let [cell (get-in (sa/current-world) coords)]
    (and (= :city (:type cell))
         (uc/has-awake? cell :awake-fighters)
         (airport-flight-path cell))))

(defn- auto-launch-fighter [coords cell]
  "Auto-launches a fighter from city airport or carrier if flight-path is set.
   Returns new coords if launched, nil otherwise."
  (when-let [flight-path (airport-flight-path cell)]
    (cond
      (awake-airport-fighter? cell)
      (container-ops/launch-fighter-from-airport coords flight-path)

      (awake-carrier-fighter? cell)
      (container-ops/launch-fighter-from-carrier coords flight-path))))

(defn- auto-disembark-army [coords cell]
  "Auto-disembarks an army from transport if marching-orders is set.
   Returns new coords if disembarked, nil otherwise."
  (let [contents (:contents cell)
        marching-orders (:marching-orders contents)
        has-awake-army? (and (= (:type contents) :transport)
                             (pos? (:awake-armies contents 0)))]
        (when (and marching-orders has-awake-army?)
      (let [[x y] coords
            adjacent-cells (for [dx [-1 0 1] dy [-1 0 1]
                                 :when (not (and (zero? dx) (zero? dy)))]
                             [(+ x dx) (+ y dy)])
            valid-target (first (filter (fn [target]
                                          (let [tcell (get-in (sa/current-world) target)]
                                            (and tcell
                                                 (= :land (:type tcell))
                                                 (not (:contents tcell)))))
                                        adjacent-cells))]
        (when valid-target
          (container-ops/disembark-army-with-target coords valid-target marching-orders))))))

(defn- process-auto-movement [coords unit]
  (let [new-coords (case (:mode unit)
                     :explore (move-explore-unit coords)
                     :coastline-follow (move-coastline-unit coords)
                     :moving (move-current-unit coords)
                     nil)]
    (if new-coords
      (do (sa/update-state! :player-items #(cons new-coords (rest %))) :continue)
      (do (sa/update-state! :player-items rest) :done))))

(defn- satellite-with-target? [unit]
  (and (= (:type unit) :satellite) (:target unit)))

(defn- try-auto-launch-or-disembark [coords cell]
  (or (auto-launch-fighter coords cell)
      (auto-disembark-army coords cell)))

(defn- process-one-item
  "Processes a single player item. Returns :done, :continue, or :waiting."
  []
  (let [coords (first (sa/read-state :player-items))
        cell (get-in (sa/current-world) coords)
        unit (:contents cell)
        sat-moving? (satellite-with-target? unit)
        unit-in-auto-mode? (#{:moving :explore :coastline-follow} (:mode unit))
        auto-coords (when-not sat-moving? (try-auto-launch-or-disembark coords cell))]
    (cond
      sat-moving?
      (do (sa/update-state! :player-items rest) :done)

      auto-coords
      (do (sa/update-state! :player-items
                            #(cond-> (cons auto-coords (rest %))
                               (should-requeue-airport? coords) (cons coords)))
          :continue)

      unit-in-auto-mode?
      (process-auto-movement coords unit)

      (player-attention/item-needs-attention? coords)
      (do (sa/write-state! :cells-needing-attention [coords])
          (player-attention/set-attention-message coords)
          (sa/write-state! :waiting-for-input true)
          :waiting)

      :else
      (process-auto-movement coords unit))))

(defn process-computer-items
  "Processes computer items until done or safety limit reached."
  []
  (computer-items/process-computer-items))

(defn- batch-should-stop? [processed]
  (or (sa/read-state :paused)
      (empty? (sa/read-state :player-items))
      (sa/read-state :waiting-for-input)
      (>= processed 100)))

(defn process-player-items-batch []
  (loop [processed 0]
    (when-not (batch-should-stop? processed)
      (let [result (process-one-item)]
        (check-player-victory!)
        (case result
          :waiting nil
          :continue (recur (inc processed))
          :done (recur (inc processed)))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:16:04.721977-05:00", :module-hash "-148534857", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 11, :hash "674996277"} {:id "defn/check-player-victory!", :kind "defn", :line 13, :end-line 16, :hash "1003493917"} {:id "defn-/advance-step", :kind "defn-", :line 18, :end-line 25, :hash "-1281843861"} {:id "defn-/end-combat-move", :kind "defn-", :line 27, :end-line 39, :hash "-621370463"} {:id "defn-/resolve-move-result", :kind "defn-", :line 41, :end-line 54, :hash "321976992"} {:id "defn/move-current-unit", :kind "defn", :line 56, :end-line 69, :hash "367190430"} {:id "defn/move-explore-unit", :kind "defn", :line 71, :end-line 74, :hash "-703094186"} {:id "defn/move-coastline-unit", :kind "defn", :line 76, :end-line 79, :hash "-127782"} {:id "defn-/airport-flight-path", :kind "defn-", :line 81, :end-line 82, :hash "502978521"} {:id "defn-/awake-airport-fighter?", :kind "defn-", :line 84, :end-line 85, :hash "-358747613"} {:id "defn-/awake-carrier-fighter?", :kind "defn-", :line 87, :end-line 89, :hash "-230396723"} {:id "defn-/auto-launch-fighter", :kind "defn-", :line 91, :end-line 100, :hash "251699985"} {:id "defn-/auto-disembark-army", :kind "defn-", :line 102, :end-line 121, :hash "217231336"} {:id "defn-/process-auto-movement", :kind "defn-", :line 123, :end-line 131, :hash "-781362847"} {:id "defn-/satellite-with-target?", :kind "defn-", :line 133, :end-line 134, :hash "-1209035924"} {:id "defn-/try-auto-launch-or-disembark", :kind "defn-", :line 136, :end-line 138, :hash "1778439438"} {:id "defn-/process-one-item", :kind "defn-", :line 140, :end-line 166, :hash "-747283155"} {:id "defn/process-computer-items", :kind "defn", :line 168, :end-line 171, :hash "-1537801344"} {:id "defn-/batch-should-stop?", :kind "defn-", :line 173, :end-line 177, :hash "102333966"} {:id "defn/process-player-items-batch", :kind "defn", :line 179, :end-line 187, :hash "1662766734"}]}
;; clj-mutate-manifest-end
