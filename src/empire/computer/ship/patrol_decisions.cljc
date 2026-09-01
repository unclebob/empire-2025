(ns empire.computer.ship.patrol-decisions)

(defn adjacent-player-transport
  [neighbors]
  (first (filter (fn [{:keys [owner type]}]
                   (and (= :player owner) (= :transport type)))
                 neighbors)))

(defn adjacent-non-transport-enemy
  [neighbors]
  (first (filter (fn [{:keys [owner type]}]
                   (and (= :player owner) (not= :transport type)))
                 neighbors)))

(defn- invasion-patrol-action
  [adjacent-enemy-ship]
  (if adjacent-enemy-ship
    {:action :attack :target adjacent-enemy-ship}
    {:action :patrol}))

(defn- regular-patrol-action
  [adjacent-transport adjacent-enemy]
  (cond
    adjacent-transport {:action :attack :target adjacent-transport}
    adjacent-enemy {:action :flee :target adjacent-enemy}
    :else {:action :patrol}))

(defn patrol-action
  [{:keys [major-invasion adjacent-enemy-ship adjacent-transport adjacent-enemy]}]
  (if major-invasion
    (invasion-patrol-action adjacent-enemy-ship)
    (regular-patrol-action adjacent-transport adjacent-enemy)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:11:13.58952-05:00", :module-hash "287944620", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "524879380"} {:id "defn/adjacent-player-transport", :kind "defn", :line 3, :end-line nil, :hash "816798324"} {:id "defn/adjacent-non-transport-enemy", :kind "defn", :line 9, :end-line nil, :hash "-269906879"} {:id "defn-/invasion-patrol-action", :kind "defn-", :line 15, :end-line nil, :hash "-331897468"} {:id "defn-/regular-patrol-action", :kind "defn-", :line 21, :end-line nil, :hash "2115763866"} {:id "defn/patrol-action", :kind "defn", :line 28, :end-line nil, :hash "203742618"}]}
;; clj-mutate-manifest-end
