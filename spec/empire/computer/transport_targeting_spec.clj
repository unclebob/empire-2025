(ns empire.computer.transport-targeting-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [empire.test-utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.transport :as transport]
            [empire.computer.transport-targeting :as targeting]

            [empire.computer.land-objectives :as land-objectives]
            [empire.player.production :as player-prod]
            [empire.test-utils :refer [build-test-map reset-all-atoms! set-test-computer-map!
                                       set-test-world! update-test-world!]]))

(describe "process-transport"
  (before (reset-all-atoms!))

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
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :claimed-transport-targets #{})
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
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :claimed-transport-targets #{})
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
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :claimed-transport-targets #{})
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
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :claimed-transport-targets #{})
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
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :claimed-transport-targets #{})
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
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :claimed-transport-targets #{})
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
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              ;; Transport at row 1 is closer to city at row 2 than row 7
              target (transport/find-unload-target pickup-continent [1 1])]
          (should= [0 2] target)))))

  (context "pickup-country-id exclusion"
    (it "does not unload on pickup-country-id land"
      ;; Transport from city (country-id 1), loaded from country-id 5.
      ;; pcp cleared (nil). Adjacent land has country-id 5.
      ;; Should NOT unload because pickup-country-id 5 should be excluded.
      (set-test-world! [[{:type :land :country-id 5}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :sailing :army-count 6
                                                        :sail-path [[0 2]]
                                                        :country-id 1
                                                        :pickup-country-id 5}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      (should-be-nil (:contents (get-in (test-utils/read-test-state :game-map) [0 0]))))

    (it "records pickup-country-id when entering sailing"
      ;; Transport at [1,1] adjacent to land with country-id 7.
      ;; When it enters sailing, pickup-country-id should be recorded.
      (let [game-map (build-test-map ["###"
                                      "~t~"
                                      "~~~"
                                      "~~~"
                                      "~~~"])]
        (set-test-world! game-map)
        (doseq [c (range 3)]
          (update-test-world! assoc-in [c 0 :country-id] 7))
        (set-test-computer-map!
                (vec (for [c (range 3)]
                       (vec (for [r (range 5)]
                              (if (< r 4) (get-in game-map [c r]) nil))))))
        (update-test-world! assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6})
        (transport/process-transport [1 1])
        (let [t (first (for [c (range 3) r (range 5)
                             :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
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
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (update-test-world! assoc-in [3 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :pickup-continent-pos [0 1]})
        (transport/process-transport [3 2])
        (let [transport-pos (first (for [c (range 5) r (range 12)
                                        :when (= :transport (get-in (test-utils/read-test-state :game-map) [c r :contents :type]))]
                                    [c r]))
              transport (get-in (test-utils/read-test-state :game-map) (conj transport-pos :contents))]
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
        (set-test-world! game-map)
        (doseq [c (range 5) r (range 2)]
          (update-test-world! assoc-in [c r :country-id] 1))
        (set-test-computer-map!
                (vec (for [c (range 5)]
                       (vec (for [r (range 5)]
                              (if (< r 3) (get-in (test-utils/read-test-state :game-map) [c r]) nil))))))
        (update-test-world! assoc-in [2 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :country-id 1
                :pickup-continent-pos [2 1]})
        (transport/process-transport [2 2])
        (let [t (first (for [c (range 5) r (range 5)
                             :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :sailing (:transport-mission t)))))

    (it "unloading transport in open sea with no coast starts sailing"
      ;; Full transport in unloading mode in open sea with no adjacent land.
      ;; Coast-crawl fails, so it starts sailing via BFS.
      (let [game-map (build-test-map ["~~~~~"
                                      "~~~~~"
                                      "~~~~~"])]
        (set-test-world! game-map)
        (set-test-computer-map!
                (vec (for [c (range 5)]
                       (vec (for [r (range 3)]
                              (if (< r 2) {:type :sea} nil))))))
        (update-test-world! assoc-in [2 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :unload-target-city [0 5]})
        (transport/process-transport [2 1])
        (let [t (first (for [c (range 5) r (range 3)
                               :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                               :when (= :transport (:type unit))]
                           unit))]
          (should= :sailing (:transport-mission t)))))

    (it "unload-target-city cleared when transport transitions to loading"
      ;; Transport with 1 army unloads completely, transitioning to loading.
      ;; The stored unload-target-city should be cleared.
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1
                                                        :unload-target-city [0 0]}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) [0 1]))]
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
        (set-test-world! game-map)
        (set-test-computer-map!
                (vec (for [c (range 5)]
                       (vec (for [r (range 5)]
                              (if (< r 3) {:type :sea} nil))))))
        (update-test-world! assoc-in [2 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :unloading :army-count 6
                :pickup-continent-pos [0 1]})
        (transport/process-transport [2 2])
        ;; Transport should switch to sailing and try to move
        (let [t (first (for [c (range 5) r (range 5)
                               :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
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
        (set-test-world! game-map)
        (set-test-computer-map!
                (vec (for [c (range 5)]
                       (vec (for [r (range 5)]
                              (if (< r 3) {:type :sea} nil))))))
        (update-test-world! assoc-in [2 2 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
})
        (transport/process-transport [2 2])
        (let [t (first (for [c (range 5) r (range 5)
                               :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
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
        (set-test-world! game-map)
        ;; Row 4 unexplored
        (set-test-computer-map!
                (vec (for [c (range 3)]
                       (vec (for [r (range 5)]
                              (if (< r 4)
                                (get-in game-map [c r])
                                nil))))))
        (update-test-world! assoc-in [1 3 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 6
})
        (transport/process-transport [1 3])
        ;; Transport should have explored toward unexplored
        (let [t (first (for [c (range 3) r (range 5)
                               :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                               :when (= :transport (:type unit))]
                           unit))]
          (should= :transport (:type t))
          (should= :sailing (:transport-mission t))))))

  (context "no-reload from recently unloaded country"
    (it "transport avoids armies from recently unloaded country"
      ;; Transport adjacent to two armies: country-1 (recently unloaded) and country-2.
      ;; Should only load the country-2 army during auto-load.
      (test-utils/set-test-state! :round-number 10)
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :country-id 1}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 0
                                                        :unloaded-countries {1 5}}}
                                {:type :land :contents {:type :army :owner :computer :country-id 2}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      ;; Country-1 army should still be on land (skipped by filter)
      (should= :army (get-in (test-utils/read-test-state :game-map) [0 0 :contents :type]))
      ;; Country-2 army should be loaded
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 2 :contents]))
      ;; Transport should have 1 army loaded
      (let [t-pos (first (for [c (range 3)
                               :when (= :transport (get-in (test-utils/read-test-state :game-map) [0 c :contents :type]))]
                           [0 c]))
            transport (get-in (test-utils/read-test-state :game-map) (conj t-pos :contents))]
        (should= 1 (:army-count transport))))

    (it "exclusion expires after 10 rounds"
      ;; Same as avoidance test but round 20 (15 rounds since unload at round 5 - expired).
      ;; Country-1 army should now be loadable again.
      (test-utils/set-test-state! :round-number 20)
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer :country-id 1}}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 0
                                                        :unloaded-countries {1 5}}}
                                {:type :land :contents {:type :army :owner :computer :country-id 2}}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      ;; Both armies should be loaded (exclusion expired)
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 0 :contents]))
      (should-be-nil (get-in (test-utils/read-test-state :game-map) [0 2 :contents]))
      ;; Transport should have 2 armies loaded
      (let [t-pos (first (for [c (range 3)
                               :when (= :transport (get-in (test-utils/read-test-state :game-map) [0 c :contents :type]))]
                           [0 c]))
            transport (get-in (test-utils/read-test-state :game-map) (conj t-pos :contents))]
        (should= 2 (:army-count transport))))

    (it "armies with no country-id are not filtered"
      (test-utils/set-test-state! :round-number 10)
      (set-test-world! [[{:type :land :contents {:type :army :owner :computer}}
                                {:type :sea}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :loading :army-count 0
                                                        :unloaded-countries {1 5}}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 2])
      ;; Should move toward army (no country-id, not filtered)
      (let [transport-pos (first (for [c (range 4)
                                       :when (= :transport (get-in (test-utils/read-test-state :game-map) [0 c :contents :type]))]
                                   [0 c]))]
        (should= [0 1] transport-pos)))

    (it "records unloaded country-id on unload"
      (test-utils/set-test-state! :round-number 15)
      (set-test-world! [[{:type :land :country-id 3}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) [0 1]))]
        (should= 15 (get-in transport [:unloaded-countries 3]))))

    (it "adjacent-to-pickup-continent? distance fallback at boundary (L22)"
      ;; When pcp has no country-id, falls back to distance <= 2.
      ;; pos at distance 2 should return true; distance 3 should return false.
      (set-test-world! [[{:type :land} {:type :sea} {:type :sea} {:type :sea}]])
      ;; pcp at [0 0] has no country-id
      (should (targeting/adjacent-to-pickup-continent? [0 2] [0 0]))
      (should-not (targeting/adjacent-to-pickup-continent? [0 3] [0 0])))

    (it "score-target-city multiplication affects target ranking (L36)"
      ;; Two player cities at different distances. Closer one should win.
      ;; With * the closer city has lower score; with / it would invert.
      (let [game-map (build-test-map ["###"
                                      "~~~"
                                      "O##"  ;; close player city
                                      "~~~"
                                      "~~~"
                                      "~~~"
                                      "~~~"
                                      "O##"  ;; far player city
                                      "###"])]
        (set-test-world! game-map)
        (set-test-computer-map! game-map)
        (test-utils/set-test-state! :claimed-transport-targets #{})
        (let [pickup-continent (land-objectives/flood-fill-continent [0 0])
              target (transport/find-unload-target pickup-continent [1 1])]
          (should= [0 2] target))))

    (it "transport falls back to explore when all armies filtered"
      (test-utils/set-test-state! :round-number 10)
      (let [game-map (build-test-map ["a~a"
                                      "~t~"
                                      "~~~"])]
        (set-test-world! game-map)
        (set-test-computer-map! (build-test-map ["a~a"
                                                    "~t~"
                                                    "~~-"]))
        (update-test-world! assoc-in [0 0 :contents :country-id] 1)
        (update-test-world! assoc-in [2 0 :contents :country-id] 1)
        (update-test-world! assoc-in [1 1 :contents]
               {:type :transport :owner :computer
                :transport-mission :loading :army-count 0
                :unloaded-countries {1 5}})
        (transport/process-transport [1 1])
        (let [transport-pos (first (for [r (range 3) c (range 3)
                                         :when (= :transport (get-in (test-utils/read-test-state :game-map) [r c :contents :type]))]
                                     [r c]))]
          (should-not-be-nil transport-pos)
          (should-not= [1 1] transport-pos))))))
