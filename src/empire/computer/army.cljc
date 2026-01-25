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

(defn- find-adjacent-free-cities
  "Find free cities adjacent to the given position."
  [pos]
  (filter (fn [neighbor]
            (let [cell (get-in @atoms/computer-map neighbor)]
              (and (= :city (:type cell))
                   (= :free (:city-status cell)))))
          (map-utils/get-matching-neighbors pos @atoms/game-map map-utils/neighbor-offsets some?)))

(defn- on-coastline?
  "Returns true if position is land adjacent to sea."
  [pos]
  (let [cell (get-in @atoms/game-map pos)]
    (and (= :land (:type cell))
         (map-utils/adjacent-to-sea? pos atoms/game-map))))

(defn- report-discoveries!
  "Report any discoveries at the current position to the Lieutenant."
  [pos]
  (when-let [lt (find-lieutenant-for-army)]
    ;; Report free cities
    (doseq [city-pos (find-adjacent-free-cities pos)]
      (let [updated-lt (engine/post-event lt {:type :free-city-found
                                               :priority :high
                                               :data {:coords city-pos}})]
        (update-lieutenant! updated-lt)))
    ;; Report coastline/beach candidates
    (when (and (on-coastline? pos)
               (base/valid-beach-candidate? pos))
      (let [lt-fresh (find-lieutenant-for-army)  ; Re-fetch after possible update
            updated-lt (engine/post-event lt-fresh {:type :coastline-mapped
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
                                         (dissoc :explore-steps :fsm :fsm-state :fsm-data))))
        nil)
      ;; Step FSM - action computes and returns :move-to
      (let [unit-stepped (step-explorer-fsm unit pos)
            fsm-data (:fsm-data unit-stepped)
            next-pos (:move-to fsm-data)]
        (if next-pos
          (let [next-cell (get-in @atoms/game-map next-pos)
                moved-unit (-> unit-stepped
                               (assoc :explore-steps remaining-steps)
                               (assoc-in [:fsm-data :position] next-pos)
                               ;; Clear :move-to after consuming it
                               (update :fsm-data dissoc :move-to))]
            (swap! atoms/game-map assoc-in pos (dissoc cell :contents))
            (swap! atoms/game-map assoc-in next-pos (assoc next-cell :contents moved-unit))
            (visibility/update-cell-visibility next-pos :computer)
            ;; Report any discoveries at new position
            (report-discoveries! next-pos)
            nil)
          ;; Stuck - wake up
          (do
            (swap! atoms/game-map assoc-in pos
                   (assoc cell :contents (-> unit
                                             (assoc :mode :awake)
                                             (dissoc :explore-steps :fsm :fsm-state :fsm-data))))
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
