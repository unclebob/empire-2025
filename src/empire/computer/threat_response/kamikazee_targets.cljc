(ns empire.computer.threat-response.kamikazee-targets
  (:require [empire.computer.core :as core]
            [empire.state.api :as sa]))

(def ^:private target-choice-width 3)

(defn current-round
  [ctx]
  (if-let [read-runtime-state (:read-runtime-state ctx)]
    (or (read-runtime-state :round-number) 0)
    0))

(defn- player-army?
  [world pos]
  (let [unit (get-in world (conj pos :contents))]
    (and unit
         (= :player (:owner unit))
         (= :army (:type unit)))))

(defn alive-targets
  [world targets]
  (->> targets
       (filter (fn [{:keys [pos]}] (player-army? world pos)))
       vec))

(defn invasion-target-points
  [state world]
  (let [army-targets (alive-targets world (:kamikazee-army-targets state))]
    (or (seq (map :pos army-targets))
        (seq (:detection-points state))
        (seq (:target-land-set state))
        [])))

(defn ordered-army-target-positions
  [state _round-number world]
  (->> (:kamikazee-army-targets state)
       (alive-targets world)
       (sort-by (fn [{:keys [seen-round pos]}]
                  [(- seen-round) pos]))
       (mapv :pos)))

(defn refresh-army-targets!
  [ctx]
  (let [world (or (when-let [current-world (:current-world ctx)]
                    (current-world))
                  (sa/current-world))
        round-number (current-round ctx)]
    (when-let [update-major-invasion-state! (:update-major-invasion-state! ctx)]
      (update-major-invasion-state!
       (fn [state]
         (assoc state :kamikazee-army-targets
                (alive-targets world (:kamikazee-army-targets state))))))
    (let [state (or (when-let [load-major-invasion-state (:load-major-invasion-state ctx)]
                      (load-major-invasion-state))
                    (sa/read-state :major-invasion-state))
          targets (ordered-army-target-positions state round-number world)]
      (doseq [i (range (count world))
              j (range (count (first world)))
              :let [unit (get-in world [i j :contents])]
              :when (and unit
                         (= :computer (:owner unit))
                         (= :fighter (:type unit))
                         (:kamikazee unit))]
        ((:update-game-map! ctx) assoc-in [i j :contents :kamikazee-targets] targets)))))

(defn record-army-target!
  [ctx pos]
  (let [round-number (current-round ctx)]
    ((:update-major-invasion-state! ctx)
     (fn [state]
       (let [targets (remove #(= pos (:pos %)) (:kamikazee-army-targets state))]
         (assoc state :kamikazee-army-targets
                (vec (cons {:pos pos :seen-round round-number} targets)))))))
  (refresh-army-targets! ctx))

(defn choose-army-target
  [state round-number world]
  (let [ordered (ordered-army-target-positions state round-number world)
        choices (vec (take target-choice-width ordered))]
    (when (seq choices)
      (rand-nth choices))))

(defn choose-major-target
  [state world pos]
  (let [targets (invasion-target-points state world)]
    (when (seq targets)
      (apply min-key #(core/distance pos %) targets))))

(defn fighter-support-targets
  [state]
  (or (seq (:kamikazee-terminal-sites state))
      (seq (:sea-reachable-detection-points state))
      (seq (:detection-points state))
      []))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-14T08:34:56.298459-05:00", :module-hash "-1735400828", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-384211463"} {:id "def/target-choice-width", :kind "def", :line 5, :end-line 5, :hash "981135397"} {:id "defn/current-round", :kind "defn", :line 7, :end-line 11, :hash "246685672"} {:id "defn-/player-army?", :kind "defn-", :line 13, :end-line 18, :hash "-1781861708"} {:id "defn/alive-targets", :kind "defn", :line 20, :end-line 24, :hash "2146204505"} {:id "defn/invasion-target-points", :kind "defn", :line 26, :end-line 32, :hash "-1326020518"} {:id "defn/ordered-army-target-positions", :kind "defn", :line 34, :end-line 40, :hash "218540818"} {:id "defn/refresh-army-targets!", :kind "defn", :line 42, :end-line 64, :hash "-1393250656"} {:id "defn/record-army-target!", :kind "defn", :line 66, :end-line 74, :hash "-777896876"} {:id "defn/choose-army-target", :kind "defn", :line 76, :end-line 81, :hash "-1766208919"} {:id "defn/choose-major-target", :kind "defn", :line 83, :end-line 87, :hash "585573367"} {:id "defn/fighter-support-targets", :kind "defn", :line 89, :end-line 94, :hash "958643692"}]}
;; clj-mutate-manifest-end
