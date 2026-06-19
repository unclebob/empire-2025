(ns empire.player.movement-support
  (:require [empire.config.units.dispatcher :as dispatcher]
            [empire.game-mechanics.containers.helpers :as uc]))

(defn calculate-extended-target
  [world coords [dx dy]]
  (let [height (count world)
        width (count (first world))
        [x y] coords]
    (loop [tx x ty y]
      (let [nx (+ tx dx)
            ny (+ ty dy)]
        (if (and (>= nx 0) (< nx height) (>= ny 0) (< ny width))
          (recur nx ny)
          [tx ty])))))

(defn requeue-airport?
  [world coords]
  (let [cell (get-in world coords)]
    (and (= :city (:type cell))
         (uc/has-awake? cell :awake-fighters))))

(defn player-items-after-fighter-launch
  [world coords fighter-pos player-items]
  (let [remaining (rest player-items)]
    (if (requeue-airport? world coords)
      (cons fighter-pos (cons coords remaining))
      (cons fighter-pos remaining))))

(defn launch-fighter-and-update!
  [current-world write-state! update-state! launch-fn coords target]
  (let [fighter-pos (launch-fn coords target)]
    (write-state! :waiting-for-input false)
    (write-state! :attention-message "")
    (write-state! :cells-needing-attention [])
    (update-state! :player-items
                   #(player-items-after-fighter-launch
                     (current-world)
                     coords
                     fighter-pos
                     %))
    true))

(defn undamaged-ship-entering-friendly-city?
  [world active-unit adjacent-target]
  (let [target-cell (get-in world adjacent-target)
        unit-type (:type active-unit)
        max-hits (dispatcher/hits unit-type)]
    (and (dispatcher/naval-unit? unit-type)
         (= :city (:type target-cell))
         (= :player (:city-status target-cell))
         (= (:hits active-unit) max-hits))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-06-19T12:39:33.733188-05:00", :module-hash "852375327", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 3, :hash "-331281901"} {:id "defn/calculate-extended-target", :kind "defn", :line 5, :end-line 15, :hash "1191882870"} {:id "defn/requeue-airport?", :kind "defn", :line 17, :end-line 21, :hash "-738903045"} {:id "defn/player-items-after-fighter-launch", :kind "defn", :line 23, :end-line 28, :hash "-735657277"} {:id "defn/launch-fighter-and-update!", :kind "defn", :line 30, :end-line 42, :hash "543340511"} {:id "defn/undamaged-ship-entering-friendly-city?", :kind "defn", :line 44, :end-line 52, :hash "2063788119"}]}
;; clj-mutate-manifest-end
