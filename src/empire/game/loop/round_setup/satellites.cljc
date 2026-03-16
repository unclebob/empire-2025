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

(defn- move-satellite-steps
  [{:keys [current-world update-game-map! update-visibility! move-satellite satellite-speed]}
   start-coords]
  (loop [coords start-coords
         steps-left satellite-speed]
    (let [cell (get-in (current-world) coords)
          satellite (:contents cell)
          action (decisions/satellite-step-action satellite steps-left)]
      (case (:action action)
        :missing
        nil

        :expire
        (do
          (update-game-map! update-in coords dissoc :contents)
          (update-visibility! coords (:owner satellite))
          nil)

        :finish-round
        (let [round-action (decisions/finish-round-action satellite)]
          (if (= :expire (:action round-action))
            (do (update-game-map! update-in coords dissoc :contents)
                (update-visibility! coords (:owner satellite))
                nil)
            (do (update-game-map! assoc-in (conj coords :contents :turns-remaining)
                                  (:turns-remaining round-action))
                coords)))

        :move
        (let [new-coords (move-satellite coords)]
          (recur new-coords (dec steps-left)))))))

(defn move-satellites!
  [ctx]
  (doseq [coords (find-satellite-coords ((:current-world ctx)))]
    (move-satellite-steps ctx coords)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-15T16:55:48.533136-05:00", :module-hash "1327348228", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 3, :hash "-551791313"} {:id "defn-/find-satellite-coords", :kind "defn-", :line 5, :end-line 12, :hash "1125499085"} {:id "defn-/move-satellite-steps", :kind "defn-", :line 14, :end-line 44, :hash "151377961"} {:id "defn/move-satellites!", :kind "defn", :line 46, :end-line 49, :hash "1438118815"}]}
;; clj-mutate-manifest-end
