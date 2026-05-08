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

(defn- format-round-log-section
  [title state-key round-window empty-message format-entry]
  (let [current-round (sa/read-state :round-number)
        min-round (max 1 (- current-round round-window))
        entries (sa/read-state state-key)
        recent (filter #(<= min-round (:round %) current-round) entries)
        by-round (group-by :round recent)
        rounds (sort (keys by-round))]
    (str "=== " title " ===\n"
         (if (empty? rounds)
           empty-message
           (str/join "\n"
                     (for [r rounds]
                       (str "  Round " r ":\n"
                            (str/join "\n" (map format-entry (get by-round r)))))))
         "\n\n")))

(defn format-computer-event-section
  "Format computer unit event history for the last 50 rounds."
  []
  (format-round-log-section "Computer Unit Events (last 50 rounds)"
                            :computer-event-log
                            49
                            "  (no events logged)\n"
                            format-computer-event-entry))

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
  (format-round-log-section "Player Unit Movement History (last 20 rounds)"
                            :player-movement-log
                            19
                            "  (no movements logged)\n"
                            format-movement-entry))

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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-05-07T17:44:24.39135-05:00", :module-hash "130156188", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 4, :hash "-454027492"} {:id "defn-/find-coastline-units", :kind "defn-", :line 6, :end-line 19, :hash "-1471486597"} {:id "defn/format-coastline-section", :kind "defn", :line 21, :end-line 33, :hash "479659958"} {:id "defn-/format-computer-event-entry", :kind "defn-", :line 35, :end-line 40, :hash "-1993787720"} {:id "defn-/format-round-log-section", :kind "defn-", :line 42, :end-line 57, :hash "594700657"} {:id "defn/format-computer-event-section", :kind "defn", :line 59, :end-line 66, :hash "327206733"} {:id "defn/format-transport-reservations-section", :kind "defn", :line 68, :end-line 77, :hash "605234511"} {:id "defn/format-movement-entry", :kind "defn", :line 79, :end-line 85, :hash "938588367"} {:id "defn/format-movement-history-section", :kind "defn", :line 87, :end-line 94, :hash "-888211472"} {:id "defn-/format-path", :kind "defn-", :line 96, :end-line 100, :hash "-337379322"} {:id "defn-/route-from-node", :kind "defn-", :line 102, :end-line 112, :hash "916091143"} {:id "defn/format-major-invasion-section", :kind "defn", :line 114, :end-line 156, :hash "-1014134331"} {:id "defn/region-kamikazee-fighters", :kind "defn", :line 158, :end-line 177, :hash "1356265407"} {:id "defn/format-kamikazee-fighter-section", :kind "defn", :line 179, :end-line 197, :hash "-1205492453"} {:id "defn/format-kamikazee-airport-section", :kind "defn", :line 199, :end-line 222, :hash "-734503359"}]}
;; clj-mutate-manifest-end
