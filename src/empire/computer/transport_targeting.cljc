;; mutation-tested: 2026-02-28
(ns empire.computer.transport-targeting
  "Transport target selection — finding unload targets and pickup continents."
  (:require [empire.adapters.state.runtime :as runtime-state]
            [empire.application.ports :as ports]
            [empire.application.runtime :as app-runtime]
            [empire.computer.core :as core]
            [empire.computer.land-objectives :as land-objectives]))

(def ^:private state-ctx
  (delay (app-runtime/default-state-ctx)))

(defn- current-world
  []
  ((:load-world @state-ctx)))

(defn- read-runtime-state
  [k]
  (let [store (runtime-state/runtime-state-store)]
    (ports/read-runtime-state store k)))

(defn- write-runtime-state!
  [k v]
  (let [store (runtime-state/runtime-state-store)]
    (ports/write-runtime-state! store k v)))

(defn adjacent-to-pickup-continent?
  "Returns true if any adjacent land cell shares a country-id with the cell
   at pickup-continent-pos. Cheap O(neighbors) alternative to flood-fill."
  [pos pickup-continent-pos]
  (let [game-map (current-world)
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
    (when (seq priority-targets)
      (let [claimed (or (read-runtime-state :claimed-transport-targets) #{})
            unclaimed (remove claimed priority-targets)
            candidates (if (seq unclaimed) unclaimed priority-targets)
            best (apply min-key
                        #(score-target-city transport-pos %)
                        candidates)]
        (when best
          (write-runtime-state! :claimed-transport-targets (conj claimed best))
          best)))))

(defn find-next-pickup-continent-pos
  "After unloading, find the nearest continent with enough computer armies,
   excluding the current unload continent. Returns an army position on
   that continent, or nil if none qualifies.
   min-armies defaults to 3 (require >3 armies)."
  ([transport-pos current-continent]
   (find-next-pickup-continent-pos transport-pos current-continent 3))
  ([transport-pos current-continent min-armies]
   (let [game-map (current-world)
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
         (let [qualifying (filter #(> (count (:armies %)) min-armies) continents)]
           (when (seq qualifying)
             (let [best (apply min-key
                               (fn [{:keys [armies]}]
                                 (apply min (map #(core/distance transport-pos %) armies)))
                               qualifying)]
               (apply min-key #(core/distance transport-pos %) (:armies best)))))
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
       (#{:sailing :unloading} mission)))
