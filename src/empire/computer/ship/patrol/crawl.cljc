(ns empire.computer.ship.patrol.crawl
  "Patrol boat coastline crawling."
  (:require [empire.computer.shared.action-resolution :as action-resolution]
            [empire.computer.shared.world-query :as world-query]
            [empire.computer.ship.core :as ship-core]
            [empire.computer.ship.patrol.repulsion :as repulsion]
            [empire.game-mechanics.visibility :as visibility]
            [empire.state.api :as sa]))

(defn- update-cell-visibility! [pos owner]
  (visibility/update-cell-visibility pos owner))

(defn adjacent-to-land?
  "Returns true if the given position has at least one adjacent land or city cell."
  [pos]
  (let [game-map (sa/read-state :computer-map)]
    (some (fn [neighbor]
            (let [cell (get-in game-map neighbor)]
              (and cell (#{:land :city} (:type cell)))))
          (world-query/get-neighbors pos))))

(defn arrived-at-unseen-coast?
  "Returns true if pos is adjacent to land/city on computer-map and not in seen-coast."
  [pos]
  (and (not (contains? (or (sa/read-state :seen-coast) #{}) pos))
       (some (fn [neighbor]
               (let [cell (get-in (sa/read-state :computer-map) neighbor)]
                 (and cell (#{:land :city} (:type cell)))))
             (world-query/get-neighbors pos))))

(defn switch-to-crawling
  "Switch patrol boat to crawling mode and clear explore state."
  [next-pos]
  (sa/update-world! update-in (conj next-pos :contents)
                    #(-> % (assoc :patrol-mode :crawling)
                         (dissoc :explore-path)))
  (visibility/sync-ai-unit-to-computer-map! next-pos))

(defn patrol-crawl-step
  "Crawl along coastline. Records position in seen-coast.
   Prefers unseen coastal cells. Switches to :exploring when
   all coastal neighbors are seen or at map edge with none unseen.
  Returns new position or nil."
  [pos]
  (let [seen-coast (or (sa/read-state :seen-coast) #{})]
    (sa/write-state! :seen-coast (conj seen-coast pos)))
  (let [computer-map (sa/read-state :computer-map)
        passable (ship-core/get-passable-sea-neighbors pos)
        empty-passable (filter #(nil? (:contents (get-in computer-map %))) passable)
        coastal (filter adjacent-to-land? empty-passable)
        unseen (remove (or (sa/read-state :seen-coast) #{}) coastal)
        targets (if (seq unseen) unseen coastal)
        switch? (empty? unseen)]
    (when (seq targets)
      (let [target (repulsion/prefer-dispersed pos targets pos)]
        (action-resolution/move-unit-to pos target)
        (update-cell-visibility! pos :computer)
        (update-cell-visibility! target :computer)
        (when switch?
          (sa/update-world! assoc-in
                            (conj target :contents :patrol-mode) :exploring))
        target))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T10:48:16.602121-05:00", :module-hash "-534124531", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 8, :hash "-1135593111"} {:id "defn-/update-cell-visibility!", :kind "defn-", :line 10, :end-line 11, :hash "-1102586575"} {:id "defn/adjacent-to-land?", :kind "defn", :line 13, :end-line 20, :hash "1867226868"} {:id "defn/arrived-at-unseen-coast?", :kind "defn", :line 22, :end-line 29, :hash "-1473299527"} {:id "defn/switch-to-crawling", :kind "defn", :line 31, :end-line 37, :hash "1473162622"} {:id "defn/patrol-crawl-step", :kind "defn", :line 39, :end-line 62, :hash "-21306921"}]}
;; clj-mutate-manifest-end
