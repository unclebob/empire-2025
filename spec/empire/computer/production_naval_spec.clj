(ns empire.computer.production-naval-spec
  "Tests for VMS Empire style computer production."
  (:require [speclj.core :refer :all]
            [empire.computer.production :as production]
            [empire.computer.ship :as ship]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(defn- add-sea-column
  "Adds a column of sea cells to make column 0 cells coastal."
  []
  (let [rows (count (first @atoms/game-map))
        sea-col (vec (repeat rows {:type :sea}))]
    (swap! atoms/game-map conj sea-col)
    (swap! atoms/computer-map conj sea-col)))

(defn- count-test-computer-cities
  "Counts computer cities in the test map."
  []
  (count (for [i (range (count @atoms/game-map))
               j (range (count (first @atoms/game-map)))
               :let [cell (get-in @atoms/game-map [i j])]
               :when (and (= :city (:type cell))
                          (= :computer (:city-status cell)))]
           true)))

(defn- saturate-fighter-limit
  "Places enough fighters on empty sea cells to satisfy total fighters >= total cities."
  []
  (let [city-count (count-test-computer-cities)
        existing-fighters (count (for [i (range (count @atoms/game-map))
                                       j (range (count (first @atoms/game-map)))
                                       :let [unit (get-in @atoms/game-map [i j :contents])]
                                       :when (and unit (= :fighter (:type unit))
                                                  (= :computer (:owner unit)))]
                                  true))
        needed (- city-count existing-fighters)
        empty-sea (for [i (range (count @atoms/game-map))
                        j (range (count (first @atoms/game-map)))
                        :let [cell (get-in @atoms/game-map [i j])]
                        :when (and (= :sea (:type cell)) (nil? (:contents cell)))]
                    [i j])]
    (doseq [pos (take needed empty-sea)]
      (swap! atoms/game-map assoc-in (conj pos :contents)
             {:type :fighter :owner :computer :mode :awake :hits 1 :fuel 20}))))

(defn- satisfy-coastal-per-country
  "Stamp coastal city with country-id and add units to satisfy all per-country priorities.
   Places armies on coastal land cells and adds 4 patrol boats.
   Saturates fighter limit so global production decisions can be reached."
  [city-col]
  (swap! atoms/game-map assoc-in [0 city-col :country-id] 1)
  ;; 1 transport with escort
  (swap! atoms/game-map assoc-in [0 5 :contents]
         {:type :transport :owner :computer :country-id 1 :transport-id 1
          :escort-destroyer-id 1 :army-count 0 :hits 3})
  ;; 4 patrol boats (new cap)
  (doseq [j [7 9 11 13]]
    (swap! atoms/game-map assoc-in [0 j :contents]
           {:type :patrol-boat :owner :computer :patrol-country-id 1 :hits 1}))
  ;; Fill any coastal land cells with armies to satisfy coastal-fill guard
  (let [game-map @atoms/game-map
        col0 (first game-map)]
    (doseq [j (range (count col0))
            :let [cell (nth col0 j)]
            :when (and (= :land (:type cell))
                       (nil? (:contents cell))
                       (= 1 (:country-id cell)))]
      (swap! atoms/game-map assoc-in [0 j :contents]
             {:type :army :owner :computer :country-id 1 :hits 1 :mode :sentry})))
  ;; Saturate fighter limit so per-country production falls through to global
  (saturate-fighter-limit))

;; ===== 5. naval and air production gates =====

(describe "naval and air production gates"
  (before (reset-all-atoms!))

  (context "destroyer escort production"

    (it "produces destroyer when country has unadopted transport and global cap allows"
      ;; 2-row: armies fill coastal cells, unadopted transport, 4 patrol boats
      (reset! atoms/game-map (build-test-map ["~Xaat~pppp"
                                               "~~~~~~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3]]
        (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
        (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
      (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
      (swap! atoms/game-map assoc-in [4 0 :contents :transport-id] 1)
      (doseq [col [6 7 8 9]]
        (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
      (should= :destroyer (production/decide-production [1 0])))

    (it "does not produce destroyer when global cap reached"
      ;; 2-row: same but 1 destroyer already → destroyers >= transports
      (reset! atoms/game-map (build-test-map ["~Xaat~ppppd"
                                               "~~~~~~~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3]]
        (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
        (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
      (swap! atoms/game-map assoc-in [4 0 :contents :country-id] 1)
      (swap! atoms/game-map assoc-in [4 0 :contents :transport-id] 1)
      (doseq [col [6 7 8 9]]
        (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
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
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (satisfy-coastal-per-country 10)
        (ship/update-distant-city-pairs!)
        (should= :carrier (production/decide-production [0 10]))))

    (it "does not produce carrier when <=10 cities"
      (let [cells (vec (for [j (range 50)]
                          (cond
                            (and (even? j) (<= j 18)) {:type :city :city-status :computer}
                            (<= j 18) {:type :land}
                            :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (satisfy-coastal-per-country 18)
        (should-not= :carrier (production/decide-production [0 18]))))

    (it "does not produce carrier when 2 already producing"
      (let [cells (vec (for [j (range 60)]
                          (cond
                            (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                            (<= j 22) {:type :land}
                            :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (satisfy-coastal-per-country 22)
        (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                  [0 2] {:item :carrier :remaining-rounds 10}})
        (should-not= :carrier (production/decide-production [0 22]))))

    (it "does not produce carrier when 8 already exist"
      (let [cells (vec (for [j (range 80)]
                          (cond
                            (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                            (<= j 22) {:type :land}
                            (<= 30 j 37) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                            :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (satisfy-coastal-per-country 22)
        (should-not= :carrier (production/decide-production [0 22]))))

    (it "does not produce carrier when no valid position exists"
      (let [cells (vec (for [j (range 44)]
                          (cond
                            (and (even? j) (<= j 22)) {:type :city :city-status :computer}
                            (<= j 22) {:type :land}
                            :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
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
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (satisfy-coastal-per-country 22)
        (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
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
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (satisfy-coastal-per-country 22)
        (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
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
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (satisfy-coastal-per-country 22)
        (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
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
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (satisfy-coastal-per-country 22)
        (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                  [0 2] {:item :carrier :remaining-rounds 10}})
        (should-not= :submarine (production/decide-production [0 22])))))

  (context "fighter global production limit"

    (it "produces fighter when total fighters < total computer cities"
      ;; 3 computer cities, 2 fighters — 2 < 3 so should produce fighter.
      ;; Coastal city at [1,0], other per-country priorities met.
      ;; Two extra computer cities at [30,0] and [31,0].
      (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaaaaaaaaaaaatd~ppppff"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
      (doseq [col (range 3 23)]
        (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
      (swap! atoms/game-map assoc-in [23 0 :contents :country-id] 1)
      (swap! atoms/game-map assoc-in [23 0 :contents :transport-id] 1)
      (swap! atoms/game-map assoc-in [23 0 :contents :escort-destroyer-id] 1)
      (doseq [col [26 27 28 29]]
        (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
      (swap! atoms/game-map assoc-in [30 0 :contents :country-id] 1)
      (swap! atoms/game-map assoc-in [31 0 :contents :country-id] 1)
      ;; Add 2 extra computer cities (total 3 cities, 2 fighters)
      (swap! atoms/game-map assoc-in [32 0] {:type :city :city-status :computer :country-id 2})
      (swap! atoms/game-map assoc-in [33 0] {:type :city :city-status :computer :country-id 3})
      (should= :fighter (production/decide-production [1 0])))

    (it "falls back to army when total fighters >= total computer cities"
      ;; 1 computer city, 1 fighter — 1 >= 1 so should NOT produce fighter.
      ;; Coastal city at [1,0], other per-country priorities met.
      (reset! atoms/game-map (build-test-map ["~X#aaaaaaaaaaaaaaaaaaaatd~ppppf"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
      (doseq [col (range 3 23)]
        (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
      (swap! atoms/game-map assoc-in [23 0 :contents :country-id] 1)
      (swap! atoms/game-map assoc-in [23 0 :contents :transport-id] 1)
      (swap! atoms/game-map assoc-in [23 0 :contents :escort-destroyer-id] 1)
      (doseq [col [26 27 28 29]]
        (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
      (swap! atoms/game-map assoc-in [30 0 :contents :country-id] 1)
      (should= :army (production/decide-production [1 0]))))

  (context "satellite production gate"

    (it "produces satellite when >15 cities and none alive"
      (let [city-row (vec (for [i (range 32)]
                            (if (even? i)
                              {:type :city :city-status :computer :country-id 1}
                              {:type :land :country-id 1})))
            game-map (vec [city-row])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (add-sea-column)
        (satisfy-coastal-per-country 0)
        (should= :satellite (production/decide-production [0 0]))))

    (it "does not produce satellite when one already alive"
      (let [city-row (vec (for [i (range 34)]
                            (cond
                              (even? i)
                              {:type :city :city-status :computer :country-id 1}
                              (#{1 3} i)
                              {:type :land :country-id 1
                               :contents {:type :fighter :owner :computer
                                          :country-id 1 :hits 1 :fuel 20}}
                              (= 5 i)
                              {:type :land :country-id 1
                               :contents {:type :transport :owner :computer
                                          :country-id 1 :transport-id 1
                                          :escort-destroyer-id 1 :army-count 17 :hits 3}}
                              (#{7 9 11 13} i)
                              {:type :land :country-id 1
                               :contents {:type :patrol-boat :owner :computer
                                          :patrol-country-id 1 :hits 1}}
                              :else
                              {:type :land :country-id 1
                               :contents {:type :army :owner :player :hits 1}})))]
        (reset! atoms/game-map [city-row (vec (repeat 34 {:type :sea}))])
        (reset! atoms/computer-map @atoms/game-map)
        (swap! atoms/game-map assoc-in [1 0 :contents]
               {:type :satellite :owner :computer :direction [1 0] :turns-remaining 50})
        (saturate-fighter-limit)
        ;; Satellite cap reached (1 alive >= max) → no satellite produced
        (should-not= :satellite (production/decide-production [0 0]))))

    (it "does not produce satellite when <=15 cities"
      (let [city-row (vec (for [i (range 30)]
                            (if (even? i)
                              {:type :city :city-status :computer :country-id 1}
                              {:type :land :country-id 1})))
            game-map (vec [city-row])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (add-sea-column)
        (satisfy-coastal-per-country 0)
        (should= :army (production/decide-production [0 0])))))

  (context "destroyer transport default (L297)"

    (it "does not produce destroyer when no transports exist"
      ;; L297: 0 -> 1 for transport default would make (< destroyers 1) true
      ;; when no transports exist, allowing destroyer production inappropriately
      (reset! atoms/game-map (build-test-map ["~Xaa~pppp"
                                               "~~~~~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 0 :country-id] 1)
      (doseq [col [2 3]]
        (swap! atoms/game-map assoc-in [col 0 :country-id] 1)
        (swap! atoms/game-map assoc-in [col 0 :contents :country-id] 1))
      (doseq [col [5 6 7 8]]
        (swap! atoms/game-map assoc-in [col 0 :contents :patrol-country-id] 1))
      ;; No transports exist → should not produce destroyer
      (should-not= :destroyer (production/decide-production [1 0]))))

  (context "count-carrier-producers (L312)"

    (it "counts cities producing carriers"
      ;; L312: = -> not= would count non-carrier producers instead
      (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                [0 2] {:item :army :remaining-rounds 5}})
      (should= 1 (#'production/count-carrier-producers)))

    (it "counts zero when no cities producing carriers"
      (reset! atoms/production {[0 0] {:item :army :remaining-rounds 5}})
      (should= 0 (#'production/count-carrier-producers))))

  (context "carrier threshold boundary (L322-L324)"

    (it "does not produce carrier when exactly at city threshold"
      ;; L322: > -> >= would produce carrier at exactly 10 cities
      ;; Need exactly 10 computer cities (= carrier-city-threshold), with distant pairs
      (let [cells (vec (for [j (range 80)]
                          (cond
                            (and (even? j) (<= j 18)) {:type :city :city-status :computer :country-id 1}
                            (<= j 18) {:type :land :country-id 1}
                            ;; Distant cities to create carrier pair
                            (and (even? j) (>= j 60) (<= j 60)) {:type :city :city-status :computer}
                            :else {:type :sea})))]
        ;; 10 cities at j=0,2,...,18 (no distant city adds to count beyond 10)
        ;; Actually distant city would make 11. Use 9 close + distant pair setup via mock.
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (satisfy-coastal-per-country 18)
        (ship/update-distant-city-pairs!)
        ;; Count is 10 + 1 distant = 11... need exact 10.
        ;; Use with-redefs to mock find-carrier-position and count
        (with-redefs [ship/find-carrier-position (fn [] {:position [0 25] :pair #{[0 0] [0 50]}})]
          ;; 10 cities at j=0,2,...,18 = exactly 10 = threshold
          ;; With > mutation to >=, (>= 10 10) would be true
          (let [cells2 (vec (for [j (range 40)]
                              (cond
                                (and (even? j) (<= j 18)) {:type :city :city-status :computer :country-id 1}
                                (<= j 18) {:type :land :country-id 1}
                                :else {:type :sea})))]
            (reset! atoms/game-map [cells2])
            (reset! atoms/computer-map [cells2])
            (satisfy-coastal-per-country 18)
            (should-not= :carrier (production/decide-production [0 18]))))))

    (it "does not produce carrier when at max live carriers"
      ;; L323: < -> <= would produce carrier when at max (8)
      (with-redefs [ship/find-carrier-position (fn [] {:position [0 40] :pair #{[0 0] [0 50]}})]
        (let [cells (vec (for [j (range 80)]
                           (cond
                             (and (even? j) (<= j 22)) {:type :city :city-status :computer :country-id 1}
                             (<= j 22) {:type :land :country-id 1}
                             (<= 30 j 37) {:type :sea :contents {:type :carrier :owner :computer :hits 8}}
                             :else {:type :sea})))]
          (reset! atoms/game-map [cells])
          (reset! atoms/computer-map [cells])
          (satisfy-coastal-per-country 22)
          ;; 12 cities > 10 threshold, 8 live carriers = max → should not produce
          (should-not= :carrier (production/decide-production [0 22])))))

    (it "does not produce carrier when at max producers"
      ;; L324: < -> <= would produce carrier with 2 already producing
      (with-redefs [ship/find-carrier-position (fn [] {:position [0 40] :pair #{[0 0] [0 50]}})]
        (let [cells (vec (for [j (range 80)]
                           (cond
                             (and (even? j) (<= j 22)) {:type :city :city-status :computer :country-id 1}
                             (<= j 22) {:type :land :country-id 1}
                             :else {:type :sea})))]
          (reset! atoms/game-map [cells])
          (reset! atoms/computer-map [cells])
          (satisfy-coastal-per-country 22)
          (reset! atoms/production {[0 0] {:item :carrier :remaining-rounds 10}
                                    [0 2] {:item :carrier :remaining-rounds 10}})
          ;; 12 cities, 0 live carriers, but 2 producing = max → should not produce another
          (should-not= :carrier (production/decide-production [0 22]))))))

  (context "submarine carrier-default (L335)"

    (it "does not produce submarine when no carriers exist"
      ;; L335: 0 -> 1 for carrier count default would make (* 2 1) = 2
      ;; allowing submarines when there are no carriers
      (let [cells (vec (for [j (range 60)]
                          (cond
                            (and (even? j) (<= j 22)) {:type :city :city-status :computer :country-id 1}
                            (<= j 22) {:type :land :country-id 1}
                            (= j 30) {:type :sea :contents {:type :battleship :owner :computer :hits 8}}
                            :else {:type :sea})))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [cells])
        (satisfy-coastal-per-country 22)
        ;; No carriers, 1 battleship → battleships >= carriers, skip BB
        ;; No carriers → submarines should not be produced (0 < 2*0 = 0 is false)
        (should-not= :submarine (production/decide-production [0 22]))))))

(run-specs)
