(ns empire.computer.transport-sailing-regular
  (:require [empire.computer.core :as core]
            [empire.computer.transport-core :as tc]
            [empire.computer.transport-sailing-path :as sailing-path]
            [empire.computer.transport-sailing-support :as support]
            [empire.computer.transport-unloading :as unloading]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]))

(defn- launch-from-city-to-sea
  [pos transport]
  (let [world (sa/read-state :computer-map)
        cell-type (get-in world (conj pos :type))]
    (when (= :city cell-type)
      (let [target-ref (or (:invasion-target transport)
                           (:major-invasion-target transport)
                           pos)
            options (->> (core/get-neighbors pos)
                         (filter (fn [n]
                                   (let [c (get-in world n)]
                                     (and c
                                          (= :sea (:type c))
                                          (nil? (:contents c))))))
                         (sort-by (fn [n]
                                    [(core/chebyshev-distance n target-ref) n])))]
        (when-let [sea-pos (first options)]
          (when (core/move-unit-to pos sea-pos)
            (support/update-cell-visibility! pos :computer)
            (support/update-cell-visibility! sea-pos :computer)
            (visibility/sync-ai-unit-to-computer-map! sea-pos)
            sea-pos))))))

(defn- sail-retreat
  [pos sail-path]
  (let [retreat (first (tc/get-passable-sea-neighbors pos))]
    (when (core/move-unit-to pos retreat)
      (support/update-cell-visibility! pos :computer)
      (support/update-cell-visibility! retreat :computer)
      (sa/update-world! assoc-in
                        (conj retreat :contents :sail-path)
                        (vec (cons pos sail-path)))
      (visibility/sync-ai-unit-to-computer-map! retreat)
      retreat)))

(defn- transport-speed
  []
  (dispatcher/speed :transport))

(defn- sync-sail-path!
  [pos sail-path]
  (sa/update-world! assoc-in
                    (conj pos :contents :sail-path)
                    (vec sail-path))
  (visibility/sync-ai-unit-to-computer-map! pos))

(defn- next-sail-step
  [previous-pos current-pos sail-path]
  (or (first sail-path)
      (when previous-pos
        (sailing-path/continue-pos (sa/read-state :computer-map) previous-pos current-pos))))

(defn- remaining-sail-path
  [sail-path]
  (if (seq sail-path) (vec (rest sail-path)) []))

(defn- sail-follow-path
  [pos sail-path maybe-unload?]
  (loop [current-pos pos
         previous-pos nil
         remaining-path (vec sail-path)
         moves-left (transport-speed)
         moved-any? false]
    (if (zero? moves-left)
      (when moved-any? current-pos)
      (if-let [next-pos (next-sail-step previous-pos current-pos remaining-path)]
        (let [path-after-step (remaining-sail-path remaining-path)]
          (if (core/move-unit-to current-pos next-pos)
            (do
              (support/update-cell-visibility! current-pos :computer)
              (support/update-cell-visibility! next-pos :computer)
              (sync-sail-path! next-pos path-after-step)
              (let [unloaded? (and maybe-unload?
                                   (unloading/try-opportunistic-unload next-pos))
                    transport (get-in (sa/read-state :computer-map) (conj next-pos :contents))]
                (if (or (zero? (dec moves-left))
                        unloaded?
                        (zero? (:army-count transport 0)))
                  next-pos
                  (recur next-pos current-pos path-after-step (dec moves-left) true))))
            (if moved-any?
              (do
                (sync-sail-path! current-pos remaining-path)
                current-pos)
              (sail-retreat pos sail-path))))
        (when moved-any?
          current-pos)))))

(defn- compute-and-follow-path!
  [pos path-fn maybe-unload?]
  (when-let [new-path (seq (path-fn pos))]
    (let [sail-path (vec new-path)]
      (sa/update-world! assoc-in (conj pos :contents :sail-path) sail-path)
      (visibility/sync-ai-unit-to-computer-map! pos)
      (sail-follow-path pos sail-path maybe-unload?))))

(defn- handle-launch-and-follow!
  [pos transport path-fn maybe-unload?]
  (if-let [sea-pos (launch-from-city-to-sea pos transport)]
    (compute-and-follow-path! sea-pos path-fn maybe-unload?)
    (compute-and-follow-path! pos path-fn maybe-unload?)))

(defn- claimed-land?
  [cell]
  (and cell
       (or (and (= :land (:type cell))
                (some? (:country-id cell)))
           (and (= :city (:type cell))
                (= :computer (:city-status cell))))))

(defn- adjacent-claimed-land?
  [pos]
  (some (fn [n]
          (claimed-land? (get-in (sa/read-state :computer-map) n)))
        (core/get-neighbors pos)))

(defn- transition-to-loading!
  [pos]
  (tc/set-transport-mission pos :loading)
  (sa/update-world! assoc-in (conj pos :contents :sail-path) [])
  (visibility/sync-ai-unit-to-computer-map! pos))

(defn- process-sail-to-unload-mission
  [pos transport]
  (if (unloading/try-opportunistic-unload pos)
    (tc/set-transport-mission pos :unloading)
    (let [computer-map (sa/read-state :computer-map)
          city-cell? (= :city (:type (get-in computer-map pos)))
          sail-path (:sail-path transport)]
      (cond
        city-cell?
        (handle-launch-and-follow! pos transport support/compute-sail-to-unload-path true)

        (adjacent-claimed-land? pos)
        (compute-and-follow-path! pos support/compute-sail-to-unload-path true)

        (seq sail-path)
        (sail-follow-path pos sail-path true)

        :else
        (compute-and-follow-path! pos support/compute-sail-to-unload-path true)))))

(defn- process-sail-to-load-mission
  [pos transport]
  (let [computer-map (sa/read-state :computer-map)
        city-cell? (= :city (:type (get-in computer-map pos)))
        sail-path (:sail-path transport)]
    (cond
      (adjacent-claimed-land? pos)
      (transition-to-loading! pos)

      city-cell?
      (handle-launch-and-follow! pos transport support/compute-sail-to-load-path false)

      (seq sail-path)
      (sail-follow-path pos sail-path false)

      :else
      (compute-and-follow-path! pos support/compute-sail-to-load-path false))))

(defn- follow-path-action
  [pos sail-path]
  (sail-follow-path pos sail-path true))

(defn process-sailing-mission
  [pos]
  (let [transport (get-in (sa/read-state :computer-map) (conj pos :contents))
        army-count (:army-count transport 0)
        mission (:transport-mission transport)]
    (case mission
      :sail-to-load (process-sail-to-load-mission pos transport)
      :sail-to-unload (process-sail-to-unload-mission pos transport)
      :sailing (if (zero? army-count)
                 (process-sail-to-load-mission pos transport)
                 (process-sail-to-unload-mission pos transport))
      (when-let [sail-path (:sail-path transport)]
        (follow-path-action pos sail-path)))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T15:01:30.75969-05:00", :module-hash "-66091184", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "913815156"} {:id "defn-/launch-from-city-to-sea", :kind "defn-", :line 10, :end-line 31, :hash "334254023"} {:id "defn-/sail-retreat", :kind "defn-", :line 33, :end-line 42, :hash "-1450208503"} {:id "defn-/sail-take-second-step", :kind "defn-", :line 44, :end-line 60, :hash "239957856"} {:id "defn-/sail-follow-path", :kind "defn-", :line 62, :end-line 70, :hash "-14709246"} {:id "defn-/compute-and-follow-sail-path!", :kind "defn-", :line 72, :end-line 77, :hash "-1373004350"} {:id "defn-/maybe-unload-or-sail!", :kind "defn-", :line 79, :end-line 89, :hash "-297549670"} {:id "defn-/handle-loaded-transport-without-path!", :kind "defn-", :line 91, :end-line 96, :hash "689794971"} {:id "defn-/loaded-no-path-action", :kind "defn-", :line 98, :end-line 109, :hash "-1817014670"} {:id "defn-/follow-path-action", :kind "defn-", :line 111, :end-line 113, :hash "-482966761"} {:id "defn-/empty-never-reload-action", :kind "defn-", :line 115, :end-line 119, :hash "196121059"} {:id "defn-/mission-handler", :kind "defn-", :line 121, :end-line 127, :hash "1693608890"} {:id "defn/process-sailing-mission", :kind "defn", :line 129, :end-line 137, :hash "792323798"}]}
;; clj-mutate-manifest-end
