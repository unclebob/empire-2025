;; mutation-tested: no
(ns empire.game.loop.round-setup.satellites)

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
          satellite (:contents cell)]
      (cond
        ;; No satellite here (already removed or error)
        (not satellite)
        nil

        ;; Satellite expired
        (<= (:turns-remaining satellite 0) 0)
        (do (update-game-map! update-in coords dissoc :contents)
            (update-visibility! coords (:owner satellite))
            nil)

        ;; No more steps this round - decrement turns-remaining once per round
        (zero? steps-left)
        (let [new-turns (dec (:turns-remaining satellite 1))]
          (if (<= new-turns 0)
            (do (update-game-map! update-in coords dissoc :contents)
                (update-visibility! coords (:owner satellite))
                nil)
            (do (update-game-map! assoc-in (conj coords :contents :turns-remaining) new-turns)
                coords)))

        ;; Move one step
        :else
        (let [new-coords (move-satellite coords)]
          (recur new-coords (dec steps-left)))))))

(defn move-satellites!
  [ctx]
  (doseq [coords (find-satellite-coords ((:current-world ctx)))]
    (move-satellite-steps ctx coords)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-12T12:00:18.564906-05:00", :module-hash "-1528467211", :forms [{:id "form/0/ns", :kind "ns", :line 2, :end-line 2, :hash "-390047102"} {:id "defn-/find-satellite-coords", :kind "defn-", :line 4, :end-line 11, :hash "1125499085"} {:id "defn-/move-satellite-steps", :kind "defn-", :line 13, :end-line 44, :hash "-851306630"} {:id "defn/move-satellites!", :kind "defn", :line 46, :end-line 49, :hash "1438118815"}]}
;; clj-mutate-manifest-end
