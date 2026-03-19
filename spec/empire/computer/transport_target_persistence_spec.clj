(ns empire.computer.transport-target-persistence-spec
  "Tests for VMS Empire style computer transport movement."
  (:require [empire.test.utils :as test-utils]
            [speclj.core :refer :all]
            [empire.computer.transport :as transport]
            [empire.computer.transport-targeting :as targeting]

            [empire.computer.land-objectives :as land-objectives]
            [empire.player.production :as player-prod]
            [empire.test.utils :refer [build-test-map reset-all-atoms! set-test-computer-map!
                                       set-test-world! update-test-world!]]))
(describe "process-transport"
  (before (reset-all-atoms!))

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
        (set-test-computer-map! (test-utils/read-test-state :game-map))
        (transport/process-transport [3 2])
        (let [transport-pos (first (for [c (range 5) r (range 12)
                                        :when (= :transport (get-in (test-utils/read-test-state :game-map) [c r :contents :type]))]
                                    [c r]))
              transport (get-in (test-utils/read-test-state :game-map) (conj transport-pos :contents))]
          ;; Transport keeps unloading and continues searching by crawl.
          (should= :unloading (:transport-mission transport)))))

    (it "re-sails when all nearby land is excluded by country-id"
      ;; Transport at [2,2] in :unloading with 6 armies.
      ;; Adjacent land (rows 0-1) has country-id 1 matching pcp.
      ;; No unloadable land nearby — the transport now stays in unloading and retries crawl.
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
        (set-test-computer-map!
                (assoc-in (test-utils/read-test-state :computer-map)
                          [2 2 :contents]
                          {:type :transport :owner :computer
                           :transport-mission :unloading :army-count 6
                           :country-id 1
                           :pickup-continent-pos [2 1]}))
        (transport/process-transport [2 2])
        (let [t (first (for [c (range 5) r (range 5)
                             :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                             :when (= :transport (:type unit))]
                         unit))]
          (should= :unloading (:transport-mission t)))))

    (it "unloading transport in open sea with no coast stays unloading"
      ;; Full transport in unloading mode in open sea with no adjacent land.
      ;; Coast-crawl fails, so it remains in unloading after resetting crawl memory.
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
        (set-test-computer-map!
                (assoc-in (test-utils/read-test-state :computer-map)
                          [2 1 :contents]
                          {:type :transport :owner :computer
                           :transport-mission :unloading :army-count 6
                           :unload-target-city [0 5]}))
        (transport/process-transport [2 1])
        (let [t (first (for [c (range 5) r (range 3)
                               :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                               :when (= :transport (:type unit))]
                           unit))]
          (should= :unloading (:transport-mission t)))))

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
        (should= :sail-to-load (:transport-mission transport))
        (should-be-nil (:unload-target-city transport)))))


  (context "global BFS unload (VMS-consistent)"
    (it "stuck unloading transport with no adjacent land stays unloading"
      ;; Full transport in :unloading mode with no adjacent unclaimed land.
      ;; It now stays in :unloading and retries crawl after clearing history.
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
        (set-test-computer-map!
                (assoc-in (test-utils/read-test-state :computer-map)
                          [2 2 :contents]
                          {:type :transport :owner :computer
                           :transport-mission :unloading :army-count 6
                           :pickup-continent-pos [0 1]}))
        (transport/process-transport [2 2])
        ;; Transport should stay unloading and try to continue its crawl.
        (let [t (first (for [c (range 5) r (range 5)
                               :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                               :when (= :transport (:type unit))]
                           unit))]
          (should= :transport (:type t))
          (should= :unloading (:transport-mission t)))))

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
        (set-test-computer-map!
                (assoc-in (test-utils/read-test-state :computer-map)
                          [2 2 :contents]
                          {:type :transport :owner :computer
                           :transport-mission :loading :army-count 6}))
        (transport/process-transport [2 2])
        (let [t (first (for [c (range 5) r (range 5)
                               :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                               :when (= :transport (:type unit))]
                           unit))]
          (should= :transport (:type t))
          (should= :sail-to-unload (:transport-mission t)))))

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
        (set-test-computer-map!
                (assoc-in (test-utils/read-test-state :computer-map)
                          [1 3 :contents]
                          {:type :transport :owner :computer
                           :transport-mission :loading :army-count 6}))
        (transport/process-transport [1 3])
        ;; Transport should have explored toward unexplored
        (let [t (first (for [c (range 3) r (range 5)
                               :let [unit (get-in (test-utils/read-test-state :game-map) [c r :contents])]
                               :when (= :transport (:type unit))]
                           unit))]
          (should= :transport (:type t))
          (should= :sail-to-unload (:transport-mission t))))))

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

    (it "does not record unloaded country-id when unloading onto unclaimed land"
      (test-utils/set-test-state! :round-number 15)
      (set-test-world! [[{:type :land}
                                {:type :sea :contents {:type :transport :owner :computer
                                                        :transport-mission :unloading
                                                        :army-count 1}}
                                {:type :sea}]])
      (set-test-computer-map! (test-utils/read-test-state :game-map))
      (transport/process-transport [0 1])
      (let [transport (:contents (get-in (test-utils/read-test-state :game-map) [0 1]))]
        (should-be-nil (:unloaded-countries transport))))

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
        ;; The only unexplored cell is the southeast corner; the two visible armies belong to
        ;; a recently unloaded country and must be ignored as pickup targets.
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
          (should-not= [1 1] transport-pos)
          (should= :sea (get-in (test-utils/read-test-state :game-map) (conj transport-pos :type))))))))
