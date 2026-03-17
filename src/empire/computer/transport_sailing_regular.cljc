(ns empire.computer.transport-sailing-regular
  (:require [empire.computer.core :as core]
            [empire.computer.transport-core :as tc]
            [empire.computer.transport-sailing-decisions :as decisions]
            [empire.computer.transport-sailing-path :as sailing-path]
            [empire.computer.transport-sailing-support :as support]
            [empire.computer.transport-unloading :as unloading]
            [empire.state.api :as sa]))

(defn- launch-from-city-to-sea
  [pos transport]
  (let [world (sa/read-state :computer-map)
        cell-type (get-in world (conj pos :type))]
    (when (= :city cell-type)
      (let [target-ref (or (:invasion-target transport)
                           (:major-invasion-target transport)
                           (:pickup-continent-pos transport)
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
      retreat)))

(defn- sail-take-second-step
  [from-pos next-pos remaining]
  (let [step2 (or (first remaining)
                  (sailing-path/continue-pos (sa/read-state :computer-map) from-pos next-pos))
        remaining2 (if (seq remaining) (vec (rest remaining)) [])
        moved2 (when step2 (core/move-unit-to next-pos step2))]
    (if moved2
      (do (support/update-cell-visibility! next-pos :computer)
          (support/update-cell-visibility! step2 :computer)
          (sa/update-world! assoc-in
                            (conj step2 :contents :sail-path) remaining2)
          (unloading/try-opportunistic-unload step2)
          step2)
      (do (sa/update-world! assoc-in
                            (conj next-pos :contents :sail-path) remaining)
          (unloading/try-opportunistic-unload next-pos)
          next-pos))))

(defn- sail-follow-path
  [pos sail-path]
  (let [next-pos (first sail-path)
        remaining (vec (rest sail-path))]
    (if (core/move-unit-to pos next-pos)
      (do (support/update-cell-visibility! pos :computer)
          (support/update-cell-visibility! next-pos :computer)
          (sail-take-second-step pos next-pos remaining))
      (sail-retreat pos sail-path))))

(defn- compute-and-follow-sail-path!
  [pos]
  (when-let [new-path (seq (support/compute-sail-path pos))]
    (let [sail-path (vec new-path)]
      (sa/update-world! assoc-in (conj pos :contents :sail-path) sail-path)
      (sail-follow-path pos sail-path))))

(defn- maybe-unload-or-sail!
  [pos transport]
  (if (unloading/has-nearby-unloadable-land? pos transport 5)
    (support/set-unloading-and-try! pos)
    (or (compute-and-follow-sail-path! pos)
        ;; No path and no adjacent coast at all: switch to unloading crawl mode.
        (when-not (some (fn [n]
                          (let [cell (get-in (sa/read-state :computer-map) n)]
                            (and cell (#{:land :city} (:type cell)))))
                        (core/get-neighbors pos))
          (support/set-unloading-and-try! pos)))))

(defn- handle-loaded-transport-without-path!
  [pos transport]
  (if-let [sea-pos (launch-from-city-to-sea pos transport)]
    (let [transport' (get-in (sa/current-world) (conj sea-pos :contents))]
      (maybe-unload-or-sail! sea-pos transport'))
    (maybe-unload-or-sail! pos transport)))

(defn- loaded-no-path-action
  [pos transport]
  (let [computer-map (sa/read-state :computer-map)
        city-cell? (= :city (:type (get-in computer-map pos)))
        adjacent-land? (some (fn [n]
                               (let [cell (get-in computer-map n)]
                                 (and cell (#{:land :city} (:type cell)))))
                             (core/get-neighbors pos))]
    (case (:action (decisions/loaded-no-path-action {:city-cell? city-cell?
                                                     :adjacent-land? adjacent-land?}))
      :launch-or-sail (handle-loaded-transport-without-path! pos transport)
      :unload-or-sail (maybe-unload-or-sail! pos transport)
      (support/set-unloading-and-try! pos))))

(defn- follow-path-action
  [pos sail-path]
  (sail-follow-path pos sail-path))

(defn- empty-never-reload-action
  [pos]
  (when-let [new-path (seq (support/compute-sail-path pos))]
    (sa/update-world! assoc-in (conj pos :contents :sail-path) (vec new-path))
    (sail-follow-path pos (vec new-path))))

(defn- mission-handler
  [state pos transport sail-path]
  ({:empty-reload (fn [] (tc/set-transport-mission pos :loading))
    :empty-never-reload (fn [] (empty-never-reload-action pos))
    :loaded-no-path (fn [] (loaded-no-path-action pos transport))
    :follow-path (fn [] (follow-path-action pos sail-path))}
   state))

(defn process-sailing-mission
  [pos]
  (let [transport (get-in (sa/current-world) (conj pos :contents))
        sail-path (:sail-path transport)
        army-count (:army-count transport 0)
        never-reload? (:never-reload? transport)
        action (:action (decisions/sailing-action sail-path army-count never-reload?))]
    (when-let [handler (mission-handler action pos transport sail-path)]
      (handler))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T15:01:30.75969-05:00", :module-hash "-66091184", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "913815156"} {:id "defn-/launch-from-city-to-sea", :kind "defn-", :line 10, :end-line 31, :hash "334254023"} {:id "defn-/sail-retreat", :kind "defn-", :line 33, :end-line 42, :hash "-1450208503"} {:id "defn-/sail-take-second-step", :kind "defn-", :line 44, :end-line 60, :hash "239957856"} {:id "defn-/sail-follow-path", :kind "defn-", :line 62, :end-line 70, :hash "-14709246"} {:id "defn-/compute-and-follow-sail-path!", :kind "defn-", :line 72, :end-line 77, :hash "-1373004350"} {:id "defn-/maybe-unload-or-sail!", :kind "defn-", :line 79, :end-line 89, :hash "-297549670"} {:id "defn-/handle-loaded-transport-without-path!", :kind "defn-", :line 91, :end-line 96, :hash "689794971"} {:id "defn-/loaded-no-path-action", :kind "defn-", :line 98, :end-line 109, :hash "-1817014670"} {:id "defn-/follow-path-action", :kind "defn-", :line 111, :end-line 113, :hash "-482966761"} {:id "defn-/empty-never-reload-action", :kind "defn-", :line 115, :end-line 119, :hash "196121059"} {:id "defn-/mission-handler", :kind "defn-", :line 121, :end-line 127, :hash "1693608890"} {:id "defn/process-sailing-mission", :kind "defn", :line 129, :end-line 137, :hash "792323798"}]}
;; clj-mutate-manifest-end
