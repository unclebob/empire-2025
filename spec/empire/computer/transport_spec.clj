(ns empire.computer.transport-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [speclj.core :refer :all]
            [empire.computer.transport :as transport]

            [empire.computer.land-objectives :as land-objectives]
            [empire.player.production :as player-prod]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "process-transport"
  (before (reset-all-atoms!))

  (context "coastal crawl loading"
    (it "crawls along coastline adjacent to land"
      ;; ###    land at row 0
      ;; t~~    transport at [0 1]
      (reset! atoms/game-map (build-test-map ["###"
                                               "t~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0
})
      (transport/process-transport [0 1])
      ;; Transport should have moved to another coastal sea cell
      (should-be-nil (:contents (get-in @atoms/game-map [0 1])))
      (let [t-pos (first (for [c (range 3) r (range 2)
                                :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                            [c r]))]
        (should-not-be-nil t-pos)))

    (it "loads army from adjacent land while crawling"
      ;; a##    army at [0 0]
      ;; t~~    transport at [0 1]
      (reset! atoms/game-map (build-test-map ["a##"
                                               "t~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0
})
      (transport/process-transport [0 1])
      ;; Army at [0 0] should be loaded
      (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
      ;; Find transport and check army-count
      (let [t-pos (first (for [c (range 3) r (range 2)
                                :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                            [c r]))
            transport (get-in @atoms/game-map (conj t-pos :contents))]
        (should= 1 (:army-count transport))))

    (it "stays put in open sea with no adjacent land"
      ;; Transport in open sea (no adjacent land) - no coastal targets
      (reset! atoms/game-map [[{:type :sea} {:type :sea}]
                               [{:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading
                                                        :army-count 0
                                                       }} {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [1 0])
      ;; Transport should stay put - surrounded by open sea
      (should= :transport (get-in @atoms/game-map [1 0 :contents :type])))

    (it "loads multiple armies from adjacent land"
      ;; aaa    3 armies at row 0
      ;; t~~    transport at [0 1]
      (reset! atoms/game-map (build-test-map ["aaa"
                                               "t~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0
})
      (transport/process-transport [0 1])
      ;; Find transport and check army-count
      (let [t-pos (first (for [c (range 3) r (range 2)
                                :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                            [c r]))
            transport (get-in @atoms/game-map (conj t-pos :contents))]
        ;; Should have loaded at least 1 army (the one adjacent at start)
        (should (pos? (:army-count transport)))))

    (it "loading transport with armies unloads all onto foreign empty land"
      ;; Transport at [1,1] in loading mode with 4 armies.
      ;; Adjacent land at [0,0]-[0,2] is foreign (no country-id matching transport).
      ;; Should opportunistically unload 3 armies (one per empty land cell).
      (reset! atoms/game-map (build-test-map ["###"
                                               "~t~"
                                               "~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 4
              :country-id 99})
      (transport/process-transport [1 1])
      ;; All 3 adjacent land cells should have armies
      (let [armies-on-land (for [c (range 3)
                                 :let [cell (get-in @atoms/game-map [c 0])]
                                 :when (= :army (:type (:contents cell)))]
                             [c 0])]
        (should= 3 (count armies-on-land)))
      ;; Transport should have 1 army remaining
      (let [t (first (for [c (range 3) r (range 3)
                           :let [unit (get-in @atoms/game-map [c r :contents])]
                           :when (= :transport (:type unit))]
                       unit))]
        (should= 1 (:army-count t)))))

  (context "unloading behavior"
    (it "unloads armies onto adjacent land"
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 2}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Should have unloaded an army onto land
      (should= :army (:type (:contents (get-in @atoms/game-map [0 0]))))
      ;; Transport should have fewer armies
      (should= 1 (:army-count (:contents (get-in @atoms/game-map [0 1])))))

    (it "changes to loading mode after full unload"
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Transport should be in loading mode now
      (should= :loading (:transport-mission (:contents (get-in @atoms/game-map [0 1]))))))

  (context "sail-path sailing transition"
    (it "full transport enters sailing with sail-path toward fog"
      ;; 5x5 all sea, transport at [2 1]. Rows 0-2 explored, rows 3-4 unexplored.
      ;; Transport should enter sailing mode with a sail-path.
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map
                (vec (for [c (range 5)]
                       (vec (for [r (range 5)]
                              (if (< r 3) {:type :sea} nil))))))
        (swap! atoms/game-map assoc-in [2 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (transport/process-transport [2 1])
        (let [t (:contents (get-in @atoms/game-map [2 1]))]
          (should= :sailing (:transport-mission t))
          (should-not-be-nil (:sail-path t))))))

  (context "mission transitions"
    (it "transport with no mission defaults to loading"
      (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :computer
                                                       :army-count 0}}
                                {:type :sea}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 0])
      ;; Find transport wherever it ended up
      (let [t-pos (first (for [c (range 3)
                               :when (= :transport (get-in @atoms/game-map [0 c :contents :type]))]
                           [0 c]))
            transport (get-in @atoms/game-map (conj t-pos :contents))]
        (should= :loading (:transport-mission transport)))))

  (context "ignores non-computer transports"
    (it "returns nil for player transport"
      (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :player
                                                       :army-count 0}}]])
      (should-be-nil (transport/process-transport [0 0])))

    (it "returns nil for empty cell"
      (reset! atoms/game-map [[{:type :sea}]])
      (should-be-nil (transport/process-transport [0 0]))))

  (context "origin continent tracking"
    (it "records pickup-continent-pos when transport becomes full"
      ;; Transport at [1,1] (sea) adjacent to land at [0,0..2]
      ;; No visible cities on computer-map so transport sails (not directed)
      (let [game-map (build-test-map ["###"
                                      "~t~"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        ;; Leave row 4 unexplored so transport has fog to explore toward
        (reset! atoms/computer-map
                (vec (for [c (range 3)]
                       (vec (for [r (range 5)]
                              (if (< r 4) (get-in game-map [c r]) nil))))))
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (transport/process-transport [1 1])
        ;; Transport may have moved; find it
        (let [t (some (fn [[c r]]
                        (let [contents (get-in @atoms/game-map [c r :contents])]
                          (when (= :transport (:type contents)) contents)))
                      (for [c (range 3) r (range 5)] [c r]))]
          (should-not-be-nil (:pickup-continent-pos t)))))

    (it "updates pickup-continent-pos after full unload to nearest qualifying continent"
      ;; Two continents: unload continent (rows 0-1) and army continent (rows 4-5).
      ;; Army continent has >3 computer armies.
      ;; After unloading, transport should update pickup-continent-pos to army continent.
      (let [game-map (build-test-map ["##~"
                                      "~t~"
                                      "~~~"
                                      "~~~"
                                      "aaa"
                                      "a##"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 1
                :pickup-continent-pos [2 5]})
        (transport/process-transport [1 1])
        (should= :army (:type (:contents (get-in @atoms/game-map [0 0]))))
        (let [transport (:contents (get-in @atoms/game-map [1 1]))]
          (should= :loading (:transport-mission transport))
          ;; pickup-continent-pos should be on the army continent (rows 4-5), not old value
          (should (>= (second (:pickup-continent-pos transport)) 4)))))

    (it "sets pickup-continent-pos to nil when no continent has >3 armies"
      ;; Only 2 armies exist on one continent - below threshold
      (let [game-map (build-test-map ["##~"
                                      "~t~"
                                      "~~~"
                                      "a#a"
                                      "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 1
                :pickup-continent-pos [1 4]})
        (transport/process-transport [1 1])
        (let [transport (:contents (get-in @atoms/game-map [1 1]))]
          (should= :loading (:transport-mission transport))
          (should-be-nil (:pickup-continent-pos transport)))))

    (it "excludes unload continent when finding next pickup"
      ;; Transport at [2,0] (sea), unload continent (rows 0-1), army continent (rows 4-5)
      ;; Unload continent also has armies, but it should be excluded.
      (let [game-map (build-test-map ["aaaa#"
                                      "a####"
                                      "t~~~~"
                                      "~~~~~"
                                      "aaaa#"
                                      "a####"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [0 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 1
                :pickup-continent-pos [4 5]})
        (transport/process-transport [0 2])
        (let [transport-pos (first (for [r (range 6) c (range 5)
                                        :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                                    [c r]))
              transport (get-in @atoms/game-map (conj transport-pos :contents))]
          (should= :loading (:transport-mission transport))
          ;; pickup-continent-pos should be on the OTHER army continent (rows 4-5),
          ;; not on the unload continent (rows 0-1)
          (should (>= (second (:pickup-continent-pos transport)) 4))))))

  (context "continent-aware unloading"
    (it "find-unload-target excludes origin continent cities"
      ;; Two continents separated by sea. Each has a player city.
      ;; Origin continent A (rows 0-1), continent B (rows 4-5)
      (let [game-map (build-test-map ["O##"
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "###"
                                      "O##"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target (transport/find-unload-target pickup-continent [1 3])]
          ;; Should return the city on continent B, not continent A
          (should-not-be-nil target)
          (should= [0 5] target))))

    (it "unload-armies skips origin continent land"
      ;; Transport at [1,1] in 1-wide sea channel between two continents
      ;; Left continent (col 0), right continent (col 2)
      (let [game-map (build-test-map ["#~#"
                                      "#t#"
                                      "#~#"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 2
                :pickup-continent-pos [0 0]})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])]
          (transport/unload-armies [1 1] pickup-continent)
          ;; Armies should NOT appear on origin continent (col 0)
          (let [origin-armies (count (for [r (range 3)
                                          :let [cell (get-in @atoms/game-map [0 r])]
                                          :when (= :army (:type (:contents cell)))]
                                      true))
                other-armies (count (for [r (range 3)
                                         :let [cell (get-in @atoms/game-map [2 r])]
                                         :when (= :army (:type (:contents cell)))]
                                     true))]
            (should= 0 origin-armies)
            (should (pos? other-armies)))))))

  (context "coastline exploration priority"
    (it "idle transport moves toward unexplored coastline over open sea"
      ;; Known land at row 4, unexplored cells near it. Also unexplored open sea at [0,2].
      ;; Transport should prefer coastline frontier (near row 3-4) over open sea [0,2].
      (reset! atoms/game-map (build-test-map ["~~~"
                                              "~~~"
                                              "~t~"
                                              "~~~"
                                              "###"]))
      (reset! atoms/computer-map [[nil {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                  [{:type :sea} {:type :sea} {:type :sea} {:type :sea} {:type :land}]
                                  [{:type :sea} {:type :sea} {:type :sea} nil nil]])
      (swap! atoms/game-map assoc-in [1 2 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0})
      (transport/process-transport [1 2])
      ;; Transport should have moved toward the coastline, not toward [0,0]
      (let [transport-pos (first (for [r (range 5) c (range 3)
                                       :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                                   [c r]))]
        (should-not-be-nil transport-pos)
        ;; Should move south toward coastline
        (should (>= (second transport-pos) 2))))

    (it "stays put in open sea when no coastline frontier"
      ;; No known land, only unexplored open sea — no coastal crawl targets
      (reset! atoms/game-map (build-test-map ["~~~"
                                              "~t~"
                                              "~~~"]))
      (reset! atoms/computer-map [[{:type :sea} {:type :sea} {:type :sea}]
                                  [{:type :sea} {:type :sea} {:type :sea}]
                                  [{:type :sea} {:type :sea} nil]])
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0})
      (transport/process-transport [1 1])
      ;; Empty loading transport with no coastal crawl targets stays put
      (should= :transport (get-in @atoms/game-map [1 1 :contents :type]))))

  (context "sailing behavior"
    ;; heading-based sailing tests removed — replaced by sail-path sailing


    (it "sailing transport with sail-path unloads at unexplored coast"
      ;; Transport at [2 2] with sail-path toward col 3.
      ;; Land at col 4 is unexplored. Transport moves to [3 2] and
      ;; opportunistically unloads an army.
      (let [game-map (build-test-map ["~~~~#"
                                      "~~~~#"
                                      "~~~~#"
                                      "~~~~#"
                                      "~~~~#"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map [(vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 nil))])
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :sailing :army-count 6
                :sail-path [[3 2]]})
        (transport/process-transport [2 2])
        ;; Transport should move to [3 2] and unload to 3 adjacent land cells
        (let [transport (:contents (get-in @atoms/game-map [3 2]))]
          (should= :transport (:type transport))
          (should= 3 (:army-count transport)))
        ;; 3 armies on adjacent land at [4,1],[4,2],[4,3]
        (let [armies-on-land (count (for [r (range 5)
                                          :let [cell (get-in @atoms/game-map [4 r])]
                                          :when (= :army (:type (:contents cell)))]
                                     true))]
          (should= 3 armies-on-land))))

    (it "transport does NOT unload on same continent"
      ;; Integration test: full transport near origin continent, only city on origin continent
      ;; Transport should NOT unload, should explore instead
      (let [game-map (build-test-map ["O##"
                                      "###"
                                      "~t~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :pickup-continent-pos [1 0]})
        (transport/process-transport [1 2])
        ;; No armies should be unloaded onto the origin continent
        (let [armies-on-land (count (for [r (range 2) c (range 3)
                                         :let [cell (get-in @atoms/game-map [c r])]
                                         :when (= :army (:type (:contents cell)))]
                                     true))]
          (should= 0 armies-on-land))))

    (it "transport does NOT unload on own-country land"
      ;; Transport adjacent to land that has country-id (claimed). Should not unload.
      (reset! atoms/game-map [[{:type :land :country-id 1}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 2
                                                        :sail-path [[0 2]]
                                                        :country-id 1}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; No army should appear on the claimed land
      (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))

    (it "sailing transport adjacent to empty land unloads opportunistically"
      ;; Transport sailing with armies, adjacent to empty land.
      ;; Opportunistic unload fires before sailing logic.
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 2
                                                        :sail-path [[0 2]]}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Army should be unloaded onto the empty land
      (should= :army (get-in @atoms/game-map [0 0 :contents :type]))
      ;; Transport should have fewer armies
      (should= 1 (get-in @atoms/game-map [0 1 :contents :army-count]))))

  (context "player city priority"
    (it "chooses player city over free city when both visible"
      ;; Origin continent (row 0), free city at row 3 (closer), player city at row 6
      ;; Transport should prefer player city even though free city is closer
      (let [game-map (build-test-map ["X##"
                                      "~~~"
                                      "~~~"
                                      "+##"
                                      "###"
                                      "~~~"
                                      "O##"
                                      "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (reset! atoms/claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target (transport/find-unload-target pickup-continent [1 1])]
          ;; Should pick player city [0 6], not free city [0 3] (even though farther)
          (should= [0 6] target))))

    (it "chooses free city when no player cities visible off-continent"
      ;; Origin continent has player city, only free city off-continent
      (let [game-map (build-test-map ["O##"
                                      "~~~"
                                      "~~~"
                                      "+##"
                                      "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (reset! atoms/claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target (transport/find-unload-target pickup-continent [1 1])]
          ;; Should pick free city [0 3] since no player cities off-continent
          (should= [0 3] target))))

    (it "respects pickup-continent exclusion for player cities"
      ;; Player cities on both origin and off-continent
      ;; Should only target the off-continent player city
      (let [game-map (build-test-map ["O##"
                                      "~~~"
                                      "~~~"
                                      "O##"
                                      "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (reset! atoms/claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target (transport/find-unload-target pickup-continent [1 1])]
          ;; Should pick [0 3], not [0 0] which is on pickup continent
          (should= [0 3] target)))))

  (context "transport target diversification"
    (it "two transports pick different cities"
      ;; Origin continent (rows 0-1), two target continents each with a player city
      ;; Continent B at row 4, Continent C at row 7
      (let [game-map (build-test-map ["X##"
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "O##"
                                      "###"
                                      "~~~"
                                      "O##"
                                      "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (reset! atoms/claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target1 (transport/find-unload-target pickup-continent [1 2])
              target2 (transport/find-unload-target pickup-continent [1 3])]
          (should-not-be-nil target1)
          (should-not-be-nil target2)
          (should-not= target1 target2))))

    (it "falls back when all targets claimed"
      ;; Only one off-continent city - both transports must target it
      (let [game-map (build-test-map ["X##"
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "O##"
                                      "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (reset! atoms/claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target1 (transport/find-unload-target pickup-continent [2 1])
              target2 (transport/find-unload-target pickup-continent [3 1])]
          (should-not-be-nil target1)
          (should-not-be-nil target2)
          (should= target1 target2))))

    (it "prefers continent without computer cities"
      ;; Two target continents: one with computer city, one without
      ;; Transport should prefer the one without computer presence
      (let [game-map (build-test-map ["###"
                                      "~~~"
                                      "~~~"
                                      "O#X"  ;; continent with computer city
                                      "###"
                                      "~~~"
                                      "O##"  ;; continent without computer city
                                      "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (reset! atoms/claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target (transport/find-unload-target pickup-continent [2 1])]
          ;; Should pick the city on continent without computer presence
          (should= [0 6] target))))

    (it "prefers nearer target when continents are similar"
      ;; Two equidistant-ish continents, transport closer to one
      (let [game-map (build-test-map ["###"
                                      "~~~"
                                      "O##"   ;; closer target
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "~~~"
                                      "O##"   ;; farther target
                                      "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (reset! atoms/claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              ;; Transport at row 1 is closer to city at row 2 than row 7
              target (transport/find-unload-target pickup-continent [1 1])]
          (should= [0 2] target)))))

  (context "pickup-country-id exclusion"
    (it "does not unload on pickup-country-id land"
      ;; Transport from city (country-id 1), loaded from country-id 5.
      ;; pcp cleared (nil). Adjacent land has country-id 5.
      ;; Should NOT unload because pickup-country-id 5 should be excluded.
      (reset! atoms/game-map [[{:type :land :country-id 5}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 6
                                                        :sail-path [[0 2]]
                                                        :country-id 1
                                                        :pickup-country-id 5}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (should-be-nil (:contents (get-in @atoms/game-map [0 0]))))

    (it "records pickup-country-id when entering sailing"
      ;; Transport at [1,1] adjacent to land with country-id 7.
      ;; When it enters sailing, pickup-country-id should be recorded.
      (let [game-map (build-test-map ["###"
                                      "~t~"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (doseq [c (range 3)]
          (swap! atoms/game-map assoc-in [c 0 :country-id] 7))
        (reset! atoms/computer-map
                (vec (for [c (range 3)]
                       (vec (for [r (range 5)]
                              (if (< r 4) (get-in game-map [c r]) nil))))))
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 3) r (range 5)
                             :let [unit (get-in @atoms/game-map [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :sailing (:transport-mission t))
          (should= 7 (:pickup-country-id t))))))

  (context "unload target persistence"
    (it "re-sails when stuck near pickup continent coast"
      ;; Transport in :unloading mode near pickup continent coast.
      ;; No unloadable land within reach — should re-sail toward foreign coast.
      (let [game-map (build-test-map ["X####"
                                      "#####"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "O####"
                                      "#####"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "O####"
                                      "#####"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [3 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :pickup-continent-pos [0 1]})
        (transport/process-transport [3 2])
        (let [transport-pos (first (for [c (range 5) r (range 12)
                                        :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                                    [c r]))
              transport (get-in @atoms/game-map (conj transport-pos :contents))]
          ;; Transport re-sails to find foreign coast
          (should= :sailing (:transport-mission transport)))))

    (it "re-sails when all nearby land is excluded by country-id"
      ;; Transport at [2,2] in :unloading with 6 armies.
      ;; Adjacent land (rows 0-1) has country-id 1 matching pcp.
      ;; No unloadable land nearby — should re-sail, not coast-crawl forever.
      (let [game-map (build-test-map ["#####"
                                      "#####"
                                      "~~t~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (doseq [c (range 5) r (range 2)]
          (swap! atoms/game-map assoc-in [c r :country-id] 1))
        (reset! atoms/computer-map
                (vec (for [c (range 5)]
                       (vec (for [r (range 5)]
                              (if (< r 3) (get-in @atoms/game-map [c r]) nil))))))
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :country-id 1
                :pickup-continent-pos [2 1]})
        (transport/process-transport [2 2])
        (let [t (first (for [c (range 5) r (range 5)
                             :let [unit (get-in @atoms/game-map [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :sailing (:transport-mission t)))))

    (it "unloading transport in open sea with no coast starts sailing"
      ;; Full transport in unloading mode in open sea with no adjacent land.
      ;; Coast-crawl fails, so it starts sailing via BFS.
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map
                (vec (for [c (range 5)]
                       (vec (for [r (range 3)]
                              (if (< r 2) {:type :sea} nil))))))
        (swap! atoms/game-map assoc-in [2 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :unload-target-city [0 5]})
        (transport/process-transport [2 1])
        (let [t (first (for [c (range 5) r (range 3)
                               :let [unit (get-in @atoms/game-map [c r :contents])]
                               :when (= :transport (:type unit))]
                           unit))]
          (should= :sailing (:transport-mission t)))))

    (it "unload-target-city cleared when transport transitions to loading"
      ;; Transport with 1 army unloads completely, transitioning to loading.
      ;; The stored unload-target-city should be cleared.
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1
                                                        :unload-target-city [0 0]}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (let [transport (:contents (get-in @atoms/game-map [0 1]))]
        (should= :loading (:transport-mission transport))
        (should-be-nil (:unload-target-city transport)))))


  (context "global BFS unload (VMS-consistent)"
    (it "stuck unloading transport with no adjacent land switches to sailing"
      ;; Full transport in :unloading mode with no adjacent unclaimed land.
      ;; Should switch to :sailing and try to move away.
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map
                (vec (for [c (range 5)]
                       (vec (for [r (range 5)]
                              (if (< r 3) {:type :sea} nil))))))
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :pickup-continent-pos [0 1]})
        (transport/process-transport [2 2])
        ;; Transport should switch to sailing and try to move
        (let [t (first (for [c (range 5) r (range 5)
                               :let [unit (get-in @atoms/game-map [c r :contents])]
                               :when (= :transport (:type unit))]
                           unit))]
          (should= :transport (:type t))
          (should= :sailing (:transport-mission t)))))

    (it "full transport explores regardless of nearby computer cities"
      ;; Full transport explores toward fog, ignoring computer cities
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map
                (vec (for [c (range 5)]
                       (vec (for [r (range 5)]
                              (if (< r 3) {:type :sea} nil))))))
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
})
        (transport/process-transport [2 2])
        (let [t (first (for [c (range 5) r (range 5)
                               :let [unit (get-in @atoms/game-map [c r :contents])]
                               :when (= :transport (:type unit))]
                           unit))]
          (should= :transport (:type t))
          (should= :sailing (:transport-mission t)))))

    (it "full transport explores even when no enemy cities visible"
      ;; Only computer cities. Full transport explores toward unexplored.
      (let [game-map (build-test-map ["X##"
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        ;; Row 4 unexplored
        (reset! atoms/computer-map
                (vec (for [c (range 3)]
                       (vec (for [r (range 5)]
                              (if (< r 4)
                                (get-in game-map [c r])
                                nil))))))
        (swap! atoms/game-map assoc-in [1 3 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
})
        (transport/process-transport [1 3])
        ;; Transport should have explored toward unexplored
        (let [t (first (for [c (range 3) r (range 5)
                               :let [unit (get-in @atoms/game-map [c r :contents])]
                               :when (= :transport (:type unit))]
                           unit))]
          (should= :transport (:type t))
          (should= :sailing (:transport-mission t))))))

  (context "no-reload from recently unloaded country"
    (it "transport avoids armies from recently unloaded country"
      ;; Transport adjacent to two armies: country-1 (recently unloaded) and country-2.
      ;; Should only load the country-2 army during auto-load.
      (reset! atoms/round-number 10)
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :country-id 1}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 0
                                                        :unloaded-countries {1 5}}}
                                {:type :land :contents {:type :army :owner :computer :country-id 2}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Country-1 army should still be on land (skipped by filter)
      (should= :army (get-in @atoms/game-map [0 0 :contents :type]))
      ;; Country-2 army should be loaded
      (should-be-nil (get-in @atoms/game-map [0 2 :contents]))
      ;; Transport should have 1 army loaded
      (let [t-pos (first (for [c (range 3)
                               :when (= :transport (get-in @atoms/game-map [0 c :contents :type]))]
                           [0 c]))
            transport (get-in @atoms/game-map (conj t-pos :contents))]
        (should= 1 (:army-count transport))))

    (it "exclusion expires after 10 rounds"
      ;; Same as avoidance test but round 20 (15 rounds since unload at round 5 - expired).
      ;; Country-1 army should now be loadable again.
      (reset! atoms/round-number 20)
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :country-id 1}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 0
                                                        :unloaded-countries {1 5}}}
                                {:type :land :contents {:type :army :owner :computer :country-id 2}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Both armies should be loaded (exclusion expired)
      (should-be-nil (get-in @atoms/game-map [0 0 :contents]))
      (should-be-nil (get-in @atoms/game-map [0 2 :contents]))
      ;; Transport should have 2 armies loaded
      (let [t-pos (first (for [c (range 3)
                               :when (= :transport (get-in @atoms/game-map [0 c :contents :type]))]
                           [0 c]))
            transport (get-in @atoms/game-map (conj t-pos :contents))]
        (should= 2 (:army-count transport))))

    (it "armies with no country-id are not filtered"
      (reset! atoms/round-number 10)
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer}}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 0
                                                        :unloaded-countries {1 5}}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 2])
      ;; Should move toward army (no country-id, not filtered)
      (let [transport-pos (first (for [c (range 4)
                                       :when (= :transport (get-in @atoms/game-map [0 c :contents :type]))]
                                   [0 c]))]
        (should= [0 1] transport-pos)))

    (it "records unloaded country-id on unload"
      (reset! atoms/round-number 15)
      (reset! atoms/game-map [[{:type :land :country-id 3}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (let [transport (:contents (get-in @atoms/game-map [0 1]))]
        (should= 15 (get-in transport [:unloaded-countries 3]))))

    (it "transport falls back to explore when all armies filtered"
      (reset! atoms/round-number 10)
      (let [game-map (build-test-map ["a~a"
                                      "~t~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map (build-test-map ["a~a"
                                                    "~t~"
                                                    "~~-"]))
        (swap! atoms/game-map assoc-in [0 0 :contents :country-id] 1)
        (swap! atoms/game-map assoc-in [2 0 :contents :country-id] 1)
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 0
                :unloaded-countries {1 5}})
        (transport/process-transport [1 1])
        (let [transport-pos (first (for [r (range 3) c (range 3)
                                         :when (= :transport (get-in @atoms/game-map [r c :contents :type]))]
                                     [r c]))]
          (should-not-be-nil transport-pos)
          (should-not= [1 1] transport-pos)))))

  (context "unload-event-id filtering"
    (it "find-nearest-army skips armies with matching unload-event-id"
      (reset-all-atoms!)
      (let [game-map (build-test-map ["a~~"
                                      "~~~"
                                      "~t~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [0 0 :contents :unload-event-id] 42)
        (let [result (#'transport/find-nearest-army [1 2] nil nil 42)]
          (should-be-nil result))))

    (it "find-nearest-army finds armies with different unload-event-id"
      (reset-all-atoms!)
      (let [game-map (build-test-map ["a~~"
                                      "~~~"
                                      "~t~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [0 0 :contents :unload-event-id] 99)
        (let [result (#'transport/find-nearest-army [1 2] nil nil 42)]
          (should= [0 0] result))))

    (it "find-nearest-army finds armies with no unload-event-id"
      (reset-all-atoms!)
      (let [game-map (build-test-map ["a~~"
                                      "~~~"
                                      "~t~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (let [result (#'transport/find-nearest-army [1 2] nil nil 42)]
          (should= [0 0] result))))

    (it "mint-unload-event-id always mints new ID even when one exists"
      (reset-all-atoms!)
      (reset! atoms/next-unload-event-id 100)
      (let [game-map (build-test-map ["t~"])]
        (reset! atoms/game-map game-map)
        (swap! atoms/game-map assoc-in [0 0 :contents :unload-event-id] 42)
        (let [transport (get-in @atoms/game-map [0 0 :contents])]
          (#'transport/mint-unload-event-id [0 0] transport)
          (should= 100 (get-in @atoms/game-map [0 0 :contents :unload-event-id]))
          (should= 101 @atoms/next-unload-event-id)))))

  (context "unload-country-id"
    (it "mint-unload-country-id mints fresh country-id onto transport"
      (reset-all-atoms!)
      (reset! atoms/next-country-id 50)
      (let [game-map (build-test-map ["t~"])]
        (reset! atoms/game-map game-map)
        (#'transport/mint-unload-country-id [0 0])
        (should= 50 (get-in @atoms/game-map [0 0 :contents :unload-country-id]))
        (should= 51 @atoms/next-country-id)))

    (it "start-sailing sets unload-country-id on transport"
      (reset-all-atoms!)
      (reset! atoms/next-country-id 10)
      (let [game-map (build-test-map ["###"
                                      "~t~"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map
                (vec (for [c (range 3)]
                       (vec (for [r (range 5)]
                              (if (< r 4) (get-in game-map [c r]) nil))))))
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 3) r (range 5)
                             :let [unit (get-in @atoms/game-map [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :sailing (:transport-mission t))
          (should-not-be-nil (:unload-country-id t)))))

    (it "unloaded army gets country-id from transport"
      (reset-all-atoms!)
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 2
                                                        :sail-path [[0 2]]
                                                        :unload-country-id 77}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (let [army (:contents (get-in @atoms/game-map [0 0]))]
        (should= :army (:type army))
        (should= 77 (:country-id army))))

    (it "unloaded army stamps land with country-id"
      (reset-all-atoms!)
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 1
                                                        :sail-path [[0 2]]
                                                        :unload-country-id 88}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (should= 88 (:country-id (get-in @atoms/game-map [0 0]))))

    (it "transport records unloaded country-id after unloading"
      (reset-all-atoms!)
      (reset! atoms/round-number 5)
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 1
                                                        :sail-path [[0 2]]
                                                        :unload-country-id 33}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (let [transport (:contents (get-in @atoms/game-map [0 1]))]
        (should= 5 (get-in transport [:unloaded-countries 33]))))

    (it "transport does not reload armies it just unloaded (cycle-breaking)"
      (reset-all-atoms!)
      (reset! atoms/round-number 5)
      (reset! atoms/next-country-id 100)
      ;; Transport at [0,1] with 1 army, adjacent to empty land at [0,0].
      ;; After unloading, army gets country-id 100 and land gets stamped.
      ;; Transport transitions to loading but should NOT reload that army
      ;; because its country-id was just recorded in unloaded-countries.
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading :army-count 1
                                                        :unload-country-id 100}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Army should be on land
      (should= :army (get-in @atoms/game-map [0 0 :contents :type]))
      ;; Transport should have 0 armies (did NOT reload)
      (let [transport (:contents (get-in @atoms/game-map [0 1]))]
        (should= :loading (:transport-mission transport))
        (should= 0 (:army-count transport)))))

  (context "transport without army-count field"
    (it "loads armies even when army-count is missing from transport"
      ;; Bug: transports spawned without :army-count caused NPE in load-adjacent-armies
      ;; because (+ nil to-load) throws. Armies were removed but count never incremented.
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1 :mode :sentry}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      ;; Note: transport has NO :army-count key at all
      (transport/process-transport [0 1])
      ;; Army should be loaded (removed from land)
      (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
      ;; Transport should have army-count 1
      (let [t-pos (first (for [c (range 3)
                                :when (= :transport (get-in @atoms/game-map [0 c :contents :type]))]
                            [0 c]))
            transport (get-in @atoms/game-map (conj t-pos :contents))]
        (should= 1 (:army-count transport))))

    (it "newly spawned computer transport has army-count 0"
      (reset! atoms/round-number 5)
      (reset! atoms/game-map [[{:type :city :city-status :computer :country-id 1}]])
      (reset! atoms/computer-map @atoms/game-map)
      (reset! atoms/production {[0 0] {:item :transport :remaining-rounds 1}})
      (player-prod/update-production)
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should= :transport (:type unit))
        (should= 0 (:army-count unit)))))


  (context "sail threshold"
    (it "does not sail with fewer than 4 armies"
      ;; Transport with 2 armies, no adjacent loadable armies.
      ;; Land has same country-id as transport to prevent opportunistic unload.
      (let [game-map (build-test-map ["###"
                                      "~t~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (doseq [c (range 3)]
          (swap! atoms/game-map assoc-in [c 0 :country-id] 1))
        (reset! atoms/computer-map
                (vec (for [c (range 3)]
                       (vec (for [r (range 4)]
                              (if (< r 3) (get-in game-map [c r]) nil))))))
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 2
                :country-id 1})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 3) r (range 4)
                             :let [unit (get-in @atoms/game-map [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :loading (:transport-mission t)))))

    (it "does not sail when loadable army within 3 coastal cells"
      ;; Transport with 4 armies at [1,1]. Army at [5,0] is 3 coastal hops away.
      ;; Land has same country-id as transport to prevent opportunistic unload.
      ;; ######   row 0: land, army at col 5
      ;; ~t~~~~   row 1: transport at [1,1]
      (let [game-map (build-test-map ["#####a"
                                      "~t~~~~"])]
        (reset! atoms/game-map game-map)
        (doseq [c (range 6)]
          (swap! atoms/game-map assoc-in [c 0 :country-id] 1))
        (reset! atoms/computer-map
                (vec (for [c (range 6)]
                       (vec (for [r (range 2)]
                              (get-in game-map [c r]))))))
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 4
                :country-id 1})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 6) r (range 2)
                             :let [unit (get-in @atoms/game-map [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :loading (:transport-mission t)))))

    (it "sails when loadable army is beyond 3 coastal cells"
      ;; Transport with 4 armies at [1,1]. Army at [6,0] is 4 coastal hops away.
      ;; Land has same country-id as transport to prevent opportunistic unload.
      (let [game-map (build-test-map ["######a"
                                      "~t~~~~~"])]
        (reset! atoms/game-map game-map)
        (doseq [c (range 7)]
          (swap! atoms/game-map assoc-in [c 0 :country-id] 1))
        (reset! atoms/computer-map
                (vec (for [c (range 7)]
                       (vec (for [r (range 2)]
                              (get-in game-map [c r]))))))
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 4
                :country-id 1})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 7) r (range 2)
                             :let [unit (get-in @atoms/game-map [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :sailing (:transport-mission t))))))

  (context "full transport sailing"
    (it "full loading transport enters sailing toward fog"
      ;; Full transport with no adjacent loadable armies enters sailing
      (let [game-map (build-test-map ["t~~~~~"
                                      "~~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map
                (vec (for [c (range 6)]
                       (vec (for [r (range 2)]
                              (if (< c 4)
                                (get-in game-map [c r])
                                nil))))))
        (swap! atoms/game-map assoc-in [0 0 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (transport/process-transport [0 0])
        (let [t (first (for [c (range 6) r (range 2)
                               :let [unit (get-in @atoms/game-map [c r :contents])]
                               :when (= :transport (:type unit))]
                           unit))]
          (should= :sailing (:transport-mission t)))))

    (it "full transport sails even when nearby armies exist"
      ;; Full transport in narrow channel with armies on adjacent land
      ;; Should still sail because it can't load any more
      ;; a##    army at [0,0], land at [1,0] and [2,0]
      ;; ~t~    transport at [1,1], sea at [0,1] and [2,1]
      ;; ~~~    open sea rows 2-4
      ;; ~~~
      ;; ~~~
      (let [game-map (build-test-map ["a##"
                                      "~t~"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map
                (vec (for [c (range 3)]
                       (vec (for [r (range 5)]
                              (if (< r 3) (get-in game-map [c r]) nil))))))
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 3) r (range 5)
                             :let [unit (get-in @atoms/game-map [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should-not-be-nil t)
          (should= :sailing (:transport-mission t))))))

  ;; smart sailing heading tests removed — replaced by sail-path sailing

  (context "sail-path sailing"
    (it "empty sailing transport with empty sail-path transitions to loading"
      (reset! atoms/game-map (build-test-map ["~~t#"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [2 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 0
              :sail-path []})
      (transport/process-transport [2 0])
      (let [t (first (for [c (range 4) r (range 1)
                           :let [unit (get-in @atoms/game-map [c r :contents])]
                           :when (= :transport (:type unit))]
                       unit))]
        (should= :loading (:transport-mission t))))

    (it "follows sail-path two steps per turn (speed 2)"
      (reset! atoms/game-map (build-test-map ["t~~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path [[1 0] [2 0] [3 0]]})
      (transport/process-transport [0 0])
      (let [t (:contents (get-in @atoms/game-map [2 0]))]
        (should= :transport (:type t))
        (should= [[3 0]] (:sail-path t))))

    (it "continues in same direction when sail-path exhausted after 1 step"
      ;; t at [0 0], sail-path [[1 0]] — only 1 step.
      ;; After step 1 to [1 0], path is empty. Continue in same direction to [2 0].
      (reset! atoms/game-map (build-test-map ["t~~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path [[1 0]]})
      (transport/process-transport [0 0])
      (let [t (:contents (get-in @atoms/game-map [2 0]))]
        (should= :transport (:type t))
        (should= [] (:sail-path t))))

    (it "stops after 1 step when continuation hits land"
      ;; t at [0 0], path [[1 0]], land at [2 0]
      ;; After step 1, continuation direction blocked by land — stays at [1 0]
      (reset! atoms/game-map (build-test-map ["t~#~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path [[1 0]]})
      (transport/process-transport [0 0])
      (let [t (:contents (get-in @atoms/game-map [1 0]))]
        (should= :transport (:type t))
        (should= [] (:sail-path t))))

    (it "retreats one cell back when blocked by enemy"
      ;; t at [2 0], enemy D at [3 0], sail-path [[3 0] [4 0]]
      ;; Transport should retreat to [1 0] and prepend [2 0] to path
      (reset! atoms/game-map (build-test-map ["~~tD~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [2 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path [[3 0] [4 0]]})
      (transport/process-transport [2 0])
      (let [t (:contents (get-in @atoms/game-map [1 0]))]
        (should= :transport (:type t))
        (should= [[2 0] [3 0] [4 0]] (:sail-path t))))

    (it "unloads at unowned land when sail-path is empty"
      ;; t at [2 0], land at [3 0], sail-path []
      (reset! atoms/game-map (build-test-map ["~~t#"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [2 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path []})
      (transport/process-transport [2 0])
      (let [t (:contents (get-in @atoms/game-map [2 0]))]
        (should= 5 (:army-count t))
        (should= :army (get-in @atoms/game-map [3 0 :contents :type]))))

    (it "computes sail-path toward fog-of-war when entering sailing"
      ;; t at [0 0], fog starts at col 3 on computer map
      ;; game map: t~~~~~~~~
      ;; comp map: t~~......
      (reset! atoms/game-map (build-test-map ["t~~~~~~~~"]))
      (reset! atoms/computer-map
              (vec (for [c (range 9)]
                     [(if (< c 3)
                        (get-in @atoms/game-map [c 0])
                        nil)])))
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 6})
      (transport/process-transport [0 0])
      (let [t (first (for [c (range 9)
                           :let [unit (get-in @atoms/game-map [c 0 :contents])]
                           :when (= :transport (:type unit))]
                       unit))]
        (should= :sailing (:transport-mission t))
        (should-not-be-nil (:sail-path t))
        (should (vector? (:sail-path t)))
        (should (pos? (count (:sail-path t))))))

    (it "exhausted sail-path with armies transitions to unloading"
      ;; t at [4 0], fog on both sides, sail-path []
      ;; Should transition to :unloading (then start-sailing on next round if no coast)
      (reset! atoms/game-map (build-test-map ["~~~~~~~~~"]))
      (reset! atoms/computer-map
              (vec (for [c (range 9)]
                     [(if (<= 3 c 5)
                        (get-in @atoms/game-map [c 0])
                        nil)])))
      (swap! atoms/game-map assoc-in [4 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 6
              :sail-path []})
      (transport/process-transport [4 0])
      (let [t (:contents (get-in @atoms/game-map [4 0]))]
        (should= :unloading (:transport-mission t)))))

  (context "load armies after move-toward-position"
    (it "loads army adjacent to destination when moving toward pickup continent"
      ;; a##a#    army at [0,0], land row 0, army at [3,0]
      ;; ~t~~~    transport at [1,1] heading toward pickup-continent-pos [4,0]
      ;; ~~~~~    sea
      ;; Transport moves from [1,1] toward [4,0], arrives at [2,1].
      ;; Army at [3,0] is adjacent to [2,1] — should be loaded.
      (let [game-map (build-test-map ["a##a#"
                                      "~t~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 0
                :pickup-continent-pos [4 0]})
        (transport/process-transport [1 1])
        ;; Army at [0,0] should be loaded (adjacent to start pos)
        (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
        ;; Army at [3,0] should also be loaded (adjacent to destination)
        (should-be-nil (:contents (get-in @atoms/game-map [3 0])))
        ;; Transport should have 2 armies loaded
        (let [t (first (for [c (range 5) r (range 3)
                             :let [unit (get-in @atoms/game-map [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= 2 (:army-count t)))))))
