(ns empire.game-loop.item-processing
  "Item processing for player and computer turns.

   Handles processing individual units and cities, including auto-launching
   fighters, auto-disembarking armies, and delegating to movement modules."
  (:require [empire.atoms :as atoms]
            [empire.computer :as computer]
            [empire.computer.production :as computer-production]
            [empire.containers.ops :as container-ops]
            [empire.containers.helpers :as uc]
            [empire.fsm.integration :as integration]
            [empire.game-loop.round-init :as round-init]
            [empire.movement.coastline :as coastline]
            [empire.movement.explore :as explore]
            [empire.player.attention :as attention]))

(defn move-explore-unit
  "Moves an exploring unit. Returns new coords if still exploring, nil if done."
  [coords]
  (explore/move-explore-unit coords))

(defn move-coastline-unit
  "Moves a coastline-following unit. Returns nil when done."
  [coords]
  (coastline/move-coastline-unit coords))

(defn- auto-launch-fighter [coords cell]
  "Auto-launches a fighter from city airport or carrier if flight-path is set.
   Returns new coords if launched, nil otherwise."
  (let [flight-path (or (:flight-path cell)
                        (:flight-path (:contents cell)))
        has-awake-airport-fighter? (uc/has-awake? cell :awake-fighters)
        has-awake-carrier-fighter? (and (= (:type (:contents cell)) :carrier)
                                        (uc/has-awake? (:contents cell) :awake-fighters))]
    (when flight-path
      (cond
        has-awake-airport-fighter?
        (container-ops/launch-fighter-from-airport coords flight-path)

        has-awake-carrier-fighter?
        (container-ops/launch-fighter-from-carrier coords flight-path)

        :else nil))))

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
                                          (let [tcell (get-in @atoms/game-map target)]
                                            (and tcell
                                                 (= :land (:type tcell))
                                                 (not (:contents tcell)))))
                                        adjacent-cells))]
        (when valid-target
          (container-ops/disembark-army-with-target coords valid-target marching-orders))))))

(defn process-one-item
  "Processes a single player item. Returns :done if item was processed and removed,
   :continue if item needs more processing (e.g., movement), or :waiting if item needs user input.
   Requires move-current-unit-fn to be passed in to avoid circular dependency."
  [move-current-unit-fn]
  (let [coords (first @atoms/player-items)
        cell (get-in @atoms/game-map coords)
        unit (:contents cell)
        satellite-with-target? (and (= (:type unit) :satellite) (:target unit))]
    (if satellite-with-target?
      (do (swap! atoms/player-items rest) :done)
      (if-let [auto-coords (or (auto-launch-fighter coords cell)
                               (auto-disembark-army coords cell))]
        (do (swap! atoms/player-items #(cons auto-coords (rest %))) :continue)
        (if (attention/item-needs-attention? coords)
          (do
            (reset! atoms/cells-needing-attention [coords])
            (attention/set-attention-message coords)
            (reset! atoms/waiting-for-input true)
            :waiting)
          (let [new-coords (case (:mode unit)
                             :explore (move-explore-unit coords)
                             :coastline-follow (move-coastline-unit coords)
                             :moving (move-current-unit-fn coords)
                             nil)]
            (if new-coords
              (do (swap! atoms/player-items #(cons new-coords (rest %))) :continue)
              (do (swap! atoms/player-items rest) :done))))))))

(defn- process-one-computer-item
  "Processes a single computer item. Returns :done when item processed."
  []
  (let [coords (first @atoms/computer-items)
        cell (get-in @atoms/game-map coords)
        is-computer-city? (and (= (:type cell) :city) (= (:city-status cell) :computer))
        has-computer-unit? (= (:owner (:contents cell)) :computer)]
    ;; Handle city production if this is a computer city
    (when is-computer-city?
      (computer-production/process-computer-city coords))
    ;; Process unit movement if there's a computer unit here
    (if has-computer-unit?
      (let [new-coords (computer/process-computer-unit coords)]
        (if new-coords
          (do (swap! atoms/computer-items #(cons new-coords (rest %))) :continue)
          (do (swap! atoms/computer-items rest) :done)))
      ;; No unit, just city processing done
      (do (swap! atoms/computer-items rest) :done))))

(defn process-computer-items
  "Processes computer items until done or safety limit reached.
   Also processes Commanding General FSM at start of computer turn."
  []
  ;; Process the Commanding General's FSM first
  (integration/process-general-turn)
  (loop [processed 0]
    (cond
      (empty? @atoms/computer-items) nil
      (>= processed 100) nil
      :else
      (let [result (process-one-computer-item)]
        (case result
          :continue (recur (inc processed))
          :done (recur (inc processed)))))))

;; Processes player items in a batch until one of three conditions:
;; 1. player-items list becomes empty
;; 2. waiting-for-input is set (unit needs player attention)
;; 3. 100 items processed (batch limit to keep UI responsive)
(defn process-player-items-batch
  "Processes player items in a batch. Requires move-current-unit-fn to avoid circular dependency."
  [move-current-unit-fn]
  (loop [processed 0]
    (cond
      (empty? @atoms/player-items) nil
      @atoms/waiting-for-input nil
      (>= processed 100) nil
      :else
      (let [result (process-one-item move-current-unit-fn)]
        (case result
          :waiting nil
          :continue (recur (inc processed))
          :done (recur (inc processed)))))))
