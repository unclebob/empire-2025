(ns empire.computer.transport-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [speclj.core :refer :all]
            [empire.computer.transport :as transport]
            [empire.computer.production :as production]
            [empire.computer.land-objectives :as land-objectives]
            [empire.player.production :as player-prod]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [build-test-map reset-all-atoms!]]))

(describe "process-transport"
  (before (reset-all-atoms!))

  (describe "coastal crawl loading"
    (it "crawls along coastline adjacent to land"
      ;; ###    land at row 0
      ;; t~~    transport at [0 1]
      (reset! atoms/game-map (build-test-map ["###"
                                               "t~~"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0
              :stuck-since-round 0})
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
              :stuck-since-round 0})
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
                                                        :stuck-since-round 0}} {:type :sea}]])
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
              :stuck-since-round 0})
      (transport/process-transport [0 1])
      ;; Find transport and check army-count
      (let [t-pos (first (for [c (range 3) r (range 2)
                                :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                            [c r]))
            transport (get-in @atoms/game-map (conj t-pos :contents))]
        ;; Should have loaded at least 1 army (the one adjacent at start)
        (should (pos? (:army-count transport))))))

  (describe "unloading behavior"
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

  (describe "heading-based sailing"
    (it "full transport gets heading and sails in heading direction"
      ;; 5x5 all sea, transport at center [2 2] with 6 armies
      ;; heading 180 (south) → transport should move to [2 3]
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :stuck-since-round 0})
        (with-redefs [rand-int (constantly 180)]
          (transport/process-transport [2 2]))
        ;; Transport should have moved south (to [2 3])
        (should-be-nil (:contents (get-in @atoms/game-map [2 2])))
        (let [transport (:contents (get-in @atoms/game-map [2 3]))]
          (should= :transport (:type transport))
          (should= :sailing (:transport-mission transport))
          (should= 180 (:heading transport))))))

  (describe "mission transitions"
    (it "sets idle transport to loading"
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

  (describe "ignores non-computer transports"
    (it "returns nil for player transport"
      (reset! atoms/game-map [[{:type :sea :contents {:type :transport :owner :player
                                                       :army-count 0}}]])
      (should-be-nil (transport/process-transport [0 0])))

    (it "returns nil for empty cell"
      (reset! atoms/game-map [[{:type :sea}]])
      (should-be-nil (transport/process-transport [0 0]))))

  (describe "origin continent tracking"
    (it "records pickup-continent-pos when transport becomes full"
      ;; Transport at [1,1] (sea) adjacent to land at [0,0..2]
      ;; No visible cities on computer-map so transport sails (not directed)
      (let [game-map (build-test-map ["###"
                                      "~t~"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (transport/process-transport [1 1])
        ;; Transport may have moved; find it
        (let [t (some (fn [[r c]]
                        (let [contents (get-in @atoms/game-map [c r :contents])]
                          (when (= :transport (:type contents)) contents)))
                      (for [r (range 5) c (range 3)] [c r]))]
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

  (describe "continent-aware unloading"
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

  (describe "coastline exploration priority"
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

    (it "falls back to open sea when no coastline frontier"
      ;; No known land, only unexplored open sea
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
      ;; Transport should still explore toward the unexplored cell
      (let [transport-pos (first (for [r (range 3) c (range 3)
                                       :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                                   [c r]))]
        (should-not-be-nil transport-pos)
        (should-not= [1 1] transport-pos))))

  (describe "sailing behavior"
    (it "full transport sails in heading direction"
      ;; Full transport sails along heading, not toward specific targets
      (let [game-map (build-test-map ["~~~"
                                      "~~~"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :stuck-since-round 0})
        (with-redefs [rand-int (constantly 0)]  ; heading 0 = north
          (transport/process-transport [1 2]))
        ;; Transport should have moved north
        (should-be-nil (:contents (get-in @atoms/game-map [1 2])))
        (let [transport (:contents (get-in @atoms/game-map [1 1]))]
          (should= :transport (:type transport))
          (should= :sailing (:transport-mission transport))
          (should= 0 (:heading transport)))))

    (it "sailing transport reflects off map border"
      ;; Transport at top edge heading north → should reflect
      (let [game-map (build-test-map ["~~~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 0 :contents]
               {:type :transport :owner :computer
                :transport-mission :sailing :army-count 6
                :heading 0 :stuck-since-round 0})
        (with-redefs [rand-int (constantly 10)]  ; jitter for reflection
          (transport/process-transport [1 0]))
        ;; Transport should stay (reflected, no move this turn)
        (let [transport (:contents (get-in @atoms/game-map [1 0]))]
          (should= :transport (:type transport))
          ;; Heading should have changed from 0 (north) to something southward
          (should-not= 0 (:heading transport)))))

    (it "sailing transport stops at unexplored coast and begins unloading"
      ;; Transport at [2 2] heading east (90). Land at col 4 is unexplored.
      ;; [3 2] is sea adjacent to unexplored land → transport stops and unloads.
      (let [game-map (build-test-map ["~~~~#"
                                      "~~~~#"
                                      "~~~~#"
                                      "~~~~#"
                                      "~~~~#"])]
        (reset! atoms/game-map game-map)
        ;; Sea explored, but land at col 4 is unexplored (nil)
        (reset! atoms/computer-map [(vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 {:type :sea}))
                                    (vec (repeat 5 nil))])
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :sailing :army-count 6
                :heading 90 :stuck-since-round 0})
        (with-redefs [rand-int (constantly 10)]
          (transport/process-transport [2 2]))
        ;; Transport should move to [3 2] and switch to unloading
        (let [transport (:contents (get-in @atoms/game-map [3 2]))]
          (should= :transport (:type transport))
          (should= :unloading (:transport-mission transport)))))

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
          (should= 0 armies-on-land)))))

  (describe "player city priority"
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

  (describe "transport target diversification"
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

  (describe "unload target persistence"
    (it "transport reuses stored unload-target-city"
      ;; Two player cities on different continents. Transport has stored target [10,0].
      ;; Even though [5,0] is closer, transport should use stored target.
      ;; Transport at [3,2] with no adjacent land (surrounded by sea).
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
                :pickup-continent-pos [0 1]
                :unload-target-city [0 10]})
        (transport/process-transport [3 2])
        ;; Transport should still have [0,10] as its unload-target-city (not re-picked)
        (let [transport-pos (first (for [c (range 5) r (range 12)
                                        :when (= :transport (get-in @atoms/game-map [c r :contents :type]))]
                                    [c r]))
              transport (get-in @atoms/game-map (conj transport-pos :contents))]
          (should= [0 10] (:unload-target-city transport)))))

    (it "full transport with stored target now sails instead"
      ;; With heading-based sailing, full transports ignore stored targets
      ;; and sail along their heading.
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [2 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :stuck-since-round 0
                :unload-target-city [0 5]})
        (with-redefs [rand-int (constantly 90)]  ; heading east
          (transport/process-transport [2 1]))
        ;; Transport should sail east, ignoring stored target
        (let [transport (:contents (get-in @atoms/game-map [3 1]))]
          (should= :sailing (:transport-mission transport))
          (should= 90 (:heading transport)))))

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

  (describe "find-unload-position bias fix"
    (it "picks sea cell closest to transport, not NW-biased"
      ;; Target city at [3,0]. Only row 1 has sea adjacent to land.
      ;; Transport at [6,2] - closest candidate is [6,1] (distance 1), not [0,1].
      (let [game-map (build-test-map ["###O###"
                                      "~~~~~~~"
                                      "~~~~~~~"])]
        (reset! atoms/game-map game-map)
        (swap! atoms/game-map assoc-in [6 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6})
        (let [result (#'empire.computer.transport/find-unload-position [3 0] nil [6 2])]
          ;; Should pick [6,1] (distance 1) not [0,1] (distance 7)
          (should= [6 1] result)))))

  (describe "global BFS unload (VMS-consistent)"
    (it "full transport with unloading mission transitions to sailing"
      ;; Full transport marked :unloading overridden by (>= army-count 6) check
      ;; which sets :sailing and assigns a heading.
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :pickup-continent-pos [0 1]
                :stuck-since-round 0})
        (with-redefs [rand-int (constantly 180)]
          (transport/process-transport [2 2]))
        ;; Transport should sail south, overriding :unloading
        (let [transport (:contents (get-in @atoms/game-map [2 3]))]
          (should= :transport (:type transport))
          (should= :sailing (:transport-mission transport))
          (should= 180 (:heading transport)))))

    (it "full transport sails regardless of nearby computer cities"
      ;; Full transport sails along heading, ignoring computer cities
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [2 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :stuck-since-round 0})
        (with-redefs [rand-int (constantly 180)]
          (transport/process-transport [2 2]))
        (let [transport (:contents (get-in @atoms/game-map [2 3]))]
          (should= :transport (:type transport))
          (should= :sailing (:transport-mission transport)))))

    (it "full transport sails even when no enemy cities visible"
      ;; Only computer cities. Full transport still sails with heading.
      (let [game-map (build-test-map ["X##"
                                      "###"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [1 3 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :stuck-since-round 0})
        (with-redefs [rand-int (constantly 180)]  ; heading south
          (transport/process-transport [1 3]))
        ;; Transport should have sailed south
        (let [transport (:contents (get-in @atoms/game-map [1 4]))]
          (should= :transport (:type transport))
          (should= :sailing (:transport-mission transport))))))

  (describe "no-reload from recently unloaded country"
    (it "transport avoids armies from recently unloaded country"
      ;; Transport adjacent to two armies: country-1 (recently unloaded) and country-2.
      ;; Should only load the country-2 army during auto-load.
      (reset! atoms/round-number 10)
      (reset! atoms/game-map [[{:type :land :contents {:type :army :owner :computer :country-id 1}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 0
                                                        :unloaded-countries {1 5}
                                                        :stuck-since-round 5}}
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
                                                        :unloaded-countries {1 5}
                                                        :stuck-since-round 15}}
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
        (swap! atoms/game-map assoc-in [0 2 :contents :country-id] 1)
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

  (describe "unload-event-id filtering"
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

  (describe "stuck transport scuttle"
    (it "transport scuttles after 10 rounds stuck"
      (reset! atoms/round-number 20)
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 0
                                                        :stuck-since-round 10}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Transport should be gone (scuttled)
      (should-be-nil (:contents (get-in @atoms/game-map [0 1]))))

    (it "unloads armies to adjacent land on scuttle"
      (reset! atoms/round-number 20)
      (reset! atoms/game-map [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 2
                                                        :stuck-since-round 10}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; Transport should be gone
      (should-be-nil (:contents (get-in @atoms/game-map [0 1])))
      ;; Army should be on land
      (should= :army (:type (:contents (get-in @atoms/game-map [0 0])))))

    (it "marks producing city as landlocked"
      (reset! atoms/round-number 20)
      (reset! atoms/game-map [[{:type :city :city-status :computer}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 0
                                                        :stuck-since-round 10
                                                        :produced-at [0 0]}}
                                {:type :sea}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      ;; City should be marked landlocked
      (should (:landlocked (get-in @atoms/game-map [0 0]))))

    (it "landlocked city produces no ships"
      (reset! atoms/game-map (build-test-map ["~X+"]))
      (reset! atoms/computer-map (build-test-map ["~X+"]))
      (swap! atoms/game-map assoc-in [0 1 :landlocked] true)
      (should-not (production/city-is-coastal? [0 1])))

    (it "stuck counter resets on move"
      ;; Transport that is not stuck (stuck-since-round was 5 rounds ago)
      (reset! atoms/round-number 10)
      (let [cells (vec (repeat 5 {:type :sea}))]
        (reset! atoms/game-map [cells])
        (reset! atoms/computer-map [[{:type :sea} {:type :sea} {:type :sea} {:type :sea} nil]])
        (swap! atoms/game-map assoc-in [0 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 0
                :stuck-since-round 8})
        (transport/process-transport [0 2])
        ;; Transport moved, stuck-since-round should be reset
        (let [transport-pos (first (for [c (range 5)
                                         :when (= :transport (get-in @atoms/game-map [0 c :contents :type]))]
                                     [0 c]))
              transport (get-in @atoms/game-map (conj transport-pos :contents))]
          (should-not-be-nil transport)
          (should= 10 (:stuck-since-round transport)))))

    (it "transport spawned with stuck-since-round and produced-at"
      ;; Test that new transports get stuck tracking fields
      (reset! atoms/round-number 5)
      (reset! atoms/game-map [[{:type :city :city-status :computer :country-id 1}]])
      (reset! atoms/computer-map @atoms/game-map)
      (reset! atoms/production {[0 0] {:item :transport :remaining-rounds 1}})
      ;; Run production to spawn the transport
      (player-prod/update-production)
      (let [unit (:contents (get-in @atoms/game-map [0 0]))]
        (should= :transport (:type unit))
        (should= 5 (:stuck-since-round unit))
        (should= [0 0] (:produced-at unit)))))

  (describe "directed transport assignment"
    (it "full loading transport gets assigned to free city on computer-map"
      (let [game-map (build-test-map ["t~~+"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map (build-test-map ["~~~+"]))
        (swap! atoms/game-map assoc-in [0 0 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :stuck-since-round 0})
        (transport/process-transport [0 0])
        (let [t-pos (first (for [c (range 4)
                                  :when (= :transport (get-in @atoms/game-map [c 0 :contents :type]))]
                              [c 0]))
              transport (get-in @atoms/game-map (conj t-pos :contents))]
          (should= :directed (:transport-mission transport))
          (should= [3 0] (:target-city transport))
          (should (vector? (:path transport))))))

    (it "full loading transport gets assigned to player city on computer-map"
      (let [game-map (build-test-map ["t~~O"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map (build-test-map ["~~~O"]))
        (swap! atoms/game-map assoc-in [0 0 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :stuck-since-round 0})
        (transport/process-transport [0 0])
        (let [t-pos (first (for [c (range 4)
                                  :when (= :transport (get-in @atoms/game-map [c 0 :contents :type]))]
                              [c 0]))
              transport (get-in @atoms/game-map (conj t-pos :contents))]
          (should= :directed (:transport-mission transport))
          (should= [3 0] (:target-city transport)))))

    (it "not-full transport is not assigned"
      (let [game-map (build-test-map ["t~~+"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map (build-test-map ["~~~+"]))
        (swap! atoms/game-map assoc-in [0 0 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 5
                :stuck-since-round 0})
        (transport/process-transport [0 0])
        (let [t-pos (first (for [c (range 4)
                                  :when (= :transport (get-in @atoms/game-map [c 0 :contents :type]))]
                              [c 0]))
              transport (get-in @atoms/game-map (conj t-pos :contents))]
          (should= :loading (:transport-mission transport)))))

    (it "directed transport follows stored path"
      (reset! atoms/game-map (build-test-map ["t~~+"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :directed :army-count 6
              :target-city [3 0] :path [[1 0] [2 0]]
              :stuck-since-round 0})
      (transport/process-transport [0 0])
      (should-be-nil (:contents (get-in @atoms/game-map [0 0])))
      (let [transport (:contents (get-in @atoms/game-map [1 0]))]
        (should= :transport (:type transport))
        (should= :directed (:transport-mission transport))
        (should= [[2 0]] (:path transport))))

    (it "directed transport waits when next step is blocked"
      (reset! atoms/game-map (build-test-map ["td~+"]))
      (reset! atoms/computer-map @atoms/game-map)
      (swap! atoms/game-map assoc-in [0 0 :contents]
             {:type :transport :owner :computer
              :transport-mission :directed :army-count 6
              :target-city [3 0] :path [[1 0] [2 0]]
              :stuck-since-round 0})
      (transport/process-transport [0 0])
      (should= :transport (get-in @atoms/game-map [0 0 :contents :type]))
      (should= [[1 0] [2 0]] (:path (get-in @atoms/game-map [0 0 :contents]))))

    (it "directed transport with empty path unloads and resumes loading"
      (reset! atoms/game-map [[{:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :directed
                                                        :army-count 2
                                                        :target-city [0 3]
                                                        :path []}}
                                {:type :land}
                                {:type :city :city-status :free}]])
      (reset! atoms/computer-map @atoms/game-map)
      (transport/process-transport [0 1])
      (should= :army (get-in @atoms/game-map [0 2 :contents :type]))
      (let [transport (:contents (get-in @atoms/game-map [0 1]))]
        (should= :loading (:transport-mission transport))
        (should-be-nil (:target-city transport))
        (should-be-nil (:path transport))))

    (it "assignment drops when target city is computer-owned"
      (let [game-map (build-test-map ["t~~X"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map game-map)
        (swap! atoms/game-map assoc-in [3 0 :city-status] :computer)
        (swap! atoms/game-map assoc-in [0 0 :contents]
               {:type :transport :owner :computer
                :transport-mission :directed :army-count 6
                :target-city [3 0] :path [[1 0] [2 0]]
                :stuck-since-round 0})
        (transport/process-transport [0 0])
        (let [t-pos (first (for [c (range 4)
                                  :when (= :transport (get-in @atoms/game-map [c 0 :contents :type]))]
                              [c 0]))
              transport (get-in @atoms/game-map (conj t-pos :contents))]
          (should= :loading (:transport-mission transport))
          (should-be-nil (:target-city transport))
          (should-be-nil (:path transport)))))

    (it "ignores computer-owned cities for assignment"
      (let [game-map (build-test-map ["t~~X"])]
        (reset! atoms/game-map game-map)
        (reset! atoms/computer-map (build-test-map ["~~~X"]))
        (swap! atoms/game-map assoc-in [3 0 :city-status] :computer)
        (swap! atoms/computer-map assoc-in [3 0 :city-status] :computer)
        (swap! atoms/game-map assoc-in [0 0 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
                :stuck-since-round 0})
        (with-redefs [rand-int (constantly 90)]
          (transport/process-transport [0 0]))
        (let [t-pos (first (for [c (range 4)
                                  :when (= :transport (get-in @atoms/game-map [c 0 :contents :type]))]
                              [c 0]))
              transport (get-in @atoms/game-map (conj t-pos :contents))]
          (should= :sailing (:transport-mission transport)))))))
