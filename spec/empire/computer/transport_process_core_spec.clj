(ns empire.computer.transport-process-core-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.transport :as transport]

            [empire.computer.land-objectives :as land-objectives]
            [empire.player.production :as player-prod]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map! set-test-world! update-test-world!]]))
(describe "process-transport"
  (before (reset-all-atoms!))

  (context "coastal crawl loading"
    (it "crawls along coastline adjacent to land"
      ;; ###    land at row 0
      ;; t~~    transport at [0 1]
      (set-test-world! (build-test-map ["###"
                                               "t~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0
})
      (transport/process-transport [0 1])
      ;; Transport should have moved to another coastal sea cell
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 1])))
      (let [t-pos (first (for [c (range 3) r (range 2)
                                :when (= :transport (get-in (test-utils/read-test-state :game-map) [c r :contents :type]))]
                            [c r]))]
        (should-not-be-nil t-pos)))

    (it "loading coastal crawl moves 2 cells per round (speed 2)"
      ;; ########   land at row 0
      ;; t~~~~~~~   transport at [0,1]
      ;; Linear coast — only one direction to crawl (rightward)
      (set-test-world! (build-test-map ["########"
                                               "t~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0})
      (transport/process-transport [0 1])
      ;; Transport should have moved 2 cells, not 1
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 1])))
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [1 1])))
      (should= :transport (get-in (test-utils/read-test-state :game-map) [2 1 :contents :type])))

    (it "loads army from adjacent land while crawling"
      ;; a##    army at [0 0]
      ;; t~~    transport at [0 1]
      (set-test-world! (build-test-map ["a##"
                                               "t~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0
})
      (transport/process-transport [0 1])
      ;; Army at [0 0] should be loaded
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0])))
      ;; Find transport and check army-count
      (let [t-pos (first (for [c (range 3) r (range 2)
                                :when (= :transport (get-in (test-utils/read-test-state :game-map) [c r :contents :type]))]
                            [c r]))
            transport (get-in (test-utils/read-test-state :game-map) (conj t-pos :contents))]
        (should= 1 (:army-count transport))))

    (it "stays put in open sea with no adjacent land"
      ;; Transport in open sea (no adjacent land) - no coastal targets
      (set-test-world! [[{:type :sea} {:type :sea}]
                               [{:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading
                                                        :army-count 0
                                                       }} {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [1 0])
      ;; Transport should stay put - surrounded by open sea
      (should= :transport (get-in (test-utils/read-test-state :game-map) [1 0 :contents :type])))

    (it "loads multiple armies from adjacent land"
      ;; aaa    3 armies at row 0
      ;; t~~    transport at [0 1]
      (set-test-world! (build-test-map ["aaa"
                                               "t~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 0
})
      (transport/process-transport [0 1])
      ;; Find transport and check army-count
      (let [t-pos (first (for [c (range 3) r (range 2)
                                :when (= :transport (get-in (test-utils/read-test-state :game-map) [c r :contents :type]))]
                            [c r]))
            transport (get-in (test-utils/read-test-state :game-map) (conj t-pos :contents))]
        ;; Should have loaded at least 1 army (the one adjacent at start)
        (should (pos? (:army-count transport)))))

    (it "loading transport with armies does NOT opportunistically unload"
      ;; Transport at [1,1] in loading mode with 4 armies.
      ;; Adjacent land at [0,0]-[0,2] is foreign — but loading transports
      ;; should keep their armies, not dump them.
      (set-test-world! (build-test-map ["###"
                                               "~t~"
                                               "~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [1 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :loading :army-count 4
              :country-id 99})
      (transport/process-transport [1 1])
      ;; No armies should be unloaded onto land
      (let [armies-on-land (for [c (range 3)
                                 :let [cell (get-in (test-utils/read-test-state :game-map) [c 0])]
                                 :when (= :army (:type (:contents cell)))]
                             [c 0])]
        (should= 0 (count armies-on-land)))
      ;; Transport should still have 4 armies (starts sailing since >= 4 and no nearby loadable)
      (let [t (first (for [c (range 3) r (range 3)
                           :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                           :when (= :transport (:type unit))]
                       unit))]
        (should= 4 (:army-count t)))))

  (context "unloading behavior"
    (it "unloads armies onto adjacent land"
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 2}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      ;; Should have unloaded an army onto land
      (should= :army (:type (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))
      ;; Transport should have fewer armies
      (should= 1 (:army-count (:contents (get-in (test-utils/read-test-state :game-map) [0 1])))))

    (it "unloading crawl moves toward unloadable coast and unloads as soon as possible"
      ;; ########   land at row 0 (cols 0-1 excluded, cols 2+ unloadable)
      ;; t~~~~~~~   transport at [0,1] in unloading mode
      ;; Adjacent land excluded → opportunistic unload fails.
      ;; BFS finds unloadable land at col 2 → unloading-crawl-move fires.
      (set-test-world! (build-test-map ["########"
                                               "t~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (doseq [c (range 2)]
        (update-test-world! assoc-in [c 0 :country-id] 1))
      (update-test-world! assoc-in [0 1 :contents]
             {:type :transport :owner :computer
              :transport-mission :unloading :army-count 2
              :country-id 1
              :pickup-country-id 1})
      (transport/process-transport [0 1])
      ;; Transport crawls toward unloadable coast and unloads immediately on arrival.
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 1])))
      (should= :transport (get-in (test-utils/read-test-state :game-map) [1 1 :contents :type]))
      (should= :army (get-in (test-utils/read-test-state :game-map) [2 0 :contents :type]))
      (should= 1 (get-in (test-utils/read-test-state :game-map) [1 1 :contents :army-count])))

    (it "changes to loading mode after full unload"
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      ;; Transport should be in loading mode now
      (should= :loading (:transport-mission (:contents (get-in (test-utils/read-test-state :game-map) [0 1]))))))

  (context "sail-path sailing transition"
    (it "full transport enters sailing with sail-path toward fog"
      ;; 5x5 all sea, transport at [2 1]. Rows 0-2 explored, rows 3-4 unexplored.
      ;; Transport should enter sailing mode with a sail-path.
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (set-test-world! game-map)
        (set-test-computer-map!
                (vec (for [c (range 5)]
                       (vec (for [r (range 5)]
                              (if (< r 3) {:type :sea} nil))))))
        (update-test-world! assoc-in [2 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (transport/process-transport [2 1])
        (let [t (:contents (get-in (test-utils/read-test-state :game-map) [2 1]))]
          (should= :sailing (:transport-mission t))
          (should-not-be-nil (:sail-path t))))))

  (context "mission transitions"
    (it "transport with no mission defaults to loading"
      (set-test-world! [[{:type :sea :contents {:type :transport :owner :computer
                                                       :army-count 0}}
                                {:type :sea}
                                {:type :land}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 0])
      ;; Find transport wherever it ended up
      (let [t-pos (first (for [c (range 3)
                               :when (= :transport (get-in (test-utils/read-test-state :game-map) [0 c :contents :type]))]
                           [0 c]))
            transport (get-in (test-utils/read-test-state :game-map) (conj t-pos :contents))]
        (should= :loading (:transport-mission transport)))))

  (context "find-armies-for-invasion targeting"
    (it "targets nearest coastal army within chebyshev 6 using sea BFS"
      ;; transport at [0,1], inland army at [7,0], coastal army at [2,0]
      ;; should move toward the coastal one, not the inland one.
      (set-test-world! (build-test-map ["##a####a"
                                        "t~~~~~~~"
                                        "~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 1 :contents]
                         {:type :transport :owner :computer
                          :transport-mission :find-armies-for-invasion
                          :major-invasion true
                          :major-invasion-target [4 0]
                          :army-count 0})
      (transport/process-transport [0 1])
      (should= :transport (get-in (test-utils/read-test-state :game-map) [1 1 :contents :type])))

    (it "opts out of invasion loading when no coastal army is reachable within 6"
      ;; nearest coastal army exists but beyond distance threshold.
      (set-test-world! (build-test-map ["#######a"
                                        "t~~~~~~~"
                                        "~~~~~~~~"]))
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (update-test-world! assoc-in [0 1 :contents]
                         {:type :transport :owner :computer
                          :transport-mission :find-armies-for-invasion
                          :major-invasion true
                          :major-invasion-target [7 0]
                          :army-count 0})
      (transport/process-transport [0 1])
      (let [t (:contents (get-in (test-utils/read-test-state :game-map) [0 1]))]
        (should= :loading (:transport-mission t))
        (should= 0 (:major-invasion-skip-revision t)))))

  (context "ignores non-computer transports"
    (it "returns nil for player transport"
      (set-test-world! [[{:type :sea :contents {:type :transport :owner :player
                                                       :army-count 0}}]])
      (should-be-nil (transport/process-transport [0 0])))

    (it "returns nil for empty cell"
      (set-test-world! [[{:type :sea}]])
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
        (set-test-world! game-map)
        ;; Leave row 4 unexplored so transport has fog to explore toward
        (set-test-computer-map!
                (vec (for [c (range 3)]
                       (vec (for [r (range 5)]
                              (if (< r 4) (get-in game-map [c r]) nil))))))
        (update-test-world! assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (transport/process-transport [1 1])
        ;; Transport may have moved; find it
        (let [t (some (fn [[c r]]
                        (let [contents (get-in (test-utils/read-test-state :game-map) [c r :contents])]
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
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 1
                :pickup-continent-pos [2 5]})
        (transport/process-transport [1 1])
        (should= :army (:type (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))
        (let [transport (:contents (get-in (test-utils/read-test-state :game-map) [1 1]))]
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
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 1
                :pickup-continent-pos [1 4]})
        (transport/process-transport [1 1])
        (let [transport (:contents (get-in (test-utils/read-test-state :game-map) [1 1]))]
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
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [0 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 1
                :pickup-continent-pos [4 5]})
        (transport/process-transport [0 2])
        (let [transport-pos (first (for [r (range 6) c (range 5)
                                        :when (= :transport (get-in (test-utils/read-test-state :game-map) [c r :contents :type]))]
                                    [c r]))
              transport (get-in (test-utils/read-test-state :game-map) (conj transport-pos :contents))]
          (should= :loading (:transport-mission transport))
          ;; pickup-continent-pos should be on the OTHER army continent (rows 4-5),
          ;; not on the unload continent (rows 0-1)
          (should (>= (second (:pickup-continent-pos transport)) 4)))))))
