(ns empire.computer.early-game.theater
  (:require [empire.computer.core :as core]
            [empire.computer.lake-naval :as lake-naval]
            [empire.computer.land-objectives :as land-objectives]
            [empire.state.api :as sa]))

(def ^:private invasion-missions
  #{:invading :load-for-invasion :find-armies-for-invasion})

(def ^:private transport-item->role
  {:army :CA
   :fighter :CF
   :transport :CT
   :patrol-boat :CP
   :satellite :CA})

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
  (let [computer-map (sa/read-state :computer-map)
        lakes (lake-cells)]
    (some (fn [neighbor]
            (let [cell (get-in computer-map neighbor)]
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
  (let [cell (get-in (sa/read-state :computer-map) pos)
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
  (let [world (sa/read-state :computer-map)]
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
        world (sa/read-state :computer-map)
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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-03-13T15:59:39.292965-05:00", :module-hash "-1990901196", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line 5, :hash "705477899"} {:id "def/invasion-missions", :kind "def", :line 7, :end-line 8, :hash "618070090"} {:id "def/transport-item->role", :kind "def", :line 10, :end-line 15, :hash "-910150492"} {:id "defn-/known-land-cell?", :kind "defn-", :line 17, :end-line 20, :hash "729866723"} {:id "defn-/lake-cells", :kind "defn-", :line 22, :end-line 25, :hash "-1686524640"} {:id "defn/city-usable-coastal?", :kind "defn", :line 27, :end-line 35, :hash "-1931668197"} {:id "defn-/theater-positions", :kind "defn-", :line 37, :end-line 42, :hash "54183605"} {:id "defn-/computer-city?", :kind "defn-", :line 44, :end-line 47, :hash "-1224059993"} {:id "defn-/city-info", :kind "defn-", :line 49, :end-line 62, :hash "1965251756"} {:id "defn-/army-on-landmass?", :kind "defn-", :line 64, :end-line 68, :hash "-233653345"} {:id "defn-/conquered-city-count", :kind "defn-", :line 70, :end-line 72, :hash "259874816"} {:id "defn-/phase", :kind "defn-", :line 74, :end-line 80, :hash "-2134968157"} {:id "defn-/active-invasion-transport?", :kind "defn-", :line 82, :end-line 91, :hash "1914935708"} {:id "defn/invasion-started?", :kind "defn", :line 93, :end-line 107, :hash "-1945983022"} {:id "defn/opening-active?", :kind "defn", :line 109, :end-line 112, :hash "1531037016"} {:id "defn/theater-summary", :kind "defn", :line 114, :end-line 135, :hash "1812566457"}]}
;; clj-mutate-manifest-end
