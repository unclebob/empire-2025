(ns empire.game.loop.item-processing.computer-items
  "Computer item processing and threat detection dispatch."
  (:require [empire.state.api :as sa]
            [empire.game-mechanics.movement.visibility :as visibility]
            [empire.computer.coordinator :as computer]
            [empire.computer.production :as computer-production]
            [empire.computer.threat-response :as threat-response]))

(defn- dispatch-detections!
  "Drains queued visibility detections and dispatches to threat-response."
  []
  (doseq [{:keys [pos cell]} (visibility/drain-detections!)]
    (threat-response/handle-detection! pos cell)))

(defn- coord-pair?
  [x]
  (and (vector? x)
       (= 2 (count x))
       (every? integer? x)))

(defn- next-computer-item-coords
  []
  (let [items (sa/read-state :computer-items)]
    (if (coord-pair? items) items (first items))))

(defn- process-one-computer-item
  "Processes a single computer item. Returns :done when item processed."
  []
  (let [coords (next-computer-item-coords)
        cell (get-in (sa/current-world) coords)
        is-computer-city? (and (= (:type cell) :city) (= (:city-status cell) :computer))
        has-computer-unit? (= (:owner (:contents cell)) :computer)
        should-requeue-city? (fn [city-pos]
                               (let [current-cell (get-in (sa/current-world) city-pos)]
                                 (pos? (:awake-kamikazee-fighters current-cell 0))))]
    (when is-computer-city?
      (computer-production/process-computer-city coords))
    (if-let [launched-pos (when is-computer-city?
                            (threat-response/launch-kamikazee-from-airport! coords))]
      (do
        (sa/update-state! :computer-items
                          #(cond-> (cons launched-pos (rest %))
                             (should-requeue-city? coords) (cons coords)))
        :continue)
      (if has-computer-unit?
      (let [new-coords (computer/process-computer-unit coords)]
        (dispatch-detections!)
        (if new-coords
          (do (sa/update-state! :computer-items #(cons new-coords (rest %))) :continue)
          (do (sa/update-state! :computer-items rest) :done)))
      (do (sa/update-state! :computer-items rest) :done)))))

(defn process-computer-items
  "Processes computer items until done or safety limit reached."
  []
  (loop [processed 0]
    (when (and (seq (sa/read-state :computer-items)) (< processed 100))
      (process-one-computer-item)
      (recur (inc processed)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:16:04.671644-05:00", :module-hash "249095973", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-926589689"} {:id "defn-/dispatch-detections!", :kind "defn-", :line 9, :end-line 13, :hash "-1526900239"} {:id "defn-/process-one-computer-item", :kind "defn-", :line 15, :end-line 30, :hash "-1616465506"} {:id "defn/process-computer-items", :kind "defn", :line 32, :end-line 38, :hash "-1463562774"}]}
;; clj-mutate-manifest-end
