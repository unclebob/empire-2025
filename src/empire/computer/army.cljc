(ns empire.computer.army
  "Computer army module - executes exploration missions assigned by Lieutenant."
  (:require [empire.atoms :as atoms]
            [empire.movement.map-utils :as map-utils]
            [empire.movement.visibility :as visibility]
            [empire.fsm.engine :as engine]
            [empire.fsm.base-establishment :as base]
            [empire.fsm.coastline-explorer :as explorer]
            [empire.fsm.context :as context]))

(def explore-steps 50)

;; --- Reporting to Lieutenant ---

(defn- find-lieutenant-for-army
  "Find the Lieutenant that should receive reports from this army.
   Currently returns the first Lieutenant (armies don't track their commander yet)."
  []
  (when-let [general @atoms/commanding-general]
    (first (:lieutenants general))))

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
        (when-let [lt (find-lieutenant-for-army)]
          (let [updated-lt (engine/post-event lt event)]
            (update-lieutenant! updated-lt)))))
    (assoc unit :event-queue [])))

(defn- report-beach-discovery!
  "Report beach/coastline candidate at the current position to the Lieutenant.
   Free city reports are now handled by FSM events."
  [pos]
  (when (and (on-coastline? pos)
             (base/valid-beach-candidate? pos))
    (when-let [lt (find-lieutenant-for-army)]
      (let [updated-lt (engine/post-event lt {:type :coastline-mapped
                                               :priority :normal
                                               :data {:coords pos}})]
        (update-lieutenant! updated-lt)))))

(defn- assign-exploration-mission
  "Assigns exploration mission to an awake army.
   Initializes the coastline explorer FSM."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        explorer-data (explorer/create-explorer-data pos)
        updated-unit (-> unit
                         (assoc :mode :explore
                                :explore-steps explore-steps)
                         (merge explorer-data)
                         (dissoc :reason :visited))]
    (swap! atoms/game-map assoc-in pos (assoc cell :contents updated-unit))))

(defn- step-explorer-fsm
  "Step the coastline explorer FSM and return updated unit."
  [unit pos]
  (let [;; Build context with position for guards
        unit-with-pos (assoc-in unit [:fsm-data :position] pos)
        ctx (context/build-context unit-with-pos)
        ;; Step the FSM
        updated (engine/step unit-with-pos ctx)]
    updated))

(defn- execute-exploration
  "Executes one exploration step using the coastline explorer FSM.
   The FSM action returns :move-to in fsm-data.
   Events from the FSM are delivered to the Lieutenant.
   Always returns nil (army moves once per round)."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)
        remaining-steps (dec (:explore-steps unit explore-steps))]
    (if (<= remaining-steps 0)
      ;; Done exploring - go back to awake
      (do
        (swap! atoms/game-map assoc-in pos
               (assoc cell :contents (-> unit
                                         (assoc :mode :awake)
                                         (dissoc :explore-steps :fsm :fsm-state :fsm-data :event-queue))))
        nil)
      ;; Step FSM - action computes and returns :move-to
      (let [unit-stepped (step-explorer-fsm unit pos)
            ;; Deliver any events from FSM to Lieutenant
            unit-events-delivered (deliver-fsm-events! unit-stepped)
            fsm-data (:fsm-data unit-events-delivered)
            next-pos (:move-to fsm-data)]
        (if next-pos
          (let [next-cell (get-in @atoms/game-map next-pos)
                moved-unit (-> unit-events-delivered
                               (assoc :explore-steps remaining-steps)
                               (assoc-in [:fsm-data :position] next-pos)
                               ;; Clear :move-to after consuming it
                               (update :fsm-data dissoc :move-to))]
            (swap! atoms/game-map assoc-in pos (dissoc cell :contents))
            (swap! atoms/game-map assoc-in next-pos (assoc next-cell :contents moved-unit))
            (visibility/update-cell-visibility next-pos :computer)
            ;; Report beach candidates at new position (free cities reported via FSM events)
            (report-beach-discovery! next-pos)
            nil)
          ;; Stuck - wake up
          (do
            (swap! atoms/game-map assoc-in pos
                   (assoc cell :contents (-> unit
                                             (assoc :mode :awake)
                                             (dissoc :explore-steps :fsm :fsm-state :fsm-data :event-queue))))
            nil))))))

(defn process-army
  "Processes a computer army's turn.
   - Awake armies get assigned exploration missions
   - Exploring armies execute one step via FSM
   Always returns nil (army moves once per round)."
  [pos]
  (let [cell (get-in @atoms/game-map pos)
        unit (:contents cell)]
    (when (and unit (= :computer (:owner unit)) (= :army (:type unit)))
      (case (:mode unit)
        :awake (do (assign-exploration-mission pos) nil)
        :explore (execute-exploration pos)
        nil))))
