(ns empire.computer.transport-targeting
  "Transport target selection — finding unload targets and pickup continents."
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.transport-targeting-decisions :as decisions]))


(defn adjacent-to-pickup-continent?
  "Returns true if any adjacent land cell shares a country-id with the cell
   at pickup-continent-pos. Cheap O(neighbors) alternative to flood-fill."
  [pos pickup-continent-pos]
  (let [game-map (sa/read-state :computer-map)
        pcp-country-id (:country-id (get-in game-map pickup-continent-pos))]
    (if pcp-country-id
      (some (fn [n]
              (let [cell (get-in game-map n)]
                (and cell
                     (#{:land :city} (:type cell))
                     (= pcp-country-id (:country-id cell)))))
            (core/get-neighbors pos))
      ;; No country-id at pcp — fall back to distance check
      (<= (core/distance pos pickup-continent-pos) 2))))

(defn- score-target-city
  "Score a target city for a transport. Lower = more attractive.
   Factors: distance, continent attackable cities, computer presence."
  [transport-pos target-city]
  (let [dist (core/distance transport-pos target-city)
        target-continent (land-objectives/flood-fill-continent target-city)
        scan (when target-continent (land-objectives/scan-continent target-continent))
        attackable (+ (:player-cities scan 0) (:free-cities scan 0))
        continent-factor (if (pos? attackable)
                           (/ 100.0 attackable)
                           100.0)
        presence-penalty (if (pos? (:computer-cities scan 0)) 10.0 1.0)]
    (* dist continent-factor presence-penalty)))

(defn find-unload-target
  "Find best enemy city to unload near, excluding pickup continent.
   Prioritizes player cities over free cities.
   Prefers unclaimed targets to spread transports."
  [pickup-continent transport-pos]
  (let [player-cities (core/find-visible-cities #{:player})
        free-cities (core/find-visible-cities #{:free})
        ;; Filter both to off-continent
        player-off (if pickup-continent
                     (remove #(contains? pickup-continent %) player-cities)
                     player-cities)
        free-off (if pickup-continent
                   (remove #(contains? pickup-continent %) free-cities)
                   free-cities)
        ;; Priority: player cities first
        priority-targets (if (seq player-off) player-off free-off)]
    (when-let [{:keys [best claimed]} (decisions/claimed-target-choice
                                       priority-targets
                                       (or (sa/read-state :claimed-transport-targets) #{})
                                       #(score-target-city transport-pos %))]
      (sa/write-state! :claimed-transport-targets claimed)
      best)))

(defn find-next-pickup-continent-pos
  "After unloading, find the nearest continent with enough computer armies,
   excluding the current unload continent. Returns an army position on
   that continent, or nil if none qualifies.
   min-armies defaults to 3 (require >3 armies)."
  ([transport-pos current-continent]
   (find-next-pickup-continent-pos transport-pos current-continent 3))
  ([transport-pos current-continent min-armies]
   (let [game-map (sa/read-state :computer-map)
         all-armies (for [i (range (count game-map))
                          j (range (count (first game-map)))
                          :let [cell (get-in game-map [i j])
                                unit (:contents cell)]
                          :when (and unit
                                     (= :computer (:owner unit))
                                     (= :army (:type unit))
                                     (or (nil? current-continent)
                                         (not (contains? current-continent [i j]))))]
                      [i j])]
     ;; Group armies by continent, avoiding redundant flood-fills
     (loop [remaining all-armies
            seen #{}
            continents []]
       (if (empty? remaining)
         (decisions/pickup-continent-choice transport-pos
                                            continents
                                            min-armies
                                            core/distance)
         (let [army-pos (first remaining)]
           (if (contains? seen army-pos)
             (recur (rest remaining) seen continents)
             (let [cont (land-objectives/flood-fill-continent army-pos)
                   cont-armies (filter #(contains? cont %) all-armies)]
               (recur (rest remaining)
                      (into seen cont)
                      (conj continents {:continent cont :armies cont-armies}))))))))))

(defn should-try-opportunistic-unload?
  [army-count mission]
  (and (pos? army-count)
       (#{:sailing :sail-to-unload :unloading} mission)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-16T14:20:27.122733-05:00", :module-hash "-1275047669", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 6, :hash "-1919004257"} {:id "defn/adjacent-to-pickup-continent?", :kind "defn", :line 9, :end-line 23, :hash "-655962961"} {:id "defn-/score-target-city", :kind "defn-", :line 25, :end-line 37, :hash "1702347925"} {:id "defn/find-unload-target", :kind "defn", :line 39, :end-line 60, :hash "1436906733"} {:id "defn/find-next-pickup-continent-pos", :kind "defn", :line 62, :end-line 97, :hash "-523599609"} {:id "defn/should-try-opportunistic-unload?", :kind "defn", :line 99, :end-line 102, :hash "2063591989"}]}
;; clj-mutate-manifest-end
