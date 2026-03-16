(ns empire.game.loop.item-processing-decisions)

(defn normalize-item-queue
  [items]
  (cond
    (and (sequential? items)
         (even? (count items))
         (every? number? items))
    (mapv vec (partition 2 items))

    (and (vector? items)
         (= 2 (count items))
         (every? number? items))
    [items]

    :else items))

(defn satellite-with-target?
  [unit]
  (and (= (:type unit) :satellite) (:target unit)))

(defn unit-auto-mode?
  [unit]
  (#{:moving :explore :coastline-follow} (:mode unit)))

(defn player-item-action
  [{:keys [sat-moving? auto-coords unit-in-auto-mode? needs-attention?]}]
  (cond
    sat-moving? :skip-satellite
    auto-coords :auto-move
    unit-in-auto-mode? :auto-move
    needs-attention? :attention
    :else :auto-move))

(defn combat-move-result-action
  [{:keys [fighter? moved-owner-matches? fighter-has-steps?]}]
  (cond
    (not moved-owner-matches?) :stop
    (and fighter? fighter-has-steps?) :fighter-continue
    :else :combat-stop))

(defn resolve-move-result-action
  [{:keys [result fighter? moved-owner-matches? fighter-has-steps?]}]
  (case result
    (:sidestep :normal) :advance-step
    :combat (combat-move-result-action
             {:fighter? fighter?
              :moved-owner-matches? moved-owner-matches?
              :fighter-has-steps? fighter-has-steps?})
    :woke :stay-put
    :docked :stop))

(defn batch-stop?
  [{:keys [paused? no-player-items? waiting-for-input? processed]}]
  (or paused?
      no-player-items?
      waiting-for-input?
      (>= processed 100)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T11:17:41.326121-05:00", :module-hash "-941238797", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "1022144592"} {:id "defn/normalize-item-queue", :kind "defn", :line 3, :end-line 16, :hash "-1299355275"} {:id "defn/satellite-with-target?", :kind "defn", :line 18, :end-line 20, :hash "1476000773"} {:id "defn/unit-auto-mode?", :kind "defn", :line 22, :end-line 24, :hash "-1209839883"} {:id "defn/player-item-action", :kind "defn", :line 26, :end-line 33, :hash "-2125543632"} {:id "defn/combat-move-result-action", :kind "defn", :line 35, :end-line 40, :hash "-661941237"} {:id "defn/resolve-move-result-action", :kind "defn", :line 42, :end-line 51, :hash "-1787381351"} {:id "defn/batch-stop?", :kind "defn", :line 53, :end-line 58, :hash "1962850966"}]}
;; clj-mutate-manifest-end
