(ns empire.game.loop.item-processing.computer-items
  "Computer item processing and threat detection dispatch."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.computer.coordinator :as computer]
            [empire.computer.production :as computer-production]
            [empire.game.loop.item-processing.computer-item-decisions :as decisions]
            [empire.computer.threat-response :as threat-response]))

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

(defn- process-one-computer-item
  "Processes a single computer item. Returns :done when item processed."
  []
  (let [coords (next-computer-item-coords)
        cell (get-in (sa/current-world) coords)
        is-computer-city? (and (= (:type cell) :city) (= (:city-status cell) :computer))
        should-requeue-city? (fn [city-pos]
                               (let [current-cell (get-in (sa/current-world) city-pos)]
                                 (pos? (:awake-kamikazee-fighters current-cell 0))))]
    (when is-computer-city?
      (computer-production/process-computer-city coords))
    (let [launched-pos (when is-computer-city?
                         (threat-response/launch-kamikazee-from-airport! coords))
          new-coords (when (= (:owner (:contents cell)) :computer)
                       (let [coords* (computer/process-computer-unit coords)]
                         (dispatch-detections!)
                         coords*))
          action (decisions/computer-item-action {:cell cell
                                                  :launched-pos launched-pos
                                                  :new-coords new-coords
                                                  :should-requeue-city? (should-requeue-city? coords)})]
      (sa/update-state! :computer-items
                        (fn [items]
                          (decisions/next-computer-items items action)))
      (if (#{:launch :unit-continue} (:action action))
        :continue
        :done))))

(defn process-computer-items
  "Processes computer items until done or safety limit reached."
  []
  (loop [processed 0]
    (when (and (seq (normalize-computer-items)) (< processed 100))
      (process-one-computer-item)
      (recur (inc processed)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T16:54:47.967083-05:00", :module-hash "975337460", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "1027675793"} {:id "defn-/dispatch-detections!", :kind "defn-", :line 10, :end-line 14, :hash "-1526900239"} {:id "defn-/normalize-computer-items", :kind "defn-", :line 16, :end-line 18, :hash "-323406046"} {:id "defn-/next-computer-item-coords", :kind "defn-", :line 20, :end-line 22, :hash "-1996732340"} {:id "defn-/process-one-computer-item", :kind "defn-", :line 24, :end-line 50, :hash "-147831484"} {:id "defn/process-computer-items", :kind "defn", :line 52, :end-line 58, :hash "-1531772992"}]}
;; clj-mutate-manifest-end
