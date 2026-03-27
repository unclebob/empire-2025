(ns empire.game-mechanics.debug.dump.sections
  "Large section formatters for debug dump output."
  (:require [clojure.string :as str]
            [empire.state.api :as sa]))

(defn- find-coastline-units
  "Find all units in coastline-follow mode."
  []
  (let [game-map (sa/current-world)]
    (for [col (range (count game-map))
          row (range (count (first game-map)))
          :let [cell (get-in game-map [col row])
                unit (:contents cell)]
          :when (= (:mode unit) :coastline-follow)]
      {:pos [col row]
       :type (:type unit)
       :owner (:owner unit)
       :visited (count (:visited unit))
       :steps-remaining (:coastline-steps unit)})))

(defn format-coastline-section
  "Format coastline-follow units for debug dump."
  []
  (let [units (find-coastline-units)]
    (str "=== Coastline-Follow Units ===\n"
         (if (empty? units)
           "  (none)\n"
           (str (str/join "\n"
                          (for [{:keys [pos type owner visited steps-remaining]} units]
                            (str "  " pos " " (name type) " owner:" (name owner)
                                 " visited:" visited " steps:" steps-remaining)))
                "\n"))
         "\n")))

(defn- format-computer-event-entry
  "Format a single computer event log entry."
  [{:keys [event pos] :as entry}]
  (let [extras (dissoc entry :round :event :pos)
        extra-str (when (seq extras) (str " " (pr-str extras)))]
    (str "    " (name event) " " pos extra-str)))

(defn format-computer-event-section
  "Format computer unit event history for the last 50 rounds."
  []
  (let [current-round (sa/read-state :round-number)
        min-round (max 1 (- current-round 49))
        entries (sa/read-state :computer-event-log)
        recent (filter #(<= min-round (:round %) current-round) entries)
        by-round (group-by :round recent)
        rounds (sort (keys by-round))]
    (str "=== Computer Unit Events (last 50 rounds) ===\n"
         (if (empty? rounds)
           "  (no events logged)\n"
           (str/join "\n"
                     (for [r rounds]
                       (str "  Round " r ":\n"
                            (str/join "\n" (map format-computer-event-entry (get by-round r)))))))
         "\n\n")))

(defn format-transport-reservations-section
  []
  (let [reservations (or (sa/read-state :transport-load-reservations) {})]
    (str "=== Transport Load Reservations ===\n"
         (if (empty? reservations)
           "  (none)\n"
           (str/join "\n"
                     (for [[transport-id reservation] (sort-by key reservations)]
                       (str "  " transport-id " " (pr-str reservation)))))
         "\n\n")))

(defn format-movement-entry
  "Format a single movement log entry."
  [{:keys [unit-type from to mode event reason]}]
  (str "    " (name unit-type) " " from "\u2192" to
       " " (name mode)
       (when (not= event :move) (str " " (name event)))
       (when reason (str " (" (name reason) ")"))))

(defn format-movement-history-section
  "Format player unit movement history for the last 20 rounds."
  []
  (let [current-round (sa/read-state :round-number)
        min-round (max 1 (- current-round 19))
        entries (sa/read-state :player-movement-log)
        recent-entries (filter #(<= min-round (:round %) current-round) entries)
        by-round (group-by :round recent-entries)
        rounds-with-moves (sort (keys by-round))]
    (str "=== Player Unit Movement History (last 20 rounds) ===\n"
         (if (empty? rounds-with-moves)
           "  (no movements logged)\n"
           (str/join "\n"
                     (for [r rounds-with-moves]
                       (str "  Round " r ":\n"
                            (str/join "\n" (map format-movement-entry (get by-round r)))))))
         "\n\n")))

(defn- format-path
  [path]
  (if (seq path)
    (str/join " -> " (map pr-str path))
    "(none)"))

(defn- route-from-node
  [next-hop-fn start]
  (loop [node start
         seen #{}
         path [start]]
    (if-let [next-hop (and (not (seen node))
                           (next-hop-fn node))]
      (recur next-hop
             (conj seen node)
             (conj path next-hop))
      path)))

(defn format-major-invasion-section
  []
  (let [state (sa/read-state :major-invasion-state)
        city-next-hops (:kamikazee-city-next-hops state)
        carrier-next-hops (:kamikazee-carrier-next-hops state)
        next-hop-fn (fn [node]
                      (or (get city-next-hops node)
                          (get carrier-next-hops node)))
        army-targets (mapv :pos (:kamikazee-army-targets state))
        city-paths (->> (keys city-next-hops)
                        sort
                        (map (fn [city]
                               (str "  " city " -> " (format-path (rest (route-from-node next-hop-fn city))))))
                        vec)
        carrier-paths (->> (keys carrier-next-hops)
                           sort
                           (map (fn [carrier]
                                  (str "  " carrier " -> " (format-path (rest (route-from-node next-hop-fn carrier))))))
                           vec)]
    (str "=== Major Invasion State ===\n"
         "active?: " (:active? state) "\n"
         "decision: " (pr-str (:decision state)) "\n"
         "failure-reason: " (pr-str (:failure-reason state)) "\n"
         "started-round: " (pr-str (:started-round state)) "\n"
         "first-landing-round: " (pr-str (:first-landing-round state)) "\n"
         "next-review-round: " (pr-str (:next-review-round state)) "\n"
         "detection-points: " (pr-str (sort (:detection-points state))) "\n"
         "sea-reachable-detection-points: " (pr-str (sort (:sea-reachable-detection-points state))) "\n"
         "target-land-set: " (pr-str (sort (:target-land-set state))) "\n"
         "kamikazee-army-targets: " (pr-str army-targets) "\n"
         "kamikazee-root-city: " (pr-str (:kamikazee-root-city state)) "\n"
         "kamikazee-forward-carrier: " (pr-str (:kamikazee-forward-carrier state)) "\n"
         "kamikazee-bridge-carriers: " (pr-str (sort (:kamikazee-bridge-carriers state))) "\n"
         "kamikazee-terminal-sites: " (pr-str (sort (:kamikazee-terminal-sites state))) "\n"
         "city-paths:\n"
         (if (seq city-paths)
           (str (str/join "\n" city-paths) "\n")
           "  (none)\n")
         "carrier-paths:\n"
         (if (seq carrier-paths)
           (str (str/join "\n" carrier-paths) "\n")
           "  (none)\n")
         "\n")))

(defn region-kamikazee-fighters
  [cell-map]
  (->> cell-map
       (keep (fn [[coords cell]]
               (let [unit (:contents cell)]
                 (when (and (= :fighter (:type unit))
                            (= :computer (:owner unit))
                            (or (:kamikazee unit)
                                (:major-invasion unit)))
                   {:pos coords
                    :fuel (:fuel unit)
                    :stage (:kamikazee-stage unit)
                    :major-target (:major-invasion-target unit)
                    :targets (:kamikazee-targets unit)
                    :route (:kamikazee-route unit)
                    :terminal-site (:kamikazee-terminal-site unit)
                    :wait-site (:kamikazee-wait-site unit)
                    :resume-pos (:kamikazee-hunt-resume-pos unit)}))))
       (sort-by :pos)
       vec))

(defn format-kamikazee-fighter-section
  [cell-map]
  (let [fighters (region-kamikazee-fighters cell-map)]
    (str "=== Kamikazee Fighters In Region ===\n"
         (if (seq fighters)
           (str/join
            "\n"
            (for [{:keys [pos fuel stage major-target targets route terminal-site wait-site resume-pos]} fighters]
              (str "  " pos
                   " fuel:" fuel
                   " stage:" (pr-str stage)
                   " major-target:" (pr-str major-target) "\n"
                   "    targets: " (pr-str targets) "\n"
                   "    route: " (pr-str route) "\n"
                   "    terminal-site: " (pr-str terminal-site)
                   " wait-site: " (pr-str wait-site)
                   " resume-pos: " (pr-str resume-pos))))
           "  (none)")
         "\n\n")))

(defn format-kamikazee-airport-section
  [cell-map]
  (let [airports (->> cell-map
                      (keep (fn [[coords cell]]
                              (when (pos? (:kamikazee-fighter-count cell 0))
                                {:pos coords
                                 :fighters (:fighter-count cell 0)
                                 :awake (:awake-fighters cell 0)
                                 :kamikazees (:kamikazee-fighter-count cell 0)
                                 :awake-kamikazees (:awake-kamikazee-fighters cell 0)})))
                      (sort-by :pos)
                      vec)]
    (str "=== Kamikazee Airports In Region ===\n"
         (if (seq airports)
           (str/join
            "\n"
            (for [{:keys [pos fighters awake kamikazees awake-kamikazees]} airports]
              (str "  " pos
                   " fighters:" fighters
                   " awake:" awake
                   " kamikazees:" kamikazees
                   " awake-kamikazees:" awake-kamikazees)))
           "  (none)")
         "\n\n")))
