(ns empire.game.loop.item-processing.computer-items
  "Computer item processing and threat detection dispatch."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.debug.logging :as debug-logging]
            [empire.game-mechanics.visibility :as visibility]
            [empire.computer.coordinator :as computer]
            [empire.computer.production :as computer-production]
            [empire.game.loop.item-processing.computer-item-decisions :as decisions]
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
  (cond
    (and (= (:type cell) :city)
         (= (:city-status cell) :computer))
    :process-computer/city

    (= (:owner (:contents cell)) :computer)
    (keyword "process-computer" (name (:type (:contents cell))))

    :else
    :process-computer/other))

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
      (computer-production/process-computer-city-with-current-visibility coords))
      (let [launched-pos (when is-computer-city?
                         (threat-response/launch-kamikazee-from-airport! coords))
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

(defn process-computer-items
  "Processes computer items until done or safety limit reached."
  []
  (refresh-computer-map!)
  (loop [processed 0]
    (when (and (seq (normalize-computer-items)) (< processed 100))
      (process-one-computer-item)
      (recur (inc processed))))
  (when (empty? (normalize-computer-items))
    (debug-logging/log-computer-units!)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:55:12.489999-05:00", :module-hash "-702469286", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 9, :hash "296092869"} {:id "defn-/dispatch-detections!", :kind "defn-", :line 11, :end-line 15, :hash "-1526900239"} {:id "defn-/normalize-computer-items", :kind "defn-", :line 17, :end-line 19, :hash "-323406046"} {:id "defn-/next-computer-item-coords", :kind "defn-", :line 21, :end-line 23, :hash "-1996732340"} {:id "defn-/item-phase", :kind "defn-", :line 25, :end-line 36, :hash "2100141913"} {:id "defn-/make-empty-visible-map", :kind "defn-", :line 38, :end-line 41, :hash "556819858"} {:id "defn-/refresh-computer-map!", :kind "defn-", :line 43, :end-line 57, :hash "-1673307678"} {:id "defn-/process-one-computer-item", :kind "defn-", :line 59, :end-line 88, :hash "1442127460"} {:id "defn/process-computer-items", :kind "defn", :line 90, :end-line 99, :hash "1620002335"}]}
;; clj-mutate-manifest-end
