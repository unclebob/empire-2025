(ns empire.game.loop.item-processing
  "Player and computer item processing, movement execution with sidestep logic."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.api :as movement-api]
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
    (container-ops/launch-fighter-from-airport coords (:flight-path cell))
    (sa/update-world! assoc-in (conj coords :flight-path-launched) true)))

(defn- auto-launch-fighter [coords cell]
  "Auto-launches a fighter from carrier if flight-path is set.
   Returns new coords if launched, nil otherwise."
  (when-let [flight-path (airport-flight-path cell)]
    (when (awake-carrier-fighter? cell)
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
    (when (and (= :player (:owner unit))
               (vector? (sa/read-state :player-map))
               (= (count (sa/read-state :player-map)) (count (sa/current-world)))
               (= (count (first (sa/read-state :player-map)))
                  (count (first (sa/current-world)))))
      (visibility/update-combatant-map :player-map :player))
    (if new-coords
      (do (sa/update-state! :player-items #(cons new-coords (rest %))) :continue)
      (do (sa/update-state! :player-items rest) :done))))

(defn- try-auto-launch-or-disembark [coords cell]
  (or (auto-launch-fighter coords cell)
      (auto-disembark-army coords cell)))

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
          action (decisions/player-item-action
                  {:sat-moving? sat-moving?
                   :auto-coords auto-coords
                   :unit-in-auto-mode? unit-in-auto-mode?
                   :needs-attention? (player-attention/item-needs-attention? coords)})]
      (case action
        :skip-satellite
        (do (sa/update-state! :player-items rest) :done)

        :auto-move
        (if auto-coords
          (do (sa/update-state! :player-items
                                #(cond-> (cons auto-coords (rest %))
                                   (should-requeue-airport? coords) (cons coords)))
              :continue)
          (process-auto-movement coords unit))

        :attention
        (do (sa/write-state! :cells-needing-attention [coords])
            (player-attention/set-attention-message coords)
            (sa/write-state! :waiting-for-input true)
            :waiting)))))

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
;; {:version 1, :tested-at "2026-03-27T01:52:51.717557-05:00", :module-hash "-1017583001", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 13, :hash "1080197236"} {:id "defn/check-player-victory!", :kind "defn", :line 15, :end-line 18, :hash "1003493917"} {:id "defn-/advance-step", :kind "defn-", :line 20, :end-line 27, :hash "-1281843861"} {:id "defn-/end-combat-move", :kind "defn-", :line 29, :end-line 41, :hash "-1330459889"} {:id "defn-/resolve-move-result", :kind "defn-", :line 43, :end-line 56, :hash "1588429236"} {:id "defn/move-current-unit", :kind "defn", :line 58, :end-line 71, :hash "367190430"} {:id "defn/move-explore-unit", :kind "defn", :line 73, :end-line 76, :hash "-703094186"} {:id "defn/move-coastline-unit", :kind "defn", :line 78, :end-line 81, :hash "-127782"} {:id "defn-/airport-flight-path", :kind "defn-", :line 83, :end-line 84, :hash "502978521"} {:id "defn-/awake-airport-fighter?", :kind "defn-", :line 86, :end-line 87, :hash "-358747613"} {:id "defn-/awake-carrier-fighter?", :kind "defn-", :line 89, :end-line 91, :hash "-230396723"} {:id "defn-/should-requeue-airport?", :kind "defn-", :line 93, :end-line 98, :hash "-431335550"} {:id "defn-/auto-launch-fighter", :kind "defn-", :line 100, :end-line 109, :hash "251699985"} {:id "defn-/auto-disembark-army", :kind "defn-", :line 111, :end-line 130, :hash "217231336"} {:id "defn-/process-auto-movement", :kind "defn-", :line 132, :end-line 146, :hash "-773956978"} {:id "defn-/try-auto-launch-or-disembark", :kind "defn-", :line 148, :end-line 150, :hash "1778439438"} {:id "defn-/process-one-item", :kind "defn-", :line 152, :end-line 183, :hash "1376545742"} {:id "defn/process-computer-items", :kind "defn", :line 185, :end-line 188, :hash "-1537801344"} {:id "defn-/batch-should-stop?", :kind "defn-", :line 190, :end-line 195, :hash "1013519075"} {:id "defn/process-player-items-batch", :kind "defn", :line 197, :end-line 205, :hash "1662766734"}]}
;; clj-mutate-manifest-end
