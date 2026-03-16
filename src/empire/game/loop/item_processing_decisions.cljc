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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T09:29:50.083001-05:00", :module-hash "1141114081", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "1022144592"} {:id "defn/normalize-item-queue", :kind "defn", :line 3, :end-line 16, :hash "-1299355275"} {:id "defn/satellite-with-target?", :kind "defn", :line 18, :end-line 20, :hash "1476000773"} {:id "defn/unit-auto-mode?", :kind "defn", :line 22, :end-line 24, :hash "-1209839883"} {:id "defn/player-item-action", :kind "defn", :line 26, :end-line 33, :hash "-2125543632"}]}
;; clj-mutate-manifest-end
