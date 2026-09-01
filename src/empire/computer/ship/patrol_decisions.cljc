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
;; {:version 1, :tested-at "2026-03-26T23:31:46.327302-05:00", :module-hash "1071155257", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 1, :hash "524879380"} {:id "defn/adjacent-player-transport", :kind "defn", :line 3, :end-line 7, :hash "816798324"} {:id "defn/adjacent-non-transport-enemy", :kind "defn", :line 9, :end-line 13, :hash "-269906879"} {:id "defn/patrol-action", :kind "defn", :line 15, :end-line 31, :hash "-427957353"}]}
;; clj-mutate-manifest-end
