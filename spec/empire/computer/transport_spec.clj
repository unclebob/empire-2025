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

    (it "loading coastal crawl moves 2 cells per round (speed 2)"
      ;; ########   land at row 0
      ;; t~~~~~~~   transport at [0,1]
      ;; Linear coast — only one direction to crawl (rightward)
      (reset! atoms/game-map (build-test-map ["########"
                                               "t~~~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0})
      (transport/process-transport [0 1])
      ;; Transport should have moved 2 cells, not 1
      (should-be-nil (:contents (get-in @atoms/game-map [0 1])))
      (should-be-nil (:contents (get-in @atoms/game-map [1 1])))
      (should= :transport (get-in @atoms/game-map [2 1 :contents :type])))

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

    (it "unloading crawl moves 2 cells per round (speed 2)"
      ;; ########   land at row 0 (cols 0-1 excluded, cols 2+ unloadable)
      ;; t~~~~~~~   transport at [0,1] in unloading mode
      ;; Adjacent land excluded → opportunistic unload fails.
      ;; BFS finds unloadable land at col 2 → unloading-crawl-move fires.
      (reset! atoms/game-map (build-test-map ["########"
                                               "t~~~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [c (range 2)]
        (swap! atoms/game-map assoc-in [c 0 :country-id] 1))
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :unloading :army-count 2
              :country-id 1
              :pickup-continent-pos [0 0]})
      (transport/process-transport [0 1])
      ;; Transport should have crawled 2 cells, not 1
      (should-be-nil (:contents (get-in @atoms/game-map [0 1])))
      (should-be-nil (:contents (get-in @atoms/game-map [1 1])))
      (should= :transport (get-in @atoms/game-map [2 1 :contents :type])))

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

  (context "sail trigger boundaries"
    (it "transport with 4 armies and no nearby armies starts sailing"
      ;; ~~~   all sea
      ;; ~t~   transport at [1,1] with 4 armies, no adjacent armies
      ;; ~~~   all sea (no adjacent land = no opportunistic unload)
      (reset! atoms/game-map (build-test-map ["~~~"
                                               "~t~"
                                               "~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 4})
      (transport/process-transport [1 1])
      (let [t (some (fn [[c r]]
                      (let [u (get-in @atoms/game-map [c r :contents])]
                        (when (= :transport (:type u)) u)))
                    (for [c (range 3) r (range 3)] [c r]))]
        (should= :sailing (:transport-mission t))))

    (it "transport with 6 armies starts sailing even with nearby armies"
      ;; a###   army at [0,0], land
      ;; ~t~~   transport at [1,1] with 6 armies
      (reset! atoms/game-map (build-test-map ["a###"
                                               "~t~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 6})
      (transport/process-transport [1 1])
      (let [t (some (fn [[c r]]
                      (let [u (get-in @atoms/game-map [c r :contents])]
                        (when (= :transport (:type u)) u)))
                    (for [c (range 4) r (range 2)] [c r]))]
        (should= :sailing (:transport-mission t))))

    (it "transport with 3 armies stays loading"
      ;; ####   land at row 0
      ;; ~t~~   transport at [1,1] with 3 armies
      (reset! atoms/game-map (build-test-map ["####"
                                               "~t~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 3})
      (transport/process-transport [1 1])
      (let [t (some (fn [[c r]]
                      (let [u (get-in @atoms/game-map [c r :contents])]
                        (when (= :transport (:type u)) u)))
                    (for [c (range 4) r (range 2)] [c r]))]
        (should= :loading (:transport-mission t)))))

  (context "unloaded army properties"
    (it "unload-armies produces army with hits 1"
      ;; #t#   land-sea-land, transport at [1,0] with 2 armies
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 2}}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/unload-armies [0 1] nil)
      (should= 1 (:hits (:contents (get-in @atoms/game-map [0 0]))))
      (should= 1 (:hits (:contents (get-in @atoms/game-map [0 2])))))

    (it "opportunistic unload produces army with hits 1 and unload-event-id"
      ;; #t#   transport at [1,0] sailing with unload-event-id 42
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing
                                                        :army-count 2
                                                        :unload-event-id 42
                                                        :sail-path [[0 2]]}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (should= 1 (:hits (:contents (get-in @atoms/game-map [0 0]))))
      (should= 42 (:unload-event-id (:contents (get-in @atoms/game-map [0 0]))))))

  (context "unload country-id tracking"
    (it "unload-armies records country-id in unloaded-countries"
      ;; Land at [0,0] has country-id 7. Transport unloads army there.
      (reset! atoms/round-number 5)
      (reset! atoms/game-map [[{:type :land :country-id 7}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/unload-armies [0 1] nil)
      (let [transport (:contents (get-in @atoms/game-map [0 1]))]
        (should= 5 (get-in transport [:unloaded-countries 7]))))

    (it "opportunistic unload records country-id in unloaded-countries"
      (reset! atoms/round-number 10)
      (reset! atoms/game-map [[{:type :land :country-id 3}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing
                                                        :army-count 1
                                                        :sail-path [[0 2]]}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Find transport wherever it ended up
      (let [t (some (fn [c]
                      (let [u (get-in @atoms/game-map [0 c :contents])]
                        (when (= :transport (:type u)) u)))
                    (range 3))]
        (should= 10 (get-in t [:unloaded-countries 3])))))

  (context "full-unload boundary"
    (it "transport with army-count equal to adjacent land cells transitions to loading"
      ;; #t#   2 land cells, transport with 2 armies -> fully unloaded -> loading
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 2}}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/unload-armies [0 1] nil)
      (should= :loading (:transport-mission (:contents (get-in @atoms/game-map [0 1]))))
      (should= 0 (:army-count (:contents (get-in @atoms/game-map [0 1])))))

    (it "transport with more armies than adjacent land cells stays unloading"
      ;; #t#   2 land cells, transport with 3 armies -> partial unload -> stays unloading
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 3}}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/unload-armies [0 1] nil)
      (should= :unloading (:transport-mission (:contents (get-in @atoms/game-map [0 1]))))
      (should= 1 (:army-count (:contents (get-in @atoms/game-map [0 1]))))))

  (context "load capacity"
    (it "transport with 4 armies loads exactly 2 from 5 adjacent armies"
      ;; aaaaa   5 armies at row 0
      ;; ~~t~~   transport at [2,1] with 4 armies -> capacity = 6-4 = 2
      (reset! atoms/game-map (build-test-map ["aaaaa"
                                               "~~t~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [2 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 4})
      (transport/process-transport [2 1])
      ;; Find transport — should have 6 armies (4 + 2 loaded)
      (let [t (some (fn [[c r]]
                      (let [u (get-in @atoms/game-map [c r :contents])]
                        (when (= :transport (:type u)) u)))
                    (for [c (range 5) r (range 2)] [c r]))]
        (should= 6 (:army-count t)))))

  (context "unload-event-id propagation"
    (it "opportunistic unload gives army the transport's unload-event-id"
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing
                                                        :army-count 1
                                                        :unload-event-id 99
                                                        :sail-path [[0 2]]}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (should= 99 (:unload-event-id (:contents (get-in @atoms/game-map [0 0]))))))

  (context "BFS army type and owner filtering"
    (it "4-army transport near player army sails (not loadable)"
      ;; All adjacent land occupied by player army — no empty land for opportunistic unload
      ;; A    player army at [0,0]
      ;; t~   transport at [0,1] with 4 armies
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :player :hits 1}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 4}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (let [t (some (fn [c]
                      (let [u (get-in @atoms/game-map [0 c :contents])]
                        (when (= :transport (:type u)) u)))
                    (range 3))]
        (should= :sailing (:transport-mission t))))

    (it "4-army transport near computer fighter sails (not loadable)"
      ;; f    computer fighter at [0,0] — wrong type
      ;; t~   transport at [0,1]
      (reset! atoms/game-map [[{:type :land :contents {:type :fighter :owner :computer :hits 1 :fuel 20}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 4}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (let [t (some (fn [c]
                      (let [u (get-in @atoms/game-map [0 c :contents])]
                        (when (= :transport (:type u)) u)))
                    (range 3))]
        (should= :sailing (:transport-mission t))))

    (it "4-army transport near army with matching unload-event-id sails"
      ;; a    army at [0,0] with unload-event-id 5
      ;; t~   transport at [0,1] with unload-event-id 5
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :unload-event-id 5}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 4
                                                        :unload-event-id 5}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (let [t (some (fn [c]
                      (let [u (get-in @atoms/game-map [0 c :contents])]
                        (when (= :transport (:type u)) u)))
                    (range 3))]
        (should= :sailing (:transport-mission t)))))

  (context "BFS multi-hop"
    (it "loadable army 2 coastal hops away keeps transport loading"
      ;; ##a   army at [2,0], land at [0,0],[1,0]
      ;; ##~   land at [0,1],[1,1], sea at [2,1]
      ;; ~t~   sea at [0,2], transport at [1,2], sea at [2,2]
      ;; Transport at [1,2]. Adjacent sea [2,1] is coastal. [2,1] is adjacent
      ;; to army at [2,0]. So BFS finds loadable army at depth 1.
      (reset! atoms/game-map (build-test-map ["##a"
                                               "##~"
                                               "~t~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 2 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 4})
      (transport/process-transport [1 2])
      ;; Transport should stay loading (army within BFS range)
      (let [t (some (fn [[c r]]
                      (let [u (get-in @atoms/game-map [c r :contents])]
                        (when (= :transport (:type u)) u)))
                    (for [c (range 3) r (range 3)] [c r]))]
        (should= :loading (:transport-mission t)))))

  (context "BFS unloadable land"
    (it "unloading transport with unloadable land nearby crawls not re-sails"
      ;; ######     land at row 0 (country-id 1 = excluded)
      ;; ~t~~~~#    transport at [1,1], land at [6,1] is unloadable (no country-id)
      ;; Long coast — transport should crawl toward unloadable land, not re-sail.
      (reset! atoms/game-map (build-test-map ["######~"
                                               "~t~~~~#"]))
      (reset! atoms/computer-map @atoms/game-map)
      ;; Mark row-0 land as excluded (country-id 1)
      (doseq [c (range 6)]
        (swap! atoms/game-map assoc-in [c 0 :country-id] 1))
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :unloading :army-count 2
              :country-id 1
              :pickup-continent-pos [0 0]})
      (with-redefs [rand (constantly 0.0)]
        (transport/process-transport [1 1]))
      ;; Transport should have crawled rightward (speed 2)
      ;; Find transport and verify it's still unloading
      (let [t (some (fn [[c r]]
                      (let [u (get-in @atoms/game-map [c r :contents])]
                        (when (= :transport (:type u)) u)))
                    (for [c (range 7) r (range 2)] [c r]))]
        (should= :unloading (:transport-mission t)))))

  (context "continue-pos sailing"
    (it "sailing with 1-element sail-path continues direction for second step"
      ;; ~~~   row 0
      ;; t~~   transport at [0,0], sail-path [[1,0]]
      ;; ~~~   row 2 — continue-pos should put transport at [2,0]
      (reset! atoms/game-map (build-test-map ["~~~"
                                               "t~~"
                                               "~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 2
              :sail-path [[1 0]]})
      (transport/process-transport [0 0])
      ;; Should have moved to [1,0] then continued to [2,0]
      (should= :transport (get-in @atoms/game-map [2 0 :contents :type]))))

  (context "sailing blocked retreat"
    (it "sailing transport blocked by player unit retreats"
      ;; ~D~   player destroyer at [1,0] blocks the path
      ;; t~~   transport at [0,1], sail-path [[1,0],[2,0]]
      ;; ~~~   row 2
      (reset! atoms/game-map [[{:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 2
                                                        :sail-path [[1 0]]}}
                                {:type :sea}]
                               [{:type :sea :contents {:type :destroyer :owner :player :hits 3}}
                                {:type :sea}
                                {:type :sea}]
                               [{:type :sea}
                                {:type :sea}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Transport should have retreated to a passable neighbor (not [1,0])
      (should-be-nil (:contents (get-in @atoms/game-map [0 1])))
      (let [t (some (fn [[c r]]
                      (let [u (get-in @atoms/game-map [c r :contents])]
                        (when (= :transport (:type u)) [c r])))
                    (for [c (range 3) r (range 3)] [c r]))]
        (should-not-be-nil t)
        (should-not= [1 0] t))))

  (context "sailing remaining path"
    (it "multi-step sail-path preserves remaining after 2 steps"
      ;; ~~~~   row 0
      ;; t~~~   transport at [0,0], sail-path [[1,0],[2,0],[3,0]]
      (reset! atoms/game-map (build-test-map ["~~~~"
                                               "~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 2
              :sail-path [[1 0] [2 0] [3 0]]})
      (transport/process-transport [0 0])
      ;; After 2 steps, transport at [2,0] with remaining [[3,0]]
      (should= :transport (get-in @atoms/game-map [2 0 :contents :type]))
      (should= [[3 0]] (get-in @atoms/game-map [2 0 :contents :sail-path]))))

  (context "score-target-city"
    (it "prefers city on continent with more attackable cities"
      ;; Two free cities on separate continents, equidistant from transport.
      ;; Continent A at [0,0] has 1 free city.
      ;; Continent B at [4,0]-[4,1] has 2 free cities.
      ;; Transport at [2,2]. More attackable cities -> lower score -> preferred.
      (let [game-map (build-test-map ["+~~~++"
                                       "~~~~##"
                                       "~~t~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (let [target (transport/find-unload-target nil [2 2])]
          ;; Should prefer continent B (2 free cities vs 1)
          (should-not-be-nil target)
          (should (#{[4 0] [5 0]} target))))))

  (context "find-unload-target filtering"
    (it "excludes pickup-continent cities"
      ;; Continent A (rows 0-1) with player city. Transport came from continent A.
      ;; Continent B (rows 4-5) with free city.
      (let [game-map (build-test-map ["O##"
                                       "###"
                                       "~~~"
                                       "~~~"
                                       "+##"
                                       "###"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target (transport/find-unload-target pickup-continent [1 3])]
          ;; Should exclude continent A city [0,0] and pick continent B
          (should-not-be-nil target)
          (should= [0 4] target))))

    (it "prefers unclaimed target over claimed"
      ;; Two continents with free cities. One already claimed.
      (let [game-map (build-test-map ["+#~+#"
                                       "##~##"
                                       "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        ;; Claim the first city
        (reset! atoms/claimed-transport-targets #{[0 0]})
        (let [target (transport/find-unload-target nil [2 2])]
          ;; Should pick the unclaimed city [3,0]
          (should= [3 0] target)))))

  (context "loading pcp clearing"
    (it "clears pickup-continent-pos when adjacent to pickup continent"
      ;; ##   land at row 0, country-id 5
      ;; t~   transport at [0,1] loading with pickup-continent-pos [0,0]
      (reset! atoms/game-map (build-test-map ["##"
                                               "t~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (doseq [c (range 2)]
        (swap! atoms/game-map assoc-in [c 0 :country-id] 5))
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0
              :pickup-continent-pos [0 0]})
      (transport/process-transport [0 1])
      ;; Find transport — pickup-continent-pos should be cleared
      (let [t (some (fn [[c r]]
                      (let [u (get-in @atoms/game-map [c r :contents])]
                        (when (= :transport (:type u)) u)))
                    (for [c (range 2) r (range 2)] [c r]))]
        (should-be-nil (:pickup-continent-pos t)))))

  (context "idle mission fix"
    (it "transport with idle mission transitions to loading"
      (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :computer
                                                       :transport-mission :idle
                                                       :army-count 0}}
                                {:type :sea}
                                {:type :land}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 0])
      (let [t (some (fn [c]
                      (let [u (get-in @atoms/game-map [0 c :contents])]
                        (when (= :transport (:type u)) u)))
                    (range 3))]
        (should= :loading (:transport-mission t)))))

  (context "crawl history avoidance"
    (it "unloading crawl avoids positions in crawl-history"
      ;; ###   land at row 0
      ;; ~t~   transport at [1,1] with crawl-history [[0,1],[2,1]]
      ;; ~~~   row 2
      ;; Preferred targets exclude history. With rand fixed, should pick remaining.
      (reset! atoms/game-map (build-test-map ["###"
                                               "~t~"
                                               "~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :unloading :army-count 2
              :crawl-history [[0 1] [2 1]]
              :country-id 99})
      ;; Adjacent land at [0,0]-[2,0] has no country-id, so opportunistic unload fires.
      ;; After unload, check the crawl-history was respected.
      (with-redefs [rand (constantly 0.0)]
        (transport/process-transport [1 1]))
      ;; Transport should still be somewhere (opportunistic unload, then crawl)
      (let [t (some (fn [[c r]]
                      (let [u (get-in @atoms/game-map [c r :contents])]
                        (when (= :transport (:type u)) {:pos [c r] :unit u})))
                    (for [c (range 3) r (range 3)] [c r]))]
        (should-not-be-nil t)
        ;; crawl-history should have been updated
        (should (seq (:crawl-history (:unit t)))))))

  (context "move-toward-position"
    (it "loading transport with pickup-continent-pos moves toward it"
      ;; ~~~~~~#   land at [6,0]
      ;; t~~~~~~   transport at [0,1] with pickup-continent-pos [6,0]
      ;; Linear sea — transport should move rightward toward [6,0]
      (reset! atoms/game-map (build-test-map ["~~~~~~#"
                                               "t~~~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0
              :pickup-continent-pos [6 0]})
      (transport/process-transport [0 1])
      ;; Transport should have moved rightward (col > 0)
      (should-be-nil (:contents (get-in @atoms/game-map [0 1])))
      (let [t-pos (some (fn [[c r]]
                          (when (= :transport (get-in @atoms/game-map [c r :contents :type]))
                            [c r]))
                        (for [c (range 7) r (range 2)] [c r]))]
        (should (> (first t-pos) 0))))

    (it "falls back to coastal crawl when preferred cell is occupied by another transport"
      ;; ##~~~~   col0,col1 = land; col2-5 = sea
      ;; #t~~~~   transport at [1,1]; pcp [5,1] (far east)
      ;; ##~~~~   col0,col1 = land; col2-5 = sea
      ;; Blocker transport placed at [2,1].
      ;; move-toward picks [2,1] (closest to pcp), but move fails.
      ;; Should fall back to coastal crawl → [2,0] or [2,2].
      (reset! atoms/game-map (build-test-map ["##~~~~"
                                               "#t~~~~"
                                               "##~~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0
              :pickup-continent-pos [5 1]})
      (swap! atoms/game-map assoc-in [2 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0})
      (transport/process-transport [1 1])
      ;; Transport should have moved away from [1,1]
      (should-be-nil (:contents (get-in @atoms/game-map [1 1])))))

  (context "passable-sea with computer unit"
    (it "transport moves past sea cell containing computer unit"
      ;; ~s~   computer sub at [1,0]
      ;; t~~   transport at [0,1]
      ;; ~~~   row 2
      ;; get-passable-sea-neighbors should include [1,0] (computer-owned)
      (reset! atoms/game-map (build-test-map ["~s~"
                                               "t~~"
                                               "~~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :sailing :army-count 2
              :sail-path [[1 0]]})
      ;; [1,0] has a computer sub — should still be passable for pathfinding
      ;; But move-unit-to will fail because cell is occupied.
      ;; Transport should retreat to a passable neighbor.
      (transport/process-transport [0 1])
      ;; Transport should have moved somewhere (retreat)
      (let [t (some (fn [[c r]]
                      (let [u (get-in @atoms/game-map [c r :contents])]
                        (when (= :transport (:type u)) [c r])))
                    (for [c (range 3) r (range 3)] [c r]))]
        (should-not-be-nil t))))

  (context "zero-army unloading"
    (it "unloading transport with 0 armies transitions to loading"
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 0}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (should= :loading (:transport-mission (:contents (get-in @atoms/game-map [0 1]))))))

  (context "recently-unloaded countries"
    (it "skips army from country unloaded less than 10 rounds ago"
      ;; a#   army at [0,0] with country-id 7
      ;; t~   transport at [0,1] with unloaded-countries {7 5}, round 10
      ;; Army's country was unloaded 5 rounds ago (< 10) — should skip
      (reset! atoms/round-number 10)
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :country-id 7}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading
                                                        :army-count 0
                                                        :unloaded-countries {7 5}}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Army should NOT be loaded (recently unloaded country)
      (let [t (some (fn [c]
                      (let [u (get-in @atoms/game-map [0 c :contents])]
                        (when (= :transport (:type u)) u)))
                    (range 2))]
        (should= 0 (:army-count t))))

    (it "loads army from country unloaded 10+ rounds ago"
      ;; a#   army at [0,0] with country-id 7
      ;; t~   transport at [0,1] with unloaded-countries {7 0}, round 10
      ;; Army's country was unloaded 10 rounds ago (>= 10) — should load
      (reset! atoms/round-number 10)
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :hits 1
                                                        :country-id 7}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading
                                                        :army-count 0
                                                        :unloaded-countries {7 0}}}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Army should be loaded (country unloaded 10 rounds ago, not recent)
      (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
      (let [t (some (fn [c]
                      (let [u (get-in @atoms/game-map [0 c :contents])]
                        (when (= :transport (:type u)) u)))
                    (range 2))]
        (should= 1 (:army-count t))))))
