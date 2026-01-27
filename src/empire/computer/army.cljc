(ns empire.computer.army
  "Computer army module - executes exploration missions assigned by Lieutenant."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.visibility :as visibility]
            [empire.fsm.engine :as engine]
            [empire.fsm.base-establishment :as base]
            [empire.fsm.coastline-explorer :as explorer]
            [empire.fsm.interior-explorer :as interior]
            [empire.fsm.waiting-reserve :as reserve]
            [empire.fsm.hurry-up-and-wait :as huaw]
            [empire.fsm.context :as context]
            [empire.fsm.lieutenant :as lieutenant]
            [empire.debug :as debug]))

;; --- Reporting to Lieutenant ---

(defn- find-lieutenant-for-army
  "Find the Lieutenant that commands this army.
   Uses the army's :lieutenant field to find the correct Lieutenant.
   Falls back to first Lieutenant if army has no assigned lieutenant."
  [unit]
  (when-let [general @atoms/commanding-general]
    (if-let [lt-name (:lieutenant unit)]
      ;; Find the named Lieutenant
      (first (filter #(= (:name %) lt-name) (:lieutenants general)))
      ;; Fallback: first Lieutenant (legacy behavior)
      (first (:lieutenants general)))))

(defn- update-lieutenant!
  "Update a Lieutenant in the General's lieutenants list."
  [updated-lt]
  (when-let [general @atoms/commanding-general]
    (swap! atoms/commanding-general
           update :lieutenants
           (fn [lts]
             (mapv (fn [lt]
                     (if (= (:name lt) (:name updated-lt))
                       updated-lt
                       lt))
                   lts)))))

(defn- on-coastline?
  "Returns true if position is land adjacent to sea."
  [pos]
  (let [cell (get-in @atoms/game-map pos)]
    (and (= :land (:type cell))
         (map-utils/adjacent-to-sea? pos atoms/game-map))))

(defn- deliver-fsm-events!
  "Deliver events from the unit's event-queue to the Lieutenant.
   Returns the unit with its event-queue cleared."
  [unit]
  (let [events (:event-queue unit)]
    (when (seq events)
      (doseq [event events]
        (when-let [lt (find-lieutenant-for-army unit)]
          (let [updated-lt (engine/post-event lt event)]
            (update-lieutenant! updated-lt)))))
    (assoc unit :event-queue [])))

(defn- report-beach-discovery!
  "Report beach/coastline candidate at the current position to the Lieutenant.
   Free city reports are now handled by FSM events."
  [pos unit]
  (when (and (on-coastline? pos)
             (base/valid-beach-candidate? pos))
    (when-let [lt (find-lieutenant-for-army unit)]
      (let [updated-lt (engine/post-event lt {:type :coastline-mapped
                                               :priority :normal
                                               :data {:coords pos}})]
        (update-lieutenant! updated-lt)))))

(defn- start-coastline-exploration
  "Start coastline exploration at the given position, optionally with a target."
  [pos unit cell target unit-id]
  (let [explorer-data (explorer/create-explorer-data pos target unit-id)
        updated-unit (-> unit
                         (assoc :mode :explore)
                         (merge explorer-data)
                         (dissoc :reason :visited))]
    (swap! atoms/game-map assoc-in pos (assoc cell :contents updated-unit))))

(defn- start-interior-exploration
  "Start interior exploration at the given position, optionally with a target."
  [pos unit cell target unit-id]
  (let [explorer-data (interior/create-interior-explorer-data pos target unit-id)
        updated-unit (-> unit
                         (assoc :mode :explore)
                         (merge explorer-data)
                         (dissoc :reason :visited))]
    (swap! atoms/game-map assoc-in pos (assoc cell :contents updated-unit))))

(defn- start-waiting-reserve
  "Start waiting reserve mission at the given position, with station target."
  [pos unit cell station unit-id]
  (let [reserve-data (reserve/create-waiting-reserve-data pos station unit-id)
        updated-unit (-> unit
                         (assoc :mode :explore)  ; Use :explore mode for FSM-driven behavior
                         (merge reserve-data)
                         (dissoc :reason :visited))]
    (swap! atoms/game-map assoc-in pos (assoc cell :contents updated-unit))))

(defn- start-hurry-up-and-wait
  "Start hurry-up-and-wait mission: move to target and enter sentry mode."
  [pos unit cell target unit-id]
  (let [huaw-data (huaw/create-hurry-up-and-wait-data pos target unit-id)
        updated-unit (-> unit
                         (assoc :mode :explore)  ; Use :explore mode for FSM-driven behavior
                         (merge huaw-data)
                         (dissoc :reason :visited))]
    (swap! atoms/game-map assoc-in pos (assoc cell :contents updated-unit))))

(defn- register-mission-with-lieutenant!
  "Immediately registers the mission assignment with the Lieutenant.
   Updates explorer counts and direct-reports so subsequent mission
   assignments see the correct state."
  [unit pos mission-type unit-id]
  (when-let [lt (find-lieutenant-for-army unit)]
    (let [explorer {:unit-id unit-id
                    :coords pos
                    :mission-type mission-type
                    :fsm-state :exploring
                    :status :active}
          lt-with-report (update lt :direct-reports conj explorer)
          updated-lt (case mission-type
                       :explore-coastline (update lt-with-report :coastline-explorer-count inc)
                       :explore-interior (update lt-with-report :interior-explorer-count inc)
                       :hurry-up-and-wait (update lt-with-report :waiting-army-count inc)
                       lt-with-report)]
      (update-lieutenant! updated-lt))))

(defn- assign-exploration-mission
  "Assigns mission to an awake army based on Lieutenant's orders.
   Mission type is determined by Lieutenant's current state and quotas.
   Immediately updates Lieutenant's counts to ensure correct mission
   assignment for subsequent armies in the same turn."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        lt (find-lieutenant-for-army unit)
        mission-info (when lt (lieutenant/get-mission-for-unit lt pos))
        mission-type (:mission-type mission-info)
        target (:target mission-info)
        unit-id (keyword (str "army-" (hash pos)))]
    (when mission-type
      ;; Immediately update Lieutenant's counts for this mission
      (register-mission-with-lieutenant! unit pos mission-type unit-id))
    (case mission-type
      :explore-coastline (start-coastline-exploration pos unit cell target unit-id)
      :explore-interior (start-interior-exploration pos unit cell target unit-id)
      :hurry-up-and-wait (start-hurry-up-and-wait pos unit cell target unit-id)
      ;; nil mission means waiting-for-transport - army stays awake
      nil (debug/log-action! [:army-no-mission pos :waiting-for-transport])
      ;; Unknown mission type - should not happen
      (debug/log-action! [:army-unknown-mission pos mission-type]))))

(defn- step-explorer-fsm
  "Step the unit's FSM and return updated unit."
  [unit pos]
  (let [;; Build context with position for guards
        unit-with-pos (assoc-in unit [:fsm-data :position] pos)
        ctx (context/build-context unit-with-pos)
        ;; Step the FSM
        updated (engine/step unit-with-pos ctx)]
    updated))

(defn- terminal-state?
  "Returns true if fsm-state is a terminal state (a vector like [:terminal :stuck])."
  [fsm-state]
  (and (vector? fsm-state)
       (= :terminal (first fsm-state))))

(defn- execute-exploration
  "Executes one exploration step using the coastline explorer FSM.
   The FSM action returns :move-to in fsm-data.
   Events from the FSM are delivered to the Lieutenant.
   Terminal states trigger wakeup and mission-ended event delivery.
   Always returns nil (army moves once per round)."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        ;; Step FSM - action computes and returns :move-to
        unit-stepped (step-explorer-fsm unit pos)
        fsm-state (:fsm-state unit-stepped)]
    ;; Check for terminal state first
    (if (terminal-state? fsm-state)
      ;; Terminal state - deliver events (including :mission-ended)
      ;; Check for :enter-sentry-mode flag to set unit mode appropriately
      (let [enter-sentry? (get-in unit-stepped [:fsm-data :enter-sentry-mode])
            new-mode (if enter-sentry? :sentry :awake)]
        (deliver-fsm-events! unit-stepped)
        (debug/log-action! [:army-terminal pos fsm-state {:enter-sentry enter-sentry?}])
        (swap! atoms/game-map assoc-in pos
               (assoc cell :contents (-> unit
                                         (assoc :mode new-mode)
                                         (dissoc :fsm :fsm-state :fsm-data :event-queue))))
        nil)
      ;; Normal processing
      (let [;; Deliver any events from FSM to Lieutenant
            unit-events-delivered (deliver-fsm-events! unit-stepped)
            fsm-data (:fsm-data unit-events-delivered)
            next-pos (:move-to fsm-data)]
        (if next-pos
          (let [next-cell (get-in @atoms/game-map next-pos)
                moved-unit (-> unit-events-delivered
                               (assoc-in [:fsm-data :position] next-pos)
                               ;; Clear :move-to after consuming it
                               (update :fsm-data dissoc :move-to))]
            (swap! atoms/game-map assoc-in pos (dissoc cell :contents))
            (swap! atoms/game-map assoc-in next-pos (assoc next-cell :contents moved-unit))
            (visibility/update-cell-visibility next-pos :computer)
            ;; Report beach candidates at new position (free cities reported via FSM events)
            (report-beach-discovery! next-pos moved-unit)
            nil)
          ;; Stuck (no move computed but not terminal) - wake up
          (let [adjacent-info (for [[dr dc] map-utils/neighbor-offsets
                                    :let [r (+ (first pos) dr)
                                          c (+ (second pos) dc)
                                          adj-cell (get-in @atoms/game-map [r c])]
                                    :when adj-cell]
                                [[r c] (:type adj-cell)
                                 (when-let [contents (:contents adj-cell)]
                                   (:type contents))])]
            (debug/log-action! [:army-wake pos :stuck
                                {:fsm-state (:fsm-state unit-stepped)
                                 :adjacent (vec adjacent-info)}])
            (swap! atoms/game-map assoc-in pos
                   (assoc cell :contents (-> unit
                                             (assoc :mode :awake)
                                             (dissoc :fsm :fsm-state :fsm-data :event-queue))))
            nil))))))

(defn process-army
  "Processes a computer army's turn.
   - Awake armies get assigned exploration missions
   - Exploring armies execute one step via FSM (including moving-to-start)
   Always returns nil (army moves once per round)."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)]
    (when (and unit (= :computer (:owner unit)) (= :army (:type unit)))
      (case (:mode unit)
        :awake (do (assign-exploration-mission pos) nil)
        :explore (execute-exploration pos)
        nil))))
