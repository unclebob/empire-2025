(ns empire.computer.transport.sailing-regular.follow
  (:require [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.transport.core :as tc]
            [empire.computer.transport.load-targeting :as load-targeting]
            [empire.computer.transport.sailing-support :as support]
            [empire.computer.transport.unloading :as unloading]
            [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]))

(defn blocked-follow-result
  [pos]
  {:blocked? true
   :pos pos})

(defn blocked-follow?
  [result]
  (true? (:blocked? result)))

(defn- transport-speed
  []
  (dispatcher/speed :transport))

(defn- sync-sail-path!
  [pos sail-path]
  (when (tc/assoc-transport-field! pos :sail-path (vec sail-path))
    (visibility/sync-ai-unit-to-computer-map! pos)))

(defn- next-sail-step
  [_previous-pos _current-pos sail-path]
  (first sail-path))

(defn- remaining-sail-path
  [sail-path]
  (if (seq sail-path) (vec (rest sail-path)) []))

(defn sail-follow-path
  [pos sail-path]
  (loop [current-pos pos
         previous-pos nil
         remaining-path (vec sail-path)
         moves-left (transport-speed)
         moved-any? false]
    (if (zero? moves-left)
      (when moved-any? current-pos)
      (if-let [next-pos (next-sail-step previous-pos current-pos remaining-path)]
        (let [path-after-step (remaining-sail-path remaining-path)]
          (if (action-resolution/move-unit-to current-pos next-pos)
            (do
              (support/update-cell-visibility! current-pos :computer)
              (support/update-cell-visibility! next-pos :computer)
              (sync-sail-path! next-pos path-after-step)
              (let [transport (get-in (sa/read-state :computer-map) (conj next-pos :contents))]
                (if (or (zero? (dec moves-left))
                        (zero? (:army-count transport 0)))
                  next-pos
                  (recur next-pos current-pos path-after-step (dec moves-left) true))))
            (blocked-follow-result current-pos)))
        (when moved-any?
          current-pos)))))

(defn- sail-retreat
  [pos sail-path]
  (let [retreat (first (tc/get-passable-sea-neighbors pos))]
    (when (action-resolution/move-unit-to pos retreat)
      (support/update-cell-visibility! pos :computer)
      (support/update-cell-visibility! retreat :computer)
      (tc/assoc-transport-field! retreat :sail-path (vec (cons pos sail-path)))
      (visibility/sync-ai-unit-to-computer-map! retreat)
      retreat)))

(defn replan-sail-path!
  [pos path-fn]
  (if-let [new-path (seq (path-fn pos))]
    (do
      (tc/assoc-transport-field! pos :sail-path (vec new-path))
      (visibility/sync-ai-unit-to-computer-map! pos)
      pos)
    pos))

(defn compute-and-follow-path!
  [pos path-fn]
  (when-let [new-path (seq (path-fn pos))]
    (let [sail-path (vec new-path)]
      (tc/assoc-transport-field! pos :sail-path sail-path)
      (visibility/sync-ai-unit-to-computer-map! pos)
      (sail-follow-path pos sail-path))))

(defn compute-and-follow-load-target-path!
  [pos transport]
  (let [computer-map (sa/read-state :computer-map)
        load-target-cell (:load-target-cell transport)
        sail-path (or (when load-target-cell
                        (load-targeting/path-to-load-target pos computer-map load-target-cell))
                      (support/compute-sail-to-load-path pos))]
    (when-let [new-path (seq sail-path)]
      (tc/assoc-transport-field! pos :sail-path (vec new-path))
      (visibility/sync-ai-unit-to-computer-map! pos)
      (sail-follow-path pos (vec new-path)))))

(defn follow-path-action
  [pos sail-path]
  (let [result (sail-follow-path pos sail-path)]
    (cond
      (blocked-follow? result)
      (sail-retreat pos sail-path)

      (and result
           (pos? (get-in (sa/read-state :computer-map) (conj result :contents :army-count) 0))
           (unloading/has-nearby-unloadable-land?
            result
            (get-in (sa/read-state :computer-map) (conj result :contents))
            0))
      (tc/set-transport-mission result :unloading)

      :else
      result)))

(defn follow-unload-sail-path
  [pos sail-path]
  (let [result (sail-follow-path pos sail-path)]
    (cond
      (blocked-follow? result)
      (replan-sail-path! pos support/compute-sail-to-unload-path)

      (and result
           (unloading/has-nearby-unloadable-land?
            result
            (get-in (sa/read-state :computer-map) (conj result :contents))
            0))
      (tc/set-transport-mission result :unloading)

      :else result)))

(defn follow-load-sail-path
  [pos sail-path load-target-cell]
  (let [computer-map (sa/read-state :computer-map)
        result (sail-follow-path pos sail-path)]
    (if (blocked-follow? result)
      (replan-sail-path!
       pos
       #(or (when load-target-cell
              (load-targeting/path-to-load-target % computer-map load-target-cell))
            (support/compute-sail-to-load-path %)))
      result)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T10:28:50.536561-05:00", :module-hash "1289387141", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 9, :hash "364883412"} {:id "defn/blocked-follow-result", :kind "defn", :line 11, :end-line 14, :hash "1040965744"} {:id "defn/blocked-follow?", :kind "defn", :line 16, :end-line 18, :hash "562193117"} {:id "defn-/transport-speed", :kind "defn-", :line 20, :end-line 22, :hash "-603549653"} {:id "defn-/sync-sail-path!", :kind "defn-", :line 24, :end-line 27, :hash "319936993"} {:id "defn-/next-sail-step", :kind "defn-", :line 29, :end-line 31, :hash "1089332052"} {:id "defn-/remaining-sail-path", :kind "defn-", :line 33, :end-line 35, :hash "-2073188554"} {:id "defn/sail-follow-path", :kind "defn", :line 37, :end-line 60, :hash "-1934290800"} {:id "defn-/sail-retreat", :kind "defn-", :line 62, :end-line 70, :hash "498662628"} {:id "defn/replan-sail-path!", :kind "defn", :line 72, :end-line 79, :hash "378039436"} {:id "defn/compute-and-follow-path!", :kind "defn", :line 81, :end-line 87, :hash "-502497740"} {:id "defn/compute-and-follow-load-target-path!", :kind "defn", :line 89, :end-line 99, :hash "-408154261"} {:id "defn/follow-path-action", :kind "defn", :line 101, :end-line 117, :hash "-1061259274"} {:id "defn/follow-unload-sail-path", :kind "defn", :line 119, :end-line 133, :hash "995517447"} {:id "defn/follow-load-sail-path", :kind "defn", :line 135, :end-line 145, :hash "-1276191905"}]}
;; clj-mutate-manifest-end
