(ns empire.player.movement-decisions)

(defn undamaged-ship-entry-action
  [extended? undamaged-ship-entering-friendly-city?]
  (when (and (not extended?) undamaged-ship-entering-friendly-city?)
    :reject-undamaged-ship))

(defn- immediate-city-combat
  [unit-type]
  (cond
    (= :army unit-type) :army-conquest
    (= :fighter unit-type) :fighter-overfly
    :else nil))

(defn hostile-combat-action
  [unit-type extended? immediate-hostile-city? coastal-army-attack?]
  (cond
    coastal-army-attack? :coastal-army-attack
    (and (not extended?) immediate-hostile-city?) (immediate-city-combat unit-type)
    :else nil))

(defn standard-movement-action
  [unit-type extended? immediate-hostile-city? coastal-army-attack? undamaged-ship-entering-friendly-city?]
  (or (hostile-combat-action unit-type extended? immediate-hostile-city? coastal-army-attack?)
      (undamaged-ship-entry-action extended? undamaged-ship-entering-friendly-city?)
      :normal-move))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:11:29.19404-05:00", :module-hash "-390908173", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "624164861"} {:id "defn/undamaged-ship-entry-action", :kind "defn", :line 3, :end-line nil, :hash "-807891016"} {:id "defn-/immediate-city-combat", :kind "defn-", :line 8, :end-line nil, :hash "1588799550"} {:id "defn/hostile-combat-action", :kind "defn", :line 15, :end-line nil, :hash "-813958184"} {:id "defn/standard-movement-action", :kind "defn", :line 22, :end-line nil, :hash "-494271070"}]}
;; clj-mutate-manifest-end
