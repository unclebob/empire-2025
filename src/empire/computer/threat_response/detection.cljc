(ns empire.computer.threat-response.detection
  "Detection handling helpers: fighter/ship/country-defense responses."
  (:require [empire.state.api :as sa]
            [empire.computer.shared.grid :as grid]
            [empire.computer.threat-response.country-defense :as country-defense]
            [empire.game-mechanics.services.threat-policy :as threat-policy]
            [empire.game-mechanics.visibility :as visibility]))

(defn find-computer-unit-positions [pred]
  (let [game-map (sa/read-state :computer-map)]
    (for [i (range (count game-map))
          j (range (count (first game-map)))
          :let [unit (get-in game-map [i j :contents])]
          :when (and unit
                     (= :computer (:owner unit))
                     (pred unit))]
      [i j])))

(defn- assign-threat-mission! [positions mission-kv]
  (doseq [pos positions]
    (when (:type (get-in (sa/current-world) (conj pos :contents)))
      (sa/update-world! update-in (conj pos :contents) merge mission-kv)
      (visibility/sync-ai-unit-to-computer-map! pos))))

(defn- closest-positions [origin positions n]
  (->> positions
       (sort-by #(grid/distance % origin))
       (take n)))

(defn handle-fighter-detection! [pos]
  (let [fighters (find-computer-unit-positions #(= :fighter (:type %)))
        selected (closest-positions pos fighters (threat-policy/fighter-response-count))]
    (assign-threat-mission!
     selected (threat-policy/fighter-sweep-mission pos))))

(defn handle-ship-detection! [pos]
  (let [patrols (find-computer-unit-positions #(= :patrol-boat (:type %)))
        battleships (find-computer-unit-positions #(= :battleship (:type %)))
        psel (closest-positions pos patrols (threat-policy/ship-response-count))
        bsel (closest-positions pos battleships (threat-policy/ship-response-count))
        selected (concat psel bsel)]
    (assign-threat-mission! selected (threat-policy/sea-scout-mission pos))))

(defn- homeland-defense-unit? [unit]
  (and unit
       (= :computer (:owner unit))
       (#{:army :fighter} (:type unit))
       (:country-id unit)))

(defn refresh-country-defense! []
  (let [targets-by-country (country-defense/player-armies-by-country (sa/read-state :computer-map))
        radius (threat-policy/threat-radius)
        game-map (sa/read-state :computer-map)]
    (doseq [i (range (count game-map))
            j (range (count (first game-map)))
            :let [unit (get-in game-map [i j :contents])]
            :when (homeland-defense-unit? unit)]
      (when (:type (get-in (sa/current-world) [i j :contents]))
        (let [cid (:country-id unit)
              targets (get targets-by-country cid)]
          (sa/update-world! update-in [i j :contents]
                            (if (seq targets)
                              #(country-defense/apply-country-defense % [i j] targets radius)
                              country-defense/clear-country-defense))
          (visibility/sync-ai-unit-to-computer-map! [i j]))))))

(defn handle-country-defense-detection! [_pos]
  (refresh-country-defense!))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-27T10:13:18.896464-05:00", :module-hash "57264155", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 7, :hash "1593046166"} {:id "defn/find-computer-unit-positions", :kind "defn", :line 9, :end-line 17, :hash "-1762426866"} {:id "defn-/assign-threat-mission!", :kind "defn-", :line 19, :end-line 22, :hash "1673520051"} {:id "defn-/closest-positions", :kind "defn-", :line 24, :end-line 27, :hash "-1666636244"} {:id "defn/handle-fighter-detection!", :kind "defn", :line 29, :end-line 33, :hash "115030172"} {:id "defn/handle-ship-detection!", :kind "defn", :line 35, :end-line 41, :hash "309076538"} {:id "defn-/homeland-defense-unit?", :kind "defn-", :line 43, :end-line 47, :hash "267565727"} {:id "defn/refresh-country-defense!", :kind "defn", :line 49, :end-line 63, :hash "1179352580"} {:id "defn/handle-country-defense-detection!", :kind "defn", :line 65, :end-line 66, :hash "2009644724"}]}
;; clj-mutate-manifest-end
