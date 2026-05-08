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

(defn- after-sail-step
  [next-pos path-after-step moves-left]
  (let [transport (get-in (sa/read-state :computer-map) (conj next-pos :contents))]
    (if (or (zero? (dec moves-left))
            (zero? (:army-count transport 0)))
      {:done? true :pos next-pos}
      {:pos next-pos
       :remaining-path path-after-step
       :moves-left (dec moves-left)
       :moved-any? true})))

(defn- sail-step
  [current-pos next-pos path-after-step moves-left]
  (if (action-resolution/move-unit-to current-pos next-pos)
    (do
      (support/update-cell-visibility! current-pos :computer)
      (support/update-cell-visibility! next-pos :computer)
      (sync-sail-path! next-pos path-after-step)
      (after-sail-step next-pos path-after-step moves-left))
    {:done? true
     :pos (blocked-follow-result current-pos)}))

(defn sail-follow-path
  [pos sail-path]
  (loop [current-pos pos
         previous-pos nil
         remaining-path (vec sail-path)
         moves-left (transport-speed)
         moved-any? false]
    (if (zero? moves-left)
      nil
      (if-let [next-pos (next-sail-step previous-pos current-pos remaining-path)]
        (let [path-after-step (remaining-sail-path remaining-path)]
          (let [{:keys [done? pos remaining-path moves-left moved-any?]}
                (sail-step current-pos next-pos path-after-step moves-left)]
            (if done?
              pos
              (recur pos current-pos remaining-path moves-left moved-any?))))
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

(defn- path-to-load-target
  [pos computer-map load-target-cell]
  (when load-target-cell
    (load-targeting/path-to-load-target pos computer-map load-target-cell)))

(defn compute-and-follow-load-target-path!
  [pos transport]
  (let [computer-map (sa/read-state :computer-map)
        load-target-cell (:load-target-cell transport)
        sail-path (or (path-to-load-target pos computer-map load-target-cell)
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
       #(or (path-to-load-target % computer-map load-target-cell)
            (support/compute-sail-to-load-path %)))
      result)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-07T19:13:01.75963-05:00", :module-hash "-1772081618", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 9, :hash "364883412"} {:id "defn/blocked-follow-result", :kind "defn", :line 11, :end-line 14, :hash "1040965744"} {:id "defn/blocked-follow?", :kind "defn", :line 16, :end-line 18, :hash "562193117"} {:id "defn-/transport-speed", :kind "defn-", :line 20, :end-line 22, :hash "-603549653"} {:id "defn-/sync-sail-path!", :kind "defn-", :line 24, :end-line 27, :hash "319936993"} {:id "defn-/next-sail-step", :kind "defn-", :line 29, :end-line 31, :hash "1089332052"} {:id "defn-/remaining-sail-path", :kind "defn-", :line 33, :end-line 35, :hash "-2073188554"} {:id "defn-/after-sail-step", :kind "defn-", :line 37, :end-line 46, :hash "-1310691613"} {:id "defn-/sail-step", :kind "defn-", :line 48, :end-line 57, :hash "1524664540"} {:id "defn/sail-follow-path", :kind "defn", :line 59, :end-line 76, :hash "-1879045701"} {:id "defn-/sail-retreat", :kind "defn-", :line 78, :end-line 86, :hash "498662628"} {:id "defn/replan-sail-path!", :kind "defn", :line 88, :end-line 95, :hash "378039436"} {:id "defn/compute-and-follow-path!", :kind "defn", :line 97, :end-line 103, :hash "-502497740"} {:id "defn-/path-to-load-target", :kind "defn-", :line 105, :end-line 108, :hash "-1527213843"} {:id "defn/compute-and-follow-load-target-path!", :kind "defn", :line 110, :end-line 119, :hash "-855310906"} {:id "defn/follow-path-action", :kind "defn", :line 121, :end-line 137, :hash "-1061259274"} {:id "defn/follow-unload-sail-path", :kind "defn", :line 139, :end-line 153, :hash "995517447"} {:id "defn/follow-load-sail-path", :kind "defn", :line 155, :end-line 164, :hash "1287635781"}]}
;; clj-mutate-manifest-end
