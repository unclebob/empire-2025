(ns empire.game.loop.round-setup-decisions)

(defn dead-unit-effects
  "Returns the world positions and side effects implied by dead units."
  [world dead-unit? computer-carrier?]
  (for [i (range (count world))
        j (range (count (first world)))
        :let [cell (get-in world [i j])
              contents (:contents cell)]
        :when (dead-unit? contents)]
    {:pos [i j]
     :owner (:owner contents)
     :computer-carrier? (computer-carrier? contents)
     :cell-without-contents (dissoc cell :contents)}))

(defn step-reset-effects
  "Returns player units paired with their refreshed step counts."
  [world effective-speed]
  (for [i (range (count world))
        j (range (count (first world)))
        :let [unit (get-in world [i j :contents])]
        :when (and unit (= :player (:owner unit)))]
    {:pos [i j]
     :steps (or (effective-speed (:type unit) (:hits unit)) 1)}))

(defn move-satellites-plan
  [{:keys [current-world
           update-game-map!
           update-visibility!
           move-satellite
           satellite-speed]}]
  {:current-world current-world
   :update-game-map! update-game-map!
   :update-visibility! update-visibility!
   :move-satellite move-satellite
   :satellite-speed satellite-speed})

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T01:57:05.55121-05:00", :module-hash "1460240136", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "-374047007"} {:id "defn/dead-unit-effects", :kind "defn", :line 3, :end-line 14, :hash "-660057601"} {:id "defn/step-reset-effects", :kind "defn", :line 16, :end-line 24, :hash "-777105427"} {:id "defn/move-satellites-plan", :kind "defn", :line 26, :end-line 36, :hash "-780402441"}]}
;; clj-mutate-manifest-end
