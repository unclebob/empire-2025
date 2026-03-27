(ns empire.computer.threat-response.kamikazee-targets
  (:require [empire.computer.shared.grid :as grid]
            [empire.computer.threat-response.kamikazee-target-decisions :as decisions]
            [empire.state.api :as sa]))

(def ^:private target-choice-width 3)

(defn- visible-world
  [ctx]
  (let [visible (when-let [read-runtime-state (:read-runtime-state ctx)]
                  (read-runtime-state :computer-map))]
    (if (vector? visible)
      visible
      (sa/read-state :computer-map))))

(defn- writable-world
  [ctx]
  (or (when-let [current-world (:current-world ctx)]
        (current-world))
      (sa/read-state :computer-map)))

(defn- kamikazee-target-writeable-unit?
  [ctx pos]
  (let [world (writable-world ctx)
        unit (get-in world (conj pos :contents))]
    (and (= :fighter (:type unit))
         (= :computer (:owner unit))
         (:kamikazee unit))))

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

(defn- trim-dead-army-targets!
  [ctx world]
  (when-let [update-major-invasion-state! (:update-major-invasion-state! ctx)]
    (update-major-invasion-state!
     #(decisions/trim-dead-army-targets-state world %))))

(defn- write-fighter-targets!
  [ctx writable-map targets]
  (doseq [{:keys [pos]} (decisions/fighter-target-writes writable-map targets)]
    (when (kamikazee-target-writeable-unit? ctx pos)
      ((:update-game-map! ctx) assoc-in (conj pos :contents :kamikazee-targets) targets))))

(defn refresh-army-targets!
  [ctx]
  (let [world (visible-world ctx)
        writable-map (writable-world ctx)
        round-number (current-round ctx)]
    (trim-dead-army-targets! ctx world)
    (let [state (or (when-let [load-major-invasion-state (:load-major-invasion-state ctx)]
                      (load-major-invasion-state))
                    (sa/read-state :major-invasion-state))
          targets (ordered-army-target-positions state round-number world)]
      (write-fighter-targets! ctx writable-map targets))))

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
      (apply min-key #(grid/distance pos %) targets))))

(defn fighter-support-targets
  [state]
  (or (seq (:kamikazee-terminal-sites state))
      (seq (:sea-reachable-detection-points state))
      (seq (:detection-points state))
      []))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-26T23:45:17.462529-05:00", :module-hash "-437727690", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "-2036962333"} {:id "def/target-choice-width", :kind "def", :line 6, :end-line 6, :hash "981135397"} {:id "defn-/visible-world", :kind "defn-", :line 8, :end-line 14, :hash "-953761304"} {:id "defn-/writable-world", :kind "defn-", :line 16, :end-line 20, :hash "425099876"} {:id "defn-/kamikazee-target-writeable-unit?", :kind "defn-", :line 22, :end-line 28, :hash "-1563584775"} {:id "defn/current-round", :kind "defn", :line 30, :end-line 34, :hash "246685672"} {:id "defn-/player-army?", :kind "defn-", :line 36, :end-line 41, :hash "-1781861708"} {:id "defn/alive-targets", :kind "defn", :line 43, :end-line 47, :hash "2146204505"} {:id "defn/invasion-target-points", :kind "defn", :line 49, :end-line 55, :hash "-1326020518"} {:id "defn/ordered-army-target-positions", :kind "defn", :line 57, :end-line 63, :hash "218540818"} {:id "defn-/trim-dead-army-targets!", :kind "defn-", :line 65, :end-line 69, :hash "-2084101296"} {:id "defn-/write-fighter-targets!", :kind "defn-", :line 71, :end-line 75, :hash "1429422742"} {:id "defn/refresh-army-targets!", :kind "defn", :line 77, :end-line 87, :hash "-403128441"} {:id "defn/record-army-target!", :kind "defn", :line 89, :end-line 97, :hash "-1734848242"} {:id "defn/choose-army-target", :kind "defn", :line 99, :end-line 104, :hash "-1766208919"} {:id "defn/choose-major-target", :kind "defn", :line 106, :end-line 110, :hash "62350449"} {:id "defn/fighter-support-targets", :kind "defn", :line 112, :end-line 117, :hash "958643692"}]}
;; clj-mutate-manifest-end
