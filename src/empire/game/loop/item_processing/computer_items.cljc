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

(defn- normalize-computer-items
  []
  (let [items (sa/read-state :computer-items)]
    (cond
      (coord-pair? items) [items]
      (sequential? items) items
      :else [])))

(defn- next-computer-item-coords
  []
  (first (normalize-computer-items)))

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
                          (fn [_]
                            (let [remaining (rest (normalize-computer-items))]
                              (cond-> (vec (cons launched-pos remaining))
                                (should-requeue-city? coords) (#(vec (cons coords %)))))))
        :continue)
      (if has-computer-unit?
      (let [new-coords (computer/process-computer-unit coords)]
        (dispatch-detections!)
        (if new-coords
          (do (sa/update-state! :computer-items (fn [_]
                                                  (vec (cons new-coords
                                                             (rest (normalize-computer-items))))))
              :continue)
          (do (sa/update-state! :computer-items (fn [_] (vec (rest (normalize-computer-items))))) :done)))
      (do (sa/update-state! :computer-items (fn [_] (vec (rest (normalize-computer-items))))) :done)))))

(defn process-computer-items
  "Processes computer items until done or safety limit reached."
  []
  (loop [processed 0]
    (when (and (seq (normalize-computer-items)) (< processed 100))
      (process-one-computer-item)
      (recur (inc processed)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:16:04.671644-05:00", :module-hash "249095973", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "-926589689"} {:id "defn-/dispatch-detections!", :kind "defn-", :line 9, :end-line 13, :hash "-1526900239"} {:id "defn-/process-one-computer-item", :kind "defn-", :line 15, :end-line 30, :hash "-1616465506"} {:id "defn/process-computer-items", :kind "defn", :line 32, :end-line 38, :hash "-1463562774"}]}
;; clj-mutate-manifest-end
