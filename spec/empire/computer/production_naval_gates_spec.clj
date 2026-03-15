(ns empire.computer.production-naval-gates-spec
  "Tests for VMS Empire style computer production."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.production :as production]
            [empire.computer.ship :as ship]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! update-test-computer-map! set-test-world! update-test-world!]]))

(defn- disable-opening!
  []
  (test-utils/set-test-state! :round-number nil))

(defn- rebuild! [] (production/rebuild-country-stats!))

(defn- add-sea-column
  "Adds a column of sea cells to make column 0 cells coastal."
  []
  (let [rows (count (first (test-utils/read-test-state :game-map)))
        sea-col (vec (repeat rows {:type :sea}))]
    (update-test-world! conj sea-col)
    (update-test-computer-map! conj sea-col)))

(defn- count-test-computer-cities
  "Counts computer cities in the test map."
  []
  (count (for [i (range (count (test-utils/read-test-state :game-map)))
               j (range (count (first (test-utils/read-test-state :game-map))))
               :let [cell (get-in (test-utils/read-test-state :game-map) [i j])]
               :when (and (= :city (:type cell))
                          (= :computer (:city-status cell)))]
           true)))

(defn- saturate-fighter-limit
  "Places enough fighters on empty sea cells to satisfy total fighters >= total cities."
  []
  (let [city-count (count-test-computer-cities)
        existing-fighters (count (for [i (range (count (test-utils/read-test-state :game-map)))
                                       j (range (count (first (test-utils/read-test-state :game-map))))
                                       :let [unit (get-in (test-utils/read-test-state :game-map) [i j :contents])]
                                       :when (and unit (= :fighter (:type unit))
                                                  (= :computer (:owner unit)))]
                                  true))
        needed (- city-count existing-fighters)
        empty-sea (for [i (range (count (test-utils/read-test-state :game-map)))
                        j (range (count (first (test-utils/read-test-state :game-map))))
                        :let [cell (get-in (test-utils/read-test-state :game-map) [i j])]
                        :when (and (= :sea (:type cell)) (nil? (:contents cell)))]
                    [i j])]
    (doseq [pos (take needed empty-sea)]
      (update-test-world! assoc-in (conj pos :contents)
             {:type :fighter :owner :computer :mode :awake :hits 1 :fuel 20}))))

(defn- satisfy-coastal-per-country
  "Stamp coastal city with country-id and add units to satisfy all per-country priorities.
   Places armies on coastal land cells and adds 4 patrol boats.
   Saturates fighter limit so global production decisions can be reached."
  [city-col]
  (update-test-world! assoc-in [0 city-col :country-id] 1)
  ;; 1 transport with escort
  (update-test-world! assoc-in [0 5 :contents]
         {:type :transport :owner :computer :country-id 1 :transport-id 1
          :escort-destroyer-id 1 :army-count 0 :hits 3})
  ;; 4 patrol boats (new cap)
  (doseq [j [7 9 11 13]]
    (update-test-world! assoc-in [0 j :contents]
           {:type :patrol-boat :owner :computer :country-id 1 :hits 1}))
  ;; Fill any coastal land cells with armies to satisfy coastal-fill guard
  (let [game-map (test-utils/read-test-state :game-map)
        col0 (first game-map)]
    (doseq [j (range (count col0))
            :let [cell (nth col0 j)]
            :when (and (= :land (:type cell))
                       (nil? (:contents cell))
                       (= 1 (:country-id cell)))]
      (update-test-world! assoc-in [0 j :contents]
             {:type :army :owner :computer :country-id 1 :hits 1 :mode :sentry})))
  ;; Saturate fighter limit so per-country production falls through to global
  (saturate-fighter-limit)
  (production/rebuild-country-stats!))

(describe "naval and air production gates"
  (before
    (reset-all-atoms!)
    (disable-opening!))

  (context "destroyer escort production"

    (it "produces destroyer when country has unadopted transport and global cap allows"
      ;; 2-row: armies fill coastal cells, unadopted transport, 4 patrol boats
      (set-test-world! (build-test-map ["~Xaat~pppp"
                                               "~~~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [4 0 :contents :country-id] 1)
      (update-test-world! assoc-in [4 0 :contents :transport-id] 1)
      (doseq [col [6 7 8 9]]
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (rebuild!)
      (should= :destroyer (production/decide-production [1 0])))

    (it "does not produce destroyer when global cap reached"
      ;; 2-row: same but 1 destroyer already → destroyers >= transports
      (set-test-world! (build-test-map ["~Xaat~ppppd"
                                               "~~~~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3]]
        (update-test-world! assoc-in [col 0 :country-id] 1)
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (update-test-world! assoc-in [4 0 :contents :country-id] 1)
      (update-test-world! assoc-in [4 0 :contents :transport-id] 1)
      (doseq [col [6 7 8 9]]
        (update-test-world! assoc-in [col 0 :contents :country-id] 1))
      (rebuild!)
      (should-not= :destroyer (production/decide-production [1 0]))))

  (context "carrier production gate"

    (it "produces carrier when >10 cities, <2 producing, valid position exists"
      ;; 12 cities: 6 at j=0,2,4,6,8,10 and 6 at j=50,52,54,56,58,60
      ;; Distance 0 to 50 = 50 > 32, creating a distant pair that needs carrier
      (let [cells (vec (for [j (range 80)]
                          (cond
                            (and (even? j) (<= j 10)) {:type :city :city-status :computer}
                            (<= j 10) {:type :land}
                            (and (even? j) (>= j 50) (<= j 60)) {:type :city :city-status :computer}
                            (and (>= j 50) (<= j 60)) {:type :land}
                            :else {:type :sea})))]
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (satisfy-coastal-per-country 10)
        (ship/update-distant-city-pairs!)
        (should= :carrier (production/decide-production [0 10]))))

    (it "does not produce carrier when <=10 cities"
      (let [cells (vec (for [j (range 50)]
                          (cond
                            (and (even? j) (<= j 18)) {:type :city :city-status :computer}
                            (<= j 18) {:type :land}
                            :else {:type :sea})))]
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (satisfy-coastal-per-country 18)
        (should-not= :carrier (production/decide-production [0 18]))))

    (it "does not produce carrier when 2 already producing"
      (let [cells (vec (for [j (range 60)]
                          (cond
                            (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                            (<= j 22) {:type :land}
                            :else {:type :sea})))]
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (satisfy-coastal-per-country 22)
        (test-utils/set-test-state! :production {[0 0] {:item :carrier :remaining-rounds 10}
                                  [0 2] {:item :carrier :remaining-rounds 10}})
        (should-not= :carrier (production/decide-production [0 22]))))

    (it "does not produce carrier when 8 already exist"
      (let [cells (vec (for [j (range 80)]
                          (cond
                            (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                            (<= j 22) {:type :land}
                            (<= 30 j 37) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                            :else {:type :sea})))]
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (satisfy-coastal-per-country 22)
        (should-not= :carrier (production/decide-production [0 22]))))

    (it "does not produce carrier when no valid position exists"
      (let [cells (vec (for [j (range 44)]
                          (cond
                            (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                            (<= j 22) {:type :land}
                            :else {:type :sea})))]
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (satisfy-coastal-per-country 22)
        (should-not= :carrier (production/decide-production [0 22])))))

  (context "battleship production gate"

    (it "produces battleship when battleships < carriers"
      (let [cells (vec (for [j (range 60)]
                          (cond
                            (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                            (<= j 22) {:type :land}
                            (= j 48) {:type :sea :contents {:type :carrier :owner :computer :hits 8
                                                             :carrier-id 1 :carrier-mode :holding
                                                             :group-battleship-id nil
                                                             :group-submarine-ids []}}
                            :else {:type :sea})))]
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (satisfy-coastal-per-country 22)
        (test-utils/set-test-state! :production {[0 0] {:item :carrier :remaining-rounds 10}
                                  [0 2] {:item :carrier :remaining-rounds 10}})
        (should= :battleship (production/decide-production [0 22]))))

    (it "does not produce battleship when battleships >= carriers"
      (let [cells (vec (for [j (range 60)]
                          (cond
                            (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                            (<= j 22) {:type :land}
                            (= j 30) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                            (= j 31) {:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                            :else {:type :sea})))]
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (satisfy-coastal-per-country 22)
        (test-utils/set-test-state! :production {[0 0] {:item :carrier :remaining-rounds 10}
                                  [0 2] {:item :carrier :remaining-rounds 10}})
        (should-not= :battleship (production/decide-production [0 22])))))

  (context "submarine production gate"

    (it "produces submarine when submarines < 2 * carriers"
      (let [cells (vec (for [j (range 60)]
                          (cond
                            (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                            (<= j 22) {:type :land}
                            (= j 30) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                            (= j 31) {:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                            :else {:type :sea})))]
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (satisfy-coastal-per-country 22)
        (test-utils/set-test-state! :production {[0 0] {:item :carrier :remaining-rounds 10}
                                  [0 2] {:item :carrier :remaining-rounds 10}})
        (should= :submarine (production/decide-production [0 22]))))

    (it "does not produce submarine when submarines >= 2 * carriers"
      (let [cells (vec (for [j (range 60)]
                          (cond
                            (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                            (<= j 22) {:type :land}
                            (= j 30) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                            (= j 31) {:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                            (= j 32) {:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                            (= j 33) {:type :sea :contents {:type :submarine :owner :computer :hits 2}}
                            :else {:type :sea})))]
        (set-test-world! [cells])
        (set-test-computer-map! [cells])
        (satisfy-coastal-per-country 22)
        (test-utils/set-test-state! :production {[0 0] {:item :carrier :remaining-rounds 10}
                                  [0 2] {:item :carrier :remaining-rounds 10}})
        (should-not= :submarine (production/decide-production [0 22])))))

)
