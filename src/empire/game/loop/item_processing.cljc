(ns empire.game.loop.item-processing
  "Player and computer item processing, movement execution with sidestep logic."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.api :as movement-api]
            [empire.game-mechanics.debug.logging :as debug-logging]
            [empire.game-mechanics.visibility :as visibility]
            [empire.config.core :as config]
            [empire.game-mechanics.containers.ops :as container-ops]
            [empire.game-mechanics.containers.helpers :as uc]
            [empire.player.attention :as player-attention]
            [empire.game-mechanics.movement.explore :as explore]
            [empire.game-mechanics.movement.coastline :as coastline]
            [empire.game.loop.item-processing-decisions :as decisions]
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
  (let [moved-unit (:contents (get-in (sa/current-world) pos))]
    (case (decisions/resolve-move-result-action
           {:result result
            :fighter? (= :fighter (:type moved-unit))
            :moved-owner-matches? (= (:owner moved-unit) owner)
            :fighter-has-steps? (pos? (:steps-remaining moved-unit 0))})
      :advance-step (advance-step pos)
      :fighter-continue (do (end-combat-move pos owner) pos)
      :combat-stop (do (end-combat-move pos owner) nil)
      :stay-put pos
      :stop nil)))

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

(defn- awake-carrier-fighter? [cell]
  (let [contents (:contents cell)]
    (and (= :carrier (:type contents)) (uc/has-awake? contents :awake-fighters))))

(defn- should-requeue-airport?
  [coords]
  (let [cell (get-in (sa/current-world) coords)]
    (and (= :city (:type cell))
         (uc/has-awake? cell :awake-fighters))))

(defn- launch-airport-flight-path-fighter
  "If city has fighters, a flight-path, and hasn't launched this round,
   launch one. Marks city so it won't launch again this round."
  [coords cell]
  (when (and (= :city (:type cell))
             (pos? (:fighter-count cell 0))
             (not (:flight-path-launched cell))
             (not (:contents cell))
             (:flight-path cell))
    (when (container-ops/launch-fighter-from-airport coords (:flight-path cell))
      (sa/update-world! assoc-in (conj coords :flight-path-launched) true))))

(defn- auto-launch-fighter [coords cell]
  "Auto-launches a fighter from carrier if flight-path is set.
   Returns new coords if launched, nil otherwise."
  (when-let [flight-path (airport-flight-path cell)]
    (when (awake-carrier-fighter? cell)
      (container-ops/launch-fighter-from-carrier coords flight-path))))

(defn- empty-land-cell?
  [tcell]
  (and tcell
       (= :land (:type tcell))
       (not (:contents tcell))))

(defn- adjacent-empty-land-target
  [coords]
  (let [[x y] coords
        adjacent-cells (for [dx [-1 0 1] dy [-1 0 1]
                             :when (not (and (zero? dx) (zero? dy)))]
                         [(+ x dx) (+ y dy)])]
    (first (filter #(empty-land-cell? (get-in (sa/current-world) %))
                   adjacent-cells))))

(defn- auto-disembark-army [coords cell]
  "Auto-disembarks an army from transport if marching-orders is set.
   Returns new coords if disembarked, nil otherwise."
  (let [contents (:contents cell)
        marching-orders (:marching-orders contents)
        has-awake-army? (and (= (:type contents) :transport)
                             (pos? (:awake-armies contents 0)))]
    (when (and marching-orders has-awake-army?)
      (when-let [valid-target (adjacent-empty-land-target coords)]
        (container-ops/disembark-army-with-target coords valid-target marching-orders)))))

(defn- auto-movement-coords
  [coords unit]
  (case (:mode unit)
    :explore (move-explore-unit coords)
    :coastline-follow (move-coastline-unit coords)
    :moving (move-current-unit coords)
    nil))

(defn- player-visibility-maps-aligned?
  [unit]
  (and (= :player (:owner unit))
       (vector? (sa/read-state :player-map))
       (= (count (sa/read-state :player-map)) (count (sa/current-world)))
       (= (count (first (sa/read-state :player-map)))
          (count (first (sa/current-world))))))

(defn- process-auto-movement [coords unit]
  (let [new-coords (auto-movement-coords coords unit)]
    (when (player-visibility-maps-aligned? unit)
      (visibility/update-combatant-map :player-map :player))
    (if new-coords
      (do (sa/update-state! :player-items #(cons new-coords (rest %))) :continue)
      (do (sa/update-state! :player-items rest) :done))))

(defn- try-auto-launch-or-disembark [coords cell]
  (or (auto-launch-fighter coords cell)
      (auto-disembark-army coords cell)))

(defn- apply-auto-move-item
  [coords unit auto-coords]
  (if auto-coords
    (do (sa/update-state! :player-items
                          #(cond-> (cons auto-coords (rest %))
                             (should-requeue-airport? coords) (cons coords)))
        :continue)
    (process-auto-movement coords unit)))

(defn- apply-attention-item
  [coords]
  (sa/write-state! :cells-needing-attention [coords])
  (player-attention/set-attention-message coords)
  (sa/write-state! :waiting-for-input true)
  :waiting)

(defn- apply-player-item-action
  [action coords unit auto-coords]
  (case action
    :skip-satellite
    (do (sa/update-state! :player-items rest) :done)

    :auto-move
    (apply-auto-move-item coords unit auto-coords)

    :attention
    (apply-attention-item coords)))

(defn- process-one-item
  "Processes a single player item. Returns :done, :continue, or :waiting."
  []
  (sa/update-state! :player-items decisions/normalize-item-queue)
  (let [coords (first (sa/read-state :player-items))
        cell (get-in (sa/current-world) coords)]
    (launch-airport-flight-path-fighter coords cell)
    (let [cell (get-in (sa/current-world) coords)
          unit (:contents cell)
          sat-moving? (decisions/satellite-with-target? unit)
          unit-in-auto-mode? (decisions/unit-auto-mode? unit)
          auto-coords (when-not sat-moving? (try-auto-launch-or-disembark coords cell))
          needs-attention? (player-attention/item-needs-attention? coords)
          action (decisions/player-item-action
                  {:sat-moving? sat-moving?
                   :auto-coords auto-coords
                   :unit-in-auto-mode? unit-in-auto-mode?
                   :needs-attention? needs-attention?})]
      (debug-logging/log-player-item-decision!
       coords
       {:cell-type (:type cell)
        :city-status (:city-status cell)
        :unit-type (:type unit)
        :unit-mode (:mode unit)
        :sat-moving? sat-moving?
        :unit-in-auto-mode? unit-in-auto-mode?
        :auto-coords auto-coords
        :needs-attention? needs-attention?
        :action action})
      (apply-player-item-action action coords unit auto-coords))))

(defn process-computer-items
  "Processes computer items until done or safety limit reached."
  []
  (computer-items/process-computer-items))

(defn- batch-should-stop? [processed]
  (decisions/batch-stop?
   {:paused? (sa/read-state :paused)
    :no-player-items? (empty? (sa/read-state :player-items))
    :waiting-for-input? (sa/read-state :waiting-for-input)
    :processed processed}))

(defn- continue-player-batch?
  [result]
  (#{:continue :done} result))

(defn process-player-items-batch []
  (loop [processed 0]
    (when-not (batch-should-stop? processed)
      (let [result (process-one-item)]
        (check-player-victory!)
        (when (continue-player-batch? result)
          (recur (inc processed)))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T15:24:24.600349-05:00", :module-hash "935517979", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-122680584"} {:id "defn/check-player-victory!", :kind "defn", :line 16, :end-line nil, :hash "1003493917"} {:id "defn-/advance-step", :kind "defn-", :line 21, :end-line nil, :hash "-1281843861"} {:id "defn-/end-combat-move", :kind "defn-", :line 30, :end-line nil, :hash "-1512457889"} {:id "defn-/resolve-move-result", :kind "defn-", :line 44, :end-line nil, :hash "1588429236"} {:id "defn/move-current-unit", :kind "defn", :line 59, :end-line nil, :hash "367190430"} {:id "defn/move-explore-unit", :kind "defn", :line 74, :end-line nil, :hash "-703094186"} {:id "defn/move-coastline-unit", :kind "defn", :line 79, :end-line nil, :hash "-127782"} {:id "defn-/airport-flight-path", :kind "defn-", :line 84, :end-line nil, :hash "502978521"} {:id "defn-/awake-carrier-fighter?", :kind "defn-", :line 87, :end-line nil, :hash "-230396723"} {:id "defn-/should-requeue-airport?", :kind "defn-", :line 91, :end-line nil, :hash "-2031622699"} {:id "defn-/launch-airport-flight-path-fighter", :kind "defn-", :line 97, :end-line nil, :hash "1707431413"} {:id "defn-/auto-launch-fighter", :kind "defn-", :line 109, :end-line nil, :hash "-1026824940"} {:id "defn-/empty-land-cell?", :kind "defn-", :line 116, :end-line nil, :hash "-1403485921"} {:id "defn-/adjacent-empty-land-target", :kind "defn-", :line 122, :end-line nil, :hash "1203791058"} {:id "defn-/auto-disembark-army", :kind "defn-", :line 131, :end-line nil, :hash "1032407122"} {:id "defn-/auto-movement-coords", :kind "defn-", :line 142, :end-line nil, :hash "1887087588"} {:id "defn-/player-visibility-maps-aligned?", :kind "defn-", :line 150, :end-line nil, :hash "2099265261"} {:id "defn-/process-auto-movement", :kind "defn-", :line 158, :end-line nil, :hash "279405655"} {:id "defn-/try-auto-launch-or-disembark", :kind "defn-", :line 166, :end-line nil, :hash "1778439438"} {:id "defn-/apply-auto-move-item", :kind "defn-", :line 170, :end-line nil, :hash "-939754946"} {:id "defn-/apply-attention-item", :kind "defn-", :line 179, :end-line nil, :hash "544841115"} {:id "defn-/apply-player-item-action", :kind "defn-", :line 186, :end-line nil, :hash "1051103722"} {:id "defn-/process-one-item", :kind "defn-", :line 198, :end-line nil, :hash "631613388"} {:id "defn/process-computer-items", :kind "defn", :line 229, :end-line nil, :hash "-1537801344"} {:id "defn-/batch-should-stop?", :kind "defn-", :line 234, :end-line nil, :hash "1013519075"} {:id "defn-/continue-player-batch?", :kind "defn-", :line 241, :end-line nil, :hash "1358965279"} {:id "defn/process-player-items-batch", :kind "defn", :line 245, :end-line nil, :hash "-1636444607"}]}
;; clj-mutate-manifest-end
