(ns empire.computer.early-game.strategy
  (:require [empire.state.api :as sa]
            [empire.computer.core :as core]
            [empire.computer.land-objectives :as land-objectives]
            [empire.computer.lake-naval :as lake-naval]))

(def ^:private invasion-missions
  #{:invading :load-for-invasion :find-armies-for-invasion})

(def ^:private transport-item->role
  {:army :CA
   :fighter :CF
   :transport :CT
   :patrol-boat :CP
   :satellite :CA})

(def ^:private role->item
  {:CA :army
   :CF :fighter
   :CT :transport
   :CP :patrol-boat})

(defn- known-land-cell?
  [computer-map pos]
  (let [cell (get-in computer-map pos)]
    (contains? #{:land :city} (:type cell))))

(defn- lake-cells
  []
  (lake-naval/lake-cells (sa/read-state :computer-map)
                         (sa/read-state :lake-max-cells)))

(defn city-usable-coastal?
  [city-pos]
  (let [world (sa/current-world)
        lakes (lake-cells)]
    (some (fn [neighbor]
            (let [cell (get-in world neighbor)]
              (and (= :sea (:type cell))
                   (not (contains? lakes neighbor)))))
          (core/get-neighbors city-pos))))

(defn- theater-positions
  [start-pos]
  (let [computer-map (sa/read-state :computer-map)]
    (->> (or (land-objectives/flood-fill-continent start-pos) #{})
         (filter #(known-land-cell? computer-map %))
         set)))

(defn- computer-city?
  [cell]
  (and (= :city (:type cell))
       (= :computer (:city-status cell))))

(defn- city-info
  [pos]
  (let [cell (get-in (sa/current-world) pos)
        production (get (sa/read-state :production) pos)
        opening-role (:opening-role cell)
        production-role (transport-item->role (:item production))
        role (or opening-role production-role)]
    {:pos pos
     :cell cell
     :country-id (:country-id cell)
     :coastal? (city-usable-coastal? pos)
     :role role
     :production production
     :pinned? (map? production)}))

(defn- army-on-landmass?
  [positions pos unit]
  (and (= :computer (:owner unit))
       (= :army (:type unit))
       (contains? positions pos)))

(defn- conquered-city-count
  [owned-city-count]
  (max 0 (dec owned-city-count)))

(defn- phase
  [{:keys [army-count conquered-city-count]}]
  (if (or (>= (or (sa/read-state :round-number) 0) 30)
          (>= army-count 6)
          (>= conquered-city-count 2))
    :phase-2
    :phase-1))

(defn- active-invasion-transport?
  [unit]
  (let [mission (:transport-mission unit)]
    (and (= :transport (:type unit))
         (= :computer (:owner unit))
         (or (contains? invasion-missions mission)
             (and (= :unloading mission)
                  (or (:major-invasion unit)
                      (:invasion-target unit)
                      (:major-invasion-target unit)))))))

(defn invasion-started?
  []
  (let [world (sa/current-world)]
    (boolean
     (or (some (fn [col]
                 (some (fn [cell]
                         (active-invasion-transport? (:contents cell)))
                       col))
               world)
         (some (fn [col]
                 (some (fn [cell]
                         (= :move-to-coast-for-invasion
                            (get-in cell [:contents :mode])))
                       col))
               world)))))

(defn opening-active?
  []
  (and (number? (sa/read-state :round-number))
       (not (invasion-started?))))

(defn theater-summary
  [start-pos]
  (let [positions (theater-positions start-pos)
        world (sa/current-world)
        city-infos (->> positions
                        (filter #(computer-city? (get-in world %)))
                        (map city-info)
                        vec)
        coastal-count (count (filter :coastal? city-infos))
        inland-count (- (count city-infos) coastal-count)
        army-count (count (for [pos positions
                                :let [unit (get-in world (conj pos :contents))]
                                :when (army-on-landmass? positions pos unit)]
                            pos))
        summary {:theater-key (first (sort positions))
                 :positions positions
                 :cities city-infos
                 :coastal-count coastal-count
                 :landlocked-count inland-count
                 :army-count army-count
                 :conquered-city-count (conquered-city-count (count city-infos))}]
    (assoc summary :phase (phase summary))))

(defn- strong-army-backlog?
  [{:keys [army-count]}]
  (>= army-count 6))

(defn- all-army-roles
  [total]
  {:CA total :CF 0 :CT 0 :CP 0})

(defn- no-coast-role-counts
  [total landlocked-count strong?]
  (if (and (pos? landlocked-count) strong?)
    {:CA (dec total) :CF 1 :CT 0 :CP 0}
    (all-army-roles total)))

(defn- one-coast-role-counts
  [landlocked-count strong?]
  (get {[0 false] {:CA 1 :CF 0 :CT 0 :CP 0}
        [0 true] {:CA 0 :CF 0 :CT 1 :CP 0}
        [1 false] {:CA 1 :CF 1 :CT 0 :CP 0}
        [1 true] {:CA 0 :CF 1 :CT 1 :CP 0}}
       [landlocked-count strong?]
       {:CA (dec landlocked-count) :CF 1 :CT 1 :CP 0}))

(defn- two-coast-role-counts
  [landlocked-count strong?]
  (get {[0 false] {:CA 1 :CF 0 :CT 1 :CP 0}
        [0 true] {:CA 1 :CF 0 :CT 1 :CP 0}
        [1 false] {:CA 1 :CF 1 :CT 1 :CP 0}
        [1 true] {:CA 1 :CF 1 :CT 1 :CP 0}}
       [landlocked-count strong?]
       (if strong?
         {:CA (dec landlocked-count) :CF 1 :CT 1 :CP 1}
         {:CA landlocked-count :CF 1 :CT 1 :CP 0})))

(defn- many-coast-role-counts
  [coastal-count landlocked-count strong?]
  (get {0 {:CA 1
           :CF 0
           :CT (max 1 (- coastal-count (if strong? 2 1)))
           :CP (if strong? 1 0)}
        1 {:CA 1 :CF 1 :CT (max 1 (- coastal-count 2)) :CP 1}
        2 {:CA 1 :CF 1 :CT (dec coastal-count) :CP 1}}
       landlocked-count
       {:CA (dec landlocked-count) :CF 1 :CT (dec coastal-count) :CP 1}))

(defn desired-role-counts
  [{:keys [coastal-count landlocked-count phase] :as summary}]
  (let [total (+ coastal-count landlocked-count)
        strong? (strong-army-backlog? summary)]
    (cond
      (zero? coastal-count)
      (no-coast-role-counts total landlocked-count strong?)

      (= phase :phase-1)
      (all-army-roles total)

      (= coastal-count 1)
      (one-coast-role-counts landlocked-count strong?)

      (= coastal-count 2)
      (two-coast-role-counts landlocked-count strong?)

      :else
      (many-coast-role-counts coastal-count landlocked-count strong?))))

(defn- pinned-role
  [{:keys [role production coastal?]}]
  (when (and role (map? production))
    (cond
      (and (#{:CT :CP} role) (not coastal?)) nil
      :else role)))

(defn- assign-random-role
  [cities assignments remaining role eligible?]
  (loop [assignments assignments
         remaining remaining
         need (get remaining role 0)]
    (if (pos? need)
      (let [eligible (->> cities
                          (remove #(contains? assignments (:pos %)))
                          (filter eligible?)
                          vec)]
        (if (seq eligible)
          (let [chosen (rand-nth eligible)]
            (recur (assoc assignments (:pos chosen) role)
                   (update remaining role dec)
                   (dec need)))
          [assignments remaining]))
      [assignments remaining])))

(defn theater-role-plan
  [{:keys [cities] :as summary}]
  (let [desired (desired-role-counts summary)
        pinned (reduce (fn [m city]
                         (if-let [role (pinned-role city)]
                           (assoc m (:pos city) role)
                           m))
                       {}
                       cities)
        remaining (reduce-kv (fn [m _pos role] (update m role (fnil dec 0))) desired pinned)
        [assignments remaining] (assign-random-role cities pinned remaining :CF #(not (:coastal? %)))
        [assignments remaining] (assign-random-role cities assignments remaining :CP :coastal?)
        [assignments remaining] (assign-random-role cities assignments remaining :CT :coastal?)
        [assignments _remaining] (assign-random-role cities assignments remaining :CA (constantly true))]
    (reduce (fn [m city]
              (update m (:pos city) #(or % :CA)))
            assignments
            cities)))

(defn assigned-role
  [city-pos]
  (let [summary (theater-summary city-pos)]
    (get (theater-role-plan summary) city-pos :CA)))

(defn- opening-satellite-ready?
  [city-pos assigned-role]
  (and (> (or (sa/read-state :round-number) 0) 50)
       (= :CA assigned-role)
       (not (sa/read-state :opening-satellite-produced?))
       (not-any? (fn [[_ prod]] (= :satellite (:item prod)))
                 (sa/read-state :production))
       (opening-active?)
       (contains? (:positions (theater-summary city-pos)) city-pos)))

(defn opening-production
  [city-pos]
  (when (opening-active?)
    (let [role (assigned-role city-pos)]
      (if (opening-satellite-ready? city-pos role)
        :satellite
        (role->item role)))))

(defn should-reset-lake-production?
  [city-pos]
  (let [cell (get-in (sa/current-world) city-pos)
        production (get (sa/read-state :production) city-pos)
        role (:opening-role cell)]
    (and (opening-active?)
         (map? production)
         (#{:CT :CP} role)
         (not (city-usable-coastal? city-pos)))))

(defn opening-exploration-profile
  [city-pos]
  (let [{:keys [coastal-count]} (theater-summary city-pos)]
    (if (< coastal-count 3)
      {:coast-walk-limit 3
       :random-explore-chance 1/5}
      {:coast-walk-limit 1
       :random-explore-chance 1/2})))

(defn theater-loading-transports
  [start-pos]
  (let [positions (:positions (theater-summary start-pos))
        world (sa/current-world)]
    (for [pos positions
          :let [unit (get-in world (conj pos :contents))]
          :when (and (= :transport (:type unit))
                     (= :computer (:owner unit))
                     (= :loading (:transport-mission unit)))]
      pos)))

(defn- theater-transport-producers
  [start-pos]
  (let [positions (:positions (theater-summary start-pos))
        production (sa/read-state :production)]
    (for [pos positions
          :let [prod (get production pos)]
          :when (= :transport (:item prod))]
      [pos (:remaining-rounds prod)])))

(defn allow-coastal-staging?
  [pos]
  (let [{:keys [phase]} (theater-summary pos)]
    (cond
      (not (opening-active?)) true
      (= phase :phase-1) false
      (seq (theater-loading-transports pos)) true
      :else
      (boolean
       (some (fn [[producer-pos remaining]]
               (<= remaining (core/distance pos producer-pos)))
             (theater-transport-producers pos))))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-13T15:12:01.033169-05:00", :module-hash "-1688970214", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "150313450"} {:id "def/invasion-missions", :kind "def", :line 7, :end-line 8, :hash "618070090"} {:id "def/transport-item->role", :kind "def", :line 10, :end-line 15, :hash "-910150492"} {:id "def/role->item", :kind "def", :line 17, :end-line 21, :hash "-1970530170"} {:id "defn-/known-land-cell?", :kind "defn-", :line 23, :end-line 26, :hash "729866723"} {:id "defn-/lake-cells", :kind "defn-", :line 28, :end-line 31, :hash "-1686524640"} {:id "defn/city-usable-coastal?", :kind "defn", :line 33, :end-line 41, :hash "-1931668197"} {:id "defn-/theater-positions", :kind "defn-", :line 43, :end-line 48, :hash "54183605"} {:id "defn-/computer-city?", :kind "defn-", :line 50, :end-line 53, :hash "-1224059993"} {:id "defn-/city-info", :kind "defn-", :line 55, :end-line 68, :hash "1965251756"} {:id "defn-/army-on-landmass?", :kind "defn-", :line 70, :end-line 74, :hash "-233653345"} {:id "defn-/conquered-city-count", :kind "defn-", :line 76, :end-line 78, :hash "259874816"} {:id "defn-/phase", :kind "defn-", :line 80, :end-line 86, :hash "-2134968157"} {:id "defn-/active-invasion-transport?", :kind "defn-", :line 88, :end-line 97, :hash "1914935708"} {:id "defn/invasion-started?", :kind "defn", :line 99, :end-line 113, :hash "-1945983022"} {:id "defn/opening-active?", :kind "defn", :line 115, :end-line 118, :hash "1531037016"} {:id "defn/theater-summary", :kind "defn", :line 120, :end-line 141, :hash "1812566457"} {:id "defn-/strong-army-backlog?", :kind "defn-", :line 143, :end-line 145, :hash "1046624873"} {:id "defn-/all-army-roles", :kind "defn-", :line 147, :end-line 149, :hash "-867129911"} {:id "defn-/no-coast-role-counts", :kind "defn-", :line 151, :end-line 155, :hash "-1009595583"} {:id "defn-/one-coast-role-counts", :kind "defn-", :line 157, :end-line 164, :hash "1208449788"} {:id "defn-/two-coast-role-counts", :kind "defn-", :line 166, :end-line 175, :hash "-291816814"} {:id "defn-/many-coast-role-counts", :kind "defn-", :line 177, :end-line 186, :hash "-778089955"} {:id "defn/desired-role-counts", :kind "defn", :line 188, :end-line 206, :hash "-17708602"} {:id "defn-/pinned-role", :kind "defn-", :line 208, :end-line 213, :hash "1262813261"} {:id "defn-/assign-random-role", :kind "defn-", :line 215, :end-line 231, :hash "-1560253281"} {:id "defn/theater-role-plan", :kind "defn", :line 233, :end-line 250, :hash "1242992076"} {:id "defn/assigned-role", :kind "defn", :line 252, :end-line 255, :hash "-725581761"} {:id "defn-/opening-satellite-ready?", :kind "defn-", :line 257, :end-line 265, :hash "-1475741007"} {:id "defn/opening-production", :kind "defn", :line 267, :end-line 273, :hash "1769800451"} {:id "defn/should-reset-lake-production?", :kind "defn", :line 275, :end-line 283, :hash "1416073797"} {:id "defn/opening-exploration-profile", :kind "defn", :line 285, :end-line 292, :hash "-385741527"} {:id "defn/theater-loading-transports", :kind "defn", :line 294, :end-line 303, :hash "-889714929"} {:id "defn-/theater-transport-producers", :kind "defn-", :line 305, :end-line 312, :hash "903840240"} {:id "defn/allow-coastal-staging?", :kind "defn", :line 314, :end-line 325, :hash "-252872511"}]}
;; clj-mutate-manifest-end
