;; mutation-tested: no
(ns empire.game.loop.round-setup.satellites
  (:require [empire.game.loop.round-setup.satellite-decisions :as decisions]))

(defn- find-satellite-coords
  [world]
  (vec (for [i (range (count world))
             j (range (count (first world)))
             :let [cell (get-in world [i j])
                   contents (:contents cell)]
             :when (= (:type contents) :satellite)]
         [i j])))

(defn- expire-satellite!
  [update-game-map! update-visibility! coords satellite]
  (update-game-map! update-in coords dissoc :contents)
  (update-visibility! coords (:owner satellite))
  nil)

(defn- finish-satellite-round
  [{:keys [update-game-map! update-visibility!]} coords satellite]
  (let [round-action (decisions/finish-round-action satellite)]
    (if (= :expire (:action round-action))
      (expire-satellite! update-game-map! update-visibility! coords satellite)
      (do (update-game-map! assoc-in (conj coords :contents :turns-remaining)
                            (:turns-remaining round-action))
          coords))))

(defn- move-satellite-steps
  [{:keys [current-world update-game-map! update-visibility! move-satellite satellite-speed] :as ctx}
   start-coords]
  (loop [coords start-coords
         steps-left satellite-speed]
    (let [cell (get-in (current-world) coords)
          satellite (:contents cell)
          action (decisions/satellite-step-action satellite steps-left)]
      (case (:action action)
        :missing nil
        :expire (expire-satellite! update-game-map! update-visibility! coords satellite)
        :finish-round (finish-satellite-round ctx coords satellite)
        (recur (move-satellite coords) (dec steps-left))))))

(defn move-satellites!
  [ctx]
  (doseq [coords (find-satellite-coords ((:current-world ctx)))]
    (move-satellite-steps ctx coords)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:08:55.161016-05:00", :module-hash "311431045", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line nil, :hash "-551791313"} {:id "defn-/find-satellite-coords", :kind "defn-", :line 5, :end-line nil, :hash "1125499085"} {:id "defn-/expire-satellite!", :kind "defn-", :line 14, :end-line nil, :hash "265782912"} {:id "defn-/finish-satellite-round", :kind "defn-", :line 20, :end-line nil, :hash "-77942652"} {:id "defn-/move-satellite-steps", :kind "defn-", :line 29, :end-line nil, :hash "-1642191042"} {:id "defn/move-satellites!", :kind "defn", :line 43, :end-line nil, :hash "1438118815"}]}
;; clj-mutate-manifest-end
