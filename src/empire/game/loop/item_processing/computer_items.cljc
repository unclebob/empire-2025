(ns empire.game.loop.item-processing.computer-items
  "Computer item processing and threat detection dispatch."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.debug.logging :as debug-logging]
            [empire.game-mechanics.visibility :as visibility]
            [empire.computer.coordinator :as computer]
            [empire.computer.production :as computer-production]
            [empire.game.loop.item-processing.computer-item-decisions :as decisions]
            [empire.game.loop.monitor :as monitor]
            [empire.computer.threat-response-impl :as threat-response]))

(defn- dispatch-detections!
  "Drains queued visibility detections and dispatches to threat-response."
  []
  (doseq [{:keys [pos cell]} (visibility/drain-detections!)]
    (threat-response/handle-detection! pos cell)))

(defn- normalize-computer-items
  []
  (decisions/normalize-computer-items (sa/read-state :computer-items)))

(defn- next-computer-item-coords
  []
  (first (normalize-computer-items)))

(defn- item-phase
  [cell]
  (let [contents (:contents cell)]
    (cond
      (and (= (:type cell) :city)
           (= (:city-status cell) :computer))
      :process-computer/city

      (and (= :transport (:type contents))
           (= :computer (:owner contents))
           monitor/*phase-sink*)
      (keyword "transport" (name (or (:transport-mission contents) "nil")))

      (= (:owner contents) :computer)
      (keyword "process-computer" (name (:type contents)))

      :else
      :process-computer/other)))

(defn- make-empty-visible-map
  [game-map]
  (vec (repeat (count game-map)
               (vec (repeat (count (first game-map)) nil)))))

(defn- refresh-computer-map!
  []
  (let [game-map (sa/current-world)
        current-map (sa/read-state :computer-map)
        visible-map (if (and (vector? current-map)
                             (= (count current-map) (count game-map))
                             (= (count (first current-map))
                                (count (first game-map))))
                      current-map
                      (make-empty-visible-map game-map))]
    (when-let [updated (visibility/update-combatant-map-state
                        visible-map
                        :computer
                        game-map)]
      (sa/write-state! :computer-map updated))))

(defn- process-one-computer-item
  "Processes a single computer item. Returns :done when item processed."
  []
  (let [coords (next-computer-item-coords)
        cell (get-in (sa/current-world) coords)
        unit-id (get-in cell [:contents :computer-unit-id])
        phase (item-phase cell)
        is-computer-city? (and (= (:type cell) :city) (= (:city-status cell) :computer))
        should-requeue-city? (fn [city-pos]
                               (let [current-cell (get-in (sa/current-world) city-pos)]
                                 (pos? (:awake-kamikazee-fighters current-cell 0))))]
    (when is-computer-city?
      (if monitor/*phase-sink*
        (swap! monitor/*phase-sink* conj
               (monitor/time-phase :city/production
                 (computer-production/process-computer-city-with-current-visibility coords)))
        (computer-production/process-computer-city-with-current-visibility coords)))
      (let [launched-pos (when is-computer-city?
                         (if monitor/*phase-sink*
                           (let [[timing result]
                                 (monitor/time-phase-returning :city/kamikazee
                                   (threat-response/launch-kamikazee-from-airport! coords))]
                             (swap! monitor/*phase-sink* conj timing)
                             result)
                           (threat-response/launch-kamikazee-from-airport! coords)))
          new-coords (when (= (:owner (:contents cell)) :computer)
                       (debug-logging/with-computer-unit-context
                         unit-id
                         (fn []
                           (let [coords* (computer/process-computer-unit coords)]
                             (dispatch-detections!)
                             coords*))))
          action (decisions/computer-item-action {:cell cell
                                                  :launched-pos launched-pos
                                                  :new-coords new-coords
                                                  :should-requeue-city? (should-requeue-city? coords)})
          state (decisions/computer-item-state {:items (sa/read-state :computer-items)
                                                :action action})]
      (sa/write-state! :computer-items (:computer-items state))
      (:result state))))

(defn- current-item-phase
  []
  (when-let [coords (next-computer-item-coords)]
    (item-phase (get-in (sa/current-world) coords))))

(defn process-computer-items
  "Processes computer items until done or safety limit reached."
  []
  (let [sink monitor/*phase-sink*]
    (if sink
      (swap! sink conj (monitor/time-phase :refresh-computer-map (refresh-computer-map!)))
      (refresh-computer-map!))
    (loop [processed 0]
      (when (and (seq (normalize-computer-items)) (< processed 100))
        (if sink
          (let [phase (current-item-phase)]
            (swap! sink conj (monitor/time-phase phase (process-one-computer-item))))
          (process-one-computer-item))
        (recur (inc processed))))
    (when (empty? (normalize-computer-items))
      (debug-logging/log-computer-units!))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T12:52:40.820475-05:00", :module-hash "393035575", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "1027675793"} {:id "defn-/dispatch-detections!", :kind "defn-", :line 10, :end-line 14, :hash "-1526900239"} {:id "defn-/normalize-computer-items", :kind "defn-", :line 16, :end-line 18, :hash "-323406046"} {:id "defn-/next-computer-item-coords", :kind "defn-", :line 20, :end-line 22, :hash "-1996732340"} {:id "defn-/process-one-computer-item", :kind "defn-", :line 24, :end-line 48, :hash "1164443286"} {:id "defn/process-computer-items", :kind "defn", :line 50, :end-line 56, :hash "-1531772992"}]}
;; clj-mutate-manifest-end
